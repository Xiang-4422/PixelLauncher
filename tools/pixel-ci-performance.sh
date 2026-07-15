#!/usr/bin/env bash
set -euo pipefail

# 仓库根目录用于定位性能 baseline、报告和 benchmark APK。
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Bash 可注入以验证任一性能子门禁失败都会停止聚合。
BASH_BIN="${PIXEL_BASH_BIN:-bash}"
cd "$ROOT_DIR"

# 各子脚本先删除自己的稳定报告并验证本轮 runId/产物，拒绝旧缓存假绿。
"$BASH_BIN" tools/pixel-perf-smoke.sh
"$BASH_BIN" tools/pixel-baseline-profile-check.sh

echo "Pixel CI performance gate passed."
