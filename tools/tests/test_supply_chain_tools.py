"""验证 SBOM、POM/产物边界和 OSV 严重度门禁的关键纯函数。"""

from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path


# 仓库根目录用于动态加载未安装的生产脚本。
ROOT = Path(__file__).resolve().parents[2]


def load_script(module_name: str, script_name: str):
    """从 tools 目录加载一个生产脚本模块。"""

    # 当前生产脚本的绝对路径。
    script = ROOT / "tools" / script_name
    # importlib 模块规格避免依赖 PYTHONPATH。
    spec = importlib.util.spec_from_file_location(module_name, script)
    assert spec is not None and spec.loader is not None
    # 由规格创建的实际模块对象。
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


# SBOM/来源生成器生产模块。
GENERATOR = load_script("generate_pixel_supply_chain", "generate_pixel_supply_chain.py")
# 发布内容与签名检查器生产模块。
CHECKER = load_script("check_pixel_supply_chain", "check_pixel_supply_chain.py")
# OSV 扫描器生产模块。
OSV = load_script("scan_pixel_osv", "scan_pixel_osv.py")


class SupplyChainToolsTest(unittest.TestCase):
    """覆盖确定性文档、元数据拒绝和漏洞严重度计算。"""

    def test_properties_reject_duplicate_key(self) -> None:
        """重复发布字段必须失败，不能让 Gradle/Python 读取结果分叉。"""

        with tempfile.TemporaryDirectory() as directory:
            # 含重复 projectUrl 的临时 properties。
            metadata = Path(directory) / "release.properties"
            metadata.write_text("projectUrl=a\nprojectUrl=b\n", encoding="utf-8")
            with self.assertRaisesRegex(AssertionError, "duplicate property"):
                GENERATOR.read_properties(metadata)

    def test_sbom_contains_all_internal_components_and_stable_serial(self) -> None:
        """同一依赖图必须生成相同序列号并保留全部 SDK 坐标。"""

        # 最小依赖图包含全部内部组件和一个外部依赖。
        components = [
            {
                "group": "com.purride",
                "name": artifact,
                "version": "1.0.0",
                "purl": f"pkg:maven/com.purride/{artifact}@1.0.0",
                "scope": "required",
            }
            for artifact in GENERATOR.PIXEL_ARTIFACTS
        ]
        components.append(
            {
                "group": "org.example",
                "name": "dependency",
                "version": "1.2.3",
                "purl": "pkg:maven/org.example/dependency@1.2.3",
                "scope": "required",
            },
        )
        # 未确认许可证不得被生成器猜测。
        metadata = {
            "projectUrl": "https://example.invalid/project",
            "scmUrl": "https://example.invalid/project",
            "issueUrl": "https://example.invalid/project/issues",
            "licenseStatus": "UNCONFIRMED",
        }
        # 依赖图只需要一个外部到内部的示例边。
        graph = {"components": components, "dependencies": []}
        first = GENERATOR.build_sbom(graph, metadata, "1.0.0")
        second = GENERATOR.build_sbom(graph, metadata, "1.0.0")
        self.assertEqual(first["serialNumber"], second["serialNumber"])
        self.assertEqual("1.7", first["specVersion"])
        self.assertFalse(any("licenses" in component for component in first["components"]))

    def test_checksum_validation_detects_tampering(self) -> None:
        """主体文件改变后旧 checksum 旁车必须立即失败。"""

        with tempfile.TemporaryDirectory() as directory:
            # 临时发布主体。
            target = Path(directory) / "artifact.aar"
            target.write_bytes(b"trusted")
            GENERATOR.write_checksum_sidecars(target)
            self.assertEqual(4, len(CHECKER.validate_checksums(target)))
            target.write_bytes(b"tampered")
            with self.assertRaisesRegex(AssertionError, "invalid md5 checksum"):
                CHECKER.validate_checksums(target)

    def test_repository_license_requires_pinned_exact_content(self) -> None:
        """许可证正文必须匹配受审 SHA-256，任意占位内容不能通过正式门禁。"""

        with tempfile.TemporaryDirectory() as directory:
            # 临时许可证正文模拟仓库根 LICENSE。
            license_file = Path(directory) / "LICENSE"
            license_file.write_bytes(b"reviewed license\n")
            # 元数据只保存完整小写 SHA-256，不依赖文件名或文本猜测许可证。
            metadata = {
                "licenseFileSha256": CHECKER.hash_file(license_file, "sha256"),
            }
            report = CHECKER.validate_repository_license(license_file, metadata)
            self.assertEqual(metadata["licenseFileSha256"], report["sha256"])
            license_file.write_bytes(b"tampered license\n")
            with self.assertRaisesRegex(AssertionError, "Repository LICENSE sha256"):
                CHECKER.validate_repository_license(license_file, metadata)

    def test_repository_license_rejects_missing_pinned_digest(self) -> None:
        """已确认许可证缺少完整强摘要时必须失败，不能只检查文件存在。"""

        with tempfile.TemporaryDirectory() as directory:
            # 任意存在的 LICENSE 文件不能替代受审摘要。
            license_file = Path(directory) / "LICENSE"
            license_file.write_bytes(b"unreviewed\n")
            with self.assertRaisesRegex(AssertionError, "valid licenseFileSha256"):
                CHECKER.validate_repository_license(license_file, {})

    def test_repository_notice_matches_pinned_digest_and_rejects_tampering(self) -> None:
        """正式 NOTICE 必须匹配受审摘要，篡改归属声明后立即失败。"""

        with tempfile.TemporaryDirectory() as directory:
            # notice_file 模拟包含项目与 Unicode 归属的仓库 NOTICE。
            notice_file = Path(directory) / "NOTICE"
            notice_file.write_bytes(b"reviewed notice\n")
            # metadata 固定正式 NOTICE 的完整 SHA-256。
            metadata = {
                "noticeFileSha256": CHECKER.hash_file(notice_file, "sha256"),
            }
            # report 保留通过校验的文件名和摘要。
            report = CHECKER.validate_repository_notice(notice_file, metadata)
            self.assertEqual(metadata["noticeFileSha256"], report["sha256"])
            notice_file.write_bytes(b"tampered notice\n")
            with self.assertRaisesRegex(AssertionError, "Repository NOTICE sha256"):
                CHECKER.validate_repository_notice(notice_file, metadata)

    def test_repository_notice_rejects_missing_pinned_digest(self) -> None:
        """缺少 NOTICE 强摘要时不能把任意归属文本当作正式声明。"""

        with tempfile.TemporaryDirectory() as directory:
            # notice_file 仅证明文件存在，不代表内容已经受审。
            notice_file = Path(directory) / "NOTICE"
            notice_file.write_bytes(b"unreviewed notice\n")
            with self.assertRaisesRegex(AssertionError, "valid noticeFileSha256"):
                CHECKER.validate_repository_notice(notice_file, {})

    def test_gradle_verification_requires_all_aapt2_platforms(self) -> None:
        """依赖校验缺少任一平台 AAPT2 精确摘要时必须失败。"""

        with tempfile.TemporaryDirectory() as directory:
            # 最小 Gradle dependency-verification XML 只声明 Linux，故应拒绝。
            metadata = Path(directory) / "verification-metadata.xml"
            metadata.write_text(
                """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata xmlns="https://schema.gradle.org/dependency-verification">
  <configuration><verify-metadata>true</verify-metadata><verify-signatures>false</verify-signatures></configuration>
  <components><component group="com.android.tools.build" name="aapt2" version="9.0.1-14304508">
    <artifact name="aapt2-9.0.1-14304508-linux.jar"><sha256 value="ab04484e27480404a32df818c1da12bebaceadab4895f50880153dfaad84e748"/></artifact>
  </component></components>
</verification-metadata>
""",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(AssertionError, "missing verified aapt2-9.0.1-14304508-osx.jar"):
                CHECKER.validate_gradle_verification_metadata(metadata)

    def test_gradle_lockfile_requires_both_release_configurations(self) -> None:
        """任一模块只锁编译配置而未锁运行配置时必须失败。"""

        with tempfile.TemporaryDirectory() as directory:
            # 当前发布模块目录模拟真实模块锁文件布局。
            lockfiles = []
            for artifact in CHECKER.PIXEL_ARTIFACTS:
                # 当前模块临时目录。
                module_directory = Path(directory) / artifact
                module_directory.mkdir()
                # 当前模块锁文件；首个模块故意缺少运行配置。
                lockfile = module_directory / "gradle.lockfile"
                configurations = (
                    "releaseCompileClasspath"
                    if artifact == CHECKER.PIXEL_ARTIFACTS[0]
                    else "releaseCompileClasspath,releaseRuntimeClasspath"
                )
                lockfile.write_text(f"org.example:dependency:1.0={configurations}\n", encoding="utf-8")
                lockfiles.append(lockfile)
            with self.assertRaisesRegex(AssertionError, "must lock both Release configurations"):
                CHECKER.validate_gradle_lockfiles(lockfiles)

    def test_cvss_v31_base_score_matches_reference_vector(self) -> None:
        """CVSS 3.1 高危参考向量必须计算为 9.8。"""

        # 网络可利用、低复杂度、无权限/交互且三类影响均为高。
        vector = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"
        self.assertEqual(9.8, OSV.cvss_v3_base_score(vector))

    def test_osv_unknown_severity_is_not_silently_downgraded(self) -> None:
        """没有文本或 CVSS 的漏洞必须保持 UNKNOWN。"""

        self.assertEqual(("UNKNOWN", None), OSV.vulnerability_severity({"id": "OSV-TEST"}))

    def test_osv_high_named_severity_wins_over_lower_cvss(self) -> None:
        """数据库 HIGH 标记不得被另一条较低 CVSS 覆盖。"""

        # 同一漏洞同时带 HIGH 文本和中危向量。
        vulnerability = {
            "database_specific": {"severity": "HIGH"},
            "severity": [
                {"type": "CVSS_V3", "score": "CVSS:3.1/AV:L/AC:H/PR:H/UI:R/S:U/C:L/I:L/A:L"},
            ],
        }
        self.assertEqual(("HIGH", None), OSV.vulnerability_severity(vulnerability))


if __name__ == "__main__":
    unittest.main()
