#!/usr/bin/env bash
set -euo pipefail

# 仓库根用于定位 wrapper、发布坐标和统一报告目录。
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# 测试可注入 wrapper；正常验收使用仓库 Wrapper。
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
# 当前源码本轮发布到隔离 file-Maven，禁止读取本机 mavenLocal。
COMPATIBILITY_REPOSITORY="$ROOT_DIR/build/compatibility-repository"
cd "$ROOT_DIR"

# 临时消费者在所有退出路径清理，不污染仓库或 Gradle 工程发现。
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/pixel-docs-consumer.XXXXXX")"
cleanup() {
  # 只删除本脚本创建的临时消费者。
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

if [[ "${PIXEL_SKIP_COMPATIBILITY_PUBLISH:-0}" != "1" ]]; then
  rm -rf "$COMPATIBILITY_REPOSITORY"
  "$GRADLEW_BIN" \
    :pixel-engine:publishReleasePublicationToCompatibilityRepository \
    --no-daemon
fi

mkdir -p "$TMP_DIR/app/src/main/kotlin/com/purride/pixeldocsconsumer"
mkdir -p "$TMP_DIR/app/src/test/kotlin/com/purride/pixeldocsconsumer"

cat >"$TMP_DIR/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            name = "PixelCompatibilityRepository"
            url = uri(providers.gradleProperty("pixelCompatibilityRepository").get())
            content { includeGroup("com.purride") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "PixelDocsConsumerSmoke"
include(":app")
EOF

cat >"$TMP_DIR/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.application") version "9.0.1" apply false
}
EOF

cat >"$TMP_DIR/app/build.gradle.kts" <<'EOF'
plugins { id("com.android.application") }

android {
    namespace = "com.purride.pixeldocsconsumer"
    compileSdk { version = release(36) }
    defaultConfig {
        applicationId = "com.purride.pixeldocsconsumer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        getByName("release") {
            /** 消费者侧 R8 验证文档示例没有依赖被裁掉的实现细节。 */
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation("com.purride:pixel-engine:1.0.0")
    testImplementation("junit:junit:4.13.2")
}
EOF

cat >"$TMP_DIR/app/src/main/AndroidManifest.xml" <<'EOF'
<manifest />
EOF

cat >"$TMP_DIR/app/src/main/kotlin/com/purride/pixeldocsconsumer/DocsConsumer.kt" <<'EOF'
package com.purride.pixeldocsconsumer

import android.content.Context
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.PixelHostSetup
import com.purride.pixelui.PixelHostSetupConfig
import com.purride.pixelui.Text
import com.purride.pixelui.TextButton
import com.purride.pixelui.Widget
import com.purride.pixelui.advanced.PixelLeafRenderObjectWidget
import com.purride.pixelui.advanced.PixelPaintContext
import com.purride.pixelui.advanced.PixelRenderBox
import com.purride.pixelui.advanced.PixelRenderConstraints
import com.purride.pixelui.advanced.PixelRenderObject
import com.purride.pixelui.advanced.PixelRenderSize
import com.purride.pixelui.PixelRouteDestination
import com.purride.pixelui.createPixelHostSetup
import com.purride.pixelui.pixelRouteDestination

/** 仅依据 1.0 文档组合 Host、typed route 和公开 RenderObject SPI 的外部消费者。 */
class DocsConsumer {
    /** 创建 Quickstart 中描述的最小 Android Host。 */
    fun createHost(context: Context): PixelHostSetup {
        return createPixelHostSetup(
            context = context,
            config = PixelHostSetupConfig(content = { DocSquareWidget(6, PixelColor.White) }),
        )
    }

    /** 创建接收 Unit、返回 String 的 typed destination。 */
    fun createRoute(): PixelRouteDestination<Unit, String> {
        return pixelRouteDestination(id = "docs.editor") { _, scope ->
            TextButton(text = "DONE", onPressed = { scope.complete("saved") })
        }
    }
}

/** 文档 SPI 示例使用的无子节点方框 widget。 */
class DocSquareWidget(
    /** 方框边长，layout 时会限制到父约束。 */
    private val side: Int,
    /** 方框描边颜色。 */
    private val color: PixelColor,
    /** retained tree 使用的可选身份。 */
    override val key: Any? = null,
) : PixelLeafRenderObjectWidget(key) {
    /** 为首次挂载创建 render object。 */
    override fun createRenderObject(context: BuildContext): PixelRenderObject {
        return DocSquareRender(side, color)
    }

    /** 只同步变化字段并选择最窄失效阶段。 */
    override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
        (renderObject as DocSquareRender).update(side, color)
    }
}

