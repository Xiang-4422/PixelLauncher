#!/usr/bin/env bash
set -euo pipefail

# Repository root makes every build and report path independent of the caller's cwd.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Executables are injectable so tooling tests can prove shell failure propagation without recursive builds.
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
# Python is injectable for hermetic CI images and gate failure-propagation tests.
PYTHON_BIN="${PIXEL_PYTHON_BIN:-python3}"
# The report is removed first so a failed build cannot leave stale passing evidence behind.
REPORT="$ROOT_DIR/build/reports/performance/baseline-profile-packaging.json"
cd "$ROOT_DIR"

rm -f "$REPORT"

# Build the publishable AAR plus minified release and signed release-like benchmark consumers.
"$GRADLEW_BIN" \
  :pixel-engine:assembleRelease \
  :pixel-benchmark-target:assembleRelease \
  :pixel-benchmark-target:assembleBenchmark \
  --no-daemon \
  -Dkotlin.compiler.execution.strategy=in-process

# Validate source ownership, exact AAR text-profile packaging, and binary APK profile installation assets.
"$PYTHON_BIN" tools/verify_baseline_profile.py \
  --engine-profile pixel-engine/src/main/baselineProfiles/baseline-prof.txt \
  --consumer-profile pixel-benchmark-target/src/main/baselineProfiles/baseline-prof.txt \
  --startup-profile pixel-benchmark-target/src/main/baselineProfiles/startup-prof.txt \
  --engine-aar pixel-engine/build/outputs/aar/pixel-engine-release.aar \
  --consumer-apk pixel-benchmark-target/build/outputs/apk/release/pixel-benchmark-target-release-unsigned.apk \
  --consumer-apk pixel-benchmark-target/build/outputs/apk/benchmark/pixel-benchmark-target-benchmark.apk \
  --report "$REPORT"

echo
echo "Baseline Profile packaging report: $REPORT"
