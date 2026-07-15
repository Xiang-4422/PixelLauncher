#!/usr/bin/env python3
"""将确定性 JVM 渲染冒烟中位数与已批准的百分之十回归基线比较。"""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path
from typing import Any, Mapping, Sequence


# 趋势报告结构只能通过显式基线迁移变更。
SCHEMA_VERSION = 1

# Kotlin 报告使用七批次中位数采样时对应的格式版本。
REQUIRED_REPORT_FORMAT_VERSION = 2

# 虽然发布以设备指标为准，但每个 JVM 冒烟旅程仍是必测项。
REQUIRED_SCENES = (
    "animation",
    "graphics_primitives",
    "list_scroll",
    "overlay",
    "page_transition",
    "text_input",
)

# M6-2 将同配置关键指标回退上限固定为百分之十。
MAXIMUM_REGRESSION_PERCENT = 10.0


def parse_arguments(arguments: Sequence[str]) -> argparse.Namespace:
    """解析精确的冒烟报告、已批准基线和机器可读输出路径。"""

    # 显式路径可防止搜索旧的 latest 报告造成假绿。
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True, help="Current JVM smoke properties report.")
    parser.add_argument("--baseline", type=Path, required=True, help="Approved JVM smoke trend baseline.")
    parser.add_argument("--output", type=Path, required=True, help="Machine-readable trend gate report.")
    return parser.parse_args(arguments)


def parse_properties(path: Path) -> dict[str, str]:
    """严格解析键值报告，并拒绝缺失文件、重复键和格式错误行。"""

    if not path.is_file() or path.stat().st_size == 0:
        raise FileNotFoundError(f"Missing or empty JVM smoke report: {path}")
    # 解析值在完成字段专属类型校验前保留原始文本。
    values: dict[str, str] = {}
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not raw_line or raw_line.startswith("#"):
            continue
        if "=" not in raw_line:
            raise ValueError(f"Malformed JVM smoke report line {line_number}: {raw_line!r}")
        # 仅第一个等号用于分隔稳定属性键和值。
        key, value = raw_line.split("=", maxsplit=1)
        if not key or key in values:
            raise ValueError(f"Missing or duplicate JVM smoke key at line {line_number}: {key!r}")
        values[key] = value
    return values


def load_json_object(path: Path) -> dict[str, Any]:
    """加载一个必需的 UTF-8 基线对象。"""

    if not path.is_file() or path.stat().st_size == 0:
        raise FileNotFoundError(f"Missing or empty JVM smoke baseline: {path}")
    # 解码后的基线必须是对象，才能可靠访问审批与场景字段。
    decoded_value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(decoded_value, dict):
        raise ValueError(f"JVM smoke baseline must be a JSON object: {path}")
    return decoded_value


def required_string(values: Mapping[str, str], key: str) -> str:
    """返回一个必需且非空的报告属性。"""

    # 即使键存在，空值仍视为证据缺失。
    value = values.get(key)
    if value is None or not value.strip():
        raise ValueError(f"Missing JVM smoke report field: {key}")
    return value


def required_integer(values: Mapping[str, str], key: str) -> int:
    """返回一个必需的整型报告属性。"""

    # 使用严格整数解析，避免小数工作负载尺寸被截断。
    raw_value = required_string(values, key)
    try:
        parsed_value = int(raw_value)
    except ValueError as error:
        raise ValueError(f"JVM smoke report field {key} must be an integer: {raw_value!r}") from error
    return parsed_value


def required_float(values: Mapping[str, str], key: str) -> float:
    """返回一个必需且有限的浮点报告属性。"""

    # 非有限缩放值会让阈值比较静默失去意义。
    raw_value = required_string(values, key)
    try:
        parsed_value = float(raw_value)
    except ValueError as error:
        raise ValueError(f"JVM smoke report field {key} must be numeric: {raw_value!r}") from error
    if not math.isfinite(parsed_value):
        raise ValueError(f"JVM smoke report field {key} must be finite.")
    return parsed_value


def required_object(container: Mapping[str, Any], key: str, label: str) -> dict[str, Any]:
    """返回一个必需的基线子对象。"""

    # 嵌套对象让审批、工作负载及逐场景结构校验保持显式。
    child_value = container.get(key)
    if not isinstance(child_value, dict):
        raise ValueError(f"{label}.{key} must be an object.")
    return child_value


