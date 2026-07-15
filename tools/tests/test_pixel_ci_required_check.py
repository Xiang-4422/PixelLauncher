#!/usr/bin/env python3
"""验证 CI required 聚合器不会把失败、取消或跳过当作成功。"""

from __future__ import annotations

import subprocess
import unittest
from pathlib import Path


# 仓库根目录用于定位被测 shell 门禁。
ROOT = Path(__file__).resolve().parents[2]
# required 聚合器脚本路径。
SCRIPT = ROOT / "tools" / "pixel-ci-required-check.sh"


class PixelCiRequiredCheckTest(unittest.TestCase):
    """覆盖 required job 的成功与所有非成功终态。"""

    def run_gate(self, *results: str) -> subprocess.CompletedProcess[str]:
        """执行聚合器并返回完整退出状态与诊断输出。"""

        # 子进程结果用于同时断言退出码和可操作错误信息。
        return subprocess.run(
            ["bash", str(SCRIPT), *results],
            cwd=ROOT,
            check=False,
            text=True,
            capture_output=True,
        )

    def test_all_success_results_pass(self) -> None:
        """所有 required 上游成功时聚合器必须通过。"""

        # 七类 PR 门禁对应七个明确成功结果。
        result = self.run_gate(*(["success"] * 7))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("passed for 7 jobs", result.stdout)

    def test_every_non_success_terminal_state_fails(self) -> None:
        """failure、cancelled、skipped 均不得放行发布候选。"""

        for rejected_state in ("failure", "cancelled", "skipped"):
            with self.subTest(rejected_state=rejected_state):
                # 中间 job 的非成功状态必须被精确报告。
                result = self.run_gate("success", rejected_state, "success")
                self.assertEqual(1, result.returncode)
                self.assertIn(rejected_state, result.stderr)

    def test_missing_results_is_configuration_error(self) -> None:
        """没有声明任何上游 job 时不能产生空集合假绿。"""

        # 空输入代表 workflow needs 配置损坏。
        result = self.run_gate()
        self.assertEqual(2, result.returncode)
        self.assertIn("no job results", result.stderr)


if __name__ == "__main__":
    unittest.main()
