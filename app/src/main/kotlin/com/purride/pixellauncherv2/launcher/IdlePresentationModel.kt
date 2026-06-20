package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.viewmodel.LauncherUiState

data class IdlePresentation(
    val isNight: Boolean,
    val showFooter: Boolean,
)

object IdlePresentationModel {

    fun presentation(state: LauncherUiState): IdlePresentation {
        return IdlePresentation(
            isNight = isNight(state.currentTimeText),
            showFooter = !state.isCharging,
        )
    }

    fun isNight(timeText: String): Boolean {
        val hour = timeText.substringBefore(':').toIntOrNull() ?: return false
        return hour < 6 || hour >= 22
    }
}
