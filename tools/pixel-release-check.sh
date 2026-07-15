#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# External executables are injectable for non-recursive failure-propagation tests.
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
PYTHON_BIN="${PIXEL_PYTHON_BIN:-python3}"
BASH_BIN="${PIXEL_BASH_BIN:-bash}"
# 当前 1.0 Goal 明确排除性能专项；后续独立性能 Goal 可显式重新启用原有三项门禁。
RUN_PERFORMANCE_GATES="${PIXEL_RELEASE_INCLUDE_PERFORMANCE_GATES:-0}"
if [[ "$RUN_PERFORMANCE_GATES" != "0" && "$RUN_PERFORMANCE_GATES" != "1" ]]; then
  echo "PIXEL_RELEASE_INCLUDE_PERFORMANCE_GATES 只允许 0 或 1。" >&2
  exit 2
fi
cd "$ROOT_DIR"

# Fail before compilation when the current tree contains a high-confidence credential shape.
"$PYTHON_BIN" tools/check_secrets.py --git-history

# --continue: run every gate task even after one fails, so a single run surfaces
# ALL failures instead of aborting at the first. Gradle still exits non-zero if
# any task failed, so the gate's pass/fail outcome is unchanged.
"$GRADLEW_BIN" \
  :pixel-core:checkBinaryApi \
  :pixel-core:checkMetalavaApi \
  :pixel-core:checkReleaseArtifactBudget \
  :pixel-core:testDebugUnitTest \
  :pixel-core:lintDebug \
  :pixel-core:assembleRelease \
  :pixel-runtime:checkRuntimeBinaryApi \
  :pixel-runtime:checkRuntimeMetalavaApi \
  :pixel-runtime:checkRuntimeReleaseArtifactBudget \
  :pixel-runtime:testDebugUnitTest \
  :pixel-runtime:lintDebug \
  :pixel-runtime:assembleRelease \
  :pixel-widgets:checkWidgetsBinaryApi \
  :pixel-widgets:checkWidgetsMetalavaApi \
  :pixel-widgets:checkWidgetsReleaseArtifactBudget \
  :pixel-widgets:testDebugUnitTest \
  :pixel-widgets:lintDebug \
  :pixel-widgets:assembleRelease \
  :pixel-navigation:checkNavigationBinaryApi \
  :pixel-navigation:checkNavigationMetalavaApi \
  :pixel-navigation:checkNavigationReleaseArtifactBudget \
  :pixel-navigation:testDebugUnitTest \
  :pixel-navigation:lintDebug \
  :pixel-navigation:assembleRelease \
  :pixel-android:checkAndroidBinaryApi \
  :pixel-android:checkAndroidMetalavaApi \
  :pixel-android:checkAndroidReleaseArtifactBudget \
  :pixel-android:lintDebug \
  :pixel-android:assembleRelease \
  :pixel-testing:checkTestingBinaryApi \
  :pixel-testing:checkTestingMetalavaApi \
  :pixel-testing:checkTestingReleaseArtifactBudget \
  :pixel-testing:testDebugUnitTest \
  :pixel-testing:lintDebug \
  :pixel-testing:assembleRelease \
  :pixel-debug:checkDebugBinaryApi \
  :pixel-debug:checkDebugMetalavaApi \
  :pixel-debug:checkDebugReleaseArtifactBudget \
  :pixel-debug:testDebugUnitTest \
  :pixel-debug:lintDebug \
  :pixel-debug:assembleRelease \
  :pixel-compose:checkComposeBinaryApi \
  :pixel-compose:checkComposeMetalavaApi \
  :pixel-compose:checkComposeReleaseArtifactBudget \
  :pixel-compose:testDebugUnitTest \
  :pixel-compose:lintDebug \
  :pixel-compose:assembleRelease \
  :pixel-compose-sample:assembleDebug \
  :pixel-compose-sample:assembleRelease \
  :pixel-engine:checkPublicApi \
  :pixel-engine:checkBinaryApi \
  :pixel-engine:checkMetalavaApi \
  :pixel-engine:checkMetalavaReleasedCompatibility \
  :pixel-engine:checkStableApiBoundary \
  :pixel-engine:checkArtifactBoundaries \
  :pixel-engine:checkThemeTokenCoverage \
  :pixel-engine:checkUnicodeGraphemeDataGeneration \
  :pixel-engine:checkUnicodeBidiDataGeneration \
  :pixel-engine:checkReleaseArtifactBudget \
  :pixel-engine:checkKdocCoverage \
  :pixel-engine:testPixelTooling \
  :pixel-engine:testDebugUnitTest \
  :pixel-engine:lintDebug \
  :pixel-engine:assembleRelease \
  :pixel-demo:assembleDebug \
  :pixel-demo:testDebugUnitTest \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:assembleDebug \
  :app:assembleRelease \
  --continue \
  --no-daemon

