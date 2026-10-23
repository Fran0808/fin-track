"""Host reboot/Doze acceptance for the disposable, network-isolated capture app."""
import argparse
from contextlib import closing
import json
from pathlib import Path
import re
import sqlite3
import subprocess
import tempfile
import time
import uuid

TARGET = "com.financemanager.listener.capturetest"
COMPONENT = TARGET + "/com.financemanager.listener.service.YapeNotificationListenerService"
ROOT = Path(__file__).resolve().parents[2]
DB = "wallet_pulse_db"


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--scenario", choices=("reboot", "doze"), default="reboot")
    parser.add_argument("--kill-during-idle", action="store_true")
    args = parser.parse_args()
    require(re.fullmatch(r"emulator-\d+", args.serial), "Explicit emulator serial required")
    require(not args.kill_during_idle or args.scenario == "doze", "Idle kill requires Doze scenario")
    report_name = args.scenario + ("-kill" if args.kill_during_idle else "")
    output = ROOT / "android/app/build/outputs/capture-tests"
    output.mkdir(parents=True, exist_ok=True)
    run = "lifecycle-" + args.scenario + "-" + uuid.uuid4().hex[:16]

    def adb(*command, optional=False):
        result = subprocess.run([args.adb, "-s", args.serial, *command], capture_output=True, timeout=180)
        if result.returncode and not optional:
            raise RuntimeError("ADB operation failed: " + " ".join(command[:3]))
        return result

    def text(*command):
        return adb(*command).stdout.decode("utf-8", errors="replace").strip()

    def wait_boot(previous_boot=None):
        deadline = time.monotonic() + 240
        while time.monotonic() < deadline:
            result = adb("shell", "getprop", "sys.boot_completed", optional=True)
            if result.returncode == 0 and result.stdout.strip() == b"1":
                boot = text("shell", "cat", "/proc/sys/kernel/random/boot_id")
                if previous_boot is None or boot != previous_boot:
                    return boot
            time.sleep(2)
        raise RuntimeError("Android did not complete the required new boot")

    require(text("emu", "avd", "name").splitlines()[0] == "FinTrackCapture", "Wrong AVD")
    boot_before = wait_boot()
    # Validate every identity before installing any artifact. Never install the normal app.
    artifacts = [
        ("app/build/outputs/apk/captureTest", TARGET),
        ("emitter/build/outputs/apk/debug", "com.fintrack.capture.emitter"),
        ("emitter/build/outputs/apk/androidTest/debug", "com.fintrack.capture.emitter.test"),
    ]
    paths = []
    for directory, package in artifacts:
        folder = ROOT / "android" / directory
        metadata = json.loads((folder / "output-metadata.json").read_text())
        require(metadata["applicationId"] == package and len(metadata["elements"]) == 1, "Wrong APK identity")
        name = metadata["elements"][0]["outputFile"]
        require(Path(name).name == name, "Invalid APK filename")
        paths.append(folder / name)
    for path in paths:
        adb("install", "-r", str(path))
    require("android.permission.INTERNET" not in text("shell", "dumpsys", "package", TARGET),
            "Isolated app must have no Internet permission")

    def phase(name):
        result = adb("shell", "am", "instrument", "-w", "-r", "-e", "class",
                     "com.fintrack.capture.emitter.CaptureRebootTest", "-e", "capture_run", run,
                     "-e", "capture_scenario", args.scenario,
                     "-e", "reboot_phase", name,
                     "com.fintrack.capture.emitter.test/androidx.test.runner.AndroidJUnitRunner")
        report = result.stdout.decode("utf-8", errors="replace")
        (output / (report_name + "-" + name + ".txt")).write_text(report, encoding="utf-8")
        require("OK (1 test)" in report and "FAILURES!!!" not in report, "Reboot phase failed: " + name)

    def snapshot():
        # run-as/cat does not instantiate Application or send app callbacks. Copy WAL too.
        # Two equal consecutive reads reject a changing file set instead of reading torn data.
        def files():
            values = {}
            for suffix in ("", "-wal"):
                name = DB + suffix
                result = adb("exec-out", "run-as", TARGET, "cat", "databases/" + name, optional=True)
                if result.returncode == 0:
                    values[name] = result.stdout
                elif not suffix or b"No such file" not in result.stderr + result.stdout:
                    raise RuntimeError("Cannot passively read isolated database")
            return values
        for _ in range(6):
            first, second = files(), files()
            if first != second:
                continue
            with tempfile.TemporaryDirectory(prefix="fintrack-reboot-") as directory:
                for name, content in second.items():
                    (Path(directory) / name).write_bytes(content)
                with closing(sqlite3.connect(str(Path(directory) / DB))) as db:
                    observations = db.execute(
                        "SELECT observationKey,ownerKey,zoneId,postTime,eventTime,status,transactionHash "
                        "FROM capture_inbox WHERE kind='POSTED' AND sourceKey LIKE ?", ("%" + run + "%",)).fetchall()
                    movements = db.execute(
                        "SELECT transactionHash,ownerKey,transactionDate,isSynced FROM local_transactions "
                        "WHERE sourceKey LIKE ?", ("%" + run + "%",)).fetchall()
                return {"observations": observations, "movements": movements}
        raise RuntimeError("Isolated database did not yield a stable snapshot")

    def same_observation(before, after):
        require(len(after["observations"]) == 1, "Observation missing or duplicated")
        require(before["observations"][0][:5] == after["observations"][0][:5], "Capture identity/owner/date changed")

    def saved(state):
        return len(state["movements"]) == 1 and state["observations"][0][5] == "SAVED"

    idle_touched = False
    was_awake = False
    during_idle = None
    try:
        print("Preparing a committed pending capture", flush=True)
        phase("prepare")
        before = snapshot()
        require(len(before["observations"]) == 1 and before["observations"][0][5] == "PENDING"
                and not before["movements"], "Fixture must be durable and unprocessed before reboot")
        require(before["observations"][0][1] is None, "Fixture must be unpaired")
        if args.scenario == "reboot":
            print("Rebooting the isolated emulator", flush=True)
            adb("reboot")
            boot_after = wait_boot(boot_before)
        else:
            require(TARGET not in text("shell", "dumpsys", "deviceidle", "whitelist"),
                    "Target must not be permanently exempt from Doze")
            was_awake = "mWakefulness=Awake" in text("shell", "dumpsys", "power")
            idle_touched = True
            adb("shell", "input", "keyevent", "3")
            adb("shell", "dumpsys", "battery", "unplug")
            adb("shell", "input", "keyevent", "223")
            adb("shell", "dumpsys", "deviceidle", "force-idle", "deep")
            require(text("shell", "dumpsys", "deviceidle", "get", "deep") == "IDLE", "Deep Doze not entered")
            entered = snapshot()
            same_observation(before, entered)
            require(entered["observations"][0][5] == "PENDING" and not entered["movements"],
                    "Capture must still be pending at Doze entry")
            print("Deep Doze entered with one pending observation", flush=True)
            if args.kill_during_idle:
                pids = text("shell", "pidof", TARGET).split()
                require(len(pids) == 1 and pids[0].isdigit(), "Expected one isolated app process")
                adb("shell", "run-as", TARGET, "kill", "-9", pids[0])
                death_deadline = time.monotonic() + 20
                while pids[0] in adb("shell", "pidof", TARGET, optional=True).stdout.decode().split():
                    require(time.monotonic() < death_deadline, "Original process survived SIGKILL")
                    time.sleep(0.1)
                print("Original application process terminated during Doze", flush=True)
            idle_deadline = time.monotonic() + 45
            while True:
                require(text("shell", "dumpsys", "deviceidle", "get", "deep") == "IDLE", "Doze exited early")
                state = snapshot()
                same_observation(before, state)
                require(len(state["movements"]) <= 1, "Movement duplicated in Doze")
                if saved(state):
                    during_idle = state
                if time.monotonic() >= idle_deadline:
                    break
                time.sleep(2)
            print("Capture processed during Doze: " + str(during_idle is not None), flush=True)
            adb("shell", "dumpsys", "deviceidle", "unforce")
            adb("shell", "dumpsys", "battery", "reset")
            adb("shell", "input", "keyevent", "224")
            require(text("shell", "dumpsys", "deviceidle", "get", "deep") != "IDLE", "Doze did not exit")
            boot_after = text("shell", "cat", "/proc/sys/kernel/random/boot_id")
            require(boot_after == boot_before, "Unexpected reboot during Doze scenario")
        # A boot ID change proves device reboot; PID comparison alone would not.
        deadline = time.monotonic() + 30
        automatic = None
        while True:
            state = snapshot()
            same_observation(before, state)
            if saved(state):
                automatic = state
                break
            require(not state["movements"], "Unexpected partial/duplicate financial result")
            if time.monotonic() >= deadline:
                break
            time.sleep(2)
        print("Passive recovery observed: " + str(automatic is not None), flush=True)
        phase("verify")
        final = snapshot()
        same_observation(before, final)
        require(saved(final), "Explicit reopening did not recover the movement")
        require(final["movements"][0][1] is None and final["movements"][0][3] == 0, "Owner or sync state changed")
        require(final["observations"][0][6] == final["movements"][0][0], "Inbox and movement hashes differ")
        if automatic is not None:
            require(automatic["movements"] == final["movements"], "Explicit reopening changed recovered movement")
        if during_idle is not None:
            require(during_idle["movements"] == final["movements"], "Doze exit changed recovered movement")
        summary = {"run": run, "scenario": args.scenario, "boot_id_changed": boot_after != boot_before,
                   "automatic_recovery_observed": automatic is not None, "passive_window_seconds": 30,
                   "identity_owner_and_hash_preserved": True, "movements": 1, "synced": 0}
        if args.scenario == "doze":
            summary.update(deep_idle_verified=True, idle_observation_seconds=45,
                           process_killed_during_idle=args.kill_during_idle,
                           processed_during_idle=during_idle is not None)
        (output / (report_name + "-result.json")).write_text(json.dumps(summary, indent=2), encoding="utf-8")
        print(json.dumps(summary), flush=True)
    finally:
        try:
            if idle_touched:
                adb("shell", "dumpsys", "deviceidle", "unforce", optional=True)
                adb("shell", "dumpsys", "battery", "reset", optional=True)
                adb("shell", "input", "keyevent", "224" if was_awake else "223", optional=True)
        finally:
            try:
                phase("cleanup")
            finally:
                adb("shell", "cmd", "notification", "disallow_listener", COMPONENT, optional=True)


if __name__ == "__main__":
    main()
