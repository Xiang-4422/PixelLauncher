#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# External executables are injectable for non-recursive failure-propagation tests.
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
PYTHON_BIN="${PIXEL_PYTHON_BIN:-python3}"
BASH_BIN="${PIXEL_BASH_BIN:-bash}"
cd "$ROOT_DIR"

# Fail before compilation when the current tree contains a high-confidence credential shape.
"$PYTHON_BIN" tools/check_secrets.py --git-history

# --continue: run every gate task even after one fails, so a single run surfaces
# ALL failures instead of aborting at the first. Gradle still exits non-zero if
# any task failed, so the gate's pass/fail outcome is unchanged.
"$GRADLEW_BIN" \
  :pixel-engine:checkPublicApi \
  :pixel-engine:checkBinaryApi \
  :pixel-engine:checkMetalavaApi \
  :pixel-engine:checkMetalavaReleasedCompatibility \
  :pixel-engine:checkStableApiBoundary \
  :pixel-engine:checkThemeTokenCoverage \
  :pixel-engine:checkUnicodeGraphemeDataGeneration \
  :pixel-engine:checkUnicodeBidiDataGeneration \
  :pixel-engine:checkReleaseArtifactBudget \
  :pixel-engine:checkKdocCoverage \
  :pixel-engine:testPixelTooling \
  :pixel-engine:testDebugUnitTest \
  :pixel-engine:lintDebug \
  :pixel-engine:assembleRelease \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:assembleDebug \
  :app:assembleRelease \
  --continue \
  --no-daemon

# Scan DEX, resources, and nested classes.jar so a generated artifact cannot reintroduce a secret.
"$PYTHON_BIN" tools/check_secrets.py \
  --no-worktree \
  --path pixel-engine/build/outputs/aar \
  --path app/build/outputs/apk \
  --report build/reports/security/artifact-secret-scan.json

# Validate the merged manifest and compiled backup XML in both app variants, not only source files.
"$PYTHON_BIN" tools/verify_backup_contract.py \
  --apk app/build/outputs/apk/debug/app-debug.apk \
  --apk app/build/outputs/apk/release/app-release-unsigned.apk

# 通过隔离构建验证源码、旧二进制与运行时行为兼容性。
"$BASH_BIN" tools/pixel-render-spi-compatibility.sh
"$BASH_BIN" tools/pixel-route-entry-compatibility.sh
"$BASH_BIN" tools/pixel-previous-binary-compatibility.sh
# 发布全部正式坐标，校验 sources/Javadoc/POM/module/AAR metadata，并执行最低/推荐消费者矩阵。
"$BASH_BIN" tools/pixel-consumer-compatibility-matrix.sh
# 正式 release gate 强制验证 Apache-2.0 LICENSE、POM 与 SBOM 一致。
PIXEL_REQUIRE_LICENSE="${PIXEL_REQUIRE_LICENSE:-1}" "$BASH_BIN" tools/pixel-supply-chain-check.sh
# Exercise Android host setup and the published POM/AAR from a separate application consumer.
PIXEL_SKIP_COMPATIBILITY_PUBLISH=1 "$BASH_BIN" tools/pixel-sdk-consumer-smoke.sh
# 只依据 1.0 发布文档组合 Host、路由、自定义 SPI 与测试的全新消费者。
PIXEL_SKIP_COMPATIBILITY_PUBLISH=1 "$BASH_BIN" tools/pixel-docs-consumer-smoke.sh
# 文档工具版本必须与 CI 固定清单一致，避免不同解析器造成链接门禁漂移。
if ! "$PYTHON_BIN" -c 'import importlib.metadata; raise SystemExit(importlib.metadata.version("mkdocs") != "1.6.1")'; then
  "$PYTHON_BIN" -m pip install --user --disable-pip-version-check -r requirements-docs.txt
fi
"$PYTHON_BIN" tools/prepare_mkdocs_docs.py
"$PYTHON_BIN" -m mkdocs build --strict
