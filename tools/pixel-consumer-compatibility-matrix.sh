#!/usr/bin/env bash
set -euo pipefail

# 仓库根目录用于定位 producer、Gradle Wrapper 和持久化报告。
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# 生产环境默认使用仓库 Wrapper；测试可以注入替代命令。
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
# Python 只负责已有发布物校验器，不参与源码生成。
PYTHON_BIN="${PIXEL_PYTHON_BIN:-python3}"
# 本轮唯一的隔离 Maven 仓库；供应链演练可以覆盖到已签名 staging 目录。
COMPATIBILITY_REPOSITORY="${PIXEL_COMPATIBILITY_REPOSITORY:-$ROOT_DIR/build/compatibility-repository}"
# 消费者解析地址可覆盖为临时 HTTP 仓库，同时保留本地目录供产物校验器读取。
COMPATIBILITY_REPOSITORY_URI="${PIXEL_COMPATIBILITY_REPOSITORY_URI:-$COMPATIBILITY_REPOSITORY}"
# M8-2 的持久化机读与原始日志目录。
REPORT_DIR="$ROOT_DIR/build/reports/compatibility/m8-2"
# 全部矩阵消费者都在一次性临时目录中构建。
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/pixel-consumer-matrix.XXXXXX")"
# 生产项目 Wrapper 入口脚本。
WRAPPER_SCRIPT="$ROOT_DIR/gradlew"
# 生产项目 Wrapper 运行时 JAR。
WRAPPER_JAR="$ROOT_DIR/gradle/wrapper/gradle-wrapper.jar"
# 被测 SDK 版本。
PIXEL_VERSION="1.0.0"
# 最低支持组合的 AGP 版本。
MINIMUM_AGP="8.10.1"
# 最低支持组合的 Gradle 版本。
MINIMUM_GRADLE="8.11.1"
# 最低支持组合显式使用的 Kotlin Gradle Plugin 版本。
MINIMUM_KOTLIN="2.2.10"
# 推荐组合的 AGP 版本。
RECOMMENDED_AGP="9.1.1"
# 推荐组合的 Gradle 版本。
RECOMMENDED_GRADLE="9.3.1"
# 推荐组合使用 AGP 管理的内置 Kotlin，版本由构建日志实测记录。
RECOMMENDED_KOTLIN="built-in"

# 无论成功失败都删除一次性消费者，持久证据只留在 build/reports。
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

cd "$ROOT_DIR"
rm -rf "$REPORT_DIR"
mkdir -p "$REPORT_DIR"

# 默认先清空并一次性发布所有正式坐标，杜绝全局缓存或前序脚本的旧 SNAPSHOT。
if [[ "${PIXEL_SKIP_COMPATIBILITY_PUBLISH:-0}" != "1" ]]; then
  rm -rf "$COMPATIBILITY_REPOSITORY"
  "$GRADLEW_BIN" \
    :pixel-engine:publishReleasePublicationToCompatibilityRepository \
    --no-daemon
fi

"$PYTHON_BIN" tools/check_pixel_publication.py \
  --repository "$COMPATIBILITY_REPOSITORY" \
  --version "$PIXEL_VERSION" \
  --report "$REPORT_DIR/publication.json"

# 为指定消费者准备独立 Wrapper，使每个矩阵项真正运行声明的 Gradle 版本。
prepare_wrapper() {
  # 当前消费者工程目录。
  local project_dir="$1"
  # 当前消费者使用的 Gradle 发行版版本。
  local gradle_version="$2"
  mkdir -p "$project_dir/gradle/wrapper"
  cp "$WRAPPER_SCRIPT" "$project_dir/gradlew"
  cp "$WRAPPER_JAR" "$project_dir/gradle/wrapper/gradle-wrapper.jar"
  chmod +x "$project_dir/gradlew"
  cat >"$project_dir/gradle.properties" <<'EOF'
# Pixel Android Host 的公开传递依赖包含 AndroidX Lifecycle。
android.useAndroidX=true
# 每个临时消费者使用稳定、受限的构建堆大小。
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
EOF
  cat >"$project_dir/gradle/wrapper/gradle-wrapper.properties" <<EOF
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-${gradle_version}-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF
}

