package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.layout.LauncherLayoutProfile
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

data class DiagnosticsLine(
    val title: String,
    val value: String,
)

object DiagnosticsModel {

    /** Overload for [LauncherUiState] (pixel-engine path). */
    fun lines(state: LauncherUiState, screenProfile: LauncherLayoutProfile): List<DiagnosticsLine> {
        val textSamples = DiagnosticsTextSampleModel.samples(state, screenProfile)
        return lines(
            homeSummary = HomeInfoModel.summary(state),
            dataSummary = DataHealthModel.summary(state),
            textSamples = textSamples,
            launchCount = state.launchCount,
            lastLaunchPackageName = state.lastLaunchPackageName,
            recentApps = state.recentApps,
            batteryLevel = state.batteryLevel,
            isCharging = state.isCharging,
            hasUsageAccess = state.hasUsageAccess,
            fontSelection = state.fontSelection,
            isFontLoading = state.isFontLoading,
            fontCacheSummary = state.fontCacheSummary,
            screenProfile = screenProfile,
        )
    }

    fun lines(state: LauncherState, screenProfile: LauncherLayoutProfile): List<DiagnosticsLine> {
        val textSamples = DiagnosticsTextSampleModel.samples(state, screenProfile)
        return lines(
            homeSummary = HomeInfoModel.summary(state),
            dataSummary = DataHealthModel.summary(state),
            textSamples = textSamples,
            launchCount = state.launchCount,
            lastLaunchPackageName = state.lastLaunchPackageName,
            recentApps = state.recentApps,
            batteryLevel = state.batteryLevel,
            isCharging = state.isCharging,
            hasUsageAccess = state.hasUsageAccess,
            fontSelection = state.fontSelection,
            isFontLoading = state.isFontLoading,
            fontCacheSummary = state.fontCacheSummary,
            screenProfile = screenProfile,
        )
    }

    private fun lines(
        homeSummary: String,
        dataSummary: String,
        textSamples: List<DiagnosticsTextSample>,
        launchCount: Int,
        lastLaunchPackageName: String?,
        recentApps: List<String>,
        batteryLevel: Int,
        isCharging: Boolean,
        hasUsageAccess: Boolean,
        /** 当前由设置页明确选择的字体家族、宽度模式和字号。 */
        fontSelection: LauncherFontSelection,
        /** 候选字体是否仍在后台准备。 */
        isFontLoading: Boolean,
        /** indexed pack 缓存条目/占用摘要。 */
        fontCacheSummary: String,
        screenProfile: LauncherLayoutProfile,
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
        val statusBarHeight = LauncherHeaderLayout.statusBarHeight(screenProfile, fontSelection)
        val fontRows = PixelFontCatalog.fontSizeOptions(fontSelection.family, fontSelection.widthMode).map { size ->
            /** 同一字体家族与宽度模式下该字号的真实度量。 */
            val sizedSelection = fontSelection.copy(size = size)
            DiagnosticsLine(PixelFontCatalog.sizeLabel(size), PixelFontCatalog.metricsLabel(sizedSelection))
        }
        val textSummary = DiagnosticsTextSampleModel.summary(textSamples)
        val maxTextSample = DiagnosticsTextSampleModel.maxSample(textSamples)
        val boundsSnapshot = DiagnosticsBoundsModel.snapshot(screenProfile, fontSelection)
        val familyDescriptor = requireNotNull(PixelFontCatalog.familyDescriptor(fontSelection.family))
        val faceDescriptor = PixelFontCatalog.requireFace(fontSelection)
        /** 当前 face 所有 pack 声明范围的稳定去重列表。 */
        val coverageRanges = faceDescriptor.packs.flatMap { pack -> pack.coverageRanges }.distinct()

        return listOf(
            DiagnosticsLine("HOME", homeSummary),
            DiagnosticsLine("DATA", dataSummary),
            DiagnosticsLine("USAGE", if (hasUsageAccess) "EVENTS" else "NO ACCESS"),
            DiagnosticsLine("LAUNCHES", launchCount.toString()),
            DiagnosticsLine("LAST", lastLaunch),
            DiagnosticsLine("RECENT", recentSummary),
            DiagnosticsLine(
                "FONT",
                "${PixelFontCatalog.familyLabel(fontSelection.family)} " +
                    "${PixelFontCatalog.widthModeLabel(fontSelection.widthMode)} " +
                    PixelFontCatalog.sizeLabel(fontSelection.size),
            ),
            DiagnosticsLine("FONT ID", fontSelection.family.id.uppercase()),
            DiagnosticsLine("FONT SRC", familyDescriptor.sourceVersion),
            DiagnosticsLine("FONT TYPE", faceDescriptor.packs.map { pack -> pack.sourceType }.distinct().joinToString("+").uppercase()),
            DiagnosticsLine("FONT PACK", faceDescriptor.packs.joinToString("+") { pack -> pack.id }.take(32)),
            DiagnosticsLine(
                "FONT RANGE",
                "${coverageRanges.size} ${coverageRanges.first().substringBefore('-')}-" +
                    coverageRanges.last().substringAfter('-'),
            ),
            DiagnosticsLine("FONT LOAD", if (isFontLoading) "LOADING" else "READY"),
            DiagnosticsLine("FONT CACHE", fontCacheSummary),
        ) + fontRows + listOf(
            DiagnosticsLine("TEXT", textSummary),
            DiagnosticsLine(
                "TEXT MAX",
                maxTextSample?.let { "${it.group} ${it.widthPx}/${it.availablePx}" } ?: "NONE",
            ),
            DiagnosticsLine("TEXT RISK", DiagnosticsTextSampleModel.riskCount(textSamples).toString()),
            DiagnosticsLine("DISPLAY", "${screenProfile.logicalWidth}X${screenProfile.logicalHeight}"),
            DiagnosticsLine("STATUS", "${screenProfile.statusBarHeight}/$statusBarHeight"),
            DiagnosticsLine("BOUNDS", boundsSnapshot.summary),
            DiagnosticsLine("POWER", "$batteryLevel%${if (isCharging) " CHG" else ""}"),
            DiagnosticsLine("DEBUG", "DATA HEALTH"),
        )
    }
}
