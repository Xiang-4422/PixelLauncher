package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.IconButton
import com.purride.pixelui.PixelColorRole
import com.purride.pixelui.PixelColorScheme
import com.purride.pixelui.PixelComponentTokens
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelIconData
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.PixelStateProperty
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.Radio
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies independent radio/iconButton tokens and retained capability-state transitions. */
class SelectionControlsThemeStateTest {
    /** Radio and IconButton consume only their independent component-token families. */
    @Test
    fun controlsResolveIndependentComponentTokens() {
        /** Custom danger role used exclusively by the Radio surface. */
        val radioSentinel = PixelColor.fromRgb(201, 31, 71)
        /** Custom warning role used exclusively by the IconButton surface. */
        val iconSentinel = PixelColor.fromRgb(37, 211, 149)
        /** Scheme assigning unmistakable values to the two custom component roles. */
        val colors = PixelColorScheme.Dark.copy(danger = radioSentinel, warning = iconSentinel)
        /** Radio token override proving the family is independent from Checkbox. */
        val radioTokens = PixelComponentTokens.Default.radio.copy(
            containerColor = PixelStateProperty.constant(PixelColorRole.Danger),
        )
        /** IconButton token override proving the family is independent from outlined Button. */
        val iconTokens = PixelComponentTokens.Default.iconButton.copy(
            containerColor = PixelStateProperty.constant(PixelColorRole.Warning),
        )
        /** Complete custom theme with only the two new component families replaced. */
        val theme = PixelThemeTokens.Default.copy(
            colors = colors,
            components = PixelComponentTokens.Default.copy(
                radio = radioTokens,
                iconButton = iconTokens,
            ),
        )
        /** Off-screen runtime sampling the resolved component pixels. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = theme,
                    child = Radio(
                        selected = false,
                        onSelected = {},
                        semanticLabel = "Radio token",
                    ),
                ),
                logicalWidth = 32,
                logicalHeight = 20,
            )
            assertTrue(tester.hasPixel(radioSentinel))
            assertFalse(tester.hasPixel(iconSentinel))

            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = theme,
                    child = IconButton(
                        icon = opaqueIcon(),
                        onPressed = {},
                        semanticLabel = "Icon token",
                    ),
                ),
                logicalWidth = 32,
                logicalHeight = 32,
            )
            assertTrue(tester.hasPixel(iconSentinel))
            assertFalse(tester.hasPixel(radioSentinel))
        } finally {
            tester.dispose()
        }
    }

    /** Selected Error Radio retains checked geometry while focus remains an additive color layer. */
    @Test
    fun selectedErrorRadioKeepsSelectionAndFocusTogether() {
        /** Error-state surface sentinel. */
        val danger = PixelColor.fromRgb(219, 43, 61)
        /** Independent focus-indicator sentinel. */
        val focus = PixelColor.fromRgb(19, 227, 239)
        /** Theme scheme exposing both persistent Error and runtime Focused channels. */
        val theme = PixelThemeTokens.Default.copy(
            colors = PixelColorScheme.Dark.copy(danger = danger, focus = focus),
        )
        /** Off-screen runtime focusing the selected Error radio. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = theme,
                    child = Radio(
                        selected = true,
                        onSelected = {},
                        semanticLabel = "Error radio",
                        states = PixelControlStateSet.of(PixelControlState.Error),
                        key = "error-radio",
                    ),
                ),
                logicalWidth = 32,
                logicalHeight = 20,
            )
            assertTrue(tester.pressKey(PixelKey.TAB))
            /** Structured node proving selection survives the higher-priority Error paint state. */
            val node = tester.semanticsNodesByLabel("Error radio").single()
            assertTrue(node.focused)
            assertEquals(true, node.checked)
            assertTrue(node.selected)
            assertTrue(tester.hasPixel(danger))
            assertTrue(tester.hasPixel(focus))
        } finally {
            tester.dispose()
        }
    }

    /** Loading retains IconButton focus but removes actions; Disabled then clears traversal focus. */
    @Test
    fun iconButtonLoadingAndDisabledTransitionsRetainThenClearFocus() {
        /** Shared immutable icon mask retained across every controlled state rebuild. */
        val icon = opaqueIcon()
        /** Activation count proving capability states never invoke stale callbacks. */
        var activations = 0
        /** Caller-owned persistent state rebuilt under one stable control key. */
        var states = PixelControlStateSet.Normal
        /** Caller-owned enabled capability rebuilt after Loading. */
        var enabled = true
        /** Off-screen runtime preserving retained focus and semantic identity. */
        val tester = PixelTester()
        try {
            /** Builds the latest controlled IconButton state under a stable identity. */
            fun buildButton() = IconButton(
                icon = icon,
                onPressed = { activations += 1 },
                semanticLabel = "Async save",
                states = states,
                enabled = enabled,
                key = "async-save",
            )

            tester.pumpWidget(buildButton(), logicalWidth = 40, logicalHeight = 40)
            assertTrue(tester.pressKey(PixelKey.TAB))
            /** Stable semantic id expected to survive both capability-state rebuilds. */
            val semanticId = tester.semanticsNodesByLabel("Async save").single().id

            states = PixelControlStateSet.of(PixelControlState.Loading)
            tester.pumpWidget(buildButton(), logicalWidth = 40, logicalHeight = 40)
            /** Loading node remains focused but has no mutation capability. */
            val loading = tester.semanticsNodesByLabel("Async save").single()
            assertEquals(semanticId, loading.id)
            assertTrue(loading.focused)
            assertFalse(loading.enabled)
            assertEquals("LOADING", loading.value)
            assertFalse(PixelSemanticsAction.CLICK in loading.actions)
            assertFalse(tester.pressKey(PixelKey.ENTER))
            assertEquals(0, activations)

            states = PixelControlStateSet.Normal
            enabled = false
            tester.pumpWidget(buildButton(), logicalWidth = 40, logicalHeight = 40)
            /** Disabled node retains identity and selection data but releases focus ownership. */
            val disabled = tester.semanticsNodesByLabel("Async save").single()
            assertEquals(semanticId, disabled.id)
            assertFalse(disabled.focused)
            assertFalse(disabled.enabled)
            assertFalse(PixelSemanticsAction.CLICK in disabled.actions)
        } finally {
            tester.dispose()
        }
    }

    /** Creates an opaque 3x3 alpha-mask icon for theme and capability-state tests. */
    private fun opaqueIcon(): PixelIconData {
        /** Opaque pixels whose RGB is replaced by the active IconButton content token. */
        val pixels = IntArray(9) { PixelColor.White.argb }
        return PixelIconData(PixelBitmap(width = 3, height = 3, pixels = pixels))
    }
}
