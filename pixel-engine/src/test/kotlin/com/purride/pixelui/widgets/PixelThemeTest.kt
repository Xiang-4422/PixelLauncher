package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Column
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Checkbox
import com.purride.pixelui.FocusNode
import com.purride.pixelui.FocusScope
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeColors
import com.purride.pixelui.PixelThemeData
import com.purride.pixelui.Text
import com.purride.pixelui.TextButton
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

    @Test
    fun disabledButtonsUseThemeDisabledColorByDefault() {
        val disabled = PixelColor.fromRgb(40, 90, 200)
        val tester = PixelTester()

        tester.pumpWidget(
            widget = PixelTheme(
                data = PixelThemeData(colors = PixelThemeColors.Default.copy(disabled = disabled)),
                child = Column(
                    children = listOf(
                        OutlinedButton(text = "OK", onPressed = null),
                        TextButton(text = "NO", onPressed = null),
                    ),
                    spacing = 1,
                ),
            ),
            logicalWidth = 48,
            logicalHeight = 24,
        )

        assertTrue(tester.hasPixel(disabled))
        tester.dispose()
    }

    /** Compound controls use the inherited focus role instead of a fixed yellow outline. */
    @Test
    fun compoundControlUsesThemeFocusIndicator() {
        /** Sentinel color that cannot be confused with the legacy hard-coded focus yellow. */
        val focus = PixelColor.fromRgb(25, 210, 230)
        /** Explicit node used to place the compound Checkbox in the focused state. */
        val node = FocusNode("themed-checkbox")
        /** Off-screen runtime rendering the exact focused component pixels. */
        val tester = PixelTester()
        tester.pumpWidget(
            widget = PixelTheme(
                data = PixelThemeData(colors = PixelThemeColors.Default.copy(focus = focus)),
                child = FocusScope(
                    child = com.purride.pixelui.Focus(
                        node = node,
                        autofocus = true,
                        child = Checkbox(
                            checked = false,
                            onChanged = { },
                            semanticLabel = "THEMED CHECKBOX",
                        ),
                    ),
                ),
            ),
            logicalWidth = 16,
            logicalHeight = 16,
        )

        assertTrue(node.isFocused)
        assertTrue(tester.hasPixel(focus))
        assertFalse(tester.hasPixel(PixelColor.fromRgb(255, 200, 0)))
        tester.dispose()
    }
}
