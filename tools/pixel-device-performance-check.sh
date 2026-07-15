#!/usr/bin/env bash
set -euo pipefail

# Repository root anchors every source, APK, and report path used by the device gate.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Python remains injectable for deterministic shell failure-propagation tests.
PYTHON_BIN="${PIXEL_PYTHON_BIN:-python3}"
cd "$ROOT_DIR"

# Validates that one required environment variable names an existing non-empty file.
require_evidence_file() {
  # The variable name is kept separate so diagnostics identify the missing release input.
  local variable_name="$1"
  # Bash indirect expansion retrieves the configured evidence path without eval.
  local evidence_path="${!variable_name:-}"
  if [[ -z "$evidence_path" ]]; then
    echo "Missing required device performance variable: $variable_name" >&2
    exit 2
  fi
  if [[ ! -s "$evidence_path" ]]; then
    echo "Missing or empty device performance evidence: $variable_name=$evidence_path" >&2
    exit 2
  fi
}

# Exact raw outputs, binaries, source, profile verification, and approved baseline are mandatory.
REQUIRED_FILE_VARIABLES=(
  PIXEL_DEVICE_MACRO_JSON
  PIXEL_DEVICE_MICRO_JSON
  PIXEL_DEVICE_MACRO_APK
  PIXEL_DEVICE_TARGET_APK
  PIXEL_DEVICE_MICRO_APK
  PIXEL_DEVICE_PROFILE_REPORT
  PIXEL_DEVICE_APPROVED_BASELINE
)
for variable_name in "${REQUIRED_FILE_VARIABLES[@]}"; do
  require_evidence_file "$variable_name"
done

# Measurement identity prevents an anonymous result from becoming release evidence.
MEASUREMENT_ID="${PIXEL_DEVICE_MEASUREMENT_ID:-}"
if [[ -z "$MEASUREMENT_ID" ]]; then
  echo "Missing required device performance variable: PIXEL_DEVICE_MEASUREMENT_ID" >&2
  exit 2
fi

# Active physical refresh rate is required because all frame thresholds derive from its period.
REFRESH_RATE_HZ="${PIXEL_DEVICE_REFRESH_RATE_HZ:-}"
if [[ -z "$REFRESH_RATE_HZ" ]]; then
  echo "Missing required device performance variable: PIXEL_DEVICE_REFRESH_RATE_HZ" >&2
  exit 2
fi

# The default report path is stable for CI collection and release audit documents.
OUTPUT_REPORT="${PIXEL_DEVICE_OUTPUT_REPORT:-build/reports/performance/device-gate/device-performance-gate.json}"
# Source-level compilation evidence is part of the benchmark configuration identity.
MACRO_SOURCE="${PIXEL_DEVICE_MACRO_SOURCE:-pixel-benchmark/src/main/kotlin/com/purride/pixelbenchmark/PixelMacrobenchmark.kt}"
# Optional report-only mode collects honest failed evidence without weakening report status.
REPORT_ONLY="${PIXEL_DEVICE_REPORT_ONLY:-0}"

# Arguments are stored as an array so paths with spaces remain exact and non-executable.
GATE_ARGUMENTS=(
  --macro-json "$PIXEL_DEVICE_MACRO_JSON"
  --micro-json "$PIXEL_DEVICE_MICRO_JSON"
  --macro-source "$MACRO_SOURCE"
  --macro-apk "$PIXEL_DEVICE_MACRO_APK"
  --target-apk "$PIXEL_DEVICE_TARGET_APK"
  --micro-apk "$PIXEL_DEVICE_MICRO_APK"
  --baseline-profile-report "$PIXEL_DEVICE_PROFILE_REPORT"
  --baseline "$PIXEL_DEVICE_APPROVED_BASELINE"
  --output "$OUTPUT_REPORT"
  --measurement-id "$MEASUREMENT_ID"
  --refresh-rate-hz "$REFRESH_RATE_HZ"
)
if [[ "$REPORT_ONLY" == "1" ]]; then
  GATE_ARGUMENTS+=(--report-only)
fi

"$PYTHON_BIN" tools/check_device_benchmarks.py "${GATE_ARGUMENTS[@]}"
