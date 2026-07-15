#!/usr/bin/env bash
set -euo pipefail

# 仓库根目录用于定位 wrapper 和隔离 Maven 仓库。
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# 测试可注入 wrapper，正常执行固定使用当前仓库版本。
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
# 独立仓库防止用户全局 Maven cache 中的旧 artifact 掩盖 POM 问题。
COMPATIBILITY_REPOSITORY="$ROOT_DIR/build/compatibility-repository"
cd "$ROOT_DIR"

# 临时消费者工程在退出时总会清理。
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/pixel-core-consumer.XXXXXX")"
cleanup() {
  # 删除只属于本次 smoke 的临时工程。
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

if [[ "${PIXEL_SKIP_COMPATIBILITY_PUBLISH:-0}" != "1" ]]; then
  rm -rf "$COMPATIBILITY_REPOSITORY"
  "$GRADLEW_BIN" :pixel-core:publishReleasePublicationToCompatibilityRepository --no-daemon
fi

mkdir -p "$TMP_DIR/app/src/main/kotlin/com/purride/pixelcoreconsumer"
mkdir -p "$TMP_DIR/app/src/test/kotlin/com/purride/pixelcoreconsumer"

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
rootProject.name = "PixelCoreConsumerSmoke"
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
    namespace = "com.purride.pixelcoreconsumer"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.purride.pixelcoreconsumer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("com.purride:pixel-core:1.0.0")
    testImplementation("junit:junit:4.13.2")
}
EOF

cat >"$TMP_DIR/app/src/main/AndroidManifest.xml" <<'EOF'
<manifest />
EOF

cat >"$TMP_DIR/app/src/main/kotlin/com/purride/pixelcoreconsumer/CoreConsumer.kt" <<'EOF'
package com.purride.pixelcoreconsumer

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor

/** 只依赖 pixel-core 的最小外部消费者。 */
class CoreConsumer {
    /** 创建一个不需要 Android View、Lifecycle 或 Compose 的像素缓冲。 */
    fun createBuffer(): PixelBuffer {
        /** 外部消费者可直接构造并写入的纯像素数据。 */
        val buffer = PixelBuffer(width = 2, height = 2)
        buffer.setPixel(x = 0, y = 0, color = PixelColor.White)
        return buffer
    }
}
EOF

cat >"$TMP_DIR/app/src/test/kotlin/com/purride/pixelcoreconsumer/CoreConsumerTest.kt" <<'EOF'
package com.purride.pixelcoreconsumer

import com.purride.pixelcore.PixelColor
import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证临时 Maven 中的 pixel-core 可以独立编译和运行。 */
class CoreConsumerTest {
    /** 纯 core 消费者应能创建 buffer 并读取稳定像素值。 */
    @Test
    fun createsPixelBufferWithoutUiArtifacts() {
        /** 被测试的外部消费者。 */
        val consumer = CoreConsumer()
        /** 从发布坐标构造的像素缓冲。 */
        val buffer = consumer.createBuffer()
        /** 从已发布 AAR 的稳定读取 API 返回的 ARGB 值。 */
        val actualArgb = buffer.getPixel(0, 0).argb

        assertEquals("pixel-core round trip", PixelColor.White.argb, actualArgb)
    }
}
EOF

# 依赖报告用于证明最小图没有 UI/testing/debug/compose 泄漏。
DEPENDENCY_REPORT="$TMP_DIR/release-runtime-dependencies.txt"
"$GRADLEW_BIN" \
  -p "$TMP_DIR" \
  -PpixelCompatibilityRepository="$COMPATIBILITY_REPOSITORY" \
  :app:dependencies \
  --configuration releaseRuntimeClasspath \
  --no-daemon >"$DEPENDENCY_REPORT"

if rg -q 'pixel-engine|pixel-(runtime|widgets|navigation|android|testing|debug|compose)|androidx\.lifecycle|androidx\.compose' "$DEPENDENCY_REPORT"; then
  echo "pixel-core consumer resolved a forbidden UI/testing/debug/compose dependency" >&2
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

echo "pixel-core isolated consumer smoke passed"
