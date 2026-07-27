#!/usr/bin/env bash
set -euo pipefail

# Repository root containing the producer and both isolated compatibility builds.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Wrapper path is injectable only for tooling failure-propagation tests.
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
# Immutable engine artifact used only by the old consumer compiler.
BASELINE_AAR="$ROOT_DIR/compatibility/previous-binary/baselines/spi-v1/pixel-engine-spi-v1.aar"
# Reviewed checksum proving the baseline artifact has not changed.
BASELINE_HASH="$BASELINE_AAR.sha256"
# Isolated build that compiles the legacy consumer against the frozen AAR.
OLD_CONSUMER_BUILD="$ROOT_DIR/compatibility/previous-binary/old-consumer"
# Old consumer artifact passed to the current-only runtime runner.
OLD_CONSUMER_AAR="$OLD_CONSUMER_BUILD/consumer/build/outputs/aar/consumer-release.aar"
# Isolated build that resolves only the current engine Maven artifact.
CURRENT_RUNNER_BUILD="$ROOT_DIR/compatibility/previous-binary/current-runner"
# Maven repository populated by the producer during this verification run.
CURRENT_REPOSITORY="$ROOT_DIR/build/compatibility-repository"
# Current producer AAR used as the authoritative runtime digest.
CURRENT_AAR="$ROOT_DIR/pixel-engine/build/outputs/aar/pixel-engine-release.aar"
# Stable report proving the old compiler used only the frozen baseline AAR.
OLD_PROVENANCE_REPORT="$ROOT_DIR/build/reports/compatibility/old-consumer-provenance.json"
# Stable report proving the runtime resolves only the current engine AAR.
RUNTIME_CLASSPATH_REPORT="$ROOT_DIR/build/reports/compatibility/runtime-classpath.json"

"$GRADLEW_BIN" \
  -p "$OLD_CONSUMER_BUILD" \
  -PbaselineEngineAar="$BASELINE_AAR" \
  -PbaselineHashFile="$BASELINE_HASH" \
  -PoldConsumerProvenanceReport="$OLD_PROVENANCE_REPORT" \
  clean \
  :consumer:assembleRelease \
  --no-daemon

# Publish the exact current producer independently so this gate never relies on a prior script's
# side effect or a stale artifact left in the shared compatibility repository.
rm -rf "$CURRENT_REPOSITORY"
"$GRADLEW_BIN" \
  :pixel-engine:publishReleasePublicationToCompatibilityRepository \
  --no-daemon

"$GRADLEW_BIN" \
  -p "$CURRENT_RUNNER_BUILD" \
  -PpixelCompatibilityRepository="$CURRENT_REPOSITORY" \
  -PpixelEngineVersion=1.0.0 \
  -PlegacyConsumerAar="$OLD_CONSUMER_AAR" \
  -PbaselineEngineAar="$BASELINE_AAR" \
  -PcurrentEngineAar="$CURRENT_AAR" \
  -PruntimeClasspathReport="$RUNTIME_CLASSPATH_REPORT" \
  :runner:testDebugUnitTest \
  --no-daemon

echo "Previous consumer binary compatibility check passed."
