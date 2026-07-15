from __future__ import annotations

import io
import json
import tempfile
import unittest
from contextlib import redirect_stderr
from pathlib import Path

from tools import check_pixel_artifact_boundaries


class PixelArtifactBoundariesTest(unittest.TestCase):
    """验证 artifact 归属、无环依赖和平台边界门禁的失败传播。"""

    def write_manifest(
        self,
        root: Path,
        *,
        artifacts: dict[str, dict[str, object]],
        path_prefixes: list[dict[str, str]],
        files: dict[str, str] | None = None,
        additional_source_roots: list[str] | None = None,
    ) -> Path:
        """在临时仓库写入最小 schemaVersion=1 清单。"""

        # manifest_path 与临时 sourceRoot 同级，便于验证相对路径解析不依赖当前目录。
        manifest_path = root / "artifact-ownership.json"
        # payload 只包含测试场景需要的稳定字段。
        payload = {
            "schemaVersion": 1,
            "repositoryRoot": ".",
            "sourceRoot": "src",
            "additionalSourceRoots": additional_source_roots or [],
            "projectImportPrefixes": ["sample."],
            "artifacts": artifacts,
            "minimalArtifacts": [],
            "forbiddenMinimalDependencies": [],
            "ownership": {
                "pathPrefixes": path_prefixes,
                "files": files or {},
            },
            "platformImportExceptions": [],
            "splitPackages": {},
        }
        manifest_path.write_text(json.dumps(payload), encoding="utf-8")
        return manifest_path

    def test_clean_acyclic_graph_passes(self) -> None:
        """唯一归属且 runtime 只依赖 core 时应生成通过报告。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # root 隔离测试源码、清单和报告。
            root = Path(temporary_directory)
            # core_source 提供被 runtime 显式导入的顶层类型。
            core_source = root / "src/core/CoreValue.kt"
            core_source.parent.mkdir(parents=True)
            core_source.write_text("package sample.core\n\npublic class CoreValue\n", encoding="utf-8")
            # runtime_source 建立一条合法直接依赖边。
            runtime_source = root / "src/runtime/RuntimeValue.kt"
            runtime_source.parent.mkdir(parents=True)
            runtime_source.write_text(
                "package sample.runtime\n\nimport sample.core.CoreValue\n\npublic class RuntimeValue(val value: CoreValue)\n",
                encoding="utf-8",
            )
            # manifest 声明与源码一致的无环图。
            manifest = self.write_manifest(
                root,
                artifacts={
                    "core": {"dependencies": []},
                    "runtime": {"dependencies": ["core"]},
                },
                path_prefixes=[
                    {"path": "core", "artifact": "core"},
                    {"path": "runtime", "artifact": "runtime"},
                ],
            )
            # report 是门禁的机器可读验收证据。
            report = root / "report.json"

            exit_code = check_pixel_artifact_boundaries.main(
                ["--manifest", str(manifest), "--report", str(report)],
            )

            payload = json.loads(report.read_text(encoding="utf-8"))
            self.assertEqual(0, exit_code)
            self.assertEqual("passed", payload["status"])
            self.assertEqual({"core": 1, "runtime": 1}, payload["artifactFileCounts"])
            self.assertEqual(1, len(payload["observedSourceEdges"]))

    def test_additional_java_source_root_is_owned_and_audited(self) -> None:
        """附加 Java 根中的精确归属文件必须计数并解析分号形式 import。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # root 同时模拟主 Kotlin 根和冻结 Java 参考实现根。
            root = Path(temporary_directory)
            # core_source 是 Java 参考实现导入的项目内符号。
            core_source = root / "src/core/CoreValue.kt"
            core_source.parent.mkdir(parents=True)
            core_source.write_text("package sample.core\n\npublic class CoreValue\n", encoding="utf-8")
            # java_source 使用 Java package/import 分号，验证不会被 Kotlin-only 正则漏掉。
            java_source = root / "java/bidi/Reference.java"
            java_source.parent.mkdir(parents=True)
            java_source.write_text(
                "package sample.bidi;\n\n"
                "import sample.core.CoreValue;\n\n"
                "public final class Reference { private CoreValue value; }\n",
                encoding="utf-8",
            )
            # Java 文件由精确覆盖归属 runtime，且 runtime 合法依赖 core。
            manifest = self.write_manifest(
                root,
                artifacts={
                    "core": {"dependencies": []},
                    "runtime": {"dependencies": ["core"]},
                },
                path_prefixes=[{"path": "core", "artifact": "core"}],
                files={"bidi/Reference.java": "runtime"},
                additional_source_roots=["java"],
            )
            # report 必须把 Java 文件纳入 owner 计数与源码边证据。
            report = root / "report.json"

            exit_code = check_pixel_artifact_boundaries.main(
                ["--manifest", str(manifest), "--report", str(report)],
            )

            payload = json.loads(report.read_text(encoding="utf-8"))
            self.assertEqual(0, exit_code)
            self.assertEqual({"core": 1, "runtime": 1}, payload["artifactFileCounts"])
            self.assertEqual(2, payload["sourceFileCount"])
            self.assertEqual("sample.core.CoreValue", payload["observedSourceEdges"][0]["import"])

    def test_declared_cycle_fails(self) -> None:
        """即使没有源码 import，清单中的 artifact 环也必须返回非零。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # root 只需一个已归属文件即可运行完整门禁。
            root = Path(temporary_directory)
            # source 避免零文件配置掩盖循环检测。
            source = root / "src/a/A.kt"
            source.parent.mkdir(parents=True)
            source.write_text("package sample.a\n\npublic class A\n", encoding="utf-8")
            # manifest 故意声明 a -> b -> a。
            manifest = self.write_manifest(
                root,
                artifacts={
                    "a": {"dependencies": ["b"]},
                    "b": {"dependencies": ["a"]},
                },
                path_prefixes=[{"path": "a", "artifact": "a"}],
            )
            # report 保留精确循环路径。
            report = root / "report.json"
            # stderr 是预期失败摘要，不应污染测试输出。
            stderr = io.StringIO()

            with redirect_stderr(stderr):
                exit_code = check_pixel_artifact_boundaries.main(
                    ["--manifest", str(manifest), "--report", str(report)],
                )

            payload = json.loads(report.read_text(encoding="utf-8"))
            categories = {finding["category"] for finding in payload["findings"]}
            self.assertEqual(1, exit_code)
            self.assertIn("DEPENDENCY_CYCLE", categories)
            self.assertIn("发现", stderr.getvalue())

    def test_undeclared_edge_and_platform_leak_fail_together(self) -> None:
        """未声明项目依赖和 runtime Android import 应在同一报告中同时失败。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # root 隔离故意非法的 Kotlin fixture。
            root = Path(temporary_directory)
            # core_source 提供项目内导入目标。
            core_source = root / "src/core/CoreValue.kt"
            core_source.parent.mkdir(parents=True)
            core_source.write_text("package sample.core\n\npublic class CoreValue\n", encoding="utf-8")
            # runtime_source 同时跨越未声明 artifact 边并导入 Android UI。
            runtime_source = root / "src/runtime/RuntimeValue.kt"
            runtime_source.parent.mkdir(parents=True)
            runtime_source.write_text(
                "package sample.runtime\n\n"
                "import android.view.View\n"
                "import sample.core.CoreValue\n\n"
                "public class RuntimeValue(val value: CoreValue, val view: View)\n",
                encoding="utf-8",
            )
            # manifest 故意不让 runtime 依赖 core，也不授予平台 import。
            manifest = self.write_manifest(
                root,
                artifacts={
                    "core": {"dependencies": []},
                    "runtime": {"dependencies": []},
                },
                path_prefixes=[
                    {"path": "core", "artifact": "core"},
                    {"path": "runtime", "artifact": "runtime"},
                ],
            )
            # report 用于断言两类 finding 都没有被短路。
            report = root / "report.json"

            with redirect_stderr(io.StringIO()):
                exit_code = check_pixel_artifact_boundaries.main(
                    ["--manifest", str(manifest), "--report", str(report)],
                )

            payload = json.loads(report.read_text(encoding="utf-8"))
            categories = {finding["category"] for finding in payload["findings"]}
            self.assertEqual(1, exit_code)
            self.assertIn("UNDECLARED_SOURCE_DEPENDENCY", categories)
            self.assertIn("PLATFORM_IMPORT_LEAK", categories)

    def test_unowned_source_and_stale_override_fail(self) -> None:
        """新增未归属文件和重构后遗留精确覆盖都不得被静默忽略。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # root 同时容纳真实未归属文件和不存在的覆盖路径。
            root = Path(temporary_directory)
            # source 不匹配任何 pathPrefix。
            source = root / "src/orphan/Orphan.kt"
            source.parent.mkdir(parents=True)
            source.write_text("package sample.orphan\n\npublic class Orphan\n", encoding="utf-8")
            # manifest 仅声明另一个目录，并保留一个不存在的 exact override。
            manifest = self.write_manifest(
                root,
                artifacts={"core": {"dependencies": []}},
                path_prefixes=[{"path": "core", "artifact": "core"}],
                files={"removed/Old.kt": "core"},
            )
            # report 必须包含两类所有权错误。
            report = root / "report.json"

            with redirect_stderr(io.StringIO()):
                exit_code = check_pixel_artifact_boundaries.main(
                    ["--manifest", str(manifest), "--report", str(report)],
                )

            payload = json.loads(report.read_text(encoding="utf-8"))
            categories = {finding["category"] for finding in payload["findings"]}
            self.assertEqual(1, exit_code)
            self.assertIn("UNOWNED_SOURCE", categories)
            self.assertIn("STALE_OWNERSHIP_OVERRIDE", categories)


if __name__ == "__main__":
    unittest.main()
