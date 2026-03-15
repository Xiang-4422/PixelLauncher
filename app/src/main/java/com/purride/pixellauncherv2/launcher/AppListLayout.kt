package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.GlyphStyle
import com.purride.pixellauncherv2.render.ScreenProfile

object AppListLayout {

    private const val sectionGap = 2
    private const val bottomPadding = 2
    private const val rowHeight = 17
    private const val labelTopInset = 0
    private const val indexRailGap = 2
    private const val indexRailPreferredWidth = 12
    private const val minListTextWidth = 18

    fun metrics(screenProfile: ScreenProfile): AppListLayoutMetrics {
        val listStartY = LauncherHeaderLayout.contentTop + sectionGap
        val railHeight = (screenProfile.logicalHeight - listStartY - bottomPadding).coerceAtLeast(rowHeight)
        val visibleRows = (railHeight / rowHeight)
            .coerceAtLeast(1)
        val textX = LauncherHeaderLayout.horizontalPadding
        val maxAvailableRailWidth = (
            screenProfile.logicalWidth -
                textX -
                LauncherHeaderLayout.horizontalPadding -
                indexRailGap -
                minListTextWidth
            ).coerceAtLeast(0)
        val indexRailWidth = minOf(indexRailPreferredWidth, maxAvailableRailWidth)
        val indexRailLeft = (
            screenProfile.logicalWidth -
                LauncherHeaderLayout.horizontalPadding -
                indexRailWidth
            ).coerceAtLeast(textX + minListTextWidth)
        val listWidth = if (indexRailWidth > 0) {
            (indexRailLeft - indexRailGap - textX).coerceAtLeast(8)
        } else {
            (screenProfile.logicalWidth - textX - LauncherHeaderLayout.horizontalPadding).coerceAtLeast(8)
        }

        return AppListLayoutMetrics(
            timeX = LauncherHeaderLayout.horizontalPadding,
            timeY = LauncherHeaderLayout.rowY,
            listStartY = listStartY,
            rowHeight = rowHeight,
            visibleRows = visibleRows,
            textX = textX,
            labelYInset = labelTopInset,
            listWidth = listWidth,
            maxTextWidth = listWidth,
            labelFontHeight = GlyphStyle.APP_LABEL_16.cellHeight,
            railTop = listStartY,
            railHeight = visibleRows * rowHeight,
            indexRailLeft = indexRailLeft,
            indexRailWidth = indexRailWidth,
            indexRailGap = indexRailGap,
        )
    }

    fun hitTestAppIndex(
        screenProfile: ScreenProfile,
        state: LauncherState,
        logicalX: Int,
        logicalY: Int,
    ): Int? {
        if (logicalX !in 0 until screenProfile.logicalWidth) {
            return null
        }

        val metrics = metrics(screenProfile)
        if (metrics.indexRailWidth > 0 && logicalX >= metrics.indexRailLeft) {
            return null
        }
        if (logicalY < metrics.listStartY) {
            return null
        }

        val row = (logicalY - metrics.listStartY) / metrics.rowHeight
        if (row !in 0 until metrics.visibleRows) {
            return null
        }

        val appIndex = state.listStartIndex + row
        return appIndex.takeIf { it in state.apps.indices }
    }

    fun hitTestIndexRailPage(
        screenProfile: ScreenProfile,
        logicalX: Int,
        logicalY: Int,
        pageCount: Int,
    ): Int? {
        if (pageCount <= 0) {
            return null
        }

        val metrics = metrics(screenProfile)
        if (metrics.indexRailWidth <= 0) {
            return null
        }
        if (logicalX < metrics.indexRailLeft || logicalX >= metrics.indexRailLeft + metrics.indexRailWidth) {
            return null
        }
        if (logicalY < metrics.railTop || logicalY >= metrics.railTop + metrics.railHeight) {
            return null
        }

        val localY = (logicalY - metrics.railTop).coerceIn(0, metrics.railHeight - 1)
        val pageIndex = (localY * pageCount) / metrics.railHeight.coerceAtLeast(1)
        return pageIndex.coerceIn(0, pageCount - 1)
    }
}

data class AppListLayoutMetrics(
    val timeX: Int,
    val timeY: Int,
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
    val indexRailLeft: Int,
    val indexRailWidth: Int,
    val indexRailGap: Int,
)
