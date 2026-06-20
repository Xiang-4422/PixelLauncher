package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.viewmodel.LauncherUiState

object HomeInfoDetailModel {

    fun notice(action: HomeInfoAction, state: LauncherState): String {
        return notice(
            action = action,
            hasLocationPermission = state.hasLocationPermission,
            hasUsageAccess = state.hasUsageAccess,
            unreadSmsCount = state.unreadSmsCount,
            rainUpdatedTimeText = state.rainUpdatedTimeText,
        )
    }

    fun notice(action: HomeInfoAction, state: LauncherUiState): String {
        return notice(
            action = action,
            hasLocationPermission = state.hasLocationPermission,
            hasUsageAccess = state.hasUsageAccess,
            unreadSmsCount = state.unreadSmsCount,
            rainUpdatedTimeText = state.rainUpdatedTimeText,
        )
    }

    fun rainRefreshStarted(): String = "RAIN REFRESHING"

    fun rainRefreshUpdated(state: LauncherState): String = rainRefreshUpdated(state.rainUpdatedTimeText)

    fun rainRefreshFailed(hasPreviousHint: Boolean): String {
        return if (hasPreviousHint) "RAIN KEEP LAST" else "RAIN UNAVAILABLE"
    }

    fun rainLocationUnavailable(): String = "RAIN NO LOCATION"

    fun usageRefreshStarted(): String = "USE REFRESHING"

    fun usageRefreshResult(state: LauncherState): String {
        return usageRefreshResult(
            hasUsageAccess = state.hasUsageAccess,
            screenUsageTimeText = state.screenUsageTimeText,
            screenOpenCountText = state.screenOpenCountText,
        )
    }

    private fun rainRefreshUpdated(rainUpdatedTimeText: String): String {
        val updated = rainUpdatedTimeText.trim()
        return if (updated.isEmpty()) "RAIN UPDATED" else "RAIN UPDATED $updated"
    }

    private fun usageRefreshResult(
        hasUsageAccess: Boolean,
        screenUsageTimeText: String,
        screenOpenCountText: String,
    ): String {
        if (!hasUsageAccess) return "USE NEEDS ACCESS"
        val usage = screenUsageTimeText.ifBlank { "--:--" }
        val opens = screenOpenCountText.ifBlank { "--" }
        return "USE $usage OPEN $opens"
    }

    private fun notice(
        action: HomeInfoAction,
        hasLocationPermission: Boolean,
        hasUsageAccess: Boolean,
        unreadSmsCount: Int,
        rainUpdatedTimeText: String,
    ): String {
        return when (action) {
            HomeInfoAction.RAIN -> when {
                !hasLocationPermission -> "RAIN NEEDS LOC"
                rainUpdatedTimeText.isNotBlank() -> "RAIN UPDATED $rainUpdatedTimeText"
                else -> "RAIN REFRESH"
            }
            HomeInfoAction.CALL -> "OPEN CALL LOG"
            HomeInfoAction.SMS -> if (unreadSmsCount > 0) "OPEN UNREAD SMS" else "OPEN SMS"
            HomeInfoAction.ALARM -> "OPEN ALARMS"
            HomeInfoAction.BATTERY -> "OPEN BATTERY"
            HomeInfoAction.NOTIFICATION -> "OPEN NOTIFY"
            HomeInfoAction.USAGE -> if (hasUsageAccess) "USE TODAY" else "USE NEEDS ACCESS"
        }
    }
}
