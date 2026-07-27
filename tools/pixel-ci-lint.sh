#!/usr/bin/env bash
set -euo pipefail

# 仓库根目录用于统一运行所有 Android library/application lint。
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# CI 默认使用仓库 Wrapper，测试可注入确定性失败命令。
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
cd "$ROOT_DIR"

# 删除旧 lint model/report 并禁止 build cache 恢复缺失报告。
"$GRADLEW_BIN" clean --no-build-cache --no-daemon
"$GRADLEW_BIN" \
  :pixel-engine:lintDebug \
  :app:lintDebug \
  --continue \
  --no-build-cache \
  --no-daemon

echo "Pixel CI lint gate passed."
