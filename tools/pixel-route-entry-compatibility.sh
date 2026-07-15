#!/usr/bin/env bash
set -euo pipefail

# Repository root containing both the SDK producer and the independent consumer build.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Wrapper path remains injectable for repository tooling failure-propagation tests.
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
# Script-owned Maven repository populated by exactly one current release publication.
COMPATIBILITY_REPOSITORY="$ROOT_DIR/build/compatibility-repository"
# Independent Gradle build that has no project dependency on the SDK producer.
ROUTE_ENTRY_CONSUMER="$ROOT_DIR/compatibility/route-entry-consumer"
# Directory containing durable positive, negative, JSON, and text evidence.
REPORT_DIR="$ROOT_DIR/build/reports/compatibility"
# Machine-readable aggregate compatibility report.
JSON_REPORT="$REPORT_DIR/route-entry-consumer.json"
# Human-readable aggregate compatibility report.
TEXT_REPORT="$REPORT_DIR/route-entry-consumer.txt"
# Positive external consumer execution log.
POSITIVE_LOG="$REPORT_DIR/route-entry-positive.log"
# Shared engine coordinate resolved by every isolated consumer module.
PIXEL_ENGINE_VERSION="1.0.0"

mkdir -p "$REPORT_DIR"
rm -rf "$COMPATIBILITY_REPOSITORY"
rm -f \
  "$JSON_REPORT" \
  "$TEXT_REPORT" \
  "$POSITIVE_LOG" \
  "$REPORT_DIR"/route-entry-negative-*.log

# Reject accidental shortcuts that would bypass the published AAR boundary.
if grep -R -n -E 'mavenLocal\(|project\(' "$ROUTE_ENTRY_CONSUMER" --include='*.gradle.kts'; then
  echo "Route-entry compatibility fixture must not use mavenLocal() or project dependencies." >&2
  exit 1
fi

# Reject imports of the implementation navigation package in external Kotlin consumers.
if grep -R -n -F 'com.purride.pixelui.widgets.navigation' "$ROUTE_ENTRY_CONSUMER" --include='*.kt'; then
  echo "Route-entry compatibility fixture must consume navigation API from com.purride.pixelui." >&2
  exit 1
fi

"$GRADLEW_BIN" \
  :pixel-core:publishReleasePublicationToCompatibilityRepository \
  :pixel-runtime:publishReleasePublicationToCompatibilityRepository \
  :pixel-widgets:publishReleasePublicationToCompatibilityRepository \
  :pixel-navigation:publishReleasePublicationToCompatibilityRepository \
  :pixel-android:publishReleasePublicationToCompatibilityRepository \
  :pixel-testing:publishReleasePublicationToCompatibilityRepository \
  :pixel-debug:publishReleasePublicationToCompatibilityRepository \
  :pixel-engine:publishReleasePublicationToCompatibilityRepository \
  --no-daemon