# 写入所有矩阵项共享的 Kotlin SPI、Android 入口和 Java 可见性探针。
write_shared_sources() {
  # 当前消费者 app 模块目录。
  local app_dir="$1"
  mkdir -p "$app_dir/src/main/kotlin/com/purride/pixelmatrix"
  mkdir -p "$app_dir/src/main/java/com/purride/pixelmatrix"
  mkdir -p "$app_dir/src/test/kotlin/com/purride/pixelmatrix"
  mkdir -p "$app_dir/src/test/java/com/purride/pixelmatrix"

  cat >"$app_dir/src/main/AndroidManifest.xml" <<'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name=".MatrixApplication"
        android:allowBackup="false"
        android:label="Pixel Matrix Consumer"
        android:supportsRtl="true" />
</manifest>
EOF

  cat >"$app_dir/src/main/kotlin/com/purride/pixelmatrix/MatrixApplication.kt" <<'EOF'
package com.purride.pixelmatrix

import android.app.Application
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.advanced.PixelLeafRenderObjectWidget
import com.purride.pixelui.advanced.PixelPaintContext
import com.purride.pixelui.advanced.PixelRenderBox
import com.purride.pixelui.advanced.PixelRenderConstraints
import com.purride.pixelui.advanced.PixelRenderObject
import com.purride.pixelui.advanced.PixelRenderSize
import com.purride.pixelui.testing.PixelTester

/** 由消费者 APK 根入口直接执行公开 Kotlin API、Java API 与自定义渲染 SPI。 */
public class MatrixApplication : Application() {
    /** 启动时验证 R8 后仍可创建、布局并绘制消费者自定义 RenderObject。 */
    override fun onCreate() {
        super.onCreate()
        /** 用于验证发布坐标中 testing API 与完整传递依赖图的离屏测试器。 */
        val tester = PixelTester()
        /** 消费者选择的确定性像素颜色。 */
        val color = PixelColor.fromRgb(37, 91, 173)
        tester.pumpWidget(
            widget = MatrixDotWidget(color),
            logicalWidth = 3,
            logicalHeight = 2,
        )
        check(tester.pixelAt(0, 0) == color)
        check(JavaVisibilityProbe.bufferSubmitNanos() == 40L)
        tester.dispose()
    }
}

/** 完全在外部消费者中实现的公开叶子 Widget。 */
public class MatrixDotWidget(
    /** 当前绘制颜色。 */
    public val color: PixelColor,
) : PixelLeafRenderObjectWidget() {
    /** 创建消费者拥有的 retained render object。 */
    override fun createRenderObject(context: BuildContext): PixelRenderObject = MatrixDotRenderObject(color)

    /** 把不可变 Widget 更新同步到 retained render object。 */
    override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
        (renderObject as MatrixDotRenderObject).updateColor(color)
    }
}

/** 只依赖稳定 advanced SPI 的消费者自定义渲染对象。 */
private class MatrixDotRenderObject(
    /** 最近一次 Widget 配置提供的颜色。 */
    private var color: PixelColor,
) : PixelRenderBox() {
    /** 选择不超过父约束的 2×2 固定尺寸。 */
    override fun layout(constraints: PixelRenderConstraints) {
        size = PixelRenderSize(
            width = constraints.constrainWidth(2),
            height = constraints.constrainHeight(2),
        )
    }

    /** 填充自定义对象占据的完整像素矩形。 */
    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        context.fillRect(offsetX, offsetY, size.width, size.height, color)
    }

    /** 颜色变化时只请求重新绘制。 */
    public fun updateColor(nextColor: PixelColor) {
        if (color == nextColor) {
            return
        }
        color = nextColor
        markNeedsPaint()
    }
}
EOF

  cat >"$app_dir/src/main/java/com/purride/pixelmatrix/JavaVisibilityProbe.java" <<'EOF'
package com.purride.pixelmatrix;

import com.purride.pixelui.PixelFrameTimings;

/** 从 Java 源码直接构造并读取稳定公开 value API。 */
public final class JavaVisibilityProbe {
    /** 工具类不允许实例化。 */
    private JavaVisibilityProbe() {
    }

