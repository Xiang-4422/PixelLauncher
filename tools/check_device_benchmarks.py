#!/usr/bin/env python3
"""Validate Pixel Engine device benchmarks and enforce absolute plus trend gates."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import sys
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence


# The report schema is versioned so baseline changes require an explicit migration.
SCHEMA_VERSION = 1

# Startup methods required by the M6-2 acceptance contract.
REQUIRED_STARTUP_SCENARIOS = ("coldStartup", "hotStartup")

# Frame-producing methods required by the M6-2 acceptance contract.
REQUIRED_FRAME_SCENARIOS = (
    "animation",
    "listScroll",
    "overlay",
    "pageTransition",
    "textInput",
)

# M6-2 五条 CPU 热路径与 M6-3 六类渲染最坏场景组成完整实体设备 Microbenchmark 集合。
REQUIRED_MICRO_SCENARIOS = (
    "graphemeBoundaryMap",
    "mixedBidiParagraphLayout",
    "pixelBufferOperations",
    "resourceCatalogParsing",
    "retainedLayoutAndPaint",
    "fullBrightnessNoGapCanvasSubmit",
    "squareGapGridCanvasSubmit",
    "nestedOpacityPaint",
    "clippedOverflowPaint",
    "complexTextLayoutAndPaint",
    "fastLazyListScrollPaint",
)

# 每个 Microbenchmark 方法必须来自真实实现它的固定测试类，防止同名替代行为验收。
MICRO_SCENARIO_CLASS_NAMES = {
    "graphemeBoundaryMap": "com.purride.pixelmicrobenchmark.PixelEngineMicrobenchmark",
    "mixedBidiParagraphLayout": "com.purride.pixelmicrobenchmark.PixelEngineMicrobenchmark",
    "pixelBufferOperations": "com.purride.pixelmicrobenchmark.PixelEngineMicrobenchmark",
    "resourceCatalogParsing": "com.purride.pixelmicrobenchmark.PixelEngineMicrobenchmark",
    "retainedLayoutAndPaint": "com.purride.pixelmicrobenchmark.PixelEngineMicrobenchmark",
    "fullBrightnessNoGapCanvasSubmit": (
        "com.purride.pixelmicrobenchmark.PixelRenderWorstCaseMicrobenchmark"
    ),
    "squareGapGridCanvasSubmit": (
        "com.purride.pixelmicrobenchmark.PixelRenderWorstCaseMicrobenchmark"
    ),
    "nestedOpacityPaint": "com.purride.pixelmicrobenchmark.PixelRenderWorstCaseMicrobenchmark",
    "clippedOverflowPaint": "com.purride.pixelmicrobenchmark.PixelRenderWorstCaseMicrobenchmark",
    "complexTextLayoutAndPaint": (
        "com.purride.pixelmicrobenchmark.PixelRenderWorstCaseMicrobenchmark"
    ),
    "fastLazyListScrollPaint": (
        "com.purride.pixelmicrobenchmark.PixelRenderWorstCaseMicrobenchmark"
    ),
}

# The benchmark source token proves that measurement requires a packaged Baseline Profile.
REQUIRED_COMPILATION_SOURCE_TOKEN = "CompilationMode.Partial(BaselineProfileMode.Require)"

# At least ten Macrobenchmark repetitions are required for each scenario.
MINIMUM_MACRO_ITERATIONS = 10

# The Goal fixes same-configuration regression tolerance at ten percent.
MAXIMUM_REGRESSION_PERCENT = 10.0

# The Goal fixes the p95 frame-production budget at seventy percent of one frame.
P95_FRAME_BUDGET_FRACTION = 0.70

# The 60 Hz core-journey jank ceiling is strictly below one percent.
SIXTY_HZ_JANK_LIMIT_PERCENT = 1.0

# AndroidX reports 60 Hz panels with small floating-point variation across devices.
SIXTY_HZ_TOLERANCE = 1.0

# 正式门禁只接受代表性实体设备证据。
PHYSICAL_EVIDENCE_MODE = "physical"

# 模拟器演练只验证采集、trace、指标和候选链路，不具备发布代表性。
EMULATOR_REHEARSAL_MODE = "emulator-rehearsal"

# AndroidX Microbenchmark 在显式抑制 EMULATOR 环境错误时写入的固定方法名前缀。
EMULATOR_BENCHMARK_PREFIX = "EMULATOR_"

# AndroidX 为避免 Method Trace 触发 ANR 时输出的固定安全提示；门禁只接受完整提示与显式保护参数。
METHOD_TRACE_ANR_SKIP_PATTERN = re.compile(
    r"\ASkipping method trace of estimated duration "
    r"(?P<estimated_seconds>[0-9]+(?:\.[0-9]+)?) sec to avoid ANR\n\n"
    r"To disable this behavior, set instrumentation arg:\n"
    r"androidx\.benchmark\.profiling\.skipWhenDurationRisksAnr = false(?:\n|\Z)",
)


def parse_arguments(arguments: Sequence[str]) -> argparse.Namespace:
    """Parse explicit evidence paths and immutable benchmark configuration metadata."""

    # The parser keeps every evidence input explicit so a stale report cannot be discovered implicitly.
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--macro-json", type=Path, required=True, help="Raw Macrobenchmark JSON output.")
    parser.add_argument("--micro-json", type=Path, required=True, help="Raw Microbenchmark JSON output.")
    parser.add_argument("--macro-source", type=Path, required=True, help="Measured Macrobenchmark source.")
    parser.add_argument("--macro-apk", type=Path, required=True, help="Installed Macrobenchmark APK.")
    parser.add_argument("--target-apk", type=Path, required=True, help="Installed benchmark target APK.")
    parser.add_argument("--micro-apk", type=Path, required=True, help="Installed Microbenchmark APK.")
    parser.add_argument(
        "--baseline-profile-report",
        type=Path,
        required=True,
        help="Passing Baseline Profile packaging verification report.",
    )
    parser.add_argument("--output", type=Path, required=True, help="Machine-readable gate report.")
    parser.add_argument("--baseline", type=Path, help="Explicitly approved same-configuration baseline.")
    parser.add_argument(
        "--candidate-baseline-output",
        type=Path,
        help="Optional unapproved baseline candidate derived from this measurement.",
    )
    parser.add_argument("--measurement-id", required=True, help="Stable identifier for the physical run.")
    parser.add_argument("--refresh-rate-hz", type=float, required=True, help="Active panel refresh rate.")
    parser.add_argument(
        "--macro-build-variant",
        default="benchmarkRelease",
        help="Macrobenchmark test APK variant used for measurement.",
    )
    parser.add_argument(
        "--target-build-type",
        default="benchmark",
        help="Target application build type used for measurement.",
    )
    parser.add_argument(
        "--micro-build-variant",
        default="releaseAndroidTest",
        help="Microbenchmark test APK variant used for measurement.",
    )
    parser.add_argument(
        "--compilation-policy",
        default="partial-baseline-profile-required",
        help="Source-level Macrobenchmark compilation policy.",
    )
    parser.add_argument(
        "--report-only",
        action="store_true",
        help="Write failed quality evidence without converting gate failures to process failure.",
    )
    parser.add_argument(
        "--evidence-mode",
        choices=(PHYSICAL_EVIDENCE_MODE, EMULATOR_REHEARSAL_MODE),
        default=PHYSICAL_EVIDENCE_MODE,
        help="证据模式；默认实体设备，模拟器只能选择非发布演练模式。",
    )
    return parser.parse_args(arguments)


def load_json_object(path: Path, label: str) -> dict[str, Any]:
    """Load one required UTF-8 JSON object and reject missing or non-object evidence."""

    if not path.is_file():
        raise FileNotFoundError(f"Missing {label}: {path}")
    # The decoded value is checked before callers rely on object keys.
    decoded_value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(decoded_value, dict):
        raise ValueError(f"{label} must contain a JSON object: {path}")
    return decoded_value


def sha256_file(path: Path) -> str:
    """Return the SHA-256 of one exact artifact without loading large traces into memory."""

    if not path.is_file():
        raise FileNotFoundError(f"Missing evidence artifact: {path}")
    # The digest records the exact bytes accepted by the gate.
    digest = hashlib.sha256()
    with path.open("rb") as source:
        # One MiB chunks bound memory while hashing hundreds of MiB of Perfetto evidence.
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def finite_number(value: Any, label: str) -> float:
    """Convert one JSON number to a finite float while rejecting booleans and invalid values."""

    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{label} must be numeric.")
    # Non-finite values cannot participate in deterministic quality thresholds.
    numeric_value = float(value)
    if not math.isfinite(numeric_value):
        raise ValueError(f"{label} must be finite.")
    return numeric_value


def integer_value(value: Any, label: str) -> int:
    """Convert one integral JSON number while rejecting fractional and boolean values."""

    # The numeric value is validated before its integral representation is accepted.
    numeric_value = finite_number(value, label)
    if not numeric_value.is_integer():
        raise ValueError(f"{label} must be an integer.")
    return int(numeric_value)


def object_value(container: Mapping[str, Any], key: str, label: str) -> dict[str, Any]:
    """Return one required child object with a field-specific validation message."""

    # The selected child remains a dictionary so nested metric access is type-safe.
    child_value = container.get(key)
    if not isinstance(child_value, dict):
        raise ValueError(f"{label}.{key} must be an object.")
    return child_value


def list_value(container: Mapping[str, Any], key: str, label: str) -> list[Any]:
    """Return one required child list with a field-specific validation message."""

    # The selected child remains a list so iteration counts cannot be forged by strings.
    child_value = container.get(key)
    if not isinstance(child_value, list):
        raise ValueError(f"{label}.{key} must be a list.")
    return child_value


def string_value(container: Mapping[str, Any], key: str, label: str) -> str:
    """Return one required non-empty string field."""

    # Whitespace-only identities are missing evidence rather than valid labels.
    child_value = container.get(key)
    if not isinstance(child_value, str) or not child_value.strip():
        raise ValueError(f"{label}.{key} must be a non-empty string.")
    return child_value


def percentile(sorted_values: Sequence[float], fraction: float) -> float:
    """Return a deterministic nearest-rank percentile for startup and micro distributions."""

    if not sorted_values:
        raise ValueError("Cannot calculate a percentile for an empty sample.")
    # Nearest-rank is deliberately conservative and stable for small device samples.
    rank = max(1, math.ceil(fraction * len(sorted_values)))
    return sorted_values[min(rank - 1, len(sorted_values) - 1)]


def numeric_runs(metric: Mapping[str, Any], label: str) -> list[float]:
    """Read one flat AndroidX metric run list and reject empty or invalid samples."""

    # Each sample is validated independently so one corrupt run cannot be hidden by summary fields.
    raw_runs = list_value(metric, "runs", label)
    runs = [finite_number(value, f"{label}.runs[{index}]") for index, value in enumerate(raw_runs)]
    if not runs:
        raise ValueError(f"{label}.runs must not be empty.")
    return runs


def sampled_runs(metric: Mapping[str, Any], label: str, expected_iterations: int) -> list[float]:
    """Flatten one AndroidX sampled-metric run matrix while preserving iteration coverage."""

    # The outer list must correspond exactly to the configured Macrobenchmark repetitions.
    raw_iterations = list_value(metric, "runs", label)
    if len(raw_iterations) != expected_iterations:
        raise ValueError(
            f"{label}.runs has {len(raw_iterations)} iterations; expected {expected_iterations}.",
        )
    # The flattened samples represent every frame captured across all repetitions.
    flattened_samples: list[float] = []
    for iteration_index, raw_iteration in enumerate(raw_iterations):
        if not isinstance(raw_iteration, list) or not raw_iteration:
            raise ValueError(f"{label}.runs[{iteration_index}] must be a non-empty list.")
        for sample_index, raw_sample in enumerate(raw_iteration):
            flattened_samples.append(
                finite_number(raw_sample, f"{label}.runs[{iteration_index}][{sample_index}]"),
            )
    return flattened_samples


def artifact_record(path: Path) -> dict[str, Any]:
    """Describe one exact APK, source, JSON, or verification report used by the gate."""

    # The byte count and hash make later acceptance reports independently auditable.
    return {
        "path": path.as_posix(),
        "bytes": path.stat().st_size if path.is_file() else 0,
        "sha256": sha256_file(path),
    }


def aggregate_manifest_hash(entries: Iterable[Mapping[str, Any]]) -> str:
    """Hash a sorted trace manifest so acceptance docs can cite one compact identifier."""

    # Stable JSON serialization makes the aggregate independent of filesystem enumeration order.
    normalized_entries = sorted(entries, key=lambda entry: str(entry["filename"]))
    encoded_manifest = json.dumps(normalized_entries, separators=(",", ":"), sort_keys=True).encode("utf-8")
    return hashlib.sha256(encoded_manifest).hexdigest()


def profiler_evidence(
    benchmark: Mapping[str, Any],
    trace_root: Path,
    required_type_counts: Mapping[str, int],
) -> dict[str, Any]:
    """Verify profiler type counts, file presence, byte size, and hashes for one benchmark."""

    # Profiler descriptors are emitted by AndroidX beside its raw benchmark JSON.
    profiler_outputs = list_value(benchmark, "profilerOutputs", string_value(benchmark, "name", "benchmark"))
    # Counts make missing Perfetto or method traces a visible evidence failure.
    observed_type_counts: dict[str, int] = {}
    # The manifest retains every profiler artifact accepted by this gate.
    manifest_entries: list[dict[str, Any]] = []
    all_files_present = True
    for output_index, profiler_output in enumerate(profiler_outputs):
        if not isinstance(profiler_output, dict):
            raise ValueError(f"profilerOutputs[{output_index}] must be an object.")
        # AndroidX's profiler type selects the evidence family being counted.
        profiler_type = string_value(profiler_output, "type", f"profilerOutputs[{output_index}]")
        # The relative filename is resolved only against the raw JSON directory.
        filename = string_value(profiler_output, "filename", f"profilerOutputs[{output_index}]")
        profiler_path = trace_root / filename
        observed_type_counts[profiler_type] = observed_type_counts.get(profiler_type, 0) + 1
        file_present = profiler_path.is_file() and profiler_path.stat().st_size > 0
        all_files_present = all_files_present and file_present
        # Missing files remain visible in the report without inventing hashes or byte counts.
        manifest_entries.append(
            {
                "filename": filename,
                "type": profiler_type,
                "present": file_present,
                "bytes": profiler_path.stat().st_size if file_present else 0,
                "sha256": sha256_file(profiler_path) if file_present else None,
            },
        )
    # Exact type counts prevent a partial profiler run from satisfying M6-2 evidence.
    type_counts_passed = all(
        observed_type_counts.get(profiler_type, 0) == required_count
        for profiler_type, required_count in required_type_counts.items()
    ) and set(observed_type_counts) == set(required_type_counts)
    return {
        "passed": all_files_present and type_counts_passed,
        "requiredTypeCounts": dict(required_type_counts),
        "observedTypeCounts": observed_type_counts,
        "files": manifest_entries,
    }


def method_trace_anr_skip_evidence(
    benchmark: Mapping[str, Any],
    trace_root: Path,
) -> dict[str, Any]:
    """验证 AndroidX 因预计 Method Trace 时长会触发 ANR 而保留的原始安全提示。"""

    # AndroidX additional-test-output 的消息名由原始测试类与方法名稳定组成。
    benchmark_name = string_value(benchmark, "name", "benchmark")
    benchmark_class = string_value(benchmark, "className", f"benchmark.{benchmark_name}")
    message_filename = (
        "additionaltestoutput.benchmark.message_"
        f"{benchmark_class}.{benchmark_name}.txt"
    )
    # 路径组成字段不得携带目录分隔符，避免原始 JSON 逃出本次证据目录。
    if any(separator in benchmark_name or separator in benchmark_class for separator in ("/", "\\")):
        raise ValueError("Benchmark class and name must not contain path separators.")
    message_path = trace_root / message_filename
    if not message_path.is_file() or message_path.stat().st_size <= 0:
        return {
            "passed": False,
            "reason": "Missing AndroidX Method Trace ANR-safety message.",
            "filename": message_filename,
        }
    # 仅解析 AndroidX 固定前缀；其后的基准数值仍由原始 JSON 独立校验。
    message_text = message_path.read_text(encoding="utf-8")
    matched_message = METHOD_TRACE_ANR_SKIP_PATTERN.match(message_text)
    if matched_message is None:
        return {
            "passed": False,
            "reason": "Method Trace skip message is not the exact AndroidX ANR-safety contract.",
            "filename": message_filename,
            "artifact": artifact_record(message_path),
        }
    # 预计时长必须为正数，防止空值或非数值提示冒充平台安全决定。
    estimated_seconds = float(matched_message.group("estimated_seconds"))
    if not math.isfinite(estimated_seconds) or estimated_seconds <= 0.0:
        return {
            "passed": False,
            "reason": "Method Trace ANR-risk estimate must be a positive finite duration.",
            "filename": message_filename,
            "artifact": artifact_record(message_path),
        }
    return {
        "passed": True,
        "reason": "AndroidX skipped Method Trace because its estimated duration risks ANR.",
        "estimatedDurationSeconds": estimated_seconds,
        "filename": message_filename,
        "artifact": artifact_record(message_path),
    }


def device_identity(raw_context: Mapping[str, Any], label: str) -> dict[str, Any]:
    """Extract stable physical-device and OS identity from AndroidX benchmark context."""

    # AndroidX nests model identity and platform version under the build object.
    build = object_value(raw_context, "build", label)
    # The SDK level is nested once more under the version object.
    version = object_value(build, "version", f"{label}.build")
    return {
        "brand": string_value(build, "brand", f"{label}.build"),
        "model": string_value(build, "model", f"{label}.build"),
        "device": string_value(build, "device", f"{label}.build"),
        "fingerprint": string_value(build, "fingerprint", f"{label}.build"),
        "buildId": string_value(build, "id", f"{label}.build"),
        "apiLevel": integer_value(version.get("sdk"), f"{label}.build.version.sdk"),
    }


def identity_looks_like_emulator(identity: Mapping[str, Any]) -> bool:
    """使用 Android 官方 AVD 标识组合判断基准身份是否明显来自模拟器。"""

    # 三个独立字段至少命中两个，避免单个厂商字符串误伤真实设备。
    model = str(identity.get("model", "")).lower()
    device = str(identity.get("device", "")).lower()
    fingerprint = str(identity.get("fingerprint", "")).lower()
    emulator_signals = (
        model.startswith("sdk_gphone") or "emulator" in model,
        device.startswith("emu") or "emulator" in device,
        "sdk_gphone" in fingerprint or "/emu" in fingerprint or "generic" in fingerprint,
    )
    return sum(1 for signal in emulator_signals if signal) >= 2


def validate_evidence_mode(evidence_mode: str, identity: Mapping[str, Any]) -> None:
    """强制实体门禁与模拟器演练使用互斥身份，防止演练数据冒充发布证据。"""

    # 当前身份只计算一次，后续两个分支使用完全相反的约束。
    is_emulator = identity_looks_like_emulator(identity)
    if evidence_mode == PHYSICAL_EVIDENCE_MODE and is_emulator:
        raise ValueError("Physical evidence mode rejects Android emulator identity.")
    if evidence_mode == EMULATOR_REHEARSAL_MODE and not is_emulator:
        raise ValueError("Emulator rehearsal mode requires Android emulator identity.")


def indexed_benchmarks(
    raw_report: Mapping[str, Any],
    expected_names: set[str],
    label: str,
    required_name_prefix: str = "",
) -> dict[str, dict[str, Any]]:
    """Index exactly the required benchmark methods and reject duplicates or silent omissions."""

    # The raw method list is the authoritative coverage set for this physical run.
    raw_benchmarks = list_value(raw_report, "benchmarks", label)
    indexed: dict[str, dict[str, Any]] = {}
    for benchmark_index, raw_benchmark in enumerate(raw_benchmarks):
        if not isinstance(raw_benchmark, dict):
            raise ValueError(f"{label}.benchmarks[{benchmark_index}] must be an object.")
        # Method names are stable contract identifiers shared with the Goal and baseline.
        raw_benchmark_name = string_value(raw_benchmark, "name", f"{label}.benchmarks[{benchmark_index}]")
        # 模拟器演练必须保留 AndroidX 的环境前缀；正式实体证据不执行任何名称改写。
        if required_name_prefix:
            if not raw_benchmark_name.startswith(required_name_prefix):
                raise ValueError(
                    f"{label}.benchmarks[{benchmark_index}].name must start with "
                    f"{required_name_prefix!r} in emulator rehearsal mode.",
                )
            benchmark_name = raw_benchmark_name[len(required_name_prefix):]
        else:
            benchmark_name = raw_benchmark_name
        if benchmark_name in indexed:
            raise ValueError(f"Duplicate {label} benchmark: {benchmark_name}")
        indexed[benchmark_name] = raw_benchmark
    # Exact coverage means a renamed, deleted, or unreviewed additional scenario cannot pass silently.
    observed_names = set(indexed)
    if observed_names != expected_names:
        missing_names = sorted(expected_names - observed_names)
        unexpected_names = sorted(observed_names - expected_names)
        raise ValueError(f"{label} coverage mismatch; missing={missing_names}, unexpected={unexpected_names}")
    return indexed


def analyze_macro(raw_report: Mapping[str, Any], trace_root: Path, refresh_rate_hz: float) -> dict[str, Any]:
    """Analyze required startup and frame scenarios using official AndroidX metric semantics."""

    if not math.isfinite(refresh_rate_hz) or refresh_rate_hz <= 0:
        raise ValueError("refresh-rate-hz must be a positive finite number.")
    # One frame budget is the physical panel period used by every absolute frame threshold.
    frame_budget_ms = 1000.0 / refresh_rate_hz
    # The p95 ceiling is fixed by the Goal and is never inferred from the current measurement.
    p95_limit_ms = frame_budget_ms * P95_FRAME_BUDGET_FRACTION
    # Exact benchmark coverage combines two startup and five frame-producing scenarios.
    expected_names = set(REQUIRED_STARTUP_SCENARIOS) | set(REQUIRED_FRAME_SCENARIOS)
    indexed = indexed_benchmarks(raw_report, expected_names, "macro")
    # Startup summaries preserve the complete ten-run distribution and trace evidence.
    startup_results: dict[str, Any] = {}
    # Frame summaries enforce CPU production, deadline, and 60 Hz jank thresholds.
    frame_results: dict[str, Any] = {}
    # All profiler entries are aggregated into one run-level evidence manifest.
    trace_manifest: list[dict[str, Any]] = []
    evidence_passed = True
    thermal_passed = True

    for scenario_name in REQUIRED_STARTUP_SCENARIOS:
        # Each required startup method must use the configured repetition floor.
        benchmark = indexed[scenario_name]
        repeat_iterations = integer_value(benchmark.get("repeatIterations"), f"macro.{scenario_name}.repeatIterations")
        if repeat_iterations < MINIMUM_MACRO_ITERATIONS:
            raise ValueError(f"macro.{scenario_name} has only {repeat_iterations} repetitions.")
        # Thermal sleep is preserved as a failure because throttled samples are not comparable.
        thermal_sleep_seconds = integer_value(
            benchmark.get("thermalThrottleSleepSeconds"),
            f"macro.{scenario_name}.thermalThrottleSleepSeconds",
        )
        thermal_passed = thermal_passed and thermal_sleep_seconds == 0
        # StartupTimingMetric reports time from launch intent through initial display.
        metrics = object_value(benchmark, "metrics", f"macro.{scenario_name}")
        startup_metric = object_value(metrics, "timeToInitialDisplayMs", f"macro.{scenario_name}.metrics")
        startup_runs = sorted(numeric_runs(startup_metric, f"macro.{scenario_name}.timeToInitialDisplayMs"))
        if len(startup_runs) != repeat_iterations:
            raise ValueError(f"macro.{scenario_name} startup run count does not match repetitions.")
        # Every Macrobenchmark repetition must retain one non-empty Perfetto trace.
        profiler = profiler_evidence(benchmark, trace_root, {"PerfettoTrace": repeat_iterations})
        evidence_passed = evidence_passed and bool(profiler["passed"])
        trace_manifest.extend(profiler["files"])
        startup_results[scenario_name] = {
            "iterations": repeat_iterations,
            "thermalThrottleSleepSeconds": thermal_sleep_seconds,
            "medianMs": finite_number(
                startup_metric.get("median"),
                f"macro.{scenario_name}.timeToInitialDisplayMs.median",
            ),
            "p95Ms": percentile(startup_runs, 0.95),
            "minimumMs": startup_runs[0],
            "maximumMs": startup_runs[-1],
            "profilerEvidencePassed": profiler["passed"],
        }

    for scenario_name in REQUIRED_FRAME_SCENARIOS:
        # Each required frame journey must use the configured repetition floor.
        benchmark = indexed[scenario_name]
        repeat_iterations = integer_value(benchmark.get("repeatIterations"), f"macro.{scenario_name}.repeatIterations")
        if repeat_iterations < MINIMUM_MACRO_ITERATIONS:
            raise ValueError(f"macro.{scenario_name} has only {repeat_iterations} repetitions.")
        # Thermal throttling invalidates comparison even if AndroidX completed the test method.
        thermal_sleep_seconds = integer_value(
            benchmark.get("thermalThrottleSleepSeconds"),
            f"macro.{scenario_name}.thermalThrottleSleepSeconds",
        )
        thermal_passed = thermal_passed and thermal_sleep_seconds == 0
        # Frame count links sampled frame arrays back to all ten journey repetitions.
        metrics = object_value(benchmark, "metrics", f"macro.{scenario_name}")
        frame_count_metric = object_value(metrics, "frameCount", f"macro.{scenario_name}.metrics")
        frame_counts = numeric_runs(frame_count_metric, f"macro.{scenario_name}.frameCount")
        if len(frame_counts) != repeat_iterations:
            raise ValueError(f"macro.{scenario_name} frameCount run count does not match repetitions.")
        # AndroidX separates UI+RenderThread CPU production time from deadline-relative overrun.
        sampled_metrics = object_value(benchmark, "sampledMetrics", f"macro.{scenario_name}")
        cpu_metric = object_value(sampled_metrics, "frameDurationCpuMs", f"macro.{scenario_name}.sampledMetrics")
        overrun_metric = object_value(sampled_metrics, "frameOverrunMs", f"macro.{scenario_name}.sampledMetrics")
        cpu_samples = sampled_runs(cpu_metric, f"macro.{scenario_name}.frameDurationCpuMs", repeat_iterations)
        overrun_samples = sampled_runs(overrun_metric, f"macro.{scenario_name}.frameOverrunMs", repeat_iterations)
        expected_frame_count = int(round(sum(frame_counts)))
        if len(cpu_samples) != expected_frame_count or len(overrun_samples) != expected_frame_count:
            raise ValueError(
                f"macro.{scenario_name} sampled frame count does not match frameCount "
                f"({len(cpu_samples)}/{len(overrun_samples)} != {expected_frame_count}).",
            )
        # AndroidX's published percentiles remain the authoritative distribution summaries.
        cpu_p95_ms = finite_number(cpu_metric.get("P95"), f"macro.{scenario_name}.frameDurationCpuMs.P95")
        cpu_p99_ms = finite_number(cpu_metric.get("P99"), f"macro.{scenario_name}.frameDurationCpuMs.P99")
        overrun_p95_ms = finite_number(overrun_metric.get("P95"), f"macro.{scenario_name}.frameOverrunMs.P95")
        overrun_p99_ms = finite_number(overrun_metric.get("P99"), f"macro.{scenario_name}.frameOverrunMs.P99")
        # Official semantics define every strictly positive overrun as a dropped/janky frame.
        jank_frames = sum(1 for sample in overrun_samples if sample > 0.0)
        jank_percent = jank_frames * 100.0 / len(overrun_samples)
        p95_passed = cpu_p95_ms <= p95_limit_ms
        p99_passed = cpu_p99_ms <= frame_budget_ms
        # The Goal's explicit jank ceiling applies to representative 60 Hz measurements.
        sixty_hz_gate_applies = abs(refresh_rate_hz - 60.0) <= SIXTY_HZ_TOLERANCE
        jank_passed = not sixty_hz_gate_applies or jank_percent < SIXTY_HZ_JANK_LIMIT_PERCENT
        # Every Macrobenchmark repetition must retain one non-empty Perfetto trace.
        profiler = profiler_evidence(benchmark, trace_root, {"PerfettoTrace": repeat_iterations})
        evidence_passed = evidence_passed and bool(profiler["passed"])
        trace_manifest.extend(profiler["files"])
        frame_results[scenario_name] = {
            "iterations": repeat_iterations,
            "frameCount": expected_frame_count,
            "thermalThrottleSleepSeconds": thermal_sleep_seconds,
            "cpuFrameDurationP95Ms": cpu_p95_ms,
            "cpuFrameDurationP99Ms": cpu_p99_ms,
            "frameOverrunP95Ms": overrun_p95_ms,
            "frameOverrunP99Ms": overrun_p99_ms,
            "jankFrames": jank_frames,
            "jankPercent": jank_percent,
            "p95BudgetLimitMs": p95_limit_ms,
            "p99DeadlineLimitMs": frame_budget_ms,
            "p95BudgetPassed": p95_passed,
            "p99DeadlinePassed": p99_passed,
            "sixtyHzJankGateApplied": sixty_hz_gate_applies,
            "jankLimitPercent": SIXTY_HZ_JANK_LIMIT_PERCENT if sixty_hz_gate_applies else None,
            "jankPassed": jank_passed,
            "absolutePassed": p95_passed and p99_passed and jank_passed,
            "profilerEvidencePassed": profiler["passed"],
        }

    # The absolute frame gate requires every critical journey to satisfy every fixed threshold.
    absolute_passed = all(bool(result["absolutePassed"]) for result in frame_results.values())
    return {
        "frameBudgetMs": frame_budget_ms,
        "p95BudgetFraction": P95_FRAME_BUDGET_FRACTION,
        "startup": startup_results,
        "frames": frame_results,
        "absolutePassed": absolute_passed,
        "thermalPassed": thermal_passed,
        "profilerEvidencePassed": evidence_passed,
        "traceEvidence": {
            "count": len(trace_manifest),
            "aggregateSha256": aggregate_manifest_hash(trace_manifest),
            "files": sorted(trace_manifest, key=lambda entry: str(entry["filename"])),
        },
    }


def analyze_micro(
    raw_report: Mapping[str, Any],
    trace_root: Path,
    required_name_prefix: str = "",
) -> dict[str, Any]:
    """Analyze all required device CPU/allocation microbenchmarks and profiler evidence."""

    # Exact method coverage prevents a missing hot path from being hidden by aggregate success.
    indexed = indexed_benchmarks(
        raw_report,
        set(REQUIRED_MICRO_SCENARIOS),
        "micro",
        required_name_prefix=required_name_prefix,
    )
    # Each hot path exposes timing, allocation, warmup, repetition, and trace evidence.
    scenario_results: dict[str, Any] = {}
    # All profiler entries are aggregated into one run-level evidence manifest.
    trace_manifest: list[dict[str, Any]] = []
    # AndroidX 官方 ANR 保护跳过与真实 Method Trace 文件分开计数，不能伪装成 trace。
    method_trace_skip_manifest: list[dict[str, Any]] = []
    captured_method_trace_count = 0
    evidence_passed = True
    thermal_passed = True
    for scenario_name in REQUIRED_MICRO_SCENARIOS:
        # AndroidX chooses repetitions dynamically after warmup for Microbenchmarks.
        benchmark = indexed[scenario_name]
        # 精确测试类约束防止另一个同名空方法冒充真实最坏场景。
        actual_class_name = string_value(
            benchmark,
            "className",
            f"micro.{scenario_name}",
        )
        expected_class_name = MICRO_SCENARIO_CLASS_NAMES[scenario_name]
        if actual_class_name != expected_class_name:
            raise ValueError(
                f"micro.{scenario_name}.className={actual_class_name}，"
                f"要求 {expected_class_name}",
            )
        repeat_iterations = integer_value(benchmark.get("repeatIterations"), f"micro.{scenario_name}.repeatIterations")
        warmup_iterations = integer_value(benchmark.get("warmupIterations"), f"micro.{scenario_name}.warmupIterations")
        if repeat_iterations <= 0 or warmup_iterations <= 0:
            raise ValueError(f"micro.{scenario_name} must contain positive warmup and repeat iterations.")
        # Thermal sleep is preserved as a failure because throttled samples are not comparable.
        thermal_sleep_seconds = integer_value(
            benchmark.get("thermalThrottleSleepSeconds"),
            f"micro.{scenario_name}.thermalThrottleSleepSeconds",
        )
        thermal_passed = thermal_passed and thermal_sleep_seconds == 0
        # Timing and allocation are both required for every covered CPU hot path.
        metrics = object_value(benchmark, "metrics", f"micro.{scenario_name}")
        time_metric = object_value(metrics, "timeNs", f"micro.{scenario_name}.metrics")
        allocation_metric = object_value(metrics, "allocationCount", f"micro.{scenario_name}.metrics")
        time_runs = sorted(numeric_runs(time_metric, f"micro.{scenario_name}.timeNs"))
        allocation_runs = sorted(numeric_runs(allocation_metric, f"micro.{scenario_name}.allocationCount"))
        # Official profiler artifacts retain both system scheduling and Java method evidence.
        profiler = profiler_evidence(benchmark, trace_root, {"PerfettoTrace": 1, "MethodTrace": 1})
        # 只有“Perfetto 完整、Method Trace 描述符完全缺席”才允许核验 AndroidX ANR 安全跳过。
        observed_profiler_counts = profiler["observedTypeCounts"]
        profiler_files_present = all(bool(entry["present"]) for entry in profiler["files"])
        method_trace_captured = bool(profiler["passed"])
        method_trace_skip = None
        if method_trace_captured:
            captured_method_trace_count += 1
        elif (
            profiler_files_present
            and observed_profiler_counts == {"PerfettoTrace": 1}
        ):
            method_trace_skip = method_trace_anr_skip_evidence(benchmark, trace_root)
            if bool(method_trace_skip["passed"]):
                method_trace_skip_manifest.append(method_trace_skip)
        effective_profiler_passed = method_trace_captured or bool(
            method_trace_skip is not None and method_trace_skip["passed"],
        )
        evidence_passed = evidence_passed and effective_profiler_passed
        trace_manifest.extend(profiler["files"])
        scenario_results[scenario_name] = {
            "warmupIterations": warmup_iterations,
            "repeatIterations": repeat_iterations,
            "thermalThrottleSleepSeconds": thermal_sleep_seconds,
            "timeMedianNs": finite_number(time_metric.get("median"), f"micro.{scenario_name}.timeNs.median"),
            "timeP95Ns": percentile(time_runs, 0.95),
            "allocationMedian": finite_number(
                allocation_metric.get("median"),
                f"micro.{scenario_name}.allocationCount.median",
            ),
            "profilerEvidencePassed": effective_profiler_passed,
            "methodTraceStatus": (
                "captured"
                if method_trace_captured
                else "skippedAnrRisk"
                if method_trace_skip is not None and bool(method_trace_skip["passed"])
                else "missing"
            ),
            "methodTraceSkipEvidence": method_trace_skip,
        }
    return {
        "scenarios": scenario_results,
        "thermalPassed": thermal_passed,
        "profilerEvidencePassed": evidence_passed,
        "traceEvidence": {
            "count": len(trace_manifest),
            "aggregateSha256": aggregate_manifest_hash(trace_manifest),
            "files": sorted(trace_manifest, key=lambda entry: str(entry["filename"])),
        },
        "methodTraceEvidence": {
            "capturedCount": captured_method_trace_count,
            "skippedAnrRiskCount": len(method_trace_skip_manifest),
            "skipEvidenceAggregateSha256": aggregate_manifest_hash(
                {
                    "filename": entry["filename"],
                    "type": "MethodTraceAnrSafetyMessage",
                    "present": True,
                    "bytes": entry["artifact"]["bytes"],
                    "sha256": entry["artifact"]["sha256"],
                }
                for entry in method_trace_skip_manifest
            ),
            "skipEvidence": sorted(
                method_trace_skip_manifest,
                key=lambda entry: str(entry["filename"]),
            ),
        },
    }


def reference_metrics(report: Mapping[str, Any]) -> dict[str, Any]:
    """Extract only lower-is-better key metrics stored in an approved trend baseline."""

    # The report sections are validated by the analysis path before this projection is built.
    macro = object_value(report, "macro", "report")
    micro = object_value(report, "micro", "report")
    startup = object_value(macro, "startup", "report.macro")
    frames = object_value(macro, "frames", "report.macro")
    micro_scenarios = object_value(micro, "scenarios", "report.micro")
    return {
        "macroStartup": {
            scenario_name: {
                "medianMs": finite_number(
                    object_value(startup, scenario_name, "report.macro.startup").get("medianMs"),
                    f"report.macro.startup.{scenario_name}.medianMs",
                ),
            }
            for scenario_name in REQUIRED_STARTUP_SCENARIOS
        },
        "macroFrames": {
            scenario_name: {
                "cpuFrameDurationP95Ms": finite_number(
                    object_value(frames, scenario_name, "report.macro.frames").get("cpuFrameDurationP95Ms"),
                    f"report.macro.frames.{scenario_name}.cpuFrameDurationP95Ms",
                ),
                "cpuFrameDurationP99Ms": finite_number(
                    object_value(frames, scenario_name, "report.macro.frames").get("cpuFrameDurationP99Ms"),
                    f"report.macro.frames.{scenario_name}.cpuFrameDurationP99Ms",
                ),
                "jankPercent": finite_number(
                    object_value(frames, scenario_name, "report.macro.frames").get("jankPercent"),
                    f"report.macro.frames.{scenario_name}.jankPercent",
                ),
            }
            for scenario_name in REQUIRED_FRAME_SCENARIOS
        },
        "micro": {
            scenario_name: {
                "timeMedianNs": finite_number(
                    object_value(micro_scenarios, scenario_name, "report.micro.scenarios").get("timeMedianNs"),
                    f"report.micro.scenarios.{scenario_name}.timeMedianNs",
                ),
                "allocationMedian": finite_number(
                    object_value(micro_scenarios, scenario_name, "report.micro.scenarios").get("allocationMedian"),
                    f"report.micro.scenarios.{scenario_name}.allocationMedian",
                ),
            }
            for scenario_name in REQUIRED_MICRO_SCENARIOS
        },
    }


def configuration_identity(report: Mapping[str, Any]) -> dict[str, Any]:
    """Select device and harness fields that must match an approved comparison baseline."""

    # Exact OS/device identity avoids comparing measurements from unlike schedulers or hardware.
    identity = object_value(report, "device", "report")
    # Harness hashes and build modes distinguish unlike benchmark configurations while code changes remain comparable.
    configuration = object_value(report, "configuration", "report")
    return {
        "device": {
            field_name: identity[field_name]
            for field_name in ("brand", "model", "device", "fingerprint", "buildId", "apiLevel", "refreshRateHz")
        },
        "configuration": {
            field_name: configuration[field_name]
            for field_name in (
                "macroBuildVariant",
                "targetBuildType",
                "microBuildVariant",
                "compilationPolicy",
                "reportedMacroCompilationMode",
                "reportedMicroCompilationMode",
                "macroSourceSha256",
            )
        },
    }


def regression_check(metric_name: str, measured: float, baseline: float) -> dict[str, Any]:
    """Compare one lower-is-better metric against the fixed ten-percent regression ceiling."""

    if baseline < 0.0 or measured < 0.0:
        raise ValueError(f"Regression metric must be non-negative: {metric_name}")
    # A zero baseline permits only another zero; no arbitrary epsilon weakens the approved value.
    allowed_maximum = baseline * (1.0 + MAXIMUM_REGRESSION_PERCENT / 100.0)
    passed = measured <= allowed_maximum
    # Infinite regression makes a non-zero result against a zero baseline explicit and machine-readable.
    regression_percent = 0.0 if measured == baseline else (math.inf if baseline == 0.0 else (measured / baseline - 1.0) * 100.0)
    return {
        "metric": metric_name,
        "measured": measured,
        "baseline": baseline,
        "allowedMaximum": allowed_maximum,
        "regressionPercent": regression_percent,
        "passed": passed,
    }


def compare_baseline(report: Mapping[str, Any], baseline_path: Path | None) -> dict[str, Any]:
    """Require an explicitly approved exact-configuration baseline and compare every key metric."""

    # 模拟器演练永远不执行可通过的发布趋势比较，即使候选文件被手工改成 approved。
    if report.get("representativePerformanceEvidence", True) is not True:
        return {
            "status": "nonRepresentative",
            "passed": False,
            "reason": "Emulator rehearsal evidence cannot satisfy the release trend gate.",
            "checks": [],
        }
    if baseline_path is None:
        return {"status": "missing", "passed": False, "reason": "No approved baseline was supplied.", "checks": []}
    # The baseline is loaded only from the caller-provided path; no latest-file discovery is allowed.
    baseline = load_json_object(baseline_path, "device benchmark baseline")
    if integer_value(baseline.get("schemaVersion"), "baseline.schemaVersion") != SCHEMA_VERSION:
        raise ValueError(f"Unsupported device benchmark baseline schema: {baseline_path}")
    if baseline.get("kind") != "pixel-device-benchmark-baseline":
        raise ValueError(f"Unexpected device benchmark baseline kind: {baseline_path}")
    if baseline.get("representativePerformanceEvidence", True) is not True:
        return {
            "status": "nonRepresentativeBaseline",
            "passed": False,
            "reason": "Approved trend baselines must come from representative physical evidence.",
            "baselineSha256": sha256_file(baseline_path),
            "checks": [],
        }
    # Approval metadata separates an automatically generated candidate from a maintainer decision.
    approval = object_value(baseline, "approval", "baseline")
    approval_status = approval.get("status")
    if approval_status != "approved":
        return {
            "status": "unapproved",
            "passed": False,
            "reason": f"Baseline approval status is {approval_status!r}, not 'approved'.",
            "baselineSha256": sha256_file(baseline_path),
            "checks": [],
        }
    for approval_field in ("approvedBy", "approvedAtUtc", "technicalReason"):
        string_value(approval, approval_field, "baseline.approval")
    # Same-device and same-harness identity is mandatory before any numeric comparison.
    expected_identity = object_value(baseline, "identity", "baseline")
    measured_identity = configuration_identity(report)
    if expected_identity != measured_identity:
        return {
            "status": "identityMismatch",
            "passed": False,
            "reason": "Measured device or benchmark configuration differs from the approved baseline.",
            "baselineSha256": sha256_file(baseline_path),
            "expectedIdentity": expected_identity,
            "measuredIdentity": measured_identity,
            "checks": [],
        }
    # Both baseline and measured metrics use the same explicit lower-is-better projection.
    baseline_metrics = object_value(baseline, "referenceMetrics", "baseline")
    measured_metrics = reference_metrics(report)
    # Every selected metric emits an independent comparison for audit and failure diagnosis.
    checks: list[dict[str, Any]] = []
    for section_name, scenario_names, metric_names in (
        ("macroStartup", REQUIRED_STARTUP_SCENARIOS, ("medianMs",)),
        (
            "macroFrames",
            REQUIRED_FRAME_SCENARIOS,
            ("cpuFrameDurationP95Ms", "cpuFrameDurationP99Ms", "jankPercent"),
        ),
        ("micro", REQUIRED_MICRO_SCENARIOS, ("timeMedianNs", "allocationMedian")),
    ):
        baseline_section = object_value(baseline_metrics, section_name, "baseline.referenceMetrics")
        measured_section = object_value(measured_metrics, section_name, "report.referenceMetrics")
        for scenario_name in scenario_names:
            baseline_scenario = object_value(baseline_section, scenario_name, f"baseline.referenceMetrics.{section_name}")
            measured_scenario = object_value(measured_section, scenario_name, f"report.referenceMetrics.{section_name}")
            for metric_name in metric_names:
                # Fully qualified names make regressions searchable in CI and acceptance artifacts.
                qualified_name = f"{section_name}.{scenario_name}.{metric_name}"
                checks.append(
                    regression_check(
                        qualified_name,
                        finite_number(measured_scenario.get(metric_name), f"report.{qualified_name}"),
                        finite_number(baseline_scenario.get(metric_name), f"baseline.{qualified_name}"),
                    ),
                )
    # The comparison passes only when all key metrics stay within the fixed ten-percent ceiling.
    comparison_passed = all(bool(check["passed"]) for check in checks)
    return {
        "status": "passed" if comparison_passed else "regressed",
        "passed": comparison_passed,
        "maximumRegressionPercent": MAXIMUM_REGRESSION_PERCENT,
        "baselineSha256": sha256_file(baseline_path),
        "checks": checks,
    }


def verify_baseline_profile_report(report_path: Path, target_apk: Path) -> dict[str, Any]:
    """Require a passing packaging report that names and hashes the exact measured target APK."""

    # The packaging verifier independently inspects the AAR and compiled APK dexopt assets.
    packaging_report = load_json_object(report_path, "Baseline Profile packaging report")
    if packaging_report.get("status") != "passed":
        raise ValueError(f"Baseline Profile packaging report did not pass: {report_path}")
    # The exact measured target APK must be one of the verifier's inspected consumer artifacts.
    consumer_apks = list_value(packaging_report, "consumerApks", "baselineProfileReport")
    target_sha256 = sha256_file(target_apk)
    matching_entries = [
        entry
        for entry in consumer_apks
        if isinstance(entry, dict)
        and Path(str(entry.get("path", ""))).resolve() == target_apk.resolve()
        and entry.get("sha256") == target_sha256
    ]
    if len(matching_entries) != 1:
        raise ValueError("Baseline Profile report does not hash the exact measured target APK.")
    return {
        "status": "passed",
        "report": artifact_record(report_path),
        "targetApkSha256": target_sha256,
    }


def build_report(options: argparse.Namespace) -> dict[str, Any]:
    """Build one complete device gate report from exact raw, profiler, source, and APK evidence."""

    # The compilation source is read before benchmark data so a weakened policy cannot be overlooked.
    macro_source_text = options.macro_source.read_text(encoding="utf-8")
    if REQUIRED_COMPILATION_SOURCE_TOKEN not in macro_source_text:
        raise ValueError("Macrobenchmark source does not require the packaged Baseline Profile.")
    # Both raw reports are independently validated and hashed as immutable source evidence.
    macro_raw = load_json_object(options.macro_json, "Macrobenchmark JSON")
    micro_raw = load_json_object(options.micro_json, "Microbenchmark JSON")
    macro_context = object_value(macro_raw, "context", "macro")
    micro_context = object_value(micro_raw, "context", "micro")
    # The Macro and Micro runs must originate from the exact same physical device/OS identity.
    macro_identity = device_identity(macro_context, "macro.context")
    micro_identity = device_identity(micro_context, "micro.context")
    if macro_identity != micro_identity:
        raise ValueError("Macrobenchmark and Microbenchmark device identities do not match.")
    # 显式模式与原始设备身份必须互相印证，默认实体门禁不会接受模拟器产物。
    validate_evidence_mode(options.evidence_mode, macro_identity)
    # Device analyses resolve profiler outputs beside their corresponding raw JSON files.
    macro = analyze_macro(macro_raw, options.macro_json.parent, options.refresh_rate_hz)
    # AndroidX 仅在模拟器 Microbenchmark 名称上添加前缀，演练模式校验后再规范化。
    micro_name_prefix = (
        EMULATOR_BENCHMARK_PREFIX
        if options.evidence_mode == EMULATOR_REHEARSAL_MODE
        else ""
    )
    micro = analyze_micro(
        micro_raw,
        options.micro_json.parent,
        required_name_prefix=micro_name_prefix,
    )
    # The Baseline Profile report must cover the exact target APK cited by this measurement.
    profile_evidence = verify_baseline_profile_report(options.baseline_profile_report, options.target_apk)
    # Artifact records bind the report to the exact installed test and target binaries.
    artifacts = {
        "macroJson": artifact_record(options.macro_json),
        "microJson": artifact_record(options.micro_json),
        "macroSource": artifact_record(options.macro_source),
        "macroApk": artifact_record(options.macro_apk),
        "targetApk": artifact_record(options.target_apk),
        "microApk": artifact_record(options.micro_apk),
        "baselineProfile": profile_evidence,
    }
    # Raw AndroidX compilation labels are retained because they differ between macro and micro runners.
    configuration = {
        "macroBuildVariant": options.macro_build_variant,
        "targetBuildType": options.target_build_type,
        "microBuildVariant": options.micro_build_variant,
        "compilationPolicy": options.compilation_policy,
        "reportedMacroCompilationMode": string_value(macro_context, "compilationMode", "macro.context"),
        "reportedMicroCompilationMode": string_value(micro_context, "compilationMode", "micro.context"),
        "macroSourceSha256": artifacts["macroSource"]["sha256"],
    }
    # Refresh rate is supplied from the active display mode captured immediately around the run.
    device = {**macro_identity, "refreshRateHz": options.refresh_rate_hz}
    # Structural evidence includes traces, zero thermal sleeps, artifacts, and profile packaging.
    evidence_passed = (
        bool(macro["profilerEvidencePassed"])
        and bool(micro["profilerEvidencePassed"])
        and bool(macro["thermalPassed"])
        and bool(micro["thermalPassed"])
    )
    # Baseline comparison is evaluated after the report contains its full identity and metrics.
    report: dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "kind": "pixel-device-benchmark-gate",
        "measurementId": options.measurement_id,
        "evidenceMode": options.evidence_mode,
        "representativePerformanceEvidence": options.evidence_mode == PHYSICAL_EVIDENCE_MODE,
        "device": device,
        "configuration": configuration,
        "artifacts": artifacts,
        "metricSemantics": {
            "cpuFrameDuration": "AndroidX frameDurationCpuMs: UI thread plus RenderThread CPU production time.",
            "frameOverrun": "AndroidX frameOverrunMs: positive values are deadline misses and visible jank.",
            "startup": "AndroidX timeToInitialDisplayMs: launch intent through first displayed frame.",
        },
        "thresholds": {
            "p95FrameBudgetFraction": P95_FRAME_BUDGET_FRACTION,
            "p99FrameDeadlineFraction": 1.0,
            "sixtyHzJankLimitPercentExclusive": SIXTY_HZ_JANK_LIMIT_PERCENT,
            "maximumRegressionPercent": MAXIMUM_REGRESSION_PERCENT,
        },
        "macro": macro,
        "micro": micro,
        "gates": {
            "evidencePassed": evidence_passed,
            "absoluteFramePassed": bool(macro["absolutePassed"]),
        },
    }
    # Trend status remains failed when no approved exact-configuration baseline is supplied.
    baseline_comparison = compare_baseline(report, options.baseline)
    report["baselineComparison"] = baseline_comparison
    report["gates"]["trendPassed"] = bool(baseline_comparison["passed"])
    # Overall status is the conjunction of evidence, absolute, and trend quality gates.
    overall_passed = evidence_passed and bool(macro["absolutePassed"]) and bool(baseline_comparison["passed"])
    report["overallPassed"] = overall_passed
    report["status"] = "passed" if overall_passed else "failed"
    return report


def build_candidate_baseline(report: Mapping[str, Any]) -> dict[str, Any]:
    """Create an explicitly unapproved baseline candidate without weakening any current gate."""

    # Candidates carry exact provenance but require a separate reviewed approval edit before use.
    artifacts = object_value(report, "artifacts", "report")
    macro = object_value(report, "macro", "report")
    micro = object_value(report, "micro", "report")
    return {
        "schemaVersion": SCHEMA_VERSION,
        "kind": "pixel-device-benchmark-baseline",
        "evidenceMode": report.get("evidenceMode", PHYSICAL_EVIDENCE_MODE),
        "representativePerformanceEvidence": bool(report.get("representativePerformanceEvidence", True)),
        "approval": {
            "status": "candidate",
            "approvedBy": None,
            "approvedAtUtc": None,
            "technicalReason": "Absolute and trace evidence must be reviewed before approval.",
        },
        "measurementId": report["measurementId"],
        "absoluteGatePassed": bool(macro["absolutePassed"]),
        "identity": configuration_identity(report),
        "sourceEvidence": {
            "macroJsonSha256": object_value(artifacts, "macroJson", "report.artifacts")["sha256"],
            "microJsonSha256": object_value(artifacts, "microJson", "report.artifacts")["sha256"],
            "macroTraceAggregateSha256": object_value(macro, "traceEvidence", "report.macro")["aggregateSha256"],
            "microTraceAggregateSha256": object_value(micro, "traceEvidence", "report.micro")["aggregateSha256"],
        },
        "referenceMetrics": reference_metrics(report),
    }


def write_json(path: Path, value: Mapping[str, Any]) -> None:
    """Write stable, reviewable JSON while preserving non-finite regression values as strings."""

    # JSON does not portably support infinity, so failed zero-baseline regressions use an explicit string.
    def normalize_non_finite(item: Any) -> Any:
        """Recursively replace non-finite floats with readable strings for strict JSON consumers."""

        if isinstance(item, float) and not math.isfinite(item):
            return "Infinity" if item > 0 else "-Infinity"
        if isinstance(item, dict):
            return {key: normalize_non_finite(child) for key, child in item.items()}
        if isinstance(item, list):
            return [normalize_non_finite(child) for child in item]
        return item

    # Parent creation supports clean build/report directories without implicit repository state.
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(normalize_non_finite(dict(value)), indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main(arguments: Sequence[str] | None = None) -> int:
    """Write device evidence and return non-zero for invalid input or any active gate failure."""

    # Explicit arguments make the entry point deterministic in unit and shell propagation tests.
    options = parse_arguments(arguments if arguments is not None else sys.argv[1:])
    try:
        # The complete report is written even when a quality threshold legitimately fails.
        report = build_report(options)
        write_json(options.output, report)
        if options.candidate_baseline_output is not None:
            write_json(options.candidate_baseline_output, build_candidate_baseline(report))
    except Exception as error:
        print(f"Device benchmark validation failed: {error}", file=sys.stderr)
        return 2
    # Report-only is an evidence collection mode and never changes the report's failed status.
    if not report["overallPassed"]:
        print(f"Device benchmark gates FAILED; report: {options.output}", file=sys.stderr)
        return 0 if options.report_only else 1
    print(f"Device benchmark gates passed; report: {options.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
