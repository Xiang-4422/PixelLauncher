package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.GlyphStyle
import com.purride.pixellauncherv2.render.ScreenProfile

object AppListLayout {

    private const val sectionGap = 2
    private const val bottomPadding = 2
    private const val rowHeight = 17
    private const val centeredRowGap = 1
    private val centeredSelectedRowHeight = (GlyphStyle.APP_LABEL_16.cellHeight * 3) / 2
    private val centeredUnselectedRowHeight = GlyphStyle.APP_LABEL_16.cellHeight
    private const val searchBoxHeight = rowHeight
    private const val labelTopInset = 0
    private const val indexRailGap = 2
    private const val indexRailPreferredWidth = 14
    private const val minListTextWidth = 18
    private const val drawerLeftVisualOffset = 1

    fun metrics(screenProfile: ScreenProfile): AppListLayoutMetrics {
        val searchTop = LauncherHeaderLayout.contentTop + sectionGap
        val listStartY = searchTop + searchBoxHeight + sectionGap
        val railHeight = (screenProfile.logicalHeight - listStartY - bottomPadding).coerceAtLeast(rowHeight)
        val visibleRows = (railHeight / rowHeight)
            .coerceAtLeast(1)
        val textX = (LauncherHeaderLayout.horizontalPadding - drawerLeftVisualOffset).coerceAtLeast(0)
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
            searchTop = searchTop,
            searchHeight = searchBoxHeight,
            searchTextX = textX,
            searchTextY = searchTop + labelTopInset,
            searchWidth = (screenProfile.logicalWidth - textX - LauncherHeaderLayout.horizontalPadding).coerceAtLeast(8),
            listStartY = listStartY,
            rowHeight = rowHeight,
            visibleRows = visibleRows,
            textX = textX,
            labelYInset = labelTopInset,
            listWidth = listWidth,
            maxTextWidth = listWidth,
            labelFontHeight = GlyphStyle.APP_LABEL_16.cellHeight,
            railTop = listStartY,
            railHeight = railHeight,
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
        if (metrics.indexRailWidth > 0 && logicalX >= metrics.indexRailLeft) {
            return null
        }
        if (logicalY < metrics.listStartY) {
            return null
        }

        if (state.mode == LauncherMode.APP_DRAWER && !state.isDrawerSearchFocused) {
            val centeredWindow = centeredListWindow(screenProfile)
            val adjustedY = logicalY - drawerListScrollOffsetPx
            if (adjustedY < centeredWindow.listTop || adjustedY >= centeredWindow.listBottomExclusive) {
                return null
            }
            val row = centeredWindow.rowAt(adjustedY) ?: return null
            val appIndex = state.selectedIndex + (row - centeredWindow.centerRow)
            return appIndex.takeIf { it in drawerApps.indices }
        }

        val adjustedY = logicalY - drawerListScrollOffsetPx
        if (adjustedY < metrics.listStartY) {
            return null
        }
        val row = (adjustedY - metrics.listStartY) / metrics.rowHeight
        if (row !in 0 until metrics.visibleRows) {
            return null
        }

        val appIndex = if (
            state.mode == LauncherMode.APP_DRAWER &&
            state.isDrawerSearchFocused &&
            state.drawerQuery.isNotBlank()
        ) {
            val centerRow = metrics.visibleRows / 2
            state.selectedIndex + (row - centerRow)
        } else {
            state.listStartIndex + row
        }
        return appIndex.takeIf { it in drawerApps.indices }
    }

    fun hitTestSearchBox(
        screenProfile: ScreenProfile,
        logicalX: Int,
        logicalY: Int,
    ): Boolean {
        val metrics = metrics(screenProfile)
        val left = metrics.searchTextX
        val right = metrics.searchTextX + metrics.searchWidth
        val top = metrics.searchTop
        val bottom = metrics.searchTop + metrics.searchHeight
        return logicalX in left until right && logicalY in top until bottom
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

    fun hitTestIndexRailLetter(
        screenProfile: ScreenProfile,
        logicalX: Int,
        logicalY: Int,
    ): Int? {
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
        val letterIndex = (localY * DrawerAlphaIndexModel.letterCount) / metrics.railHeight.coerceAtLeast(1)
        return letterIndex.coerceIn(0, DrawerAlphaIndexModel.lastLetterIndex)
    }

    fun centeredVisibleRows(screenProfile: ScreenProfile): Int {
        return centeredListWindow(screenProfile).visibleRows
    }

    fun centeredRowHeightPx(): Int = centeredUnselectedRowHeight + centeredRowGap

    fun centeredSelectedRowHeightPx(): Int = centeredSelectedRowHeight

    fun centeredUnselectedRowHeightPx(): Int = centeredUnselectedRowHeight

    fun centeredRowGapPx(): Int = centeredRowGap

    fun centeredListTop(screenProfile: ScreenProfile): Int {
        return centeredListWindow(screenProfile).listTop
    }

    fun centeredListWindow(screenProfile: ScreenProfile): CenteredListWindow {
        val metrics = metrics(screenProfile)
        val pairHeight = 2 * (centeredUnselectedRowHeight + centeredRowGap)
        val availableForPairs = (metrics.railHeight - centeredSelectedRowHeight).coerceAtLeast(0)
        val pairs = (availableForPairs / pairHeight).coerceAtLeast(0)
        val visibleRows = 1 + (pairs * 2)
        val centerRow = pairs
        val contentHeight = centeredSelectedRowHeight + (pairs * pairHeight)
        val extraHeight = (metrics.railHeight - contentHeight).coerceAtLeast(0)
        val listTop = metrics.listStartY + (extraHeight / 2)

        val rowHeights = List(visibleRows) { row ->
            if (row == centerRow) centeredSelectedRowHeight else centeredUnselectedRowHeight
        }
        val rowTops = MutableList(visibleRows) { 0 }
        var currentTop = listTop
        for (row in 0 until visibleRows) {
            rowTops[row] = currentTop
            val gapAfter = if (row == visibleRows - 1) 0 else centeredRowGap
            currentTop += rowHeights[row] + gapAfter
        }

        return CenteredListWindow(
            listTop = listTop,
            listBottomExclusive = currentTop,
            visibleRows = visibleRows,
            centerRow = centerRow,
            rowTops = rowTops,
            rowHeights = rowHeights,
        )
    }
}

data class CenteredListWindow(
    val listTop: Int,
    val listBottomExclusive: Int,
    val visibleRows: Int,
    val centerRow: Int,
    private val rowTops: List<Int>,
    private val rowHeights: List<Int>,
) {
    fun rowTop(row: Int): Int = rowTops[row]

    fun rowHeight(row: Int): Int = rowHeights[row]

    fun rowBottomExclusive(row: Int): Int = rowTops[row] + rowHeights[row]

    fun rowAt(logicalY: Int): Int? {
        for (row in rowTops.indices) {
            val top = rowTops[row]
            val bottom = top + rowHeights[row]
            if (logicalY in top until bottom) {
                return row
            }
        }
        return null
    }
}

data class AppListLayoutMetrics(
    val timeX: Int,
    val timeY: Int,
    val searchTop: Int,
    val searchHeight: Int,
    val searchTextX: Int,
    val searchTextY: Int,
    val searchWidth: Int,
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