    /** 返回 Java 可见的 buffer submit 字段，证明构造器和 getter 都可链接。 */
    public static long bufferSubmitNanos() {
        /** 覆盖完整公开构造器的确定性 timing 值。 */
        final PixelFrameTimings timings = new PixelFrameTimings(10L, 20L, 30L, 40L, 50L, 160L, 10L);
        return timings.getBufferSubmitNanos();
    }
}
EOF

  cat >"$app_dir/src/test/kotlin/com/purride/pixelmatrix/MatrixKotlinConsumerTest.kt" <<'EOF'
package com.purride.pixelmatrix

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证每个矩阵项都真实编译并执行 Kotlin 与消费者自定义 SPI。 */
public class MatrixKotlinConsumerTest {
    /** 外部 RenderObject 必须通过发布 AAR 完成布局和逐像素绘制。 */
    @Test
    public fun customRenderObjectRunsFromPublishedAar() {
        /** 本矩阵项独立创建的离屏测试器。 */
        val tester = PixelTester()
        /** 用于精确像素断言的消费者颜色。 */
        val color = PixelColor.fromRgb(13, 101, 211)
        tester.pumpWidget(MatrixDotWidget(color), logicalWidth = 3, logicalHeight = 2)
        assertEquals(color, tester.pixelAt(0, 0))
        assertEquals(PixelColor.Transparent, tester.pixelAt(2, 0))
        tester.dispose()
    }
}
EOF

  cat >"$app_dir/src/test/java/com/purride/pixelmatrix/MatrixJavaConsumerTest.java" <<'EOF'
package com.purride.pixelmatrix;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 验证每个矩阵项都真实编译并执行 Java 可见性边界。 */
public final class MatrixJavaConsumerTest {
    /** Java 调用必须解析到发布 AAR 的稳定构造器与 getter。 */
    @Test
    public void javaCanUsePublishedValueApi() {
        assertEquals(40L, JavaVisibilityProbe.bufferSubmitNanos());
    }
}
EOF
}

