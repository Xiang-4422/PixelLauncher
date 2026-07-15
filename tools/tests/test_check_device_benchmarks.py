from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from typing import Any, Sequence

from tools import check_device_benchmarks


class DeviceBenchmarkGateTest(unittest.TestCase):
    """Locks M6-2 coverage, evidence, absolute thresholds, and same-device trend behavior."""

    def write_json(self, path: Path, value: dict[str, Any]) -> None:
        """Write one temporary JSON fixture with deterministic formatting."""

        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    def benchmark_context(
        self,
        *,
        fingerprint: str = "fixture/device:1/build:user/release",
        emulator: bool = False,
    ) -> dict[str, Any]:
        """Build one stable AndroidX physical-device context shared by Macro and Micro fixtures."""

        # 模拟器夹具同时命中 model、device 与 fingerprint 三个独立身份信号。
        model = "sdk_gphone16k_arm64" if emulator else "Fixture Phone"
        device = "emu64a16k" if emulator else "fixture"
        resolved_fingerprint = (
            "google/sdk_gphone16k_arm64/emu64a16k:17/BUILD1:user/dev-keys"
            if emulator
            else fingerprint
        )
        return {
            "build": {
                "brand": "FixtureBrand",
                "device": device,
                "fingerprint": resolved_fingerprint,
                "id": "BUILD1",
                "model": model,
                "version": {"sdk": 31},
            },
            "compilationMode": "run-from-apk",
        }

    def profiler_outputs(
        self,
        root: Path,
        scenario_name: str,
        profiler_types: Sequence[str],
    ) -> list[dict[str, str]]:
        """Create non-empty profiler files and return matching AndroidX descriptors."""

        # Every profiler type receives a stable extension and unique fixture filename.
        outputs: list[dict[str, str]] = []
        for output_index, profiler_type in enumerate(profiler_types):
            extension = "perfetto-trace" if profiler_type == "PerfettoTrace" else "trace"
            filename = f"{scenario_name}-{output_index}.{extension}"
            (root / filename).write_bytes(f"{scenario_name}:{profiler_type}:{output_index}".encode("utf-8"))
            outputs.append({"type": profiler_type, "label": profiler_type, "filename": filename})
        return outputs

    def prepare_fixture(
        self,
        root: Path,
        *,
        cpu_p95_ms: float = 5.0,
        cpu_p99_ms: float = 6.0,
        overrun_samples: Sequence[float] = (-5.0, -1.0),
        thermal_sleep_seconds: int = 0,
        emulator: bool = False,
    ) -> dict[str, Path]:
        """Create complete passing-or-parameterized Macro, Micro, APK, source, and profile evidence."""

        # Separate evidence directories mirror AndroidX additional-test-output layout.
        macro_root = root / "macro"
        micro_root = root / "micro"
        macro_root.mkdir(parents=True)
        micro_root.mkdir(parents=True)
        # Ten startup repetitions and traces satisfy the fixed M6-2 floor.
        macro_benchmarks: list[dict[str, Any]] = []
        for scenario_name in check_device_benchmarks.REQUIRED_STARTUP_SCENARIOS:
            startup_runs = [float(10 + run_index) for run_index in range(10)]
            macro_benchmarks.append(
                {
                    "name": scenario_name,
                    "className": "com.purride.pixelbenchmark.PixelMacrobenchmark",
                    "metrics": {
                        "timeToInitialDisplayMs": {
                            "minimum": min(startup_runs),
                            "maximum": max(startup_runs),
                            "median": 14.5,
                            "runs": startup_runs,
                        },
                    },
                    "sampledMetrics": {},
                    "warmupIterations": 0,
                    "repeatIterations": 10,
                    "thermalThrottleSleepSeconds": thermal_sleep_seconds,
                    "profilerOutputs": self.profiler_outputs(
                        macro_root,
                        scenario_name,
                        ["PerfettoTrace"] * 10,
                    ),
                },
            )
        # Each frame scenario emits two frames per repetition and one trace per repetition.
        for scenario_name in check_device_benchmarks.REQUIRED_FRAME_SCENARIOS:
            cpu_iterations = [[4.0, 5.0] for _ in range(10)]
            overrun_iterations = [list(overrun_samples) for _ in range(10)]
            macro_benchmarks.append(
                {
                    "name": scenario_name,
                    "className": "com.purride.pixelbenchmark.PixelMacrobenchmark",
                    "metrics": {
                        "frameCount": {
                            "minimum": 2.0,
                            "maximum": 2.0,
                            "median": 2.0,
                            "runs": [2.0] * 10,
                        },
                    },
                    "sampledMetrics": {
                        "frameDurationCpuMs": {
                            "P50": 4.5,
                            "P90": cpu_p95_ms,
                            "P95": cpu_p95_ms,
                            "P99": cpu_p99_ms,
                            "runs": cpu_iterations,
                        },
                        "frameOverrunMs": {
                            "P50": min(overrun_samples),
                            "P90": max(overrun_samples),
                            "P95": max(overrun_samples),
                            "P99": max(overrun_samples),
                            "runs": overrun_iterations,
                        },
                    },
                    "warmupIterations": 0,
                    "repeatIterations": 10,
                    "thermalThrottleSleepSeconds": thermal_sleep_seconds,
                    "profilerOutputs": self.profiler_outputs(
                        macro_root,
                        scenario_name,
                        ["PerfettoTrace"] * 10,
                    ),
                },
            )
        # The raw Macrobenchmark report carries one physical-device context and exact method set.
        macro_json = macro_root / "macro.json"
        self.write_json(
            macro_json,
            {"context": self.benchmark_context(emulator=emulator), "benchmarks": macro_benchmarks},
        )

        # Each micro hot path records timing, allocations, warmup, repetitions, and two profiler families.
        micro_benchmarks: list[dict[str, Any]] = []
        for scenario_name in check_device_benchmarks.REQUIRED_MICRO_SCENARIOS:
            micro_benchmarks.append(
                {
                    "name": (
                        f"{check_device_benchmarks.EMULATOR_BENCHMARK_PREFIX}{scenario_name}"
                        if emulator
                        else scenario_name
                    ),
                    "className": check_device_benchmarks.MICRO_SCENARIO_CLASS_NAMES[scenario_name],
                    "metrics": {
                        "timeNs": {
                            "minimum": 100.0,
                            "maximum": 110.0,
                            "median": 105.0,
                            "runs": [100.0, 105.0, 110.0],
                        },
                        "allocationCount": {
                            "minimum": 1.0,
                            "maximum": 1.0,
                            "median": 1.0,
                            "runs": [1.0, 1.0, 1.0],
                        },
                    },
                    "sampledMetrics": {},
                    "warmupIterations": 100,
                    "repeatIterations": 50,
                    "thermalThrottleSleepSeconds": thermal_sleep_seconds,
                    "profilerOutputs": self.profiler_outputs(
                        micro_root,
                        scenario_name,
                        ["PerfettoTrace", "MethodTrace"],
                    ),
                },
            )
        # Micro uses the same device identity while retaining AndroidX's speed compilation label.
        micro_context = self.benchmark_context(emulator=emulator)
        micro_context["compilationMode"] = "speed"
        micro_json = micro_root / "micro.json"
        self.write_json(micro_json, {"context": micro_context, "benchmarks": micro_benchmarks})

        # Minimal non-empty artifacts make hash and provenance checks deterministic.
        macro_source = root / "PixelMacrobenchmark.kt"
        macro_source.write_text(
            "val mode = CompilationMode.Partial(BaselineProfileMode.Require)\n",
            encoding="utf-8",
        )
        macro_apk = root / "macro.apk"
        target_apk = root / "target.apk"
        micro_apk = root / "micro.apk"
        macro_apk.write_bytes(b"macro-apk")
        target_apk.write_bytes(b"target-apk-with-baseline-profile")
        micro_apk.write_bytes(b"micro-apk")

        # The packaging report must name and hash the exact measured target APK.
        profile_report = root / "baseline-profile-packaging.json"
        self.write_json(
            profile_report,
            {
                "schemaVersion": 1,
                "status": "passed",
                "consumerApks": [
                    {
                        "path": target_apk.as_posix(),
                        "sha256": check_device_benchmarks.sha256_file(target_apk),
                    },
                ],
            },
        )
        # Returned paths are the complete CLI fixture contract.
        return {
            "macroJson": macro_json,
            "microJson": micro_json,
            "macroSource": macro_source,
            "macroApk": macro_apk,
            "targetApk": target_apk,
            "microApk": micro_apk,
            "profileReport": profile_report,
            "report": root / "device-gate.json",
            "candidate": root / "candidate-baseline.json",
        }

    def command_arguments(
        self,
        fixture: dict[str, Path],
        *,
        baseline: Path | None = None,
        report_only: bool = False,
        write_candidate: bool = False,
        evidence_mode: str | None = None,
    ) -> list[str]:
        """Build exact CLI arguments for one temporary fixture invocation."""

        # Required paths bind the report to raw JSON, source, APK, and profile packaging evidence.
        arguments = [
            "--macro-json",
            str(fixture["macroJson"]),
            "--micro-json",
            str(fixture["microJson"]),
            "--macro-source",
            str(fixture["macroSource"]),
            "--macro-apk",
            str(fixture["macroApk"]),
            "--target-apk",
            str(fixture["targetApk"]),
            "--micro-apk",
            str(fixture["microApk"]),
            "--baseline-profile-report",
            str(fixture["profileReport"]),
            "--output",
            str(fixture["report"]),
            "--measurement-id",
            "fixture-api31-60hz",
            "--refresh-rate-hz",
            "60",
        ]
        if baseline is not None:
            arguments.extend(["--baseline", str(baseline)])
        if report_only:
            arguments.append("--report-only")
        if write_candidate:
            arguments.extend(["--candidate-baseline-output", str(fixture["candidate"])])
        if evidence_mode is not None:
            arguments.extend(["--evidence-mode", evidence_mode])
        return arguments

    def approve_candidate(self, candidate_path: Path) -> None:
        """Turn one temporary candidate into an explicit reviewed baseline fixture."""

        # Approval fields are mandatory for a trend comparison to execute.
        candidate = json.loads(candidate_path.read_text(encoding="utf-8"))
        candidate["approval"] = {
            "status": "approved",
            "approvedBy": "fixture-maintainer",
            "approvedAtUtc": "2026-07-14T00:00:00Z",
            "technicalReason": "Passing fixture used to verify exact trend semantics.",
        }
        self.write_json(candidate_path, candidate)

    def replace_method_trace_with_androidx_anr_skip(
        self,
        fixture: dict[str, Path],
        scenario_name: str,
        *,
        message_text: str | None = None,
    ) -> None:
        """把一个 Method Trace 夹具替换为 AndroidX 原始 ANR 安全跳过消息。"""

        # 先从原始 JSON 中删除 Method Trace 描述符及对应文件，保留必需的 Perfetto。
        micro = json.loads(fixture["microJson"].read_text(encoding="utf-8"))
        benchmark = next(
            candidate
            for candidate in micro["benchmarks"]
            if candidate["name"] == scenario_name
        )
        method_trace = next(
            output
            for output in benchmark["profilerOutputs"]
            if output["type"] == "MethodTrace"
        )
        (fixture["microJson"].parent / method_trace["filename"]).unlink()
        benchmark["profilerOutputs"] = [
            output
            for output in benchmark["profilerOutputs"]
            if output["type"] != "MethodTrace"
        ]
        self.write_json(fixture["microJson"], micro)
        # 消息文件名与 AndroidX additional-test-output 的类名和方法名契约一致。
        message_path = fixture["microJson"].parent / (
            "additionaltestoutput.benchmark.message_"
            f"{benchmark['className']}.{benchmark['name']}.txt"
        )
        resolved_message = message_text or (
            "Skipping method trace of estimated duration 5.875209 sec to avoid ANR\n\n"
            "To disable this behavior, set instrumentation arg:\n"
            "androidx.benchmark.profiling.skipWhenDurationRisksAnr = false\n"
            "fixture benchmark result\n"
        )
        message_path.write_text(resolved_message, encoding="utf-8")

    def test_approved_same_configuration_baseline_passes_all_gates(self) -> None:
        """完整绝对通过且趋势不变时，全部 39 个关键指标比较必须通过。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # The first report-only invocation emits an unapproved candidate from exact evidence.
            fixture = self.prepare_fixture(Path(temporary_directory))
            first_exit = check_device_benchmarks.main(
                self.command_arguments(fixture, report_only=True, write_candidate=True),
            )
            self.assertEqual(0, first_exit)
            self.approve_candidate(fixture["candidate"])

            # The second invocation compares identical evidence to the approved candidate.
            gate_exit = check_device_benchmarks.main(
                self.command_arguments(fixture, baseline=fixture["candidate"]),
            )
            report = json.loads(fixture["report"].read_text(encoding="utf-8"))
            self.assertEqual(0, gate_exit)
            self.assertTrue(report["overallPassed"])
            self.assertEqual("passed", report["baselineComparison"]["status"])
            self.assertEqual(39, len(report["baselineComparison"]["checks"]))

    def test_absolute_failure_is_written_but_default_gate_fails(self) -> None:
        """Over-budget and janky frame samples remain failed even when report-only collects evidence."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # Positive overruns make every frame janky while CPU p95/p99 exceed fixed 60 Hz limits.
            fixture = self.prepare_fixture(
                Path(temporary_directory),
                cpu_p95_ms=20.0,
                cpu_p99_ms=25.0,
                overrun_samples=(1.0, 3.0),
            )
            report_only_exit = check_device_benchmarks.main(
                self.command_arguments(fixture, report_only=True),
            )
            report = json.loads(fixture["report"].read_text(encoding="utf-8"))
            self.assertEqual(0, report_only_exit)
            self.assertFalse(report["macro"]["absolutePassed"])
            self.assertEqual(100.0, report["macro"]["frames"]["listScroll"]["jankPercent"])

            # Removing report-only restores non-zero process failure without changing the evidence.
            gate_exit = check_device_benchmarks.main(self.command_arguments(fixture))
            self.assertEqual(1, gate_exit)

    def test_more_than_ten_percent_regression_fails_trend_gate(self) -> None:
        """An approved lower baseline rejects a key metric regression above the fixed ten-percent limit."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # A passing candidate provides complete identity and reference metric structure.
            fixture = self.prepare_fixture(Path(temporary_directory))
            check_device_benchmarks.main(
                self.command_arguments(fixture, report_only=True, write_candidate=True),
            )
            self.approve_candidate(fixture["candidate"])
            baseline = json.loads(fixture["candidate"].read_text(encoding="utf-8"))
            baseline["referenceMetrics"]["macroFrames"]["animation"]["cpuFrameDurationP95Ms"] = 4.0
            self.write_json(fixture["candidate"], baseline)

            # Measured 5 ms is 25 percent slower than the approved 4 ms reference.
            gate_exit = check_device_benchmarks.main(
                self.command_arguments(fixture, baseline=fixture["candidate"]),
            )
            report = json.loads(fixture["report"].read_text(encoding="utf-8"))
            self.assertEqual(1, gate_exit)
            self.assertEqual("regressed", report["baselineComparison"]["status"])
            failed_checks = [check for check in report["baselineComparison"]["checks"] if not check["passed"]]
            self.assertEqual(1, len(failed_checks))
            self.assertEqual(25.0, failed_checks[0]["regressionPercent"])

    def test_unapproved_candidate_cannot_satisfy_trend_gate(self) -> None:
        """Automatically generated candidate data cannot impersonate a maintainer-approved baseline."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # Candidate generation deliberately keeps approval status at candidate.
            fixture = self.prepare_fixture(Path(temporary_directory))
            check_device_benchmarks.main(
                self.command_arguments(fixture, report_only=True, write_candidate=True),
            )
            gate_exit = check_device_benchmarks.main(
                self.command_arguments(fixture, baseline=fixture["candidate"]),
            )
            report = json.loads(fixture["report"].read_text(encoding="utf-8"))
            self.assertEqual(1, gate_exit)
            self.assertEqual("unapproved", report["baselineComparison"]["status"])

    def test_missing_trace_is_an_evidence_failure(self) -> None:
        """A missing profiler file stays visible and prevents an otherwise fast run from passing."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # One descriptor remains in raw JSON while its physical Perfetto file is removed.
            fixture = self.prepare_fixture(Path(temporary_directory))
            macro = json.loads(fixture["macroJson"].read_text(encoding="utf-8"))
            missing_filename = macro["benchmarks"][0]["profilerOutputs"][0]["filename"]
            (fixture["macroJson"].parent / missing_filename).unlink()
            report_exit = check_device_benchmarks.main(
                self.command_arguments(fixture, report_only=True),
            )
            report = json.loads(fixture["report"].read_text(encoding="utf-8"))
            self.assertEqual(0, report_exit)
            self.assertFalse(report["gates"]["evidencePassed"])
            self.assertFalse(report["macro"]["profilerEvidencePassed"])

    def test_androidx_method_trace_anr_skip_is_explicit_evidence(self) -> None:
        """AndroidX 的精确 ANR 安全提示可替代一份 Method Trace，但不能伪装成 trace 文件。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # 正式基准仍保留全部 11 份 Perfetto，只让一个 Method Trace 走平台安全分支。
            fixture = self.prepare_fixture(Path(temporary_directory))
            self.replace_method_trace_with_androidx_anr_skip(
                fixture,
                "fullBrightnessNoGapCanvasSubmit",
            )
            report_exit = check_device_benchmarks.main(
                self.command_arguments(fixture, report_only=True),
            )
            report = json.loads(fixture["report"].read_text(encoding="utf-8"))
            self.assertEqual(0, report_exit)
            self.assertTrue(report["gates"]["evidencePassed"])
            self.assertTrue(report["micro"]["profilerEvidencePassed"])
            self.assertEqual(10, report["micro"]["methodTraceEvidence"]["capturedCount"])
            self.assertEqual(1, report["micro"]["methodTraceEvidence"]["skippedAnrRiskCount"])
            self.assertEqual(21, report["micro"]["traceEvidence"]["count"])
            self.assertEqual(
                "skippedAnrRisk",
                report["micro"]["scenarios"]["fullBrightnessNoGapCanvasSubmit"]["methodTraceStatus"],
            )

    def test_noncanonical_method_trace_skip_message_is_an_evidence_failure(self) -> None:
        """普通缺失或伪造提示不能绕过 Method Trace 证据约束。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # 文本提到 ANR 但不满足 AndroidX 完整固定契约，门禁必须继续失败。
            fixture = self.prepare_fixture(Path(temporary_directory))
            self.replace_method_trace_with_androidx_anr_skip(
                fixture,
                "squareGapGridCanvasSubmit",
                message_text="Skipping trace because of ANR risk.\n",
            )
            report_exit = check_device_benchmarks.main(
                self.command_arguments(fixture, report_only=True),
            )
            report = json.loads(fixture["report"].read_text(encoding="utf-8"))
            self.assertEqual(0, report_exit)
            self.assertFalse(report["gates"]["evidencePassed"])
            self.assertFalse(report["micro"]["profilerEvidencePassed"])
            self.assertEqual(
                "missing",
                report["micro"]["scenarios"]["squareGapGridCanvasSubmit"]["methodTraceStatus"],
            )

    def test_missing_required_scenario_is_invalid_input(self) -> None:
        """Deleting one named journey is rejected before a partial report can look complete."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # Removing Overlay violates the exact seven-method Macrobenchmark contract.
            fixture = self.prepare_fixture(Path(temporary_directory))
            macro = json.loads(fixture["macroJson"].read_text(encoding="utf-8"))
            macro["benchmarks"] = [benchmark for benchmark in macro["benchmarks"] if benchmark["name"] != "overlay"]
            self.write_json(fixture["macroJson"], macro)
            gate_exit = check_device_benchmarks.main(self.command_arguments(fixture))
            self.assertEqual(2, gate_exit)

    def test_emulator_rehearsal_is_explicit_and_never_representative(self) -> None:
        """模拟器原始命名只在演练模式接受，且不能形成可通过的发布趋势证据。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # 完整模拟器证据仍生成报告和候选，便于验证采集链路。
            fixture = self.prepare_fixture(Path(temporary_directory), emulator=True)
            rehearsal_exit = check_device_benchmarks.main(
                self.command_arguments(
                    fixture,
                    report_only=True,
                    write_candidate=True,
                    evidence_mode=check_device_benchmarks.EMULATOR_REHEARSAL_MODE,
                ),
            )
            report = json.loads(fixture["report"].read_text(encoding="utf-8"))
            candidate = json.loads(fixture["candidate"].read_text(encoding="utf-8"))
            self.assertEqual(0, rehearsal_exit)
            self.assertEqual("emulator-rehearsal", report["evidenceMode"])
            self.assertFalse(report["representativePerformanceEvidence"])
            self.assertEqual("nonRepresentative", report["baselineComparison"]["status"])
            self.assertEqual(set(check_device_benchmarks.REQUIRED_MICRO_SCENARIOS), set(report["micro"]["scenarios"]))
            self.assertFalse(candidate["representativePerformanceEvidence"])

            # 即使人工修改候选审批字段，演练报告仍不能通过趋势门禁。
            self.approve_candidate(fixture["candidate"])
            enforced_exit = check_device_benchmarks.main(
                self.command_arguments(
                    fixture,
                    baseline=fixture["candidate"],
                    evidence_mode=check_device_benchmarks.EMULATOR_REHEARSAL_MODE,
                ),
            )
            self.assertEqual(1, enforced_exit)

    def test_physical_mode_rejects_emulator_and_rehearsal_rejects_physical(self) -> None:
        """实体与模拟器证据模式必须双向拒绝错误设备身份。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # 默认实体模式不能接受带完整 AVD 身份的原始证据。
            emulator_fixture = self.prepare_fixture(Path(temporary_directory) / "emulator", emulator=True)
            self.assertEqual(2, check_device_benchmarks.main(self.command_arguments(emulator_fixture)))

            # 演练模式也不能被用于普通实体身份，避免调用方错误标记证据来源。
            physical_fixture = self.prepare_fixture(Path(temporary_directory) / "physical")
            self.assertEqual(
                2,
                check_device_benchmarks.main(
                    self.command_arguments(
                        physical_fixture,
                        evidence_mode=check_device_benchmarks.EMULATOR_REHEARSAL_MODE,
                    ),
                ),
            )


if __name__ == "__main__":
    unittest.main()
