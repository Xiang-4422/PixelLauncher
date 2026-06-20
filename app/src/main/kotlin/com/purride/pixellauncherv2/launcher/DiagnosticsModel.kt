package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

data class DiagnosticsLine(
    val title: String,
    val value: String,
)

object DiagnosticsModel {

    /** Overload for [LauncherUiState] (pixel-engine path). */
    fun lines(state: LauncherUiState, screenProfile: ScreenProfile): List<DiagnosticsLine> {
        return lines(
            homeSummary = HomeInfoModel.summary(state),
            dataSummary = DataHealthModel.summary(state),
            textSummary = DiagnosticsTextSampleModel.summary(state, screenProfile),
            launchCount = state.launchCount,
            lastLaunchPackageName = state.lastLaunchPackageName,
            recentApps = state.recentApps,
            batteryLevel = state.batteryLevel,
            isCharging = state.isCharging,
            screenProfile = screenProfile,
        )
    }

    fun lines(state: LauncherState, screenProfile: ScreenProfile): List<DiagnosticsLine> {
        return lines(
            homeSummary = HomeInfoModel.summary(state),
            dataSummary = DataHealthModel.summary(state),
            textSummary = DiagnosticsTextSampleModel.summary(state, screenProfile),
            launchCount = state.launchCount,
            lastLaunchPackageName = state.lastLaunchPackageName,
            recentApps = state.recentApps,
            batteryLevel = state.batteryLevel,
            isCharging = state.isCharging,
            screenProfile = screenProfile,
        )
    }

    private fun lines(
        homeSummary: String,
        dataSummary: String,
        textSummary: String,
        launchCount: Int,
        lastLaunchPackageName: String?,
        recentApps: List<String>,
        batteryLevel: Int,
        isCharging: Boolean,
        screenProfile: ScreenProfile,
    ): List<DiagnosticsLine> {
        val lastLaunch = lastLaunchPackageName
            ?.substringAfterLast('.')
            ?.uppercase()
            ?.take(10)
            ?.ifBlank { "NONE" }
            ?: "NONE"
        val recentSummary = recentApps.firstOrNull()
            ?.substringAfterLast('.')
            ?.uppercase()
            ?.take(8)
            ?.ifBlank { "0" }
            ?: "0"
        val statusBarHeight = LauncherHeaderLayout.statusBarHeight(screenProfile)
        val fontRows = PixelFontCatalog.fontSizeOptions().map { size ->
            DiagnosticsLine(PixelFontCatalog.sizeLabel(size), PixelFontCatalog.metricsLabel(size))
        }

        return listOf(
            DiagnosticsLine("HOME", homeSummary),
            DiagnosticsLine("DATA", dataSummary),
            DiagnosticsLine("LAUNCHES", launchCount.toString()),
            DiagnosticsLine("LAST", lastLaunch),
            DiagnosticsLine("RECENT", recentSummary),
            DiagnosticsLine("FONT", "FUSION ${PixelFontCatalog.sizeLabel(PixelFontCatalog.defaultUiFontSize)}"),
        ) + fontRows + listOf(
            DiagnosticsLine("TEXT", textSummary),
            DiagnosticsLine("DISPLAY", "${screenProfile.logicalWidth}X${screenProfile.logicalHeight}"),
            DiagnosticsLine("STATUS", "${screenProfile.statusBarHeight}/$statusBarHeight"),
            DiagnosticsLine("POWER", "$batteryLevel%${if (isCharging) " CHG" else ""}"),
        )
    }
}
