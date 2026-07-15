#!/usr/bin/env python3
"""校验 Pixel SDK 源文件归属、artifact 依赖无环性和平台边界。"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Sequence


# Kotlin/Java package 声明的单行匹配式；Java 允许末尾分号。
PACKAGE_PATTERN = re.compile(r"^package\s+([A-Za-z_][A-Za-z0-9_.]*)\s*;?\s*$", re.MULTILINE)
# Kotlin/Java 显式或星号 import 的单行匹配式；Kotlin 仍支持别名。
IMPORT_PATTERN = re.compile(
    r"^import\s+([A-Za-z_][A-Za-z0-9_.*]*)(?:\s+as\s+[A-Za-z_][A-Za-z0-9_]*)?\s*;?\s*$",
    re.MULTILINE,
)
# 顶层类、接口、对象和 typealias 的保守匹配式。
TYPE_DECLARATION_PATTERN = re.compile(
    r"^(?:(?:public|internal|private|protected|expect|actual|sealed|data|open|abstract|final|value|fun)\s+)*"
    r"(?:(?:enum|annotation)\s+)?(?:class|interface|object|typealias)\s+"
    r"([A-Za-z_][A-Za-z0-9_]*)",
    re.MULTILINE,
)
# 顶层函数名称的保守匹配式，支持泛型和扩展接收者。
FUNCTION_DECLARATION_PATTERN = re.compile(
    r"^(?:(?:public|internal|private|protected|expect|actual|inline|infix|operator|suspend|tailrec|external)\s+)*"
    r"fun\s+(?:<[^\n>]+>\s*)?(?:[A-Za-z_][A-Za-z0-9_<>?,.() ]*\.)?"
    r"([A-Za-z_][A-Za-z0-9_]*)\s*\(",
    re.MULTILINE,
)
# 顶层属性名称的保守匹配式。
PROPERTY_DECLARATION_PATTERN = re.compile(
    r"^(?:(?:public|internal|private|protected|expect|actual|const|lateinit)\s+)*(?:val|var)\s+"
    r"(?:[A-Za-z_][A-Za-z0-9_<>?,.() ]*\.)?([A-Za-z_][A-Za-z0-9_]*)\b",
    re.MULTILINE,
)
# Android 平台或 AndroidX import 的前缀。
PLATFORM_IMPORT_PREFIXES = ("android.", "androidx.")
# Compose 只能由可选 compose artifact 直接引用。
COMPOSE_IMPORT_PREFIX = "androidx.compose."


class AuditConfigurationError(ValueError):
    """表示清单结构错误，不能把不完整检查误报为通过。"""


@dataclass(frozen=True)
class SourceRecord:
    """保存一个 Kotlin 或 Java 生产源文件的确定性审计信息。"""

    # 仓库中的绝对源文件路径，仅用于读取内容。
    path: Path
    # 相对配置 sourceRoot 的稳定路径，用于报告和归属匹配。
    relative_path: str
    # 清单解析出的唯一 artifact 名称。
    artifact: str
    # Kotlin/Java package，用于解析项目内 import。
    package: str
    # 文件中的显式 import 集合。
    imports: tuple[str, ...]
    # 本文件保守提取出的顶层符号全名。
    symbols: tuple[str, ...]


@dataclass(frozen=True)
class Finding:
    """描述一个会阻止 artifact 边界门禁通过的问题。"""

    # 稳定的问题类别，供 CI 和测试断言。
    category: str
    # 发生问题的源文件、package 或 artifact。
    subject: str
    # 面向维护者的具体修复线索。
    detail: str


def parse_arguments(arguments: Sequence[str]) -> argparse.Namespace:
    """解析清单和机器报告路径。"""

    # 参数解析器保持独立，使单元测试无需启动子进程。
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path, help="artifact ownership JSON 清单。")
    parser.add_argument("--report", required=True, type=Path, help="机器可读 JSON 报告输出路径。")
    return parser.parse_args(arguments)


def load_manifest(manifest_path: Path) -> dict[str, Any]:
    """读取并验证门禁所需的最小清单结构。"""

    if not manifest_path.is_file():
        raise AuditConfigurationError(f"找不到 artifact 清单：{manifest_path}")
    try:
        # 清单对象必须是 JSON object，避免数组等输入被静默接受。
        payload: Any = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise AuditConfigurationError(f"无法读取 artifact 清单：{error}") from error
    if not isinstance(payload, dict) or payload.get("schemaVersion") != 1:
        raise AuditConfigurationError("artifact 清单必须是 schemaVersion=1 的 JSON object")
    if not isinstance(payload.get("sourceRoot"), str):
        raise AuditConfigurationError("artifact 清单缺少字符串 sourceRoot")
    # additionalSourceRoots 允许同一冻结源码树中的 Java 实现进入相同审计闭包。
    additional_source_roots = payload.get("additionalSourceRoots", [])
    if not isinstance(additional_source_roots, list) or any(
        not isinstance(path, str) or not path for path in additional_source_roots
    ):
        raise AuditConfigurationError("artifact 清单 additionalSourceRoots 必须是字符串数组")
    if not isinstance(payload.get("artifacts"), dict) or not payload["artifacts"]:
        raise AuditConfigurationError("artifact 清单必须声明非空 artifacts")
    if not isinstance(payload.get("ownership"), dict):
        raise AuditConfigurationError("artifact 清单缺少 ownership")
    return payload


def normalize_manifest_path(path_text: str, field_name: str) -> str:
    """把清单路径规范化为不含父目录跳转的 POSIX 相对路径。"""

    # Path.parts 用于阻止清单逃逸 sourceRoot。
    path = Path(path_text)
    if path.is_absolute() or ".." in path.parts:
        raise AuditConfigurationError(f"{field_name} 必须是 sourceRoot 内相对路径：{path_text}")
    return path.as_posix().lstrip("./")


def validate_artifact_graph(manifest: dict[str, Any]) -> tuple[dict[str, tuple[str, ...]], list[Finding]]:
    """验证依赖引用存在、声明图无环且最小图不传递到禁用 artifact。"""

    # artifacts 是整个审计的节点全集。
    artifact_payload: dict[str, Any] = manifest["artifacts"]
    # dependencies 保存排序后的直接边，输出和遍历都保持确定性。
    dependencies: dict[str, tuple[str, ...]] = {}
    # findings 收集所有可同时报告的问题，减少逐个修复往返。
    findings: list[Finding] = []
    for artifact_name, artifact_config in sorted(artifact_payload.items()):
        if not isinstance(artifact_config, dict) or not isinstance(artifact_config.get("dependencies"), list):
            raise AuditConfigurationError(f"artifact {artifact_name} 缺少 dependencies 数组")
        # 直接依赖去重后排序，重复配置不会改变报告顺序。
        direct_dependencies = tuple(sorted(set(artifact_config["dependencies"])))
        dependencies[artifact_name] = direct_dependencies
        for dependency in direct_dependencies:
            if dependency not in artifact_payload:
                findings.append(Finding("UNKNOWN_DEPENDENCY", artifact_name, f"引用未声明 artifact：{dependency}"))

    # visiting 表示当前 DFS 栈，用于输出完整循环路径。
    visiting: list[str] = []
    # visited 防止重复遍历已证明无环的子图。
    visited: set[str] = set()

    def visit(artifact_name: str) -> None:
        """深度优先访问一个 artifact，并记录第一次发现的每条环。"""

        if artifact_name in visiting:
            # cycle 从首次出现的节点开始并回到起点。
            cycle_start = visiting.index(artifact_name)
            cycle = visiting[cycle_start:] + [artifact_name]
            findings.append(Finding("DEPENDENCY_CYCLE", artifact_name, " -> ".join(cycle)))
            return
        if artifact_name in visited:
            return
        visiting.append(artifact_name)
        for dependency in dependencies.get(artifact_name, ()):
            if dependency in dependencies:
                visit(dependency)
        visiting.pop()
        visited.add(artifact_name)

    for artifact_name in sorted(dependencies):
        visit(artifact_name)

    # minimalArtifacts 明确哪些消费者图不得出现 testing/debug/compose。
    minimal_artifacts = manifest.get("minimalArtifacts", [])
    # forbidden_artifacts 是所有最小图禁止到达的可选能力。
    forbidden_artifacts = set(manifest.get("forbiddenMinimalDependencies", []))
    for artifact_name in minimal_artifacts:
        if artifact_name not in dependencies:
            findings.append(Finding("UNKNOWN_MINIMAL_ARTIFACT", artifact_name, "minimalArtifacts 引用了未知节点"))
            continue
        # pending 保存尚未展开的传递依赖。
        pending = list(dependencies[artifact_name])
        # reachable 防止传递遍历在已报告的环上无限循环。
        reachable: set[str] = set()
        while pending:
            dependency = pending.pop()
            if dependency in reachable:
                continue
            reachable.add(dependency)
            pending.extend(dependencies.get(dependency, ()))
        for forbidden in sorted(reachable & forbidden_artifacts):
            findings.append(
                Finding("FORBIDDEN_MINIMAL_DEPENDENCY", artifact_name, f"传递依赖了可选 artifact：{forbidden}"),
            )
    return dependencies, findings


def resolve_owner(relative_path: str, manifest: dict[str, Any]) -> str | None:
    """按精确覆盖优先、最长路径前缀次之解析唯一文件归属。"""

    # ownership 配置同时支持大目录规则和冻结兼容文件的精确覆盖。
    ownership: dict[str, Any] = manifest["ownership"]
    # exact_files 用于包名暂不能移动时的兼容归属。
    exact_files = ownership.get("files", {})
    if relative_path in exact_files:
        return exact_files[relative_path]
    # candidates 收集全部匹配规则，最长前缀表达最具体的目录所有权。
    candidates: list[tuple[int, str]] = []
    for rule in ownership.get("pathPrefixes", []):
        if not isinstance(rule, dict) or not isinstance(rule.get("path"), str):
            raise AuditConfigurationError("ownership.pathPrefixes 每项必须包含 path 和 artifact")
        # prefix 统一为目录形式，避免 `foo` 错误匹配 `foobar`。
        prefix = normalize_manifest_path(rule["path"], "ownership.pathPrefixes.path").rstrip("/") + "/"
        if relative_path.startswith(prefix):
            candidates.append((len(prefix), rule.get("artifact")))
    if not candidates:
        return None
    # 同长度规则必须指向同一 owner，否则清单自身含糊。
    maximum_length = max(length for length, _ in candidates)
    owners = {owner for length, owner in candidates if length == maximum_length}
    if len(owners) != 1:
        raise AuditConfigurationError(f"文件 {relative_path} 命中相同优先级的多个 owner：{sorted(owners)}")
    return next(iter(owners))


def extract_source_record(path: Path, relative_path: str, artifact: str) -> SourceRecord:
    """提取一个 Kotlin 或 Java 文件的 package、import 和顶层符号。"""

    # source_text 保留完整源码，正则均锚定行首以排除缩进后的嵌套声明。
    source_text = path.read_text(encoding="utf-8")
    # package_match 是项目内符号解析的必要输入。
    package_match = PACKAGE_PATTERN.search(source_text)
    if package_match is None:
        raise AuditConfigurationError(f"生产源文件缺少 package：{relative_path}")
    # package_name 是生成符号全名的稳定前缀。
    package_name = package_match.group(1)
    # symbol_names 合并三种顶层声明并去重。
    symbol_names = {
        *(match.group(1) for match in TYPE_DECLARATION_PATTERN.finditer(source_text)),
        *(match.group(1) for match in FUNCTION_DECLARATION_PATTERN.finditer(source_text)),
        *(match.group(1) for match in PROPERTY_DECLARATION_PATTERN.finditer(source_text)),
    }
    # symbols 只保存全名，后续可对 nested import 执行最长前缀匹配。
    symbols = tuple(sorted(f"{package_name}.{symbol_name}" for symbol_name in symbol_names))
    # imports 去重排序，避免源码重排造成报告噪声。
    imports = tuple(sorted(set(IMPORT_PATTERN.findall(source_text))))
    return SourceRecord(path, relative_path, artifact, package_name, imports, symbols)


def collect_sources(repository_root: Path, manifest: dict[str, Any]) -> tuple[list[SourceRecord], list[Finding]]:
    """枚举全部生产 Kotlin/Java 文件并保证每个文件有有效 owner。"""

    # source_root_settings 包含主 Kotlin 根和需要共同审计的附加 Java 根。
    source_root_settings = [manifest["sourceRoot"], *manifest.get("additionalSourceRoots", [])]
    # source_roots 是相对清单仓库根解析后的全部受控生产源集。
    source_roots: list[Path] = []
    for index, source_root_setting in enumerate(source_root_settings):
        # field_name 让配置错误能精确定位主根或附加根。
        field_name = "sourceRoot" if index == 0 else f"additionalSourceRoots[{index - 1}]"
        # source_root 始终限制为仓库内相对目录。
        source_root = repository_root / normalize_manifest_path(source_root_setting, field_name)
        if not source_root.is_dir():
            raise AuditConfigurationError(f"{field_name} 不存在：{source_root}")
        source_roots.append(source_root)
    # artifact_names 用于拒绝拼写错误的 owner。
    artifact_names = set(manifest["artifacts"])
    # records 是所有成功解析归属的生产源文件。
    records: list[SourceRecord] = []
    # findings 保存未归属或未知 owner，而不是把这些文件从审计中静默丢弃。
    findings: list[Finding] = []
    # source_entries 同时保留所属根；相对路径继续兼容已有 ownership 规则。
    source_entries: list[tuple[Path, Path]] = []
    for source_root in source_roots:
        for source_suffix in ("*.kt", "*.java"):
            source_entries.extend((source_path, source_root) for source_path in source_root.rglob(source_suffix))
    # 排序键包含相对路径和绝对路径，保证跨源根结果稳定。
    source_entries.sort(
        key=lambda entry: (entry[0].relative_to(entry[1]).as_posix(), entry[0].as_posix()),
    )
    # relative_path_counts 用于拒绝跨源根的同名歧义。
    relative_path_counts: dict[str, int] = {}
    for source_path, source_root in source_entries:
        # relative_path 是清单匹配和报告使用的 POSIX 路径。
        relative_path = source_path.relative_to(source_root).as_posix()
        relative_path_counts[relative_path] = relative_path_counts.get(relative_path, 0) + 1
        # owner 是精确覆盖或最长前缀解析结果。
        owner = resolve_owner(relative_path, manifest)
        if owner is None:
            findings.append(Finding("UNOWNED_SOURCE", relative_path, "没有命中任何 ownership 规则"))
            continue
        if owner not in artifact_names:
            findings.append(Finding("UNKNOWN_OWNER", relative_path, f"归属未知 artifact：{owner}"))
            continue
        records.append(extract_source_record(source_path, relative_path, owner))

    for relative_path, occurrence_count in sorted(relative_path_counts.items()):
        if occurrence_count > 1:
            findings.append(
                Finding(
                    "DUPLICATE_SOURCE_PATH",
                    relative_path,
                    f"在 {occurrence_count} 个 production source root 中重复",
                ),
            )

    # exact_files 必须指向真实生产源码，防止重构后遗留规则掩盖归属变化。
    exact_files = manifest["ownership"].get("files", {})
    # actual_paths 是 O(1) 检查精确规则的真实文件集合。
    actual_paths = {path.relative_to(source_root).as_posix() for path, source_root in source_entries}
    for exact_path in sorted(exact_files):
        normalized_path = normalize_manifest_path(exact_path, "ownership.files")
        if normalized_path not in actual_paths:
            findings.append(Finding("STALE_OWNERSHIP_OVERRIDE", normalized_path, "精确归属文件不存在"))
    return records, findings


def resolve_import_owners(
    imported_name: str,
    symbol_owners: dict[str, set[str]],
    package_owners: dict[str, set[str]],
) -> set[str]:
    """把显式项目 import 解析到一个或多个源 artifact。"""

    if imported_name.endswith(".*"):
        return set(package_owners.get(imported_name[:-2], set()))
    # candidate 允许导入 nested class；最长已知顶层符号负责确定源文件 owner。
    candidate = imported_name
    while "." in candidate:
        if candidate in symbol_owners:
            return set(symbol_owners[candidate])
        candidate = candidate.rsplit(".", 1)[0]
    return set()


def audit_sources(
    records: Sequence[SourceRecord],
    manifest: dict[str, Any],
    dependencies: dict[str, tuple[str, ...]],
) -> tuple[list[Finding], list[dict[str, str]], dict[str, list[str]]]:
    """校验源码依赖、平台 import 和 split package 声明。"""

    # symbol_owners 支持普通类、顶层函数、属性以及 nested import 的解析。
    symbol_owners: dict[str, set[str]] = {}
    # package_owners 用于 wildcard import 和 split package 审计。
    package_owners: dict[str, set[str]] = {}
    # project_import_prefixes 区分应解析的仓库代码与 Kotlin/第三方依赖。
    project_import_prefixes = tuple(manifest.get("projectImportPrefixes", ["com.purride."]))
    for record in records:
        package_owners.setdefault(record.package, set()).add(record.artifact)
        for symbol in record.symbols:
            symbol_owners.setdefault(symbol, set()).add(record.artifact)

    # findings 保存所有边界问题。
    findings: list[Finding] = []
    # observed_edges 是可审阅的精确 import 证据，而不是只有汇总计数。
    observed_edges: list[dict[str, str]] = []
    # platform_exceptions 仅允许精确文件和精确 import，禁止宽泛平台白名单。
    platform_exceptions: dict[tuple[str, str], str] = {}
    for exception in manifest.get("platformImportExceptions", []):
        if not isinstance(exception, dict) or not exception.get("reason"):
            raise AuditConfigurationError("platformImportExceptions 每项必须包含 file/import/reason")
        # exception_file 是 sourceRoot 相对路径。
        exception_file = normalize_manifest_path(exception.get("file", ""), "platformImportExceptions.file")
        # exception_import 必须是完整 import，不能使用通配前缀。
        exception_import = exception.get("import")
        if not isinstance(exception_import, str) or "*" in exception_import:
            raise AuditConfigurationError("平台兼容例外必须声明精确 import，不能使用通配符")
        platform_exceptions[(exception_file, exception_import)] = exception["reason"]

    for record in records:
        # artifact_config 提供唯一允许的平台前缀；默认完全禁止平台 import。
        artifact_config: dict[str, Any] = manifest["artifacts"][record.artifact]
        # allowed_platform_prefixes 只用于真正的平台适配 artifact。
        allowed_platform_prefixes = tuple(artifact_config.get("allowedPlatformImportPrefixes", []))
        for imported_name in record.imports:
            if imported_name.startswith(COMPOSE_IMPORT_PREFIX) and record.artifact != "pixel-compose":
                findings.append(
                    Finding("COMPOSE_LEAK", record.relative_path, f"{record.artifact} 直接导入 {imported_name}"),
                )
            if imported_name.startswith(PLATFORM_IMPORT_PREFIXES):
                # platform_allowed 要么来自 Android/Compose artifact 策略，要么来自单项冻结 ABI 例外。
                platform_allowed = imported_name.startswith(allowed_platform_prefixes) or (
                    record.relative_path,
                    imported_name,
                ) in platform_exceptions
                if not platform_allowed:
                    findings.append(
                        Finding("PLATFORM_IMPORT_LEAK", record.relative_path, f"{record.artifact} 直接导入 {imported_name}"),
                    )
            # target_owners 由项目顶层符号或 wildcard package 解析得到。
            target_owners = resolve_import_owners(imported_name, symbol_owners, package_owners)
            if not target_owners:
                if imported_name.startswith(project_import_prefixes):
                    findings.append(Finding("UNRESOLVED_PROJECT_IMPORT", record.relative_path, imported_name))
                continue
            for target_owner in sorted(target_owners):
                if target_owner == record.artifact:
                    continue
                # edge 保存实际源文件 import 证据，便于拆模块时逐边迁移。
                edge = {
                    "from": record.artifact,
                    "to": target_owner,
                    "file": record.relative_path,
                    "import": imported_name,
                }
                observed_edges.append(edge)
                if target_owner not in dependencies.get(record.artifact, ()):
                    findings.append(
                        Finding(
                            "UNDECLARED_SOURCE_DEPENDENCY",
                            record.relative_path,
                            f"{record.artifact} -> {target_owner}：{imported_name}",
                        ),
                    )

    # declared_split_packages 是兼容期允许跨 artifact 保持旧 package 的精确声明。
    declared_split_packages: dict[str, Any] = manifest.get("splitPackages", {})
    for package_name, owners in sorted(package_owners.items()):
        if len(owners) <= 1:
            continue
        # split_config 必须精确列出 owner 且说明保留原因。
        split_config = declared_split_packages.get(package_name)
        if not isinstance(split_config, dict) or not split_config.get("reason"):
            findings.append(Finding("UNDECLARED_SPLIT_PACKAGE", package_name, ", ".join(sorted(owners))))
            continue
        declared_owners = set(split_config.get("artifacts", []))
        if owners != declared_owners:
            findings.append(
                Finding(
                    "SPLIT_PACKAGE_OWNER_MISMATCH",
                    package_name,
                    f"实际={sorted(owners)}，声明={sorted(declared_owners)}",
                ),
            )

    # 已不再跨 artifact 的声明必须删除，防止未来误以为兼容例外仍然生效。
    for package_name in sorted(set(declared_split_packages) - set(package_owners)):
        findings.append(Finding("STALE_SPLIT_PACKAGE_DECLARATION", package_name, "源码中不存在该 package"))
    for package_name in sorted(set(declared_split_packages) & set(package_owners)):
        if len(package_owners[package_name]) <= 1:
            findings.append(
                Finding("STALE_SPLIT_PACKAGE_DECLARATION", package_name, "该 package 已由单一 artifact 持有"),
            )

    # split_package_report 将 set 转为稳定数组供 JSON 输出。
    split_package_report = {
        package_name: sorted(owners)
        for package_name, owners in sorted(package_owners.items())
        if len(owners) > 1
    }
    # 排序并去重边，避免一个 import 的多个解析路径产生重复证据。
    unique_edges = {
        (edge["from"], edge["to"], edge["file"], edge["import"]): edge
        for edge in observed_edges
    }
    sorted_edges = [unique_edges[key] for key in sorted(unique_edges)]
    return findings, sorted_edges, split_package_report


def write_report(report_path: Path, payload: dict[str, Any]) -> None:
    """写入确定性 JSON 报告并创建父目录。"""

    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def run_audit(manifest_path: Path) -> dict[str, Any]:
    """执行完整 artifact 边界审计并返回机器报告。"""

    # manifest 使用仓库相对路径；配置文件固定在模块 config 下，因此仓库根为上两级。
    manifest = load_manifest(manifest_path)
    # repository_root 可由清单显式覆盖，测试夹具默认使用清单父目录。
    repository_root_setting = manifest.get("repositoryRoot", ".")
    # repository_root 基于清单目录解析，避免依赖调用者当前工作目录。
    repository_root = (manifest_path.parent / repository_root_setting).resolve()
    # dependencies 和 graph_findings 是声明图证据。
    dependencies, graph_findings = validate_artifact_graph(manifest)
    # records 和 ownership_findings 是生产源集归属证据。
    records, ownership_findings = collect_sources(repository_root, manifest)
    # source_findings、observed_edges 和 split_packages 是源码级边界证据。
    source_findings, observed_edges, split_packages = audit_sources(records, manifest, dependencies)
    # findings 按类别/主体/详情排序，保证报告可稳定比较。
    findings = sorted(
        graph_findings + ownership_findings + source_findings,
        key=lambda finding: (finding.category, finding.subject, finding.detail),
    )
    # artifact_counts 显示每个 artifact 的物理源文件数量，包括零文件可选 artifact。
    artifact_counts = {artifact_name: 0 for artifact_name in sorted(manifest["artifacts"])}
    for record in records:
        artifact_counts[record.artifact] += 1
    # declared_edges 直接来自清单，用于独立验证无环目标图。
    declared_edges = [
        {"from": artifact_name, "to": dependency}
        for artifact_name in sorted(dependencies)
        for dependency in dependencies[artifact_name]
    ]
    return {
        "schemaVersion": 1,
        "status": "failed" if findings else "passed",
        "manifest": manifest_path.name,
        "sourceFileCount": len(records),
        "artifactFileCounts": artifact_counts,
        "declaredEdges": declared_edges,
        "observedSourceEdges": observed_edges,
        "splitPackages": split_packages,
        "findingCount": len(findings),
        "findings": [
            {"category": finding.category, "subject": finding.subject, "detail": finding.detail}
            for finding in findings
        ],
        "analysisLimitations": [
            "源码门禁解析显式 import；同 package 的未限定引用由后续物理模块独立编译门禁最终确认。",
            "平台例外只冻结当前 JVM 描述符；不得用于新增 Android UI API。",
        ],
    }


def main(arguments: Sequence[str] | None = None) -> int:
    """运行门禁，发现配置错误返回 2，发现边界问题返回 1。"""

    # parsed 是 CLI 或单元测试传入的显式参数。
    parsed = parse_arguments(sys.argv[1:] if arguments is None else arguments)
    try:
        # payload 同时供终端摘要和 CI JSON 消费。
        payload = run_audit(parsed.manifest.resolve())
    except AuditConfigurationError as error:
        # error_payload 确保配置错误也有机器证据，不能伪装为零文件成功。
        error_payload = {
            "schemaVersion": 1,
            "status": "error",
            "manifest": parsed.manifest.name,
            "findingCount": 0,
            "findings": [],
            "reason": str(error),
        }
        write_report(parsed.report, error_payload)
        print(f"pixel-artifact-boundaries: 配置错误：{error}", file=sys.stderr)
        return 2
    write_report(parsed.report, payload)
    if payload["status"] != "passed":
        print(
            f"pixel-artifact-boundaries: 失败，发现 {payload['findingCount']} 个问题；见 {parsed.report}",
            file=sys.stderr,
        )
        return 1
    print(
        "pixel-artifact-boundaries: 通过 "
        f"({payload['sourceFileCount']} 个源文件，{len(payload['declaredEdges'])} 条声明依赖)",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
