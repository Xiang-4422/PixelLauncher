#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# The Gradle executable is injectable for deterministic shell failure-propagation tests.
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
# Python remains injectable for deterministic trend-check failure-propagation tests.
PYTHON_BIN="${PIXEL_PYTHON_BIN:-python3}"
cd "$ROOT_DIR"

# Stable report path shared with the JVM smoke test and CI artifact collection.
REPORT="pixel-engine/build/reports/perf/pixel-engine-render-smoke.txt"
# The reviewed workload-matched baseline is source controlled and never inferred from the latest run.
TREND_BASELINE="pixel-engine/performance/baselines/jvm-smoke-v2.json"
# The JSON trend result is separate from the raw Kotlin properties report.
TREND_REPORT="build/reports/performance/jvm-smoke-trend.json"
# A unique identifier proves that a report was created by this invocation.
RUN_ID="$(date -u '+%Y%m%dT%H%M%SZ')-$$-${RANDOM}"
# The complete required scene set prevents a silently removed scene from passing.
SCENES=(
  list_scroll
  text_input
  animation
  graphics_primitives
  page_transition
  overlay
)

# Removes any prior report so Gradle or a stale workspace cannot create a false pass.
rm -f "$REPORT"

PIXEL_PERF_RUN_ID="$RUN_ID" "$GRADLEW_BIN" \
  :pixel-engine:testDebugUnitTest \
  --tests 'com.purride.pixelui.regression.EnginePerformanceSmokeTest' \
  --rerun-tasks \
  --no-daemon

# A non-empty report is required even when the Gradle test task itself succeeded.
if [[ ! -s "$REPORT" ]]; then
  echo "Missing perf smoke report: $REPORT" >&2
  exit 1
fi

# Verifies one exact properties-style field in the current report.
require_report_line() {
  # The expected line is kept separate to avoid partial key or value matches.
  local expected_line="$1"
  if ! grep -Fqx "$expected_line" "$REPORT"; then
    echo "Perf smoke report is missing '$expected_line': $REPORT" >&2
    exit 1
  fi
}

require_report_line "formatVersion=2"
require_report_line "runId=$RUN_ID"
require_report_line "sampleBatches=7"
require_report_line "sceneCount=${#SCENES[@]}"
require_report_line "overallPass=true"

for scene in "${SCENES[@]}"; do
  # Every named scene must expose both its threshold and final pass result.
  require_report_line "scene.${scene}.pass=true"
  if ! grep -Eq "^scene\.${scene}\.maxAverageNanos=[1-9][0-9]*$" "$REPORT"; then
    echo "Perf smoke report has no positive threshold for '$scene': $REPORT" >&2
    exit 1
  fi
done

# Compare every scene's seven-batch median to its approved baseline plus the fixed 10% ceiling.
"$PYTHON_BIN" tools/check_jvm_perf_trend.py \
  --report "$REPORT" \
  --baseline "$TREND_BASELINE" \
  --output "$TREND_REPORT"

echo
echo "Perf smoke report: $REPORT"
cat "$REPORT"
echo
echo "Perf smoke trend report: $TREND_REPORT"
cat "$TREND_REPORT"