def baseline_integer(container: Mapping[str, Any], key: str, label: str) -> int:
    """返回一个必需的整型基线数值，并拒绝布尔值。"""

    # JSON 布尔值在 Python 中属于整数，但不能作为尺寸或时长。
    raw_value = container.get(key)
    if isinstance(raw_value, bool) or not isinstance(raw_value, (int, float)):
        raise ValueError(f"{label}.{key} must be an integer.")
    numeric_value = float(raw_value)
    if not math.isfinite(numeric_value) or not numeric_value.is_integer():
        raise ValueError(f"{label}.{key} must be a finite integer.")
    return int(numeric_value)


def parse_batch_averages(values: Mapping[str, str], prefix: str, sample_batches: int) -> list[int]:
    """解析精确的批次平均值，并要求配置的奇数批次数量。"""

    # Kotlin 冒烟测试按测量顺序输出逗号分隔的整数。
    raw_items = required_string(values, f"{prefix}.batchAverageNanos").split(",")
    if len(raw_items) != sample_batches:
        raise ValueError(f"{prefix}.batchAverageNanos has {len(raw_items)} batches; expected {sample_batches}.")
    try:
        batch_averages = [int(raw_item) for raw_item in raw_items]
    except ValueError as error:
        raise ValueError(f"{prefix}.batchAverageNanos must contain integers.") from error
    if any(batch_average <= 0 for batch_average in batch_averages):
        raise ValueError(f"{prefix}.batchAverageNanos must contain positive durations.")
    return batch_averages


