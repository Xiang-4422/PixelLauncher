"""验证 Release artifact 预算检查器的通过、超限和 classfile 计数路径。"""

from __future__ import annotations

import contextlib
import io
import json
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

from tools import check_pixel_artifact_budget


class PixelArtifactBudgetTest(unittest.TestCase):
    """覆盖最终 AAR 和依赖预算的机器门禁。"""

    def test_accepts_exact_artifact_and_dependency_budget(self) -> None:
        """精确依赖集合和未超限字节码应通过。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # 每个测试使用独立目录，避免产物串扰。
            root = Path(temporary_directory)
            paths = self._write_fixture(root, max_method_count=6)
            exit_code = self._run_main(paths)
            self.assertEqual(0, exit_code)
            # 报告必须证明两个 classfile 共含五个 method_info。
            report = json.loads(paths["report"].read_text(encoding="utf-8"))
            self.assertEqual("passed", report["status"])
            self.assertEqual(2, report["artifact"]["classCount"])
            self.assertEqual(5, report["artifact"]["methodCount"])

    def test_rejects_method_overflow_without_hiding_other_evidence(self) -> None:
        """方法数超限必须失败，同时保留完整观测报告。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # 四方法预算低于 fixture 的五个 method_info。
            root = Path(temporary_directory)
            paths = self._write_fixture(root, max_method_count=4)
            exit_code = self._run_main(paths)
            self.assertEqual(1, exit_code)
            report = json.loads(paths["report"].read_text(encoding="utf-8"))
            self.assertEqual("failed", report["status"])
            self.assertTrue(any("method count=5" in item for item in report["violations"]))

    def test_rejects_unknown_constant_pool_tag(self) -> None:
        """未知常量池结构不得被静默当成零方法。"""

        # tag 99 不是 JVM classfile 常量池类型。
        invalid_class = (
            struct.pack(">IHHH", check_pixel_artifact_budget.CLASS_FILE_MAGIC, 0, 61, 2)
            + struct.pack(">B", 99)
        )
        with self.assertRaisesRegex(ValueError, "未知 classfile 常量池"):
            check_pixel_artifact_budget.count_classfile_methods(invalid_class, "Invalid.class")

    def test_rejects_resolved_runtime_dependency_drift(self) -> None:
        """新增解析后依赖即使仍低于宽泛数量上限也必须失败。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # 先创建原本可通过的完整 fixture。
            root = Path(temporary_directory)
            paths = self._write_fixture(root, max_method_count=6)
            # 在排序位置加入未评审依赖，并同步放宽数量以证明精确集合门禁生效。
            paths["runtime"].write_text(
                "example:extra:1.0\nexample:runtime:1.0\n",
                encoding="utf-8",
            )
            budget = json.loads(paths["budget"].read_text(encoding="utf-8"))
            budget["dependencies"]["maxResolvedRuntimeArtifactCount"] = 2
            paths["budget"].write_text(json.dumps(budget, indent=2) + "\n", encoding="utf-8")
            exit_code = self._run_main(paths)
            self.assertEqual(1, exit_code)
            report = json.loads(paths["report"].read_text(encoding="utf-8"))
            self.assertTrue(any("artifact 集合漂移" in item for item in report["violations"]))

    def test_dependency_aar_counts_union_and_rejects_duplicate_class(self) -> None:
        """传递 AAR 必须计入总预算，且同名 class 不能被聚合坐标重复提供。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # root 隔离主 AAR、依赖 AAR 和机器报告。
            root = Path(temporary_directory)
            # paths 先创建原本可通过的主 artifact fixture。
            paths = self._write_fixture(root, max_method_count=10)
            # dependency_aar 故意重复主 AAR 的 First.class，并增加一个独立 class。
            dependency_aar = root / "dependency.aar"
            # classes_buffer 保存依赖 AAR 的最小 classes.jar。
            classes_buffer = io.BytesIO()
            with zipfile.ZipFile(classes_buffer, mode="w") as classes_archive:
                classes_archive.writestr("sample/First.class", self._minimal_class(1))
                classes_archive.writestr("sample/Third.class", self._minimal_class(1))
            with zipfile.ZipFile(dependency_aar, mode="w") as aar_archive:
                aar_archive.writestr("classes.jar", classes_buffer.getvalue())
            # dependency_aar 通过 CLI 进入聚合预算。
            paths["dependency_aar"] = dependency_aar
            # class 上限按去重并集设置为 3，确保失败只来自重复 class 契约。
            budget = json.loads(paths["budget"].read_text(encoding="utf-8"))
            budget["artifact"]["maxClassCount"] = 3
            paths["budget"].write_text(json.dumps(budget, indent=2) + "\n", encoding="utf-8")

            exit_code = self._run_main(paths)

            report = json.loads(paths["report"].read_text(encoding="utf-8"))
            self.assertEqual(1, exit_code)
            self.assertEqual(2, report["artifact"]["artifactCount"])
            self.assertEqual(3, report["artifact"]["classCount"])
            self.assertEqual(1, report["artifact"]["duplicateClassCount"])
            self.assertTrue(any("重复 class" in item for item in report["violations"]))

    def _write_fixture(self, root: Path, max_method_count: int) -> dict[str, Path]:
        """写入一个含两类、五方法和固定依赖集合的最小受检 fixture。"""

        # 内层 classes.jar 使用不压缩 classfile，便于断言结构计数。
        classes_buffer = io.BytesIO()
        with zipfile.ZipFile(classes_buffer, mode="w") as classes_archive:
            classes_archive.writestr("sample/First.class", self._minimal_class(3))
            classes_archive.writestr("sample/Second.class", self._minimal_class(2))
        # 外层 AAR 只需提供预算检查器要求的 classes.jar。
        aar_path = root / "sample.aar"
        with zipfile.ZipFile(aar_path, mode="w") as aar_archive:
            aar_archive.writestr("classes.jar", classes_buffer.getvalue())
        # 发布 POM 声明一个 compile 依赖。
        pom_path = root / "pom.xml"
        pom_path.write_text(
            """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <dependencies>
    <dependency>
      <groupId>example</groupId><artifactId>runtime</artifactId>
      <version>1.0</version><scope>compile</scope>
    </dependency>
  </dependencies>
</project>
""",
            encoding="utf-8",
        )
        # 解析后运行时 artifact 清单必须排序且精确匹配预算。
        runtime_path = root / "runtime.txt"
        runtime_path.write_text("example:runtime:1.0\n", encoding="utf-8")
        # 字节与 class 上限留足 fixture 空间，仅由参数控制方法上限。
        budget_path = root / "budget.json"
        budget_path.write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "artifact": {
                        "maxAarBytes": 100_000,
                        "maxClassCount": 2,
                        "maxMethodCount": max_method_count,
                    },
                    "dependencies": {
                        "maxPublishedRuntimeDependencyCount": 1,
                        "maxResolvedRuntimeArtifactCount": 1,
                        "expectedPublishedRuntimeDependencies": ["example:runtime:1.0"],
                        "expectedResolvedRuntimeArtifacts": ["example:runtime:1.0"],
                    },
                },
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        return {
            "aar": aar_path,
            "pom": pom_path,
            "runtime": runtime_path,
            "budget": budget_path,
            "report": root / "report.json",
        }

    def _minimal_class(self, method_count: int) -> bytes:
        """构造结构完整、无常量池条目且含指定方法数的最小 classfile。"""

        # 魔数、版本和 constant_pool_count=1 组成 classfile 头。
        result = bytearray(
            struct.pack(
                ">IHHH",
                check_pixel_artifact_budget.CLASS_FILE_MAGIC,
                0,
                61,
                1,
            ),
        )
        # access/this/super/interfaces、fields_count 均置零。
        result.extend(struct.pack(">HHHHH", 0, 0, 0, 0, 0))
        result.extend(struct.pack(">H", method_count))
        for _ in range(method_count):
            # 每个 method_info 的索引与 attributes_count 均为零。
            result.extend(struct.pack(">HHHH", 0, 0, 0, 0))
        # class attributes_count 置零。
        result.extend(struct.pack(">H", 0))
        return bytes(result)

    def _arguments(self, paths: dict[str, Path]) -> list[str]:
        """把 fixture 路径转换成检查器 CLI 参数。"""

        # arguments 保持主 AAR 参数兼容，并在 fixture 提供时追加传递 AAR。
        arguments = [
            "--aar",
            str(paths["aar"]),
            "--pom",
            str(paths["pom"]),
            "--runtime-dependencies",
            str(paths["runtime"]),
            "--budget",
            str(paths["budget"]),
            "--report",
            str(paths["report"]),
        ]
        if "dependency_aar" in paths:
            arguments.extend(("--dependency-aar", str(paths["dependency_aar"])))
        return arguments

    def _run_main(self, paths: dict[str, Path]) -> int:
        """静默执行一次 CLI，避免预期失败案例污染测试日志。"""

        # 正负向测试都通过报告断言，控制台输出无需进入单测结果。
        standard_output = io.StringIO()
        standard_error = io.StringIO()
        with contextlib.redirect_stdout(standard_output), contextlib.redirect_stderr(standard_error):
            return check_pixel_artifact_budget.main(self._arguments(paths))


if __name__ == "__main__":
    unittest.main()
