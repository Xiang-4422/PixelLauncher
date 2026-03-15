package com.purride.pixellauncherv2.render

import com.purride.pixellauncherv2.launcher.AppEntry
import com.purride.pixellauncherv2.launcher.AppListLayout
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelRendererCenteredListTest {

    private val screenProfile = ScreenProfile(
        logicalWidth = 72,
        logicalHeight = 160,
        dotSizePx = 15,
    )
    private val layout = AppListLayout.metrics(screenProfile)
    private val centeredWindow = AppListLayout.centeredListWindow(screenProfile)
    private val centeredRows = centeredWindow.visibleRows
    private val centerRow = centeredWindow.centerRow
    private val renderer = PixelRenderer(PixelFontEngine(BlockGlyphProvider()))

    @Test
    fun nonSearchDrawerKeepsSelectedItemOnCenterRowWithTopPaddingWhenAtStart() {
        val apps = List(6) { index ->
            AppEntry(
                label = "AAAAA",
                packageName = "pkg.$index",
                activityName = "Activity$index",
            )
        }
        val buffer = renderBuffer(
            apps = apps,
            selectedIndex = 0,
            isSearchFocused = false,
            query = "",
        )

        for (row in 0 until centerRow) {
            assertTrue(rowLitPixelCount(buffer, row) == 0)
        }
        assertTrue(rowLitPixelCount(buffer, centerRow) > 0)
    }

    @Test
    fun nonSearchDrawerRendersSelectedRowLargerThanNeighborRows() {
        val apps = List(20) { index ->
            AppEntry(
                label = "AAAAA",
                packageName = "pkg.$index",
                activityName = "Activity$index",
            )
        }
        val buffer = renderBuffer(
            apps = apps,
            selectedIndex = 8,
            isSearchFocused = false,
            query = "",
        )

        val selectedPixels = rowLitPixelCount(buffer, centerRow)
        val abovePixels = rowLitPixelCount(buffer, centerRow - 1)
        val belowPixels = rowLitPixelCount(buffer, centerRow + 1)

        assertTrue(selectedPixels > abovePixels)
        assertTrue(selectedPixels > belowPixels)
    }

    private fun renderBuffer(
        apps: List<AppEntry>,
        selectedIndex: Int,
        isSearchFocused: Boolean,
        query: String,
    ): PixelBuffer {
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            apps = apps,
            drawerVisibleApps = apps,
            drawerQuery = query,
            isDrawerSearchFocused = isSearchFocused,
            selectedIndex = selectedIndex,
            listStartIndex = selectedIndex.coerceAtLeast(0),
            isLoading = false,
        )
        return renderer.render(
            state = state,
            screenProfile = screenProfile,
            animationState = LauncherAnimationState(),
        )
    }

    private fun rowLitPixelCount(buffer: PixelBuffer, row: Int): Int {
        if (row !in 0 until centeredRows) {
            return 0
        }
        val rowTop = centeredWindow.rowTop(row)
        val rowBottomExclusive = centeredWindow.rowBottomExclusive(row).coerceAtMost(buffer.height)
        var pixels = 0
        for (y in rowTop until rowBottomExclusive) {
            for (x in layout.textX until (layout.textX + layout.maxTextWidth).coerceAtMost(buffer.width)) {
                if (buffer.getPixel(x, y) != PixelBuffer.OFF) {
                    pixels += 1
                }
            }
        }
        return pixels
    }

    @Test
    fun centeredRowsUseVerticalCenteredTopOffset() {
        val apps = List(20) { index ->
            AppEntry(
                label = "AAAAA",
                packageName = "pkg.$index",
                activityName = "Activity$index",
            )
        }
        val buffer = renderBuffer(
            apps = apps,
            selectedIndex = 8,
            isSearchFocused = false,
            query = "",
        )

        val firstLitY = firstLitY(buffer)
        val centeredTop = centeredWindow.listTop
        assertTrue(firstLitY >= centeredTop)
    }

    @Test
    fun centeredRowGapStaysClearBetweenSelectedAndNeighborRows() {
        if (centerRow <= 0 || centerRow >= centeredRows - 1) {
            return
        }
        val apps = List(20) { index ->
            AppEntry(
                label = "AAAAA",
                packageName = "pkg.$index",
                activityName = "Activity$index",
            )
        }
        val buffer = renderBuffer(
            apps = apps,
            selectedIndex = 8,
            isSearchFocused = false,
            query = "",
        )

        val topGapStart = centeredWindow.rowBottomExclusive(centerRow - 1)
        val topGapEnd = centeredWindow.rowTop(centerRow)
        val bottomGapStart = centeredWindow.rowBottomExclusive(centerRow)
        val bottomGapEnd = centeredWindow.rowTop(centerRow + 1)
        assertTrue(isGapClear(buffer, topGapStart, topGapEnd))
        assertTrue(isGapClear(buffer, bottomGapStart, bottomGapEnd))
    }

    private fun firstLitY(buffer: PixelBuffer): Int {
        val listTop = layout.listStartY
        val listBottom = (layout.listStartY + layout.railHeight).coerceAtMost(buffer.height)
        for (y in listTop until listBottom) {
            for (x in layout.textX until (layout.textX + layout.maxTextWidth).coerceAtMost(buffer.width)) {
                if (buffer.getPixel(x, y) != PixelBuffer.OFF) {
                    return y
                }
            }
        }
        return Int.MAX_VALUE
    }

    private fun isGapClear(buffer: PixelBuffer, startY: Int, endY: Int): Boolean {
        if (endY <= startY) {
            return true
        }
        for (y in startY until endY.coerceAtMost(buffer.height)) {
            for (x in layout.textX until (layout.textX + layout.maxTextWidth).coerceAtMost(buffer.width)) {
                if (buffer.getPixel(x, y) != PixelBuffer.OFF) {
                    return false
                }
            }
        }
        return true
    }

    @Test
    fun searchWithQueryUsesPagedListInsteadOfCenteredList() {
        val apps = List(20) { index ->
            AppEntry(
                label = "AAAAA",
                packageName = "pkg.$index",
                activityName = "Activity$index",
            )
        }
        val pagedBuffer = renderBuffer(
            apps = apps,
            selectedIndex = 8,
            isSearchFocused = true,
            query = "A",
        )
        val centeredBuffer = renderBuffer(
            apps = apps,
            selectedIndex = 8,
            isSearchFocused = false,
            query = "",
        )

        val pagedFirstLitY = firstLitY(pagedBuffer)
        val centeredFirstLitY = firstLitY(centeredBuffer)

        assertTrue(pagedFirstLitY < centeredFirstLitY)
    }

    @Test
    fun focusedSearchWithoutQueryHidesListArea() {
        val apps = List(20) { index ->
            AppEntry(
                label = "AAAAA",
                packageName = "pkg.$index",
                activityName = "Activity$index",
            )
        }
        val hiddenListBuffer = renderBuffer(
            apps = apps,
            selectedIndex = 8,
            isSearchFocused = true,
            query = "",
        )

        var litPixels = 0
        val listTop = layout.listStartY
        val listBottom = (layout.listStartY + layout.railHeight).coerceAtMost(hiddenListBuffer.height)
        for (y in listTop until listBottom) {
            for (x in layout.textX until (layout.textX + layout.maxTextWidth).coerceAtMost(hiddenListBuffer.width)) {
                if (hiddenListBuffer.getPixel(x, y) != PixelBuffer.OFF) {
                    litPixels += 1
                }
            }
        }
        assertTrue(litPixels == 0)
    }

    private class BlockGlyphProvider : GlyphProvider {
        override fun rasterizeGlyph(character: Char, style: GlyphStyle): GlyphBitmap {
            val isWideGlyph = character.code !in 32..126
            val width = if (isWideGlyph) style.wideAdvanceWidth else style.narrowAdvanceWidth
            return GlyphBitmap(
                width = width,
                height = style.cellHeight,
                pixels = ByteArray(width * style.cellHeight) { 1 },
                metrics = GlyphMetrics(
                    advanceWidth = width,
                    baselineOffset = style.cellHeight - 2,
                    isWideGlyph = isWideGlyph,
                ),
            )
        }
    }
}
