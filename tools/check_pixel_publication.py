#!/usr/bin/env python3
"""校验 Pixel SDK 临时 Maven 仓库中的全部正式发布物。"""

from __future__ import annotations

import argparse
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from typing import Any


# 1.0 正式发布必须同时提供的全部 Android library 坐标。
ARTIFACTS = (
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

# 每个坐标通过 POM 和 Gradle module metadata 必须传递的内部依赖。
EXPECTED_INTERNAL_DEPENDENCIES = {
    "pixel-core": set(),
    "pixel-runtime": {"pixel-core"},
    "pixel-widgets": {"pixel-core", "pixel-runtime"},
    "pixel-navigation": {"pixel-core", "pixel-runtime", "pixel-widgets"},
    "pixel-android": {"pixel-core", "pixel-runtime", "pixel-widgets", "pixel-navigation"},
    "pixel-testing": {"pixel-core", "pixel-runtime", "pixel-widgets", "pixel-navigation"},
    "pixel-debug": {
        "pixel-core",
        "pixel-runtime",
        "pixel-widgets",
        "pixel-navigation",
        "pixel-android",
        "pixel-testing",
    },
    "pixel-compose": {
        "pixel-core",
        "pixel-runtime",
        "pixel-widgets",
        "pixel-navigation",
        "pixel-android",
    },
    "pixel-engine": {
        "pixel-core",
        "pixel-runtime",
        "pixel-widgets",
        "pixel-navigation",
        "pixel-android",
        "pixel-testing",
        "pixel-debug",
    },
}

# 所有 AAR 必须显式声明的最低消费者构建环境。
EXPECTED_MIN_AGP = "8.10.0"
# 所有 AAR 必须显式声明的最低 compileSdk。
EXPECTED_MIN_COMPILE_SDK = "36"


def parse_args() -> argparse.Namespace:
    """解析发布仓库、版本和报告路径。"""

    # 命令行解析器同时服务本地门禁和 CI。
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--version", default="1.0.0")
    parser.add_argument("--report", type=Path, required=True)
    return parser.parse_args()


def require_single(version_directory: Path, pattern: str, label: str) -> Path:
    """返回唯一发布文件；缺失或重复时立即给出可操作错误。"""

    # 排除 Maven 为主体文件生成的校验和旁车文件。
    matches = sorted(path for path in version_directory.glob(pattern) if path.is_file())
    if len(matches) != 1:
        raise AssertionError(
            f"{version_directory}: expected exactly one {label} matching {pattern}, found {len(matches)}",
        )
    return matches[0]


def sha256(path: Path) -> str:
    """计算一个发布文件的 SHA-256。"""

    # 哈希器按块消费文件，避免把大型 AAR 一次性读入内存。
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while True:
            # 固定块大小使内存上界保持稳定。
            block = stream.read(1024 * 1024)
            if not block:
                break
            digest.update(block)
    return digest.hexdigest()


def read_zip_names(path: Path) -> list[str]:
    """读取 ZIP/JAR/AAR 中全部非目录条目名称。"""

    with zipfile.ZipFile(path) as archive:
        # 只保留真实文件，防止空目录让产物误判为非空。
        return [entry.filename for entry in archive.infolist() if not entry.is_dir()]


def read_aar_metadata(path: Path) -> dict[str, str]:
    """解析 AAR 内嵌的 AGP metadata properties。"""

    # AGP 约定的固定 metadata 路径。
    metadata_path = "META-INF/com/android/build/gradle/aar-metadata.properties"
    with zipfile.ZipFile(path) as archive:
        try:
            # 原始 properties 文本使用 UTF-8 且不允许重复 key。
            raw_metadata = archive.read(metadata_path).decode("utf-8")
        except KeyError as error:
            raise AssertionError(f"{path}: missing {metadata_path}") from error
    # 解析后的键值表只接受有效的非注释行。
    metadata: dict[str, str] = {}
    for raw_line in raw_metadata.splitlines():
        # 去除行首尾空白后再判断注释和空行。
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise AssertionError(f"{path}: malformed AAR metadata line: {raw_line!r}")
        # 每行只按第一个等号拆分，保留值中的合法等号。
        key, value = line.split("=", 1)
        if key in metadata:
            raise AssertionError(f"{path}: duplicate AAR metadata key: {key}")
        metadata[key] = value
    return metadata


def pom_internal_dependencies(path: Path) -> set[str]:
    """返回 POM 中全部 com.purride 内部依赖坐标。"""

    # Maven POM 使用固定命名空间。
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    # POM XML 根节点。
    root = ET.parse(path).getroot()
    # 已发布的内部 artifactId 集合。
    internal_dependencies: set[str] = set()
    for dependency in root.findall("m:dependencies/m:dependency", namespace):
        # 当前依赖的 groupId。
        group_id = dependency.findtext("m:groupId", default="", namespaces=namespace)
        # 当前依赖的 artifactId。
        artifact_id = dependency.findtext("m:artifactId", default="", namespaces=namespace)
        if group_id == "com.purride":
            internal_dependencies.add(artifact_id)
    return internal_dependencies


def module_internal_dependencies(module: dict[str, Any]) -> set[str]:
    """返回 Gradle module metadata 的运行时内部依赖并拒绝 project shortcut。"""

    # 全部发布 variant，至少应包含 API、runtime 与两类文档。
    variants = module.get("variants")
    if not isinstance(variants, list):
        raise AssertionError("Gradle module metadata has no variants array")
    # 聚合所有 variant 的内部坐标，API/runtime 的并集应等于 POM 依赖图。
    internal_dependencies: set[str] = set()
    for variant in variants:
        # 当前 variant 的依赖数组；文档 variant 可以为空。
        dependencies = variant.get("dependencies", [])
        if not isinstance(dependencies, list):
            raise AssertionError("Gradle module variant dependencies is not an array")
        for dependency in dependencies:
            if "project" in dependency:
                raise AssertionError("Gradle module metadata leaked a project dependency")
            if dependency.get("group") == "com.purride":
                # 当前内部依赖的模块名。
                module_name = dependency.get("module")
                if not isinstance(module_name, str):
                    raise AssertionError("Gradle module metadata has an invalid internal module name")
                internal_dependencies.add(module_name)
    return internal_dependencies


def validate_module_metadata(path: Path, artifact: str, expected_dependencies: set[str]) -> None:
    """校验 Gradle module metadata 的坐标、variant 和传递依赖。"""

    # JSON module metadata 根对象。
    module = json.loads(path.read_text(encoding="utf-8"))
    # 当前发布组件坐标。
    component = module.get("component", {})
    if component.get("group") != "com.purride" or component.get("module") != artifact:
        raise AssertionError(f"{path}: invalid component coordinate: {component}")
    # 全部 variant 名称用于确认二进制和文档模型同时发布。
    variant_names = {variant.get("name", "") for variant in module.get("variants", [])}
    # 文档类型通过属性判断，避免绑定 AGP 生成的易变 variant 名称。
    document_types = {
        variant.get("attributes", {}).get("org.gradle.docstype")
        for variant in module.get("variants", [])
    }
    # 二进制 usage 集合必须同时包含 API 与 runtime。
    usages = {
        variant.get("attributes", {}).get("org.gradle.usage")
        for variant in module.get("variants", [])
    }
    if "java-api" not in usages or "java-runtime" not in usages:
        raise AssertionError(f"{path}: missing java-api/java-runtime variants: {sorted(variant_names)}")
    if not {"sources", "javadoc"}.issubset(document_types):
        raise AssertionError(f"{path}: missing sources/javadoc variants")
    # module metadata 的内部依赖必须与模块归属图精确一致。
    actual_dependencies = module_internal_dependencies(module)
    if actual_dependencies != expected_dependencies:
        raise AssertionError(
            f"{path}: internal dependencies {sorted(actual_dependencies)} != {sorted(expected_dependencies)}",
        )


def validate_artifact(repository: Path, version: str, artifact: str) -> dict[str, Any]:
    """校验一个坐标的 AAR、POM、module、sources 与 Javadoc/KDoc。"""

    # 当前坐标的版本化 Maven 目录。
    version_directory = repository / "com" / "purride" / artifact / version
    if not version_directory.is_dir():
        raise AssertionError(f"Missing publication directory: {version_directory}")
    # 唯一主 AAR。
    aar = require_single(version_directory, "*.aar", "AAR")
    # 唯一 Maven POM。
    pom = require_single(version_directory, "*.pom", "POM")
    # 唯一 Gradle module metadata。
    module = require_single(version_directory, "*.module", "Gradle module metadata")
    # 唯一源码包。
    sources = require_single(version_directory, "*-sources.jar", "sources JAR")
    # 唯一 Javadoc/KDoc 包。
    javadoc = require_single(version_directory, "*-javadoc.jar", "Javadoc JAR")

    # AAR 中的非目录条目。
    aar_names = read_zip_names(aar)
    if "classes.jar" not in aar_names or "proguard.txt" not in aar_names:
        raise AssertionError(f"{aar}: missing classes.jar or consumer ProGuard rules")
    with zipfile.ZipFile(aar) as archive:
        # classes.jar 不能是空占位文件。
        classes_jar = archive.read("classes.jar")
        # consumer rules 必须真实进入发布物。
        consumer_rules = archive.read("proguard.txt").decode("utf-8").strip()
    if not classes_jar:
        raise AssertionError(f"{aar}: empty classes.jar")
    if not consumer_rules:
        raise AssertionError(f"{aar}: empty consumer ProGuard rules")

    # AAR metadata 决定不支持组合是否能在依赖解析阶段提前失败。
    aar_metadata = read_aar_metadata(aar)
    if aar_metadata.get("minAndroidGradlePluginVersion") != EXPECTED_MIN_AGP:
        raise AssertionError(f"{aar}: min AGP is not {EXPECTED_MIN_AGP}: {aar_metadata}")
    if aar_metadata.get("minCompileSdk") != EXPECTED_MIN_COMPILE_SDK:
        raise AssertionError(f"{aar}: min compileSdk is not {EXPECTED_MIN_COMPILE_SDK}: {aar_metadata}")

    # 源码包必须包含可读源文件且不得混入编译后 class。
    source_names = read_zip_names(sources)
    if not any(name.endswith((".kt", ".java")) for name in source_names):
        raise AssertionError(f"{sources}: no Kotlin/Java source")
    if any(name.endswith(".class") for name in source_names):
        raise AssertionError(f"{sources}: compiled class leaked into sources JAR")

    # Javadoc 由 Dokka/AGP 生成，至少包含入口和一个类型页面。
    javadoc_names = read_zip_names(javadoc)
    if "index.html" not in javadoc_names or not any(name.endswith(".html") and "/" in name for name in javadoc_names):
        raise AssertionError(f"{javadoc}: missing rendered API documentation")

    # 当前坐标应有的内部传递依赖。
    expected_dependencies = EXPECTED_INTERNAL_DEPENDENCIES[artifact]
    # POM 内部依赖必须精确等于模块归属图。
    pom_dependencies = pom_internal_dependencies(pom)
    if pom_dependencies != expected_dependencies:
        raise AssertionError(
            f"{pom}: internal dependencies {sorted(pom_dependencies)} != {sorted(expected_dependencies)}",
        )
    validate_module_metadata(module, artifact, expected_dependencies)

    # 每个坐标的可机读摘要包含真实文件名、大小和内容摘要。
    return {
        "artifact": artifact,
        "status": "passed",
        "files": {
            "aar": {"name": aar.name, "bytes": aar.stat().st_size, "sha256": sha256(aar)},
            "pom": {"name": pom.name, "bytes": pom.stat().st_size, "sha256": sha256(pom)},
            "module": {"name": module.name, "bytes": module.stat().st_size, "sha256": sha256(module)},
            "sources": {"name": sources.name, "bytes": sources.stat().st_size, "sha256": sha256(sources)},
            "javadoc": {"name": javadoc.name, "bytes": javadoc.stat().st_size, "sha256": sha256(javadoc)},
        },
        "aarMetadata": aar_metadata,
        "internalDependencies": sorted(expected_dependencies),
    }


def main() -> int:
    """校验全部坐标并原子写出成功报告。"""

    # 已解析的命令行参数。
    args = parse_args()
    # 逐坐标校验结果；任一异常都会阻止报告伪装为成功。
    artifact_reports = [
        validate_artifact(args.repository, args.version, artifact)
        for artifact in ARTIFACTS
    ]
    # 最终机读报告。
    report = {
        "schemaVersion": 1,
        "status": "passed",
        "repository": str(args.repository.resolve()),
        "version": args.version,
        "requirements": {
            "minAgp": EXPECTED_MIN_AGP,
            "minCompileSdk": int(EXPECTED_MIN_COMPILE_SDK),
        },
        "artifacts": artifact_reports,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    # 临时文件保证中途中断不会留下半份成功报告。
    temporary_report = args.report.with_suffix(args.report.suffix + ".tmp")
    temporary_report.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    temporary_report.replace(args.report)
    print(f"Pixel publication validation passed: {args.report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
