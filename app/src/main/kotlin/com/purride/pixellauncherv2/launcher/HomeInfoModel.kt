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
        nextAlarmText = state.nextAlarmText,
        isCharging = state.isCharging,
        batteryLevel = state.batteryLevel,
        screenUsageTimeText = state.screenUsageTimeText,
        screenOpenCountText = state.screenOpenCountText,
    )

    fun lines(state: LauncherUiState): List<HomeInfoLine> = lines(
        nextAlarmText = state.nextAlarmText,
        isCharging = state.isCharging,
        batteryLevel = state.batteryLevel,
        screenUsageTimeText = state.screenUsageTimeText,
        screenOpenCountText = state.screenOpenCountText,
    )

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
        nextAlarmText: String,
        isCharging: Boolean,
        batteryLevel: Int,
        screenUsageTimeText: String,
        screenOpenCountText: String,
    ): List<HomeInfoLine> {
        return buildList {
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
        }
    }

    private const val LOW_BATTERY_THRESHOLD = 15

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
