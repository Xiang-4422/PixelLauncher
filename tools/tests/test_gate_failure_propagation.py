from __future__ import annotations

import os
import subprocess
import tempfile
import unittest
from pathlib import Path


class GateFailurePropagationTest(unittest.TestCase):
    """Proves shell gates preserve a failing build command instead of reporting false success."""

    # A fake Gradle executable avoids recursively launching the complete release build while still
    # exercising each script's `set -e` boundary.

    def test_soak_gate_propagates_gradle_failure(self) -> None:
        """The soak wrapper must return the injected Gradle failure code."""

        self.assertEqual(73, self.run_with_failing_gradle("tools/pixel-soak-test.sh"))

    def test_consumer_gate_propagates_publish_failure(self) -> None:
        """The consumer wrapper must stop when publishing the SDK fails."""

        self.assertEqual(73, self.run_with_failing_gradle("tools/pixel-sdk-consumer-smoke.sh"))

    def test_route_entry_gate_propagates_publish_failure(self) -> None:
        """The route-entry wrapper must stop when its isolated SDK publication fails."""

        self.assertEqual(73, self.run_with_failing_gradle("tools/pixel-route-entry-compatibility.sh"))

    def test_render_spi_gate_propagates_producer_or_consumer_failure(self) -> None:
        """The external render SPI wrapper must never continue after a failed isolated Gradle phase."""

        self.assertEqual(73, self.run_with_failing_gradle("tools/pixel-render-spi-compatibility.sh"))

    def test_consumer_matrix_gate_propagates_publish_failure(self) -> None:
        """The consumer matrix wrapper must stop when the shared SDK publication fails."""

        self.assertEqual(73, self.run_with_failing_gradle("tools/pixel-consumer-compatibility-matrix.sh"))

    def test_baseline_gate_propagates_clean_failure(self) -> None:
        """The reproducible baseline wrapper must not collect stale reports after clean fails."""

        self.assertEqual(73, self.run_with_failing_gradle("tools/pixel-baseline.sh"))

    def test_jvm_performance_gate_propagates_gradle_failure(self) -> None:
        """The JVM smoke wrapper must stop before parsing a stale report when Gradle fails."""

        self.assertEqual(73, self.run_with_failing_gradle("tools/pixel-perf-smoke.sh"))

    def run_with_failing_gradle(self, relative_script: str) -> int:
        """Run one repository script with a deterministic executable that exits with code 73."""

        repository_root = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory() as temporary_directory:
            fake_gradle = Path(temporary_directory) / "failing-gradle"
            fake_gradle.write_text("#!/usr/bin/env bash\nexit 73\n", encoding="utf-8")
            fake_gradle.chmod(0o755)
            environment = os.environ.copy()
            environment["PIXEL_GRADLEW_BIN"] = str(fake_gradle)
            result = subprocess.run(
                ["bash", str(repository_root / relative_script)],
                cwd=repository_root,
                env=environment,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                check=False,
            )
        return result.returncode


if __name__ == "__main__":
    unittest.main()
