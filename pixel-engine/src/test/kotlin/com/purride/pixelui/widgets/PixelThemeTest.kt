package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeColors
import com.purride.pixelui.PixelThemeData
import com.purride.pixelui.Text
import com.purride.pixelui.TextField
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelThemeTest {
    @Test
    fun textUsesThemeDefaultStyle() {
        val ink = PixelColor.fromRgb(20, 220, 180)
        val tester = PixelTester()

        tester.pumpWidget(
            widget = PixelTheme(
                data = PixelThemeData(colors = PixelThemeColors.Default.copy(text = ink)),
                child = Text("I"),
            ),
            logicalWidth = 16,
            logicalHeight = 8,
        )

        assertTrue(tester.hasPixel(ink))
        assertFalse(tester.hasPixel(PixelColor.White))
    }

    @Test
    fun explicitTextColorOverridesTheme() {
        val ink = PixelColor.fromRgb(20, 220, 180)
        val tester = PixelTester()

        tester.pumpWidget(
            widget = PixelTheme(
                data = PixelThemeData(colors = PixelThemeColors.Default.copy(text = ink)),
                child = Text("I", color = PixelColor.White),
            ),
            logicalWidth = 16,
            logicalHeight = 8,
        )

        assertTrue(tester.hasPixel(PixelColor.White))
        assertFalse(tester.hasPixel(ink))
    }

    @Test
    fun buttonAndTextFieldUseThemeBorderByDefault() {
        val border = PixelColor.fromRgb(180, 30, 220)
        val controller = PixelTextFieldController()
        val state = controller.create()
        val tester = PixelTester()

        tester.pumpWidget(
            widget = PixelTheme(
                data = PixelThemeData(colors = PixelThemeColors.Default.copy(border = border)),
                child = com.purride.pixelui.Column(
                    children = listOf(
                        OutlinedButton(text = "OK", onPressed = {}),
                        TextField(state = state, controller = controller, placeholder = "NAME"),
                    ),
                    spacing = 1,
                ),
            ),
            logicalWidth = 48,
            logicalHeight = 24,
        )

        assertTrue(tester.hasPixel(border))
    }

    private fun PixelTester.hasPixel(color: PixelColor): Boolean {
        return requireNotNull(renderResult).buffer.pixels.any { it == color.argb }
    }
}
