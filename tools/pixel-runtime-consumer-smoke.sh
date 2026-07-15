#!/usr/bin/env bash
set -euo pipefail

# 仓库根用于定位 wrapper 和本轮隔离 Maven 仓库。
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# 测试可注入 wrapper；正常验收固定使用仓库自带版本。
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
# 隔离仓库防止全局 Maven cache 中的可变 SNAPSHOT 掩盖 POM 缺陷。
COMPATIBILITY_REPOSITORY="$ROOT_DIR/build/compatibility-repository"
cd "$ROOT_DIR"

# 临时消费者工程在任何退出路径都必须清理。
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/pixel-runtime-consumer.XXXXXX")"
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
    --no-daemon
fi

mkdir -p "$TMP_DIR/app/src/main/kotlin/com/purride/pixelruntimeconsumer"
mkdir -p "$TMP_DIR/app/src/test/kotlin/com/purride/pixelruntimeconsumer"

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
rootProject.name = "PixelRuntimeConsumerSmoke"
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
    namespace = "com.purride.pixelruntimeconsumer"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.purride.pixelruntimeconsumer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            /** 开启消费者侧 R8，验证 runtime consumer rules 和二进制边界。 */
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation("com.purride:pixel-runtime:1.0.0")
    testImplementation("junit:junit:4.13.2")
}
EOF

cat >"$TMP_DIR/app/src/main/AndroidManifest.xml" <<'EOF'
<manifest />
EOF

cat >"$TMP_DIR/app/src/main/kotlin/com/purride/pixelruntimeconsumer/RuntimeConsumer.kt" <<'EOF'
package com.purride.pixelruntimeconsumer

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.InheritedWidget
import com.purride.pixelui.Listenable
import com.purride.pixelui.Widget
import com.purride.pixelui.advanced.PixelLeafRenderObjectWidget
import com.purride.pixelui.advanced.PixelPaintContext
import com.purride.pixelui.advanced.PixelRenderBox
import com.purride.pixelui.advanced.PixelRenderConstraints
import com.purride.pixelui.advanced.PixelRenderObject
import com.purride.pixelui.advanced.PixelRenderSize
import kotlin.reflect.KClass

/** 只依赖已发布 pixel-runtime 与其传递 core 的最小外部消费者。 */
class RuntimeConsumer {
    /** 通过公开 Widget/RenderObject SPI 布局并绘制一个纯 runtime 叶子。 */
    fun renderWhiteBuffer(width: Int, height: Int): PixelBuffer {
        /** 不读取环境的纯色声明式叶子。 */
        val leaf = SolidRuntimeLeaf(color = PixelColor.White)
        /** 由公开 Widget SPI 创建的 retained render object。 */
        val renderObject = leaf.createRenderObject(EmptyBuildContext) as PixelRenderBox
        /** 目标 buffer 只来自 pixel-core 传递依赖。 */
        val buffer = PixelBuffer(width = width, height = height)
        renderObject.layout(
            PixelRenderConstraints(
                maxWidth = width,
                maxHeight = height,
            ),
        )
        renderObject.paint(
            context = PixelPaintContext(buffer = buffer),
            offsetX = 0,
            offsetY = 0,
        )
        return buffer
    }

    /** 外部消费者定义的纯色叶子 Widget。 */
    private data class SolidRuntimeLeaf(
        /** 叶子绘制的确定颜色。 */
        val color: PixelColor,
    ) : PixelLeafRenderObjectWidget() {
        /** 创建不依赖标准 widgets 或 Android Host 的 RenderBox。 */
        override fun createRenderObject(context: BuildContext): PixelRenderObject = SolidRuntimeBox(color)
    }

    /** 外部消费者定义的最小单色 RenderBox。 */
    private class SolidRuntimeBox(
        /** 当前盒模型填充颜色。 */
        private val color: PixelColor,
    ) : PixelRenderBox() {
        /** 采用父级约束允许的最大逻辑尺寸。 */
        override fun layout(constraints: PixelRenderConstraints) {
            size = PixelRenderSize(
                width = constraints.maxWidth,
                height = constraints.maxHeight,
            )
        }

        /** 使用公开 PixelPaintContext 覆盖完整盒模型区域。 */
        override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
            context.fillRect(
                x = offsetX,
                y = offsetY,
                width = size.width,
                height = size.height,
                color = color,
            )
        }
    }

    /** 不提供环境值的最小 BuildContext，仅用于纯叶子 SPI 创建。 */
    private object EmptyBuildContext : BuildContext {
        /** 纯叶子不会读取声明式 widget；误用时立即失败。 */
        override val widget: Widget
            get() = error("The isolated runtime leaf must not read BuildContext.widget.")

        /** 纯叶子没有 InheritedWidget 依赖。 */
        override fun <T : InheritedWidget> dependOnInheritedWidgetOfExactType(type: KClass<T>): T? = null

        /** 纯叶子没有只读 InheritedWidget 查询。 */
        override fun <T : InheritedWidget> getInheritedWidgetOfExactType(type: KClass<T>): T? = null

        /** 纯叶子不订阅可监听状态。 */
        override fun watch(listenable: Listenable?) = Unit
    }
}
EOF

cat >"$TMP_DIR/app/src/test/kotlin/com/purride/pixelruntimeconsumer/RuntimeConsumerTest.kt" <<'EOF'
package com.purride.pixelruntimeconsumer

import com.purride.pixelcore.PixelColor
import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证真实 Maven POM 下的 runtime/core 最小图可以独立布局和绘制。 */
class RuntimeConsumerTest {
    /** 公开 runtime SPI 应生成完整确定的白色像素区域。 */
    @Test
    fun rendersLeafWithoutWidgetsOrAndroidHost() {
        /** 只依赖 pixel-runtime 发布坐标的消费者。 */
        val consumer = RuntimeConsumer()
        /** 经公开 Widget/RenderObject SPI 生成的像素缓冲。 */
        val buffer = consumer.renderWhiteBuffer(width = 4, height = 3)

        assertEquals(PixelColor.White, buffer.getPixel(0, 0))
        assertEquals(PixelColor.White, buffer.getPixel(3, 2))
    }
}
EOF

# 依赖报告证明最小图只包含 runtime/core 与 Kotlin 基础依赖。
DEPENDENCY_REPORT="$TMP_DIR/release-runtime-dependencies.txt"
"$GRADLEW_BIN" \
  -p "$TMP_DIR" \
  -PpixelCompatibilityRepository="$COMPATIBILITY_REPOSITORY" \
  :app:dependencies \
  --configuration releaseRuntimeClasspath \
  --no-daemon >"$DEPENDENCY_REPORT"

if rg -q 'pixel-engine|pixel-(widgets|navigation|android|testing|debug|compose)|androidx\.lifecycle|androidx\.compose' "$DEPENDENCY_REPORT"; then
  echo "pixel-runtime consumer resolved a forbidden widgets/android/testing/debug/compose dependency" >&2
  sed -n '1,240p' "$DEPENDENCY_REPORT" >&2
  exit 1
fi

if ! rg -q 'com\.purride:pixel-core:1\.0\.0' "$DEPENDENCY_REPORT"; then
  echo "pixel-runtime consumer did not resolve the required pixel-core dependency" >&2
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

echo "pixel-runtime isolated consumer smoke passed"
