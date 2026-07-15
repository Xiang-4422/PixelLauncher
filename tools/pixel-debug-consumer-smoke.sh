#!/usr/bin/env bash
set -euo pipefail

# 仓库根用于定位 wrapper 与隔离 Maven 仓库。
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# 测试可注入 wrapper；正常验收使用仓库自带版本。
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
# 隔离仓库防止全局 Maven cache 中的可变 SNAPSHOT 掩盖 POM 缺陷。
COMPATIBILITY_REPOSITORY="$ROOT_DIR/build/compatibility-repository"
cd "$ROOT_DIR"

# 临时消费者工程在任何退出路径都必须清理。
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/pixel-debug-consumer.XXXXXX")"
cleanup() {
  # 只删除本脚本创建的临时工程。
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

if [[ "${PIXEL_SKIP_COMPATIBILITY_PUBLISH:-0}" != "1" ]]; then
  rm -rf "$COMPATIBILITY_REPOSITORY"
  "$GRADLEW_BIN" \
    :pixel-core:publishReleasePublicationToCompatibilityRepository \
    :pixel-runtime:publishReleasePublicationToCompatibilityRepository \
    :pixel-widgets:publishReleasePublicationToCompatibilityRepository \
    :pixel-navigation:publishReleasePublicationToCompatibilityRepository \
    :pixel-android:publishReleasePublicationToCompatibilityRepository \
    :pixel-testing:publishReleasePublicationToCompatibilityRepository \
    :pixel-debug:publishReleasePublicationToCompatibilityRepository \
    --no-daemon
fi

mkdir -p "$TMP_DIR/app/src/main/kotlin/com/purride/pixeldebugconsumer"
mkdir -p "$TMP_DIR/app/src/test/kotlin/com/purride/pixeldebugconsumer"

cat >"$TMP_DIR/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            name = "PixelCompatibilityRepository"
            url = uri(
                providers.gradleProperty("pixelCompatibilityRepository").orNull
                    ?: error("Missing pixelCompatibilityRepository property."),
            )
            content {
                includeGroup("com.purride")
            }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "PixelDebugConsumerSmoke"
include(":app")
EOF

cat >"$TMP_DIR/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.application") version "9.0.1" apply false
}
EOF

cat >"$TMP_DIR/app/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.application")
}

android {
    namespace = "com.purride.pixeldebugconsumer"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.purride.pixeldebugconsumer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            /** 开启消费者侧 R8，验证 debug consumer rules 与拆分后的二进制边界。 */
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation("com.purride:pixel-debug:1.0.0")
    testImplementation("junit:junit:4.13.2")
}
EOF

cat >"$TMP_DIR/app/src/main/AndroidManifest.xml" <<'EOF'
<manifest />
EOF

cat >"$TMP_DIR/app/src/main/kotlin/com/purride/pixeldebugconsumer/DebugConsumer.kt" <<'EOF'
package com.purride.pixeldebugconsumer

import com.purride.pixelui.PixelDebugOverlay
import com.purride.pixelui.Widget

/** 只依赖已发布 pixel-debug 及其传递完整最小 SDK 图的外部消费者。 */
class DebugConsumer {
    /** 使用独立 debug artifact 创建带稳定标识的空诊断层。 */
    fun emptyOverlay(): Widget = PixelDebugOverlay(stats = null, key = "external-debug")
}
EOF

cat >"$TMP_DIR/app/src/test/kotlin/com/purride/pixeldebugconsumer/DebugConsumerTest.kt" <<'EOF'
package com.purride.pixeldebugconsumer

import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证真实 Maven POM 下 debug 与 testing 的公开组合可以独立使用。 */
class DebugConsumerTest {
    /** 独立 debug 坐标应能构建并挂载诊断 overlay。 */
    @Test
    fun mountsOverlayWithoutAggregateArtifacts() {
        /** 只依赖 pixel-debug 发布坐标的消费者。 */
        val consumer = DebugConsumer()
        /** 由 debug POM 传递得到的 testing 驱动器。 */
        val tester = PixelTester()

        tester.pumpWidget(consumer.emptyOverlay(), logicalWidth = 8, logicalHeight = 8)
        assertTrue(tester.exists(find.byKey("external-debug")))
        tester.dispose()
    }
}
EOF

# 依赖报告证明 debug 会传递 android/testing 与完整最小 SDK 图。
DEPENDENCY_REPORT="$TMP_DIR/release-runtime-dependencies.txt"
"$GRADLEW_BIN" \
  -p "$TMP_DIR" \
  -PpixelCompatibilityRepository="$COMPATIBILITY_REPOSITORY" \
  :app:dependencies \
  --configuration releaseRuntimeClasspath \
  --no-daemon >"$DEPENDENCY_REPORT"

if rg -q 'pixel-engine|pixel-compose|androidx\.compose' "$DEPENDENCY_REPORT"; then
  echo "pixel-debug consumer resolved a forbidden aggregate/compose dependency" >&2
  sed -n '1,240p' "$DEPENDENCY_REPORT" >&2
  exit 1
fi

for required_coordinate in pixel-core pixel-runtime pixel-widgets pixel-navigation pixel-android pixel-testing pixel-debug; do
  if ! rg -q "com\\.purride:${required_coordinate}:1\\.0\\.0" "$DEPENDENCY_REPORT"; then
    echo "pixel-debug consumer did not resolve required $required_coordinate dependency" >&2
    sed -n '1,240p' "$DEPENDENCY_REPORT" >&2
    exit 1
  fi
done

if ! rg -q 'androidx\.lifecycle:lifecycle-runtime-ktx' "$DEPENDENCY_REPORT"; then
  echo "pixel-debug consumer did not resolve required Lifecycle dependency" >&2
  sed -n '1,240p' "$DEPENDENCY_REPORT" >&2
  exit 1
fi

"$GRADLEW_BIN" \
  -p "$TMP_DIR" \
  -PpixelCompatibilityRepository="$COMPATIBILITY_REPOSITORY" \
  :app:testDebugUnitTest \
  :app:assembleDebug \
  :app:assembleRelease \
  --no-daemon

echo "pixel-debug isolated consumer smoke passed"
