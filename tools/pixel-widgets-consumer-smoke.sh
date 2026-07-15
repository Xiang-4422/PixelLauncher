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
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/pixel-widgets-consumer.XXXXXX")"
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
    --no-daemon
fi

mkdir -p "$TMP_DIR/app/src/main/kotlin/com/purride/pixelwidgetsconsumer"
mkdir -p "$TMP_DIR/app/src/test/kotlin/com/purride/pixelwidgetsconsumer"

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
rootProject.name = "PixelWidgetsConsumerSmoke"
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
    namespace = "com.purride.pixelwidgetsconsumer"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.purride.pixelwidgetsconsumer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            /** 开启消费者侧 R8，验证 widgets consumer rules 与拆分后的二进制边界。 */
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation("com.purride:pixel-widgets:1.0.0")
    testImplementation("junit:junit:4.13.2")
}
EOF

cat >"$TMP_DIR/app/src/main/AndroidManifest.xml" <<'EOF'
<manifest />
EOF

cat >"$TMP_DIR/app/src/main/kotlin/com/purride/pixelwidgetsconsumer/WidgetsConsumer.kt" <<'EOF'
package com.purride.pixelwidgetsconsumer

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.DecoratedBox
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Widget

/** 只依赖已发布 pixel-widgets 及其传递 core/runtime 的最小外部消费者。 */
class WidgetsConsumer {
    /** 使用标准组件工厂构建具有稳定标识的可发布组件树。 */
    fun buildStatusCard(onConfirm: () -> Unit): Widget {
        return DecoratedBox(
            fillColor = PixelColor.White,
            borderColor = PixelColor.Black,
            child = OutlinedButton(
                text = "OK",
                onPressed = onConfirm,
                key = "confirm-button",
            ),
            key = "status-card",
        )
    }
}
EOF

cat >"$TMP_DIR/app/src/test/kotlin/com/purride/pixelwidgetsconsumer/WidgetsConsumerTest.kt" <<'EOF'
package com.purride.pixelwidgetsconsumer

import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证真实 Maven POM 下的 widgets/core/runtime 最小图可以独立使用。 */
class WidgetsConsumerTest {
    /** 标准 widgets 工厂应保留调用方声明的 retained identity。 */
    @Test
    fun buildsWidgetTreeWithoutNavigationOrPlatformArtifacts() {
        /** 只依赖 pixel-widgets 发布坐标的消费者。 */
        val consumer = WidgetsConsumer()
        /** 经标准组件公开工厂构建的根组件。 */
        val root = consumer.buildStatusCard(onConfirm = { })

        assertEquals("status-card", root.key)
    }
}
EOF

# 依赖报告证明最小图只包含 widgets/core/runtime 与 Kotlin 基础依赖。
DEPENDENCY_REPORT="$TMP_DIR/release-runtime-dependencies.txt"
"$GRADLEW_BIN" \
  -p "$TMP_DIR" \
  -PpixelCompatibilityRepository="$COMPATIBILITY_REPOSITORY" \
  :app:dependencies \
  --configuration releaseRuntimeClasspath \
  --no-daemon >"$DEPENDENCY_REPORT"

if rg -q 'pixel-engine|pixel-(navigation|android|testing|debug|compose)|androidx\.lifecycle|androidx\.compose' "$DEPENDENCY_REPORT"; then
  echo "pixel-widgets consumer resolved a forbidden navigation/platform/testing/debug/compose dependency" >&2
  sed -n '1,240p' "$DEPENDENCY_REPORT" >&2
  exit 1
fi

for required_coordinate in pixel-core pixel-runtime pixel-widgets; do
  if ! rg -q "com\\.purride:${required_coordinate}:1\\.0\\.0" "$DEPENDENCY_REPORT"; then
    echo "pixel-widgets consumer did not resolve required $required_coordinate dependency" >&2
    sed -n '1,240p' "$DEPENDENCY_REPORT" >&2
    exit 1
  fi
done

"$GRADLEW_BIN" \
  -p "$TMP_DIR" \
  -PpixelCompatibilityRepository="$COMPATIBILITY_REPOSITORY" \
  :app:testDebugUnitTest \
  :app:assembleDebug \
  :app:assembleRelease \
  --no-daemon

echo "pixel-widgets isolated consumer smoke passed"
