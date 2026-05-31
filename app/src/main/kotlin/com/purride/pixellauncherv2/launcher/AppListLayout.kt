package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.GlyphStyle
import com.purride.pixellauncherv2.render.ScreenProfile

object AppListLayout {

    private const val bottomPadding = 0
    // Match the engine's rendered drawer row pitch so visibleRows agrees with
    // what DrawerScreen actually lays out (single source: DrawerListGeometry).
    private val rowHeight: Int
        get() = DrawerListGeometry.rowPitch(GlyphStyle.APP_LABEL_16.cellHeight)
    private const val labelTopInset = 0
    private const val hiddenRailWidthDivisor = 5
    private const val hiddenRailMinWidth = 12
    private const val hiddenRailMaxWidth = 16

    fun metrics(screenProfile: ScreenProfile): AppListLayoutMetrics {
        val listStartY = LauncherHeaderLayout.firstContentItemTop
        val railHeight = (screenProfile.logicalHeight - listStartY - bottomPadding).coerceAtLeast(rowHeight)
        val textList = TextListSupport.createLayoutMetrics(
            top = listStartY,
            bottomExclusive = listStartY + railHeight,
            rowHeight = rowHeight,
        )
        val textX = LauncherHeaderLayout.horizontalPadding
        val listWidth = (screenProfile.logicalWidth - textX - LauncherHeaderLayout.horizontalPadding).coerceAtLeast(8)
        val hiddenRailWidth = (screenProfile.logicalWidth / hiddenRailWidthDivisor)
            .coerceIn(hiddenRailMinWidth, hiddenRailMaxWidth)
        val hiddenRailLeft = (screenProfile.logicalWidth - hiddenRailWidth).coerceAtLeast(0)

        return AppListLayoutMetrics(
            timeX = LauncherHeaderLayout.horizontalPadding,
            timeY = LauncherHeaderLayout.rowY,
            headerTop = 0,
            headerBottomExclusive = LauncherHeaderLayout.contentTop,
            textList = textList,
            listStartY = listStartY,
            rowHeight = rowHeight,
            visibleRows = textList.viewport.visibleRows,
            textX = textX,
            labelYInset = labelTopInset,
            listWidth = listWidth,
            maxTextWidth = listWidth,
            labelFontHeight = GlyphStyle.APP_LABEL_16.cellHeight,
            railTop = listStartY,
            railHeight = railHeight,
            hiddenRailLeft = hiddenRailLeft,
            hiddenRailWidth = hiddenRailWidth,
        )
    }
}

data class AppListLayoutMetrics(
    val timeX: Int,
    val timeY: Int,
    val headerTop: Int,
    val headerBottomExclusive: Int,
    val textList: TextListLayoutMetrics,
    val listStartY: Int,
    val rowHeight: Int,
    val visibleRows: Int,
    val textX: Int,
    val labelYInset: Int,
    val listWidth: Int,
    val maxTextWidth: Int,
    val labelFontHeight: Int,
    val railTop: Int,
    val railHeight: Int,
    val hiddenRailLeft: Int,
    val hiddenRailWidth: Int,
)
