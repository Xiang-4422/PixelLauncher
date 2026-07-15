from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from typing import Any

from tools import check_macrobenchmark_compatibility


class MacrobenchmarkCompatibilityGateTest(unittest.TestCase):
    """锁定四档系统矩阵、指标覆盖、16KB 身份和原始文件精确性。"""

    def write_json(self, path: Path, value: dict[str, Any]) -> None:
        """以稳定 UTF-8 格式写入一个临时 JSON 夹具。"""

        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    def write_case(
        self,
        root: Path,
        api_level: int,
        device_name: str,
        page_size_bytes: int | None,
    ) -> Path:
        """创建一个包含七项 JUnit、消息、trace 和设备元数据的通过档位。"""

        # 每个 API 使用独立目录，避免文件名相同的消息互相覆盖。
        case_directory = root / f"api{api_level}"
        # AndroidX additional output 目录保存逐场景消息和 trace。
        additional_output = case_directory / "additional-output"
        additional_output.mkdir(parents=True)
        # JUnit testcase 顺序故意与常量不同，证明验收使用精确集合而非固定顺序。
        testcase_xml = "".join(
            f'<testcase name="{scenario}" '
            f'classname="{check_macrobenchmark_compatibility.BENCHMARK_CLASS_NAME}" time="1.0" />'
            for scenario in reversed(check_macrobenchmark_compatibility.REQUIRED_SCENARIOS)
        )
        # 汇总计数、设备属性和 testcase 集合共同构成完整 XML 证据。
        junit_xml = (
            "<?xml version='1.0' encoding='UTF-8'?>"
            f'<testsuite tests="7" failures="0" errors="0" skipped="0">'
            f'<properties><property name="device" value="{device_name}" /></properties>'
            f"{testcase_xml}</testsuite>"
        )
        # 文件名只需满足唯一 TEST-*.xml 约束。
        (case_directory / f"TEST-api{api_level}.xml").write_text(junit_xml, encoding="utf-8")
        # 元数据明确区分兼容 dry-run 与代表性性能测量。
        self.write_json(
            case_directory / "device-metadata.json",
            {
                "apiLevel": api_level,
                "dryRun": True,
                "isEmulator": True,
                "pageSizeBytes": page_size_bytes,
            },
        )
        for scenario in check_macrobenchmark_compatibility.REQUIRED_SCENARIOS:
            # 每个场景使用唯一且非空的 trace 文件。
            trace_name = f"PixelMacrobenchmark_{scenario}_iter000.perfetto-trace"
            (additional_output / trace_name).write_bytes(f"trace:{api_level}:{scenario}".encode("utf-8"))
            # 指标集合由生产验收器本身的 API/场景契约生成。
            metric_names = check_macrobenchmark_compatibility.expected_metrics(api_level, scenario)
            # 消息保留稳定标题、全部指标和唯一 trace 引用。
            message_lines = [f"PixelMacrobenchmark_{scenario}"]
            message_lines.extend(f"{metric_name} 1.0" for metric_name in metric_names)
            message_lines.append(f"Traces: Iteration [0](file://{trace_name})")
            # AndroidX 文件名必须与生产规则完全一致。
            message_name = (
                f"{check_macrobenchmark_compatibility.MESSAGE_FILE_PREFIX}{scenario}.txt"
            )
            (additional_output / message_name).write_text(
                "\n".join(message_lines) + "\n",
                encoding="utf-8",
            )
        return case_directory

    def write_matrix(self, root: Path) -> Path:
        """创建 API 24/29/36/37 的完整通过矩阵清单。"""

        # 设备名与页大小覆盖 XML 身份和 API 37 的 16KB 强制条件。
        case_specs = (
            (24, "API 24 fixture", None),
            (29, "API 29 fixture", None),
            (36, "API 36 fixture", None),
            (37, "API 37 fixture", 16384),
        )
        # 清单条目保存所有已创建证据目录。
        entries: list[dict[str, Any]] = []
        for api_level, device_name, page_size_bytes in case_specs:
            # 当前条目的完整原始证据目录。
            case_directory = self.write_case(root, api_level, device_name, page_size_bytes)
            entries.append(
                {
                    "apiLevel": api_level,
                    "evidenceDirectory": case_directory.as_posix(),
                    "expectedDevice": device_name,
                    "expectedPageSizeBytes": page_size_bytes,
                },
            )
        # 顶层清单固定版本与 dry-run 语义。
        manifest_path = root / "matrix.json"
        self.write_json(
            manifest_path,
            {
                "schemaVersion": 1,
                "evidenceMode": "compatibility-dry-run",
                "entries": entries,
            },
        )
        return manifest_path

    def run_gate(self, root: Path, manifest_path: Path) -> tuple[int, dict[str, Any]]:
        """运行生产入口并读取其机器可读报告。"""

        # 每个测试使用独立报告，避免失败报告覆盖其他断言。
        output_path = root / "report.json"
        # 入口返回值必须与报告状态保持一致。
        exit_code = check_macrobenchmark_compatibility.main(
            ["--manifest", str(manifest_path), "--output", str(output_path)],
        )
        # 报告根值由生产工具保证为对象。
        report = json.loads(output_path.read_text(encoding="utf-8"))
        return exit_code, report

    def test_complete_matrix_passes_and_is_not_performance_evidence(self) -> None:
        """完整四档 dry-run 通过，但必须明确不能充当代表性性能基线。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # 临时根目录容纳本测试所有原始证据。
            root = Path(temporary_directory)
            # 通过清单覆盖全部 API 档位。
            manifest_path = self.write_matrix(root)
            # 生产入口应成功并写出四个条目。
            exit_code, report = self.run_gate(root, manifest_path)
        self.assertEqual(exit_code, 0)
        self.assertEqual(report["status"], "pass")
        self.assertFalse(report["representativePerformanceEvidence"])
        self.assertEqual([entry["apiLevel"] for entry in report["entries"]], [24, 29, 36, 37])

    def test_missing_api_specific_metric_fails(self) -> None:
        """API 36 缺失 frameOverrunMs 时不能被通用帧指标掩盖。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # 临时根目录容纳可变消息夹具。
            root = Path(temporary_directory)
            # 先生成完整通过矩阵，再破坏一个 API 特定指标。
            manifest_path = self.write_matrix(root)
            # 动画消息是 API 36 五个产帧场景之一。
            message_path = root / "api36" / "additional-output" / (
                f"{check_macrobenchmark_compatibility.MESSAGE_FILE_PREFIX}animation.txt"
            )
            # 删除 overrun 名称但保留其他内容，模拟不完整 Perfetto 指标。
            message_path.write_text(
                message_path.read_text(encoding="utf-8").replace("frameOverrunMs 1.0\n", ""),
                encoding="utf-8",
            )
            # 门禁必须失败并给出缺失指标原因。
            exit_code, report = self.run_gate(root, manifest_path)
        self.assertEqual(exit_code, 1)
        self.assertIn("frameOverrunMs", report["errors"][0])

    def test_wrong_16kb_metadata_fails(self) -> None:
        """API 37 元数据不是 16KB 时，即使七项测试全绿也必须失败。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # 临时根目录容纳设备身份变体。
            root = Path(temporary_directory)
            # 完整矩阵提供可被单点篡改的通过基线。
            manifest_path = self.write_matrix(root)
            # API 37 元数据被改成普通 4KB 页大小。
            metadata_path = root / "api37" / "device-metadata.json"
            self.write_json(
                metadata_path,
                {
                    "apiLevel": 37,
                    "dryRun": True,
                    "isEmulator": True,
                    "pageSizeBytes": 4096,
                },
            )
            # 页大小身份不匹配必须产生非零退出码。
            exit_code, report = self.run_gate(root, manifest_path)
        self.assertEqual(exit_code, 1)
        self.assertIn("页大小不匹配", report["errors"][0])

    def test_stale_extra_trace_fails(self) -> None:
        """证据目录残留另一轮 trace 时不能静默选择其中一组。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # 临时根目录容纳额外陈旧文件。
            root = Path(temporary_directory)
            # 完整矩阵先建立精确文件集合。
            manifest_path = self.write_matrix(root)
            # 额外 trace 模拟复制证据时未清空旧运行目录。
            stale_trace = root / "api29" / "additional-output" / "stale.perfetto-trace"
            stale_trace.write_bytes(b"stale")
            # 精确集合检查必须拒绝该目录。
            exit_code, report = self.run_gate(root, manifest_path)
        self.assertEqual(exit_code, 1)
        self.assertIn("trace 集合不精确", report["errors"][0])


if __name__ == "__main__":
    unittest.main()