def analyze(report_path: Path, baseline_path: Path) -> dict[str, Any]:
    """校验工作负载身份，并将每个场景中位数与已批准基线包络比较。"""

    # 当前 properties 报告必须是刚完成的 Gradle 测试精确输出。
    values = parse_properties(report_path)
    # 受源码管理的 JSON 基线携带显式审批和工作负载身份。
    baseline = load_json_object(baseline_path)
    if baseline_integer(baseline, "schemaVersion", "baseline") != SCHEMA_VERSION:
        raise ValueError("Unsupported JVM smoke baseline schema version.")
    if baseline.get("kind") != "pixel-jvm-smoke-baseline":
        raise ValueError("Unexpected JVM smoke baseline kind.")
    # 审批元数据防止自动生成的候选数值静默成为质量门禁。
    approval = required_object(baseline, "approval", "baseline")
    if approval.get("status") != "approved":
        raise ValueError("JVM smoke baseline is not explicitly approved.")
    for approval_field in ("approvedBy", "approvedAtUtc", "technicalReason"):
        approval_value = approval.get(approval_field)
        if not isinstance(approval_value, str) or not approval_value.strip():
            raise ValueError(f"baseline.approval.{approval_field} must be a non-empty string.")
    # 基线本身必须保持 M6-2 固定百分之十回归契约。
    baseline_regression_percent = baseline.get("maximumRegressionPercent")
    if baseline_regression_percent != MAXIMUM_REGRESSION_PERCENT:
        raise ValueError("JVM smoke baseline must use the fixed ten-percent regression limit.")

    # 工作负载字段防止场景尺寸或采样变化后复用不可比较的数据。
    workload = required_object(baseline, "workload", "baseline")
    report_format_version = required_integer(values, "formatVersion")
    warmup_frames = required_integer(values, "warmupFrames")
    sample_frames = required_integer(values, "sampleFrames")
    sample_batches = required_integer(values, "sampleBatches")
    scene_count = required_integer(values, "sceneCount")
    if report_format_version != REQUIRED_REPORT_FORMAT_VERSION:
        raise ValueError(f"Unsupported JVM smoke report format: {report_format_version}")
    if sample_batches <= 0 or sample_batches % 2 == 0:
        raise ValueError("JVM smoke sampleBatches must be a positive odd number.")
    expected_workload = {
        "reportFormatVersion": report_format_version,
        "warmupFrames": warmup_frames,
        "sampleFrames": sample_frames,
        "sampleBatches": sample_batches,
        "sceneCount": scene_count,
    }
    if any(workload.get(key) != value for key, value in expected_workload.items()):
        raise ValueError("JVM smoke report workload differs from the approved baseline.")
    if scene_count != len(REQUIRED_SCENES):
        raise ValueError(f"JVM smoke sceneCount is {scene_count}; expected {len(REQUIRED_SCENES)}.")
    # 阈值缩放仅为故障注入保留，趋势证据始终要求缩放为一。
    threshold_scale = required_float(values, "thresholdScale")
    if threshold_scale != 1.0:
        raise ValueError("JVM smoke trend comparison requires thresholdScale=1.0.")

    # 精确场景键可防止缺失或重命名工作负载借剩余子集通过。
    baseline_scenes = required_object(baseline, "scenes", "baseline")
    if set(baseline_scenes) != set(REQUIRED_SCENES):
        raise ValueError("JVM smoke baseline scene coverage does not match the required set.")
    # 每个场景独立产出百分之十比较和工作负载形状证明。
    checks: list[dict[str, Any]] = []
    for scene_name in REQUIRED_SCENES:
        prefix = f"scene.{scene_name}"
        baseline_scene = required_object(baseline_scenes, scene_name, "baseline.scenes")
        width = required_integer(values, f"{prefix}.width")
        height = required_integer(values, f"{prefix}.height")
        frames = required_integer(values, f"{prefix}.frames")
        total_nanos = required_integer(values, f"{prefix}.totalNanos")
        measured_average_nanos = required_integer(values, f"{prefix}.averageNanos")
        batch_averages = parse_batch_averages(values, prefix, sample_batches)
        if width != baseline_integer(baseline_scene, "width", f"baseline.scenes.{scene_name}"):
            raise ValueError(f"{scene_name} width differs from the approved workload.")
        if height != baseline_integer(baseline_scene, "height", f"baseline.scenes.{scene_name}"):
            raise ValueError(f"{scene_name} height differs from the approved workload.")
        if frames != sample_frames * sample_batches or total_nanos <= 0:
            raise ValueError(f"{scene_name} has invalid frame or total-time evidence.")
        # 报告中的关键指标必须等于所有批次平均值的真实中位数。
        calculated_median = sorted(batch_averages)[sample_batches // 2]
        if measured_average_nanos != calculated_median:
            raise ValueError(f"{scene_name} averageNanos is not the median batch average.")
        approved_baseline_nanos = baseline_integer(
            baseline_scene,
            "approvedBaselineAverageNanos",
            f"baseline.scenes.{scene_name}",
        )
        allowed_maximum_nanos = math.floor(
            approved_baseline_nanos * (1.0 + MAXIMUM_REGRESSION_PERCENT / 100.0),
        )
        regression_percent = (measured_average_nanos / approved_baseline_nanos - 1.0) * 100.0
        passed = measured_average_nanos <= allowed_maximum_nanos
        checks.append(
            {
                "scene": scene_name,
                "width": width,
                "height": height,
                "frames": frames,
                "measuredMedianAverageNanos": measured_average_nanos,
                "approvedBaselineAverageNanos": approved_baseline_nanos,
                "allowedMaximumNanos": allowed_maximum_nanos,
                "regressionPercent": regression_percent,
                "passed": passed,
            },
        )
    # 六个场景都必须处于已批准的百分之十趋势包络内。
    overall_passed = all(bool(check["passed"]) for check in checks)
    return {
        "schemaVersion": SCHEMA_VERSION,
        "kind": "pixel-jvm-smoke-trend-gate",
        "status": "passed" if overall_passed else "failed",
        "overallPassed": overall_passed,
        "runId": required_string(values, "runId"),
        "maximumRegressionPercent": MAXIMUM_REGRESSION_PERCENT,
        "workload": expected_workload,
        "environment": {
            "javaRuntimeVersion": values.get("javaRuntimeVersion", "unreported"),
            "javaVmName": values.get("javaVmName", "unreported"),
            "osName": values.get("osName", "unreported"),
            "osArch": values.get("osArch", "unreported"),
        },
        "checks": checks,
    }


def write_json(path: Path, report: Mapping[str, Any]) -> None:
    """为 CI 和验收证据写入稳定的严格 JSON 趋势报告。"""

    # 创建父目录以支持不存在预置报告目录的干净构建目录。
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(dict(report), indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main(arguments: Sequence[str] | None = None) -> int:
    """仅当六个 JVM 冒烟场景都通过已批准趋势包络时返回零。"""

    # 显式参数保证 shell 与单元测试调用一致。
    options = parse_arguments(arguments if arguments is not None else sys.argv[1:])
    try:
        # 遇到真实回归失败时也要先写出比较报告。
        report = analyze(options.report, options.baseline)
        write_json(options.output, report)
    except Exception as error:
        print(f"JVM performance trend validation failed: {error}", file=sys.stderr)
        return 2
    if not report["overallPassed"]:
        print(f"JVM performance trend FAILED; report: {options.output}", file=sys.stderr)
        return 1
    print(f"JVM performance trend passed; report: {options.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
