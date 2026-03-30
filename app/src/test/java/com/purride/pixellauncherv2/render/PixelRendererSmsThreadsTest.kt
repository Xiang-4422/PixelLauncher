package com.purride.pixellauncherv2.render

import com.purride.pixellauncherv2.data.SmsThreadSummary
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelRendererSmsThreadsTest {

    private val screenProfile = ScreenProfile(
        logicalWidth = 72,
        logicalHeight = 160,
        dotSizePx = 15,
    )
    private val layout = com.purride.pixellauncherv2.launcher.SmsLayout.threadListMetrics(screenProfile)
    private val renderer = PixelRenderer(PixelFontEngine(BlockGlyphProvider()))

    @Test
    fun smsThreadListScrollOffsetMatchesOtherTextLists() {
        val threads = List(10) { index ->
            SmsThreadSummary(
                threadId = index.toLong(),
                address = "12345$index",
                snippet = "THREAD $index",
                dateMillis = 0L,
                unreadCount = 1,
                messageCount = 1,
            )
        }

        val baseline = renderBuffer(
            threads = threads,
            listStartIndex = 0,
            selectedIndex = 0,
            scrollOffsetPx = 0,
        )
        val shifted = renderBuffer(
            threads = threads,
            listStartIndex = 0,
            selectedIndex = 0,
            scrollOffsetPx = 5,
        )

        assertTrue(firstLitYInList(shifted) > firstLitYInList(baseline))
    }

    private fun renderBuffer(
        threads: List<SmsThreadSummary>,
        listStartIndex: Int,
        selectedIndex: Int,
        scrollOffsetPx: Int,
    ): PixelBuffer {
        val state = LauncherState(
            mode = LauncherMode.SMS_THREADS,
            smsThreads = threads,
            smsThreadListStartIndex = listStartIndex,
            smsThreadSelectedIndex = selectedIndex,
        )
        return renderer.render(
            state = state,
            screenProfile = screenProfile,
            animationState = LauncherAnimationState(),
            settingsListScrollOffsetPx = scrollOffsetPx,
        )
    }

    private fun firstLitYInList(buffer: PixelBuffer): Int {
        for (y in layout.textList.viewport.top until layout.panelBottom.coerceAtMost(buffer.height)) {
            for (x in layout.rowTextX until (layout.rowTextX + layout.rowMaxWidth).coerceAtMost(buffer.width)) {
                if (buffer.getPixel(x, y) != PixelBuffer.OFF) {
                    return y
                }
            }
        }
        return Int.MAX_VALUE
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
