#!/usr/bin/env bash
set -euo pipefail

# Repository root containing both the SDK producer and isolated consumer build.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Wrapper path is injectable only for tooling failure-propagation tests.
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
# Disposable Maven repository populated by exactly one producer build.
COMPATIBILITY_REPOSITORY="$ROOT_DIR/build/compatibility-repository"
# Independent Gradle build that consumes only the Maven artifact above.
EXTERNAL_CONSUMER="$ROOT_DIR/compatibility/external-render-spi"
# Stable repository-level report proving the external bytecode boundary.
EXTERNAL_BYTECODE_REPORT="$ROOT_DIR/build/reports/compatibility/external-spi-bytecode.json"

rm -rf "$COMPATIBILITY_REPOSITORY"

"$GRADLEW_BIN" \
  :pixel-engine:publishReleasePublicationToCompatibilityRepository \
  --no-daemon

"$GRADLEW_BIN" \
  -p "$EXTERNAL_CONSUMER" \
  -PpixelCompatibilityRepository="$COMPATIBILITY_REPOSITORY" \
  -PpixelEngineVersion=1.0.0 \
  -PpixelCompatibilityReport="$EXTERNAL_BYTECODE_REPORT" \
  :consumer:testDebugUnitTest \
  :consumer:checkNoInternalBytecodeReferences \
  --no-daemon

echo "External RenderObject SPI compatibility check passed."
