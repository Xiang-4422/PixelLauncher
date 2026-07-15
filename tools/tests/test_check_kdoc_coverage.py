"""验证 KDoc 扫描器不会遗漏多行、注解、protected 和构造器声明。"""

from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


# 仓库根目录用于加载受检脚本。
ROOT = Path(__file__).resolve().parents[2]
# 扫描器脚本路径保持测试不依赖 Python 包安装。
SCRIPT = ROOT / "tools" / "check_kdoc_coverage.py"
# 动态模块规格用于复用生产入口。
SPEC = importlib.util.spec_from_file_location("check_kdoc_coverage", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
# 实际加载的扫描器模块。
MODULE = importlib.util.module_from_spec(SPEC)
# dataclass 在 Python 3.9 解析延迟注解时需要先从 sys.modules 找到所属模块。
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class KdocCoverageTest(unittest.TestCase):
    """覆盖扫描完整性、文档质量和失败传播。"""

    def test_scans_annotations_multiline_protected_constructor_and_properties(self) -> None:
        """注解、多行、protected、构造器属性和同一行声明都必须计数。"""

        with tempfile.TemporaryDirectory() as directory:
            # 临时 Kotlin 源码覆盖旧扫描器曾经漏掉的形式。
            source = Path(directory) / "Sample.kt"
            source.write_text(
                """
                /** 表示可复用的示例容器。 */
                @Deprecated("fixture")
                public data class Sample /** 创建具名示例。 */ public constructor(
                    /** 对外暴露的示例名称。 */ public val name: String,
                ) {
                    /** 执行受保护的示例动作。 */
                    protected
                    open fun run(): Unit = Unit

                    /** 返回当前示例名称。 */ public fun label(): String = name
                }
                """.strip()
                + "\n",
                encoding="utf-8",
            )
            # 单文件扫描结果应该包含 class、constructor、property 和两个方法。
            declarations = MODULE.scan_kotlin_file(source)
            self.assertEqual(5, len(declarations))
            self.assertEqual(
                ["class", "constructor", "val", "fun", "fun"],
                [item.kind for item in declarations],
            )
            self.assertTrue(all(item.kdoc for item in declarations))

    def test_rejects_missing_placeholder_and_signature_only_kdoc(self) -> None:
        """缺失、待办模板和只复述名称的 KDoc 都不得计入覆盖。"""

        with tempfile.TemporaryDirectory() as directory:
            # 三个声明分别覆盖缺失、模板和签名复述。
            source_root = Path(directory) / "kotlin"
            source_root.mkdir()
            (source_root / "Invalid.kt").write_text(
                """
                public class Missing
                /** TODO 待补充。 */ public class Placeholder
                /** SignatureOnly */ public class SignatureOnly
                /** 提供可以被消费者理解的有效职责说明。 */ public class Valid
                """.strip()
                + "\n",
                encoding="utf-8",
            )
            # 有效数量只能包含最后一个声明。
            result = MODULE.collect_coverage([source_root])
            self.assertEqual(4, result.total)
            self.assertEqual(1, result.documented)
            self.assertEqual(1, len(result.missing))
            self.assertEqual(2, len(result.invalid))

    def test_accepts_class_property_tag_for_primary_constructor_property(self) -> None:
        """类 KDoc 的同名 @property 应覆盖主构造属性，但不能覆盖普通成员。"""

        with tempfile.TemporaryDirectory() as directory:
            # 主构造属性采用 Kotlin 官方 @property 归属，普通成员仍然故意缺文档。
            source = Path(directory) / "Tagged.kt"
            source.write_text(
                """
                /**
                 * 保存主题尺寸。
                 * @property width 参与布局的逻辑宽度。
                 */
                public data class Tagged(public val width: Int) {
                    public val height: Int = width
                }
                """.strip()
                + "\n",
                encoding="utf-8",
            )
            declarations = MODULE.scan_kotlin_file(source)
            # class 和主构造 width 有文档，普通成员 height 仍必须单独补充。
            self.assertEqual([True, True, False], [item.kdoc is not None for item in declarations])

    def test_main_writes_full_report_and_propagates_threshold_failure(self) -> None:
        """低于 100% 时入口返回失败并保留全部定位报告。"""

        with tempfile.TemporaryDirectory() as directory:
            # 临时源码保留一个缺失 KDoc 的 public 属性。
            root = Path(directory)
            source_root = root / "kotlin"
            source_root.mkdir()
            (source_root / "Gate.kt").write_text("public val answer: Int = 42\n", encoding="utf-8")
            # 报告路径用于验证机器可读字段。
            report = root / "report.json"
            exit_code = MODULE.main(
                [
                    "--source",
                    str(source_root),
                    "--report",
                    str(report),
                    "--minimum-percent",
                    "100",
                ]
            )
            self.assertEqual(1, exit_code)
            payload = json.loads(report.read_text(encoding="utf-8"))
            self.assertEqual(1, payload["publicProtectedDeclarations"])
            self.assertEqual(0, payload["documentedDeclarations"])
            self.assertEqual("answer", payload["missing"][0]["name"])


if __name__ == "__main__":
    unittest.main()