/** 执行方框约束布局和一像素描边绘制的外部 render object。 */
private class DocSquareRender(
    /** 当前请求边长。 */
    private var side: Int,
    /** 当前描边颜色。 */
    private var color: PixelColor,
) : PixelRenderBox() {
    /** 在父约束内选择正方形逻辑尺寸。 */
    override fun layout(constraints: PixelRenderConstraints) {
        size = PixelRenderSize(
            width = constraints.constrainWidth(side),
            height = constraints.constrainHeight(side),
        )
    }

    /** 在当前绝对偏移绘制一像素方框。 */
    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        context.drawRect(offsetX, offsetY, size.width, size.height, color)
    }

    /** 比较新配置，并按尺寸或颜色变化标记 layout/paint。 */
    fun update(nextSide: Int, nextColor: PixelColor) {
        val sizeChanged = side != nextSide
        val colorChanged = color != nextColor
        if (!sizeChanged && !colorChanged) return
        side = nextSide
        color = nextColor
        if (sizeChanged) markNeedsLayout() else markNeedsPaint()
    }
}
EOF

cat >"$TMP_DIR/app/src/test/kotlin/com/purride/pixeldocsconsumer/DocsConsumerTest.kt" <<'EOF'
package com.purride.pixeldocsconsumer

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证文档路径在独立发布坐标上可以一起使用。 */
class DocsConsumerTest {
    /** typed route 与自定义 SPI 应在同一消费者中保持公开契约。 */
    @Test
    fun compilesRouteAndRendersCustomObject() {
        /** 依据路由指南创建的 destination。 */
        val route = DocsConsumer().createRoute()
        /** 依据测试指南创建的离屏测试器。 */
        val tester = PixelTester()
        tester.pumpWidget(DocSquareWidget(4, PixelColor.White), 8, 8)

        assertEquals("docs.editor", route.id)
        assertEquals(PixelColor.White, tester.pixelAt(0, 0))
        tester.dispose()
    }
}
EOF

# 真实执行 JVM 测试、Debug 和消费者侧 R8 Release，证明四条文档路径不是伪代码。
"$GRADLEW_BIN" \
  -p "$TMP_DIR" \
  -PpixelCompatibilityRepository="$COMPATIBILITY_REPOSITORY" \
  :app:testDebugUnitTest \
  :app:assembleDebug \
  :app:assembleRelease \
  --no-daemon

# 稳定报告保留消费者场景和两个 APK 的摘要，供 CI artifact 与 M9 验收引用。
DOCS_CONSUMER_REPORT="$ROOT_DIR/build/reports/compatibility/docs-consumer.txt"
# Debug APK 证明 Quickstart Host 与自定义 SPI 能进入普通应用包。
DEBUG_APK="$TMP_DIR/app/build/outputs/apk/debug/app-debug.apk"
# R8 Release APK 证明文档示例不依赖仅在调试构建存在的实现细节。
RELEASE_APK="$TMP_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
mkdir -p "$(dirname "$DOCS_CONSUMER_REPORT")"
{
  echo "status=PASS"
  echo "scenarios=host,typed-route,custom-render-spi,pixel-tester,r8-release"
  echo "debugApkSha256=$(shasum -a 256 "$DEBUG_APK" | awk '{print $1}')"
  echo "releaseApkSha256=$(shasum -a 256 "$RELEASE_APK" | awk '{print $1}')"
} >"$DOCS_CONSUMER_REPORT"

echo "Pixel documentation-only consumer smoke passed."
