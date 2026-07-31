package com.purride.pixellauncherv2.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 手势相机调试帧的 Release 三层门禁接线契约。
 *
 * 调试帧包含相机画面：Release 用户没有 HAND DEBUG 开关，任何一层漏接都会变成
 * "无法关闭的调试相机画面"（隐私与性能风险）。三层各自独立成立：
 *
 * 1. 采集端断源：AppContainer 构造 HandTrackingRepository 时传 `debugFramesEnabled = BuildConfig.DEBUG`；
 * 2. 状态归一化：AppContainer 构造 FontSettingsRepository 时传 `handDebugSettingAllowed = BuildConfig.DEBUG`；
 * 3. 状态边界与显示端合取：MainActivity 的 applyUiBehavior、调试帧回调与 overlay 同步
 *    都以 `BuildConfig.DEBUG` 合取。
 *
 * 两个仓库依赖 CameraX/MediaPipe 与 Android SharedPreferences，无法在 JVM 中整机实例化，
 * 因此按仓库既有契约测试口径（见 [AppContainerContractTest]）对接线做源码校验；
 * FontSettingsRepository 的归一化行为另有 FontSettingsRepositoryHandDebugTest 直接验证。
 */
class HandDebugReleaseGateContractTest {

    @Test
    fun appContainerWiresBuildTypeGateIntoBothRepositories() {
        val containerSource = readSource("app/AppContainer.kt")
        assertTrue(
            "HandTrackingRepository 必须以 BuildConfig.DEBUG 断源采集端",
            containerSource.contains("debugFramesEnabled = BuildConfig.DEBUG"),
        )
        assertTrue(
            "FontSettingsRepository 必须以 BuildConfig.DEBUG 归一化持久化偏好",
            containerSource.contains("handDebugSettingAllowed = BuildConfig.DEBUG"),
        )
    }

    @Test
    fun mainActivityGatesStateBoundaryAndDisplaySites() {
        val mainActivitySource = readSource("app/MainActivity.kt")
        assertTrue(
            "applyUiBehavior 必须在状态边界用 BuildConfig.DEBUG 合取手势调试开关",
            mainActivitySource.contains("BuildConfig.DEBUG && isPixelMatterHandDebugEnabled"),
        )
        assertTrue(
            "调试帧显示回调必须用 BuildConfig.DEBUG 合取",
            mainActivitySource.contains("BuildConfig.DEBUG && state.isPixelMatterHandDebugEnabled"),
        )
    }

    private fun readSource(relativePath: String): String {
        val workingDirectory = File(".").canonicalFile
        val moduleRoot = if (workingDirectory.name == "app") workingDirectory else workingDirectory.resolve("app")
        return moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/$relativePath")
            .readText()
    }
}
