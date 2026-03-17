package com.purride.pixellauncherv2.render

import com.purride.pixellauncherv2.launcher.AppEntry
import com.purride.pixellauncherv2.launcher.AppListLayout
import com.purride.pixellauncherv2.launcher.DrawerListAlignment
import com.purride.pixellauncherv2.launcher.LauncherHeaderLayout
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import org.junit.Assert.assertEquals
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
    private val renderer = PixelRenderer(PixelFontEngine(BlockGlyphProvider()))

    @Test
    fun nonSearchDrawerDoesNotHighlightVisibleRows() {
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
            listStartIndex = 8,
            isSearchFocused = false,
            query = "",
        )

        val firstRowAccent = rowAccentPixelCount(buffer, row = 0, offsetPx = 0)
        val secondRowAccent = rowAccentPixelCount(buffer, row = 1, offsetPx = 0)

        assertEquals(0, firstRowAccent)
        assertEquals(0, secondRowAccent)
    }

    @Test
    fun nonSearchDrawerStartsRenderingFromListTop() {
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
            listStartIndex = 8,
            isSearchFocused = false,
            query = "",
        )

        val firstLitY = firstLitY(buffer)
        assertTrue(firstLitY >= layout.listStartY)
        assertTrue(firstLitY < layout.listStartY + layout.rowHeight)
    }

    @Test
    fun nonSearchDrawerClipsOverflowRowInsteadOfDroppingIt() {
        val overflowTop = layout.listStartY + (layout.visibleRows * layout.rowHeight)
        val clipBottomExclusive = layout.listStartY + layout.railHeight
        if (overflowTop >= clipBottomExclusive) {
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
            selectedIndex = 0,
            listStartIndex = 0,
            isSearchFocused = false,
            query = "",
        )

        var overflowPixels = 0
        for (y in overflowTop until clipBottomExclusive.coerceAtMost(buffer.height)) {
            for (x in layout.textX until (layout.textX + layout.maxTextWidth).coerceAtMost(buffer.width)) {
                if (buffer.getPixel(x, y) != PixelBuffer.OFF) {
                    overflowPixels += 1
                }
            }
        }

        assertTrue(overflowPixels > 0)
    }

    @Test
    fun nonSearchScrollOffsetDoesNotIntroduceHighlight() {
        val apps = List(20) { index ->
            AppEntry(
                label = "AAAAA",
                packageName = "pkg.$index",
                activityName = "Activity$index",
            )
        }
        val smallOffset = -5
        val largeOffset = -16
        val smallBuffer = renderBuffer(
            apps = apps,
            selectedIndex = 8,
            listStartIndex = 8,
            isSearchFocused = false,
            query = "",
            drawerScrollOffsetPx = smallOffset,
        )
        val largeBuffer = renderBuffer(
            apps = apps,
            selectedIndex = 8,
            listStartIndex = 8,
            isSearchFocused = false,
            query = "",
            drawerScrollOffsetPx = largeOffset,
        )

        assertEquals(0, rowAccentPixelCount(smallBuffer, row = 0, offsetPx = smallOffset))
        assertEquals(0, rowAccentPixelCount(smallBuffer, row = 1, offsetPx = smallOffset))
        assertEquals(0, rowAccentPixelCount(largeBuffer, row = 1, offsetPx = largeOffset))
    }

    @Test
    fun drawerAlignmentChangesTextStartX() {
        val apps = listOf(
            AppEntry(
                label = "AAAAA",
                packageName = "pkg.a",
                activityName = "ActivityA",
            ),
        )
        val leftBuffer = renderBuffer(
            apps = apps,
            selectedIndex = 0,
            listStartIndex = 0,
            isSearchFocused = false,
            query = "",
            drawerListAlignment = DrawerListAlignment.LEFT,
        )
        val centerBuffer = renderBuffer(
            apps = apps,
            selectedIndex = 0,
            listStartIndex = 0,
            isSearchFocused = false,
            query = "",
            drawerListAlignment = DrawerListAlignment.CENTER,
        )
        val rightBuffer = renderBuffer(
            apps = apps,
            selectedIndex = 0,
            listStartIndex = 0,
            isSearchFocused = false,
            query = "",
            drawerListAlignment = DrawerListAlignment.RIGHT,
        )

        val leftX = firstLitXForRow(leftBuffer, row = 0)
        val centerX = firstLitXForRow(centerBuffer, row = 0)
        val rightX = firstLitXForRow(rightBuffer, row = 0)

        assertTrue(leftX < centerX)
        assertTrue(centerX < rightX)
    }

    @Test
    fun rightAlignmentCanUseHiddenRailSpace() {
        val apps = listOf(
            AppEntry(
                label = "AAAAAAAAAAAA",
                packageName = "pkg.a",
                activityName = "ActivityA",
            ),
        )
        val rightBuffer = renderBuffer(
            apps = apps,
            selectedIndex = 0,
            listStartIndex = 0,
            isSearchFocused = false,
            query = "",
            drawerListAlignment = DrawerListAlignment.RIGHT,
        )

        val lastX = lastLitXForRow(rightBuffer, row = 0)

        assertTrue(lastX >= layout.hiddenRailLeft)
    }

    @Test
    fun searchWithQueryDoesNotHighlightVisibleRows() {
        val apps = List(20) { index ->
            AppEntry(
                label = "AAAAA",
                packageName = "pkg.$index",
                activityName = "Activity$index",
            )
        }
        val searchBuffer = renderBuffer(
            apps = apps,
            selectedIndex = 8,
            listStartIndex = 8,
            isSearchFocused = true,
            query = "A",
        )

        val firstRowAccent = rowAccentPixelCount(searchBuffer, row = 0, offsetPx = 0)
        val secondRowAccent = rowAccentPixelCount(searchBuffer, row = 1, offsetPx = 0)

        assertEquals(0, firstRowAccent)
        assertEquals(0, secondRowAccent)
    }

    @Test
    fun searchRowsFollowDrawerScrollOffset() {
        val apps = List(20) { index ->
            AppEntry(
                label = "AAAAA",
                packageName = "pkg.$index",
                activityName = "Activity$index",
            )
        }
        val baseBuffer = renderBuffer(
            apps = apps,
            selectedIndex = 8,
            listStartIndex = 8,
            isSearchFocused = true,
            query = "A",
            drawerScrollOffsetPx = 0,
        )
        val shiftedBuffer = renderBuffer(
            apps = apps,
            selectedIndex = 8,
            listStartIndex = 8,
            isSearchFocused = true,
            query = "A",
            drawerScrollOffsetPx = 5,
        )

        assertTrue(pixelDiffCount(baseBuffer, shiftedBuffer) > 0)
    }

    @Test
    fun searchScrollDoesNotIntrudeIntoHeaderArea() {
        val apps = List(30) { index ->
            AppEntry(
                label = "AAAAA",
                packageName = "pkg.$index",
                activityName = "Activity$index",
            )
        }
        val baseline = renderBuffer(
            apps = apps,
            selectedIndex = 12,
            listStartIndex = 12,
            isSearchFocused = true,
            query = "A",
            drawerScrollOffsetPx = 0,
        )
        val shifted = renderBuffer(
            apps = apps,
            selectedIndex = 12,
            listStartIndex = 12,
            isSearchFocused = true,
            query = "A",
            drawerScrollOffsetPx = 18,
        )

        assertEquals(0, pixelDiffCountInSearchHeader(baseline, shifted))
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
            listStartIndex = 8,
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
        assertEquals(0, litPixels)
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
            listStartIndex = 0,
            isSearchFocused = true,
            query = "",
            headerChargeTick = 0,
        )
        val tick10 = renderBuffer(
            apps = apps,
            selectedIndex = 0,
            listStartIndex = 0,
            isSearchFocused = true,
            query = "",
            headerChargeTick = 10,
        )
        val tick14 = renderBuffer(
            apps = apps,
            selectedIndex = 0,
            listStartIndex = 0,
            isSearchFocused = true,
            query = "",
            headerChargeTick = 14,
        )

        val litAt0 = searchHeaderLitPixelCount(tick0)
        val litAt10 = searchHeaderLitPixelCount(tick10)
        val litAt14 = searchHeaderLitPixelCount(tick14)

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
            listStartIndex = 0,
            isSearchFocused = true,
            query = "zzz",
            drawerVisibleApps = emptyList(),
        )

        val firstLitY = firstLitY(buffer)
        val expectedY = layout.listStartY +
            ((layout.railHeight - GlyphStyle.APP_LABEL_16.cellHeight) / 2).coerceAtLeast(0)
        assertTrue(abs(firstLitY - expectedY) <= 1)
    }

    private fun renderBuffer(
        apps: List<AppEntry>,
        selectedIndex: Int,
        listStartIndex: Int,
        isSearchFocused: Boolean,
        query: String,
        drawerVisibleApps: List<AppEntry> = apps,
        drawerListAlignment: DrawerListAlignment = DrawerListAlignment.LEFT,
        headerChargeTick: Int = 0,
        drawerScrollOffsetPx: Int = 0,
    ): PixelBuffer {
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            apps = apps,
            drawerVisibleApps = drawerVisibleApps,
            drawerQuery = query,
            isDrawerSearchFocused = isSearchFocused,
            selectedIndex = selectedIndex,
            listStartIndex = listStartIndex,
            drawerListAlignment = drawerListAlignment,
            isLoading = false,
        )
        return renderer.render(
            state = state,
            screenProfile = screenProfile,
            animationState = LauncherAnimationState(headerChargeTick = headerChargeTick),
            drawerListScrollOffsetPx = drawerScrollOffsetPx,
        )
    }

    private fun rowAccentPixelCount(buffer: PixelBuffer, row: Int, offsetPx: Int): Int {
        if (row !in 0 until layout.visibleRows) {
            return 0
        }
        val rowTop = layout.listStartY + (row * layout.rowHeight) + offsetPx
        val rowBottomExclusive = (rowTop + layout.rowHeight).coerceAtMost(buffer.height)
        var pixels = 0
        for (y in rowTop.coerceAtLeast(0) until rowBottomExclusive) {
            for (x in layout.textX until (layout.textX + layout.maxTextWidth).coerceAtMost(buffer.width)) {
                if (buffer.getPixel(x, y) == PixelBuffer.ACCENT) {
                    pixels += 1
                }
            }
        }
        return pixels
    }

    private fun firstLitXForRow(buffer: PixelBuffer, row: Int): Int {
        if (row !in 0 until layout.visibleRows) {
            return Int.MAX_VALUE
        }
        val rowTop = (layout.listStartY + (row * layout.rowHeight)).coerceAtLeast(0)
        val rowBottomExclusive = (rowTop + layout.rowHeight).coerceAtMost(buffer.height)
        for (x in 0 until buffer.width) {
            for (y in rowTop until rowBottomExclusive) {
                if (buffer.getPixel(x, y) != PixelBuffer.OFF) {
                    return x
                }
            }
        }
        return Int.MAX_VALUE
    }

    private fun lastLitXForRow(buffer: PixelBuffer, row: Int): Int {
        if (row !in 0 until layout.visibleRows) {
            return Int.MIN_VALUE
        }
        val rowTop = (layout.listStartY + (row * layout.rowHeight)).coerceAtLeast(0)
        val rowBottomExclusive = (rowTop + layout.rowHeight).coerceAtMost(buffer.height)
        for (x in buffer.width - 1 downTo 0) {
            for (y in rowTop until rowBottomExclusive) {
                if (buffer.getPixel(x, y) != PixelBuffer.OFF) {
                    return x
                }
            }
        }
        return Int.MIN_VALUE
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

    private fun pixelDiffCount(left: PixelBuffer, right: PixelBuffer): Int {
        var diff = 0
        val width = minOf(left.width, right.width)
        val height = minOf(left.height, right.height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (left.getPixel(x, y) != right.getPixel(x, y)) {
                    diff += 1
                }
            }
        }
        return diff
    }

    private fun pixelDiffCountInSearchHeader(left: PixelBuffer, right: PixelBuffer): Int {
        var diff = 0
        val startY = layout.headerTop.coerceAtLeast(0)
        val endY = layout.headerBottomExclusive.coerceAtMost(left.height)
        val startX = 0
        val endX = left.width.coerceAtMost(right.width)
        for (y in startY until endY) {
            for (x in startX until endX) {
                if (left.getPixel(x, y) != right.getPixel(x, y)) {
                    diff += 1
                }
            }
        }
        return diff
    }

    private fun searchHeaderLitPixelCount(buffer: PixelBuffer): Int {
        var count = 0
        val startY = layout.headerTop.coerceIn(0, buffer.height)
        val endY = LauncherHeaderLayout.dividerY.coerceIn(startY, buffer.height)
        val startX = 0
        val endX = buffer.width
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
