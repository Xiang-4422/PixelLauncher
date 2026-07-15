#!/usr/bin/env python3
"""验证各 CI 分组入口不会吞掉底层工具失败。"""

from __future__ import annotations

import os
import subprocess
import tempfile
import unittest
from pathlib import Path


# 仓库根目录用于定位六个 CI 分组脚本。
ROOT = Path(__file__).resolve().parents[2]
# 失败替身使用的固定退出码，便于断言没有被包装器改写为成功。
FAILURE_CODE = 23


class PixelCiEntrypointFailureTest(unittest.TestCase):
    """覆盖 Gradle 型与 Bash 聚合型 CI 入口的故障传播。"""

    def create_failing_command(self, directory: Path) -> Path:
        """在临时目录创建确定返回固定非零退出码的命令。"""

        # 失败命令路径由当前临时测试独占。
        command = directory / "fail-command.sh"
        command.write_text(
            f"#!/usr/bin/env bash\nexit {FAILURE_CODE}\n",
            encoding="utf-8",
        )
        command.chmod(0o755)
        return command

    def run_entrypoint(
        self,
        script_name: str,
        injected_variable: str,
        failing_command: Path,
        temporary_directory: Path,
    ) -> subprocess.CompletedProcess[str]:
        """执行一个注入失败工具的 CI 入口并返回完整结果。"""

        # 子进程环境继承正常工具链，仅替换当前测试目标命令。
        environment = os.environ.copy()
        environment[injected_variable] = str(failing_command)
        # publication 使用临时仓库和报告，避免故障注入删除真实构建证据。
        environment["PIXEL_COMPATIBILITY_REPOSITORY"] = str(
            temporary_directory / "repository"
        )
        environment["PIXEL_PUBLICATION_REPORT"] = str(
            temporary_directory / "publication.json"
        )
        return subprocess.run(
            ["bash", str(ROOT / "tools" / script_name)],
            cwd=ROOT,
            env=environment,
            check=False,
            text=True,
            capture_output=True,
        )

    def test_gradle_entrypoints_propagate_failure(self) -> None:
        """fast、API、Lint 与 publication 必须传播 Gradle 非零状态。"""

        # 使用同一临时失败替身覆盖四个相互独立的入口。
        with tempfile.TemporaryDirectory() as temporary_path:
            # 临时目录承载失败命令与 publication 隔离产物。
            temporary_directory = Path(temporary_path)
            # 固定失败命令模拟 Gradle 在首个必需任务报错。
            failing_command = self.create_failing_command(temporary_directory)
            for script_name in (
                "pixel-ci-fast.sh",
                "pixel-ci-api.sh",
                "pixel-ci-lint.sh",
                "pixel-publication-validation.sh",
            ):
                with self.subTest(script_name=script_name):
                    # 当前入口结果必须保留失败替身的退出码。
                    result = self.run_entrypoint(
                        script_name,
                        "PIXEL_GRADLEW_BIN",
                        failing_command,
                        temporary_directory,
                    )
                    self.assertEqual(FAILURE_CODE, result.returncode)

    def test_shell_aggregate_entrypoints_propagate_failure(self) -> None:
        """consumer 与 performance 必须在任一子门禁失败时立即失败。"""

        # 使用独立临时目录，避免和 Gradle 入口测试共享状态。
        with tempfile.TemporaryDirectory() as temporary_path:
            # 临时目录只保存失败替身。
            temporary_directory = Path(temporary_path)
            # 固定失败命令模拟首个兼容或性能子脚本报错。
            failing_command = self.create_failing_command(temporary_directory)
            for script_name in (
                "pixel-ci-consumer.sh",
                "pixel-ci-performance.sh",
            ):
                with self.subTest(script_name=script_name):
                    # 当前入口结果必须保留失败替身的退出码。
                    result = self.run_entrypoint(
                        script_name,
                        "PIXEL_BASH_BIN",
                        failing_command,
                        temporary_directory,
                    )
                    self.assertEqual(FAILURE_CODE, result.returncode)


if __name__ == "__main__":
    unittest.main()
