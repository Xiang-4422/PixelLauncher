from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from typing import Any

from tools import check_jvm_perf_trend


class JvmPerformanceTrendGateTest(unittest.TestCase):
    """Locks approved workload identity, seven-batch median validation, and ten-percent regressions."""

    # Stable fixture dimensions match the six production smoke journeys.
    SCENE_DIMENSIONS = {
        "animation": (24, 8),
        "graphics_primitives": (96, 24),
        "list_scroll": (96, 64),
        "overlay": (108, 46),
        "page_transition": (96, 40),
        "text_input": (96, 32),
    }

    def write_json(self, path: Path, value: dict[str, Any]) -> None:
        """Write one temporary JSON baseline fixture."""

        path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    def prepare_fixture(self, root: Path, *, measured_average_nanos: int = 100) -> dict[str, Path]:
        """Create one complete approved baseline and matching properties report."""

        # Six approved scene entries share a simple reference value while retaining real dimensions.
        baseline_scenes = {
            scene_name: {
                "width": dimensions[0],
                "height": dimensions[1],
                "approvedBaselineAverageNanos": 100,
            }
            for scene_name, dimensions in self.SCENE_DIMENSIONS.items()
        }
        # Approval and workload fields are mandatory comparison preconditions.
        baseline = {
            "schemaVersion": 1,
            "kind": "pixel-jvm-smoke-baseline",
            "maximumRegressionPercent": 10.0,
            "approval": {
                "status": "approved",
                "approvedBy": "fixture-maintainer",
                "approvedAtUtc": "2026-07-14T00:00:00Z",
                "technicalReason": "Deterministic unit-test baseline.",
            },
            "workload": {
                "reportFormatVersion": 2,
                "warmupFrames": 10,
                "sampleFrames": 20,
                "sampleBatches": 7,
                "sceneCount": 6,
            },
            "scenes": baseline_scenes,
        }
        baseline_path = root / "baseline.json"
        self.write_json(baseline_path, baseline)

        # Seven values put the requested measured duration at the exact sorted median.
        batch_averages = [measured_average_nanos - 2, measured_average_nanos - 1] + [measured_average_nanos] * 3 + [
            measured_average_nanos + 1,
            measured_average_nanos + 2,
        ]
        # Global fields identify the stable v2 sampling workload.
        lines = [
            "formatVersion=2",
            "runId=fixture-run",
            "thresholdScale=1.0",
            "warmupFrames=10",
            "sampleFrames=20",
            "sampleBatches=7",
            "sceneCount=6",
            "javaRuntimeVersion=21-fixture",
            "javaVmName=Fixture VM",
            "osName=Fixture OS",
            "osArch=fixture64",
        ]
        for scene_name, dimensions in self.SCENE_DIMENSIONS.items():
            # Each scene exposes dimensions, exact frame count, total, median, and full batch evidence.
            prefix = f"scene.{scene_name}"
            lines.extend(
                [
                    f"{prefix}.frames=140",
                    f"{prefix}.width={dimensions[0]}",
                    f"{prefix}.height={dimensions[1]}",
                    f"{prefix}.totalNanos={sum(batch_averages) * 20}",
                    f"{prefix}.averageNanos={measured_average_nanos}",
                    f"{prefix}.batchAverageNanos={','.join(str(value) for value in batch_averages)}",
                    f"{prefix}.pass=true",
                ],
            )
        report_path = root / "smoke.txt"
        report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return {
            "baseline": baseline_path,
            "report": report_path,
            "output": root / "trend.json",
        }

    def arguments(self, fixture: dict[str, Path]) -> list[str]:
        """Build the exact checker arguments for one temporary fixture."""

        return [
            "--report",
            str(fixture["report"]),
            "--baseline",
            str(fixture["baseline"]),
            "--output",
            str(fixture["output"]),
        ]

    def test_matching_approved_baseline_passes_all_scenes(self) -> None:
        """All six exact-workload medians at their approved values pass the trend gate."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # The fixture uses measured 100 ns against approved 100 ns in every scene.
            fixture = self.prepare_fixture(Path(temporary_directory))
            exit_code = check_jvm_perf_trend.main(self.arguments(fixture))
            report = json.loads(fixture["output"].read_text(encoding="utf-8"))
            self.assertEqual(0, exit_code)
            self.assertTrue(report["overallPassed"])
            self.assertEqual(6, len(report["checks"]))

    def test_eleven_percent_regression_returns_gate_failure(self) -> None:
        """Measured 111 ns exceeds the approved 100 ns plus ten-percent ceiling."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # Every scene is deliberately one nanosecond beyond its 110 ns allowed maximum.
            fixture = self.prepare_fixture(Path(temporary_directory), measured_average_nanos=111)
            exit_code = check_jvm_perf_trend.main(self.arguments(fixture))
            report = json.loads(fixture["output"].read_text(encoding="utf-8"))
            self.assertEqual(1, exit_code)
            self.assertFalse(report["overallPassed"])
            self.assertTrue(all(not check["passed"] for check in report["checks"]))

    def test_reported_average_must_equal_batch_median(self) -> None:
        """A forged summary cannot hide slower emitted batch evidence."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # Only the list summary is changed; its seven physical batch values remain centered at 100.
            fixture = self.prepare_fixture(Path(temporary_directory))
            report_text = fixture["report"].read_text(encoding="utf-8").replace(
                "scene.list_scroll.averageNanos=100",
                "scene.list_scroll.averageNanos=99",
            )
            fixture["report"].write_text(report_text, encoding="utf-8")
            exit_code = check_jvm_perf_trend.main(self.arguments(fixture))
            self.assertEqual(2, exit_code)

    def test_workload_change_requires_new_baseline(self) -> None:
        """Changing sample frames invalidates comparison instead of reusing unlike timing numbers."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # A global sample-frame change no longer matches the approved workload identity.
            fixture = self.prepare_fixture(Path(temporary_directory))
            report_text = fixture["report"].read_text(encoding="utf-8").replace(
                "sampleFrames=20",
                "sampleFrames=21",
            )
            fixture["report"].write_text(report_text, encoding="utf-8")
            exit_code = check_jvm_perf_trend.main(self.arguments(fixture))
            self.assertEqual(2, exit_code)

    def test_unapproved_baseline_is_rejected(self) -> None:
        """Candidate numbers cannot satisfy the JVM trend gate without approval metadata."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # The baseline remains structurally valid but its approval decision is withdrawn.
            fixture = self.prepare_fixture(Path(temporary_directory))
            baseline = json.loads(fixture["baseline"].read_text(encoding="utf-8"))
            baseline["approval"]["status"] = "candidate"
            self.write_json(fixture["baseline"], baseline)
            exit_code = check_jvm_perf_trend.main(self.arguments(fixture))
            self.assertEqual(2, exit_code)


if __name__ == "__main__":
    unittest.main()
