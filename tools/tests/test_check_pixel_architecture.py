#!/usr/bin/env python3
"""验证 Pixel Engine 函数规模、模块契约与历史文本门禁。"""

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
    """覆盖函数超限、模块漂移、旧模块文本和真实仓库通过状态。"""

    def write_architecture_contract(self, root: Path) -> None:
        """在临时仓库写入当前五模块及依赖图契约。"""

        # 临时 settings 使用单次多参数 include，覆盖生产解析器支持的标准写法。
        (root / "settings.gradle.kts").write_text(
            'include(":app", ":pixel-engine", ":showcase", ":showcase-desktop", ":lockscreen-module")\n',
            encoding="utf-8",
        )
        # 关键文档共享同一模块清单受控区段。
        module_block = "\n".join(
            (
                MODULE.MODULE_CONTRACT_START,
                *(f"- `{module}`" for module in MODULE.EXPECTED_GRADLE_MODULES),
                MODULE.MODULE_CONTRACT_END,
            )
        )
        # 两份总览还共享同一依赖图受控区段。
        dependency_block = "\n".join(
            (
                MODULE.DEPENDENCY_CONTRACT_START,
                "```text",
                *MODULE.EXPECTED_DEPENDENCY_CONTRACT_LINES,
                "```",
                MODULE.DEPENDENCY_CONTRACT_END,
            )
        )
        for relative in MODULE.MODULE_CONTRACT_DOCUMENTS:
            # 当前临时文档是否同时承担依赖图契约。
            contract = module_block
            if relative in MODULE.DEPENDENCY_CONTRACT_DOCUMENTS:
                contract += f"\n{dependency_block}"
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(contract + "\n", encoding="utf-8")

    def write_budget(self, root: Path, function_limit: int = 220) -> Path:
        """在临时仓库写入最小合法规模预算。"""

        self.write_architecture_contract(root)
        # 临时预算路径与生产默认布局一致。
        budget = root / "pixel-engine/config/architecture-budget.json"
        budget.parent.mkdir(parents=True)
        budget.write_text(
            json.dumps(
                {
                    "maxProductionFunctionLines": function_limit,
                }
            ),
            encoding="utf-8",
        )
        return budget

    def test_accepts_large_file_when_functions_remain_bounded(self) -> None:
        """大文件只要没有超长函数，就不应被机械行数门禁拒绝。"""

        with tempfile.TemporaryDirectory() as directory:
            # 临时仓库包含远超旧 1200 行上限、但没有长函数的 Kotlin 文件。
            root = Path(directory)
            source = root / "pixel-engine/src/main/kotlin/example/Large.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                "package example\n"
                + "\n".join(f"internal const val VALUE_{index} = {index}" for index in range(1_300)),
                encoding="utf-8",
            )
            # 函数预算仍保留，但当前文件没有块函数。
            budget = self.write_budget(root)

            # 文件总行数不再决定架构门禁结果。
            report = MODULE.check_repository(root, budget)
            self.assertEqual([], report["findings"])
            self.assertEqual("passed", report["status"])

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
            # 两行函数上限故意小于当前三行函数。
            budget = self.write_budget(root, function_limit=2)

            # 报告必须给出函数名和起始行。
            report = MODULE.check_repository(root, budget)
            self.assertEqual("failed", report["status"])
            self.assertEqual("production-kotlin-function-size", report["findings"][0]["kind"])
            self.assertEqual("oversized", report["findings"][0]["function"])

    def test_rejects_removed_module_name_in_governance_text(self) -> None:
        """README 重新引用已删除模块时必须失败。"""

        with tempfile.TemporaryDirectory() as directory:
            # 临时仓库先建立完整契约，再向 README 注入错误历史模块名称。
            root = Path(directory)
            (root / "pixel-engine/src/main/kotlin").mkdir(parents=True)
            # 当前预算把测试焦点限制在文本门禁。
            budget = self.write_budget(root)
            readme = root / "README.md"
            readme.write_text(
                readme.read_text(encoding="utf-8") + "依赖 pixel-demo\n",
                encoding="utf-8",
            )

            # 报告必须保留具体旧模块名称。
            report = MODULE.check_repository(root, budget)
            self.assertEqual("failed", report["status"])
            stale_finding = next(
                finding
                for finding in report["findings"]
                if finding["kind"] == "stale-architecture-text"
            )
            self.assertEqual("pixel-demo", stale_finding["pattern"])

    def test_accepts_current_five_module_contract(self) -> None:
        """Gradle 清单及关键文档一致声明五模块时必须通过。"""

        with tempfile.TemporaryDirectory() as directory:
            # 空生产源码使测试只验证模块契约的正向路径；Gradle 声明顺序不应影响结果。
            root = Path(directory)
            (root / "pixel-engine/src/main/kotlin").mkdir(parents=True)
            budget = self.write_budget(root)
            (root / "settings.gradle.kts").write_text(
                'include(":lockscreen-module", ":showcase-desktop", ":showcase", ":pixel-engine", ":app")\n',
                encoding="utf-8",
            )
            # 受控区段外的解释文字可重复引用模块和依赖，不应污染契约解析。
            readme = root / "README.md"
            readme.write_text(
                readme.read_text(encoding="utf-8")
                + "解释文字可引用 `:app`，也可再次说明 :app -> :pixel-engine。\n",
                encoding="utf-8",
            )

            report = MODULE.check_repository(root, budget)
            self.assertEqual([], report["findings"])
            self.assertEqual("passed", report["status"])

    def test_gradle_module_parser_ignores_line_and_block_comments(self) -> None:
        """注释中的 include 不得进入真实模块清单，字符串内注释符必须保留。"""

        # 真实 include 混合单参数和多参数写法，注释还包含嵌套块与伪模块声明。
        settings_text = """
            include(":app")
            val endpoint = "https://example.invalid/*literal*/"
            val description = "include(\":string-only\")"
            // include(":commented-line")
            /* include(":commented-block")
               /* include(":nested-comment") */
            */
            include(":pixel-engine", ":showcase", ":showcase-desktop", ":lockscreen-module")
        """

        modules = MODULE.declared_gradle_modules(settings_text)
        self.assertEqual(MODULE.EXPECTED_GRADLE_MODULES, modules)

    def test_rejects_commented_out_gradle_module(self) -> None:
        """settings 仅在注释中保留锁屏模块时必须报告清单漂移。"""

        with tempfile.TemporaryDirectory() as directory:
            # 完整文档保持不变，锁屏模块的 include 被行注释移除。
            root = Path(directory)
            (root / "pixel-engine/src/main/kotlin").mkdir(parents=True)
            budget = self.write_budget(root)
            (root / "settings.gradle.kts").write_text(
                'include(":app", ":pixel-engine", ":showcase")\n'
                'include(":showcase-desktop")\n'
                '// include(":lockscreen-module")\n'
                '/* include(":commented-block") */\n',
                encoding="utf-8",
            )

            report = MODULE.check_repository(root, budget)
            finding = next(
                finding for finding in report["findings"] if finding["kind"] == "gradle-module-list"
            )
            self.assertEqual(":showcase-desktop", finding["actual"][-1])
            self.assertEqual(":lockscreen-module", finding["expected"][-1])

    def test_rejects_key_document_module_list_drift(self) -> None:
        """关键架构文档的受控模块区段漏项时必须失败。"""

        with tempfile.TemporaryDirectory() as directory:
            # 项目总览故意从受控区段移除锁屏模块。
            root = Path(directory)
            (root / "pixel-engine/src/main/kotlin").mkdir(parents=True)
            budget = self.write_budget(root)
            overview = root / "docs/项目总览.md"
            overview.write_text(
                overview.read_text(encoding="utf-8").replace("- `:lockscreen-module`\n", ""),
                encoding="utf-8",
            )

            report = MODULE.check_repository(root, budget)
            finding = next(
                finding
                for finding in report["findings"]
                if finding["kind"] == "documented-module-list"
            )
            self.assertEqual("docs/项目总览.md", finding["path"])
            self.assertNotIn(":lockscreen-module", finding["actual"])

    def test_rejects_missing_or_duplicate_module_contract_marker(self) -> None:
        """关键文档模块区段缺少或重复边界标记时必须失败。"""

        for scenario in ("missing-end", "duplicate-start"):
            with self.subTest(scenario=scenario), tempfile.TemporaryDirectory() as directory:
                # Engine README 用于隔离两种不明确区段结构。
                root = Path(directory)
                (root / "pixel-engine/src/main/kotlin").mkdir(parents=True)
                budget = self.write_budget(root)
                readme = root / "pixel-engine/README.md"
                text = readme.read_text(encoding="utf-8")
                if scenario == "missing-end":
                    text = text.replace(MODULE.MODULE_CONTRACT_END, "")
                else:
                    text = text.replace(
                        MODULE.MODULE_CONTRACT_START,
                        f"{MODULE.MODULE_CONTRACT_START}\n{MODULE.MODULE_CONTRACT_START}",
                    )
                readme.write_text(text, encoding="utf-8")

                report = MODULE.check_repository(root, budget)
                finding = next(
                    finding
                    for finding in report["findings"]
                    if finding["kind"] == "documented-module-list"
                )
                self.assertEqual("pixel-engine/README.md", finding["path"])
                self.assertEqual("missing-or-ambiguous-contract-block", finding["reason"])

    def test_rejects_documented_dependency_drift(self) -> None:
        """总览依赖图区段遗漏桌面共享源码关系时必须失败。"""

        with tempfile.TemporaryDirectory() as directory:
            # 根 README 只删掉一条特殊消费关系，隔离依赖图负向路径。
            root = Path(directory)
            (root / "pixel-engine/src/main/kotlin").mkdir(parents=True)
            budget = self.write_budget(root)
            readme = root / "README.md"
            missing_line = ":showcase-desktop --shared scene sources--> :showcase\n"
            readme.write_text(
                readme.read_text(encoding="utf-8").replace(missing_line, ""),
                encoding="utf-8",
            )

            report = MODULE.check_repository(root, budget)
            finding = next(
                finding
                for finding in report["findings"]
                if finding["kind"] == "documented-module-dependencies"
            )
            self.assertEqual("README.md", finding["path"])
            self.assertNotIn(missing_line.strip(), finding["actual"])

    def test_current_repository_passes_architecture_governance(self) -> None:
        """受版本控制的当前仓库必须满足函数规模、模块契约与历史文本预算。"""

        # 真实预算文件与 CI 使用同一路径。
        budget = ROOT / "pixel-engine/config/architecture-budget.json"
        # 当前源码和文档不得依赖测试夹具才能通过。
        report = MODULE.check_repository(ROOT, budget)
        self.assertEqual([], report["findings"])
        self.assertEqual("passed", report["status"])


if __name__ == "__main__":
    unittest.main()
