#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Tests can inject a deterministic failing executable to prove shell exit propagation.
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
cd "$ROOT_DIR"

"$GRADLEW_BIN" \
  :pixel-engine:testDebugUnitTest \
  --tests 'com.purride.pixelui.regression.EngineLifecycleSoakTest' \
  --tests 'com.purride.pixelui.regression.EngineResourceLifecycleStressTest' \
  --no-daemon
