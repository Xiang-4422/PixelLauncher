#!/usr/bin/env python3
"""只读检查 Pixel Engine 正式发布所需的 GitHub 外部状态。"""

from __future__ import annotations

import argparse
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import quote


# 默认仓库与发布元数据中的 SCM 保持一致。
DEFAULT_REPOSITORY = "Xiang-4422/PixelLauncher"
# 默认保护分支是仓库的正式发布分支。
DEFAULT_BRANCH = "main"
# 当前候选版本用于定位不可变 tag 与 GitHub Release。
DEFAULT_VERSION = "1.0.0"
# branch protection 必须绑定的稳定聚合 job 名称。
DEFAULT_REQUIRED_CONTEXT = "Required pixel-engine gate"
# 默认机器报告路径与其他 M9 证据放在同一报告根目录。
DEFAULT_REPORT = Path("build/reports/release/github-release-readiness.json")


def parse_arguments() -> argparse.Namespace:
    """解析只读检查参数，不提供任何修改 GitHub 状态的选项。"""

    # 参数解析器只接受仓库、版本和报告位置等无副作用输入。
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", default=DEFAULT_REPOSITORY)
    parser.add_argument("--branch", default=DEFAULT_BRANCH)
    parser.add_argument("--version", default=DEFAULT_VERSION)
    parser.add_argument("--required-context", default=DEFAULT_REQUIRED_CONTEXT)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    parser.add_argument(
        "--allow-incomplete",
        action="store_true",
        help="仍写出报告，但外部发布条件未满足时返回成功。",
    )
    return parser.parse_args()


