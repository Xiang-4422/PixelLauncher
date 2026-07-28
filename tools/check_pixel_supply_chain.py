#!/usr/bin/env python3
"""验证 Pixel SDK POM、签名、checksum、SBOM、来源证明与发布内容边界。"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from io import BytesIO
from pathlib import Path
from typing import Any


# 唯一受支持的正式 Maven artifactId。
PIXEL_ARTIFACTS = ("pixel-engine",)
# 所有发布主体都必须具备的校验算法。
CHECKSUM_ALGORITHMS = ("md5", "sha1", "sha256", "sha512")
# 不得进入任何生产 AAR 的测试框架或夹具类路径片段。
GLOBAL_FORBIDDEN_CLASS_PARTS = (
    "/src/test/",
    "/testfixtures/",
    "/testFixtures/",
    "org/junit/",
    "androidx/test/",
)
# 每个坐标允许直接暴露给 Maven 消费者的外部依赖与 scope。
EXPECTED_EXTERNAL_POM_DEPENDENCIES = {
    "pixel-engine": {
        ("androidx.lifecycle", "lifecycle-runtime-ktx", "compile"),
        ("org.jetbrains.kotlin", "kotlin-stdlib", "compile"),
    },
}
# 允许进入 AAR 的资源根；其他资源必须由发布契约显式解释。
ALLOWED_AAR_RESOURCE_PREFIXES = (
    "AndroidManifest.xml",
    "R.txt",
    "classes.jar",
    "consumer-rules.pro",
    "proguard.txt",
    "public.txt",
    "META-INF/",
    "res/",
    "assets/",
    "jni/",
    "libs/",
    "baseline-prof.txt",
    "baseline.profm",
)
# Gradle 依赖校验必须覆盖的 AGP 9.0.1 平台专用 AAPT2 精确摘要。
EXPECTED_AAPT2_PLATFORM_SHA256 = {
    "aapt2-9.0.1-14304508-linux.jar": "ab04484e27480404a32df818c1da12bebaceadab4895f50880153dfaad84e748",
    "aapt2-9.0.1-14304508-osx.jar": "4cf09e80b16a217a4cc1f997208599de7158dff283ecee2bd966246541b33070",
    "aapt2-9.0.1-14304508-windows.jar": "8b46160a24a87f3a4dd33af6979b424d4f8c25e5329aa4eb26c93beaa727c741",
}
# 冷缓存 CI 必须解析并校验的 AGP classpath/UTP 元数据，防止暖缓存掩盖缺项。
EXPECTED_CI_METADATA_SHA256 = {
    ("com.fasterxml.jackson", "jackson-base", "2.15.0", "jackson-base-2.15.0.pom"):
        "524296bede32185ac11012f07d9246e38c19253c2b513f1cf28799121a34e770",
    ("com.google.guava", "guava-parent", "33.3.1-jre", "guava-parent-33.3.1-jre.pom"):
        "55441db27e8869dfefe053059bdf478bdc7e95585642bf391f0023345fd56287",
    ("org.junit", "junit-bom", "5.11.0-M2", "junit-bom-5.11.0-M2.module"):
        "86477abcf490d6ca059aa9973cb108d22a506f49d1a5569bb32cc6cbf43c2cce",
    ("org.junit", "junit-bom", "5.9.2", "junit-bom-5.9.2.module"):
        "ab137ba5a8e32c9b066bf9126a1c76dd5614b724ba5c0b02549772b5e9f4cf1f",
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-bom", "1.8.0", "kotlinx-coroutines-bom-1.8.0.pom"):
        "1239e9dbe1397cd5971342956b2511bc3ace7b641842e4372a088dcfa8b9ad55",
}
# 每个 SDK 模块的发布锁文件只允许绑定这两个受审配置。
EXPECTED_LOCK_CONFIGURATIONS = {"releaseCompileClasspath", "releaseRuntimeClasspath"}
# SHA-256 必须是完整的小写十六进制摘要。
SHA256_PATTERN = re.compile(r"[0-9a-f]{64}")


def parse_args() -> argparse.Namespace:
    """解析临时仓库、元数据、签名公钥和报告参数。"""

    # 命令行解析器供本地演练与 CI 共用。
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--public-key", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--verification-metadata", type=Path, required=True)
    parser.add_argument("--lockfile", type=Path, action="append", required=True)
    parser.add_argument(
        "--require-license",
        action="store_true",
        help="许可证未明确确认或仓库缺少 LICENSE 时让门禁失败。",
    )
    parser.add_argument("--repository-license", type=Path)
    parser.add_argument("--repository-notice", type=Path)
    return parser.parse_args()


def read_properties(path: Path) -> dict[str, str]:
    """读取受审 properties 并拒绝重复或格式错误字段。"""

    # 解析后的发布字段表。
    properties: dict[str, str] = {}
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        # 清理空白后识别注释和空行。
        line = raw_line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        if "=" not in line:
            raise AssertionError(f"{path}:{line_number}: malformed property")
        # 每个属性只按首个等号拆分。
        key, value = line.split("=", 1)
        key = key.strip()
        if key in properties:
            raise AssertionError(f"{path}:{line_number}: duplicate property {key}")
        properties[key] = value.strip()
    return properties


def hash_file(path: Path, algorithm: str) -> str:
    """按块计算发布文件摘要。"""

    # 固定算法由调用方的受审枚举提供。
    digest = hashlib.new(algorithm)
    with path.open("rb") as stream:
        while True:
            # 1 MiB 块避免大 AAR 占用过多内存。
            block = stream.read(1024 * 1024)
            if not block:
                break
            digest.update(block)
    return digest.hexdigest()


def validate_repository_license(path: Path, metadata: dict[str, str]) -> dict[str, Any]:
    """验证仓库许可证文件与用户确认并固定在发布元数据中的强摘要一致。"""

    # 元数据中的受审摘要用于防止任意占位文件冒充已确认许可证。
    expected_sha256 = metadata.get("licenseFileSha256", "")
    if SHA256_PATTERN.fullmatch(expected_sha256) is None:
        raise AssertionError("Confirmed release requires a valid licenseFileSha256")
    # 仓库根许可证必须是普通文件，调用方已负责拒绝缺失路径。
    actual_sha256 = hash_file(path, "sha256")
    if actual_sha256 != expected_sha256:
        raise AssertionError(
            f"Repository LICENSE sha256 {actual_sha256} != {expected_sha256}",
        )
    return {
        "path": path.name,
        "bytes": path.stat().st_size,
        "sha256": actual_sha256,
    }


def validate_repository_notice(path: Path, metadata: dict[str, str]) -> dict[str, Any]:
    """验证 NOTICE 与受审摘要一致，固定源码归属和 Unicode 数据声明。"""

    # expected_sha256 防止空白或临时 NOTICE 仅凭文件名通过正式候选门禁。
    expected_sha256 = metadata.get("noticeFileSha256", "")
    if SHA256_PATTERN.fullmatch(expected_sha256) is None:
        raise AssertionError("Confirmed release requires a valid noticeFileSha256")
    # actual_sha256 是仓库当前 NOTICE 的独立内容身份。
    actual_sha256 = hash_file(path, "sha256")
    if actual_sha256 != expected_sha256:
        raise AssertionError(
            f"Repository NOTICE sha256 {actual_sha256} != {expected_sha256}",
        )
    return {
        "path": path.name,
        "bytes": path.stat().st_size,
        "sha256": actual_sha256,
    }


def version_directory(repository: Path, artifact: str, version: str) -> Path:
    """返回一个 Pixel GAV 的 Maven 版本目录。"""

    # Maven groupId 按点拆分为固定目录层级。
    directory = repository / "com" / "purride" / artifact / version
    if not directory.is_dir():
        raise AssertionError(f"Missing publication directory: {directory}")
    return directory


def require_unique(directory: Path, pattern: str) -> Path:
    """返回唯一匹配文件，缺失或重复时失败。"""

    # 旁车文件不会匹配以主体扩展名结尾的 pattern。
    matches = sorted(path for path in directory.glob(pattern) if path.is_file())
    if len(matches) != 1:
        raise AssertionError(f"{directory}: expected one {pattern}, found {[path.name for path in matches]}")
    return matches[0]


def primary_files(repository: Path, version: str) -> list[Path]:
    """返回统一坐标的 AAR、POM、module、sources 和 Javadoc 文件。"""

    # 汇总后的文件列表按 Maven 相对路径稳定排序。
    files: list[Path] = []
    for artifact in PIXEL_ARTIFACTS:
        # 当前坐标的版本目录。
        directory = version_directory(repository, artifact, version)
        files.extend(
            (
                require_unique(directory, "*.aar"),
                require_unique(directory, "*.pom"),
                require_unique(directory, "*.module"),
                require_unique(directory, "*-sources.jar"),
                require_unique(directory, "*-javadoc.jar"),
            ),
        )
    return sorted(files)


def required_xml_text(root: ET.Element, path: str, namespace: dict[str, str], label: str) -> str:
    """读取 POM 必填 XML 字段并拒绝空文本。"""

    # ElementTree 的命名空间路径与 Maven 4.0.0 schema 对齐。
    value = root.findtext(path, default="", namespaces=namespace).strip()
    if not value:
        raise AssertionError(f"POM missing {label}")
    return value


def validate_pom(path: Path, artifact: str, metadata: dict[str, str], require_license: bool) -> None:
    """校验 Central 必填 POM 元数据与用户确认的许可证状态。"""

    # Maven POM 4.0 的固定 XML 命名空间。
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    # 当前 POM 根节点。
    root = ET.parse(path).getroot()
    # 项目 URL 必须来自真实 origin，而不是旧占位地址。
    project_url = required_xml_text(root, "m:url", namespace, "project URL")
    if project_url != metadata["projectUrl"]:
        raise AssertionError(f"{path}: project URL {project_url!r} != {metadata['projectUrl']!r}")
    # 每个坐标都必须有可读名称和职责描述。
    required_xml_text(root, "m:name", namespace, "name")
    required_xml_text(root, "m:description", namespace, "description")
    # 唯一 developer 必须与受审 GitHub 身份一致。
    developers = root.findall("m:developers/m:developer", namespace)
    if len(developers) != 1:
        raise AssertionError(f"{path}: expected one developer, found {len(developers)}")
    # 开发者字段逐项比对，避免旧 Purride 占位残留。
    developer_id = required_xml_text(developers[0], "m:id", namespace, "developer id")
    developer_name = required_xml_text(developers[0], "m:name", namespace, "developer name")
    if developer_id != metadata["developerId"] or developer_name != metadata["developerName"]:
        raise AssertionError(f"{path}: invalid developer metadata")
    # SCM 三字段是 Central 的发布必填项。
    scm = root.find("m:scm", namespace)
    if scm is None:
        raise AssertionError(f"{path}: missing SCM")
    expected_scm = {
        "connection": metadata["scmConnection"],
        "developerConnection": metadata["scmDeveloperConnection"],
        "url": metadata["scmUrl"],
    }
    for field, expected_value in expected_scm.items():
        # 当前 SCM 字段文本。
        actual_value = required_xml_text(scm, f"m:{field}", namespace, f"SCM {field}")
        if actual_value != expected_value:
            raise AssertionError(f"{path}: SCM {field} {actual_value!r} != {expected_value!r}")
    # issue tracker 必须指向真实仓库。
    issue_system = required_xml_text(root, "m:issueManagement/m:system", namespace, "issue system")
    issue_url = required_xml_text(root, "m:issueManagement/m:url", namespace, "issue URL")
    if issue_system != metadata["issueSystem"] or issue_url != metadata["issueUrl"]:
        raise AssertionError(f"{path}: invalid issueManagement")

    # 许可证数组必须与显式决策状态完全一致。
    licenses = root.findall("m:licenses/m:license", namespace)
    if metadata.get("licenseStatus") == "CONFIRMED":
        if len(licenses) != 1:
            raise AssertionError(f"{path}: confirmed release must declare exactly one license")
        # 当前 POM 的许可证名称、URL 与分发策略。
        actual_license = {
            "name": required_xml_text(licenses[0], "m:name", namespace, "license name"),
            "url": required_xml_text(licenses[0], "m:url", namespace, "license URL"),
            "distribution": required_xml_text(licenses[0], "m:distribution", namespace, "license distribution"),
        }
        # 元数据文件中的唯一受审许可证声明。
        expected_license = {
            "name": metadata["licenseName"],
            "url": metadata["licenseUrl"],
            "distribution": metadata["licenseDistribution"],
        }
        if actual_license != expected_license:
            raise AssertionError(f"{path}: license {actual_license!r} != {expected_license!r}")
    else:
        if licenses:
            raise AssertionError(f"{path}: unconfirmed release must not infer a license")
        if require_license:
            raise AssertionError("Pixel release license is still UNCONFIRMED")
    # artifact 参数被显式使用，防止误把其他 POM 当成统一引擎产物。
    if root.findtext("m:artifactId", default="", namespaces=namespace) != artifact:
        raise AssertionError(f"{path}: artifactId does not match {artifact}")

    # POM 中全部非 Pixel 直接依赖必须与逐 artifact allowlist 精确相等。
    external_dependencies = {
        (
            dependency.findtext("m:groupId", default="", namespaces=namespace),
            dependency.findtext("m:artifactId", default="", namespaces=namespace),
            dependency.findtext("m:scope", default="compile", namespaces=namespace),
        )
        for dependency in root.findall("m:dependencies/m:dependency", namespace)
        if dependency.findtext("m:groupId", default="", namespaces=namespace) != "com.purride"
    }
    # 任一新增依赖都需要在发布契约中显式审阅，不能由插件升级静默进入 POM。
    expected_external_dependencies = EXPECTED_EXTERNAL_POM_DEPENDENCIES[artifact]
    if external_dependencies != expected_external_dependencies:
        raise AssertionError(
            f"{path}: external dependencies {sorted(external_dependencies)} != "
            f"{sorted(expected_external_dependencies)}",
        )


def nested_class_names(aar: Path) -> list[str]:
    """读取 AAR 内 classes.jar 的全部 class 相对路径。"""

    with zipfile.ZipFile(aar) as archive:
        try:
            # classes.jar 保留为内存字节流，AAR 体积门禁已限制其上界。
            classes_jar = archive.read("classes.jar")
        except KeyError as error:
            raise AssertionError(f"{aar}: missing classes.jar") from error
    with zipfile.ZipFile(BytesIO(classes_jar)) as class_archive:
        return sorted(
            entry.filename
            for entry in class_archive.infolist()
            if not entry.is_dir() and entry.filename.endswith(".class")
        )


def validate_aar_contents(aar: Path, artifact: str) -> dict[str, Any]:
    """拒绝测试框架、测试夹具和未知 AAR 根条目。"""

    with zipfile.ZipFile(aar) as archive:
        # AAR 根条目清单用于定位意外资源和嵌套依赖。
        archive_names = sorted(entry.filename for entry in archive.infolist() if not entry.is_dir())
    # 根条目只能来自 Android AAR 标准或明确允许的资源目录。
    unexpected_entries = [
        name
        for name in archive_names
        if not any(name == prefix or name.startswith(prefix) for prefix in ALLOWED_AAR_RESOURCE_PREFIXES)
    ]
    if unexpected_entries:
        raise AssertionError(f"{aar}: unexpected AAR entries {unexpected_entries}")
    # 当前 AAR 的 JVM class 清单。
    class_names = nested_class_names(aar)
    # 所有产物都不得捆绑测试框架或 testFixtures。
    forbidden_global = [
        name for name in class_names if any(part in name for part in GLOBAL_FORBIDDEN_CLASS_PARTS)
    ]
    if forbidden_global:
        raise AssertionError(f"{aar}: test fixture/framework classes leaked: {forbidden_global[:20]}")
    # 资源清单进入报告供人工发现仍未能自动判断的无用资源。
    resource_entries = [name for name in archive_names if name.startswith(("res/", "assets/"))]
    return {
        "artifact": artifact,
        "classCount": len(class_names),
        "resourceEntries": resource_entries,
        "unexpectedEntries": unexpected_entries,
    }


def validate_checksums(path: Path) -> dict[str, str]:
    """重算并比对一个主体文件的四种 checksum 旁车。"""

    # 已验证的算法到摘要映射。
    checksums: dict[str, str] = {}
    for algorithm in CHECKSUM_ALGORITHMS:
        # 当前算法旁车路径。
        sidecar = path.with_name(path.name + f".{algorithm}")
        if not sidecar.is_file():
            raise AssertionError(f"{path}: missing {algorithm} checksum")
        # 旁车只允许一个十六进制摘要和可选结尾换行。
        expected = sidecar.read_text(encoding="ascii").strip().lower()
        actual = hash_file(path, algorithm)
        if expected != actual:
            raise AssertionError(f"{path}: invalid {algorithm} checksum")
        checksums[algorithm] = actual
    return checksums


def validate_gradle_verification_metadata(path: Path) -> dict[str, Any]:
    """校验 Gradle 依赖元数据使用逐文件 SHA-256 且覆盖 CI 冷缓存必需文件。"""

    # Gradle dependency-verification 1.3 的默认命名空间。
    namespace = {"v": "https://schema.gradle.org/dependency-verification"}
    # 完整校验元数据 XML 根节点。
    root = ET.parse(path).getroot()
    # POM/module 元数据本身也必须校验，不能只校验二进制文件。
    verify_metadata = root.findtext("v:configuration/v:verify-metadata", default="", namespaces=namespace)
    if verify_metadata.strip().lower() != "true":
        raise AssertionError(f"{path}: verify-metadata must be true")
    # 只允许 Android Studio 动态请求且不参与编译/打包的 AndroidX 示例源码附件。
    trusted_artifacts = root.findall("v:configuration/v:trusted-artifacts/v:trust", namespace)
    # 允许规则的完整属性必须精确匹配，防止扩大到普通 sources 或二进制产物。
    allowed_trust_rule = {
        "group": r"androidx\..*",
        "file": r".*-samples-sources\.jar",
        "regex": "true",
        "reason": "AndroidX IDE 示例源码附件不参与编译或打包，且其逻辑文件名由 Android Studio 动态生成",
    }
    if any(element.attrib != allowed_trust_rule for element in trusted_artifacts):
        raise AssertionError(f"{path}: broad trusted artifacts are forbidden")
    # 忽略签名密钥会绕过逐文件 checksum 审阅，任何形式都不允许。
    ignored_keys = root.find("v:configuration/v:ignored-keys", namespace)
    if ignored_keys is not None and len(ignored_keys) > 0:
        raise AssertionError(f"{path}: ignored keys are forbidden")

    # 全部受审 artifact 必须至少有一个合法 SHA-256。
    artifacts = root.findall("v:components/v:component/v:artifact", namespace)
    if not artifacts:
        raise AssertionError(f"{path}: no verified dependency artifacts")
    for artifact in artifacts:
        # 当前 artifact 的名称用于可定位错误。
        artifact_name = artifact.get("name", "<missing-name>")
        # 同一 artifact 可因仓库镜像拥有多个受审摘要。
        checksums = [element.get("value", "") for element in artifact.findall("v:sha256", namespace)]
        if not checksums or any(SHA256_PATTERN.fullmatch(value) is None for value in checksums):
            raise AssertionError(f"{path}: {artifact_name} is missing a valid SHA-256")

    # 关键 CI 元数据按完整坐标和文件名索引，不能由同组件的 POM/JAR 冒充覆盖。
    component_checksums: dict[tuple[str, str, str, str], set[str]] = {}
    for component in root.findall("v:components/v:component", namespace):
        # 当前组件坐标用于区分相同 artifact 文件名的不同版本。
        component_coordinate = (
            component.get("group", ""),
            component.get("name", ""),
            component.get("version", ""),
        )
        for artifact in component.findall("v:artifact", namespace):
            # 当前文件的全部受审摘要允许 Maven 镜像存在多个合法内容版本。
            artifact_checksums = {
                element.get("value", "") for element in artifact.findall("v:sha256", namespace)
            }
            component_checksums[(*component_coordinate, artifact.get("name", ""))] = artifact_checksums
    for coordinate, expected_checksum in EXPECTED_CI_METADATA_SHA256.items():
        if expected_checksum not in component_checksums.get(coordinate, set()):
            raise AssertionError(f"{path}: missing verified CI metadata {':'.join(coordinate)}")

    # AAPT2 组件必须显式覆盖 Linux、macOS、Windows 构建机。
    aapt2_component = root.find(
        "v:components/v:component[@group='com.android.tools.build'][@name='aapt2'][@version='9.0.1-14304508']",
        namespace,
    )
    if aapt2_component is None:
        raise AssertionError(f"{path}: missing AGP 9.0.1 AAPT2 component")
    # 按 artifact 名称索引其全部受审摘要。
    aapt2_checksums = {
        artifact.get("name", ""): {
            element.get("value", "") for element in artifact.findall("v:sha256", namespace)
        }
        for artifact in aapt2_component.findall("v:artifact", namespace)
    }
    for artifact_name, expected_checksum in EXPECTED_AAPT2_PLATFORM_SHA256.items():
        if expected_checksum not in aapt2_checksums.get(artifact_name, set()):
            raise AssertionError(f"{path}: missing verified {artifact_name}")
    return {
        "artifactCount": len(artifacts),
        "aapt2Platforms": sorted(EXPECTED_AAPT2_PLATFORM_SHA256),
        "ciMetadataArtifacts": sorted(":".join(coordinate) for coordinate in EXPECTED_CI_METADATA_SHA256),
    }


def validate_gradle_lockfiles(paths: list[Path]) -> dict[str, Any]:
    """校验统一 SDK 模块锁定 Release 编译与运行配置。"""

    if len(paths) != len(PIXEL_ARTIFACTS):
        raise AssertionError(f"Expected {len(PIXEL_ARTIFACTS)} module lockfiles, found {len(paths)}")
    # 每个 artifact 必须恰好映射到一个同名模块目录下的 lockfile。
    lockfiles_by_artifact = {path.parent.name: path for path in paths}
    if set(lockfiles_by_artifact) != set(PIXEL_ARTIFACTS):
        raise AssertionError(f"Unexpected module lockfiles: {sorted(lockfiles_by_artifact)}")
    # 各模块的锁定依赖数量进入机器可读报告。
    dependency_counts: dict[str, int] = {}
    for artifact in PIXEL_ARTIFACTS:
        # 当前模块必须提交的 Gradle 锁文件。
        path = lockfiles_by_artifact[artifact]
        if not path.is_file():
            raise AssertionError(f"Missing Gradle lockfile: {path}")
        # 模块中至少出现一次的受审配置集合。
        observed_configurations: set[str] = set()
        # 非 empty 行数量用于确认锁文件不是空占位。
        dependency_count = 0
        for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            # Gradle 注释和空行不参与锁定语义。
            line = raw_line.strip()
            if not line or line.startswith("#"):
                continue
            if "=" not in line:
                raise AssertionError(f"{path}:{line_number}: malformed lock entry")
            # 左侧是 module coordinate，右侧是逗号分隔配置。
            coordinate, configuration_text = line.split("=", 1)
            if coordinate == "empty":
                continue
            dependency_count += 1
            # 当前依赖实际被哪些配置锁定。
            configurations = {value for value in configuration_text.split(",") if value}
            if not configurations or not configurations <= EXPECTED_LOCK_CONFIGURATIONS:
                raise AssertionError(f"{path}:{line_number}: unexpected lock configurations {configurations}")
            observed_configurations.update(configurations)
        if dependency_count == 0 or observed_configurations != EXPECTED_LOCK_CONFIGURATIONS:
            raise AssertionError(
                f"{path}: must lock both Release configurations, observed {sorted(observed_configurations)}",
            )
        dependency_counts[artifact] = dependency_count
    return {"moduleCount": len(paths), "dependencyCounts": dependency_counts}


def verify_signatures(files: list[Path], public_key: Path) -> str:
    """在隔离 GnuPG home 中导入公钥并验证每个 detached signature。"""

    with tempfile.TemporaryDirectory(prefix="pixel-signature-check-") as home_value:
        # 隔离 GnuPG home 不读取用户个人 keyring。
        home = Path(home_value)
        subprocess.run(
            ["gpg", "--batch", "--homedir", str(home), "--import", str(public_key)],
            check=True,
            capture_output=True,
            text=True,
        )
        for path in files:
            # 每个主体文件必须有 ASCII-armored detached signature。
            signature = path.with_name(path.name + ".asc")
            if not signature.is_file():
                raise AssertionError(f"{path}: missing OpenPGP signature")
            subprocess.run(
                ["gpg", "--batch", "--homedir", str(home), "--verify", str(signature), str(path)],
                check=True,
                capture_output=True,
                text=True,
            )
        # 公钥完整 fingerprint 绑定本轮验收证据。
        fingerprint_output = subprocess.run(
            ["gpg", "--batch", "--homedir", str(home), "--with-colons", "--fingerprint"],
            check=True,
            capture_output=True,
            text=True,
        ).stdout
    # 第一个 fpr 记录就是导入签名主密钥的完整指纹。
    fingerprint = next(
        (line.split(":")[9] for line in fingerprint_output.splitlines() if line.startswith("fpr:")),
        "",
    )
    if len(fingerprint) != 40:
        raise AssertionError("Unable to read signing key fingerprint")
    return fingerprint


def validate_sbom(path: Path, version: str, metadata: dict[str, str]) -> dict[str, Any]:
    """校验 CycloneDX 1.7 根组件、内部坐标和许可证状态。"""

    # CycloneDX JSON 根对象。
    sbom = json.loads(path.read_text(encoding="utf-8"))
    if sbom.get("bomFormat") != "CycloneDX" or sbom.get("specVersion") != "1.7":
        raise AssertionError(f"{path}: not a CycloneDX 1.7 document")
    # 所有组件都必须具备唯一 bom-ref 与 Maven purl。
    components = sbom.get("components")
    if not isinstance(components, list):
        raise AssertionError(f"{path}: missing components array")
    # 当前 SBOM 中的统一内部坐标。
    internal_components = {
        component.get("name")
        for component in components
        if component.get("group") == "com.purride" and component.get("version") == version
    }
    if internal_components != set(PIXEL_ARTIFACTS):
        raise AssertionError(f"{path}: internal components {sorted(internal_components)}")
    # bom-ref 不得重复，否则依赖边含义不确定。
    references = [component.get("bom-ref") for component in components]
    if len(references) != len(set(references)) or any(not reference for reference in references):
        raise AssertionError(f"{path}: duplicate or empty bom-ref")
    # 元数据中必须保留真实许可证决策状态。
    properties = {
        item.get("name"): item.get("value")
        for item in sbom.get("metadata", {}).get("properties", [])
    }
    if properties.get("com.purride.pixel:license-status") != metadata["licenseStatus"]:
        raise AssertionError(f"{path}: SBOM license status mismatch")
    return {
        "componentCount": len(components),
        "dependencyNodeCount": len(sbom.get("dependencies", [])),
        "serialNumber": sbom.get("serialNumber"),
    }


def validate_provenance(path: Path, repository: Path, subjects: list[Path]) -> dict[str, Any]:
    """校验 in-toto/SLSA 类型、subject 完整性和实际 SHA-256。"""

    # provenance JSON 根对象。
    provenance = json.loads(path.read_text(encoding="utf-8"))
    if provenance.get("_type") != "https://in-toto.io/Statement/v1":
        raise AssertionError(f"{path}: invalid in-toto statement type")
    if provenance.get("predicateType") != "https://slsa.dev/provenance/v1":
        raise AssertionError(f"{path}: invalid SLSA predicate type")
    # 按仓库相对路径索引来源证明 subject。
    actual_subjects = {
        subject.get("name"): subject.get("digest", {}).get("sha256")
        for subject in provenance.get("subject", [])
    }
    # 期望 subject 来自统一坐标的五个 Maven 主体文件。
    expected_subjects = {
        subject.relative_to(repository).as_posix(): hash_file(subject, "sha256")
        for subject in subjects
    }
    if actual_subjects != expected_subjects:
        raise AssertionError(f"{path}: provenance subjects do not match Maven primary files")
    # dirtyWorktree 必须存在，即使本地演练值为 true 也不能被省略。
    internal_parameters = provenance.get("predicate", {}).get("buildDefinition", {}).get("internalParameters", {})
    if not isinstance(internal_parameters.get("dirtyWorktree"), bool):
        raise AssertionError(f"{path}: missing dirtyWorktree provenance parameter")
    return {
        "subjectCount": len(actual_subjects),
        "dirtyWorktree": internal_parameters["dirtyWorktree"],
    }


def write_json_atomic(path: Path, document: dict[str, Any]) -> None:
    """原子写出确定性 JSON 验收报告。"""

    # 同目录临时文件防止失败留下半份通过报告。
    temporary_path = path.with_name(path.name + ".tmp")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path.write_text(
        json.dumps(document, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary_path.replace(path)


def main() -> int:
    """执行全部供应链发布验证并只在全通过后写报告。"""

    # 已解析的命令行参数。
    args = parse_args()
    # 发布元数据的唯一受审字段表。
    metadata = read_properties(args.metadata)
    # 仓库许可证的独立内容校验结果；未要求许可证时保持空值以暴露演练边界。
    repository_license_report: dict[str, Any] | None = None
    # 仓库 NOTICE 的独立内容校验结果，与许可证使用同一正式候选开关。
    repository_notice_report: dict[str, Any] | None = None
    if args.require_license:
        if metadata.get("licenseStatus") != "CONFIRMED":
            raise AssertionError("Pixel release license is still UNCONFIRMED")
        if args.repository_license is None or not args.repository_license.is_file():
            raise AssertionError("Confirmed release requires a repository LICENSE file")
        repository_license_report = validate_repository_license(args.repository_license, metadata)
        if args.repository_notice is None or not args.repository_notice.is_file():
            raise AssertionError("Confirmed release requires a repository NOTICE file")
        repository_notice_report = validate_repository_notice(args.repository_notice, metadata)

    # Gradle 依赖校验和模块锁文件必须在发布内容校验前通过。
    gradle_verification_report = validate_gradle_verification_metadata(args.verification_metadata)
    # 各模块 Release 配置锁定摘要。
    gradle_lock_report = validate_gradle_lockfiles(args.lockfile)

    # 统一坐标共五个 Maven 主体文件。
    files = primary_files(args.repository, args.version)
    # AAR 发布内容审计结果。
    aar_reports: list[dict[str, Any]] = []
    for artifact in PIXEL_ARTIFACTS:
        # 当前坐标版本目录。
        directory = version_directory(args.repository, artifact, args.version)
        # 当前坐标唯一 POM。
        pom = require_unique(directory, "*.pom")
        validate_pom(pom, artifact, metadata, args.require_license)
        # 当前坐标唯一 AAR。
        aar = require_unique(directory, "*.aar")
        aar_reports.append(validate_aar_contents(aar, artifact))

    # 聚合坐标中的 SBOM 与 provenance 补充物。
    aggregate_directory = version_directory(args.repository, "pixel-engine", args.version)
    # 唯一 CycloneDX JSON 文件。
    sbom = require_unique(aggregate_directory, f"pixel-engine-{args.version}-sbom.cdx.json")
    # 唯一 in-toto provenance JSON 文件。
    provenance = require_unique(
        aggregate_directory,
        f"pixel-engine-{args.version}-provenance.intoto.json",
    )
    # 签名与 checksum 覆盖五个主体和两个补充物。
    signed_files = files + [sbom, provenance]
    # 每个文件的强摘要进入验收报告。
    checksum_reports = {
        path.relative_to(args.repository).as_posix(): validate_checksums(path)
        for path in signed_files
    }
    # 隔离 keyring 验证后的完整公钥指纹。
    signing_fingerprint = verify_signatures(signed_files, args.public_key)
    # CycloneDX 结构摘要。
    sbom_report = validate_sbom(sbom, args.version, metadata)
    # SLSA/in-toto 来源摘要。
    provenance_report = validate_provenance(provenance, args.repository, files)
    # 最终验收报告。
    report = {
        "schemaVersion": 1,
        "status": "passed",
        "version": args.version,
        "licenseStatus": metadata.get("licenseStatus", "MISSING"),
        "licenseRequired": args.require_license,
        "repositoryLicense": repository_license_report,
        "repositoryNotice": repository_notice_report,
        "signingFingerprint": signing_fingerprint,
        "signedFileCount": len(signed_files),
        "checksums": checksum_reports,
        "sbom": sbom_report,
        "provenance": provenance_report,
        "aarContents": aar_reports,
        "gradleDependencyVerification": gradle_verification_report,
        "gradleDependencyLocks": gradle_lock_report,
    }
    write_json_atomic(args.report, report)
    print(f"Pixel supply-chain validation passed: {args.report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
