package com.purride.pixellauncherv2.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [AppContainer] 无法在 JVM 单元测试中真正实例化（依赖真实 Android [android.content.Context]）。
 * 这里只做静态契约校验：容器边界存在、覆盖了预期的仓库，且 MainActivity 不再自行 new 这些仓库。
 */
class AppContainerContractTest {

    @Test
    fun appContainerConstructsEveryRepositoryMainActivityNeeds() {
        val containerSource = readSource("AppContainer.kt")

        REPOSITORY_CONSTRUCTOR_CALLS.forEach { constructorCall ->
            assertTrue(
                "AppContainer must construct $constructorCall.",
                containerSource.contains(constructorCall),
            )
        }
    }

    @Test
    fun appContainerDoesNotExposeMutableGlobalTestOverrides() {
        val containerSource = readSource("AppContainer.kt")

        assertFalse(
            "AppContainer must not expose a companion object (no global mutable overrides for tests).",
            containerSource.contains("companion object"),
        )
        assertFalse(
            "AppContainer properties must not be reassignable after construction.",
            containerSource.contains("var appRepository") ||
                containerSource.contains("var smsRepository"),
        )
    }

    @Test
    fun mainActivityObtainsRepositoriesFromContainerInsteadOfNewingThem() {
        val mainActivitySource = readSource("MainActivity.kt")

        assertTrue(
            "MainActivity must assemble its container before reading dependencies from it.",
            mainActivitySource.contains("appContainer = AppContainer("),
        )
        DIRECT_REPOSITORY_CONSTRUCTOR_CALLS.forEach { constructorCall ->
            assertFalse(
                "MainActivity.onCreate must no longer directly construct $constructorCall; it must come from AppContainer.",
                mainActivitySource.contains(constructorCall),
            )
        }
        REPOSITORY_PROPERTY_NAMES.forEach { propertyName ->
            assertTrue(
                "MainActivity must read $propertyName from appContainer.",
                mainActivitySource.contains("$propertyName = appContainer.$propertyName"),
            )
        }
    }

    private fun readSource(fileName: String): String {
        return moduleRoot()
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/app/$fileName")
            .readText()
    }

    private fun moduleRoot(): File {
        val workingDirectory = File(".").canonicalFile
        return if (workingDirectory.name == "app") workingDirectory else workingDirectory.resolve("app")
    }

    private companion object {
        val REPOSITORY_CONSTRUCTOR_CALLS = listOf(
            "PackageManagerAppRepository(appContext)",
            "AppCustomizationRepository(appContext)",
            "FontSettingsRepository(appContext)",
            "LauncherStatsRepository(appContext)",
            "DeviceStatusRepository(appContext)",
            "NextAlarmRepository(appContext)",
            "ScreenUsageRepository(appContext)",
            "CommunicationStatusRepository(appContext)",
            "NotificationSummaryRepository()",
            "NotificationSummarySettingsRepository(appContext)",
            "MediaPlaybackRepository(",
            "DeviceLocationRepository(appContext)",
            "DeviceMotionRepository(appContext)",
            "HandTrackingRepository(",
            "RainForecastRepository()",
        )

        val DIRECT_REPOSITORY_CONSTRUCTOR_CALLS = listOf(
            "PackageManagerAppRepository(applicationContext)",
            "AppCustomizationRepository(applicationContext)",
            "FontSettingsRepository(applicationContext)",
            "LauncherStatsRepository(applicationContext)",
            "DeviceStatusRepository(applicationContext)",
            "NextAlarmRepository(applicationContext)",
            "ScreenUsageRepository(applicationContext)",
            "CommunicationStatusRepository(applicationContext)",
            "DeviceLocationRepository(applicationContext)",
            "DeviceMotionRepository(applicationContext)",
        )

        val REPOSITORY_PROPERTY_NAMES = listOf(
            "appRepository",
            "appCustomizationRepository",
            "fontSettingsRepository",
            "launcherStatsRepository",
            "deviceStatusRepository",
            "nextAlarmRepository",
            "screenUsageRepository",
            "communicationStatusRepository",
            "notificationSummaryRepository",
            "notificationSummarySettingsRepository",
            "mediaPlaybackRepository",
            "deviceLocationRepository",
            "deviceMotionRepository",
            "handTrackingRepository",
            "rainForecastRepository",
        )
    }
}