# Scan DEX, resources, and nested classes.jar so a generated artifact cannot reintroduce a secret.
"$PYTHON_BIN" tools/check_secrets.py \
  --no-worktree \
  --path pixel-engine/build/outputs/aar \
  --path app/build/outputs/apk \
  --report build/reports/security/artifact-secret-scan.json

# Validate the merged manifest and compiled backup XML in both app variants, not only source files.
"$PYTHON_BIN" tools/verify_backup_contract.py \
  --apk app/build/outputs/apk/debug/app-debug.apk \
  --apk app/build/outputs/apk/release/app-release-unsigned.apk

# Prove source, bytecode-boundary, old-binary, and runtime behavior compatibility in isolated builds.
"$BASH_BIN" tools/pixel-render-spi-compatibility.sh
"$BASH_BIN" tools/pixel-route-entry-compatibility.sh
"$BASH_BIN" tools/pixel-previous-binary-compatibility.sh
# 发布全部正式坐标，校验 sources/Javadoc/POM/module/AAR metadata，并执行最低/推荐消费者矩阵。
"$BASH_BIN" tools/pixel-consumer-compatibility-matrix.sh
# 正式 release gate 强制验证 Apache-2.0 LICENSE、POM 与 SBOM 一致。
PIXEL_REQUIRE_LICENSE="${PIXEL_REQUIRE_LICENSE:-1}" "$BASH_BIN" tools/pixel-supply-chain-check.sh
# Exercise Android host setup and the published POM/AAR from a separate application consumer.
PIXEL_SKIP_COMPATIBILITY_PUBLISH=1 "$BASH_BIN" tools/pixel-sdk-consumer-smoke.sh
# 只依赖 pixel-core 的消费者不得解析到 UI/testing/debug/compose artifact。
PIXEL_SKIP_COMPATIBILITY_PUBLISH=1 "$BASH_BIN" tools/pixel-core-consumer-smoke.sh
# 只依赖 pixel-runtime 的消费者必须能通过传递 core 使用公开 RenderObject SPI。
PIXEL_SKIP_COMPATIBILITY_PUBLISH=1 "$BASH_BIN" tools/pixel-runtime-consumer-smoke.sh
# 只依赖 pixel-widgets 的消费者必须只传递 core/runtime，并通过消费者侧 R8。
PIXEL_SKIP_COMPATIBILITY_PUBLISH=1 "$BASH_BIN" tools/pixel-widgets-consumer-smoke.sh
# 只依赖 pixel-navigation 的消费者必须只传递 core/runtime/widgets，并通过消费者侧 R8。
PIXEL_SKIP_COMPATIBILITY_PUBLISH=1 "$BASH_BIN" tools/pixel-navigation-consumer-smoke.sh
# 只依赖 pixel-android 的消费者必须传递最小 SDK 图与 Lifecycle，并通过消费者侧 R8。
PIXEL_SKIP_COMPATIBILITY_PUBLISH=1 "$BASH_BIN" tools/pixel-android-consumer-smoke.sh
# 只依赖 pixel-testing 的消费者不得传递 Android Host/debug/compose。
PIXEL_SKIP_COMPATIBILITY_PUBLISH=1 "$BASH_BIN" tools/pixel-testing-consumer-smoke.sh
# 只依赖 pixel-debug 的消费者必须传递 testing/Android Host，但不得传递聚合或 Compose。
PIXEL_SKIP_COMPATIBILITY_PUBLISH=1 "$BASH_BIN" tools/pixel-debug-consumer-smoke.sh
# 只依赖 pixel-compose 的消费者必须真实编译 Composable wrapper，并且不得传递聚合/testing/debug。
PIXEL_SKIP_COMPATIBILITY_PUBLISH=1 "$BASH_BIN" tools/pixel-compose-consumer-smoke.sh
# 只依据 1.0 发布文档组合 Host、路由、自定义 SPI 与测试的全新消费者。
PIXEL_SKIP_COMPATIBILITY_PUBLISH=1 "$BASH_BIN" tools/pixel-docs-consumer-smoke.sh
if [[ "$RUN_PERFORMANCE_GATES" == "1" ]]; then
  # 后续性能 Goal 仍可生成独立趋势、生命周期和 Baseline Profile 证据。
  "$BASH_BIN" tools/pixel-perf-smoke.sh
  "$BASH_BIN" tools/pixel-soak-test.sh
  "$BASH_BIN" tools/pixel-baseline-profile-check.sh
else
  echo "当前 1.0 Goal 已排除性能与 soak 专项；跳过可选性能门禁。"
fi

# 文档工具版本必须与 CI 固定清单一致，避免不同解析器造成链接门禁漂移。
if ! "$PYTHON_BIN" -c 'import importlib.metadata; raise SystemExit(importlib.metadata.version("mkdocs") != "1.6.1")'; then
  "$PYTHON_BIN" -m pip install --user --disable-pip-version-check -r requirements-docs.txt
fi
"$PYTHON_BIN" tools/prepare_mkdocs_docs.py
"$PYTHON_BIN" -m mkdocs build --strict
