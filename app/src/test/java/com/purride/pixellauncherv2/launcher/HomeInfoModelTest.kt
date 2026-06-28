package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.viewmodel.LauncherUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeInfoModelTest {

    @Test
    fun quietStateShowsUsageOnly() {
        val state = LauncherUiState(
            rainHintText = "",
            hasLocationPermission = true,
            screenUsageTimeText = "",
            screenOpenCountText = "",
        )
        val lines = HomeInfoModel.lines(state)

        assertEquals(
            listOf(
                "USE --:--  OPEN --" to HomeInfoAction.USAGE,
            ),
            lines.map { it.text to it.action },
        )
        assertEquals("1 ROW", HomeInfoModel.summary(state))
        assertEquals(
            "WEATHER --" to HomeInfoAction.RAIN,
            HomeInfoModel.weatherLine(state).let { it.text to it.action },
        )
    }

    @Test
    fun communicationCountsDoNotConsumeHomeInfoRows() {
        val lines = HomeInfoModel.lines(
            LauncherUiState(
                rainHintText = "RAIN 12:00",
                hasLocationPermission = true,
                missedCallCount = 2,
                unreadSmsCount = 3,
                nextAlarmText = "07:30",
                batteryLevel = 8,
                screenUsageTimeText = "00:10",
                screenOpenCountText = "4",
            ),
        )

        assertEquals(
            listOf(
                "ALARM 07:30" to HomeInfoAction.ALARM,
                "BATTERY 8%" to HomeInfoAction.BATTERY,
                "USE 00:10  OPEN 4" to HomeInfoAction.USAGE,
            ),
            lines.map { it.text to it.action },
        )
    }

    @Test
    fun notificationSummaryShowsAfterAllHomeStatusLines() {
        val lines = HomeInfoModel.lines(
            LauncherUiState(
                missedCallCount = 1,
                unreadSmsCount = 1,
                notificationSummaryText = "BANK OTP",
                notificationCount = 1,
                nextAlarmText = "07:30",
                hasLocationPermission = true,
                screenUsageTimeText = "00:20",
                screenOpenCountText = "2",
            ),
        )

        assertEquals(
            listOf(
                "ALARM 07:30" to HomeInfoAction.ALARM,
                "USE 00:20  OPEN 2" to HomeInfoAction.USAGE,
                "NOTIFY BANK OTP" to HomeInfoAction.NOTIFICATION,
            ),
            lines.map { it.text to it.action },
        )
    }

    @Test
    fun notificationSummarySplitsVisibleNotificationsIntoTailRows() {
        val lines = HomeInfoModel.lines(
            LauncherUiState(
                notificationSummaryText = "CAL MEET  BANK OTP",
                notificationCount = 2,
                nextAlarmText = "07:30",
                hasLocationPermission = true,
                screenUsageTimeText = "00:20",
                screenOpenCountText = "2",
            ),
        )

        assertEquals(
            listOf(
                "ALARM 07:30" to HomeInfoAction.ALARM,
                "USE 00:20  OPEN 2" to HomeInfoAction.USAGE,
                "NOTIFY CAL MEET" to HomeInfoAction.NOTIFICATION,
                "NOTIFY BANK OTP" to HomeInfoAction.NOTIFICATION,
            ),
            lines.map { it.text to it.action },
        )
    }

    @Test
    fun notificationSummaryIsRenderedAfterUsage() {
        val lines = HomeInfoModel.lines(
            LauncherUiState(
                notificationSummaryText = "BANK OTP",
                notificationCount = 1,
                hasLocationPermission = true,
                screenUsageTimeText = "00:20",
                screenOpenCountText = "2",
            ),
        )

        assertEquals(
            listOf(
                "USE 00:20  OPEN 2" to HomeInfoAction.USAGE,
                "NOTIFY BANK OTP" to HomeInfoAction.NOTIFICATION,
            ),
            lines.map { it.text to it.action },
        )
    }

    @Test
    fun lowBatteryComesBeforeUsageWithoutDuplicatingWeather() {
        val lines = HomeInfoModel.lines(
            LauncherUiState(
                rainHintText = "RAIN 12C",
                hasLocationPermission = true,
                batteryLevel = 12,
                isCharging = false,
                screenUsageTimeText = "01:00",
                screenOpenCountText = "8",
            ),
        )

        assertEquals(
            listOf(
                "BATTERY 12%" to HomeInfoAction.BATTERY,
                "USE 01:00  OPEN 8" to HomeInfoAction.USAGE,
            ),
            lines.map { it.text to it.action },
        )
        assertEquals(
            "RAIN 12C" to HomeInfoAction.RAIN,
            HomeInfoModel.weatherLine(
                LauncherUiState(
                    rainHintText = "RAIN 12C",
                    hasLocationPermission = true,
                ),
            ).let { it.text to it.action },
        )
    }

    @Test
    fun ordinaryWeatherIsAlwaysAvailableOutsidePriorityLines() {
        val state = LauncherUiState(
            rainHintText = "CLEAR 22C",
            hasLocationPermission = true,
            screenUsageTimeText = "00:20",
            screenOpenCountText = "2",
        )
        val lines = HomeInfoModel.lines(state)

        assertEquals(
            "CLEAR 22C" to HomeInfoAction.RAIN,
            HomeInfoModel.weatherLine(state).let { it.text to it.action },
        )
        assertEquals(
            listOf("USE 00:20  OPEN 2" to HomeInfoAction.USAGE),
            lines.map { it.text to it.action },
        )
    }

    @Test
    fun unavailableLocationKeepsWeatherComponentVisible() {
        val missingPermission = LauncherUiState(
            rainHintText = "",
            hasLocationPermission = false,
        )
        val unavailableLocation = LauncherUiState(
            rainHintText = "LOC",
            hasLocationPermission = true,
        )

        listOf(missingPermission, unavailableLocation).forEach { state ->
            assertEquals(
                "WEATHER LOC" to HomeInfoAction.RAIN,
                HomeInfoModel.weatherLine(state).let { it.text to it.action },
            )
        }
    }

    @Test
    fun attentionWeatherDoesNotConsumePriorityLine() {
        val state = LauncherUiState(
            rainHintText = "RAIN 12C",
            rainUpdatedTimeText = "09:41",
            hasLocationPermission = true,
            screenUsageTimeText = "00:20",
            screenOpenCountText = "2",
        )
        val lines = HomeInfoModel.lines(state)

        assertEquals(
            "RAIN 12C" to HomeInfoAction.RAIN,
            HomeInfoModel.weatherLine(state).let { it.text to it.action },
        )
        assertEquals(
            listOf("USE 00:20  OPEN 2" to HomeInfoAction.USAGE),
            lines.map { it.text to it.action },
        )
    }

    @Test
    fun chargingSuppressesLowBatteryWarning() {
        val lines = HomeInfoModel.lines(
            LauncherUiState(
                batteryLevel = 8,
                isCharging = true,
                hasLocationPermission = true,
                screenUsageTimeText = "00:20",
                screenOpenCountText = "2",
            ),
        )

        assertEquals(
            listOf("USE 00:20  OPEN 2" to HomeInfoAction.USAGE),
            lines.map { it.text to it.action },
        )
    }

    @Test
    fun missingLocationPermissionDoesNotConsumePriorityLine() {
        val state = LauncherUiState(
            rainHintText = "",
            hasLocationPermission = false,
            screenUsageTimeText = "00:20",
            screenOpenCountText = "2",
        )
        val lines = HomeInfoModel.lines(state)

        assertEquals(
            listOf("USE 00:20  OPEN 2" to HomeInfoAction.USAGE),
            lines.map { it.text to it.action },
        )
        assertEquals("WEATHER LOC", HomeInfoModel.weatherLine(state).text)
    }

    @Test
    fun launcherStateOverloadMatchesUiStateForSettingsSummary() {
        val launcherState = LauncherState(
            missedCallCount = 1,
            unreadSmsCount = 2,
            nextAlarmText = "07:30",
            hasLocationPermission = true,
            screenUsageTimeText = "00:20",
            screenOpenCountText = "2",
        )
        val uiState = LauncherUiState(
            missedCallCount = 1,
            unreadSmsCount = 2,
            nextAlarmText = "07:30",
            hasLocationPermission = true,
            screenUsageTimeText = "00:20",
            screenOpenCountText = "2",
        )

        assertEquals(HomeInfoModel.lines(uiState), HomeInfoModel.lines(launcherState))
        assertEquals("2 ROWS", HomeInfoModel.summary(launcherState))
        assertEquals(HomeInfoModel.summary(uiState), HomeInfoModel.summary(launcherState))
        assertEquals(HomeInfoModel.weatherLine(uiState), HomeInfoModel.weatherLine(launcherState))
    }
}
