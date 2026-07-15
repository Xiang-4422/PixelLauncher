from __future__ import annotations

import os
import subprocess
import tempfile
import unittest
from pathlib import Path


class PixelConnectedBenchmarkScriptTest(unittest.TestCase):
    """验证 connected benchmark 只能运行在显式授权且身份一致的设备上。"""

    def write_executable(self, path: Path, source: str) -> None:
        """写入一个临时 shell 替身并授予当前用户执行权限。"""

        path.write_text(source, encoding="utf-8")
        path.chmod(0o755)

    def prepare_tools(self, root: Path) -> tuple[Path, Path, Path]:
        """创建可控制 qemu 身份并记录 Gradle 参数的 adb/Gradle 替身。"""

        # adb 替身只实现生产脚本使用的三个只读查询。
        adb_path = root / "adb"
        self.write_executable(
            adb_path,
            """#!/usr/bin/env bash
set -euo pipefail
if [[ "$3" == "get-state" ]]; then
  echo device
elif [[ "$3" == "shell" && "$4" == "getprop" && "$5" == "ro.serialno" ]]; then
  echo FAKE-HARDWARE-SERIAL
elif [[ "$3" == "shell" && "$4" == "getprop" && "$5" == "ro.kernel.qemu" ]]; then
  echo "${FAKE_QEMU-1}"
else
  echo "unexpected adb arguments: $*" >&2
  exit 9
fi
""",
        )
        # Gradle 替身记录 ANDROID_SERIAL 与所有参数，证明选择和设备端校验使用同一身份。
        gradle_path = root / "gradlew"
        self.write_executable(
            gradle_path,
            """#!/usr/bin/env bash
set -euo pipefail
{
  echo "ANDROID_SERIAL=${ANDROID_SERIAL:-}"
  printf '%s\n' "$@"
} > "$FAKE_GRADLE_LOG"
""",
        )
        # 日志文件由 Gradle 替身在真正被调用时创建。
        log_path = root / "gradle.log"
        return adb_path, gradle_path, log_path

    def run_script(
        self,
        root: Path,
        *,
        serial: str | None,
        qemu: str = "1",
        allow_physical: str = "0",
        test_class: str | None = None,
    ) -> tuple[subprocess.CompletedProcess[str], Path]:
        """使用隔离环境运行生产脚本并返回进程结果与 Gradle 日志路径。"""

        # 每次调用使用新的替身文件和空日志。
        adb_path, gradle_path, log_path = self.prepare_tools(root)
        # 环境继承 PATH，同时覆盖生产脚本的可注入工具与授权变量。
        environment = os.environ.copy()
        environment.update(
            {
                "PIXEL_ADB_BIN": str(adb_path),
                "PIXEL_GRADLEW_BIN": str(gradle_path),
                "PIXEL_BENCHMARK_ALLOW_PHYSICAL": allow_physical,
                "FAKE_QEMU": qemu,
                "FAKE_GRADLE_LOG": str(log_path),
            },
        )
        if serial is None:
            environment.pop("PIXEL_BENCHMARK_SERIAL", None)
        else:
            environment["PIXEL_BENCHMARK_SERIAL"] = serial
        if test_class is None:
            environment.pop("PIXEL_BENCHMARK_TEST_CLASS", None)
        else:
            environment["PIXEL_BENCHMARK_TEST_CLASS"] = test_class
        # 生产脚本路径由仓库根目录相对当前测试文件解析。
        script_path = Path(__file__).resolve().parents[1] / "pixel-connected-benchmark.sh"
        # 虚拟 Gradle 任务名用于验证参数透传且不会启动真实构建。
        result = subprocess.run(
            [str(script_path), "fixtureTask", "--fixture-argument"],
            text=True,
            capture_output=True,
            env=environment,
            check=False,
        )
        return result, log_path

    def test_missing_serial_stops_before_gradle(self) -> None:
        """缺少显式序列号时不得选择 adb 默认设备。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # 临时目录容纳替身工具与可能的调用日志。
            root = Path(temporary_directory)
            # 不提供序列号应在任何 Gradle 调用前失败。
            result, log_path = self.run_script(root, serial=None)
        self.assertEqual(result.returncode, 2)
        self.assertIn("缺少 PIXEL_BENCHMARK_SERIAL", result.stderr)
        self.assertFalse(log_path.exists())

    def test_emulator_binds_host_and_instrumentation_identity(self) -> None:
        """模拟器调用同时固定 ANDROID_SERIAL、AGP 序列号和硬件序列号。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # 临时目录保存成功调用的完整参数证据。
            root = Path(temporary_directory)
            # qemu=1 表示无需实体设备额外授权。
            result, log_path = self.run_script(root, serial="emulator-5554", qemu="1")
            # 日志在临时目录销毁前读取。
            log_text = log_path.read_text(encoding="utf-8")
        self.assertEqual(result.returncode, 0)
        self.assertIn("ANDROID_SERIAL=emulator-5554", log_text)
        self.assertIn("-Pandroid.injected.device.serial=emulator-5554", log_text)
        self.assertIn(
            "-Ppixel.benchmark.expectedHardwareSerial=FAKE-HARDWARE-SERIAL",
            log_text,
        )
        self.assertIn("-Ppixel.benchmark.allowPhysical=false", log_text)
        self.assertIn("fixtureTask", log_text)

    def test_explicit_test_class_is_forwarded(self) -> None:
        """专用长跑或 Macrobenchmark 类过滤器必须原样传入 Gradle。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # 临时日志证明 wrapper 只对当前调用注入测试类属性。
            root = Path(temporary_directory)
            # 使用稳定夹具类名验证属性拼接，不启动真实 instrumentation。
            result, log_path = self.run_script(
                root,
                serial="emulator-5554",
                test_class="com.example.SoakTest",
            )
            # 日志必须在临时目录销毁前读取。
            log_text = log_path.read_text(encoding="utf-8")
        self.assertEqual(result.returncode, 0)
        self.assertIn("-Ppixel.benchmark.testClass=com.example.SoakTest", log_text)

    def test_physical_device_is_rejected_by_default(self) -> None:
        """qemu 身份缺失时必须在 Gradle 部署前拒绝实体设备。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # 临时目录允许断言 Gradle 日志完全未创建。
            root = Path(temporary_directory)
            # qemu 空值模拟普通实体设备属性。
            result, log_path = self.run_script(root, serial="physical-1", qemu="")
        self.assertEqual(result.returncode, 2)
        self.assertIn("拒绝隐式操作实体设备", result.stderr)
        self.assertFalse(log_path.exists())

    def test_explicit_physical_authorization_is_forwarded(self) -> None:
        """专用真实设备采集必须逐次传递 allowPhysical=true。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # 临时目录保存实体设备显式授权的参数证据。
            root = Path(temporary_directory)
            # 只有当前调用设置 1 才允许 qemu 非模拟器身份继续。
            result, log_path = self.run_script(
                root,
                serial="physical-1",
                qemu="",
                allow_physical="1",
            )
            # 成功日志必须包含设备端二次校验使用的 true 值。
            log_text = log_path.read_text(encoding="utf-8")
        self.assertEqual(result.returncode, 0)
        self.assertIn("ANDROID_SERIAL=physical-1", log_text)
        self.assertIn("-Ppixel.benchmark.allowPhysical=true", log_text)

    def test_text_input_journey_waits_for_committed_semantics_without_global_idle(self) -> None:
        """文本旅程必须等待精确语义重绘，且不能采集动作后的周期性光标 idle。"""

        # 生产旅程源码是本静态合同的唯一输入，避免测试复制一份容易漂移的实现。
        journey_source_path = (
            Path(__file__).resolve().parents[2]
            / "pixel-benchmark/src/main/kotlin/com/purride/pixelbenchmark/PixelBenchmarkJourneys.kt"
        )
        # 只截取 enterText，防止列表旅程合法的 waitForIdle 造成误报。
        journey_source = journey_source_path.read_text(encoding="utf-8")
        enter_text_source = journey_source.split("fun enterText()", maxsplit=1)[1].split(
            "fun runAnimation()",
            maxsplit=1,
        )[0]
        self.assertIn("node.text?.toString() == BenchmarkInputText", enter_text_source)
        self.assertIn("node.textSelectionStart == BenchmarkInputText.length", enter_text_source)
        self.assertIn("node.textSelectionEnd == BenchmarkInputText.length", enter_text_source)
        self.assertNotIn("waitForIdle()", enter_text_source)


if __name__ == "__main__":
    unittest.main()
