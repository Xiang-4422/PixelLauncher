#!/usr/bin/env python3
"""检查 Pixel Engine 是否满足不可逆正式发布的全部前置条件。"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


# 当前脚本所在仓库根目录。
ROOT = Path(__file__).resolve().parents[1]
# 正式发布只允许统一的 pixel-engine 坐标。
ARTIFACTS = ("pixel-engine",)
# OpenPGP v4/v5 指纹只接受完整十六进制文本。
FINGERPRINT_PATTERN = re.compile(r"(?:[0-9A-F]{40}|[0-9A-F]{64})")
# GitHub 状态报告在正式发布时最多允许十五分钟陈旧。
MAX_GITHUB_REPORT_AGE_SECONDS = 15 * 60


def parse_arguments() -> argparse.Namespace:
    """解析版本、外部状态报告和输出位置。"""

    # 参数解析器只执行只读检查，不提供 publish 或远端修改选项。
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", default="1.0.0")
    parser.add_argument(
        "--metadata",
        type=Path,
        default=ROOT / "pixel-engine" / "config" / "release-metadata.properties",
    )
    parser.add_argument(
        "--github-report",
        type=Path,
        default=ROOT / "build" / "reports" / "release" / "github-release-readiness.json",
    )
    parser.add_argument(
        "--github-support-report",
        type=Path,
        default=ROOT / "build" / "reports" / "security" / "github-support-cache-purge.json",
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=ROOT / "build" / "reports" / "release" / "formal-release-preflight.json",
    )
    parser.add_argument(
        "--allow-incomplete",
        action="store_true",
        help="仍写出机器报告，但条件未满足时返回成功。",
    )
    return parser.parse_args()


def read_properties(path: Path) -> dict[str, str]:
    """读取不允许重复 key 的简单 Java properties 文件。"""

    # 解析结果保留空值，用于明确区分“尚未确认”和“字段不存在”。
    properties: dict[str, str] = {}
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        # 去除空白后忽略注释与空行。
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise AssertionError(f"{path}:{line_number}: 非法 properties 行")
        # 值中允许继续包含等号，因此只切分一次。
        key, value = line.split("=", 1)
        if key in properties:
            raise AssertionError(f"{path}:{line_number}: 重复 key {key}")
        properties[key] = value.strip()
    return properties


def read_json(path: Path) -> dict[str, Any]:
    """读取必须为 JSON object 的机器报告。"""

    # 报告缺失时由调用方生成明确未完成结果，而不是抛出难懂的文件错误。
    if not path.is_file():
        return {}
    # 根对象必须是字典，防止任意 JSON 数组冒充状态报告。
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise AssertionError(f"{path}: JSON 根节点必须是 object")
    return value


def run_git(*arguments: str, allow_failure: bool = False) -> str:
    """在仓库根执行只读 Git 命令并返回去尾输出。"""

    # Git 子进程不使用 shell，避免 ref 或路径被二次解释。
    result = subprocess.run(
        ["git", *arguments],
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0 and not allow_failure:
        # Git 诊断保留 stderr，但不包含任何发布凭据。
        diagnostic = result.stderr.strip() or result.stdout.strip()
        raise RuntimeError(f"git {' '.join(arguments)} 失败：{diagnostic}")
    return result.stdout.strip() if result.returncode == 0 else ""


def git_succeeds(*arguments: str) -> bool:
    """执行无输出需求的只读 Git 判定并返回是否成功。"""

    # merge-base 等 Git 谓词通过退出码表达结果，不需要解析文本。
    result = subprocess.run(
        ["git", *arguments],
        cwd=ROOT,
        check=False,
        capture_output=True,
    )
    return result.returncode == 0


def read_assignment(path: Path, key: str) -> str | None:
    """读取 Kotlin Gradle 脚本中的顶层字符串赋值。"""

    # 候选构建脚本目前使用稳定的 group/version 顶层赋值形式。
    pattern = re.compile(rf'^{re.escape(key)}\s*=\s*"([^"]+)"\s*$', re.MULTILINE)
    # 每个字段必须恰好出现一次，缺失或动态值都不能在正式预检中猜测。
    matches = pattern.findall(path.read_text(encoding="utf-8"))
    return matches[0] if len(matches) == 1 else None


def read_module_coordinates() -> dict[str, dict[str, str | None]]:
    """读取九个发布模块当前声明的 group 与 version。"""

    # 每个模块的坐标快照用于报告具体漂移，而不是只给出总失败。
    coordinates: dict[str, dict[str, str | None]] = {}
    for artifact in ARTIFACTS:
        # 当前发布模块的 Kotlin Gradle 构建脚本。
        build_file = ROOT / artifact / "build.gradle.kts"
        coordinates[artifact] = {
            "group": read_assignment(build_file, "group"),
            "version": read_assignment(build_file, "version"),
        }
    return coordinates


def is_proof_uri(value: str) -> bool:
    """判断 namespace 证明是否使用可审计的 HTTPS 或 DNS URI。"""

    # DNS TXT 证明和 Portal/仓库页面分别使用 dns 与 https scheme。
    parsed = urlparse(value)
    return parsed.scheme in {"https", "dns"} and bool(parsed.netloc or parsed.path)


def github_report_is_fresh(report: dict[str, Any], now: datetime) -> bool:
    """判断 GitHub 外部状态快照是否在允许的十五分钟窗口内。"""

    # 缺少时间戳时不能证明当前远端状态。
    checked_at = str(report.get("checkedAtUtc", ""))
    if not checked_at:
        return False
    try:
        # Python 使用 +00:00 解析 GitHub 报告中的 Z 后缀。
        checked_time = datetime.fromisoformat(checked_at.replace("Z", "+00:00"))
    except ValueError:
        return False
    # 未来时间戳和超过窗口的旧报告都不可用于不可逆发布。
    age_seconds = (now - checked_time).total_seconds()
    return 0 <= age_seconds <= MAX_GITHUB_REPORT_AGE_SECONDS


def evaluate_formal_release(
    *,
    version: str,
    metadata: dict[str, str],
    module_coordinates: dict[str, dict[str, str | None]],
    changelog_dated: bool,
    clean_worktree: bool,
    version_tag_at_head: bool,
    head_on_origin_main: bool,
    github_report: dict[str, Any],
    github_report_fresh: bool,
    support_report: dict[str, Any],
) -> dict[str, Any]:
    """把本地、GitHub、namespace 和签名条件汇总为正式发布判定。"""

    # 元数据中的候选 groupId 必须与九个模块当前声明完全一致。
    group_id = metadata.get("groupId", "")
    # 坐标一致性同时检查 group 和不可变版本。
    coordinates_consistent = bool(group_id) and all(
        coordinate.get("group") == group_id and coordinate.get("version") == version
        for coordinate in module_coordinates.values()
    )
    # 正式仓库必须是非本地 HTTPS 端点。
    repository_url = metadata.get("releaseRepositoryUrl", "")
    parsed_repository = urlparse(repository_url)
    repository_is_remote = bool(
        metadata.get("releaseRepositoryStatus") == "CONFIRMED"
        and parsed_repository.scheme == "https"
        and parsed_repository.netloc
        and parsed_repository.hostname not in {"localhost", "127.0.0.1", "::1"}
    )
    # 长期签名身份必须有受审完整 fingerprint，不能复用临时 key 描述。
    signing_fingerprint = metadata.get("signingFingerprint", "").upper()
    signing_confirmed = bool(
        metadata.get("signingIdentityStatus") == "CONFIRMED"
        and FINGERPRINT_PATTERN.fullmatch(signing_fingerprint)
    )
    # 每个检查项都对应一项不可由 dry-run 替代的正式发布事实。
    checks = {
        "coordinatesConsistent": coordinates_consistent,
        "namespaceConfirmed": bool(
            metadata.get("namespaceStatus") == "CONFIRMED"
            and is_proof_uri(metadata.get("namespaceProofUrl", ""))
        ),
        "releaseRepositoryConfirmed": repository_is_remote,
        "signingIdentityConfirmed": signing_confirmed,
        "changelogDated": changelog_dated,
        "cleanWorktree": clean_worktree,
        "versionTagAtHead": version_tag_at_head,
        "headPublishedOnOriginMain": head_on_origin_main,
        "githubReleaseReady": bool(
            github_report.get("status") == "ready"
            and github_report.get("version") == version
        ),
        "githubReportFresh": github_report_fresh,
        "githubHistoryCacheCleared": support_report.get("status") == "cleared",
    }
    # 稳定排序的缺失项可直接用于审批清单和回归断言。
    missing = sorted(name for name, passed in checks.items() if not passed)
    # 报告只记录公开身份和状态，不写入仓库密码、私钥或 token。
    return {
        "schemaVersion": 1,
        "status": "ready" if not missing else "incomplete",
        "version": version,
        "groupId": group_id,
        "checks": checks,
        "missing": missing,
        "evidence": {
            "namespaceProofUrl": metadata.get("namespaceProofUrl") or None,
            "releaseRepositoryUrl": repository_url or None,
            "signingFingerprint": signing_fingerprint or None,
            "moduleCoordinates": module_coordinates,
            "githubReportCheckedAtUtc": github_report.get("checkedAtUtc"),
            "githubSupportStatus": support_report.get("status"),
        },
    }


def main() -> int:
    """采集正式发布条件、写机器报告，并按就绪状态返回退出码。"""

    # 当前命令行参数决定版本和三份机器报告位置。
    arguments = parse_arguments()
    # 受版本控制的发布元数据是 namespace、仓库和签名身份的唯一来源。
    metadata = read_properties(arguments.metadata)
    # 九模块坐标必须从当前构建脚本重新读取。
    module_coordinates = read_module_coordinates()
    # CHANGELOG 必须包含真实 ISO 日期，Unreleased 不满足正式发布。
    changelog = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
    changelog_dated = bool(
        re.search(rf"(?m)^## {re.escape(arguments.version)} - \d{{4}}-\d{{2}}-\d{{2}}$", changelog)
    )
    # 任何 tracked/untracked 变化都会使 provenance 无法对应不可变 tag。
    clean_worktree = run_git("status", "--porcelain", "--untracked-files=all") == ""
    # 当前 HEAD 必须精确由目标 SemVer tag 指向。
    version_tag = f"v{arguments.version}"
    tags_at_head = set(run_git("tag", "--points-at", "HEAD").splitlines())
    version_tag_at_head = version_tag in tags_at_head
    # 正式 tag 的提交必须已经进入远端 main，而不是仅存在于本地孤立历史。
    head_on_origin_main = git_succeeds("merge-base", "--is-ancestor", "HEAD", "origin/main")
    # GitHub 发布就绪与历史缓存报告必须由各自专用检查器生成。
    github_report = read_json(arguments.github_report)
    support_report = read_json(arguments.github_support_report)
    # 当前 UTC 时间只用于判断外部状态报告是否仍然新鲜。
    now = datetime.now(timezone.utc)
    # 汇总结果是正式发布审批的只读硬门禁。
    report = evaluate_formal_release(
        version=arguments.version,
        metadata=metadata,
        module_coordinates=module_coordinates,
        changelog_dated=changelog_dated,
        clean_worktree=clean_worktree,
        version_tag_at_head=version_tag_at_head,
        head_on_origin_main=head_on_origin_main,
        github_report=github_report,
        github_report_fresh=github_report_is_fresh(github_report, now),
        support_report=support_report,
    )
    # 运行时间只写在最外层，不参与纯函数测试判定。
    report["checkedAtUtc"] = now.isoformat().replace("+00:00", "Z")
    # 报告目录允许首次创建。
    arguments.report.parent.mkdir(parents=True, exist_ok=True)
    arguments.report.write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(
        "Pixel formal release preflight: "
        f"{report['status']} ({len(report['missing'])} missing); report={arguments.report}"
    )
    return 0 if report["status"] == "ready" or arguments.allow_incomplete else 1


if __name__ == "__main__":
    raise SystemExit(main())
