package com.purride.pixellauncherv2.util

import com.purride.pixellauncherv2.launcher.DataHealthModel
import com.purride.pixellauncherv2.launcher.LauncherState

class TerminalStatusProvider {

    fun buildStatus(state: LauncherState): String {
        val dataIssueCount = DataHealthModel.issueCount(state)
        return when {
            !state.isCharging && state.batteryLevel <= 15 -> "LOW POWER ${state.batteryLevel}%"
            dataIssueCount > 0 -> "DATA $dataIssueCount ISSUE"
            isNight(state.currentTimeText) -> "NIGHT MODE READY"
            else -> "SYSTEM READY"
        }
    }

    private fun isNight(timeText: String): Boolean {
        val hour = timeText.substringBefore(':').toIntOrNull() ?: return false
        return hour < 6 || hour >= 22
    }
}
