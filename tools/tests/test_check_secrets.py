from __future__ import annotations

import io
import json
import subprocess
import tempfile
import unittest
import zipfile
from contextlib import redirect_stderr
from pathlib import Path

from tools import check_secrets


class SecretScannerTest(unittest.TestCase):
    """Verifies redaction, archive coverage, exact allowlisting, and failure exit codes."""

    def test_clean_file_passes(self) -> None:
        """Ordinary source text must produce an empty finding list."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source = root / "Clean.kt"
            source.write_text('const val STATUS = "UNCONFIGURED"\n', encoding="utf-8")

            result = check_secrets.scan_paths(root=root, paths=[source], allowlist=())

            self.assertEqual(1, result.scanned_files)
            self.assertEqual((), result.findings)

    def test_cli_fails_without_printing_secret_value(self) -> None:
        """A synthetic provider token must fail while console and JSON stay redacted."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source = root / "Leaked.kt"
            synthetic_credential = "sk-" + ("a" * 32)
            source.write_text(f'val key = "{synthetic_credential}"\n', encoding="utf-8")
            report = root / "report.json"
            stderr = io.StringIO()

            with redirect_stderr(stderr):
                exit_code = check_secrets.main(
                    [
                        "--root",
                        str(root),
                        "--no-worktree",
                        "--path",
                        str(source),
                        "--allowlist",
                        str(root / "missing-allowlist"),
                        "--report",
                        str(report),
                    ],
                )

            report_text = report.read_text(encoding="utf-8")
            self.assertEqual(1, exit_code)
            self.assertNotIn(synthetic_credential, stderr.getvalue())
            self.assertNotIn(synthetic_credential, report_text)
            self.assertEqual("failed", json.loads(report_text)["status"])

    def test_archive_entries_are_scanned(self) -> None:
        """Credentials embedded in classes.dex inside an APK must be detected."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            archive_path = root / "sample.apk"
            synthetic_credential = "sk-" + ("b" * 32)
            with zipfile.ZipFile(archive_path, mode="w") as archive:
                # DEX string data separates the credential from adjacent binary data with NUL bytes.
                archive.writestr("classes.dex", b"prefix\0" + synthetic_credential.encode("ascii") + b"\0suffix")

            result = check_secrets.scan_paths(root=root, paths=[archive_path], allowlist=())

            self.assertEqual(1, result.scanned_files)
            self.assertEqual(1, len(result.findings))
            self.assertIn("sample.apk!/classes.dex", result.findings[0].path)

    def test_allowlist_requires_exact_rule_path_and_fingerprint(self) -> None:
        """A narrow reviewed entry suppresses only the exact redacted finding."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source = root / "fixture.txt"
            synthetic_credential = "sk-" + ("c" * 32)
            source.write_text(synthetic_credential, encoding="utf-8")
            finding = check_secrets.scan_paths(root=root, paths=[source], allowlist=()).findings[0]
            allowlist_path = root / ".secret-scan-allowlist"
            allowlist_path.write_text(
                "\t".join(
                    [
                        finding.rule_id,
                        r"fixture\.txt",
                        finding.fingerprint,
                        "Synthetic scanner unit-test fixture",
                    ],
                )
                + "\n",
                encoding="utf-8",
            )

            allowlist = check_secrets.load_allowlist(allowlist_path)
            result = check_secrets.scan_paths(root=root, paths=[source], allowlist=allowlist)

            self.assertEqual(1, result.allowed_findings)
            self.assertEqual((), result.findings)

    def test_git_history_finds_removed_secret_without_exposing_value(self) -> None:
        """当前树已删除的历史凭据仍必须由可达 blob 扫描发现。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # 独立临时仓库不会污染真实工作树或依赖全局 Git 身份。
            root = Path(temporary_directory)
            subprocess.run(["git", "init", "-q"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.name", "Secret Scanner Test"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.email", "secret-scanner@invalid.example"], cwd=root, check=True)
            source = root / "Leaked.kt"
            # 合成凭据只验证规则和脱敏报告，不复用任何真实值。
            synthetic_credential = "sk-" + ("d" * 32)
            source.write_text(f'val key = "{synthetic_credential}"\n', encoding="utf-8")
            subprocess.run(["git", "add", "Leaked.kt"], cwd=root, check=True)
            subprocess.run(["git", "commit", "-qm", "add synthetic secret"], cwd=root, check=True)
            source.unlink()
            subprocess.run(["git", "add", "-u"], cwd=root, check=True)
            subprocess.run(["git", "commit", "-qm", "remove synthetic secret"], cwd=root, check=True)

            result = check_secrets.scan_git_history(root=root, allowlist=())

            self.assertEqual(1, len(result.findings))
            self.assertTrue(result.findings[0].path.startswith("git:"))
            self.assertNotIn(synthetic_credential, result.findings[0].path)


if __name__ == "__main__":
    unittest.main()
