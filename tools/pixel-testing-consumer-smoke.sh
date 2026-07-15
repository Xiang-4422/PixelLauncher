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
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/pixel-testing-consumer.XXXXXX")"
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
    :pixel-testing:publishReleasePublicationToCompatibilityRepository \
    --no-daemon
fi

mkdir -p "$TMP_DIR/app/src/main/kotlin/com/purride/pixeltestingconsumer"
mkdir -p "$TMP_DIR/app/src/test/kotlin/com/purride/pixeltestingconsumer"

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
rootProject.name = "PixelTestingConsumerSmoke"
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
    namespace = "com.purride.pixeltestingconsumer"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.purride.pixeltestingconsumer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            /** 开启消费者侧 R8，验证 testing consumer rules 与拆分后的二进制边界。 */
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation("com.purride:pixel-testing:1.0.0")
    testImplementation("junit:junit:4.13.2")
}
EOF

cat >"$TMP_DIR/app/src/main/AndroidManifest.xml" <<'EOF'
<manifest />
EOF

cat >"$TMP_DIR/app/src/main/kotlin/com/purride/pixeltestingconsumer/TestingConsumer.kt" <<'EOF'
package com.purride.pixeltestingconsumer

import com.purride.pixelui.Text
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find

/** 只依赖已发布 pixel-testing 及其传递 core/runtime/widgets/navigation 的最小外部消费者。 */
class TestingConsumer {
    /** 使用独立 testing artifact 渲染并查找指定文本。 */
    fun renders(text: String): Boolean {
        /** 发布坐标提供的确定性离屏测试驱动器。 */
        val tester = PixelTester()
        tester.pumpWidget(Text(text), logicalWidth = 32, logicalHeight = 8)
        /** finder 对真实 retained widget 树的查询结果。 */
        val found = tester.exists(find.byText(text))
        tester.dispose()
        return found
    }
}
EOF

cat >"$TMP_DIR/app/src/test/kotlin/com/purride/pixeltestingconsumer/TestingConsumerTest.kt" <<'EOF'
package com.purride.pixeltestingconsumer

import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证真实 Maven POM 下的 testing/core/runtime/widgets/navigation 最小图可以独立使用。 */
class TestingConsumerTest {
    /** 独立 testing 坐标应能完成离屏渲染和 finder 查询。 */
    @Test
    fun rendersWithoutAggregateOrPlatformArtifacts() {
        /** 只依赖 pixel-testing 发布坐标的消费者。 */
        val consumer = TestingConsumer()

        assertTrue(consumer.renders("EXTERNAL TEST"))
    }
}
EOF

# 依赖报告证明最小图只包含 testing/navigation/widgets/core/runtime 与 Kotlin 基础依赖。
DEPENDENCY_REPORT="$TMP_DIR/release-runtime-dependencies.txt"
"$GRADLEW_BIN" \
  -p "$TMP_DIR" \
  -PpixelCompatibilityRepository="$COMPATIBILITY_REPOSITORY" \
  :app:dependencies \
  --configuration releaseRuntimeClasspath \
  --no-daemon >"$DEPENDENCY_REPORT"

if rg -q 'pixel-engine|pixel-(android|debug|compose)|androidx\.lifecycle|androidx\.compose' "$DEPENDENCY_REPORT"; then
  echo "pixel-testing consumer resolved a forbidden aggregate/platform/debug/compose dependency" >&2
  sed -n '1,240p' "$DEPENDENCY_REPORT" >&2
  exit 1
fi

for required_coordinate in pixel-core pixel-runtime pixel-widgets pixel-navigation pixel-testing; do
  if ! rg -q "com\\.purride:${required_coordinate}:1\\.0\\.0" "$DEPENDENCY_REPORT"; then
    echo "pixel-testing consumer did not resolve required $required_coordinate dependency" >&2
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

echo "pixel-testing isolated consumer smoke passed"
