package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.viewmodel.LauncherUiState

enum class IdleStatusKind {
    NOTIFICATION,
    CHARGING,
    LOW_BATTERY,
    WEATHER,
    DEFAULT,
}

data class IdleStatusLine(
    val kind: IdleStatusKind,
    val title: String,
    val body: String,
)

object IdleStatusModel {

    fun line(state: LauncherUiState): IdleStatusLine {
        val notifications = buildList {
            if (state.missedCallCount > 0) add("CALL ${state.missedCallCount}")
            if (state.unreadSmsCount > 0) add("SMS ${state.unreadSmsCount}")
            if (state.notificationCount > 0 && state.notificationSummaryText.isNotBlank()) {
                add(state.notificationSummaryText.trim())
            }
        }
        if (notifications.isNotEmpty()) {
            return IdleStatusLine(
                kind = IdleStatusKind.NOTIFICATION,
                title = "NOTIFY",
                body = notifications.joinToString("  "),
            )
        }

        val batteryText = "BAT ${state.batteryLevel.coerceIn(0, 100)}%"
        if (state.isCharging) {
            return IdleStatusLine(
                kind = IdleStatusKind.CHARGING,
                title = "CHARGING",
                body = "$batteryText  ${SettingsMenuModel.chargeIdleEffectLabel(state.chargeIdleEffect)}",
            )
        }

        if (state.batteryLevel <= LOW_BATTERY_THRESHOLD) {
            return IdleStatusLine(
                kind = IdleStatusKind.LOW_BATTERY,
                title = "LOW BAT",
                body = batteryText,
            )
        }

        if (WeatherAttentionModel.isAttentionWeather(state.rainHintText)) {
            return IdleStatusLine(
                kind = IdleStatusKind.WEATHER,
                title = "WEATHER",
                body = state.rainHintText,
            )
        }

        return IdleStatusLine(
            kind = IdleStatusKind.DEFAULT,
            title = "IDLE",
            body = state.nextAlarmText.takeIf { it.isNotBlank() && it != "--:--" }?.let { "ALARM $it" }
                ?: "NO PRIORITY STATE",
        )
    }

    private const val LOW_BATTERY_THRESHOLD = 15
}
