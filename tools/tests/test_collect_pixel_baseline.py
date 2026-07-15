from __future__ import annotations

import tempfile
import unittest
import zipfile
from io import BytesIO
from pathlib import Path

from tools import collect_pixel_baseline


class PixelBaselineCollectorTest(unittest.TestCase):
    """Locks the machine-readable parsers used by the reproducible M0 baseline."""

    def test_parse_kdoc_report_requires_all_numeric_fields(self) -> None:
        """The KDoc parser must preserve both measured coverage and the active gate floor."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            report = Path(temporary_directory) / "kdoc.txt"
            report.write_text(
                "publicDeclarations=100\n"
                "documentedDeclarations=75\n"
                "coveragePercent=75.00\n"
                "minimumPercent=35.00\n\n"
                "missingKdocSample:\n",
                encoding="utf-8",
            )

            parsed = collect_pixel_baseline.parse_kdoc_report(report)

            self.assertEqual(100, parsed["publicDeclarations"])
            self.assertEqual(75, parsed["documentedDeclarations"])
            self.assertEqual(75.0, parsed["coveragePercent"])

    def test_parse_perf_report_rejects_missing_samples(self) -> None:
        """A stale or truncated perf file cannot be accepted as valid baseline evidence."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            report = Path(temporary_directory) / "perf.txt"
            report.write_text("warmupFrames=3\nsampleFrames=20\n", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "Incomplete JVM performance report"):
                collect_pixel_baseline.parse_perf_report(report)

    def test_parse_perf_report_preserves_threshold_and_pass_evidence(self) -> None:
        """The collector must retain measured values, thresholds, run identity, and final status."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            report = Path(temporary_directory) / "perf.txt"
            report.write_text(
                "formatVersion=1\n"
                "runId=test-run\n"
                "thresholdScale=1.0\n"
                "warmupFrames=3\n"
                "sampleFrames=20\n"
                "sampleBatches=3\n"
                "sceneCount=1\n"
                "scene.sample.frames=20\n"
                "scene.sample.width=16\n"
                "scene.sample.height=8\n"
                "scene.sample.totalNanos=2000\n"
                "scene.sample.averageNanos=100\n"
                "scene.sample.batchAverageNanos=90,100,110\n"
                "scene.sample.baseMaxAverageNanos=500\n"
                "scene.sample.maxAverageNanos=500\n"
                "scene.sample.pass=true\n"
                "overallPass=true\n",
                encoding="utf-8",
            )

            parsed = collect_pixel_baseline.parse_perf_report(report)

            self.assertTrue(parsed["overallPassed"])
            self.assertEqual("test-run", parsed["runId"])
            self.assertEqual(500, parsed["samples"][0]["maxAverageNanos"])
            self.assertEqual([90, 100, 110], parsed["samples"][0]["batchAverageNanos"])
            self.assertTrue(parsed["samples"][0]["passed"])

    def test_inspect_release_aar_counts_classes_and_hashes_exact_artifact(self) -> None:
        """AAR inspection must read nested classes.jar instead of estimating source declarations."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            classes_buffer = BytesIO()
            with zipfile.ZipFile(classes_buffer, mode="w") as classes:
                classes.writestr("sample/First.class", b"first")
                classes.writestr("sample/Second.class", b"second")
                classes.writestr("META-INF/data", b"ignored")
            aar = root / "sample.aar"
            with zipfile.ZipFile(aar, mode="w") as archive:
                archive.writestr("classes.jar", classes_buffer.getvalue())

            inspected = collect_pixel_baseline.inspect_release_aar(aar)

            self.assertEqual(2, inspected["classCount"])
            self.assertEqual(64, len(inspected["sha256"]))
            self.assertEqual(aar.stat().st_size, inspected["bytes"])


if __name__ == "__main__":
    unittest.main()
