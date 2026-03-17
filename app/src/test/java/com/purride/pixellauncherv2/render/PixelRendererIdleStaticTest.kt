package com.purride.pixellauncherv2.render

import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelRendererIdleStaticTest {

    private val screenProfile = ScreenProfile(
        logicalWidth = 40,
        logicalHeight = 40,
        dotSizePx = 10,
    )
    private val renderer = PixelRenderer(PixelFontEngine(BlockGlyphProvider()))

    @Test
    fun renderIdleStaticOmitsFluidPixels() {
        val state = LauncherState(
            mode = LauncherMode.IDLE,
            currentTimeText = "12:34",
            idleFluidState = IdleFluidState(
                width = 40,
                height = 40,
                litMask = BooleanArray(40 * 40) { index -> index % 7 == 0 },
            ),
        )

        val staticBuffer = renderer.renderIdleStatic(
            state = state,
            screenProfile = screenProfile,
        )
        val fullBuffer = renderer.render(
            state = state,
            screenProfile = screenProfile,
            animationState = LauncherAnimationState(),
        )

        assertTrue(litPixelCount(fullBuffer) > litPixelCount(staticBuffer))
    }

    private fun litPixelCount(buffer: PixelBuffer): Int {
        var count = 0
        for (y in 0 until buffer.height) {
            for (x in 0 until buffer.width) {
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
