#!/usr/bin/env bash
set -euo pipefail

# 所有路径都以仓库根目录为锚点，避免从其他工作目录运行时选错工程。
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# 测试可注入 adb 替身，生产调用默认使用 Android SDK 的 adb。
ADB_BIN="${PIXEL_ADB_BIN:-adb}"
# 测试可注入 Gradle 替身，生产调用默认使用仓库 Gradle Wrapper。
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
# 每次调用都必须显式指定唯一 adb 序列号，禁止依赖当前默认设备。
TARGET_SERIAL="${PIXEL_BENCHMARK_SERIAL:-}"
# 实体设备默认拒绝，只有专门的真实设备采集调用才能逐次开启。
ALLOW_PHYSICAL="${PIXEL_BENCHMARK_ALLOW_PHYSICAL:-0}"
# 可选测试类由宿主显式注入，普通基准与长跑不会在同一次 instrumentation 中混跑。
TARGET_TEST_CLASS="${PIXEL_BENCHMARK_TEST_CLASS:-}"

# 校验调用参数，避免空任务或未绑定设备的 connected test 开始部署。
if [[ -z "$TARGET_SERIAL" ]]; then
  echo "缺少 PIXEL_BENCHMARK_SERIAL；基准测试不会选择默认设备。" >&2
  exit 2
fi
if [[ "$ALLOW_PHYSICAL" != "0" && "$ALLOW_PHYSICAL" != "1" ]]; then
  echo "PIXEL_BENCHMARK_ALLOW_PHYSICAL 只允许为 0 或 1。" >&2
  exit 2
fi
if [[ "$#" -eq 0 ]]; then
  echo "用法：PIXEL_BENCHMARK_SERIAL=<serial> tools/pixel-connected-benchmark.sh <Gradle task> [参数...]" >&2
  exit 2
fi

# 只查询显式目标设备的连接状态，不枚举或操作其他已连接设备。
TARGET_STATE="$($ADB_BIN -s "$TARGET_SERIAL" get-state 2>/dev/null || true)"
if [[ "$TARGET_STATE" != "device" ]]; then
  echo "目标设备不可用：${TARGET_SERIAL}（状态：${TARGET_STATE:-missing}）。" >&2
  exit 2
fi

# 硬件序列号会注入 instrumentation，并在设备端动作前与当前设备复核。
HARDWARE_SERIAL="$($ADB_BIN -s "$TARGET_SERIAL" shell getprop ro.serialno | tr -d '\r' | xargs)"
if [[ -z "$HARDWARE_SERIAL" ]]; then
  echo "无法读取目标设备硬件序列号：${TARGET_SERIAL}。" >&2
  exit 2
fi

# qemu 属性比型号命名更可靠，可防止普通实体设备被模拟器任务误选。
IS_EMULATOR="$($ADB_BIN -s "$TARGET_SERIAL" shell getprop ro.kernel.qemu | tr -d '\r' | xargs)"
if [[ "$IS_EMULATOR" != "1" && "$ALLOW_PHYSICAL" != "1" ]]; then
  echo "拒绝隐式操作实体设备 ${TARGET_SERIAL}；专门采集时显式设置 PIXEL_BENCHMARK_ALLOW_PHYSICAL=1。" >&2
  exit 2
fi

# ANDROID_SERIAL 与 AGP 注入属性共同把部署和测试限制在同一目标设备。
export ANDROID_SERIAL="$TARGET_SERIAL"
# Gradle 固定参数数组始终非空，兼容 macOS Bash 3.2 在 `set -u` 下的数组展开语义。
GRADLE_ARGUMENTS=(
  "-Pandroid.injected.device.serial=$TARGET_SERIAL"
  "-Ppixel.benchmark.expectedHardwareSerial=$HARDWARE_SERIAL"
  "-Ppixel.benchmark.allowPhysical=$([[ "$ALLOW_PHYSICAL" == "1" ]] && echo true || echo false)"
)
# 只有调用方提供类名时才增加过滤属性，保留既有 wrapper 的通用任务兼容性。
if [[ -n "$TARGET_TEST_CLASS" ]]; then
  GRADLE_ARGUMENTS+=("-Ppixel.benchmark.testClass=$TARGET_TEST_CLASS")
fi
# 调用方提供的 Gradle 任务及参数保持原顺序追加。
GRADLE_ARGUMENTS+=("$@")
cd "$ROOT_DIR"
"$GRADLEW_BIN" "${GRADLE_ARGUMENTS[@]}"