def fetch_github_json(endpoint: str, *, allow_not_found: bool = False) -> Any | None:
    """通过已登录的 gh 执行只读 API 请求并解析 JSON。"""

    # gh 子进程继承系统 keyring 登录态，但不会把 token 放入参数或报告。
    result = subprocess.run(
        ["gh", "api", endpoint],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode == 0:
        return json.loads(result.stdout)
    # 可选资源的 404 表示尚未配置，而不是检查器自身失败。
    not_found = "HTTP 404" in result.stderr or '"status":"404"' in result.stderr
    if allow_not_found and not_found:
        return None
    # 其余认证、网络或 API 错误必须阻止生成误导性就绪结论。
    diagnostic = result.stderr.strip() or result.stdout.strip() or "unknown gh api failure"
    raise RuntimeError(f"GitHub API 请求失败：{endpoint}: {diagnostic}")


def collect_required_contexts(protection: dict[str, Any] | None) -> set[str]:
    """兼容 contexts 与 checks 两种 branch protection 响应结构。"""

    if protection is None:
        return set()
    # required_status_checks 缺失表示分支没有绑定任何 required check。
    required_status = protection.get("required_status_checks") or {}
    # 旧响应直接返回 context 字符串数组。
    contexts = {
        str(context)
        for context in required_status.get("contexts", [])
        if str(context).strip()
    }
    # 新响应可同时返回带 GitHub App 身份的 check 对象。
    check_contexts = {
        str(check.get("context"))
        for check in required_status.get("checks", [])
        if isinstance(check, dict) and str(check.get("context", "")).strip()
    }
    return contexts | check_contexts


def evaluate_release_readiness(
    *,
    repository: str,
    branch: str,
    version: str,
    required_context: str,
    repository_metadata: dict[str, Any],
    protection: dict[str, Any] | None,
    pages: dict[str, Any] | None,
    required_workflow: dict[str, Any] | None,
    documentation_workflow: dict[str, Any] | None,
    tag: dict[str, Any] | None,
    release: dict[str, Any] | None,
) -> dict[str, Any]:
    """把 GitHub API 快照归一化为稳定、可测试的发布就绪报告。"""

    # 分支保护中实际绑定的全部检查名称用于精确诊断配置漂移。
    required_contexts = collect_required_contexts(protection)
    # strict=true 要求候选在合并前基于最新目标分支重新通过门禁。
    protection_is_strict = bool(
        protection
        and (protection.get("required_status_checks") or {}).get("strict") is True
    )
    # GitHub Release 只有公开、非草稿、非预发布且具有发布时间才算正式发布。
    release_is_published = bool(
        release
        and release.get("tag_name") == f"v{version}"
        and release.get("draft") is False
        and release.get("prerelease") is False
        and release.get("published_at")
    )
    # 每个布尔项对应 M9-3 中一个可由 GitHub 当前状态证明的外部条件。
    checks = {
        "repositoryAvailable": bool(
            repository_metadata.get("visibility") == "public"
            and repository_metadata.get("default_branch") == branch
            and repository_metadata.get("archived") is False
            and repository_metadata.get("disabled") is False
        ),
        "requiredWorkflowActive": bool(
            required_workflow and required_workflow.get("state") == "active"
        ),
        "documentationWorkflowActive": bool(
            documentation_workflow and documentation_workflow.get("state") == "active"
        ),
        "requiredGateBound": bool(
            protection_is_strict and required_context in required_contexts
        ),
        "pagesConfigured": bool(
            pages
            and pages.get("build_type") == "workflow"
            and str(pages.get("html_url", "")).strip()
        ),
        "versionTagPresent": tag is not None,
        "releasePublished": release_is_published,
    }
    # 缺失项使用稳定 key，供发布脚本或人工审批精确定位下一步。
    missing = sorted(name for name, passed in checks.items() if not passed)
    # 报告只保存发布状态摘要，不复制 GitHub 用户、token 或完整 API 响应。
    return {
        "schemaVersion": 1,
        "status": "ready" if not missing else "incomplete",
        "checkedAtUtc": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "repository": repository,
        "branch": branch,
        "version": version,
        "requiredContext": required_context,
        "checks": checks,
        "missing": missing,
        "evidence": {
            "requiredContexts": sorted(required_contexts),
            "branchProtectionStrict": protection_is_strict,
            "pagesUrl": pages.get("html_url") if pages else None,
            "tagRef": tag.get("ref") if tag else None,
            "releaseUrl": release.get("html_url") if release else None,
        },
    }


def main() -> int:
    """采集 GitHub 当前状态、写入报告，并按是否就绪返回退出码。"""

    # 命令行参数决定本次只读检查的目标版本和报告位置。
    arguments = parse_arguments()
    if arguments.repository.count("/") != 1:
        raise SystemExit("--repository 必须使用 owner/name 格式。")
    # URL 路径片段必须编码，避免分支或 tag 名中的斜杠改变 API endpoint。
    encoded_branch = quote(arguments.branch, safe="")
    # 正式 tag 固定使用 SemVer 常见的 v 前缀。
    encoded_tag = quote(f"v{arguments.version}", safe="")
    # 仓库基础 endpoint 复用于全部只读请求。
    repository_endpoint = f"repos/{arguments.repository}"

    # 核心仓库元数据不存在时无法对任何发布条件做可靠判断。
    repository_metadata = fetch_github_json(repository_endpoint)
    # 以下资源在正式发布前允许 404，并在报告中显示为未完成。
    protection = fetch_github_json(
        f"{repository_endpoint}/branches/{encoded_branch}/protection",
        allow_not_found=True,
    )
    pages = fetch_github_json(f"{repository_endpoint}/pages", allow_not_found=True)
    required_workflow = fetch_github_json(
        f"{repository_endpoint}/actions/workflows/pixel-engine.yml",
        allow_not_found=True,
    )
    documentation_workflow = fetch_github_json(
        f"{repository_endpoint}/actions/workflows/pixel-engine-docs.yml",
        allow_not_found=True,
    )
    tag = fetch_github_json(
        f"{repository_endpoint}/git/ref/tags/{encoded_tag}",
        allow_not_found=True,
    )
    release = fetch_github_json(
        f"{repository_endpoint}/releases/tags/{encoded_tag}",
        allow_not_found=True,
    )
    # 归一化报告是命令输出、测试和发布审批共用的唯一结果。
    report = evaluate_release_readiness(
        repository=arguments.repository,
        branch=arguments.branch,
        version=arguments.version,
        required_context=arguments.required_context,
        repository_metadata=repository_metadata,
        protection=protection,
        pages=pages,
        required_workflow=required_workflow,
        documentation_workflow=documentation_workflow,
        tag=tag,
        release=release,
    )
    # 父目录可能是首次生成，必须在写报告前创建。
    arguments.report.parent.mkdir(parents=True, exist_ok=True)
    arguments.report.write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(
        "Pixel GitHub release readiness: "
        f"{report['status']} ({len(report['missing'])} missing); report={arguments.report}"
    )
    return 0 if report["status"] == "ready" or arguments.allow_incomplete else 1


if __name__ == "__main__":
    raise SystemExit(main())
