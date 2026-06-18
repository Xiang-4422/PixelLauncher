#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

./gradlew \
  :pixel-engine:testDebugUnitTest \
  --tests 'com.purride.pixelui.regression.EnginePerformanceSmokeTest' \
  --no-daemon

REPORT="pixel-engine/build/reports/perf/pixel-engine-render-smoke.txt"
if [[ ! -f "$REPORT" ]]; then
  echo "Missing perf smoke report: $REPORT" >&2
  exit 1
fi

echo
echo "Perf smoke report: $REPORT"
cat "$REPORT"
