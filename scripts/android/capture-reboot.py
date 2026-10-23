"""Host reboot acceptance for the disposable, network-isolated capture app."""
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
    args = parser.parse_args()
    require(re.fullmatch(r"emulator-\d+", args.serial), "Explicit emulator serial required")
    output = ROOT / "android/app/build/outputs/capture-tests"
    output.mkdir(parents=True, exist_ok=True)
    run = "lifecycle-reboot-" + uuid.uuid4().hex[:16]

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
                     "-e", "reboot_phase", name,
                     "com.fintrack.capture.emitter.test/androidx.test.runner.AndroidJUnitRunner")
        report = result.stdout.decode("utf-8", errors="replace")
        (output / ("reboot-" + name + ".txt")).write_text(report, encoding="utf-8")
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

    try:
        print("Preparing a committed pending capture", flush=True)
        phase("prepare")
        before = snapshot()
        require(len(before["observations"]) == 1 and before["observations"][0][5] == "PENDING"
                and not before["movements"], "Fixture must be durable and unprocessed before reboot")
        require(before["observations"][0][1] is None, "Fixture must be unpaired")
        print("Rebooting the isolated emulator", flush=True)
        adb("reboot")
        boot_after = wait_boot(boot_before)
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
        summary = {"run": run, "boot_id_changed": boot_after != boot_before,
                   "automatic_recovery_observed": automatic is not None, "passive_window_seconds": 30,
                   "identity_owner_and_hash_preserved": True, "movements": 1, "synced": 0}
        (output / "reboot-result.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
        print(json.dumps(summary), flush=True)
    finally:
        try:
            phase("cleanup")
        finally:
            adb("shell", "cmd", "notification", "disallow_listener", COMPONENT, optional=True)


if __name__ == "__main__":
    main()
