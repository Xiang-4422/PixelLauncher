package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 防止 Launcher 偏离单模块 Pixel Engine、显式 Engine 或类型化路由的静态集成门禁。 */
class LauncherPixelEngineIntegrationStaticTest {

    /** 验证生产依赖只通过唯一的 Pixel Engine SDK 模块接入。 */
    @Test
    fun appUsesSinglePixelEngineDependency() {
        /** 当前 app 模块目录。 */
        val moduleRoot = resolveModuleRoot()
        /** app 的 Gradle 构建脚本。 */
        val buildScript = moduleRoot.resolve("build.gradle.kts").readText()

        assertTrue(buildScript.contains("implementation(project(\":pixel-engine\"))"))
        assertFalse(buildScript.contains("implementation(project(\":pixel-android\"))"))
    }

    /** 验证 Host 显式绑定 Engine、typed Navigator 和终态释放。 */
    @Test
    fun rootHostUsesNewEngineAndTypedRoutes() {
        /** 当前 app 模块目录。 */
        val moduleRoot = resolveModuleRoot()
        /** Launcher Host 的生产源码。 */
        val hostSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherRootHost.kt")
            .readText()
        /** Engine 工厂的生产源码。 */
        val engineSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherPixelEngine.kt")
            .readText()
        /** Activity 生命周期接线源码。 */
        val activitySource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/app/MainActivity.kt")
            .readText()

        assertTrue(engineSource.contains("PixelEngine.Builder()"))
        assertTrue(engineSource.contains(".logger("))
        assertTrue(engineSource.contains(".errorReporter("))
        assertTrue(engineSource.contains(".hostServices("))
        assertTrue(hostSource.contains("engine = engine"))
        assertTrue(hostSource.contains("PixelNavigator.typed("))
        assertTrue(hostSource.contains("PixelRouteRequest("))
        assertTrue(hostSource.contains("navigator.entries.map"))
        assertFalse(hostSource.contains("PixelRoute("))
        assertFalse(hostSource.contains("navigator.stack"))
        assertFalse(activitySource.contains(".hostBridge"))
        assertTrue(activitySource.contains("launcherRootHost.dispose()"))
    }

    /** 从 Gradle 测试工作目录向上定位 app 模块。 */
    private fun resolveModuleRoot(): File {
        /** Gradle 测试进程提供的非空工作目录。 */
        val workingDirectory = System.getProperty("user.dir")
            ?: error("Gradle test process did not provide user.dir")
        /** 测试进程当前目录或其 app 子目录。 */
        val current = File(workingDirectory).canonicalFile
        return sequenceOf(current, current.resolve("app"))
            .firstOrNull { candidate ->
                candidate.resolve("src/main/kotlin").isDirectory &&
                    candidate.resolve("build.gradle.kts").isFile
            }
            ?: error("Unable to resolve app module root from $current")
    }
}
