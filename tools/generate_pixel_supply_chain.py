#!/usr/bin/env python3
"""根据冻结依赖图和临时 Maven 仓库生成 Pixel SDK 的 SBOM、来源证明与校验和。"""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import uuid
from pathlib import Path
from typing import Any


# Pixel SDK 的 Maven group，与发布脚本和消费者坐标保持一致。
PIXEL_GROUP = "com.purride"
# 需要进入聚合 SBOM 的九个正式发布坐标。
PIXEL_ARTIFACTS = (
    "pixel-core",
    "pixel-runtime",
    "pixel-widgets",
    "pixel-navigation",
    "pixel-android",
    "pixel-testing",
    "pixel-debug",
    "pixel-compose",
    "pixel-engine",
)
# Maven 主体文件使用的校验算法；同时覆盖 Central 必需和现代强摘要。
CHECKSUM_ALGORITHMS = ("md5", "sha1", "sha256", "sha512")
# CycloneDX 序列号的稳定命名空间，不依赖运行时随机数。
SBOM_UUID_NAMESPACE = uuid.UUID("a5de819d-8447-4fb7-b605-4019b71dbad8")


def parse_args() -> argparse.Namespace:
    """解析依赖图、仓库、版本、元数据和输出路径。"""

    # 命令行解析器同时服务本地发布演练和 CI。
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dependency-graph", type=Path, required=True)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--repository-root", type=Path, required=True)
    parser.add_argument("--output-directory", type=Path, required=True)
    return parser.parse_args()


def read_properties(path: Path) -> dict[str, str]:
    """读取不含续行转义的受审 Java properties 元数据。"""

    # 最终键值表拒绝重复字段，避免 Gradle 与 Python 读取到不同语义。
    properties: dict[str, str] = {}
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        # 去除首尾空白后识别注释和空行。
        line = raw_line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        if "=" not in line:
            raise AssertionError(f"{path}:{line_number}: malformed property")
        # 属性值允许包含后续等号，因此只拆分一次。
        key, value = line.split("=", 1)
        key = key.strip()
        if key in properties:
            raise AssertionError(f"{path}:{line_number}: duplicate property {key}")
        properties[key] = value.strip()
    return properties


def hash_file(path: Path, algorithm: str) -> str:
    """按块计算文件摘要，避免把 AAR 一次性读入内存。"""

    # hashlib 构造器只接受受支持的固定算法名称。
    digest = hashlib.new(algorithm)
    with path.open("rb") as stream:
        while True:
            # 1 MiB 块让内存上界稳定且具有足够吞吐。
            block = stream.read(1024 * 1024)
            if not block:
                break
            digest.update(block)
    return digest.hexdigest()


