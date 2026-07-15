#!/usr/bin/env bash
set -euo pipefail

# 所有源码、构建和报告路径都以仓库根目录为锚点。
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# 设备长跑必须由调用方显式提供唯一 adb 序列号。
TARGET_SERIAL="${PIXEL_BENCHMARK_SERIAL:-}"
# 正式设备长跑默认执行 Goal 最短要求的 30 分钟。
DURATION_SECONDS="${PIXEL_SOAK_DURATION_SECONDS:-1800}"
# 默认每分钟采集一次目标进程终态内存。
SAMPLE_INTERVAL_SECONDS="${PIXEL_SOAK_SAMPLE_INTERVAL_SECONDS:-60}"
# 本地接线可显式允许短跑，但报告永远不会被 checker 认作正式 Goal 证据。
ALLOW_SHORT="${PIXEL_SOAK_ALLOW_SHORT:-0}"
# Python 解释器允许 CI 或测试注入，默认使用当前 PATH 的 python3。
PYTHON_BIN="${PIXEL_PYTHON_BIN:-python3}"
# 专用 instrumentation 类名确保普通 Macrobenchmark 与长跑完全隔离。
SOAK_TEST_CLASS="com.purride.pixelbenchmark.PixelDeviceSoakInstrumentedTest"
# AndroidX additional output 的固定宿主根目录。
ADDITIONAL_OUTPUT_ROOT="$ROOT_DIR/pixel-benchmark/build/outputs/connected_android_test_additional_output"
# 受审设备长跑报告统一留存在仓库 build 目录。
REPORT_DIR="$ROOT_DIR/build/reports/performance/device-soak"
# 原始 instrumentation JSON 的稳定归档路径。
RAW_REPORT="$REPORT_DIR/pixel-device-soak-report.json"
# 主机 checker 输出的稳定验收摘要路径。
CHECK_REPORT="$REPORT_DIR/pixel-device-soak-check.json"

# 缺少序列号时不得依赖 adb 默认设备。
if [[ -z "$TARGET_SERIAL" ]]; then
  echo "缺少 PIXEL_BENCHMARK_SERIAL；设备长跑不会选择默认设备。" >&2
  exit 2
fi
# 所有数值参数必须是十进制正整数，避免 runner 收到截断或 shell 表达式。
if [[ ! "$DURATION_SECONDS" =~ ^[0-9]+$ ]] || [[ "$DURATION_SECONDS" -le 0 ]]; then
  echo "PIXEL_SOAK_DURATION_SECONDS 必须是正整数。" >&2
  exit 2
fi
if [[ ! "$SAMPLE_INTERVAL_SECONDS" =~ ^[0-9]+$ ]] || [[ "$SAMPLE_INTERVAL_SECONDS" -le 0 ]]; then
  echo "PIXEL_SOAK_SAMPLE_INTERVAL_SECONDS 必须是正整数。" >&2
  exit 2
fi
if [[ "$ALLOW_SHORT" != "0" && "$ALLOW_SHORT" != "1" ]]; then
  echo "PIXEL_SOAK_ALLOW_SHORT 只允许为 0 或 1。" >&2
  exit 2
fi
# 60 分钟是 Goal 的明确上限；普通正式调用同时要求至少 30 分钟。
if [[ "$DURATION_SECONDS" -gt 3600 ]]; then
  echo "设备长跑不能超过 3600 秒。" >&2
  exit 2
fi
if [[ "$ALLOW_SHORT" != "1" && "$DURATION_SECONDS" -lt 1800 ]]; then
  echo "正式设备长跑至少需要 1800 秒；接线验证需显式设置 PIXEL_SOAK_ALLOW_SHORT=1。" >&2
  exit 2
fi

cd "$ROOT_DIR"
# 先运行精确 10,000 次 retained/route/Host-like 生命周期和资源压力测试。
bash tools/pixel-soak-test.sh

# 清除本模块旧 connected 结果，防止 checker 误选陈旧同名报告。
rm -rf \
  "$ROOT_DIR/pixel-benchmark/build/outputs/androidTest-results/connected" \
  "$ADDITIONAL_OUTPUT_ROOT"

# wrapper 会再次核对 qemu、宿主序列号和 instrumentation 设备硬件序列号。
PIXEL_BENCHMARK_TEST_CLASS="$SOAK_TEST_CLASS" \
  PIXEL_BENCHMARK_SERIAL="$TARGET_SERIAL" \
  bash tools/pixel-connected-benchmark.sh \
    :pixel-benchmark:connectedBenchmarkReleaseAndroidTest \
    -Ppixel.soak.enabled=true \
    "-Ppixel.soak.durationSeconds=$DURATION_SECONDS" \
    "-Ppixel.soak.sampleIntervalSeconds=$SAMPLE_INTERVAL_SECONDS" \
    --no-build-cache \
    --no-daemon

# additional output 中必须且只能出现一份本次长跑报告；不用 mapfile 以兼容 macOS Bash 3.2。
SOAK_REPORT="$(find "$ADDITIONAL_OUTPUT_ROOT" -type f -name 'pixel-device-soak-report.json' -print | sort)"
SOAK_REPORT_COUNT="$(find "$ADDITIONAL_OUTPUT_ROOT" -type f -name 'pixel-device-soak-report.json' -print | wc -l | tr -d ' ')"
if [[ "$SOAK_REPORT_COUNT" -ne 1 ]]; then
  echo "预期一份设备长跑报告，实际找到 ${SOAK_REPORT_COUNT} 份。" >&2
  exit 1
fi

# 归档原始报告后执行独立主机复算；短跑只验证 schema，不要求 qualified。
mkdir -p "$REPORT_DIR"
cp "$SOAK_REPORT" "$RAW_REPORT"
CHECK_ARGUMENTS=(
  --report "$RAW_REPORT"
  --output "$CHECK_REPORT"
)
if [[ "$ALLOW_SHORT" != "1" ]]; then
  CHECK_ARGUMENTS+=(--require-qualified)
fi
"$PYTHON_BIN" tools/check_device_soak.py "${CHECK_ARGUMENTS[@]}"

echo "设备长跑原始报告：$RAW_REPORT"
echo "设备长跑验收摘要：$CHECK_REPORT"
