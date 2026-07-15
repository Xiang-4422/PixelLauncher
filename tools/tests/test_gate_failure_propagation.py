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

    def test_runtime_consumer_gate_propagates_publish_failure(self) -> None:
        """The runtime-only consumer wrapper must stop when publishing its artifacts fails."""

        self.assertEqual(73, self.run_with_failing_gradle("tools/pixel-runtime-consumer-smoke.sh"))

    def test_widgets_consumer_gate_propagates_publish_failure(self) -> None:
        """widgets 独立消费者必须在发布失败时立即停止。"""

        self.assertEqual(73, self.run_with_failing_gradle("tools/pixel-widgets-consumer-smoke.sh"))

    def test_navigation_consumer_gate_propagates_publish_failure(self) -> None:
        """navigation 独立消费者必须在发布失败时立即停止。"""

        self.assertEqual(73, self.run_with_failing_gradle("tools/pixel-navigation-consumer-smoke.sh"))

    def test_android_consumer_gate_propagates_publish_failure(self) -> None:
        """android 独立消费者必须在发布失败时立即停止。"""

        self.assertEqual(73, self.run_with_failing_gradle("tools/pixel-android-consumer-smoke.sh"))

    def test_testing_consumer_gate_propagates_publish_failure(self) -> None:
        """testing 独立消费者必须在发布失败时立即停止。"""

        self.assertEqual(73, self.run_with_failing_gradle("tools/pixel-testing-consumer-smoke.sh"))

    def test_debug_consumer_gate_propagates_publish_failure(self) -> None:
        """debug 独立消费者必须在发布失败时立即停止。"""

        self.assertEqual(73, self.run_with_failing_gradle("tools/pixel-debug-consumer-smoke.sh"))

    def test_compose_consumer_gate_propagates_publish_failure(self) -> None:
        """compose 可选边界消费者必须在发布失败时立即停止。"""

        self.assertEqual(73, self.run_with_failing_gradle("tools/pixel-compose-consumer-smoke.sh"))

    def test_isolated_consumer_coordinate_assertions_follow_stable_version(self) -> None:
        """隔离消费者的传递坐标断言必须跟随 1.0.0 正式版本。"""

        # 仓库根用于读取真实发布门禁脚本，避免测试复制另一份版本常量。
        repository_root = Path(__file__).resolve().parents[2]
        # 这些脚本都会检查当前坐标的全部必需传递依赖。
        scripts = (
            "pixel-runtime-consumer-smoke.sh",
            "pixel-widgets-consumer-smoke.sh",
            "pixel-navigation-consumer-smoke.sh",
            "pixel-android-consumer-smoke.sh",
            "pixel-testing-consumer-smoke.sh",
            "pixel-debug-consumer-smoke.sh",
            "pixel-compose-consumer-smoke.sh",
        )
        for script in scripts:
            # 脚本文本必须拒绝继续使用内部 snapshot 坐标作为正式传递依赖断言。
            source = (repository_root / "tools" / script).read_text(encoding="utf-8")
            # 双引号 shell 正则需要双反斜线；归一化后统一比较实际传给 rg 的模式。
            normalized_source = source.replace("\\\\", "\\")
            with self.subTest(script=script):
                self.assertNotIn("0\\.1\\.0-SNAPSHOT", normalized_source)
                self.assertIn("1\\.0\\.0", normalized_source)

    def test_route_entry_gate_propagates_publish_failure(self) -> None:
        """The route-entry wrapper must stop when its isolated SDK publication fails."""

        self.assertEqual(73, self.run_with_failing_gradle("tools/pixel-route-entry-compatibility.sh"))

    def test_previous_binary_gate_propagates_producer_or_consumer_failure(self) -> None:
        """The old-binary wrapper must never continue after a failed isolated Gradle phase."""

        self.assertEqual(73, self.run_with_failing_gradle("tools/pixel-previous-binary-compatibility.sh"))

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
