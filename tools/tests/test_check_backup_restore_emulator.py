#!/usr/bin/env python3
"""锁定 M0-1 模拟器备份恢复门禁的设备安全边界。"""

from __future__ import annotations

import unittest
from unittest import mock

from tools import check_backup_restore_emulator


class BackupRestoreEmulatorToolTest(unittest.TestCase):
    """确保设备身份在任何 ADB 调用前得到强制校验。"""

    def test_main_rejects_physical_serial_before_adb(self) -> None:
        """非 emulator 序列号必须立即失败，且不得产生任何 ADB 操作。"""

        # ADB 替身用于证明实体设备序列号在调用边界前即被拒绝。
        with mock.patch.object(check_backup_restore_emulator, "run_adb") as adb_call:
            with self.assertRaisesRegex(SystemExit, "physical devices are forbidden"):
                check_backup_restore_emulator.main(["--serial", "physical-device"])
            adb_call.assert_not_called()


if __name__ == "__main__":
    unittest.main()
