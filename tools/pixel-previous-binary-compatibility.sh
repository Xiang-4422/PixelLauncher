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
# 当前独立 core AAR，用于确认旧消费者运行时解析到精确的拆分产物。
CURRENT_CORE_AAR="$ROOT_DIR/pixel-core/build/outputs/aar/pixel-core-release.aar"
# 当前独立 runtime AAR，用于确认聚合 POM 传递解析到精确的运行时产物。
CURRENT_RUNTIME_AAR="$ROOT_DIR/pixel-runtime/build/outputs/aar/pixel-runtime-release.aar"
# 当前独立 widgets AAR，用于确认旧消费者运行时使用拆分后的标准组件产物。
CURRENT_WIDGETS_AAR="$ROOT_DIR/pixel-widgets/build/outputs/aar/pixel-widgets-release.aar"
# 当前独立 navigation AAR，用于确认旧消费者运行时使用拆分后的路由产物。
CURRENT_NAVIGATION_AAR="$ROOT_DIR/pixel-navigation/build/outputs/aar/pixel-navigation-release.aar"
# 当前独立 android AAR，用于确认旧消费者运行时使用拆分后的平台宿主产物。
CURRENT_ANDROID_AAR="$ROOT_DIR/pixel-android/build/outputs/aar/pixel-android-release.aar"
# 当前独立 testing AAR，用于确认旧消费者运行时使用拆分后的测试工具产物。
CURRENT_TESTING_AAR="$ROOT_DIR/pixel-testing/build/outputs/aar/pixel-testing-release.aar"
# 当前独立 debug AAR，用于确认旧消费者运行时使用拆分后的诊断 UI 产物。
CURRENT_DEBUG_AAR="$ROOT_DIR/pixel-debug/build/outputs/aar/pixel-debug-release.aar"
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
  :pixel-core:publishReleasePublicationToCompatibilityRepository \
  :pixel-runtime:publishReleasePublicationToCompatibilityRepository \
  :pixel-widgets:publishReleasePublicationToCompatibilityRepository \
  :pixel-navigation:publishReleasePublicationToCompatibilityRepository \
  :pixel-android:publishReleasePublicationToCompatibilityRepository \
  :pixel-testing:publishReleasePublicationToCompatibilityRepository \
  :pixel-debug:publishReleasePublicationToCompatibilityRepository \
  :pixel-engine:publishReleasePublicationToCompatibilityRepository \
  --no-daemon

"$GRADLEW_BIN" \
  -p "$CURRENT_RUNNER_BUILD" \
  -PpixelCompatibilityRepository="$CURRENT_REPOSITORY" \
  -PpixelEngineVersion=1.0.0 \
  -PlegacyConsumerAar="$OLD_CONSUMER_AAR" \
  -PbaselineEngineAar="$BASELINE_AAR" \
  -PcurrentEngineAar="$CURRENT_AAR" \
  -PcurrentCoreAar="$CURRENT_CORE_AAR" \
  -PcurrentRuntimeAar="$CURRENT_RUNTIME_AAR" \
  -PcurrentWidgetsAar="$CURRENT_WIDGETS_AAR" \
  -PcurrentNavigationAar="$CURRENT_NAVIGATION_AAR" \
  -PcurrentAndroidAar="$CURRENT_ANDROID_AAR" \
  -PcurrentTestingAar="$CURRENT_TESTING_AAR" \
  -PcurrentDebugAar="$CURRENT_DEBUG_AAR" \
  -PruntimeClasspathReport="$RUNTIME_CLASSPATH_REPORT" \
  :runner:testDebugUnitTest \
  --no-daemon

echo "Previous consumer binary compatibility check passed."
