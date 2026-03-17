package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.GlyphStyle
import com.purride.pixellauncherv2.render.ScreenProfile

object AppListLayout {

    private const val bottomPadding = 0
    private const val rowHeight = 17
    private const val labelTopInset = 0
    private const val drawerLeftVisualOffset = 1
    private const val hiddenRailWidthDivisor = 5
    private const val hiddenRailMinWidth = 12
    private const val hiddenRailMaxWidth = 16

    fun metrics(screenProfile: ScreenProfile): AppListLayoutMetrics {
        val listStartY = LauncherHeaderLayout.contentTop
        val railHeight = (screenProfile.logicalHeight - listStartY - bottomPadding).coerceAtLeast(rowHeight)
        val textList = TextListSupport.createLayoutMetrics(
            top = listStartY,
            bottomExclusive = listStartY + railHeight,
            rowHeight = rowHeight,
        )
        val textX = (LauncherHeaderLayout.horizontalPadding - drawerLeftVisualOffset).coerceAtLeast(0)
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

    fun hitTestAppIndex(
        screenProfile: ScreenProfile,
        state: LauncherState,
        logicalX: Int,
        logicalY: Int,
        drawerListScrollOffsetPx: Int = 0,
    ): Int? {
        val drawerApps = when {
            state.drawerVisibleApps.isNotEmpty() -> state.drawerVisibleApps
            state.drawerQuery.isNotBlank() -> emptyList()
            else -> state.apps
        }
        if (logicalX !in 0 until screenProfile.logicalWidth) {
            return null
        }

        val metrics = metrics(screenProfile)
        if (logicalY < metrics.listStartY) {
            return null
        }
        if (logicalY >= metrics.listStartY + metrics.railHeight) {
            return null
        }
        if (state.mode == LauncherMode.APP_DRAWER && !state.isDrawerSearchFocused && logicalX >= metrics.hiddenRailLeft) {
            return null
        }
        return TextListSupport.hitTestRow(
            viewport = metrics.textList.viewport,
            logicalY = logicalY,
            rowCount = drawerApps.size,
            listStartIndex = state.listStartIndex,
            scrollOffsetPx = drawerListScrollOffsetPx,
        )
    }

    fun hitTestDrawerHeaderSearchArea(
        screenProfile: ScreenProfile,
        logicalX: Int,
        logicalY: Int,
    ): Boolean {
        val metrics = metrics(screenProfile)
        return logicalX in 0 until screenProfile.logicalWidth &&
            logicalY in metrics.headerTop until metrics.headerBottomExclusive
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
        if (metrics.hiddenRailWidth <= 0) {
            return null
        }
        if (logicalX < metrics.hiddenRailLeft || logicalX >= metrics.hiddenRailLeft + metrics.hiddenRailWidth) {
            return null
        }
        if (logicalY < metrics.railTop || logicalY >= metrics.railTop + metrics.railHeight) {
            return null
        }

        val localY = (logicalY - metrics.railTop).coerceIn(0, metrics.railHeight - 1)
        val pageIndex = (localY * pageCount) / metrics.railHeight.coerceAtLeast(1)
        return pageIndex.coerceIn(0, pageCount - 1)
    }

    fun hitTestIndexRailLetter(
        screenProfile: ScreenProfile,
        logicalX: Int,
        logicalY: Int,
    ): Int? {
        val metrics = metrics(screenProfile)
        if (metrics.hiddenRailWidth <= 0) {
            return null
        }
        if (logicalX < metrics.hiddenRailLeft || logicalX >= metrics.hiddenRailLeft + metrics.hiddenRailWidth) {
            return null
        }
        if (logicalY < metrics.railTop || logicalY >= metrics.railTop + metrics.railHeight) {
            return null
        }

        val localY = (logicalY - metrics.railTop).coerceIn(0, metrics.railHeight - 1)
        val letterIndex = (localY * DrawerAlphaIndexModel.letterCount) / metrics.railHeight.coerceAtLeast(1)
        return letterIndex.coerceIn(0, DrawerAlphaIndexModel.lastLetterIndex)
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
