#!/usr/bin/env python3
"""验证 Pixel Engine 架构规模与单模块文本门禁。"""

from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


# 仓库根目录用于加载生产检查器并执行真实仓库自检。
ROOT = Path(__file__).resolve().parents[2]
# 生产架构检查器路径。
SCRIPT = ROOT / "tools/check_pixel_architecture.py"
# 动态模块规格允许测试直接调用纯函数。
SPEC = importlib.util.spec_from_file_location("check_pixel_architecture", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
# 当前加载的生产检查器模块。
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class PixelArchitectureGovernanceTest(unittest.TestCase):
    """覆盖规模超限、旧模块文本和真实仓库通过状态。"""

    def write_budget(self, root: Path, default_limit: int = 3) -> Path:
        """在临时仓库写入最小合法规模预算。"""

        # 临时预算路径与生产默认布局一致。
        budget = root / "pixel-engine/config/architecture-budget.json"
        budget.parent.mkdir(parents=True)
        budget.write_text(
            json.dumps(
                {
                    "defaultMaxProductionKotlinLines": default_limit,
                    "maxProductionFunctionLines": 220,
                    "grandfatheredProductionKotlinFiles": {},
                }
            ),
            encoding="utf-8",
        )
        return budget

    def test_rejects_production_file_over_default_limit(self) -> None:
        """未审生产文件超过统一行数上限时必须失败。"""

        with tempfile.TemporaryDirectory() as directory:
            # 临时仓库只包含一个四行 Kotlin 文件。
            root = Path(directory)
            source = root / "pixel-engine/src/main/kotlin/example/Large.kt"
            source.parent.mkdir(parents=True)
            source.write_text("package example\nclass Large\n// three\n// four\n", encoding="utf-8")
            # 三行上限故意小于实际文件。
            budget = self.write_budget(root, default_limit=3)

            # 报告必须精确指出规模超限。
            report = MODULE.check_repository(root, budget)
            self.assertEqual("failed", report["status"])
            self.assertEqual("production-kotlin-size", report["findings"][0]["kind"])

    def test_rejects_production_function_over_limit(self) -> None:
        """生产块函数超过统一行数上限时必须失败。"""

        with tempfile.TemporaryDirectory() as directory:
            # 临时仓库包含一个三行块函数。
            root = Path(directory)
            source = root / "pixel-engine/src/main/kotlin/example/Functions.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                "package example\nfun oversized() {\n    println(1)\n}\n",
                encoding="utf-8",
            )
            # 宽松文件预算确保只触发函数预算。
            budget = self.write_budget(root, default_limit=100)
            # 两行函数上限故意小于当前三行函数。
            budget.write_text(
                json.dumps(
                    {
                        "defaultMaxProductionKotlinLines": 100,
                        "maxProductionFunctionLines": 2,
                        "grandfatheredProductionKotlinFiles": {},
                    }
                ),
                encoding="utf-8",
            )

            # 报告必须给出函数名和起始行。
            report = MODULE.check_repository(root, budget)
            self.assertEqual("failed", report["status"])
            self.assertEqual("production-kotlin-function-size", report["findings"][0]["kind"])
            self.assertEqual("oversized", report["findings"][0]["function"])

    def test_rejects_removed_module_name_in_governance_text(self) -> None:
        """README 重新引用已删除模块时必须失败。"""

        with tempfile.TemporaryDirectory() as directory:
            # 临时仓库使用空生产源码根和一份错误 README。
            root = Path(directory)
            (root / "pixel-engine/src/main/kotlin").mkdir(parents=True)
            (root / "README.md").write_text("依赖 pixel-demo\n", encoding="utf-8")
            # 宽松预算把测试焦点限制在文本门禁。
            budget = self.write_budget(root, default_limit=100)

            # 报告必须保留具体旧模块名称。
            report = MODULE.check_repository(root, budget)
            self.assertEqual("failed", report["status"])
            self.assertEqual("pixel-demo", report["findings"][0]["pattern"])

    def test_current_repository_passes_architecture_governance(self) -> None:
        """受版本控制的当前仓库必须满足规模与单模块文本预算。"""

        # 真实预算文件与 CI 使用同一路径。
        budget = ROOT / "pixel-engine/config/architecture-budget.json"
        # 当前源码和文档不得依赖测试夹具才能通过。
        report = MODULE.check_repository(ROOT, budget)
        self.assertEqual([], report["findings"])
        self.assertEqual("passed", report["status"])


if __name__ == "__main__":
    unittest.main()
