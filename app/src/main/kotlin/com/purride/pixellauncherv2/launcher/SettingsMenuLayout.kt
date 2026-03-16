package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.ScreenProfile

object SettingsMenuLayout {

    private const val panelX = 0
    private const val panelTopOffset = 1
    private const val panelBottomPadding = 4
    private const val rowHeight = 14
    private const val firstRowInsetY = 3
    private const val rowTextInsetX = 2
    private const val rowValueInsetRight = 2
    private const val rowTextYOffset = 1
    private const val rowMinGap = 3

    fun metrics(screenProfile: ScreenProfile): SettingsMenuLayoutMetrics {
        val width = screenProfile.logicalWidth.coerceAtLeast(24)
        val panelTop = LauncherHeaderLayout.contentTop + panelTopOffset
        val panelBottom = (screenProfile.logicalHeight - panelBottomPadding).coerceAtLeast(panelTop + 24)
        val rowTextX = panelX + rowTextInsetX
        val rowValueRightX = (panelX + width - rowValueInsetRight).coerceAtLeast(rowTextX)

        return SettingsMenuLayoutMetrics(
            panelX = panelX,
            panelTop = panelTop,
            panelWidth = width,
            panelBottom = panelBottom,
            firstRowY = panelTop + firstRowInsetY,
            rowHeight = rowHeight,
            rowTextX = rowTextX,
            rowValueRightX = rowValueRightX,
            rowTextYOffset = rowTextYOffset,
            rowMaxTextWidth = (screenProfile.logicalWidth - rowTextX - 1).coerceAtLeast(8),
            rowMinGap = rowMinGap,
        )
    }

    fun hitTestRow(screenProfile: ScreenProfile, logicalX: Int, logicalY: Int, rowCount: Int): Int? {
        if (rowCount <= 0) {
            return null
        }

        val metrics = metrics(screenProfile)
        if (logicalX < metrics.panelX || logicalX >= metrics.panelX + metrics.panelWidth) {
            return null
        }
        if (logicalY < metrics.firstRowY) {
            return null
        }

        val row = (logicalY - metrics.firstRowY) / metrics.rowHeight
        return row.takeIf { it in 0 until rowCount }
    }
}

data class SettingsMenuLayoutMetrics(
    val panelX: Int,
    val panelTop: Int,
    val panelWidth: Int,
    val panelBottom: Int,
    val firstRowY: Int,
    val rowHeight: Int,
    val rowTextX: Int,
    val rowValueRightX: Int,
    val rowTextYOffset: Int,
    val rowMaxTextWidth: Int,
    val rowMinGap: Int,
)
