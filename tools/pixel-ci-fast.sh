#!/usr/bin/env bash
set -euo pipefail

# 仓库根目录用于让本脚本可从任意工作目录执行。
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# 测试可注入 Gradle 命令，CI 默认使用受版本控制的 Wrapper。
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
# Python 用于执行快速工具测试与源码凭据扫描。
PYTHON_BIN="${PIXEL_PYTHON_BIN:-python3}"
cd "$ROOT_DIR"

# 清除任务输出后禁用 build cache，确保缺失测试报告不能由旧工作区伪装通过。
"$GRADLEW_BIN" clean --no-build-cache --no-daemon
"$PYTHON_BIN" tools/check_secrets.py --git-history

"$GRADLEW_BIN" \
  :pixel-core:testDebugUnitTest \
  :pixel-runtime:testDebugUnitTest \
  :pixel-widgets:testDebugUnitTest \
  :pixel-navigation:testDebugUnitTest \
  :pixel-testing:testDebugUnitTest \
  :pixel-debug:testDebugUnitTest \
  :pixel-compose:testDebugUnitTest \
  :pixel-engine:testPixelTooling \
  :pixel-engine:testDebugUnitTest \
  :pixel-demo:testDebugUnitTest \
  :app:testDebugUnitTest \
  :pixel-compose-sample:assembleDebug \
  --continue \
  --no-build-cache \
  --no-daemon

echo "Pixel CI fast gate passed."
