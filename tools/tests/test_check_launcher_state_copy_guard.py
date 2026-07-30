#!/usr/bin/env python3
"""验证 Launcher 聚合状态直接 copy 的精确防增长门禁。"""

from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path
from typing import Any


# 仓库根目录用于加载生产检查器并执行真实仓库自检。
ROOT = Path(__file__).resolve().parents[2]
# 生产检查器路径。
SCRIPT = ROOT / "tools/check_launcher_state_copy_guard.py"
# 动态模块规格允许测试直接调用纯函数。
SPEC = importlib.util.spec_from_file_location(
    "check_launcher_state_copy_guard",
    SCRIPT,
)
assert SPEC is not None and SPEC.loader is not None
# 当前加载的生产检查器模块。
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class LauncherStateCopyGuardTest(unittest.TestCase):
    """覆盖真实零基线、解析负控及全部要求的 baseline 漂移。"""

    def setUp(self) -> None:
        """为每个 fixture 创建独立临时仓库。"""

        # TemporaryDirectory 在 tearDown 中统一释放。
        self.temporary_directory = tempfile.TemporaryDirectory()
        # 临时仓库根。
        self.root = Path(self.temporary_directory.name)
        # fixture 使用与生产默认值一致的源码根布局。
        self.source_root = self.root / "app/src/main/kotlin"
        self.source_root.mkdir(parents=True)
        # fixture baseline 保持在 tools 目录，模拟真实仓库结构。
        self.baseline_path = self.root / "tools/launcher-state-copy-baseline.json"
        self.baseline_path.parent.mkdir(parents=True)

    def tearDown(self) -> None:
        """释放当前测试的临时仓库。"""

        self.temporary_directory.cleanup()

    def write_source(
        self,
        source: str,
        relative: str = "app/src/main/kotlin/example/MainActivity.kt",
    ) -> Path:
        """写入单个 Kotlin fixture，并返回文件路径。"""

        # 调用者可覆盖路径以验证文件身份差异。
        source_file = self.root / relative
        source_file.parent.mkdir(parents=True, exist_ok=True)
        source_file.write_text(source, encoding="utf-8")
        return source_file

    def write_baseline(self, entries: list[dict[str, Any]]) -> None:
        """写入最小合法 baseline。"""

        # JSON 使用生产 schema，避免测试绕过真实配置校验。
        baseline = {"schemaVersion": 1, "entries": entries}
        self.baseline_path.write_text(
            json.dumps(baseline),
            encoding="utf-8",
        )

    def check(self) -> dict[str, Any]:
        """对当前临时仓库运行生产检查器。"""

        return MODULE.check_repository(
            self.root,
            self.source_root,
            self.baseline_path,
        )

    @staticmethod
    def baseline_entry(
        method: str,
        fields: list[str],
        count: int = 1,
    ) -> dict[str, Any]:
        """构造 MainActivity fixture 的一条 baseline 签名。"""

        return {
            "file": "app/src/main/kotlin/example/MainActivity.kt",
            "method": method,
            "fields": fields,
            "count": count,
        }

    def test_current_repository_rejects_all_direct_state_copy_expressions(self) -> None:
        """真实仓库必须没有任何 LauncherState 聚合直接 copy。"""

        # 真实 baseline 路径。
        baseline_path = ROOT / "tools/launcher-state-copy-baseline.json"
        # 真实 Launcher 生产源码根。
        source_root = ROOT / "app/src/main/kotlin"
        # 门禁报告必须无差异。
        report = MODULE.check_repository(ROOT, source_root, baseline_path)
        self.assertEqual("passed", report["status"])
        self.assertEqual(0, report["expectedExpressionCount"])
        self.assertEqual(0, report["actualExpressionCount"])
        self.assertEqual([], report["baseline"])
        self.assertEqual([], report["findings"])
        self.assertEqual([], report["observed"])
        # 空文件字典锁定所有生产文件均不存在聚合直接 copy。
        counts: dict[str, int] = {}
        for observed in report["observed"]:
            file_name = Path(observed["file"]).name
            counts[file_name] = counts.get(file_name, 0) + 1
        self.assertEqual({}, counts)

    def test_ignores_comments_strings_and_ordinary_data_class_copy(self) -> None:
        """注释、字符串和非 LauncherState 的 copy 不能造成误报。"""

        self.write_source(
            """
            data class LauncherState(val mode: Int)
            data class Item(val label: String)
            class MainActivity {
                private var state: LauncherState = LauncherState(0)
                fun render() {
                    val text = "state.copy(mode = 1)"
                    // state.copy(mode = 2)
                    /* LauncherStateTransitions.show(state).copy(mode = 3) */
                    val item = Item(text)
                    item.copy(label = "next")
                }
            }
            """
        )
        self.write_baseline([])

        # 只有普通 Item.copy，实际聚合状态 copy 必须为零。
        report = self.check()
        self.assertEqual("passed", report["status"])
        self.assertEqual(0, report["actualExpressionCount"])

    def test_recognizes_transition_result_chain(self) -> None:
        """规范 transition 结果后的链式 copy 必须纳入聚合写入。"""

        self.write_source(
            """
            data class LauncherState(val mode: Int)
            class MainActivity {
                private var state: LauncherState = LauncherState(0)
                fun navigate() {
                    state = LauncherStateTransitions.showHome(
                        state,
                    ).copy(mode = 1)
                }
            }
            """
        )
        self.write_baseline([self.baseline_entry("navigate", ["mode"])])

        # 接收者类型必须明确标记为 transition 链。
        report = self.check()
        self.assertEqual("passed", report["status"])
        self.assertEqual("transition-chain", report["observed"][0]["receiverKind"])

    def test_recognizes_direct_and_inferred_launcher_state_aliases(self) -> None:
        """直接 copy 赋值及从 state 传播出的简单别名都不能绕过门禁。"""

        self.write_source(
            """
            data class LauncherState(val mode: Int)
            class MainActivity {
                private var state: LauncherState = LauncherState(0)
                fun aliasWrite() {
                    val snapshot = state
                    val next = snapshot.copy(mode = 1)
                    state = next
                }
            }
            """
        )
        self.write_baseline([])

        # snapshot 由显式 LauncherState state 推导，必须报告未授权写入。
        report = self.check()
        self.assertEqual("failed", report["status"])
        self.assertEqual("unexpected-copy", report["findings"][0]["kind"])
        self.assertIn("aliasWrite", report["findings"][0]["message"])
        self.assertIn("mode", report["findings"][0]["message"])

    def test_rejects_unauthorized_expression_with_file_method_and_fields(self) -> None:
        """新增未授权表达式必须报告准确文件、方法和字段。"""

        self.write_source(
            """
            data class LauncherState(val mode: Int, val query: String)
            class MainActivity {
                private var state: LauncherState = LauncherState(0, "")
                fun allowed() {
                    state = state.copy(mode = 1)
                }
                fun added() {
                    val next = state.copy(query = "x")
                    state = next
                }
            }
            """
        )
        self.write_baseline([self.baseline_entry("allowed", ["mode"])])

        # 新增入口不能被已有方法的 allowlist 泛化放过。
        report = self.check()
        self.assertEqual("failed", report["status"])
        self.assertEqual("unexpected-copy", report["findings"][0]["kind"])
        self.assertIn("MainActivity.kt::added", report["findings"][0]["message"])
        self.assertIn("query", report["findings"][0]["message"])

    def test_rejects_new_field_on_existing_expression(self) -> None:
        """现有表达式扩大字段集合时必须指出新增字段。"""

        self.write_source(
            """
            data class LauncherState(val mode: Int, val query: String)
            class MainActivity {
                private var state: LauncherState = LauncherState(0, "")
                fun allowed() {
                    state = state.copy(mode = 1, query = "x")
                }
            }
            """
        )
        self.write_baseline([self.baseline_entry("allowed", ["mode"])])

        # 同文件同方法优先配对为字段变化。
        report = self.check()
        self.assertEqual("failed", report["status"])
        finding = report["findings"][0]
        self.assertEqual("field-set-changed", finding["kind"])
        self.assertEqual(["query"], finding["addedFields"])
        self.assertIn("MainActivity.kt::allowed", finding["message"])

    def test_rejects_expression_moved_to_unapproved_method(self) -> None:
        """字段不变但方法变化时必须报告位置移动。"""

        self.write_source(
            """
            data class LauncherState(val mode: Int)
            class MainActivity {
                private var state: LauncherState = LauncherState(0)
                fun moved() {
                    state = state.copy(mode = 1)
                }
            }
            """
        )
        self.write_baseline([self.baseline_entry("allowed", ["mode"])])

        # 同文件同字段集合应明确给出预期与实际方法。
        report = self.check()
        self.assertEqual("failed", report["status"])
        finding = report["findings"][0]
        self.assertEqual("method-changed", finding["kind"])
        self.assertEqual("allowed", finding["expectedMethod"])
        self.assertEqual("moved", finding["actualMethod"])

    def test_rejects_duplicate_expression(self) -> None:
        """授权方法内重复相同字段集合也必须失败。"""

        self.write_source(
            """
            data class LauncherState(val mode: Int)
            class MainActivity {
                private var state: LauncherState = LauncherState(0)
                fun allowed() {
                    state = state.copy(mode = 1)
                    state = state.copy(mode = 2)
                }
            }
            """
        )
        self.write_baseline([self.baseline_entry("allowed", ["mode"])])

        # 完全相同签名的增加应报告数量变化。
        report = self.check()
        self.assertEqual("failed", report["status"])
        finding = report["findings"][0]
        self.assertEqual("expression-count-increased", finding["kind"])
        self.assertEqual(1, finding["expectedCount"])
        self.assertEqual(2, finding["actualCount"])

    def test_rejects_deleted_expression_when_baseline_was_not_reduced(self) -> None:
        """只删生产代码而忘记同步缩减 baseline 时必须失败。"""

        self.write_source(
            """
            data class LauncherState(val mode: Int)
            class MainActivity {
                private var state: LauncherState = LauncherState(0)
                fun allowed() {
                    render()
                }
                fun render() = Unit
            }
            """
        )
        self.write_baseline([self.baseline_entry("allowed", ["mode"])])

        # baseline 中残留的签名必须明确报告缺失。
        report = self.check()
        self.assertEqual("failed", report["status"])
        finding = report["findings"][0]
        self.assertEqual("missing-copy", finding["kind"])
        self.assertIn("baseline 尚未缩减", finding["message"])
        self.assertIn("allowed", finding["message"])


if __name__ == "__main__":
    unittest.main()
