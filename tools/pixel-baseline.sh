#!/usr/bin/env bash
set -euo pipefail

# Repository root is resolved from this script so callers can run it from any working directory.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# External executables are injectable for deterministic shell failure-contract tests.
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
PYTHON_BIN="${PIXEL_PYTHON_BIN:-python3}"
# Stable locale prevents decimal and sorting differences in text reports parsed into baseline JSON.
export LC_ALL=C

cd "$ROOT_DIR"

# A default clean run proves reports are fresh; local iteration can opt out explicitly without changing CI behavior.
if [[ "${PIXEL_BASELINE_SKIP_CLEAN:-0}" != "1" ]]; then
  "$GRADLEW_BIN" clean --no-daemon
fi

# Build every artifact and JUnit report consumed by collect_pixel_baseline.py.
"$GRADLEW_BIN" \
  :pixel-engine:checkBinaryApi \
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

# Produce redacted security evidence for the current tree and all release/debug Android archives.
"$PYTHON_BIN" tools/check_secrets.py \
  --path pixel-engine/build/outputs/aar \
  --path app/build/outputs/apk

# Compile-time resource merging can change XML semantics, so inspect both produced APKs directly.
"$PYTHON_BIN" tools/verify_backup_contract.py \
  --apk app/build/outputs/apk/debug/app-debug.apk \
  --apk app/build/outputs/apk/release/app-release-unsigned.apk

"$PYTHON_BIN" tools/collect_pixel_baseline.py