# Snapshot version directory containing the timestamped Maven publication payload.
PUBLISHED_VERSION_DIR="$COMPATIBILITY_REPOSITORY/com/purride/pixel-engine/$PIXEL_ENGINE_VERSION"
# Maven snapshots use timestamped physical names, so discover the single published AAR explicitly.
shopt -s nullglob
PUBLISHED_AARS=("$PUBLISHED_VERSION_DIR"/*.aar)
shopt -u nullglob
if [[ "${#PUBLISHED_AARS[@]}" -ne 1 ]]; then
  echo "Expected exactly one published pixel-engine AAR in $PUBLISHED_VERSION_DIR." >&2
  exit 1
fi
# Concrete AAR path proves that publication produced an artifact before consumer resolution.
PUBLISHED_AAR="${PUBLISHED_AARS[0]}"
# Content hash identifies the exact AAR exercised by the external consumer run.
PUBLISHED_AAR_SHA256="$(shasum -a 256 "$PUBLISHED_AAR" | awk '{print $1}')"
# Timestamped physical filename records the Maven snapshot payload selected for this run.
PUBLISHED_AAR_NAME="$(basename "$PUBLISHED_AAR")"

# Run the external PixelTester behavior contract before exercising expected compile failures.
if ! "$GRADLEW_BIN" \
  -p "$ROUTE_ENTRY_CONSUMER" \
  -PpixelCompatibilityRepository="$COMPATIBILITY_REPOSITORY" \
  -PpixelEngineVersion="$PIXEL_ENGINE_VERSION" \
  :positive:testDebugUnitTest \
  --refresh-dependencies \
  --rerun-tasks \
  --no-daemon \
  >"$POSITIVE_LOG" 2>&1; then
  echo "Positive route-entry consumer failed; see $POSITIVE_LOG" >&2
  exit 1
fi

# Runs one intentionally invalid consumer and verifies that its compiler failure names the expected
# source. Arguments are the Gradle module name and Kotlin source file name.
run_expected_type_failure() {
  # Gradle module containing exactly one intentional type violation.
  local module_name="$1"
  # Source basename that must appear in the Kotlin compiler diagnostic.
  local source_name="$2"
  # Durable compiler log used as negative compatibility evidence.
  local log_file="$REPORT_DIR/route-entry-${module_name}.log"
  # Exit status captured without allowing `set -e` to terminate before validation.
  local compile_status

  set +e
  "$GRADLEW_BIN" \
    -p "$ROUTE_ENTRY_CONSUMER" \
    -PpixelCompatibilityRepository="$COMPATIBILITY_REPOSITORY" \
    -PpixelEngineVersion="$PIXEL_ENGINE_VERSION" \
    ":$module_name:compileDebugKotlin" \
    --rerun-tasks \
    --no-daemon \
    >"$log_file" 2>&1
  compile_status=$?
  set -e

  if [[ "$compile_status" -eq 0 ]]; then
    echo "Expected :$module_name:compileDebugKotlin to fail, but it succeeded." >&2
    exit 1
  fi
  if ! grep -F "$source_name" "$log_file" >/dev/null; then
    echo "Expected compiler diagnostic for $source_name; see $log_file" >&2
    exit 1
  fi
  if ! grep -E -i '(argument type mismatch|type mismatch)' "$log_file" >/dev/null; then
    echo "Expected a Kotlin type-mismatch diagnostic for $source_name; see $log_file" >&2
    exit 1
  fi
}

run_expected_type_failure "negative-wrong-argument" "WrongArgumentType.kt"
run_expected_type_failure "negative-wrong-complete" "WrongCompleteResultType.kt"
run_expected_type_failure "negative-wrong-replace" "WrongReplaceResultType.kt"

cat >"$JSON_REPORT" <<EOF
{"status":"passed","artifact":"com.purride:pixel-engine:$PIXEL_ENGINE_VERSION","artifactFile":"$PUBLISHED_AAR_NAME","artifactSha256":"$PUBLISHED_AAR_SHA256","repository":"exclusive-file-maven","positive":{"task":":positive:testDebugUnitTest","status":"passed","pixelTester":true},"negative":[{"module":"negative-wrong-argument","source":"WrongArgumentType.kt","status":"expected_type_failure","diagnostic":"argument type mismatch"},{"module":"negative-wrong-complete","source":"WrongCompleteResultType.kt","status":"expected_type_failure","diagnostic":"argument type mismatch"},{"module":"negative-wrong-replace","source":"WrongReplaceResultType.kt","status":"expected_type_failure","diagnostic":"argument type mismatch"}]}
EOF

cat >"$TEXT_REPORT" <<EOF
Pixel route-entry external consumer compatibility: PASSED
Artifact: com.purride:pixel-engine:$PIXEL_ENGINE_VERSION
Artifact file: $PUBLISHED_AAR_NAME
Artifact SHA-256: $PUBLISHED_AAR_SHA256
Repository boundary: exclusive isolated file Maven repository
Positive PixelTester behavior: PASSED
Negative wrong argument type: EXPECTED COMPILE FAILURE
Negative scope.complete result type: EXPECTED COMPILE FAILURE
Negative scope.replaceWith result type: EXPECTED COMPILE FAILURE
EOF

echo "Route-entry compatibility check passed."
echo "JSON report: $JSON_REPORT"
echo "Text report: $TEXT_REPORT"
