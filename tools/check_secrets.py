#!/usr/bin/env python3
"""Scan the current worktree and release archives without printing secret values."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import zipfile
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
from typing import Iterable, Sequence


# Individual source files are bounded so a malformed artifact cannot exhaust CI memory.
MAX_FILE_BYTES = 32 * 1024 * 1024
# Archive totals are bounded independently because an APK/AAR can contain many small entries.
MAX_ARCHIVE_UNCOMPRESSED_BYTES = 256 * 1024 * 1024
# Archive types are unpacked so credentials embedded in classes.dex or classes.jar are visible.
ARCHIVE_SUFFIXES = frozenset({".aar", ".apk", ".jar", ".zip"})


@dataclass(frozen=True)
class SecretRule:
    """Describes one credential shape that must not enter source or release artifacts."""

    rule_id: str
    description: str
    expression: re.Pattern[str]


@dataclass(frozen=True)
class SecretFinding:
    """Stores redacted evidence for one match; the matched credential is never retained."""

    rule_id: str
    path: str
    line: int
    fingerprint: str


@dataclass(frozen=True)
class AllowlistEntry:
    """Allows one exact fingerprint at paths matching a narrow regular expression."""

    rule_id: str
    path_expression: re.Pattern[str]
    fingerprint: str
    reason: str


@dataclass(frozen=True)
class ScanResult:
    """Contains machine-readable scan counts and unsuppressed redacted findings."""

    scanned_files: int
    allowed_findings: int
    findings: tuple[SecretFinding, ...]


# Rules intentionally target well-known high-confidence token formats to keep false positives reviewable.
SECRET_RULES = (
    SecretRule(
        rule_id="provider-api-key",
        description="Long sk-prefixed provider API credential",
        expression=re.compile(r"\bsk-[A-Za-z0-9_-]{20,}\b"),
    ),
    SecretRule(
        rule_id="google-api-key",
        description="Google API credential",
        expression=re.compile(r"\bAIza[0-9A-Za-z_-]{35}\b"),
    ),
    SecretRule(
        rule_id="aws-access-key",
        description="AWS access key identifier",
        expression=re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
    ),
    SecretRule(
        rule_id="github-token",
        description="GitHub personal, OAuth, app, refresh, or fine-grained token",
        expression=re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{40,})\b"),
    ),
    SecretRule(
        rule_id="private-key",
        description="PEM private key material",
        expression=re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    ),
)


def parse_arguments(arguments: Sequence[str]) -> argparse.Namespace:
    """Parse CLI arguments while keeping the repository-root default deterministic."""

    default_root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=default_root, help="Repository root to scan.")
    parser.add_argument(
        "--path",
        dest="paths",
        action="append",
        type=Path,
        default=[],
        help="Additional file, directory, APK, AAR, JAR, or ZIP to scan.",
    )
    parser.add_argument(
        "--no-worktree",
        action="store_true",
        help="Skip tracked and untracked worktree files; useful for an artifact-only pass.",
    )
    parser.add_argument(
        "--git-history",
        action="store_true",
        help="Scan every blob reachable from local Git refs without checking out old commits.",
    )
    parser.add_argument(
        "--allowlist",
        type=Path,
        default=default_root / ".secret-scan-allowlist",
        help="Tab-separated redacted allowlist file.",
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=default_root / "build/reports/security/secret-scan.json",
        help="Machine-readable redacted JSON report path.",
    )
    return parser.parse_args(arguments)


def load_allowlist(path: Path) -> tuple[AllowlistEntry, ...]:
    """Load narrow allowlist entries and reject broad or undocumented records."""

    if not path.exists():
        return ()

    entries: list[AllowlistEntry] = []
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        fields = line.split("\t", maxsplit=3)
        if len(fields) != 4 or not all(fields):
            raise ValueError(f"Invalid secret allowlist entry at {path}:{line_number}")
        rule_id, path_pattern, fingerprint, reason = fields
        if rule_id not in {rule.rule_id for rule in SECRET_RULES}:
            raise ValueError(f"Unknown secret rule '{rule_id}' at {path}:{line_number}")
        if len(fingerprint) != 16 or not re.fullmatch(r"[0-9a-f]{16}", fingerprint):
            raise ValueError(f"Invalid redacted fingerprint at {path}:{line_number}")
        if path_pattern in {".*", ".+", "^.*$"}:
            raise ValueError(f"Overly broad path allowlist at {path}:{line_number}")
        entries.append(
            AllowlistEntry(
                rule_id=rule_id,
                path_expression=re.compile(path_pattern),
                fingerprint=fingerprint,
                reason=reason,
            ),
        )
    return tuple(entries)


def list_worktree_files(root: Path) -> tuple[Path, ...]:
    """Return tracked and non-ignored untracked files from the current Git worktree."""

    result = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        cwd=root,
        check=True,
        stdout=subprocess.PIPE,
    )
    relative_paths = [path for path in result.stdout.decode("utf-8").split("\0") if path]
    return tuple(
        candidate
        for relative_path in sorted(relative_paths)
        if (candidate := root / relative_path).is_file() and not candidate.is_symlink()
    )


def expand_explicit_paths(paths: Iterable[Path]) -> tuple[Path, ...]:
    """Expand explicit directories without following symlinks or silently ignoring missing inputs."""

    expanded: list[Path] = []
    for path in paths:
        resolved_path = path.resolve()
        if not resolved_path.exists():
            raise FileNotFoundError(f"Secret scan input does not exist: {resolved_path}")
        if resolved_path.is_dir():
            expanded.extend(
                candidate
                for candidate in sorted(resolved_path.rglob("*"))
                if candidate.is_file() and not candidate.is_symlink()
            )
        elif resolved_path.is_file() and not resolved_path.is_symlink():
            expanded.append(resolved_path)
    return tuple(expanded)


def display_path(path: Path, root: Path) -> str:
    """Use repository-relative paths when possible so reports are stable across machines."""

    try:
        return path.resolve().relative_to(root.resolve()).as_posix()
    except ValueError:
        return path.resolve().as_posix()


def fingerprint(value: str) -> str:
    """Create a short one-way identifier used for reviewable exact allowlist entries."""

    return hashlib.sha256(value.encode("utf-8")).hexdigest()[:16]


def scan_text(text: str, path: str) -> tuple[SecretFinding, ...]:
    """Scan decoded text and return only redacted metadata for high-confidence matches."""

    findings: list[SecretFinding] = []
    for rule in SECRET_RULES:
        for match in rule.expression.finditer(text):
            line_number = text.count("\n", 0, match.start()) + 1
            findings.append(
                SecretFinding(
                    rule_id=rule.rule_id,
                    path=path,
                    line=line_number,
                    fingerprint=fingerprint(match.group(0)),
                ),
            )
    return tuple(findings)


def scan_regular_file(path: Path, root: Path) -> tuple[SecretFinding, ...]:
    """Scan one bounded regular file, decoding bytes losslessly with Latin-1 for binary strings."""

    file_size = path.stat().st_size
    if file_size > MAX_FILE_BYTES:
        raise ValueError(f"Secret scan input exceeds {MAX_FILE_BYTES} bytes: {path}")
    text = path.read_bytes().decode("latin-1")
    return scan_text(text, display_path(path, root))


def scan_archive(path: Path, root: Path) -> tuple[int, tuple[SecretFinding, ...]]:
    """Scan every bounded regular entry in an APK/AAR/JAR/ZIP, including nested class payloads."""

    return scan_archive_payload(path.read_bytes(), display_path(path, root))


def scan_archive_payload(payload: bytes, archive_label: str) -> tuple[int, tuple[SecretFinding, ...]]:
    """扫描内存中的 ZIP 类归档，供工作树文件与历史 Git blob 共用。"""

    # 归档内全部发现只保留脱敏元数据。
    findings: list[SecretFinding] = []
    # 已实际展开并扫描的归档条目数。
    scanned_entries = 0
    # 累计解压大小用于拒绝 zip bomb。
    total_uncompressed_bytes = 0
    with zipfile.ZipFile(BytesIO(payload)) as archive:
        for entry in sorted(archive.infolist(), key=lambda item: item.filename):
            if entry.is_dir():
                continue
            total_uncompressed_bytes += entry.file_size
            if total_uncompressed_bytes > MAX_ARCHIVE_UNCOMPRESSED_BYTES:
                raise ValueError(f"Archive secret scan exceeds its uncompressed limit: {archive_label}")
            if entry.file_size > MAX_FILE_BYTES:
                raise ValueError(
                    f"Archive entry secret scan exceeds its file limit: {archive_label}!/{entry.filename}",
                )
            entry_text = archive.read(entry).decode("latin-1")
            # 报告路径保留 blob/文件来源与归档内条目名。
            archive_path = f"{archive_label}!/{entry.filename}"
            findings.extend(scan_text(entry_text, archive_path))
            scanned_entries += 1
    return scanned_entries, tuple(findings)


def list_reachable_git_blobs(root: Path) -> tuple[tuple[str, str], ...]:
    """列出所有本地 ref 可达的唯一 Git blob 及其一个稳定路径。"""

    # rev-list 提供对象与路径，随后由 cat-file 类型检查剔除 commit/tree/tag。
    objects_result = subprocess.run(
        ["git", "rev-list", "--objects", "--all"],
        cwd=root,
        check=True,
        stdout=subprocess.PIPE,
        text=True,
    )
    # 同一对象可能被多个 ref 访问，按对象 ID 去重并保留首个路径。
    object_paths: dict[str, str] = {}
    for raw_line in objects_result.stdout.splitlines():
        object_id, separator, path = raw_line.partition(" ")
        object_paths.setdefault(object_id, path if separator else "<no-path>")
    # 批量类型检查避免为每个历史对象启动一个 Git 子进程。
    check_result = subprocess.run(
        ["git", "cat-file", "--batch-check=%(objectname) %(objecttype) %(objectsize)"],
        cwd=root,
        check=True,
        input="".join(f"{object_id}\n" for object_id in object_paths),
        stdout=subprocess.PIPE,
        text=True,
    )
    # 历史扫描对象按完整 ID 排序，保证报告与测试确定性。
    blobs: list[tuple[str, str]] = []
    for line in check_result.stdout.splitlines():
        object_id, object_type, size_text = line.split(" ", maxsplit=2)
        if object_type != "blob":
            continue
        # 超大历史 blob 必须显式失败，不能静默留下未扫描边界。
        object_size = int(size_text)
        if object_size > MAX_FILE_BYTES:
            raise ValueError(
                f"Git blob {object_id} exceeds {MAX_FILE_BYTES} bytes: {object_paths[object_id]}",
            )
        blobs.append((object_id, object_paths[object_id]))
    return tuple(sorted(blobs))


def scan_git_history(root: Path, allowlist: Sequence[AllowlistEntry]) -> ScanResult:
    """扫描所有可达 Git blob，并确保凭据值不会进入命令行、日志或报告。"""

    # 历史 blob 清单不包含不可达松散对象，目标与远端 ref 的可验证边界一致。
    blobs = list_reachable_git_blobs(root)
    # 单个常驻 cat-file 进程按对象 ID 流式返回内容，避免把全部历史载入内存。
    process = subprocess.Popen(
        ["git", "cat-file", "--batch"],
        cwd=root,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
    )
    if process.stdin is None or process.stdout is None:
        process.kill()
        raise RuntimeError("Unable to open git cat-file pipes")
    # 所有匹配只存储规则、位置与单向指纹。
    all_findings: list[SecretFinding] = []
    # Git blob 与归档内条目分别计入扫描数量。
    scanned_files = 0
    try:
        for object_id, path in blobs:
            process.stdin.write(f"{object_id}\n".encode("ascii"))
            process.stdin.flush()
            # batch 响应头包含回显 ID、类型与精确字节数。
            header = process.stdout.readline().decode("ascii").strip()
            returned_id, object_type, size_text = header.split(" ", maxsplit=2)
            if returned_id != object_id or object_type != "blob":
                raise RuntimeError(f"Unexpected git cat-file header: {header}")
            payload = process.stdout.read(int(size_text))
            if len(payload) != int(size_text) or process.stdout.read(1) != b"\n":
                raise RuntimeError(f"Truncated git blob: {object_id}")
            # 路径标签只含公开对象 ID 和仓库路径，不含 blob 内容。
            history_label = f"git:{object_id}:{path}"
            payload_stream = BytesIO(payload)
            if Path(path).suffix.lower() in ARCHIVE_SUFFIXES and zipfile.is_zipfile(payload_stream):
                archive_entries, archive_findings = scan_archive_payload(payload, history_label)
                scanned_files += archive_entries
                all_findings.extend(archive_findings)
            else:
                scanned_files += 1
                all_findings.extend(scan_text(payload.decode("latin-1"), history_label))
    finally:
        process.stdin.close()
        process.stdout.close()
        process.wait()
    if process.returncode != 0:
        raise RuntimeError(f"git cat-file failed with exit code {process.returncode}")
    # allowlist 仍要求规则、完整历史路径和指纹三者精确匹配。
    allowed_findings = sum(1 for finding in all_findings if is_allowed(finding, allowlist))
    visible_findings = tuple(
        finding
        for finding in all_findings
        if not is_allowed(finding, allowlist)
    )
    return ScanResult(
        scanned_files=scanned_files,
        allowed_findings=allowed_findings,
        findings=visible_findings,
    )


def merge_scan_results(results: Iterable[ScanResult]) -> ScanResult:
    """合并工作树、显式产物和 Git 历史的扫描统计。"""

    # 调用方按扫描来源顺序传入，发现顺序因此保持确定性。
    result_list = tuple(results)
    return ScanResult(
        scanned_files=sum(result.scanned_files for result in result_list),
        allowed_findings=sum(result.allowed_findings for result in result_list),
        findings=tuple(finding for result in result_list for finding in result.findings),
    )


def is_allowed(finding: SecretFinding, entries: Sequence[AllowlistEntry]) -> bool:
    """Return true only for an exact rule/fingerprint under a narrowly matching path expression."""

    return any(
        entry.rule_id == finding.rule_id
        and entry.fingerprint == finding.fingerprint
        and entry.path_expression.fullmatch(finding.path) is not None
        for entry in entries
    )


def scan_paths(root: Path, paths: Iterable[Path], allowlist: Sequence[AllowlistEntry]) -> ScanResult:
    """Scan unique paths and apply redacted exact allowlist entries."""

    unique_paths = sorted({path.resolve() for path in paths})
    all_findings: list[SecretFinding] = []
    scanned_files = 0
    for path in unique_paths:
        if path.suffix.lower() in ARCHIVE_SUFFIXES and zipfile.is_zipfile(path):
            archive_entries, archive_findings = scan_archive(path, root)
            scanned_files += archive_entries
            all_findings.extend(archive_findings)
        else:
            scanned_files += 1
            all_findings.extend(scan_regular_file(path, root))

    allowed_findings = sum(1 for finding in all_findings if is_allowed(finding, allowlist))
    visible_findings = tuple(
        finding
        for finding in all_findings
        if not is_allowed(finding, allowlist)
    )
    return ScanResult(
        scanned_files=scanned_files,
        allowed_findings=allowed_findings,
        findings=visible_findings,
    )


def write_report(path: Path, result: ScanResult) -> None:
    """Write deterministic redacted JSON that can be uploaded safely from CI."""

    report = {
        "schemaVersion": 1,
        "status": "passed" if not result.findings else "failed",
        "scannedFiles": result.scanned_files,
        "allowedFindings": result.allowed_findings,
        "findingCount": len(result.findings),
        "findings": [
            {
                "ruleId": finding.rule_id,
                "path": finding.path,
                "line": finding.line,
                "fingerprint": finding.fingerprint,
            }
            for finding in result.findings
        ],
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main(arguments: Sequence[str] | None = None) -> int:
    """Run the worktree/artifact scan and return non-zero for findings or scanner failures."""

    options = parse_arguments(arguments if arguments is not None else sys.argv[1:])
    root = options.root.resolve()
    try:
        allowlist = load_allowlist(options.allowlist.resolve())
        inputs: list[Path] = []
        if not options.no_worktree:
            inputs.extend(list_worktree_files(root))
        inputs.extend(expand_explicit_paths(options.paths))
        if not inputs and not options.git_history:
            raise ValueError("Secret scan received no worktree or artifact inputs.")
        # 当前树/产物与 Git 历史分别扫描，随后形成单一失败语义和报告。
        scan_results: list[ScanResult] = []
        if inputs:
            scan_results.append(scan_paths(root=root, paths=inputs, allowlist=allowlist))
        if options.git_history:
            scan_results.append(scan_git_history(root=root, allowlist=allowlist))
        result = merge_scan_results(scan_results)
        write_report(options.report.resolve(), result)
    except Exception as error:
        print(f"Secret scan could not complete: {error}", file=sys.stderr)
        return 2

    if result.findings:
        print(f"Secret scan failed with {len(result.findings)} redacted finding(s).", file=sys.stderr)
        for finding in result.findings:
            print(
                f"  {finding.path}:{finding.line} [{finding.rule_id}] fingerprint={finding.fingerprint}",
                file=sys.stderr,
            )
        return 1

    print(
        f"Secret scan passed: {result.scanned_files} files/entries, "
        f"{result.allowed_findings} reviewed allowlist match(es).",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
