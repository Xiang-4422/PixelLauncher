#!/usr/bin/env python3
"""使用 OSV 官方 API 扫描 Pixel SDK Release 依赖图中的未解释高危漏洞。"""

from __future__ import annotations

import argparse
import json
import math
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


# OSV 官方批量查询端点。
OSV_BATCH_URL = "https://api.osv.dev/v1/querybatch"
# OSV 官方漏洞详情端点前缀。
OSV_DETAIL_URL = "https://api.osv.dev/v1/vulns/"
# 高危阈值采用 CVSS 通用的 7.0 分界。
HIGH_SEVERITY_THRESHOLD = 7.0
# 单次 HTTP 请求的超时秒数。
HTTP_TIMEOUT_SECONDS = 30
# 临时网络错误的最大尝试次数。
HTTP_ATTEMPTS = 3


def parse_args() -> argparse.Namespace:
    """解析依赖图、allowlist、离线 fixture 和报告路径。"""

    # 命令行解析器供 CI 网络扫描和单元测试 fixture 共用。
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dependency-graph", type=Path, required=True)
    parser.add_argument("--allowlist", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--fixture", type=Path)
    return parser.parse_args()


def write_json_atomic(path: Path, document: dict[str, Any]) -> None:
    """原子写出确定性 OSV 报告。"""

    # 同目录临时文件确保网络/解析异常不会留下半份通过报告。
    temporary_path = path.with_name(path.name + ".tmp")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path.write_text(
        json.dumps(document, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary_path.replace(path)


def post_json(url: str, payload: dict[str, Any]) -> dict[str, Any]:
    """向 OSV 发送 JSON POST，并对短暂网络错误做有限重试。"""

    # UTF-8 请求体使用稳定 JSON 编码。
    request_body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    # OSV API 的显式请求头。
    request = urllib.request.Request(
        url,
        data=request_body,
        headers={"Content-Type": "application/json", "User-Agent": "pixel-engine-supply-chain/1.0"},
        method="POST",
    )
    # 最后一次异常在重试耗尽后重新抛出。
    last_error: Exception | None = None
    for attempt in range(HTTP_ATTEMPTS):
        try:
            with urllib.request.urlopen(request, timeout=HTTP_TIMEOUT_SECONDS) as response:
                return json.loads(response.read().decode("utf-8"))
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
            last_error = error
            if attempt + 1 < HTTP_ATTEMPTS:
                time.sleep(attempt + 1)
    raise AssertionError(f"OSV POST failed after {HTTP_ATTEMPTS} attempts: {last_error}")


def get_json(url: str) -> dict[str, Any]:
    """读取一个 OSV 漏洞详情，并对短暂网络错误做有限重试。"""

    # OSV 详情请求不携带凭据。
    request = urllib.request.Request(
        url,
        headers={"Accept": "application/json", "User-Agent": "pixel-engine-supply-chain/1.0"},
        method="GET",
    )
    # 最后一次异常用于失败诊断。
    last_error: Exception | None = None
    for attempt in range(HTTP_ATTEMPTS):
        try:
            with urllib.request.urlopen(request, timeout=HTTP_TIMEOUT_SECONDS) as response:
                return json.loads(response.read().decode("utf-8"))
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
            last_error = error
            if attempt + 1 < HTTP_ATTEMPTS:
                time.sleep(attempt + 1)
    raise AssertionError(f"OSV GET failed after {HTTP_ATTEMPTS} attempts: {last_error}")


def round_up_tenth(value: float) -> float:
    """按 CVSS 3.x 规则向上取到一位小数。"""

    return math.ceil((value - 1e-10) * 10.0) / 10.0


def cvss_v3_base_score(vector: str) -> float | None:
    """计算 CVSS 3.0/3.1 基础分；非受支持向量返回 null。"""

    if not vector.startswith(("CVSS:3.0/", "CVSS:3.1/")):
        return None
    # 向量中的指标键值表。
    metrics: dict[str, str] = {}
    for part in vector.split("/")[1:]:
        if ":" not in part:
            return None
        # 每个 CVSS 指标只拆分一次。
        key, value = part.split(":", 1)
        metrics[key] = value
    # 基础分所需的全部指标。
    required_metrics = {"AV", "AC", "PR", "UI", "S", "C", "I", "A"}
    if not required_metrics.issubset(metrics):
        return None
    # Attack Vector 权重。
    attack_vector = {"N": 0.85, "A": 0.62, "L": 0.55, "P": 0.20}.get(metrics["AV"])
    # Attack Complexity 权重。
    attack_complexity = {"L": 0.77, "H": 0.44}.get(metrics["AC"])
    # User Interaction 权重。
    user_interaction = {"N": 0.85, "R": 0.62}.get(metrics["UI"])
    # 机密性、完整性和可用性共用影响权重。
    impact_weights = {"N": 0.0, "L": 0.22, "H": 0.56}
    # Scope 决定 Privileges Required 的不同权重。
    privileges_required = (
        {"N": 0.85, "L": 0.68, "H": 0.50}.get(metrics["PR"])
        if metrics["S"] == "C"
        else {"N": 0.85, "L": 0.62, "H": 0.27}.get(metrics["PR"])
    )
    # 任一未知枚举都说明向量不完整或不是 CVSS 3.x。
    if None in (attack_vector, attack_complexity, user_interaction, privileges_required):
        return None
    try:
        # 三类影响权重。
        confidentiality = impact_weights[metrics["C"]]
        integrity = impact_weights[metrics["I"]]
        availability = impact_weights[metrics["A"]]
    except KeyError:
        return None
    # Impact Sub Score 基础值。
    impact_subscore = 1.0 - ((1.0 - confidentiality) * (1.0 - integrity) * (1.0 - availability))
    if metrics["S"] == "U":
        # Scope Unchanged 影响分。
        impact = 6.42 * impact_subscore
    elif metrics["S"] == "C":
        # Scope Changed 影响分。
        impact = 7.52 * (impact_subscore - 0.029) - 3.25 * ((impact_subscore - 0.02) ** 15)
    else:
        return None
    if impact <= 0.0:
        return 0.0
    # 可利用性子分。
    exploitability = 8.22 * attack_vector * attack_complexity * privileges_required * user_interaction
    # Scope Changed 需要额外 1.08 系数。
    base = min((1.08 if metrics["S"] == "C" else 1.0) * (impact + exploitability), 10.0)
    return round_up_tenth(base)


def normalized_named_severity(value: Any) -> str | None:
    """把数据库文本严重度规范为 CRITICAL/HIGH/MEDIUM/LOW。"""

    if not isinstance(value, str):
        return None
    # 常见数据库严重度名称统一转为大写。
    severity = value.strip().upper()
    return severity if severity in {"CRITICAL", "HIGH", "MODERATE", "MEDIUM", "LOW"} else None


def vulnerability_severity(vulnerability: dict[str, Any]) -> tuple[str, float | None]:
    """从 OSV 详情提取最保守的文本或 CVSS 严重度。"""

    # 数据库和生态扩展中的文本严重度候选。
    named_candidates = [
        normalized_named_severity(vulnerability.get("database_specific", {}).get("severity")),
        normalized_named_severity(vulnerability.get("ecosystem_specific", {}).get("severity")),
    ]
    if "CRITICAL" in named_candidates:
        return "CRITICAL", None
    if "HIGH" in named_candidates:
        return "HIGH", None
    # OSV severity 数组中的最高 CVSS 3.x 基础分。
    scores = [
        score
        for entry in vulnerability.get("severity", [])
        if isinstance(entry, dict)
        for score in [cvss_v3_base_score(str(entry.get("score", "")))]
        if score is not None
    ]
    if scores:
        # 选取最严重评分，避免多数据库记录掩盖风险。
        maximum_score = max(scores)
        if maximum_score >= 9.0:
            return "CRITICAL", maximum_score
        if maximum_score >= HIGH_SEVERITY_THRESHOLD:
            return "HIGH", maximum_score
        if maximum_score >= 4.0:
            return "MEDIUM", maximum_score
        return "LOW", maximum_score
    if any(candidate in {"MODERATE", "MEDIUM"} for candidate in named_candidates):
        return "MEDIUM", None
    if "LOW" in named_candidates:
        return "LOW", None
    return "UNKNOWN", None


def load_allowlist(path: Path) -> dict[str, dict[str, Any]]:
    """读取带理由和截止日期的高危漏洞例外清单。"""

    # allowlist JSON 根对象。
    document = json.loads(path.read_text(encoding="utf-8"))
    # 例外条目数组；空数组是正常状态。
    entries = document.get("entries")
    if not isinstance(entries, list):
        raise AssertionError(f"{path}: entries must be an array")
    # 以 OSV ID 为键的例外表。
    allowlist: dict[str, dict[str, Any]] = {}
    for entry in entries:
        # 每个例外必须说明风险处置和过期日期，禁止永久静默。
        vulnerability_id = str(entry.get("id", "")).strip()
        rationale = str(entry.get("rationale", "")).strip()
        expires = str(entry.get("expires", "")).strip()
        if not vulnerability_id or not rationale or not expires:
            raise AssertionError(f"{path}: allowlist entries require id, rationale, and expires")
        if vulnerability_id in allowlist:
            raise AssertionError(f"{path}: duplicate allowlist id {vulnerability_id}")
        allowlist[vulnerability_id] = entry
    return allowlist


def external_components(graph: dict[str, Any]) -> list[dict[str, str]]:
    """返回需要查询 OSV 的外部 Maven 组件。"""

    # 去除 SDK 自身坐标，并按 purl 去重排序。
    components = {
        str(component["purl"]): {
            "group": str(component["group"]),
            "name": str(component["name"]),
            "version": str(component["version"]),
            "purl": str(component["purl"]),
        }
        for component in graph.get("components", [])
        if component.get("group") != "com.purride"
    }
    return [components[purl] for purl in sorted(components)]


def query_osv(components: list[dict[str, str]]) -> tuple[list[dict[str, Any]], dict[str, dict[str, Any]]]:
    """批量查询组件并获取每个唯一漏洞的完整详情。"""

    # querybatch 的查询顺序与组件顺序一一对应。
    batch_payload = {
        "queries": [
            {
                "version": component["version"],
                "package": {"ecosystem": "Maven", "name": f"{component['group']}:{component['name']}"},
            }
            for component in components
        ],
    }
    # OSV 批量响应。
    batch_response = post_json(OSV_BATCH_URL, batch_payload)
    # 每个组件对应的结果对象。
    results = batch_response.get("results")
    if not isinstance(results, list) or len(results) != len(components):
        raise AssertionError("OSV querybatch response does not match request ordering")
    # 全部唯一漏洞 ID。
    vulnerability_ids = sorted(
        {
            str(vulnerability["id"])
            for result in results
            for vulnerability in result.get("vulns", [])
            if isinstance(vulnerability, dict) and vulnerability.get("id")
        },
    )
    # 每个 ID 的完整 OSV 详情。
    details = {
        vulnerability_id: get_json(OSV_DETAIL_URL + urllib.parse.quote(vulnerability_id, safe=""))
        for vulnerability_id in vulnerability_ids
    }
    return results, details


def fixture_osv(path: Path) -> tuple[list[dict[str, Any]], dict[str, dict[str, Any]]]:
    """读取测试用 OSV querybatch 与详情 fixture。"""

    # fixture 文档包含与在线 API 同形的两部分。
    document = json.loads(path.read_text(encoding="utf-8"))
    # 批量结果数组。
    results = document.get("results")
    # 详情 ID 映射。
    details = document.get("details")
    if not isinstance(results, list) or not isinstance(details, dict):
        raise AssertionError(f"{path}: fixture requires results and details")
    return results, details


def main() -> int:
    """扫描全部外部组件并拒绝未解释的高危或未知严重度漏洞。"""

    # 已解析的命令行参数。
    args = parse_args()
    # Gradle Release 依赖图。
    graph = json.loads(args.dependency_graph.read_text(encoding="utf-8"))
    # OSV 查询目标组件。
    components = external_components(graph)
    # 已人工审阅的例外表。
    allowlist = load_allowlist(args.allowlist)
    if args.fixture is not None:
        # 单元测试使用确定性离线响应。
        results, details = fixture_osv(args.fixture)
    else:
        # 正式门禁使用 OSV 官方在线 API。
        results, details = query_osv(components)
    if len(results) != len(components):
        raise AssertionError("OSV results count does not match dependency component count")

    # 按组件展开后的全部漏洞发现。
    findings: list[dict[str, Any]] = []
    for component, result in zip(components, results):
        for compact_vulnerability in result.get("vulns", []):
            # 当前 OSV 漏洞 ID。
            vulnerability_id = str(compact_vulnerability["id"])
            # 完整 OSV 记录用于严重度与链接。
            detail = details.get(vulnerability_id)
            if not isinstance(detail, dict):
                raise AssertionError(f"Missing OSV detail for {vulnerability_id}")
            # 规范严重度和可用 CVSS 分数。
            severity, score = vulnerability_severity(detail)
            findings.append(
                {
                    "id": vulnerability_id,
                    "component": component["purl"],
                    "severity": severity,
                    "score": score,
                    "summary": str(detail.get("summary", "")),
                    "modified": str(detail.get("modified", "")),
                    "allowlisted": vulnerability_id in allowlist,
                },
            )
    findings.sort(key=lambda finding: (finding["id"], finding["component"]))
    # 未在有期限例外中解释的 HIGH/CRITICAL 发现。
    unexplained_high = [
        finding
        for finding in findings
        if finding["severity"] in {"HIGH", "CRITICAL"} and not finding["allowlisted"]
    ]
    # 未知严重度同样不能被静默当作低风险。
    unexplained_unknown = [
        finding
        for finding in findings
        if finding["severity"] == "UNKNOWN" and not finding["allowlisted"]
    ]
    # 最终状态由未解释发现决定。
    status = "passed" if not unexplained_high and not unexplained_unknown else "failed"
    # 可机读扫描报告。
    report = {
        "schemaVersion": 1,
        "status": status,
        "source": OSV_BATCH_URL if args.fixture is None else str(args.fixture),
        "componentCount": len(components),
        "findingCount": len(findings),
        "findings": findings,
        "unexplainedHigh": unexplained_high,
        "unexplainedUnknown": unexplained_unknown,
        "allowlistEntryCount": len(allowlist),
    }
    write_json_atomic(args.report, report)
    if status != "passed":
        raise AssertionError(
            f"OSV scan found {len(unexplained_high)} unexplained high and "
            f"{len(unexplained_unknown)} unexplained unknown vulnerabilities",
        )
    print(f"Pixel OSV scan passed: {len(components)} components, {len(findings)} findings")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
