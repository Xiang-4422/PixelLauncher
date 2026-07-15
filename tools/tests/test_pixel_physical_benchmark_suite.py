from __future__ import annotations

import os
import subprocess
import tempfile
import unittest
from pathlib import Path


class PixelPhysicalBenchmarkSuiteTest(unittest.TestCase):
    """验证实体性能 wrapper 的设备锁定、Magisk 恢复和退出码契约。"""

    def write_executable(self, path: Path, source: str) -> None:
        """写入一个隔离 shell 替身并授予当前用户执行权限。"""

        path.write_text(source, encoding="utf-8")
        path.chmod(0o755)

    def prepare_fixture(self, root: Path) -> tuple[Path, Path, Path, Path]:
        """创建记录设备动作并模拟 Magisk deny/restore 状态的工具替身。"""

        # state_dir 保存跨 adb 子进程共享的策略和 marker 状态。
        state_dir = root / "state"
        state_dir.mkdir()
        # adb_log 记录所有设备动作，供测试断言准备与恢复顺序。
        adb_log = root / "adb.log"
        # benchmark_log 证明正式 connected wrapper 只被调用一次且收到实体授权。
        benchmark_log = root / "benchmark.log"
        # adb_path 是仅实现生产 wrapper 所需命令的确定性设备替身。
        adb_path = root / "adb"
        self.write_executable(
            adb_path,
            r"""#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$FAKE_ADB_LOG"
if [[ "$1" != "-s" || "$2" != "physical-1" ]]; then
  echo "unexpected serial arguments: $*" >&2
  exit 9
fi
if [[ "$3" == "get-state" ]]; then
  echo device
elif [[ "$3" == "push" ]]; then
  exit 0
elif [[ "$3" == "shell" && "$4" == "getprop" && "$5" == "ro.kernel.qemu" ]]; then
  echo "${FAKE_QEMU:-}"
elif [[ "$3" == "shell" && "$4" == "getconf" && "$5" == "_NPROCESSORS_CONF" ]]; then
  echo 8
elif [[ "$3" == "shell" && "$4" == "cat" && "$5" == /sys/devices/system/cpu/cpu*/online ]]; then
  echo 1
elif [[ "$3" == "shell" && "$4" == "cat" && "$5" == /sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq ]]; then
  if [[ "$5" == */cpu7/* ]]; then
    echo 2956800
  else
    echo 1785600
  fi
elif [[ "$3" == "shell" && "$4" == "cat" && "$5" == /sys/devices/system/cpu/cpu*/cpufreq/scaling_max_freq ]]; then
  if [[ "$5" == */cpu7/* && -e "$FAKE_STATE_DIR/prime_locked" && !( "${FAKE_CPU_CEILING_DROPS_AFTER_PREPARE:-0}" == "1" && -e "$FAKE_STATE_DIR/prepared" ) ]]; then
    echo 2956800
  elif [[ "$5" == */cpu7/* && "${FAKE_CPU_CEILING_LIMITED:-0}" == "1" ]]; then
    echo 2016000
  elif [[ "$5" == */cpu7/* && "${FAKE_CPU_CEILING_DROPS_AFTER_PREPARE:-0}" == "1" && -e "$FAKE_STATE_DIR/prepared" ]]; then
    echo 2016000
  elif [[ "$5" == */cpu7/* ]]; then
    echo 2956800
  else
    echo 1785600
  fi
elif [[ "$3" == "shell" && "$4" == "cat" && "$5" == /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor ]]; then
  if [[ "$5" == */cpu7/* && -e "$FAKE_STATE_DIR/prime_locked" ]]; then
    echo userspace
  else
    echo schedutil
  fi
elif [[ "$3" == "shell" && "$4" == "cat" && "$5" == /sys/devices/system/cpu/cpu*/cpufreq/scaling_min_freq ]]; then
  echo 825600
elif [[ "$3" == "shell" && "$4" == "cat" && ( "$5" == /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq || "$5" == /sys/devices/system/cpu/cpu*/cpufreq/scaling_setspeed ) ]]; then
  if [[ "$5" == */cpu7/* && -e "$FAKE_STATE_DIR/prime_locked" && !( "${FAKE_CPU_CEILING_DROPS_AFTER_PREPARE:-0}" == "1" && -e "$FAKE_STATE_DIR/prepared" ) ]]; then
    echo 2956800
  else
    echo 2016000
  fi
elif [[ "$3" == "shell" && "$4" == "cat" && "$5" == /data/local/tmp/pixel-cpu-frequency-*.violation ]]; then
  if [[ -e "$FAKE_STATE_DIR/cpu_violation" ]]; then
    echo "reason=prime-cpu-frequency-invariant-broken"
    echo "cpuIndex=7"
    echo "expectedFrequencyKHz=2956800"
    echo "governor=userspace"
    echo "scalingCurFreqKHz=2419200"
    echo "scalingSetSpeedKHz=2419200"
    echo "scalingMaxFreqKHz=2419200"
    exit 0
  fi
  exit 1
elif [[ "$3" == "shell" && "$4" == "settings" && "$5" == "get" ]]; then
  if [[ "$7" == "stay_on_while_plugged_in" ]]; then
    echo 0
  elif [[ "$7" == "POWER_PERFORMANCE_MODE_OPEN" ]]; then
    echo 0
  elif [[ "$7" == "POWER_BALANCED_MODE_OPEN" ]]; then
    echo 1
  else
    echo 90
  fi
elif [[ "$3" == "shell" && "$4" == "settings" && ( "$5" == "put" || "$5" == "delete" ) ]]; then
  if [[ "$5" == "put" && "$7" == "peak_refresh_rate" ]]; then
    : > "$FAKE_STATE_DIR/prepared"
  fi
  exit 0
elif [[ "$3" == "shell" && ( "$4" == "input" || "$4" == "wm" ) ]]; then
  exit 0
elif [[ "$3" == "shell" && "$4" == "dumpsys" && "$5" == "power" ]]; then
  if [[ "${FAKE_DEVICE_ASLEEP_BEFORE_PREPARE:-0}" == "1" && ! -e "$FAKE_STATE_DIR/prepared" ]]; then
    echo "  mWakefulness=Dozing"
    echo "  mStayOn=false"
    echo "  mStayOnWhilePluggedInSetting=0"
  else
    echo "  mWakefulness=Awake"
    echo "  mStayOn=true"
    echo "  mStayOnWhilePluggedInSetting=7"
  fi
elif [[ "$3" == "shell" && "$4" == "dumpsys" && "$5" == "window" && "$6" == "policy" ]]; then
  if [[ "${FAKE_DEVICE_LOCKED:-0}" == "1" ]]; then
    echo "      showing=true"
    echo "      interactiveState=INTERACTIVE_STATE_AWAKE"
    echo "        mIsShowing=true"
  else
    echo "      showing=false"
    echo "      interactiveState=INTERACTIVE_STATE_AWAKE"
    echo "        mIsShowing=false"
  fi
elif [[ "$3" == "shell" && "$4" == "su" && "$5" == "-c" && "$6" == "id" ]]; then
  if [[ -e "$FAKE_STATE_DIR/denied" ]]; then
    echo "Permission denied" >&2
    exit 1
  fi
  echo "uid=0(root) gid=0(root) groups=0(root)"
elif [[ "$3" == "shell" && "$4" == *"SELECT uid,policy,until,logging,notification"* ]]; then
  if [[ "${FAKE_CUSTOM_POLICY:-0}" == "1" ]]; then
    echo "logging=0|notification=1|policy=2|uid=2000|until=0"
  else
    echo "logging=1|notification=1|policy=2|uid=2000|until=0"
  fi
elif [[ "$3" == "shell" && "$4" == *"echo userspace > /sys/devices/system/cpu/cpu7/cpufreq/scaling_governor"* ]]; then
  : > "$FAKE_STATE_DIR/prime_locked"
elif [[ "$3" == "shell" && "$4" == *"echo schedutil > /sys/devices/system/cpu/cpu7/cpufreq/scaling_governor"* ]]; then
  rm -f "$FAKE_STATE_DIR/prime_locked"
elif [[ "$3" == "shell" && "$4" == *"echo "*" > /sys/devices/system/cpu/cpu7/cpufreq/"* ]]; then
  exit 0
elif [[ "$3" == "shell" && "$4" == *"chmod 0700 /data/local/tmp/pixel-cpu-frequency-"* && "$4" == *"start-stop-daemon -S -b -m"* ]]; then
  : > "$FAKE_STATE_DIR/cpu_ready"
  if [[ "${FAKE_CPU_RUNTIME_VIOLATION:-0}" == "1" ]]; then
    : > "$FAKE_STATE_DIR/cpu_violation"
    : > "$FAKE_STATE_DIR/cpu_done"
  fi
elif [[ "$3" == "shell" && "$4" == *"chmod 0700 /data/local/tmp/pixel-magisk-benchmark-"* && "$4" == *"start-stop-daemon -S -b -m"* ]]; then
  : > "$FAKE_STATE_DIR/denied"
  : > "$FAKE_STATE_DIR/magisk_ready"
elif [[ "$3" == "shell" && "$4" == "test" && "$5" == "-e" ]]; then
  if [[ "$6" == /data/local/tmp/pixel-cpu-frequency-*.ready && -e "$FAKE_STATE_DIR/cpu_ready" ]]; then
    exit 0
  fi
  if [[ "$6" == /data/local/tmp/pixel-cpu-frequency-*.done && -e "$FAKE_STATE_DIR/cpu_done" ]]; then
    exit 0
  fi
  if [[ "$6" == /data/local/tmp/pixel-magisk-benchmark-*.ready && -e "$FAKE_STATE_DIR/magisk_ready" ]]; then
    exit 0
  fi
  if [[ "$6" == /data/local/tmp/pixel-magisk-benchmark-*.done && -e "$FAKE_STATE_DIR/magisk_done" ]]; then
    exit 0
  fi
  exit 1
elif [[ "$3" == "shell" && "$4" == "touch" && "$5" == /data/local/tmp/pixel-cpu-frequency-*.stop ]]; then
  : > "$FAKE_STATE_DIR/cpu_done"
elif [[ "$3" == "shell" && "$4" == "touch" && "$5" == *.restore ]]; then
  rm -f "$FAKE_STATE_DIR/denied"
  : > "$FAKE_STATE_DIR/magisk_done"
elif [[ "$3" == "shell" && "$4" == *"rm -f /data/local/tmp/pixel-cpu-frequency-"* ]]; then
  rm -f "$FAKE_STATE_DIR/cpu_ready" "$FAKE_STATE_DIR/cpu_done" "$FAKE_STATE_DIR/cpu_violation"
elif [[ "$3" == "shell" && "$4" == *"rm -f /data/local/tmp/pixel-magisk-benchmark-"* ]]; then
  exit 0
else
  echo "unexpected adb arguments: $*" >&2
  exit 9
fi
""",
        )
        # benchmark_path 模拟底层 connected wrapper，并保留调用环境和参数。
        benchmark_path = root / "connected-benchmark"
        self.write_executable(
            benchmark_path,
            r"""#!/usr/bin/env bash
set -euo pipefail
{
  echo "PIXEL_BENCHMARK_SERIAL=${PIXEL_BENCHMARK_SERIAL:-}"
  echo "PIXEL_BENCHMARK_ALLOW_PHYSICAL=${PIXEL_BENCHMARK_ALLOW_PHYSICAL:-}"
  printf '%s\n' "$@"
} > "$FAKE_BENCHMARK_LOG"
exit "${FAKE_BENCHMARK_EXIT:-0}"
""",
        )
        return adb_path, benchmark_path, adb_log, benchmark_log

    def run_suite(
        self,
        root: Path,
        *,
        serial: str | None = "physical-1",
        refresh_rate: str | None = "60",
        benchmark_exit: int = 0,
        qemu: str = "",
        custom_policy: bool = False,
        device_locked: bool = False,
        cpu_ceiling_limited: bool = False,
        cpu_ceiling_drops_after_prepare: bool = False,
        cpu_runtime_violation: bool = False,
        device_asleep_before_prepare: bool = False,
    ) -> tuple[subprocess.CompletedProcess[str], Path, Path, Path]:
        """运行生产 wrapper，并返回进程、日志和跨进程设备状态。"""

        # adb_path 与 benchmark_path 完全隔离真实设备和 Gradle。
        adb_path, benchmark_path, adb_log, benchmark_log = self.prepare_fixture(root)
        # state_dir 必须与 adb 替身创建的跨进程状态目录一致。
        state_dir = root / "state"
        # environment 只覆盖生产脚本公开的工具、身份和测试控制变量。
        environment = os.environ.copy()
        environment.update(
            {
                "PIXEL_ADB_BIN": str(adb_path),
                "PIXEL_CONNECTED_BENCHMARK_SCRIPT": str(benchmark_path),
                "PIXEL_MAGISK_RESTORE_TIMEOUT_SECONDS": "60",
                "PIXEL_MAGISK_POLICY_SETTLE_SECONDS": "1",
                "PIXEL_DEVICE_READY_TIMEOUT_SECONDS": "1",
                "PIXEL_CPU_CEILING_TIMEOUT_SECONDS": "1",
                "FAKE_ADB_LOG": str(adb_log),
                "FAKE_BENCHMARK_LOG": str(benchmark_log),
                "FAKE_BENCHMARK_EXIT": str(benchmark_exit),
                "FAKE_QEMU": qemu,
                "FAKE_CUSTOM_POLICY": "1" if custom_policy else "0",
                "FAKE_DEVICE_LOCKED": "1" if device_locked else "0",
                "FAKE_DEVICE_ASLEEP_BEFORE_PREPARE": (
                    "1" if device_asleep_before_prepare else "0"
                ),
                "FAKE_CPU_CEILING_LIMITED": "1" if cpu_ceiling_limited else "0",
                "FAKE_CPU_CEILING_DROPS_AFTER_PREPARE": (
                    "1" if cpu_ceiling_drops_after_prepare else "0"
                ),
                "FAKE_CPU_RUNTIME_VIOLATION": "1" if cpu_runtime_violation else "0",
                "FAKE_STATE_DIR": str(state_dir),
            },
        )
        if serial is None:
            environment.pop("PIXEL_BENCHMARK_SERIAL", None)
        else:
            environment["PIXEL_BENCHMARK_SERIAL"] = serial
        if refresh_rate is None:
            environment.pop("PIXEL_PHYSICAL_REFRESH_RATE_HZ", None)
        else:
            environment["PIXEL_PHYSICAL_REFRESH_RATE_HZ"] = refresh_rate
        # script_path 指向仓库中的真实实体采集 wrapper。
        script_path = Path(__file__).resolve().parents[1] / "pixel-physical-benchmark-suite.sh"
        # fixtureTask 只验证参数传播，不启动任何真实构建。
        result = subprocess.run(
            [str(script_path), "fixtureTask", "--fixture-argument"],
            text=True,
            capture_output=True,
            env=environment,
            check=False,
            timeout=15,
        )
        return result, adb_log, benchmark_log, state_dir

    def test_requires_explicit_serial_and_refresh_rate(self) -> None:
        """缺少设备身份或矩阵刷新率时不得开始任何设备准备。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # root 保存本次缺少序列号夹具。
            root = Path(temporary_directory)
            # serial=None 必须在调用 adb 前结束。
            result, adb_log, benchmark_log, _ = self.run_suite(root, serial=None)
        self.assertEqual(result.returncode, 2)
        self.assertIn("缺少 PIXEL_BENCHMARK_SERIAL", result.stderr)
        self.assertFalse(adb_log.exists())
        self.assertFalse(benchmark_log.exists())

        with tempfile.TemporaryDirectory() as temporary_directory:
            # root 保存本次缺少刷新率夹具。
            root = Path(temporary_directory)
            # refresh_rate=None 必须在调用 adb 前结束。
            result, adb_log, benchmark_log, _ = self.run_suite(root, refresh_rate=None)
        self.assertEqual(result.returncode, 2)
        self.assertIn("PIXEL_PHYSICAL_REFRESH_RATE_HZ", result.stderr)
        self.assertFalse(adb_log.exists())
        self.assertFalse(benchmark_log.exists())

    def test_rejects_emulator_before_device_mutation(self) -> None:
        """qemu=1 时只能读取身份，不能修改设置或调用 benchmark。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # root 保存模拟器拒绝路径的动作日志。
            root = Path(temporary_directory)
            # qemu=1 明确模拟器身份。
            result, adb_log, benchmark_log, _ = self.run_suite(root, qemu="1")
            # adb_text 在临时目录销毁前读取。
            adb_text = adb_log.read_text(encoding="utf-8")
        self.assertEqual(result.returncode, 2)
        self.assertIn("拒绝把模拟器", result.stderr)
        self.assertNotIn("settings put", adb_text)
        self.assertFalse(benchmark_log.exists())

    def test_success_prepares_denies_runs_and_restores(self) -> None:
        """成功采集必须经历设备准备、Shell deny、benchmark 和完整恢复。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # root 保存成功路径的完整证据。
            root = Path(temporary_directory)
            # 默认 fake 环境模拟标准 Magisk allow 策略。
            result, adb_log, benchmark_log, state_dir = self.run_suite(root)
            # 两份日志必须在临时目录销毁前读取。
            adb_text = adb_log.read_text(encoding="utf-8")
            benchmark_text = benchmark_log.read_text(encoding="utf-8")
            # denied_after_run 证明 EXIT trap 已清除临时拒绝状态。
            denied_after_run = (state_dir / "denied").exists()
        self.assertEqual(result.returncode, 0, msg=result.stderr)
        self.assertIn("settings put system peak_refresh_rate 60", adb_text)
        self.assertIn("settings put system POWER_PERFORMANCE_MODE_OPEN 1", adb_text)
        self.assertIn("settings put system POWER_BALANCED_MODE_OPEN 0", adb_text)
        self.assertIn("settings put global stay_on_while_plugged_in 7", adb_text)
        self.assertGreaterEqual(adb_text.count("shell dumpsys power"), 2)
        self.assertGreaterEqual(adb_text.count("shell dumpsys window policy"), 2)
        self.assertIn("pixel-magisk-shell-policy-watchdog.sh", adb_text)
        self.assertIn(".restore", adb_text)
        self.assertIn("settings put system peak_refresh_rate 90", adb_text)
        self.assertIn("settings put system POWER_PERFORMANCE_MODE_OPEN 0", adb_text)
        self.assertIn("settings put system POWER_BALANCED_MODE_OPEN 1", adb_text)
        self.assertIn("settings put global stay_on_while_plugged_in 0", adb_text)
        self.assertIn("PIXEL_BENCHMARK_SERIAL=physical-1", benchmark_text)
        self.assertIn("PIXEL_BENCHMARK_ALLOW_PHYSICAL=1", benchmark_text)
        self.assertFalse(denied_after_run)

    def test_locked_device_fails_before_magisk_and_benchmark(self) -> None:
        """Keyguard 未解除时必须先恢复设置，且不得启动 watchdog 或正式 benchmark。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # root 保存锁屏拒绝路径的动作和恢复证据。
            root = Path(temporary_directory)
            # device_locked=True 模拟 MIUI 忽略 dismiss-keyguard 后仍保持锁屏。
            result, adb_log, benchmark_log, state_dir = self.run_suite(
                root,
                device_locked=True,
            )
            # adb_text 在临时目录销毁前读取，供顺序和副作用断言使用。
            adb_text = adb_log.read_text(encoding="utf-8")
            # denied_created 证明设备就绪失败发生在 Magisk 策略切换之前。
            denied_created = (state_dir / "denied").exists()
        self.assertEqual(result.returncode, 1)
        self.assertIn("解除 Keyguard", result.stderr)
        self.assertFalse(benchmark_log.exists())
        self.assertNotIn("pixel-magisk-shell-policy-watchdog.sh", adb_text)
        self.assertIn("settings put global stay_on_while_plugged_in 0", adb_text)
        self.assertFalse(denied_created)

    def test_limited_prime_cpu_ceiling_fails_before_device_mutation(self) -> None:
        """超大核策略上限低于硬件上限时不得修改设备或启动正式 benchmark。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # root 保存频率受限拒绝路径的动作与副作用证据。
            root = Path(temporary_directory)
            # cpu_ceiling_limited=True 模拟 MIUI 把 2.956GHz 超大核限制在 2.016GHz。
            result, adb_log, benchmark_log, state_dir = self.run_suite(
                root,
                cpu_ceiling_limited=True,
            )
            # adb_text 在临时目录销毁前读取，验证门禁发生在任何设备写操作之前。
            adb_text = adb_log.read_text(encoding="utf-8")
            # denied_created 证明 Magisk watchdog 未在频率拒绝路径启动。
            denied_created = (state_dir / "denied").exists()
        self.assertEqual(result.returncode, 1)
        self.assertIn("超大核频率上限未在 1 秒内恢复", result.stderr)
        self.assertIn("7|2956800|2016000", result.stderr)
        self.assertFalse(benchmark_log.exists())
        self.assertIn("settings put system POWER_PERFORMANCE_MODE_OPEN 1", adb_text)
        self.assertIn("settings put system POWER_PERFORMANCE_MODE_OPEN 0", adb_text)
        self.assertNotIn("settings put system peak_refresh_rate", adb_text)
        self.assertNotIn("pixel-magisk-shell-policy-watchdog.sh", adb_text)
        self.assertFalse(denied_created)

    def test_prime_cpu_ceiling_drop_after_prepare_restores_settings(self) -> None:
        """亮屏准备触发 OEM 降频时必须恢复设置且不得启动 Magisk 或 benchmark。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # root 保存准备后降频路径的设备动作与恢复证据。
            root = Path(temporary_directory)
            # cpu_ceiling_drops_after_prepare 模拟休眠态满频、亮屏后被限制的 MIUI 竞态。
            result, adb_log, benchmark_log, state_dir = self.run_suite(
                root,
                cpu_ceiling_drops_after_prepare=True,
            )
            # adb_text 在临时目录销毁前读取，验证设置已写入后仍由 EXIT trap 完整恢复。
            adb_text = adb_log.read_text(encoding="utf-8")
            # denied_created 证明降频拒绝发生在 Magisk watchdog 启动之前。
            denied_created = (state_dir / "denied").exists()
        self.assertEqual(result.returncode, 1)
        self.assertIn("唤醒与刷新率准备后重新限制超大核频率", result.stderr)
        self.assertIn("7|2956800|2016000", result.stderr)
        self.assertFalse(benchmark_log.exists())
        self.assertIn("settings put system peak_refresh_rate 60", adb_text)
        self.assertIn("settings put system peak_refresh_rate 90", adb_text)
        self.assertNotIn("pixel-magisk-shell-policy-watchdog.sh", adb_text)
        self.assertFalse(denied_created)

    def test_benchmark_failure_propagates_after_cleanup(self) -> None:
        """底层失败必须保留原退出码，同时仍恢复 root 与设备设置。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # root 保存失败路径的清理证据。
            root = Path(temporary_directory)
            # benchmark_exit=17 模拟 Gradle/instrumentation 原始失败。
            result, adb_log, benchmark_log, state_dir = self.run_suite(
                root,
                benchmark_exit=17,
            )
            # adb_text 在临时目录销毁前读取。
            adb_text = adb_log.read_text(encoding="utf-8")
            # benchmark_called 在临时目录销毁前记录底层 wrapper 是否真正执行。
            benchmark_called = benchmark_log.exists()
            # denied_after_run 证明失败没有绕过 EXIT trap。
            denied_after_run = (state_dir / "denied").exists()
        self.assertEqual(result.returncode, 17)
        self.assertTrue(benchmark_called)
        self.assertIn(".restore", adb_text)
        self.assertIn("settings put system min_refresh_rate 90", adb_text)
        self.assertFalse(denied_after_run)

    def test_runtime_cpu_frequency_violation_rejects_successful_benchmark(self) -> None:
        """采集中途降频必须拒收原本成功的 benchmark，并完成全部环境恢复。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # root 保存运行中频率破坏路径的动作、退出码和清理证据。
            root = Path(temporary_directory)
            # cpu_runtime_violation=True 模拟 MIUI 在 Gradle 执行期间改写超大核目标频率。
            result, adb_log, benchmark_log, state_dir = self.run_suite(
                root,
                cpu_runtime_violation=True,
            )
            # adb_text 在临时目录销毁前读取，验证看门狗完成停止和临时文件清理。
            adb_text = adb_log.read_text(encoding="utf-8")
            # benchmark_called 证明拒收来自运行中证据，而不是前置门禁误判。
            benchmark_called = benchmark_log.exists()
            # denied_after_run 证明频率拒收没有绕过 Magisk 策略恢复。
            denied_after_run = (state_dir / "denied").exists()
        self.assertEqual(result.returncode, 1)
        self.assertTrue(benchmark_called)
        self.assertIn("benchmark 期间破坏超大核频率不变量", result.stderr)
        self.assertIn("scalingCurFreqKHz=2419200", result.stderr)
        self.assertIn("pixel-cpu-frequency-", adb_text)
        self.assertIn(".stop", adb_text)
        self.assertIn("rm -f /data/local/tmp/pixel-cpu-frequency-", adb_text)
        self.assertIn("settings put system POWER_BALANCED_MODE_OPEN 1", adb_text)
        self.assertFalse(denied_after_run)

    def test_originally_sleeping_device_is_returned_to_sleep(self) -> None:
        """采集前处于 Dozing 的设备必须在成功退出后收到明确休眠恢复动作。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # root 保存原始休眠设备的准备和恢复动作。
            root = Path(temporary_directory)
            # device_asleep_before_prepare=True 让首次 power 快照为 Dozing，准备后才为 Awake。
            result, adb_log, _, _ = self.run_suite(
                root,
                device_asleep_before_prepare=True,
            )
            # adb_text 用于证明 KEYCODE_SLEEP 位于完整成功路径中。
            adb_text = adb_log.read_text(encoding="utf-8")
        self.assertEqual(result.returncode, 0, msg=result.stderr)
        self.assertIn("shell input keyevent KEYCODE_WAKEUP", adb_text)
        self.assertIn("shell input keyevent KEYCODE_SLEEP", adb_text)

    def test_custom_magisk_policy_is_never_overwritten(self) -> None:
        """非标准 Shell 策略必须拒绝采集并只恢复已修改的系统设置。"""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # root 保存自定义策略拒绝路径的证据。
            root = Path(temporary_directory)
            # custom_policy=True 模拟用户关闭 logging 的自定义授权行。
            result, adb_log, benchmark_log, state_dir = self.run_suite(
                root,
                custom_policy=True,
            )
            # adb_text 在临时目录销毁前读取。
            adb_text = adb_log.read_text(encoding="utf-8")
            # denied_created 证明 watchdog 从未启动。
            denied_created = (state_dir / "denied").exists()
        self.assertEqual(result.returncode, 2)
        self.assertIn("拒绝修改非标准 Magisk Shell 策略", result.stderr)
        self.assertFalse(benchmark_log.exists())
        self.assertNotIn("pixel-magisk-shell-policy-watchdog.sh", adb_text)
        self.assertIn("settings put system user_refresh_rate 90", adb_text)
        self.assertFalse(denied_created)


if __name__ == "__main__":
    unittest.main()
