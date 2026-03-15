package com.purride.pixellauncherv2.render

import com.purride.pixellauncherv2.launcher.AppEntry
import com.purride.pixellauncherv2.launcher.AppListLayout
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
    fun railShowsSingleLetterAndMagnifiesDuringSliding() {
        val idleBuffer = renderBuffer(selectedIndex = 1, isRailSliding = false)
        val slidingBuffer = renderBuffer(selectedIndex = 1, isRailSliding = true)
        val idleRows = railLitRows(idleBuffer)
        val slidingRows = railLitRows(slidingBuffer)

        assertTrue(idleRows.isNotEmpty())
        assertTrue(slidingRows.isNotEmpty())
        assertEquals(1, contiguousRowSegments(idleRows))
        assertEquals(1, contiguousRowSegments(slidingRows))
        assertTrue(railLitPixelCount(slidingBuffer) > railLitPixelCount(idleBuffer))
    }

    @Test
    fun railLetterStaysOnSearchRowForDifferentLetters() {
        val alphaCenter = railLitCenterY(renderBuffer(selectedIndex = 0, isRailSliding = false))
        val zuluCenter = railLitCenterY(renderBuffer(selectedIndex = 2, isRailSliding = false))
        val expectedCenter = layout.searchTextY + (GlyphStyle.APP_LABEL_16.cellHeight / 2)

        assertTrue(abs(alphaCenter - expectedCenter) <= 1)
        assertTrue(abs(zuluCenter - expectedCenter) <= 1)
        assertTrue(abs(alphaCenter - zuluCenter) <= 1)
    }

    @Test
    fun railLetterIsRightAlignedInRailArea() {
        val idleRightMostX = railRightMostLitX(renderBuffer(selectedIndex = 1, isRailSliding = false))
        val slidingRightMostX = railRightMostLitX(renderBuffer(selectedIndex = 1, isRailSliding = true))
        val railRight = layout.indexRailLeft + layout.indexRailWidth - 1

        assertEquals(railRight, idleRightMostX)
        assertEquals(railRight, slidingRightMostX)
    }

    private fun renderBuffer(selectedIndex: Int, isRailSliding: Boolean): PixelBuffer {
        val state = LauncherState(
            apps = drawerApps,
            drawerVisibleApps = drawerApps,
            drawerQuery = "",
            isDrawerSearchFocused = false,
            isDrawerRailSliding = isRailSliding,
            selectedIndex = selectedIndex,
            listStartIndex = 0,
            drawerPageIndex = 0,
            isLoading = false,
            mode = LauncherMode.APP_DRAWER,
        )
        return renderer.render(
            state = state,
            screenProfile = screenProfile,
            animationState = LauncherAnimationState(),
        )
    }

    private fun railLitCenterY(buffer: PixelBuffer): Int {
        val litRows = railLitRows(buffer)
        assertTrue(litRows.isNotEmpty())
        return litRows.sum() / litRows.size
    }

    private fun railLitRows(buffer: PixelBuffer): List<Int> {
        val rows = mutableSetOf<Int>()
        val top = letterRegionTop(buffer)
        val bottomExclusive = letterRegionBottomExclusive(buffer)
        for (y in top until bottomExclusive) {
            for (x in layout.indexRailLeft until (layout.indexRailLeft + layout.indexRailWidth)) {
                if (buffer.getPixel(x, y) != PixelBuffer.OFF) {
                    rows += y
                    break
                }
            }
        }
        return rows.sorted()
    }

    private fun railLitPixelCount(buffer: PixelBuffer): Int {
        var count = 0
        val top = letterRegionTop(buffer)
        val bottomExclusive = letterRegionBottomExclusive(buffer)
        for (y in top until bottomExclusive) {
            for (x in layout.indexRailLeft until (layout.indexRailLeft + layout.indexRailWidth)) {
                if (buffer.getPixel(x, y) != PixelBuffer.OFF) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun railRightMostLitX(buffer: PixelBuffer): Int {
        var rightMostX = -1
        val top = letterRegionTop(buffer)
        val bottomExclusive = letterRegionBottomExclusive(buffer)
        for (y in top until bottomExclusive) {
            for (x in layout.indexRailLeft until (layout.indexRailLeft + layout.indexRailWidth)) {
                if (buffer.getPixel(x, y) != PixelBuffer.OFF) {
                    rightMostX = maxOf(rightMostX, x)
                }
            }
        }
        return rightMostX
    }

    private fun contiguousRowSegments(rows: List<Int>): Int {
        if (rows.isEmpty()) {
            return 0
        }
        var segments = 1
        var previous = rows.first()
        for (index in 1 until rows.size) {
            val current = rows[index]
            if (current - previous > 1) {
                segments += 1
            }
            previous = current
        }
        return segments
    }

    private fun letterRegionTop(buffer: PixelBuffer): Int {
        return layout.searchTextY.coerceIn(0, buffer.height)
    }

    private fun letterRegionBottomExclusive(buffer: PixelBuffer): Int {
        return (layout.searchTextY + GlyphStyle.APP_LABEL_16.cellHeight).coerceIn(0, buffer.height)
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
