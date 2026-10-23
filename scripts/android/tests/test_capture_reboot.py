"""Safety guards must reject other devices/artifacts before installation or reboot."""
import importlib.util
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("capture_reboot", Path(__file__).parents[1] / "capture-reboot.py")
reboot = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reboot)


class RebootSafetyTest(unittest.TestCase):
    def test_physical_serial_is_rejected_without_adb(self):
        with patch("sys.argv", ["reboot", "--adb", "unused", "--serial", "physical-device"]), \
                patch.object(reboot.subprocess, "run") as run:
            with self.assertRaisesRegex(RuntimeError, "Explicit emulator"):
                reboot.main()
            run.assert_not_called()

    def test_unrelated_avd_is_rejected_before_installation(self):
        result = subprocess.CompletedProcess([], 0, b"PersonalAVD\n", b"")
        with patch("sys.argv", ["reboot", "--adb", "unused", "--serial", "emulator-5580"]), \
                patch.object(reboot.subprocess, "run", return_value=result) as run:
            with self.assertRaisesRegex(RuntimeError, "Wrong AVD"):
                reboot.main()
            self.assertEqual(1, run.call_count)

    def test_normal_apk_is_rejected_before_any_installation(self):
        def diagnostic_only(command, **kwargs):
            if "emu" in command:
                output = b"FinTrackCapture\n"
            elif "getprop" in command:
                output = b"1\n"
            elif "cat" in command:
                output = b"synthetic-boot-id\n"
            else:
                self.fail("Unexpected mutation before artifact validation")
            return subprocess.CompletedProcess(command, 0, output, b"")
        metadata = json.dumps({"applicationId": "com.financemanager.listener", "elements": [{}]})
        with patch("sys.argv", ["reboot", "--adb", "unused", "--serial", "emulator-5580"]), \
                patch.object(reboot.subprocess, "run", side_effect=diagnostic_only), \
                patch.object(Path, "read_text", return_value=metadata):
            with self.assertRaisesRegex(RuntimeError, "Wrong APK identity"):
                reboot.main()


if __name__ == "__main__":
    unittest.main()
