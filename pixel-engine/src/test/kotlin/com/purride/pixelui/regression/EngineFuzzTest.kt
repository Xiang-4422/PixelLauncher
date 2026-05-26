package com.purride.pixelui.regression

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelBitmapRegion
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelSpriteSheet
import com.purride.pixelui.Alignment
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Stack
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class EngineFuzzTest {
    @Test
    fun randomBoundsAndHitTargetsDoNotCrash() {
        val random = Random(20260526L)
        repeat(80) {
            val tester = PixelTester()
            val width = 8 + random.nextInt(72)
            val height = 8 + random.nextInt(48)
            try {
                tester.pumpWidget(randomWidget(random, depth = 0, onTap = { }), width, height)
                assertEquals(width, tester.renderResult!!.buffer.width)
                assertEquals(height, tester.renderResult!!.buffer.height)
            } finally {
                tester.dispose()
            }
        }
    }

    @Test
    fun hitTargetFuzzClickDoesNotCrash() {
        repeat(20) { index ->
            var taps = 0
            val tester = PixelTester()
            try {
                tester.pumpWidget(
                    Center(
                        child = OutlinedButton(text = "TAP", onPressed = { taps += 1 }),
                    ),
                    logicalWidth = 24 + index,
                    logicalHeight = 12 + index,
                )
                tester.tap(com.purride.pixelui.testing.find.byText("TAP"))
                assertEquals(1, taps)
            } finally {
                tester.dispose()
            }
        }
    }

    @Test
    fun textSamplesRenderAcrossNarrowWidths() {
        val samples = listOf(
            "",
            "ASCII",
            "中文像素",
            "emoji🙂🙂",
            "LONGWORDWITHOUTBREAKSLONGWORDWITHOUTBREAKS",
            "LINE1\nLINE2\nLINE3",
        )
        samples.forEachIndexed { index, sample ->
            val tester = PixelTester()
            try {
                tester.pumpWidget(
                    Text(data = sample, softWrap = true, maxLines = 4),
                    logicalWidth = 1 + index * 3,
                    logicalHeight = 32,
                )
                assertEquals(32, tester.renderResult!!.buffer.height)
            } finally {
                tester.dispose()
            }
        }
    }

    @Test
    fun resourceRegionsRejectInvalidInputs() {
        val bitmap = PixelBitmap(width = 4, height = 4, pixels = IntArray(16) { PixelColor.White.argb })

        assertThrowsIllegalArgument { PixelBitmapRegion(left = -1, top = 0, width = 1, height = 1) }
        assertThrowsIllegalArgument { PixelBitmapRegion(left = 0, top = 0, width = 0, height = 1) }
        assertThrowsIllegalArgument { PixelSpriteSheet(bitmap = bitmap, frames = emptyList()) }
        assertThrowsIllegalArgument {
            PixelSpriteSheet(
                bitmap = bitmap,
                frames = listOf(PixelBitmapRegion(left = 3, top = 0, width = 2, height = 1)),
            )
        }
    }

    @Test
    fun transparentSourcePixelsKeepDestinationColor() {
        val destination = PixelBuffer(width = 2, height = 1)
        val source = PixelBuffer(width = 2, height = 1)
        destination.fillRect(0, 0, 2, 1, PixelColor.White)
        source.setPixel(0, 0, PixelColor.Transparent)
        source.setPixel(1, 0, PixelColor.fromArgb(128, 255, 0, 0))

        destination.blit(source, 0, 0)

        assertEquals(PixelColor.White, destination.getPixel(0, 0))
        assertTrue(destination.getPixel(1, 0).argb != PixelColor.White.argb)
    }

    private fun randomWidget(random: Random, depth: Int, onTap: () -> Unit): Widget {
        if (depth >= 3) {
            return when (random.nextInt(3)) {
                0 -> Text("TXT")
                1 -> SizedBox(width = random.nextInt(12), height = random.nextInt(12))
                else -> GestureDetector(
                    child = Container(child = Text("TAP"), padding = EdgeInsets.all(1), borderColor = PixelColor.White),
                    onTap = onTap,
                )
            }
        }
        return when (random.nextInt(5)) {
            0 -> Row(
                children = List(1 + random.nextInt(3)) { randomWidget(random, depth + 1, onTap) },
                spacing = random.nextInt(3),
            )
            1 -> Column(
                children = List(1 + random.nextInt(3)) { randomWidget(random, depth + 1, onTap) },
                spacing = random.nextInt(3),
            )
            2 -> Stack(
                children = List(1 + random.nextInt(3)) { randomWidget(random, depth + 1, onTap) },
            )
            3 -> Container(
                child = randomWidget(random, depth + 1, onTap),
                width = random.nextInt(28),
                height = random.nextInt(20),
                padding = EdgeInsets.all(random.nextInt(3)),
                borderColor = if (random.nextBoolean()) PixelColor.White else null,
                alignment = Alignment.CENTER,
            )
            else -> Center(child = randomWidget(random, depth + 1, onTap))
        }
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("Expected IllegalArgumentException")
    }
}
