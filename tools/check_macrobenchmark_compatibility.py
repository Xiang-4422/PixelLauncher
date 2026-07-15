#!/usr/bin/env python3
"""验证 Pixel Engine Macrobenchmark 系统兼容矩阵及其完整原始证据。"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import xml.etree.ElementTree as element_tree
from pathlib import Path
from typing import Any, Mapping, Sequence


# 报告结构显式版本化，避免后续字段变化静默解释旧证据。
SCHEMA_VERSION = 1

# M6-2 固定要求的两个启动旅程。
STARTUP_SCENARIOS = ("coldStartup", "hotStartup")

# M6-2 固定要求的五个持续产帧旅程。
FRAME_SCENARIOS = ("animation", "listScroll", "overlay", "pageTransition", "textInput")

# 完整矩阵中不允许增删或改名的七个旅程。
REQUIRED_SCENARIOS = STARTUP_SCENARIOS + FRAME_SCENARIOS

# 当前 1.0 Goal 要求覆盖的 Android API 档位。
REQUIRED_API_LEVELS = (24, 29, 36, 37)

# API 24–28 官方 gfxinfo 回退必须提供的完整指标集合。
GFXINFO_METRICS = (
    "gfxFrameJankPercent",
    "gfxFrameTime50thPercentileMs",
    "gfxFrameTime90thPercentileMs",
    "gfxFrameTime95thPercentileMs",
    "gfxFrameTime99thPercentileMs",
    "gfxFrameTotalCount",
)

# AndroidX 生成的测试类名必须保持稳定，防止误收其他测试结果。
BENCHMARK_CLASS_NAME = "com.purride.pixelbenchmark.PixelMacrobenchmark"

# 每个场景对应的 AndroidX 文本消息前缀。
MESSAGE_FILE_PREFIX = f"additionaltestoutput.benchmark.message_{BENCHMARK_CLASS_NAME}."


def parse_arguments(arguments: Sequence[str]) -> argparse.Namespace:
    """解析显式矩阵清单与机器可读输出路径。"""

    # 参数解析器不自动发现历史目录，避免把陈旧结果误当作当前证据。
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True, help="兼容矩阵输入清单。")
    parser.add_argument("--output", type=Path, required=True, help="机器可读验收报告。")
    return parser.parse_args(arguments)


def load_json_object(path: Path, label: str) -> dict[str, Any]:
    """读取一个必需 JSON 对象，并拒绝缺失文件或非对象根值。"""

    if not path.is_file():
        raise FileNotFoundError(f"缺少{label}：{path}")
    # 解码结果必须先完成类型检查，调用方才能安全读取字段。
    decoded_value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(decoded_value, dict):
        raise ValueError(f"{label}必须是 JSON 对象：{path}")
    return decoded_value


def require_string(container: Mapping[str, Any], key: str, label: str) -> str:
    """读取一个必需且非空的字符串字段。"""

    # 空白字符串不能充当设备身份或证据路径。
    value = container.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{label}.{key} 必须是非空字符串")
    return value


def require_integer(container: Mapping[str, Any], key: str, label: str) -> int:
    """读取一个必需整数，并显式拒绝布尔值。"""

    # Python 的 bool 是 int 子类，因此必须单独排除。
    value = container.get(key)
    if isinstance(value, bool) or not isinstance(value, int):
        raise ValueError(f"{label}.{key} 必须是整数")
    return value


def sha256_file(path: Path) -> str:
    """流式计算一个证据文件的 SHA-256。"""

    # 摘要对象按固定块大小读取，避免大型 trace 占用过多内存。
    digest = hashlib.sha256()
    with path.open("rb") as source:
        # 一 MiB 块同时兼顾吞吐与稳定内存上限。
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def artifact_record(path: Path) -> dict[str, Any]:
    """记录一个非空证据文件的路径、字节数和摘要。"""

    if not path.is_file() or path.stat().st_size <= 0:
        raise ValueError(f"证据文件缺失或为空：{path}")
    # 文件大小与摘要共同锁定本次验收使用的确切字节。
    return {
        "path": path.as_posix(),
        "bytes": path.stat().st_size,
        "sha256": sha256_file(path),
    }


def expected_metrics(api_level: int, scenario: str) -> tuple[str, ...]:
    """返回一个 API/场景组合必须出现在 AndroidX 消息中的指标名。"""

    if api_level < 29:
        return GFXINFO_METRICS
    if scenario in STARTUP_SCENARIOS:
        return ("timeToInitialDisplayMs",)
    # API 31 起 FrameTimingMetric 还必须公开 overrun 分布。
    frame_metrics = ["frameCount", "frameDurationCpuMs"]
    if api_level >= 31:
        frame_metrics.append("frameOverrunMs")
    return tuple(frame_metrics)


def validate_junit_xml(
    xml_path: Path,
    expected_device: str,
) -> tuple[dict[str, Any], list[str]]:
    """验证 JUnit XML 的设备身份、精确测试集合和零失败状态。"""

    # XML 根节点必须可解析且对应唯一测试套件。
    root = element_tree.parse(xml_path).getroot()
    if root.tag != "testsuite":
        raise ValueError(f"JUnit 根节点必须是 testsuite：{xml_path}")
    # 四个汇总计数必须与七项完整通过完全一致。
    expected_counts = {"tests": 7, "failures": 0, "errors": 0, "skipped": 0}
    for attribute_name, expected_value in expected_counts.items():
        # XML 属性使用十进制字符串，缺失或非整数同样属于无效证据。
        raw_value = root.attrib.get(attribute_name)
        try:
            actual_value = int(raw_value) if raw_value is not None else -1
        except ValueError as error:
            raise ValueError(f"JUnit {attribute_name} 不是整数：{raw_value}") from error
        if actual_value != expected_value:
            raise ValueError(
                f"JUnit {attribute_name}={actual_value}，要求 {expected_value}：{xml_path}",
            )
    # Android Gradle Plugin 把设备名写在 properties/property 中。
    device_values = [
        property_node.attrib.get("value", "")
        for property_node in root.findall("./properties/property")
        if property_node.attrib.get("name") == "device"
    ]
    if device_values != [expected_device]:
        raise ValueError(f"JUnit 设备身份不匹配：actual={device_values} expected={expected_device}")
    # 每个 testcase 必须来自固定测试类且名称集合精确匹配。
    testcase_nodes = root.findall("testcase")
    testcase_names = [node.attrib.get("name", "") for node in testcase_nodes]
    testcase_classes = {node.attrib.get("classname", "") for node in testcase_nodes}
    if sorted(testcase_names) != sorted(REQUIRED_SCENARIOS):
        raise ValueError(
            f"JUnit 旅程集合不匹配：actual={sorted(testcase_names)} "
            f"expected={sorted(REQUIRED_SCENARIOS)}",
        )
    if testcase_classes != {BENCHMARK_CLASS_NAME}:
        raise ValueError(f"JUnit 测试类不匹配：{sorted(testcase_classes)}")
    return artifact_record(xml_path), testcase_names


def validate_message_and_trace(
    additional_output: Path,
    api_level: int,
    scenario: str,
) -> tuple[dict[str, Any], dict[str, Any], list[str]]:
    """验证单场景指标消息、指标覆盖和其唯一非空 trace。"""

    # 文件名由 AndroidX 测试类和方法精确决定。
    message_path = additional_output / f"{MESSAGE_FILE_PREFIX}{scenario}.txt"
    if not message_path.is_file():
        raise FileNotFoundError(f"缺少 {scenario} 指标消息：{message_path}")
    # 文本内容既要标识正确场景，也要包含该平台应有的全部指标。
    message_text = message_path.read_text(encoding="utf-8")
    expected_header = f"PixelMacrobenchmark_{scenario}"
    if expected_header not in message_text:
        raise ValueError(f"{scenario} 消息缺少场景标题：{expected_header}")
    # 缺失指标逐项报告，便于区分平台回退和采集失败。
    missing_metrics = [
        metric_name
        for metric_name in expected_metrics(api_level, scenario)
        if metric_name not in message_text
    ]
    if missing_metrics:
        raise ValueError(f"{scenario} 缺少指标：{missing_metrics}")
    # dry-run 每个场景必须恰好引用一次 iteration 0 trace。
    trace_names = sorted(
        {
            token.split("file://", 1)[1].split(")", 1)[0]
            for token in message_text.split()
            if "file://" in token and ".perfetto-trace" in token
        },
    )
    if len(trace_names) != 1:
        raise ValueError(f"{scenario} 应恰好引用一条 dry-run trace，实际为 {trace_names}")
    # 引用文件必须位于同一 evidence 目录且非空。
    trace_path = additional_output / trace_names[0]
    return (
        artifact_record(message_path),
        artifact_record(trace_path),
        list(expected_metrics(api_level, scenario)),
    )


def validate_entry(entry: Mapping[str, Any], index: int) -> dict[str, Any]:
    """验证矩阵中的一个 API 档位，并返回可审计证据摘要。"""

    # 标签只用于生成明确错误上下文，不参与设备身份判定。
    label = f"entries[{index}]"
    # API、设备名和目录都由清单显式声明。
    api_level = require_integer(entry, "apiLevel", label)
    expected_device = require_string(entry, "expectedDevice", label)
    evidence_directory = Path(require_string(entry, "evidenceDirectory", label))
    # 每个目录只能包含一份 JUnit XML，避免混用多次运行。
    xml_paths = sorted(evidence_directory.glob("TEST-*.xml"))
    if len(xml_paths) != 1:
        raise ValueError(f"{label} 必须恰好包含一份 JUnit XML：{xml_paths}")
    # 设备元数据把 API、页大小和 dry-run 身份与原始测试目录绑定。
    metadata_path = evidence_directory / "device-metadata.json"
    metadata = load_json_object(metadata_path, f"{label} 设备元数据")
    metadata_api_level = require_integer(metadata, "apiLevel", f"{label}.metadata")
    if metadata_api_level != api_level:
        raise ValueError(f"{label} API 不匹配：metadata={metadata_api_level} manifest={api_level}")
    if metadata.get("isEmulator") is not True or metadata.get("dryRun") is not True:
        raise ValueError(f"{label} 必须明确标记 isEmulator=true 且 dryRun=true")
    # API 37 档位必须由运行时元数据证明为 16KB 页大小。
    expected_page_size = entry.get("expectedPageSizeBytes")
    if expected_page_size is not None:
        if isinstance(expected_page_size, bool) or not isinstance(expected_page_size, int):
            raise ValueError(f"{label}.expectedPageSizeBytes 必须是整数或 null")
        metadata_page_size = require_integer(metadata, "pageSizeBytes", f"{label}.metadata")
        if metadata_page_size != expected_page_size:
            raise ValueError(
                f"{label} 页大小不匹配：metadata={metadata_page_size} "
                f"expected={expected_page_size}",
            )
    # JUnit、消息和 trace 必须全部来自同一证据目录。
    junit_artifact, testcase_names = validate_junit_xml(xml_paths[0], expected_device)
    additional_output = evidence_directory / "additional-output"
    if not additional_output.is_dir():
        raise FileNotFoundError(f"{label} 缺少 additional-output：{additional_output}")
    # 逐场景保存指标覆盖与精确文件摘要。
    scenario_reports: dict[str, Any] = {}
    trace_filenames: list[str] = []
    for scenario in REQUIRED_SCENARIOS:
        # 每个场景独立验证，避免一个消息为多个旅程冒充证据。
        message_artifact, trace_artifact, metric_names = validate_message_and_trace(
            additional_output,
            api_level,
            scenario,
        )
        trace_filenames.append(Path(trace_artifact["path"]).name)
        scenario_reports[scenario] = {
            "requiredMetrics": metric_names,
            "message": message_artifact,
            "trace": trace_artifact,
        }
    # 目录中不允许残留额外消息或 trace，以免报告指向错轮运行。
    actual_message_names = sorted(
        path.name for path in additional_output.glob(f"{MESSAGE_FILE_PREFIX}*.txt")
    )
    expected_message_names = sorted(
        f"{MESSAGE_FILE_PREFIX}{scenario}.txt" for scenario in REQUIRED_SCENARIOS
    )
    if actual_message_names != expected_message_names:
        raise ValueError(f"{label} 指标消息集合不精确：{actual_message_names}")
    actual_trace_names = sorted(path.name for path in additional_output.glob("*.perfetto-trace"))
    if actual_trace_names != sorted(trace_filenames):
        raise ValueError(
            f"{label} trace 集合不精确：actual={actual_trace_names} "
            f"referenced={sorted(trace_filenames)}",
        )
    return {
        "apiLevel": api_level,
        "expectedDevice": expected_device,
        "expectedPageSizeBytes": expected_page_size,
        "evidenceDirectory": evidence_directory.as_posix(),
        "testcases": testcase_names,
        "junit": junit_artifact,
        "deviceMetadata": artifact_record(metadata_path),
        "scenarios": scenario_reports,
    }


def build_report(manifest_path: Path) -> dict[str, Any]:
    """验证完整四档矩阵，并构造通过或失败报告。"""

    # 清单版本和模式必须与本验收器理解的语义一致。
    manifest = load_json_object(manifest_path, "兼容矩阵清单")
    if manifest.get("schemaVersion") != SCHEMA_VERSION:
        raise ValueError(
            f"兼容矩阵 schemaVersion 必须为 {SCHEMA_VERSION}：{manifest.get('schemaVersion')}",
        )
    if manifest.get("evidenceMode") != "compatibility-dry-run":
        raise ValueError("兼容矩阵 evidenceMode 必须为 compatibility-dry-run")
    # entries 必须是四档互不重复的对象列表。
    raw_entries = manifest.get("entries")
    if not isinstance(raw_entries, list):
        raise ValueError("兼容矩阵 entries 必须是列表")
    entry_reports: list[dict[str, Any]] = []
    for index, raw_entry in enumerate(raw_entries):
        if not isinstance(raw_entry, dict):
            raise ValueError(f"entries[{index}] 必须是对象")
        entry_reports.append(validate_entry(raw_entry, index))
    # API 集合必须精确覆盖 Goal 指定档位，不允许用邻近版本代替。
    actual_api_levels = sorted(report["apiLevel"] for report in entry_reports)
    if actual_api_levels != list(REQUIRED_API_LEVELS):
        raise ValueError(
            f"API 矩阵不完整：actual={actual_api_levels} expected={list(REQUIRED_API_LEVELS)}",
        )
    return {
        "schemaVersion": SCHEMA_VERSION,
        "status": "pass",
        "evidenceMode": "compatibility-dry-run",
        "representativePerformanceEvidence": False,
        "manifest": artifact_record(manifest_path),
        "entries": entry_reports,
    }


def write_report(path: Path, report: Mapping[str, Any]) -> None:
    """以稳定 JSON 格式写入兼容矩阵报告。"""

    # 父目录由工具创建，使 CI 与本地使用同一输出约定。
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main(arguments: Sequence[str] | None = None) -> int:
    """执行兼容矩阵验收，并确保失败也产生机器可读报告。"""

    # 测试可传入参数列表，命令行调用默认读取 sys.argv。
    parsed_arguments = parse_arguments(sys.argv[1:] if arguments is None else arguments)
    try:
        # 通过报告包含全部原始文件摘要和逐场景指标契约。
        report = build_report(parsed_arguments.manifest)
        exit_code = 0
    except (FileNotFoundError, ValueError, element_tree.ParseError, OSError, json.JSONDecodeError) as error:
        # 失败报告保留清单路径和明确原因，同时进程必须非零退出。
        report = {
            "schemaVersion": SCHEMA_VERSION,
            "status": "fail",
            "evidenceMode": "compatibility-dry-run",
            "representativePerformanceEvidence": False,
            "manifestPath": parsed_arguments.manifest.as_posix(),
            "errors": [str(error)],
        }
        exit_code = 1
    write_report(parsed_arguments.output, report)
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
