#!/usr/bin/env bash
set -euo pipefail

# 仓库根目录用于定位 producer、隔离仓库和报告校验器。
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# CI 默认使用仓库 Wrapper。
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
# Python 用于结构化检查发布物内容。
PYTHON_BIN="${PIXEL_PYTHON_BIN:-python3}"
# 每次调用独占并重建的 Maven 仓库，测试可覆盖到临时目录。
COMPATIBILITY_REPOSITORY="${PIXEL_COMPATIBILITY_REPOSITORY:-$ROOT_DIR/build/compatibility-repository}"
# publication job 的稳定机读报告，测试可覆盖以保护真实证据。
PUBLICATION_REPORT="${PIXEL_PUBLICATION_REPORT:-$ROOT_DIR/build/reports/compatibility/publication.json}"
cd "$ROOT_DIR"

# 删除仓库和报告，确保 Gradle cache 不能用旧 SNAPSHOT 或旧成功 JSON 掩盖缺失产物。
rm -rf "$COMPATIBILITY_REPOSITORY"
rm -f "$PUBLICATION_REPORT"

"$GRADLEW_BIN" \
  :pixel-engine:publishReleasePublicationToCompatibilityRepository \
  --no-build-cache \
  --no-daemon

"$PYTHON_BIN" tools/check_pixel_publication.py \
  --repository "$COMPATIBILITY_REPOSITORY" \
  --version "1.0.0" \
  --report "$PUBLICATION_REPORT"

echo "Pixel publication CI gate passed: $PUBLICATION_REPORT"
