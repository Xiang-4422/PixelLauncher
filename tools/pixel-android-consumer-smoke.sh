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
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/pixel-android-consumer.XXXXXX")"
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
    --no-daemon
fi

mkdir -p "$TMP_DIR/app/src/main/kotlin/com/purride/pixelandroidconsumer"
mkdir -p "$TMP_DIR/app/src/test/kotlin/com/purride/pixelandroidconsumer"

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
rootProject.name = "PixelAndroidConsumerSmoke"
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
    namespace = "com.purride.pixelandroidconsumer"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.purride.pixelandroidconsumer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            /** 开启消费者侧 R8，验证 android consumer rules 与拆分后的二进制边界。 */
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation("com.purride:pixel-android:1.0.0")
    testImplementation("junit:junit:4.13.2")
}
EOF

cat >"$TMP_DIR/app/src/main/AndroidManifest.xml" <<'EOF'
<manifest />
EOF

cat >"$TMP_DIR/app/src/main/kotlin/com/purride/pixelandroidconsumer/AndroidConsumer.kt" <<'EOF'
package com.purride.pixelandroidconsumer

import android.content.Context
import com.purride.pixelengine.PixelEngine
import com.purride.pixelui.PixelHostSetup
import com.purride.pixelui.PixelHostSetupConfig
import com.purride.pixelui.createPixelHostSetup
import com.purride.pixelui.host.ManualFrameScheduler
import com.purride.pixelui.services.PixelClock

/** 只依赖已发布 pixel-android 及其传递 core/runtime/widgets/navigation 的最小外部消费者。 */
class AndroidConsumer {
    /** 消费者注入的手动帧调度器，用于证明 Builder 不依赖 Android Looper。 */
    val frameScheduler: ManualFrameScheduler = ManualFrameScheduler()

    /** 返回可由普通 JVM 测试检查的默认 Android Host 配置。 */
    fun defaultConfig(): PixelHostSetupConfig = PixelHostSetupConfig()

    /** 通过发布坐标公开的 Builder 创建隔离 Engine。 */
    fun createEngine(): PixelEngine {
        return PixelEngine.Builder()
            .clock(FixedPixelClock)
            .frameScheduler(frameScheduler)
            .build()
    }

    /** 使用独立 Android artifact 的公开 Engine 重载创建完整 Host。 */
    fun createHost(context: Context): PixelHostSetup {
        return createPixelHostSetup(
            context = context,
            engine = createEngine(),
            config = defaultConfig(),
        )
    }
}

/** 外部消费者提供的确定性时钟，验证服务接口可从发布物实现。 */
private object FixedPixelClock : PixelClock {
    /** 返回固定运行毫秒。 */
    override fun uptimeMillis(): Long = 42L

    /** 返回固定单调纳秒。 */
    override fun nanoTime(): Long = 42_000_000L

    /** 返回固定墙上时间。 */
    override fun currentTimeMillis(): Long = 1_700_000_000_000L
}
EOF

cat >"$TMP_DIR/app/src/test/kotlin/com/purride/pixelandroidconsumer/AndroidConsumerTest.kt" <<'EOF'
package com.purride.pixelandroidconsumer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import com.purride.pixelui.TextDirection

/** 验证真实 Maven POM 下的 android/core/runtime/widgets/navigation 最小图可以独立使用。 */
class AndroidConsumerTest {
    /** 独立坐标公开的 Host 配置应保留稳定的默认文本方向。 */
    @Test
    fun createsDefaultHostConfigWithoutAggregateArtifacts() {
        /** 只依赖 pixel-android 发布坐标的消费者。 */
        val consumer = AndroidConsumer()

        assertEquals(TextDirection.LTR, consumer.defaultConfig().textDirection)
    }

    /** 发布坐标应允许消费者注入纯 JVM 服务并构建隔离 Engine。 */
    @Test
    fun buildsEngineWithInjectedRuntimeServices() {
        /** 只依赖 pixel-android 发布坐标的消费者。 */
        val consumer = AndroidConsumer()
        /** 通过新 Builder 创建的隔离 Engine。 */
        val engine = consumer.createEngine()

        assertEquals(42_000_000L, engine.services.clock.nanoTime())
        assertSame(consumer.frameScheduler, engine.services.frameScheduler)
    }
}
EOF

# 依赖报告证明最小图只包含 android/navigation/widgets/core/runtime、Lifecycle 与 Kotlin 基础依赖。
DEPENDENCY_REPORT="$TMP_DIR/release-runtime-dependencies.txt"
"$GRADLEW_BIN" \
  -p "$TMP_DIR" \
  -PpixelCompatibilityRepository="$COMPATIBILITY_REPOSITORY" \
  :app:dependencies \
  --configuration releaseRuntimeClasspath \
  --no-daemon >"$DEPENDENCY_REPORT"

if rg -q 'pixel-engine|pixel-(testing|debug|compose)|androidx\.compose' "$DEPENDENCY_REPORT"; then
  echo "pixel-android consumer resolved a forbidden aggregate/testing/debug/compose dependency" >&2
  sed -n '1,240p' "$DEPENDENCY_REPORT" >&2
  exit 1
fi

for required_coordinate in pixel-core pixel-runtime pixel-widgets pixel-navigation pixel-android; do
  if ! rg -q "com\\.purride:${required_coordinate}:1\\.0\\.0" "$DEPENDENCY_REPORT"; then
    echo "pixel-android consumer did not resolve required $required_coordinate dependency" >&2
    sed -n '1,240p' "$DEPENDENCY_REPORT" >&2
    exit 1
  fi
done

if ! rg -q 'androidx\.lifecycle:lifecycle-runtime-ktx' "$DEPENDENCY_REPORT"; then
  echo "pixel-android consumer did not resolve required Lifecycle dependency" >&2
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

echo "pixel-android isolated consumer smoke passed"
