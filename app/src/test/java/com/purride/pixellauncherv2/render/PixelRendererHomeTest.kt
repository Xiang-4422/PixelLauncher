package com.purride.pixellauncherv2.render

import com.purride.pixellauncherv2.launcher.HomeLayout
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelRendererHomeTest {

    private val screenProfile = ScreenProfile(
        logicalWidth = 72,
        logicalHeight = 160,
        dotSizePx = 15,
    )
    private val homeLayout = HomeLayout.metrics(screenProfile)
    private val renderer = PixelRenderer(PixelFontEngine(BlockGlyphProvider()))

    @Test
    fun homeKeepsRainRowWhenHintTextIsBlank() {
        val withoutRain = renderHome(
            rainHintText = "",
            missedCallCount = 0,
            unreadSmsCount = 0,
        )
        val withRain = renderHome(
            rainHintText = "LOC",
            missedCallCount = 0,
            unreadSmsCount = 0,
        )

        val withoutRainPixels = fixedInfoLitPixels(withoutRain)
        val withRainPixels = fixedInfoLitPixels(withRain)

        assertTrue(withoutRainPixels > 0)
        assertTrue(withRainPixels >= withoutRainPixels)
    }

    @Test
    fun homeStillShowsDynamicInfoRowWhenOnlyRainPlaceholderIsPresent() {
        val withRainPlaceholder = renderHome(
            rainHintText = "",
            missedCallCount = 0,
            unreadSmsCount = 0,
        )
        val withDynamicInfo = renderHome(
            rainHintText = "LOC",
            missedCallCount = 1,
            unreadSmsCount = 2,
        )

        assertTrue(fixedInfoLitPixels(withRainPlaceholder) > 0)
        assertTrue(fixedInfoLitPixels(withDynamicInfo) > fixedInfoLitPixels(withRainPlaceholder))
    }

    @Test
    fun homeBottomButtonsUseMeasuredWidthsForPropFonts() {
        val propFontEngine = PixelFontEngine(VariableWidthGlyphProvider())
        val contactWidth = propFontEngine.measureText("CONTACT", GlyphStyle.UI_SMALL_10)
        val smsWidth = propFontEngine.measureText("SMS", GlyphStyle.UI_SMALL_10)
        val layout = HomeLayout.metrics(
            screenProfile = screenProfile,
            contactButtonWidth = contactWidth,
            smsButtonWidth = smsWidth,
        )

        assertEquals(contactWidth, layout.contactButtonRight - layout.contactButtonLeft + 1)
        assertEquals(smsWidth, layout.smsButtonRight - layout.smsButtonLeft + 1)
    }

    private fun renderHome(
        rainHintText: String,
        missedCallCount: Int = 1,
        unreadSmsCount: Int = 2,
    ): PixelBuffer {
        return renderer.render(
            state = LauncherState(
                mode = LauncherMode.HOME,
                currentDateText = "TUESDAY MAR 17",
                nextAlarmText = "07:30",
                missedCallCount = missedCallCount,
                unreadSmsCount = unreadSmsCount,
                rainHintText = rainHintText,
                screenUsageTimeText = "02:15",
                screenOpenCountText = "17",
                terminalStatusText = "READY",
            ),
            screenProfile = screenProfile,
            animationState = LauncherAnimationState(),
        )
    }

    private fun fixedInfoLitPixels(buffer: PixelBuffer): Int {
        var litPixels = 0
        val top = homeLayout.fixedInfoStartY.coerceIn(0, buffer.height)
        val bottom = homeLayout.fixedBottom.coerceIn(top, buffer.height)
        for (y in top until bottom) {
            for (x in homeLayout.innerLeft until homeLayout.innerRight.coerceAtMost(buffer.width)) {
                if (buffer.getPixel(x, y) != PixelBuffer.OFF) {
                    litPixels += 1
                }
            }
        }
        return litPixels
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

    private class VariableWidthGlyphProvider : GlyphProvider {
        override fun rasterizeGlyph(character: Char, style: GlyphStyle): GlyphBitmap {
            val isWideGlyph = character.code !in 32..126
            val width = when (character) {
                'T', 'S' -> style.narrowAdvanceWidth + 2
                else -> if (isWideGlyph) style.wideAdvanceWidth else style.narrowAdvanceWidth
            }
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
