#!/usr/bin/env python3
"""检查 Pixel Engine Release AAR、class/method 数量与运行时依赖预算。"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import struct
import sys
import xml.etree.ElementTree as element_tree
import zipfile
from pathlib import Path
from typing import Any, Mapping, Sequence


# 预算和报告共同使用的结构版本，防止旧工具静默解释新字段。
SCHEMA_VERSION = 1

# Java classfile 固定魔数。
CLASS_FILE_MAGIC = 0xCAFEBABE


class ClassFileReader:
    """对单个 Java classfile 执行有边界的顺序读取。"""

    def __init__(self, data: bytes, label: str) -> None:
        """绑定不可变 classfile 字节和错误上下文。"""

        # memoryview 避免每次跳过字段时复制底层字节。
        self._data = memoryview(data)
        # 当前顺序读取偏移。
        self._offset = 0
        # 报错时使用的 AAR entry 名称。
        self._label = label

    @property
    def offset(self) -> int:
        """返回当前读取偏移。"""

        return self._offset

    @property
    def size(self) -> int:
        """返回 classfile 总字节数。"""

        return len(self._data)

    def read_u1(self) -> int:
        """读取一个无符号一字节整数。"""

        return self._read_integer(1)

    def read_u2(self) -> int:
        """读取一个大端无符号二字节整数。"""

        return self._read_integer(2)

    def read_u4(self) -> int:
        """读取一个大端无符号四字节整数。"""

        return self._read_integer(4)

    def skip(self, byte_count: int) -> None:
        """在完成边界检查后跳过指定字节数。"""

        if byte_count < 0 or self._offset + byte_count > len(self._data):
            raise ValueError(
                f"classfile 越界：{self._label} offset={self._offset} skip={byte_count} "
                f"size={len(self._data)}",
            )
        self._offset += byte_count

    def _read_integer(self, byte_count: int) -> int:
        """读取固定宽度的大端无符号整数。"""

        # 先检查完整字段可读，再由 int.from_bytes 解码。
        start_offset = self._offset
        self.skip(byte_count)
        return int.from_bytes(self._data[start_offset:self._offset], byteorder="big")


def parse_arguments(arguments: Sequence[str]) -> argparse.Namespace:
    """解析所有显式输入和机器报告路径。"""

    # 不自动发现 build 目录，避免把陈旧 AAR 或 POM 当作当前结果。
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--aar", type=Path, required=True, help="最终 Release AAR。")
    parser.add_argument(
        "--dependency-aar",
        type=Path,
        action="append",
        default=[],
        help="聚合坐标传递依赖的 Release AAR；可重复传入。",
    )
    parser.add_argument("--pom", type=Path, required=True, help="最终发布 POM。")
    parser.add_argument(
        "--runtime-dependencies",
        type=Path,
        required=True,
        help="Gradle 解析后的运行时 artifact 坐标清单。",
    )
    parser.add_argument("--budget", type=Path, required=True, help="受审预算 JSON。")
    parser.add_argument("--report", type=Path, required=True, help="机器可读结果 JSON。")
    return parser.parse_args(arguments)


def load_json_object(path: Path, label: str) -> dict[str, Any]:
    """读取一个必需 JSON 对象。"""

    if not path.is_file():
        raise FileNotFoundError(f"缺少{label}：{path}")
    # 根值必须先完成类型检查，后续预算字段才可安全读取。
    decoded_value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(decoded_value, dict):
        raise ValueError(f"{label}根值必须是 JSON 对象：{path}")
    return decoded_value


def require_integer(container: Mapping[str, Any], key: str, label: str) -> int:
    """读取一个非负整数预算，并显式拒绝 bool。"""

    # Python bool 是 int 子类，必须单独排除。
    value = container.get(key)
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise ValueError(f"{label}.{key} 必须是非负整数")
    return value


def require_string_list(container: Mapping[str, Any], key: str, label: str) -> list[str]:
    """读取一个无重复、已排序的非空字符串列表。"""

    # 坐标列表使用精确集合而不是模糊前缀，新增依赖必须显式评审。
    value = container.get(key)
    if not isinstance(value, list) or any(not isinstance(item, str) or not item for item in value):
        raise ValueError(f"{label}.{key} 必须是非空字符串数组")
    if value != sorted(set(value)):
        raise ValueError(f"{label}.{key} 必须排序且不得重复")
    return list(value)


def sha256_file(path: Path) -> str:
    """以固定块大小计算文件 SHA-256。"""

    # 流式读取避免把整个 AAR 复制到内存。
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def skip_attributes(reader: ClassFileReader) -> None:
    """跳过当前 member 或 class 的完整 attributes 表。"""

    # 每个 attribute 由 name_index、length 和 payload 组成。
    attribute_count = reader.read_u2()
    for _ in range(attribute_count):
        reader.skip(2)
        attribute_length = reader.read_u4()
        reader.skip(attribute_length)


def skip_member(reader: ClassFileReader) -> None:
    """跳过一个 field_info 或 method_info 结构。"""

    # access_flags、name_index、descriptor_index 共六字节。
    reader.skip(6)
    skip_attributes(reader)


def count_classfile_methods(data: bytes, label: str) -> int:
    """统计 classfile 的 method_info 数量，包含构造器、私有与 synthetic 方法。"""

    # 解析器只读取结构长度，不依赖 javap 或主机 JDK 版本。
    reader = ClassFileReader(data, label)
    if reader.read_u4() != CLASS_FILE_MAGIC:
        raise ValueError(f"无效 classfile 魔数：{label}")
    # 跳过 minor_version 与 major_version。
    reader.skip(4)
    # 常量池索引从 1 开始，Long/Double 占两个索引槽。
    constant_pool_count = reader.read_u2()
    constant_pool_index = 1
    while constant_pool_index < constant_pool_count:
        tag = reader.read_u1()
        if tag == 1:
            reader.skip(reader.read_u2())
        elif tag in (3, 4):
            reader.skip(4)
        elif tag in (5, 6):
            reader.skip(8)
            constant_pool_index += 1
        elif tag in (7, 8, 16, 19, 20):
            reader.skip(2)
        elif tag in (9, 10, 11, 12, 17, 18):
            reader.skip(4)
        elif tag == 15:
            reader.skip(3)
        else:
            raise ValueError(f"未知 classfile 常量池 tag={tag}：{label}")
        constant_pool_index += 1
    # 跳过 access_flags、this_class、super_class。
    reader.skip(6)
    # interfaces 表只有常量池索引。
    reader.skip(reader.read_u2() * 2)
    # fields 不计入方法预算，但仍需完整越过结构。
    field_count = reader.read_u2()
    for _ in range(field_count):
        skip_member(reader)
    # method_info 数量就是稳定、工具无关的总方法计数口径。
    method_count = reader.read_u2()
    for _ in range(method_count):
        skip_member(reader)
    skip_attributes(reader)
    if reader.offset != reader.size:
        raise ValueError(
            f"classfile 存在未解析尾部：{label} offset={reader.offset} size={reader.size}",
        )
    return method_count


def inspect_aar(path: Path) -> dict[str, Any]:
    """读取最终 AAR，并统计 classes.jar 中的 class 与 method。"""

    if not path.is_file() or path.stat().st_size <= 0:
        raise FileNotFoundError(f"Release AAR 缺失或为空：{path}")
    with zipfile.ZipFile(path) as aar_archive:
        try:
            # classes.jar 是 SDK 自有字节码的发布边界。
            classes_jar_bytes = aar_archive.read("classes.jar")
        except KeyError as error:
            raise ValueError(f"Release AAR 缺少 classes.jar：{path}") from error
    with zipfile.ZipFile(io.BytesIO(classes_jar_bytes)) as classes_archive:
        # 排序后逐 class 解析，确保报告和首个失败位置稳定。
        class_entries = sorted(
            (entry for entry in classes_archive.infolist() if entry.filename.endswith(".class")),
            key=lambda entry: entry.filename,
        )
        if not class_entries:
            raise ValueError(f"classes.jar 不包含 classfile：{path}")
        method_count = sum(
            count_classfile_methods(classes_archive.read(entry), entry.filename)
            for entry in class_entries
        )
    return {
        "path": path.as_posix(),
        "bytes": path.stat().st_size,
        "sha256": sha256_file(path),
        "classesJarBytes": len(classes_jar_bytes),
        "classCount": len(class_entries),
        "methodCount": method_count,
        "methodCountDefinition": "classes.jar 中全部 method_info；包含构造器、私有和 synthetic 方法",
        # 私有字段只供聚合去重，写报告前会移除。
        "_classNames": [entry.filename for entry in class_entries],
    }


def inspect_aar_union(primary_path: Path, dependency_paths: Sequence[Path]) -> dict[str, Any]:
    """汇总主 AAR 与传递 AAR，并显式报告重复 class。"""

    # paths 以主 artifact 开头，依赖按 CLI 顺序保留后再统一排序证据。
    paths = [primary_path, *dependency_paths]
    if len(paths) != len(set(paths)):
        raise ValueError("AAR 并集输入路径不得重复")
    # inspections 保存每个真实发布 AAR 的独立大小与校验和。
    inspections = [inspect_aar(path) for path in paths]
    # class_owners 记录每个 JVM class 来自哪些 artifact。
    class_owners: dict[str, list[str]] = {}
    for inspection in inspections:
        for class_name in inspection["_classNames"]:
            class_owners.setdefault(class_name, []).append(inspection["path"])
    # duplicate_classes 只包含跨 AAR 重复的精确 entry 和 owner。
    duplicate_classes = [
        {"class": class_name, "owners": owners}
        for class_name, owners in sorted(class_owners.items())
        if len(owners) > 1
    ]
    # published_artifacts 删除仅供内部聚合的 class 名单，避免报告体积无界增长。
    published_artifacts = [
        {key: value for key, value in inspection.items() if key != "_classNames"}
        for inspection in inspections
    ]
    # primary 是向后兼容的主 artifact 证据字段。
    primary = published_artifacts[0]
    return {
        "path": primary["path"],
        "sha256": primary["sha256"],
        "bytes": sum(inspection["bytes"] for inspection in inspections),
        "classesJarBytes": sum(inspection["classesJarBytes"] for inspection in inspections),
        "classCount": len(class_owners),
        "methodCount": sum(inspection["methodCount"] for inspection in inspections),
        "methodCountDefinition": "所有聚合 AAR classes.jar 的 method_info 总和；重复 class 会独立判失败",
        "artifactCount": len(inspections),
        "artifacts": published_artifacts,
        "duplicateClassCount": len(duplicate_classes),
        "duplicateClasses": duplicate_classes,
    }


def child_text(node: element_tree.Element, namespace: str, child_name: str) -> str:
    """读取 Maven dependency 的必需子元素文本。"""

    # POM 可能带默认 namespace，也可能来自最小测试 fixture。
    qualified_name = f"{{{namespace}}}{child_name}" if namespace else child_name
    child = node.find(qualified_name)
    if child is None or child.text is None or not child.text.strip():
        raise ValueError(f"POM dependency 缺少 {child_name}")
    return child.text.strip()


def published_runtime_dependencies(path: Path) -> list[str]:
    """提取发布 POM 中消费者运行时可见的直接依赖坐标。"""

    if not path.is_file():
        raise FileNotFoundError(f"发布 POM 缺失：{path}")
    # 默认 namespace 从根 tag 提取，避免绑定具体 Maven XML 前缀。
    root = element_tree.parse(path).getroot()
    namespace = root.tag.partition("}")[0].lstrip("{") if root.tag.startswith("{") else ""
    dependency_path = (
        f"{{{namespace}}}dependencies/{{{namespace}}}dependency"
        if namespace
        else "dependencies/dependency"
    )
    # compile/runtime 且非 optional 的依赖会进入普通消费者运行边界。
    coordinates: list[str] = []
    for dependency in root.findall(dependency_path):
        scope = child_text(dependency, namespace, "scope")
        optional_node = dependency.find(
            f"{{{namespace}}}optional" if namespace else "optional",
        )
        optional = optional_node is not None and (optional_node.text or "").strip().lower() == "true"
        if scope not in ("compile", "runtime") or optional:
            continue
        coordinates.append(
            ":".join(
                (
                    child_text(dependency, namespace, "groupId"),
                    child_text(dependency, namespace, "artifactId"),
                    child_text(dependency, namespace, "version"),
                ),
            ),
        )
    return sorted(set(coordinates))


def resolved_runtime_dependencies(path: Path) -> list[str]:
    """读取 Gradle 解析后且已排序去重的运行时 artifact 坐标。"""

    if not path.is_file():
        raise FileNotFoundError(f"运行时依赖清单缺失：{path}")
    # 空行不计 artifact，但重复或乱序表示生成器契约被破坏。
    coordinates = [line.strip() for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    if coordinates != sorted(set(coordinates)):
        raise ValueError(f"运行时依赖清单必须排序且不得重复：{path}")
    return coordinates


def compare_budget(
    budget: Mapping[str, Any],
    artifact: Mapping[str, Any],
    published_dependencies: list[str],
    resolved_dependencies: list[str],
) -> tuple[dict[str, Any], list[str]]:
    """把观测值与固定预算逐项比较，并返回规范化预算和违规列表。"""

    if budget.get("schemaVersion") != SCHEMA_VERSION:
        raise ValueError(f"预算 schemaVersion 必须为 {SCHEMA_VERSION}")
    # 两组预算对象分别约束字节码体积和依赖边界。
    artifact_budget = budget.get("artifact")
    dependency_budget = budget.get("dependencies")
    if not isinstance(artifact_budget, dict) or not isinstance(dependency_budget, dict):
        raise ValueError("预算必须包含 artifact 与 dependencies 对象")
    max_aar_bytes = require_integer(artifact_budget, "maxAarBytes", "artifact")
    max_class_count = require_integer(artifact_budget, "maxClassCount", "artifact")
    max_method_count = require_integer(artifact_budget, "maxMethodCount", "artifact")
    max_published_count = require_integer(
        dependency_budget,
        "maxPublishedRuntimeDependencyCount",
        "dependencies",
    )
    max_resolved_count = require_integer(
        dependency_budget,
        "maxResolvedRuntimeArtifactCount",
        "dependencies",
    )
    expected_published = require_string_list(
        dependency_budget,
        "expectedPublishedRuntimeDependencies",
        "dependencies",
    )
    expected_resolved = require_string_list(
        dependency_budget,
        "expectedResolvedRuntimeArtifacts",
        "dependencies",
    )
    # 每条违规独立保留，单次 CI 即可看到所有超限和依赖漂移。
    violations: list[str] = []
    if artifact.get("duplicateClassCount", 0) > 0:
        violations.append(
            f"聚合 artifact 含 {artifact['duplicateClassCount']} 个重复 class："
            f"{artifact.get('duplicateClasses', [])}",
        )
    for metric_name, actual_value, maximum_value in (
        ("AAR bytes", artifact["bytes"], max_aar_bytes),
        ("class count", artifact["classCount"], max_class_count),
        ("method count", artifact["methodCount"], max_method_count),
        ("published runtime dependency count", len(published_dependencies), max_published_count),
        ("resolved runtime artifact count", len(resolved_dependencies), max_resolved_count),
    ):
        if actual_value > maximum_value:
            violations.append(f"{metric_name}={actual_value} 超过预算 {maximum_value}")
    if published_dependencies != expected_published:
        violations.append(
            "发布运行时依赖集合漂移："
            f"actual={published_dependencies} expected={expected_published}",
        )
    if resolved_dependencies != expected_resolved:
        violations.append(
            "解析后运行时 artifact 集合漂移："
            f"actual={resolved_dependencies} expected={expected_resolved}",
        )
    normalized_budget = {
        "artifact": {
            "maxAarBytes": max_aar_bytes,
            "maxClassCount": max_class_count,
            "maxMethodCount": max_method_count,
        },
        "dependencies": {
            "maxPublishedRuntimeDependencyCount": max_published_count,
            "maxResolvedRuntimeArtifactCount": max_resolved_count,
            "expectedPublishedRuntimeDependencies": expected_published,
            "expectedResolvedRuntimeArtifacts": expected_resolved,
        },
    }
    return normalized_budget, violations


def write_report(path: Path, report: Mapping[str, Any]) -> None:
    """以 UTF-8、稳定 key 顺序和单一尾换行写入报告。"""

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def main(arguments: Sequence[str] = sys.argv[1:]) -> int:
    """执行完整预算检查，并在失败时仍保留机器报告。"""

    # 参数解析失败由 argparse 保持标准非零语义。
    parsed = parse_arguments(arguments)
    try:
        # 所有输入都必须来自同一次显式 Gradle 构建。
        budget = load_json_object(parsed.budget, "预算")
        artifact = inspect_aar_union(parsed.aar, parsed.dependency_aar)
        published_dependencies = published_runtime_dependencies(parsed.pom)
        resolved_dependencies = resolved_runtime_dependencies(parsed.runtime_dependencies)
        normalized_budget, violations = compare_budget(
            budget,
            artifact,
            published_dependencies,
            resolved_dependencies,
        )
        report: dict[str, Any] = {
            "schemaVersion": SCHEMA_VERSION,
            "status": "failed" if violations else "passed",
            "artifact": artifact,
            "publishedRuntimeDependencies": published_dependencies,
            "resolvedRuntimeArtifacts": resolved_dependencies,
            "budget": normalized_budget,
            "violations": violations,
        }
    except Exception as error:  # noqa: BLE001 - 报告必须保留所有输入/解析失败。
        report = {
            "schemaVersion": SCHEMA_VERSION,
            "status": "failed",
            "error": f"{type(error).__name__}: {error}",
            "violations": [str(error)],
        }
    write_report(parsed.report, report)
    if report["status"] != "passed":
        print(json.dumps(report, ensure_ascii=False, indent=2), file=sys.stderr)
        return 1
    print(
        "Pixel release artifact budget passed: "
        f"aarBytes={report['artifact']['bytes']} "
        f"classes={report['artifact']['classCount']} "
        f"methods={report['artifact']['methodCount']} "
        f"runtimeArtifacts={len(report['resolvedRuntimeArtifacts'])}",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
