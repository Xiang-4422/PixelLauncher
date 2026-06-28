package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixellauncherv2.viewmodel.LauncherUiState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for [DiagnosticsModel.lines] — the diagnostic rows (titles +
 * package-tail / battery / display formatting and blank fallbacks) and parity
 * between the LauncherState and LauncherUiState overloads. JVM-safe; no Android
 * dependencies.
 */
class DiagnosticsModelTest {

    private val profile = ScreenProfile(logicalWidth = 120, logicalHeight = 240, dotSizePx = 4)

    @Test
    fun lines_buildsRowsInExpectedOrder() {
        val titles = DiagnosticsModel.lines(LauncherState(), profile).map { it.title }
        assertEquals(
            listOf(
                "HOME",
                "DATA",
                "USAGE",
                "LAUNCHES",
                "LAST",
                "RECENT",
                "FONT",
                "8PX",
                "10PX",
                "12PX",
                "TEXT",
                "TEXT MAX",
                "TEXT RISK",
                "DISPLAY",
                "STATUS",
                "BOUNDS",
                "POWER",
                "DEBUG",
            ),
            titles,
        )
    }

    @Test
    fun lines_formatsLaunchLastAndRecentFromPackageTails() {
        val state = LauncherState(
            launchCount = 7,
            lastLaunchPackageName = "com.android.chrome",
            recentApps = listOf("com.google.calculator", "com.x.y"),
        )
        val byTitle = DiagnosticsModel.lines(state, profile).associate { it.title to it.value }
        assertEquals("7", byTitle["LAUNCHES"])
        assertEquals("CHROME", byTitle["LAST"])
        assertEquals("CALCULAT", byTitle["RECENT"]) // "CALCULATOR" truncated to 8 chars
    }

    @Test
    fun lines_fallsBackWhenNoLaunchOrRecentApps() {
        val byTitle = DiagnosticsModel.lines(LauncherState(), profile).associate { it.title to it.value }
        assertEquals("NONE", byTitle["LAST"])
        assertEquals("0", byTitle["RECENT"])
    }

    @Test
    fun lines_exposesHomeAndDataSummariesForAdvancedOverview() {
        val state = LauncherState(
            missedCallCount = 1,
            unreadSmsCount = 2,
            hasUsageAccess = true,
            hasLocationPermission = true,
            hasCallLogPermission = true,
            hasSmsReadPermission = true,
            isDefaultSmsApp = true,
            smsPermissionState = SmsPermissionState.READY,
            hasPostNotificationPermission = true,
            hasNotificationListenerAccess = true,
        )
        val byTitle = DiagnosticsModel.lines(state, profile).associate { it.title to it.value }

        assertEquals("1 ROW", byTitle["HOME"])
        assertEquals("OK", byTitle["DATA"])
        assertEquals("EVENTS", byTitle["USAGE"])
    }

    @Test
    fun lines_displayAndPowerReflectProfileAndBattery() {
        val state = LauncherState(batteryLevel = 80, isCharging = true)
        val profileWithStatus = profile.copy(statusBarHeight = 14)
        val byTitle = DiagnosticsModel.lines(state, profileWithStatus).associate { it.title to it.value }
        assertEquals("120X240", byTitle["DISPLAY"])
        assertEquals("14/14", byTitle["STATUS"])
        assertEquals("80% CHG", byTitle["POWER"])
    }

    @Test
    fun lines_exposesFontMetricsForUiDiagnostics() {
        val byTitle = DiagnosticsModel.lines(LauncherState(), profile).associate { it.title to it.value }

        assertEquals("FUSION 10PX", byTitle["FONT"])
        assertEquals("C8 B7 A4/8", byTitle["8PX"])
        assertEquals("C10 B9 A6/10", byTitle["10PX"])
        assertEquals("C12 B11 A8/12", byTitle["12PX"])
        assertEquals("RISK 1 138/116", byTitle["TEXT"])
        assertEquals("NO ACCESS", byTitle["USAGE"])
        assertEquals("DATA 138/116", byTitle["TEXT MAX"])
        assertEquals("1", byTitle["TEXT RISK"])
        assertEquals("0/12", byTitle["STATUS"])
        assertEquals("OK 18 ROW", byTitle["BOUNDS"])
        assertEquals("DATA HEALTH", byTitle["DEBUG"])
    }

    @Test
    fun lines_powerOmitsChargeSuffixWhenNotCharging() {
        val state = LauncherState(batteryLevel = 55, isCharging = false)
        val byTitle = DiagnosticsModel.lines(state, profile).associate { it.title to it.value }
        assertEquals("55%", byTitle["POWER"])
    }

    @Test
    fun lines_uiStateOverloadMatchesLauncherStateOverload() {
        val launcherState = LauncherState(
            launchCount = 3,
            lastLaunchPackageName = "com.a.b",
            recentApps = listOf("com.c.d"),
            batteryLevel = 42,
            isCharging = true,
            hasLocationPermission = true,
        )
        val uiState = LauncherUiState(
            launchCount = 3,
            lastLaunchPackageName = "com.a.b",
            recentApps = listOf("com.c.d"),
            batteryLevel = 42,
            isCharging = true,
            hasLocationPermission = true,
        )
        assertEquals(
            DiagnosticsModel.lines(launcherState, profile),
            DiagnosticsModel.lines(uiState, profile),
        )
    }
}