# 创建一个 AGP 8 + 显式 Kotlin plugin 消费者。
create_legacy_project() {
  # 当前矩阵项标识。
  local matrix_id="$1"
  # 当前 AGP 版本。
  local agp_version="$2"
  # 当前 compileSdk 整数版本。
  local compile_sdk="$3"
  # 当前 Gradle 版本。
  local gradle_version="$4"
  # 当前消费者工程目录。
  local project_dir="$TMP_DIR/$matrix_id"
  mkdir -p "$project_dir/app"
  prepare_wrapper "$project_dir" "$gradle_version"
  write_shared_sources "$project_dir/app"

  cat >"$project_dir/settings.gradle.kts" <<'EOF'
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
            url = uri(providers.gradleProperty("pixelCompatibilityRepository").get())
            isAllowInsecureProtocol = url.scheme == "http"
            content { includeGroup("com.purride") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "PixelConsumerMatrixLegacy"
include(":app")
EOF

  cat >"$project_dir/build.gradle.kts" <<EOF
plugins {
    id("com.android.application") version "$agp_version" apply false
    id("org.jetbrains.kotlin.android") version "$MINIMUM_KOTLIN" apply false
}
EOF

  cat >"$project_dir/app/build.gradle.kts" <<EOF
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.purride.pixelmatrix"
    compileSdk = $compile_sdk

    defaultConfig {
        applicationId = "com.purride.pixelmatrix.$matrix_id"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        getByName("release") {
            /** 开启消费者侧 R8，验证全部传递 consumer rules。 */
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

kotlin {
    compilerOptions {
        /** Kotlin 与 SDK 的 Java 11 字节码边界保持一致。 */
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation("com.purride:pixel-engine:$PIXEL_VERSION")
    testImplementation("junit:junit:4.13.2")
}

/** 输出本矩阵项实际加载的 Kotlin compiler 版本。 */
tasks.register("pixelMatrixEnvironment") {
    doLast {
        /** 通过编译器公开常量读取实际 KGP 版本。 */
        val versionClass = Class.forName("org.jetbrains.kotlin.config.KotlinCompilerVersion")
        println("PIXEL_KOTLIN_VERSION=" + versionClass.getField("VERSION").get(null))
    }
}
EOF
}

# 创建一个 AGP 9 内置 Kotlin + Android 36.1 消费者。
create_recommended_project() {
  # 推荐消费者工程目录。
  local project_dir="$TMP_DIR/recommended"
  mkdir -p "$project_dir/app"
  prepare_wrapper "$project_dir" "$RECOMMENDED_GRADLE"
  write_shared_sources "$project_dir/app"

  cat >"$project_dir/settings.gradle.kts" <<'EOF'
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
            url = uri(providers.gradleProperty("pixelCompatibilityRepository").get())
            isAllowInsecureProtocol = url.scheme == "http"
            content { includeGroup("com.purride") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "PixelConsumerMatrixRecommended"
include(":app")
EOF

  cat >"$project_dir/build.gradle.kts" <<EOF
plugins {
    id("com.android.application") version "$RECOMMENDED_AGP" apply false
}
EOF

  cat >"$project_dir/app/build.gradle.kts" <<EOF
plugins {
    id("com.android.application")
}

android {
    namespace = "com.purride.pixelmatrix"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.purride.pixelmatrix.recommended"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        getByName("release") {
            /** 开启消费者侧 R8，覆盖 AGP 9.1 默认重打包行为。 */
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation("com.purride:pixel-engine:$PIXEL_VERSION")
    testImplementation("junit:junit:4.13.2")
}

/** 输出本矩阵项实际加载的 AGP 内置 Kotlin compiler 版本。 */
tasks.register("pixelMatrixEnvironment") {
    doLast {
        /** 通过编译器公开常量读取实际内置 KGP 版本。 */
        val versionClass = Class.forName("org.jetbrains.kotlin.config.KotlinCompilerVersion")
        println("PIXEL_KOTLIN_VERSION=" + versionClass.getField("VERSION").get(null))
    }
}
EOF
}

# 运行一个支持组合并验证测试、debug APK、R8 release APK 与 mapping 均真实产生。
run_supported() {
  # 当前矩阵项标识。
  local matrix_id="$1"
  # 当前矩阵项工程目录。
  local project_dir="$TMP_DIR/$matrix_id"
  # 当前矩阵项完整构建日志。
  local log_file="$REPORT_DIR/$matrix_id.log"
  "$project_dir/gradlew" \
    -p "$project_dir" \
    -PpixelCompatibilityRepository="$COMPATIBILITY_REPOSITORY_URI" \
    :app:testDebugUnitTest \
    :app:assembleDebug \
    :app:assembleRelease \
    :app:pixelMatrixEnvironment \
    --no-daemon \
    2>&1 | tee "$log_file"

  # 当前矩阵项 debug APK。
  local debug_apk="$project_dir/app/build/outputs/apk/debug/app-debug.apk"
  # 当前矩阵项 minified release APK。
  local release_apk="$project_dir/app/build/outputs/apk/release/app-release-unsigned.apk"
  # 当前矩阵项 R8 mapping，存在即证明 release 真正执行 shrinker。
  local mapping_file="$project_dir/app/build/outputs/mapping/release/mapping.txt"
  # 当前矩阵项 JUnit XML，必须同时记录 Kotlin 与 Java 两个测试。
  local kotlin_test_report="$project_dir/app/build/test-results/testDebugUnitTest/TEST-com.purride.pixelmatrix.MatrixKotlinConsumerTest.xml"
  # 当前矩阵项 Java 可见性测试的独立 JUnit XML。
  local java_test_report="$project_dir/app/build/test-results/testDebugUnitTest/TEST-com.purride.pixelmatrix.MatrixJavaConsumerTest.xml"
  [[ -s "$debug_apk" ]] || { echo "$matrix_id did not produce a debug APK" >&2; return 1; }
  [[ -s "$release_apk" ]] || { echo "$matrix_id did not produce a minified release APK" >&2; return 1; }
  [[ -s "$mapping_file" ]] || { echo "$matrix_id did not produce an R8 mapping" >&2; return 1; }
  [[ -s "$kotlin_test_report" ]] || { echo "$matrix_id did not execute Kotlin tests" >&2; return 1; }
  [[ -s "$java_test_report" ]] || { echo "$matrix_id did not execute Java tests" >&2; return 1; }
  grep -q "PIXEL_KOTLIN_VERSION=" "$log_file" || {
    echo "$matrix_id did not report its actual Kotlin compiler version" >&2
    return 1
  }
}

# 运行一个明确不支持的组合并保留依赖解析阶段的预期失败日志。
run_unsupported() {
  # 当前负例矩阵项标识。
  local matrix_id="$1"
  # 错误信息中必须出现的边界提示。
  local expected_message="$2"
  # 当前负例工程目录。
  local project_dir="$TMP_DIR/$matrix_id"
  # 当前负例原始失败日志。
  local log_file="$REPORT_DIR/$matrix_id.log"
  set +e
  "$project_dir/gradlew" \
    -p "$project_dir" \
    -PpixelCompatibilityRepository="$COMPATIBILITY_REPOSITORY_URI" \
    :app:checkDebugAarMetadata \
    --no-daemon \
    >"$log_file" 2>&1
  # 预期失败命令的退出码。
  local exit_code=$?
  set -e
  if [[ "$exit_code" -eq 0 ]]; then
    echo "$matrix_id unexpectedly accepted an unsupported consumer" >&2
    return 1
  fi
  if ! grep -Eiq "$expected_message" "$log_file"; then
    echo "$matrix_id failed without the documented compatibility message" >&2
    tail -80 "$log_file" >&2
    return 1
  fi
}

create_legacy_project "minimum" "$MINIMUM_AGP" 36 "$MINIMUM_GRADLE"
create_recommended_project
create_legacy_project "unsupported-compile-sdk" "$MINIMUM_AGP" 35 "$MINIMUM_GRADLE"
create_legacy_project "unsupported-agp" "8.9.0" 36 "8.11.1"

run_supported "minimum"
run_supported "recommended"
run_unsupported "unsupported-compile-sdk" "compileSdk.*36|compile against version 36"
run_unsupported "unsupported-agp" "Android Gradle plugin.*8\.10\.0|AGP.*8\.10\.0"

# 两个支持组合实测到的 Kotlin compiler 版本。
MINIMUM_ACTUAL_KOTLIN="$(sed -n 's/.*PIXEL_KOTLIN_VERSION=//p' "$REPORT_DIR/minimum.log" | tail -1)"
RECOMMENDED_ACTUAL_KOTLIN="$(sed -n 's/.*PIXEL_KOTLIN_VERSION=//p' "$REPORT_DIR/recommended.log" | tail -1)"
# 发布报告摘要用于把矩阵结果绑定到本轮真实 Maven 产物。
PUBLICATION_SHA256="$(shasum -a 256 "$REPORT_DIR/publication.json" | awk '{print $1}')"

cat >"$REPORT_DIR/matrix.json" <<EOF
{
  "schemaVersion": 1,
  "status": "passed",
  "artifact": "com.purride:pixel-engine:$PIXEL_VERSION",
  "publicationReportSha256": "$PUBLICATION_SHA256",
  "supported": [
    {
      "id": "minimum",
      "agp": "$MINIMUM_AGP",
      "gradle": "$MINIMUM_GRADLE",
      "kotlin": "$MINIMUM_ACTUAL_KOTLIN",
      "compileSdk": "36",
      "debug": "passed",
      "minifiedRelease": "passed",
      "kotlinSpi": "passed",
      "javaVisibility": "passed",
      "tests": 2
    },
    {
      "id": "recommended",
      "agp": "$RECOMMENDED_AGP",
      "gradle": "$RECOMMENDED_GRADLE",
      "kotlin": "$RECOMMENDED_ACTUAL_KOTLIN",
      "kotlinMode": "$RECOMMENDED_KOTLIN",
      "compileSdk": "36.1",
      "debug": "passed",
      "minifiedRelease": "passed",
      "kotlinSpi": "passed",
      "javaVisibility": "passed",
      "tests": 2
    }
  ],
  "unsupported": [
    {
      "id": "unsupported-compile-sdk",
      "agp": "$MINIMUM_AGP",
      "gradle": "$MINIMUM_GRADLE",
      "kotlin": "$MINIMUM_KOTLIN",
      "compileSdk": "35",
      "status": "expected_failure",
      "boundary": "AAR minCompileSdk=36"
    },
    {
      "id": "unsupported-agp",
      "agp": "8.9.0",
      "gradle": "8.11.1",
      "kotlin": "$MINIMUM_KOTLIN",
      "compileSdk": "36",
      "status": "expected_failure",
      "boundary": "AAR minAndroidGradlePluginVersion=8.10.0"
    }
  ]
}
EOF

echo "Pixel consumer compatibility matrix passed: $REPORT_DIR/matrix.json"
