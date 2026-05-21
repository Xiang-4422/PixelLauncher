package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.PixelFontCatalog
import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

data class DiagnosticsLine(
    val title: String,
    val value: String,
)

object DiagnosticsModel {

    /** Overload for [LauncherUiState] (pixel-engine path). */
    fun lines(state: LauncherUiState, screenProfile: ScreenProfile): List<DiagnosticsLine> {
        val lastLaunch = state.lastLaunchPackageName
            ?.substringAfterLast('.')
            ?.uppercase()
            ?.take(10)
            ?.ifBlank { "NONE" }
            ?: "NONE"
        val recentSummary = state.recentApps.firstOrNull()
            ?.substringAfterLast('.')
            ?.uppercase()
            ?.take(8)
            ?.ifBlank { "0" }
            ?: "0"

        return listOf(
            DiagnosticsLine("LAUNCHES", state.launchCount.toString()),
            DiagnosticsLine("LAST", lastLaunch),
            DiagnosticsLine("RECENT", recentSummary),
            DiagnosticsLine("FONT", PixelFontCatalog.combinedLabel(state.selectedFontSize, state.selectedFontStyle)),
            DiagnosticsLine("DISPLAY", "${screenProfile.logicalWidth}X${screenProfile.logicalHeight}"),
            DiagnosticsLine("POWER", "${state.batteryLevel}%${if (state.isCharging) " CHG" else ""}"),
        )
    }

    fun lines(state: LauncherState, screenProfile: ScreenProfile): List<DiagnosticsLine> {
        val lastLaunch = state.lastLaunchPackageName
            ?.substringAfterLast('.')
            ?.uppercase()
            ?.take(10)
            ?.ifBlank { "NONE" }
            ?: "NONE"
        val recentSummary = state.recentApps.firstOrNull()
            ?.substringAfterLast('.')
            ?.uppercase()
            ?.take(8)
            ?.ifBlank { "0" }
            ?: "0"

        return listOf(
            DiagnosticsLine("LAUNCHES", state.launchCount.toString()),
            DiagnosticsLine("LAST", lastLaunch),
            DiagnosticsLine("RECENT", recentSummary),
            DiagnosticsLine("FONT", PixelFontCatalog.combinedLabel(state.selectedFontSize, state.selectedFontStyle)),
            DiagnosticsLine("DISPLAY", "${screenProfile.logicalWidth}X${screenProfile.logicalHeight}"),
            DiagnosticsLine("POWER", "${state.batteryLevel}%${if (state.isCharging) " CHG" else ""}"),
        )
    }
}
