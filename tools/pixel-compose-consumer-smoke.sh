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
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/pixel-compose-consumer.XXXXXX")"
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
    :pixel-compose:publishReleasePublicationToCompatibilityRepository \
    --no-daemon
fi

mkdir -p "$TMP_DIR/app/src/main/kotlin/com/purride/pixelcomposeconsumer"
mkdir -p "$TMP_DIR/app/src/test/kotlin/com/purride/pixelcomposeconsumer"

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
rootProject.name = "PixelComposeConsumerSmoke"
include(":app")
EOF

cat >"$TMP_DIR/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
EOF

cat >"$TMP_DIR/app/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.purride.pixelcomposeconsumer"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.purride.pixelcomposeconsumer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        /** 外部消费者真实编译 PixelHost Composable。 */
        compose = true
    }

    buildTypes {
        getByName("release") {
            /** 开启消费者侧 R8，验证 compose consumer rules 与拆分后的二进制边界。 */
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation("com.purride:pixel-compose:1.0.0")
    testImplementation("junit:junit:4.13.2")
}
EOF

cat >"$TMP_DIR/app/src/main/AndroidManifest.xml" <<'EOF'
<manifest />
EOF

cat >"$TMP_DIR/app/src/main/kotlin/com/purride/pixelcomposeconsumer/ComposeConsumer.kt" <<'EOF'
package com.purride.pixelcomposeconsumer

import androidx.compose.runtime.Composable
import com.purride.pixelcompose.PixelHost
import com.purride.pixelengine.PixelEngine
import com.purride.pixelui.Text

/** 只依赖已发布 pixel-compose 及其传递最小 Host SDK 图的外部消费者。 */
class ComposeConsumer {
    /** 外部消费者独占且可注入服务的 Engine。 */
    private val engine: PixelEngine = PixelEngine.Builder().build()

    /** 真实调用发布坐标中的 Composable wrapper 与 Pixel Widget 内容。 */
    @Composable
    fun Content() {
        PixelHost(
            engine = engine,
            stateKey = "external-consumer",
            content = { Text("EXTERNAL") },
        )
    }

    /** 返回可被普通 JVM 测试验证的 Engine 主题引用。 */
    fun defaultTheme() = engine.theme
}
EOF

cat >"$TMP_DIR/app/src/test/kotlin/com/purride/pixelcomposeconsumer/ComposeConsumerTest.kt" <<'EOF'
package com.purride.pixelcomposeconsumer

import org.junit.Assert.assertNotNull
import org.junit.Test

/** 验证真实 Maven POM 下 Compose wrapper 与最小 Host 图可以独立使用。 */
class ComposeConsumerTest {
    /** 独立 compose 坐标应传递 Engine 与 Compose 编译所需 API。 */
    @Test
    fun resolvesRealComposeHostGraph() {
        /** 只依赖 pixel-compose 发布坐标的消费者。 */
        val consumer = ComposeConsumer()

        assertNotNull(consumer.defaultTheme())
    }
}
EOF

# 依赖报告证明 Compose 只经可选坐标传递，并保持聚合/testing/debug 隔离。
DEPENDENCY_REPORT="$TMP_DIR/release-runtime-dependencies.txt"
"$GRADLEW_BIN" \
  -p "$TMP_DIR" \
  -PpixelCompatibilityRepository="$COMPATIBILITY_REPOSITORY" \
  :app:dependencies \
  --configuration releaseRuntimeClasspath \
  --no-daemon >"$DEPENDENCY_REPORT"

if rg -q 'pixel-engine|pixel-testing|pixel-debug' "$DEPENDENCY_REPORT"; then
  echo "pixel-compose consumer resolved a forbidden aggregate/testing/debug dependency" >&2
  sed -n '1,240p' "$DEPENDENCY_REPORT" >&2
  exit 1
fi

for required_coordinate in pixel-core pixel-runtime pixel-widgets pixel-navigation pixel-android pixel-compose; do
  if ! rg -q "com\\.purride:${required_coordinate}:1\\.0\\.0" "$DEPENDENCY_REPORT"; then
    echo "pixel-compose consumer did not resolve required $required_coordinate dependency" >&2
    sed -n '1,240p' "$DEPENDENCY_REPORT" >&2
    exit 1
  fi
done

if ! rg -q 'androidx\.lifecycle:lifecycle-runtime-ktx' "$DEPENDENCY_REPORT"; then
  echo "pixel-compose consumer did not resolve required Lifecycle dependency" >&2
  sed -n '1,240p' "$DEPENDENCY_REPORT" >&2
  exit 1
fi

for required_compose_coordinate in androidx.compose.runtime:runtime androidx.compose.ui:ui; do
  if ! rg -q "$required_compose_coordinate" "$DEPENDENCY_REPORT"; then
    echo "pixel-compose consumer did not resolve required $required_compose_coordinate dependency" >&2
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

echo "pixel-compose isolated consumer smoke passed"
