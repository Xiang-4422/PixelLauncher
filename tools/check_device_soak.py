#!/usr/bin/env python3
"""严格校验 Pixel Engine 设备长跑 JSON，拒绝短跑或资源残留冒充 Goal 证据。"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


# 当前 checker 只接受 instrumentation 明确发布的结构版本。
EXPECTED_SCHEMA_VERSION = 1
# 六类设备长跑旅程必须全部至少完成一次。
EXPECTED_JOURNEYS = {
    "startup",
    "listScroll",
    "textInput",
    "animation",
    "pageTransition",
    "overlay",
}
# 每轮终态必须为零且报告最大值也必须精确为零的资源集合。
EXPECTED_RESIDUE_KEYS = {
    "pendingCallbacks",
    "frameListeners",
    "activeTickers",
    "liveTickers",
    "sourceFramePending",
    "retainedElementRoot",
    "retainedRenderRoot",
    "retainedTargets",
    "pendingBuild",
    "focusedTextInput",
    "activePagers",
    "activeLists",
}
# Goal 接受的设备长跑最短秒数。
MINIMUM_GOAL_DURATION_SECONDS = 30 * 60
# Goal 接受的设备长跑最长秒数。
MAXIMUM_GOAL_DURATION_SECONDS = 60 * 60
# 最后一轮旅程允许的收尾超时，单位毫秒。
MAXIMUM_DURATION_OVERRUN_MILLIS = 30_000
# 正式长跑用于趋势判断的最少内存样本数。
MINIMUM_GOAL_MEMORY_SAMPLE_COUNT = 10
# PSS 固定增长预算，单位 KB。
FIXED_PSS_GROWTH_BUDGET_KB = 8 * 1_024
# PSS 比例增长预算使用起始中位数的五分之一。
PSS_GROWTH_BUDGET_DIVISOR = 5


class DeviceSoakValidationError(ValueError):
    """表示设备长跑报告违反结构、身份、时长或资源门禁。"""


def require(condition: bool, message: str) -> None:
    """条件不满足时抛出带稳定消息的报告校验错误。"""

    if not condition:
        raise DeviceSoakValidationError(message)


def require_mapping(value: Any, name: str) -> dict[str, Any]:
    """要求指定字段是 JSON object 并返回其类型收窄结果。"""

    require(isinstance(value, dict), f"{name} 必须是 object")
    return value


def require_integer(value: Any, name: str, *, minimum: int = 0) -> int:
    """要求指定字段是非布尔整数且不低于下界。"""

    require(isinstance(value, int) and not isinstance(value, bool), f"{name} 必须是整数")
    require(value >= minimum, f"{name} 必须 >= {minimum}")
    return value


def validate_device(device: Any) -> dict[str, Any]:
    """校验设备身份、API 和刷新率字段，避免缺失设备元数据。"""

    # 设备对象必须保留可与宿主授权链复核的硬件序列号。
    device_mapping = require_mapping(device, "device")
    hardware_serial = device_mapping.get("hardwareSerial")
    require(isinstance(hardware_serial, str) and bool(hardware_serial.strip()), "硬件序列号不能为空")
    require(type(device_mapping.get("isEmulator")) is bool, "device.isEmulator 必须是布尔值")
    require_integer(device_mapping.get("apiLevel"), "device.apiLevel", minimum=24)
    refresh_rate = device_mapping.get("refreshRateHz")
    require(
        isinstance(refresh_rate, (int, float)) and not isinstance(refresh_rate, bool),
        "device.refreshRateHz 必须是数值",
    )
    require(float(refresh_rate) > 0.0, "device.refreshRateHz 必须 > 0")
    return device_mapping


def validate_memory_samples(samples: Any) -> list[dict[str, int]]:
    """校验内存样本数量、顺序与正数 PSS/heap 值。"""

    require(isinstance(samples, list), "memorySamples 必须是数组")
    require(len(samples) >= 2, "memorySamples 至少需要首尾两个样本")
    # 单调相对时间用于证明样本来自同一场连续执行。
    previous_elapsed = -1
    validated_samples: list[dict[str, int]] = []
    for index, sample in enumerate(samples):
        # 每个样本必须包含精确的时间、PSS 和 Java heap。
        sample_mapping = require_mapping(sample, f"memorySamples[{index}]")
        require(
            set(sample_mapping) == {"elapsedMillis", "totalPssKb", "javaHeapKb"},
            f"memorySamples[{index}] 字段集合不匹配",
        )
        elapsed_millis = require_integer(
            sample_mapping["elapsedMillis"],
            f"memorySamples[{index}].elapsedMillis",
        )
        total_pss_kb = require_integer(
            sample_mapping["totalPssKb"],
            f"memorySamples[{index}].totalPssKb",
            minimum=1,
        )
        java_heap_kb = require_integer(
            sample_mapping["javaHeapKb"],
            f"memorySamples[{index}].javaHeapKb",
            minimum=1,
        )
        require(elapsed_millis > previous_elapsed, "memorySamples.elapsedMillis 必须严格递增")
        previous_elapsed = elapsed_millis
        validated_samples.append(
            {
                "elapsedMillis": elapsed_millis,
                "totalPssKb": total_pss_kb,
                "javaHeapKb": java_heap_kb,
            },
        )
    return validated_samples


def validate_heap_trend(trend: Any) -> dict[str, Any]:
    """复算 PSS 增长和预算，拒绝只篡改 bounded 布尔值。"""

    # 趋势对象只接受当前协议的五个字段。
    trend_mapping = require_mapping(trend, "heapTrend")
    require(
        set(trend_mapping)
        == {
            "firstMedianPssKb",
            "lastMedianPssKb",
            "growthPssKb",
            "allowedGrowthPssKb",
            "bounded",
        },
        "heapTrend 字段集合不匹配",
    )
    first_median = require_integer(trend_mapping["firstMedianPssKb"], "heapTrend.firstMedianPssKb", minimum=1)
    last_median = require_integer(trend_mapping["lastMedianPssKb"], "heapTrend.lastMedianPssKb", minimum=1)
    growth = trend_mapping["growthPssKb"]
    require(isinstance(growth, int) and not isinstance(growth, bool), "heapTrend.growthPssKb 必须是整数")
    allowed_growth = require_integer(
        trend_mapping["allowedGrowthPssKb"],
        "heapTrend.allowedGrowthPssKb",
        minimum=1,
    )
    require(growth == last_median - first_median, "heapTrend.growthPssKb 复算不一致")
    expected_allowed_growth = max(
        FIXED_PSS_GROWTH_BUDGET_KB,
        first_median // PSS_GROWTH_BUDGET_DIVISOR,
    )
    require(allowed_growth == expected_allowed_growth, "heapTrend.allowedGrowthPssKb 预算不一致")
    require(type(trend_mapping["bounded"]) is bool, "heapTrend.bounded 必须是布尔值")
    require(trend_mapping["bounded"] == (growth <= allowed_growth), "heapTrend.bounded 复算不一致")
    require(trend_mapping["bounded"], "设备长跑 PSS 趋势超过允许预算")
    return trend_mapping


def validate_report(report: Any, *, require_qualified: bool) -> dict[str, Any]:
    """校验完整设备长跑报告并返回供 CI 留存的精简摘要。"""

    # 根对象和结构版本必须精确匹配当前 checker。
    report_mapping = require_mapping(report, "report")
    require(report_mapping.get("schemaVersion") == EXPECTED_SCHEMA_VERSION, "schemaVersion 不匹配")
    require(report_mapping.get("status") == "pass", "设备长跑 status 必须为 pass")
    require(report_mapping.get("failure") is None, "通过报告的 failure 必须为 null")
    device = validate_device(report_mapping.get("device"))

    # 时长、周期和逐旅程计数共同证明不是空测试。
    requested_duration_seconds = require_integer(
        report_mapping.get("requestedDurationSeconds"),
        "requestedDurationSeconds",
        minimum=1,
    )
    require(
        requested_duration_seconds <= MAXIMUM_GOAL_DURATION_SECONDS,
        "requestedDurationSeconds 超过 60 分钟",
    )
    actual_duration_millis = require_integer(
        report_mapping.get("actualDurationMillis"),
        "actualDurationMillis",
        minimum=1,
    )
    require(
        actual_duration_millis >= requested_duration_seconds * 1_000,
        "actualDurationMillis 未达到请求时长",
    )
    require(
        actual_duration_millis
        <= MAXIMUM_GOAL_DURATION_SECONDS * 1_000 + MAXIMUM_DURATION_OVERRUN_MILLIS,
        "actualDurationMillis 超过 60 分钟与收尾预算",
    )
    completed_cycles = require_integer(
        report_mapping.get("completedJourneyCycles"),
        "completedJourneyCycles",
        minimum=1,
    )
    terminal_checks = require_integer(
        report_mapping.get("terminalDiagnosticsChecks"),
        "terminalDiagnosticsChecks",
        minimum=1,
    )
    require(terminal_checks == completed_cycles, "每个旅程周期都必须完成一次终态诊断")
    journeys = require_mapping(report_mapping.get("journeys"), "journeys")
    require(set(journeys) == EXPECTED_JOURNEYS, "journeys 字段集合不匹配")
    validated_journey_counts = {
        name: require_integer(value, f"journeys.{name}", minimum=1)
        for name, value in journeys.items()
    }
    require(sum(validated_journey_counts.values()) == completed_cycles, "journey 次数总和不匹配")

    # 最大残留必须包含精确资源集合且全部为零。
    residue = require_mapping(
        report_mapping.get("maximumTerminalResidue"),
        "maximumTerminalResidue",
    )
    require(set(residue) == EXPECTED_RESIDUE_KEYS, "maximumTerminalResidue 字段集合不匹配")
    for name, value in residue.items():
        require_integer(value, f"maximumTerminalResidue.{name}")
        require(value == 0, f"maximumTerminalResidue.{name} 必须为零")

    # 内存趋势字段必须与样本和固定预算契约一致。
    memory_samples = validate_memory_samples(report_mapping.get("memorySamples"))
    require(
        memory_samples[-1]["elapsedMillis"] <= actual_duration_millis,
        "最后一个 memorySamples.elapsedMillis 不能超过实际执行时长",
    )
    heap_trend = validate_heap_trend(report_mapping.get("heapTrend"))
    reported_qualified = report_mapping.get("qualifiesForGoal")
    require(type(reported_qualified) is bool, "qualifiesForGoal 必须是布尔值")
    expected_qualified = (
        requested_duration_seconds >= MINIMUM_GOAL_DURATION_SECONDS
        and actual_duration_millis >= MINIMUM_GOAL_DURATION_SECONDS * 1_000
        and len(memory_samples) >= MINIMUM_GOAL_MEMORY_SAMPLE_COUNT
    )
    require(reported_qualified == expected_qualified, "qualifiesForGoal 复算不一致")
    if require_qualified:
        require(reported_qualified, "报告是接线短跑，未达到 30–60 分钟 Goal 证据要求")

    return {
        "schemaVersion": EXPECTED_SCHEMA_VERSION,
        "status": "pass",
        "qualifiesForGoal": reported_qualified,
        "hardwareSerial": device["hardwareSerial"],
        "isEmulator": device["isEmulator"],
        "apiLevel": device["apiLevel"],
        "refreshRateHz": device["refreshRateHz"],
        "requestedDurationSeconds": requested_duration_seconds,
        "actualDurationMillis": actual_duration_millis,
        "completedJourneyCycles": completed_cycles,
        "memorySampleCount": len(memory_samples),
        "pssGrowthKb": heap_trend["growthPssKb"],
        "allowedPssGrowthKb": heap_trend["allowedGrowthPssKb"],
        "maximumTerminalResidue": residue,
    }


def parse_args(argv: list[str]) -> argparse.Namespace:
    """解析报告路径、正式证据要求和可选摘要输出路径。"""

    # 参数名称保持可直接用于本地脚本和 CI。
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True, help="instrumentation 原始 JSON")
    parser.add_argument(
        "--require-qualified",
        action="store_true",
        help="要求报告真实执行 30–60 分钟且至少有 10 个内存样本",
    )
    parser.add_argument("--output", type=Path, help="可选的机器验收摘要 JSON")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    """加载、校验并可选写出精简摘要。"""

    # 命令行入口使用显式参数列表，便于单元测试。
    args = parse_args(sys.argv[1:] if argv is None else argv)
    try:
        # 原始 JSON 必须是 UTF-8 且能被标准解析器完整读取。
        report = json.loads(args.report.read_text(encoding="utf-8"))
        summary = validate_report(report, require_qualified=args.require_qualified)
    except (OSError, json.JSONDecodeError, DeviceSoakValidationError) as error:
        print(f"Pixel device soak validation failed: {error}", file=sys.stderr)
        return 1
    if args.output is not None:
        # 摘要目录由 checker 创建，避免调用脚本依赖预先存在的构建目录。
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(
            json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
    print(
        "Pixel device soak validation passed: "
        f"qualified={summary['qualifiesForGoal']} "
        f"durationMs={summary['actualDurationMillis']} "
        f"cycles={summary['completedJourneyCycles']} "
        f"samples={summary['memorySampleCount']}",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
