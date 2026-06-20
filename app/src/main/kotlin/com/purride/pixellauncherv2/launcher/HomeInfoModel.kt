package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.viewmodel.LauncherUiState

enum class HomeInfoAction {
    RAIN,
    CALL,
    SMS,
    ALARM,
    BATTERY,
    NOTIFICATION,
    USAGE,
}

data class HomeInfoLine(
    val text: String,
    val action: HomeInfoAction? = null,
)

object HomeInfoModel {

    fun lines(state: LauncherState): List<HomeInfoLine> = lines(
        missedCallCount = state.missedCallCount,
        unreadSmsCount = state.unreadSmsCount,
        nextAlarmText = state.nextAlarmText,
        isCharging = state.isCharging,
        batteryLevel = state.batteryLevel,
        notificationSummaryText = state.notificationSummaryText,
        notificationCount = state.notificationCount,
        screenUsageTimeText = state.screenUsageTimeText,
        screenOpenCountText = state.screenOpenCountText,
        terminalStatusText = state.terminalStatusText,
    )

    fun lines(state: LauncherUiState): List<HomeInfoLine> = buildList {
        addAll(
            lines(
                missedCallCount = state.missedCallCount,
                unreadSmsCount = state.unreadSmsCount,
                nextAlarmText = state.nextAlarmText,
                isCharging = state.isCharging,
                batteryLevel = state.batteryLevel,
                notificationSummaryText = state.notificationSummaryText,
                notificationCount = state.notificationCount,
                screenUsageTimeText = state.screenUsageTimeText,
                screenOpenCountText = state.screenOpenCountText,
                terminalStatusText = state.terminalStatusText,
            ),
        )
    }

    fun summary(state: LauncherState): String = rowCountLabel(lines(state).size)

    fun summary(state: LauncherUiState): String = rowCountLabel(lines(state).size)

    fun weatherLine(state: LauncherState): HomeInfoLine = weatherLine(
        hasLocationPermission = state.hasLocationPermission,
        weatherSummary = state.rainHintText,
    )

    fun weatherLine(state: LauncherUiState): HomeInfoLine = weatherLine(
        hasLocationPermission = state.hasLocationPermission,
        weatherSummary = state.rainHintText,
    )

    private fun lines(
        missedCallCount: Int,
        unreadSmsCount: Int,
        nextAlarmText: String,
        isCharging: Boolean,
        batteryLevel: Int,
        notificationSummaryText: String,
        notificationCount: Int,
        screenUsageTimeText: String,
        screenOpenCountText: String,
        terminalStatusText: String,
    ): List<HomeInfoLine> = buildList {
        if (missedCallCount > 0) {
            add(HomeInfoLine("CALL $missedCallCount", HomeInfoAction.CALL))
        }
        if (unreadSmsCount > 0) {
            add(HomeInfoLine("SMS $unreadSmsCount", HomeInfoAction.SMS))
        }
        val notificationLine = notificationLine(notificationSummaryText, notificationCount)
        if (notificationLine != null) {
            add(HomeInfoLine(notificationLine, HomeInfoAction.NOTIFICATION))
        }
        if (nextAlarmText.isNotBlank() && nextAlarmText != "--:--") {
            add(HomeInfoLine("ALARM $nextAlarmText", HomeInfoAction.ALARM))
        }

        if (!isCharging && batteryLevel <= LOW_BATTERY_THRESHOLD) {
            add(HomeInfoLine("BATTERY $batteryLevel%", HomeInfoAction.BATTERY))
        }
        add(
            HomeInfoLine(
                text = "USE ${screenUsageTimeText.ifBlank { "--:--" }}  " +
                    "OPEN ${screenOpenCountText.ifBlank { "--" }}",
                action = HomeInfoAction.USAGE,
            ),
        )

        if (terminalStatusText.isNotBlank()) {
            add(HomeInfoLine(terminalStatusText))
        }
    }.take(MAX_HOME_INFO_LINES)

    private const val MAX_HOME_INFO_LINES = 3
    private const val LOW_BATTERY_THRESHOLD = 15

    private fun notificationLine(summaryText: String, count: Int): String? {
        val summary = summaryText.trim()
        if (summary.isEmpty() || count <= 0) return null
        return "NOTIFY $summary"
    }

    private fun weatherLine(
        hasLocationPermission: Boolean,
        weatherSummary: String,
    ): HomeInfoLine {
        val summary = weatherSummary.trim()
        val text = when {
            !hasLocationPermission || summary == "LOC" -> "WEATHER LOC"
            summary.isEmpty() -> "WEATHER --"
            else -> summary
        }
        return HomeInfoLine(text = text, action = HomeInfoAction.RAIN)
    }

    private fun rowCountLabel(count: Int): String = if (count == 1) "1 ROW" else "$count ROWS"
}
