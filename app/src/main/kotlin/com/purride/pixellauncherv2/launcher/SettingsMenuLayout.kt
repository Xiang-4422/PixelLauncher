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
        val textList = TextListSupport.createLayoutMetrics(
            top = panelTop + firstRowInsetY,
            bottomExclusive = panelBottom,
            rowHeight = rowHeight,
        )

        return SettingsMenuLayoutMetrics(
            panelX = panelX,
            panelTop = panelTop,
            panelWidth = width,
            panelBottom = panelBottom,
            textList = textList,
            firstRowY = textList.viewport.top,
            rowAreaHeight = textList.viewport.height,
            rowHeight = rowHeight,
            visibleRows = textList.viewport.visibleRows,
            rowTextX = rowTextX,
            rowValueRightX = rowValueRightX,
            rowTextYOffset = rowTextYOffset,
            rowMaxTextWidth = (screenProfile.logicalWidth - rowTextX - 1).coerceAtLeast(8),
            rowMinGap = rowMinGap,
        )
    }

    fun hitTestRow(
        screenProfile: ScreenProfile,
        logicalX: Int,
        logicalY: Int,
        rowCount: Int,
        listStartIndex: Int = 0,
        scrollOffsetPx: Int = 0,
    ): Int? {
        if (rowCount <= 0) {
            return null
        }

        val metrics = metrics(screenProfile)
        if (logicalX < metrics.panelX || logicalX >= metrics.panelX + metrics.panelWidth) {
            return null
        }
        if (logicalY < metrics.firstRowY || logicalY >= metrics.panelBottom) {
            return null
        }

        return TextListSupport.hitTestRow(
            viewport = metrics.textList.viewport,
            logicalY = logicalY,
            rowCount = rowCount,
            listStartIndex = listStartIndex,
            scrollOffsetPx = scrollOffsetPx,
        )
    }
}

data class SettingsMenuLayoutMetrics(
    val panelX: Int,
    val panelTop: Int,
    val panelWidth: Int,
    val panelBottom: Int,
    val textList: TextListLayoutMetrics,
    val firstRowY: Int,
    val rowAreaHeight: Int,
    val rowHeight: Int,
    val visibleRows: Int,
    val rowTextX: Int,
    val rowValueRightX: Int,
    val rowTextYOffset: Int,
    val rowMaxTextWidth: Int,
    val rowMinGap: Int,
)
