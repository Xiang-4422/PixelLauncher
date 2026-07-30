#!/usr/bin/env bash
set -euo pipefail

# 仓库根目录用于保证脚本可从任意工作目录执行。
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# 允许 CI 注入 Gradle 命令，默认使用仓库内的 Wrapper。
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"

cd "$ROOT_DIR"

# JVM 套件覆盖无设备副作用的核心状态旅程；androidTest 仅编译，绝不连接或修改真实设备。
"$GRADLEW_BIN" \
  :app:testDebugUnitTest \
  :app:assembleDebugAndroidTest \
  --no-daemon

echo "Pixel Launcher app core acceptance gate passed."
