package com.purride.pixellauncherv2.util

import com.purride.pixellauncherv2.launcher.LauncherState

class TerminalStatusProvider {

    fun buildStatus(state: LauncherState): String {
        return when {
            state.isCharging -> "CHARGING ${state.batteryLevel}%"
            state.batteryLevel <= 15 -> "LOW POWER ${state.batteryLevel}%"
            isNight(state.currentTimeText) -> "NIGHT MODE READY"
            else -> "SYSTEM READY"
        }
    }

    private fun isNight(timeText: String): Boolean {
        val hour = timeText.substringBefore(':').toIntOrNull() ?: return false
        return hour < 6 || hour >= 22
    }
}
