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
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/pixel-navigation-consumer.XXXXXX")"
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
    --no-daemon
fi

mkdir -p "$TMP_DIR/app/src/main/kotlin/com/purride/pixelnavigationconsumer"
mkdir -p "$TMP_DIR/app/src/test/kotlin/com/purride/pixelnavigationconsumer"

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
rootProject.name = "PixelNavigationConsumerSmoke"
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
    namespace = "com.purride.pixelnavigationconsumer"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.purride.pixelnavigationconsumer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            /** 开启消费者侧 R8，验证 navigation consumer rules 与拆分后的二进制边界。 */
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation("com.purride:pixel-navigation:1.0.0")
    testImplementation("junit:junit:4.13.2")
}
EOF

cat >"$TMP_DIR/app/src/main/AndroidManifest.xml" <<'EOF'
<manifest />
EOF

cat >"$TMP_DIR/app/src/main/kotlin/com/purride/pixelnavigationconsumer/NavigationConsumer.kt" <<'EOF'
package com.purride.pixelnavigationconsumer

import com.purride.pixelui.widgets.navigation.PixelDeepLink

/** 只依赖已发布 pixel-navigation 及其传递 core/runtime/widgets 的最小外部消费者。 */
class NavigationConsumer {
    /** 仅当 URI 指向受支持的 profile 路径时返回解码后的用户标识。 */
    fun profileId(uri: String): String? {
        /** 使用 navigation artifact 公开解析器得到的结构化深链。 */
        val link = PixelDeepLink.parse(uri)
        return if (link.scheme == "pixel" && link.host == "app" && link.pathSegments == listOf("profile")) {
            link.queryParameter("id")
        } else {
            null
        }
    }
}
EOF

cat >"$TMP_DIR/app/src/test/kotlin/com/purride/pixelnavigationconsumer/NavigationConsumerTest.kt" <<'EOF'
package com.purride.pixelnavigationconsumer

import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证真实 Maven POM 下的 navigation/core/runtime/widgets 最小图可以独立使用。 */
class NavigationConsumerTest {
    /** 独立坐标公开的深链解析器应正确解码路径和查询参数。 */
    @Test
    fun parsesDeepLinkWithoutAggregateOrPlatformArtifacts() {
        /** 只依赖 pixel-navigation 发布坐标的消费者。 */
        val consumer = NavigationConsumer()

        assertEquals("42", consumer.profileId("pixel://app/profile?id=42"))
    }
}
EOF

# 依赖报告证明最小图只包含 navigation/widgets/core/runtime 与 Kotlin 基础依赖。
DEPENDENCY_REPORT="$TMP_DIR/release-runtime-dependencies.txt"
"$GRADLEW_BIN" \
  -p "$TMP_DIR" \
  -PpixelCompatibilityRepository="$COMPATIBILITY_REPOSITORY" \
  :app:dependencies \
  --configuration releaseRuntimeClasspath \
  --no-daemon >"$DEPENDENCY_REPORT"

if rg -q 'pixel-engine|pixel-(android|testing|debug|compose)|androidx\.lifecycle|androidx\.compose' "$DEPENDENCY_REPORT"; then
  echo "pixel-navigation consumer resolved a forbidden aggregate/platform/testing/debug/compose dependency" >&2
  sed -n '1,240p' "$DEPENDENCY_REPORT" >&2
  exit 1
fi

for required_coordinate in pixel-core pixel-runtime pixel-widgets pixel-navigation; do
  if ! rg -q "com\\.purride:${required_coordinate}:1\\.0\\.0" "$DEPENDENCY_REPORT"; then
    echo "pixel-navigation consumer did not resolve required $required_coordinate dependency" >&2
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

echo "pixel-navigation isolated consumer smoke passed"
