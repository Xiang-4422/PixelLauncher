from __future__ import annotations

import copy
import unittest

from tools.check_device_soak import DeviceSoakValidationError, validate_report


class DeviceSoakReportTest(unittest.TestCase):
    """验证设备长跑门禁接受完整证据并拒绝短跑、残留和伪造趋势。"""

    def valid_report(self, *, duration_seconds: int = 1800) -> dict[str, object]:
        """构造满足当前 schema 的最小合格报告。"""

        # 11 个严格递增样本满足正式 Goal 趋势证据数量。
        memory_samples = [
            {
                "elapsedMillis": index * 180_000 + 1,
                "totalPssKb": 20_000 + index * 100,
                "javaHeapKb": 4_000 + index * 10,
            }
            for index in range(11)
        ]
        # 起始与末尾中位数按 instrumentation 首尾三分位算法给出。
        first_median = 20_100
        last_median = 20_900
        # 六类旅程之和必须与完成周期一致。
        journey_counts = {
            "startup": 2,
            "listScroll": 2,
            "textInput": 2,
            "animation": 2,
            "pageTransition": 1,
            "overlay": 1,
        }
        # 终态资源键严格对应 Android 报告协议。
        maximum_residue = {
            "pendingCallbacks": 0,
            "frameListeners": 0,
            "activeTickers": 0,
            "liveTickers": 0,
            "sourceFramePending": 0,
            "retainedElementRoot": 0,
            "retainedRenderRoot": 0,
            "retainedTargets": 0,
            "pendingBuild": 0,
            "focusedTextInput": 0,
            "activePagers": 0,
            "activeLists": 0,
        }
        return {
            "schemaVersion": 1,
            "status": "pass",
            "qualifiesForGoal": duration_seconds >= 1800,
            "startedEpochMillis": 1,
            "requestedDurationSeconds": duration_seconds,
            "actualDurationMillis": duration_seconds * 1_000 + 5_000,
            "completedJourneyCycles": 10,
            "terminalDiagnosticsChecks": 10,
            "device": {
                "hardwareSerial": "EMULATOR",
                "isEmulator": True,
                "apiLevel": 37,
                "release": "17",
                "model": "fixture",
                "refreshRateHz": 60.0,
            },
            "journeys": journey_counts,
            "maximumTerminalResidue": maximum_residue,
            "memorySamples": memory_samples,
            "heapTrend": {
                "firstMedianPssKb": first_median,
                "lastMedianPssKb": last_median,
                "growthPssKb": last_median - first_median,
                "allowedGrowthPssKb": 8192,
                "bounded": True,
            },
            "failure": None,
        }

    def assert_rejected(self, report: dict[str, object]) -> None:
        """断言正式门禁拒绝给定变异报告。"""

        with self.assertRaises(DeviceSoakValidationError):
            validate_report(report, require_qualified=True)

    def test_accepts_qualified_report(self) -> None:
        """真实 30 分钟、六旅程、零残留和有界 PSS 应通过。"""

        summary = validate_report(self.valid_report(), require_qualified=True)
        self.assertTrue(summary["qualifiesForGoal"])
        self.assertEqual(summary["completedJourneyCycles"], 10)

    def test_short_report_cannot_qualify(self) -> None:
        """接线短跑即使业务断言通过也不能冒充 Goal 证据。"""

        report = self.valid_report(duration_seconds=15)
        self.assert_rejected(report)

    def test_terminal_residue_is_rejected(self) -> None:
        """任一 callback、listener、ticker 或 retained 树残留都必须失败。"""

        report = copy.deepcopy(self.valid_report())
        report["maximumTerminalResidue"]["frameListeners"] = 1  # type: ignore[index]
        self.assert_rejected(report)

    def test_tampered_heap_boolean_is_rejected(self) -> None:
        """不能只把 bounded 改为 true 而绕过增长和预算复算。"""

        report = copy.deepcopy(self.valid_report())
        report["heapTrend"]["growthPssKb"] = 99_999  # type: ignore[index]
        self.assert_rejected(report)

    def test_missing_journey_is_rejected(self) -> None:
        """同名少量场景不能替代精确六旅程集合。"""

        report = copy.deepcopy(self.valid_report())
        del report["journeys"]["overlay"]  # type: ignore[index]
        self.assert_rejected(report)


if __name__ == "__main__":
    unittest.main()