def write_json_atomic(path: Path, document: dict[str, Any]) -> None:
    """以确定性键序和原子替换方式写出 JSON。"""

    # 同目录临时文件保证 rename 不跨文件系统。
    temporary_path = path.with_name(path.name + ".tmp")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path.write_text(
        json.dumps(document, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary_path.replace(path)


def write_checksum_sidecars(path: Path) -> dict[str, str]:
    """为一个发布文件写出 MD5、SHA-1、SHA-256 和 SHA-512 旁车。"""

    # 返回值供来源证明和机读报告复用，避免重复计算。
    checksums: dict[str, str] = {}
    for algorithm in CHECKSUM_ALGORITHMS:
        # 当前算法的十六进制摘要。
        checksum = hash_file(path, algorithm)
        checksums[algorithm] = checksum
        path.with_name(path.name + f".{algorithm}").write_text(checksum + "\n", encoding="ascii")
    return checksums


def publication_files(repository: Path, version: str) -> list[Path]:
    """返回九个坐标中需要签名和校验和的 Maven 主体文件。"""

    # 后缀集合只接受发布契约中的主体，不把已有旁车递归纳入。
    accepted_suffixes = (".aar", ".pom", ".module", ".jar")
    # 汇总后的稳定文件清单。
    files: list[Path] = []
    for artifact in PIXEL_ARTIFACTS:
        # 当前 Maven GAV 的版本目录。
        version_directory = repository / "com" / "purride" / artifact / version
        if not version_directory.is_dir():
            raise AssertionError(f"Missing publication directory: {version_directory}")
        # 过滤 maven-metadata 与摘要/签名旁车，只保留坐标自身的五类主体文件。
        artifact_files = [
            path
            for path in version_directory.iterdir()
            if path.is_file()
            and path.name.startswith(f"{artifact}-")
            and path.name.endswith(accepted_suffixes)
            and not path.name.endswith(".asc")
        ]
        # 每个 Android library 必须正好有 AAR、POM、module、sources 和 Javadoc。
        if len(artifact_files) != 5:
            raise AssertionError(
                f"{version_directory}: expected 5 primary files, found {sorted(path.name for path in artifact_files)}",
            )
        files.extend(artifact_files)
    return sorted(files)


def build_sbom(
    graph: dict[str, Any],
    metadata: dict[str, str],
    version: str,
) -> dict[str, Any]:
    """把 Gradle 解析图转换为 CycloneDX 1.7 JSON SBOM。"""

    # Gradle 图中的组件数组必须是对象列表。
    graph_components = graph.get("components")
    if not isinstance(graph_components, list):
        raise AssertionError("Dependency graph has no components array")
    # 许可证只有在用户确认后才能出现在 SBOM 中。
    license_confirmed = metadata.get("licenseStatus") == "CONFIRMED"
    # 转换后的 CycloneDX 组件按 purl 稳定排序。
    components: list[dict[str, Any]] = []
    for graph_component in graph_components:
        # 当前 Gradle 组件的 Maven 字段。
        group = str(graph_component["group"])
        name = str(graph_component["name"])
        component_version = str(graph_component["version"])
        purl = str(graph_component["purl"])
        # CycloneDX library 组件保留解析期 scope 和唯一 bom-ref。
        component: dict[str, Any] = {
            "type": "library",
            "bom-ref": purl,
            "group": group,
            "name": name,
            "version": component_version,
            "purl": purl,
            "scope": str(graph_component.get("scope", "required")),
        }
        if license_confirmed and group == PIXEL_GROUP:
            component["licenses"] = [
                {"license": {"name": metadata["licenseName"], "url": metadata["licenseUrl"]}},
            ]
        components.append(component)

    # 依赖数组沿用 Gradle 已解析边并只保留存在的 bom-ref。
    dependencies = sorted(
        (
            {
                "ref": str(dependency["ref"]),
                "dependsOn": sorted(str(reference) for reference in dependency.get("dependsOn", [])),
            }
            for dependency in graph.get("dependencies", [])
        ),
        key=lambda dependency: dependency["ref"],
    )
    # 内容指纹决定稳定 UUID，同一依赖图可复现相同 SBOM。
    serial_seed = json.dumps(
        {"components": components, "dependencies": dependencies},
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    # 聚合组件对应用户实际添加的 pixel-engine Maven 坐标。
    aggregate_purl = f"pkg:maven/{PIXEL_GROUP}/pixel-engine@{version}"
    # SBOM 的根组件携带项目主页和 issue tracker，便于独立消费。
    root_component: dict[str, Any] = {
        "type": "library",
        "bom-ref": aggregate_purl,
        "group": PIXEL_GROUP,
        "name": "pixel-engine",
        "version": version,
        "purl": aggregate_purl,
        "externalReferences": [
            {"type": "website", "url": metadata["projectUrl"]},
            {"type": "vcs", "url": metadata["scmUrl"]},
            {"type": "issue-tracker", "url": metadata["issueUrl"]},
        ],
    }
    if license_confirmed:
        root_component["licenses"] = [
            {"license": {"name": metadata["licenseName"], "url": metadata["licenseUrl"]}},
        ]
    return {
        "bomFormat": "CycloneDX",
        "specVersion": "1.7",
        "serialNumber": f"urn:uuid:{uuid.uuid5(SBOM_UUID_NAMESPACE, serial_seed)}",
        "version": 1,
        "metadata": {
            "component": root_component,
            "properties": [
                {"name": "com.purride.pixel:dependency-locking", "value": "releaseCompileClasspath,releaseRuntimeClasspath"},
                {"name": "com.purride.pixel:license-status", "value": metadata["licenseStatus"]},
            ],
        },
        "components": sorted(components, key=lambda component: component["purl"]),
        "dependencies": dependencies,
    }


def git_output(repository_root: Path, *arguments: str) -> str:
    """执行只读 Git 查询并返回去除尾部换行的文本。"""

    # Git 子进程禁止交互并在失败时保留可诊断异常。
    result = subprocess.run(
        ["git", *arguments],
        cwd=repository_root,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def build_provenance(
    repository_root: Path,
    metadata: dict[str, str],
    version: str,
    repository: Path,
    subjects: list[Path],
) -> dict[str, Any]:
    """生成 in-toto Statement v1 + SLSA Provenance v1 构建来源。"""

    # 当前源码提交是来源材料的不可变标识。
    commit = git_output(repository_root, "rev-parse", "HEAD")
    # 脏工作树状态必须显式记录，防止本地候选伪装成已提交构建。
    dirty = bool(git_output(repository_root, "status", "--porcelain"))
    # 每个 Maven 主体文件成为可独立验证的 provenance subject。
    provenance_subjects = [
        {
            "name": path.relative_to(repository).as_posix(),
            "digest": {"sha256": hash_file(path, "sha256")},
        }
        for path in subjects
    ]
    # 受版本控制的锁文件和依赖校验元数据构成解析材料。
    material_paths = sorted(repository_root.glob("pixel-*/gradle.lockfile"))
    material_paths.extend(
        path
        for path in (
            repository_root / "settings-gradle.lockfile",
            repository_root / "gradle" / "verification-metadata.xml",
        )
        if path.is_file()
    )
    # 来源材料同时记录 Git 提交和每个依赖防篡改文件。
    resolved_dependencies: list[dict[str, Any]] = [
        {"uri": metadata["scmUrl"], "digest": {"gitCommit": commit}},
    ]
    resolved_dependencies.extend(
        {
            "uri": path.relative_to(repository_root).as_posix(),
            "digest": {"sha256": hash_file(path, "sha256")},
        }
        for path in material_paths
    )
    return {
        "_type": "https://in-toto.io/Statement/v1",
        "subject": provenance_subjects,
        "predicateType": "https://slsa.dev/provenance/v1",
        "predicate": {
            "buildDefinition": {
                "buildType": f"{metadata['projectUrl']}/blob/main/pixel-engine/docs/build-type-gradle-android.md",
                "externalParameters": {
                    "version": version,
                    "tasks": "publishReleasePublicationToPixelStagingRepository",
                    "signing": "openpgp-in-memory",
                },
                "internalParameters": {
                    "dirtyWorktree": dirty,
                    "dependencyVerification": "strict",
                    "dependencyLocking": "releaseCompileClasspath,releaseRuntimeClasspath",
                },
                "resolvedDependencies": resolved_dependencies,
            },
            "runDetails": {
                "builder": {
                    "id": f"{metadata['projectUrl']}/actions/workflows/pixel-engine.yml",
                    "version": {"gradle": "9.1.0", "java": "21"},
                },
                "metadata": {"invocationId": f"git:{commit}:dirty={str(dirty).lower()}"},
            },
        },
    }


def main() -> int:
    """生成发布补充物、复制到聚合坐标并写出全部 checksum。"""

    # 已解析的命令行参数。
    args = parse_args()
    # 受审发布元数据。
    metadata = read_properties(args.metadata)
    # Gradle 解析得到的稳定依赖图。
    graph = json.loads(args.dependency_graph.read_text(encoding="utf-8"))
    # Maven 仓库中已有的全部主体发布文件。
    primary_files = publication_files(args.repository, args.version)
    # 独立报告目录中的 CycloneDX SBOM 路径。
    sbom_path = args.output_directory / "pixel-engine-sbom.cdx.json"
    # 独立报告目录中的 SLSA provenance 路径。
    provenance_path = args.output_directory / "pixel-engine-provenance.intoto.json"
    write_json_atomic(sbom_path, build_sbom(graph, metadata, args.version))
    write_json_atomic(
        provenance_path,
        build_provenance(
            args.repository_root,
            metadata,
            args.version,
            args.repository,
            primary_files,
        ),
    )

    # 聚合坐标目录是远程消费者获取补充物的固定位置。
    aggregate_directory = (
        args.repository / "com" / "purride" / "pixel-engine" / args.version
    )
    # 仓库内使用带版本的 Maven classifier 风格文件名。
    published_sbom = aggregate_directory / f"pixel-engine-{args.version}-sbom.cdx.json"
    # provenance 使用明确的 in-toto 扩展名，避免被当作普通构建配置。
    published_provenance = aggregate_directory / f"pixel-engine-{args.version}-provenance.intoto.json"
    published_sbom.write_bytes(sbom_path.read_bytes())
    published_provenance.write_bytes(provenance_path.read_bytes())

    # Gradle 已生成主体 checksum，这里重算并统一扩充四种算法，验证文件内容而非信任旧旁车。
    checksum_targets = primary_files + [published_sbom, published_provenance]
    # 机读摘要用于后续门禁绑定本轮文件数量和 SHA-256。
    checksum_report = []
    for target in checksum_targets:
        # 当前发布文件的全部摘要。
        checksums = write_checksum_sidecars(target)
        checksum_report.append(
            {
                "path": target.relative_to(args.repository).as_posix(),
                "bytes": target.stat().st_size,
                "checksums": checksums,
            },
        )
    # 发布补充物自身也在报告目录保留强摘要。
    write_checksum_sidecars(sbom_path)
    write_checksum_sidecars(provenance_path)
    # 生成器结果不把许可证未确认伪装成成功决策。
    generation_report = {
        "schemaVersion": 1,
        "status": "generated",
        "version": args.version,
        "licenseStatus": metadata.get("licenseStatus", "MISSING"),
        "primaryFileCount": len(primary_files),
        "publishedSupplementCount": 2,
        "files": checksum_report,
    }
    write_json_atomic(args.output_directory / "generation-report.json", generation_report)
    print(
        "Pixel supply-chain documents generated: "
        f"{len(primary_files)} primary files, {len(graph.get('components', []))} components",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
