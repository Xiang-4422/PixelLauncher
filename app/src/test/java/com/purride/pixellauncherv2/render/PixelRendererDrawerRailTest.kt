package com.purride.pixellauncherv2.render

import com.purride.pixellauncherv2.launcher.AppEntry
import com.purride.pixellauncherv2.launcher.AppListLayout
import com.purride.pixellauncherv2.launcher.LauncherHeaderLayout
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelRendererDrawerRailTest {

    private val screenProfile = ScreenProfile(
        logicalWidth = 72,
        logicalHeight = 160,
        dotSizePx = 15,
    )
    private val layout = AppListLayout.metrics(screenProfile)
    private val renderer = PixelRenderer(PixelFontEngine(BlockGlyphProvider()))
    private val drawerApps = listOf(
        AppEntry(label = "Alpha", packageName = "pkg.alpha", activityName = "A"),
        AppEntry(label = "Mike", packageName = "pkg.mike", activityName = "M"),
        AppEntry(label = "Zulu", packageName = "pkg.zulu", activityName = "Z"),
    )

    @Test
    fun idleHeaderShowsLeftTimeCenterSearchAndRightAppsLetter() {
        val buffer = renderBuffer(
            selectedIndex = 1,
            isSearchFocused = false,
            query = "",
        )

        val leftPixels = headerRegionLitPixelCount(buffer, 0, screenProfile.logicalWidth / 4)
        val centerPixels = headerRegionLitPixelCount(
            buffer,
            (screenProfile.logicalWidth / 3),
            ((screenProfile.logicalWidth * 2) / 3),
        )
        val rightPixels = headerRegionLitPixelCount(
            buffer,
            ((screenProfile.logicalWidth * 3) / 4),
            screenProfile.logicalWidth,
        )

        assertTrue(leftPixels > 0)
        assertTrue(centerPixels > 0)
        assertTrue(rightPixels > 0)
    }

    @Test
    fun searchHeaderHidesTimeAndAppsLetterWhileKeepingCenteredQuery() {
        val idleBuffer = renderBuffer(
            selectedIndex = 1,
            isSearchFocused = false,
            query = "",
        )
        val searchBuffer = renderBuffer(
            selectedIndex = 1,
            isSearchFocused = true,
            query = "A",
        )

        val leftBoundary = screenProfile.logicalWidth / 4
        val rightBoundary = (screenProfile.logicalWidth * 3) / 4
        val idleLeft = headerRegionLitPixelCount(idleBuffer, 0, leftBoundary)
        val idleRight = headerRegionLitPixelCount(idleBuffer, rightBoundary, screenProfile.logicalWidth)
        val searchLeft = headerRegionLitPixelCount(searchBuffer, 0, leftBoundary)
        val searchCenter = headerRegionLitPixelCount(searchBuffer, leftBoundary, rightBoundary)
        val searchRight = headerRegionLitPixelCount(searchBuffer, rightBoundary, screenProfile.logicalWidth)

        assertTrue(idleLeft > 0)
        assertTrue(idleRight > 0)
        assertEquals(0, searchLeft)
        assertTrue(searchCenter > 0)
        assertEquals(0, searchRight)
    }

    private fun renderBuffer(
        selectedIndex: Int,
        isSearchFocused: Boolean,
        query: String,
    ): PixelBuffer {
        val visibleApps = if (query.isBlank()) drawerApps else drawerApps.filter { it.label.contains(query, ignoreCase = true) }
        val safeSelectedIndex = selectedIndex.coerceAtMost(visibleApps.lastIndex.coerceAtLeast(0))
        val state = LauncherState(
            apps = drawerApps,
            drawerVisibleApps = visibleApps,
            drawerQuery = query,
            isDrawerSearchFocused = isSearchFocused,
            selectedIndex = safeSelectedIndex,
            listStartIndex = safeSelectedIndex,
            drawerPageIndex = 0,
            isLoading = false,
            mode = LauncherMode.APP_DRAWER,
            currentTimeText = "09:41",
        )
        return renderer.render(
            state = state,
            screenProfile = screenProfile,
            animationState = LauncherAnimationState(),
        )
    }

    private fun headerRegionLitPixelCount(buffer: PixelBuffer, startX: Int, endXExclusive: Int): Int {
        var count = 0
        val safeStartX = startX.coerceIn(0, buffer.width)
        val safeEndX = endXExclusive.coerceIn(safeStartX, buffer.width)
        val safeStartY = layout.headerTop.coerceIn(0, buffer.height)
        val safeEndY = LauncherHeaderLayout.dividerY.coerceIn(safeStartY, buffer.height)
        for (y in safeStartY until safeEndY) {
            for (x in safeStartX until safeEndX) {
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
