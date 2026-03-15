package com.purride.pixellauncherv2.render

import com.purride.pixellauncherv2.launcher.AppEntry
import com.purride.pixellauncherv2.launcher.AppListLayout
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
        drawerVisibleApps: List<AppEntry> = apps,
        headerChargeTick: Int = 0,
    ): PixelBuffer {
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            apps = apps,
            drawerVisibleApps = drawerVisibleApps,
            drawerQuery = query,
            isDrawerSearchFocused = isSearchFocused,
            selectedIndex = selectedIndex,
            listStartIndex = selectedIndex.coerceAtLeast(0),
            isLoading = false,
        )
        return renderer.render(
            state = state,
            screenProfile = screenProfile,
            animationState = LauncherAnimationState(headerChargeTick = headerChargeTick),
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

    @Test
    fun cursorBlinkRemainsVisibleLongerBeforeTogglingOff() {
        val apps = List(6) { index ->
            AppEntry(
                label = "AAAAA",
                packageName = "pkg.$index",
                activityName = "Activity$index",
            )
        }
        val tick0 = renderBuffer(
            apps = apps,
            selectedIndex = 0,
            isSearchFocused = true,
            query = "",
            headerChargeTick = 0,
        )
        val tick10 = renderBuffer(
            apps = apps,
            selectedIndex = 0,
            isSearchFocused = true,
            query = "",
            headerChargeTick = 10,
        )
        val tick14 = renderBuffer(
            apps = apps,
            selectedIndex = 0,
            isSearchFocused = true,
            query = "",
            headerChargeTick = 14,
        )

        val litAt0 = searchBoxLitPixelCount(tick0)
        val litAt10 = searchBoxLitPixelCount(tick10)
        val litAt14 = searchBoxLitPixelCount(tick14)

        assertTrue(litAt0 > 0)
        assertTrue(litAt10 > 0)
        assertTrue(litAt14 == 0)
    }

    @Test
    fun noResultMessageIsCenteredInListArea() {
        val allApps = listOf(
            AppEntry(label = "Alpha", packageName = "pkg.alpha", activityName = "A"),
            AppEntry(label = "Bravo", packageName = "pkg.bravo", activityName = "B"),
        )
        val buffer = renderBuffer(
            apps = allApps,
            selectedIndex = 0,
            isSearchFocused = true,
            query = "zzz",
            drawerVisibleApps = emptyList(),
        )

        val firstLitY = firstLitY(buffer)
        val expectedY = layout.listStartY +
            ((layout.railHeight - GlyphStyle.APP_LABEL_16.cellHeight) / 2).coerceAtLeast(0)
        assertTrue(abs(firstLitY - expectedY) <= 1)
    }

    private fun searchBoxLitPixelCount(buffer: PixelBuffer): Int {
        var count = 0
        val startY = layout.searchTextY.coerceIn(0, buffer.height)
        val endY = (layout.searchTextY + layout.searchHeight).coerceIn(0, buffer.height)
        val startX = layout.searchTextX.coerceIn(0, buffer.width)
        val endX = (layout.searchTextX + layout.searchWidth).coerceIn(0, buffer.width)
        for (y in startY until endY) {
            for (x in startX until endX) {
                if (buffer.getPixel(x, y) != PixelBuffer.OFF) {
                    count += 1
                }
            }
        }
        return count
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
