package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

data class DiagnosticsTextSample(
    val group: String,
    val text: String,
    val widthPx: Int,
    val availablePx: Int,
) {
    val fits: Boolean
        get() = widthPx <= availablePx
}

object DiagnosticsTextSampleModel {

    fun summary(state: LauncherState, screenProfile: ScreenProfile): String {
        return summary(samples(state, screenProfile))
    }

    fun summary(state: LauncherUiState, screenProfile: ScreenProfile): String {
        return summary(samples(state, screenProfile))
    }

    fun samples(state: LauncherState, screenProfile: ScreenProfile): List<DiagnosticsTextSample> {
        return buildSamples(
            homeLines = HomeInfoModel.lines(state).map { it.text },
            dataLines = DataHealthModel.lines(state).map { "${it.title} ${it.value}" },
            screenProfile = screenProfile,
        )
    }

    fun samples(state: LauncherUiState, screenProfile: ScreenProfile): List<DiagnosticsTextSample> {
        return buildSamples(
            homeLines = HomeInfoModel.lines(state).map { it.text },
            dataLines = DataHealthModel.lines(state).map { "${it.title} ${it.value}" },
            screenProfile = screenProfile,
        )
    }

    private fun buildSamples(
        homeLines: List<String>,
        dataLines: List<String>,
        screenProfile: ScreenProfile,
    ): List<DiagnosticsTextSample> {
        val availablePx = contentWidth(screenProfile)
        return buildList {
            addAll(homeLines.map { sample(group = "HOME", text = it, availablePx = availablePx) })
            addAll(dataLines.map { sample(group = "DATA", text = it, availablePx = availablePx) })
            addAll(settingsSamples.map { sample(group = "SETTINGS", text = it, availablePx = availablePx) })
        }
    }

    private fun summary(samples: List<DiagnosticsTextSample>): String {
        val maxWidth = samples.maxOfOrNull { it.widthPx } ?: 0
        val availablePx = samples.firstOrNull()?.availablePx ?: 0
        val riskCount = samples.count { !it.fits }
        return if (riskCount == 0) {
            "OK $maxWidth/$availablePx"
        } else {
            "RISK $riskCount $maxWidth/$availablePx"
        }
    }

    private fun sample(
        group: String,
        text: String,
        availablePx: Int,
    ): DiagnosticsTextSample {
        return DiagnosticsTextSample(
            group = group,
            text = text,
            widthPx = PixelFontCatalog.estimatedTextWidth(text),
            availablePx = availablePx,
        )
    }

    private fun contentWidth(screenProfile: ScreenProfile): Int {
        return (screenProfile.logicalWidth - CONTENT_HORIZONTAL_PADDING_PX * 2).coerceAtLeast(1)
    }

    private val settingsSamples = listOf(
        "DISPLAY",
        "HOME",
        "DRAWER",
        "IDLE",
        "DATA",
        "ADVANCED",
        "PIXEL 999PX",
        "GAP 100%",
        "THEME NIGHT",
        "STATUS 3 ROWS",
        "ALIGN CENTER",
        "SEARCH ON",
        "APPS OPEN",
        "TIMEOUT 120S",
        "EFFECT DOT MATRIX",
        "DATA 8 ISSUE",
        "ADVANCED OPEN",
    )

    private const val CONTENT_HORIZONTAL_PADDING_PX = 2
}
