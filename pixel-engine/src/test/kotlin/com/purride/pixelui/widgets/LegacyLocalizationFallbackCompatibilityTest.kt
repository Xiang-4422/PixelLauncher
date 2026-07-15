package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Dialog
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelButtonStyle
import com.purride.pixelui.PixelLabelTokens
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelTextButtonStyle
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.SizedBox
import com.purride.pixelui.TextButton
import com.purride.pixelui.Tooltip
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runtime compatibility contract when no localization provider is installed.
 *
 * These tests intentionally use only historical facades, [PixelTheme], and [PixelTester]. They
 * freeze the pre-localization fallback boundary without coupling to the new localization API.
 */
class LegacyLocalizationFallbackCompatibilityTest {
    /** Scope-less legacy facades retain historical pixels and English semantic defaults. */
    @Test
    fun scopeLessLegacyFacadesRetainHistoricalPixelsAndSemanticFallbacks() {
        /** Unique outlined-button surface color proving its historical visual branch painted. */
        val buttonFill = PixelColor.fromRgb(31, 73, 127)
        /** Unique outlined-button border color proving explicit legacy style precedence. */
        val buttonBorder = PixelColor.fromRgb(211, 59, 83)
        /** Unique outlined-button glyph color proving legacy typography remains active. */
        val buttonText = PixelColor.fromRgb(239, 193, 47)
        /** Unique TextButton glyph color proving its scope-less natural-size branch painted. */
        val textButtonText = PixelColor.fromRgb(53, 227, 173)
        /** Historical progress foreground sentinel. */
        val progressFill = PixelColor.fromRgb(23, 149, 83)
        /** Historical progress track sentinel. */
        val progressTrack = PixelColor.fromRgb(41, 43, 47)
        /** Unique Dialog surface sentinel proving the legacy overlay path remained mounted. */
        val dialogFill = PixelColor.fromRgb(17, 29, 71)
        /** Unique Dialog outline sentinel proving legacy overlay styling remained active. */
        val dialogBorder = PixelColor.fromRgb(197, 181, 37)
        /** Reused off-screen runtime prevents platform fonts or Android accessibility from intervening. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = OutlinedButton(
                    text = "LEGACY BUTTON",
                    onPressed = {},
                    style = PixelButtonStyle(
                        fillColor = buttonFill,
                        borderColor = buttonBorder,
                        textStyle = PixelTextStyle(color = buttonText),
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 20,
            )
            /** Caller text remains both the visible content and the spoken legacy label. */
            val outlinedNode = tester.semanticsNodesByLabel("LEGACY BUTTON").single()
            assertEquals(PixelSemanticRole.BUTTON, outlinedNode.role)
            assertTrue(outlinedNode.enabled)
            assertTrue(tester.hasPixel(buttonFill))
            assertTrue(tester.hasPixel(buttonBorder))
            assertTrue(tester.hasPixel(buttonText))

            tester.pumpWidget(
                widget = TextButton(
                    text = "LEGACY TEXT BUTTON",
                    onPressed = {},
                    style = PixelTextButtonStyle(
                        textStyle = PixelTextStyle(color = textButtonText),
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 16,
            )
            /** TextButton keeps caller text instead of consulting a hidden default provider. */
            val textButtonNode = tester.semanticsNodesByLabel("LEGACY TEXT BUTTON").single()
            assertEquals(PixelSemanticRole.BUTTON, textButtonNode.role)
            assertTrue(tester.hasPixel(textButtonText))

            tester.pumpWidget(
                widget = ProgressBar(
                    progress = 0.5f,
                    width = 10,
                    height = 5,
                    color = progressFill,
                    trackColor = progressTrack,
                ),
                logicalWidth = 16,
                logicalHeight = 8,
            )
            /** Scope-less legacy ProgressBar remains paint-only and exports no token-era semantics. */
            assertTrue(tester.semanticsNodesByLabel("Progress").isEmpty())
            assertEquals(progressFill, tester.pixelAt(0, 0))
            assertEquals(progressFill, tester.pixelAt(4, 4))
            assertEquals(PixelColor.White, tester.pixelAt(5, 0))
            assertEquals(progressTrack, tester.pixelAt(6, 2))

            tester.pumpWidget(
                widget = Dialog(
                    content = SizedBox(width = 4, height = 3),
                    fillColor = dialogFill,
                    borderColor = dialogBorder,
                    modal = false,
                ),
                logicalWidth = 40,
                logicalHeight = 28,
            )
            /** Omitted Dialog semantics retain the published English legacy default. */
            val dialogNode = tester.semanticsNodesByLabel("Dialog").single()
            assertEquals(PixelSemanticRole.DIALOG, dialogNode.role)
            assertTrue(tester.hasPixel(dialogFill))
            assertTrue(tester.hasPixel(dialogBorder))

            tester.pumpWidget(
                widget = Tooltip(
                    message = "",
                    visible = true,
                    child = SizedBox(width = 4, height = 3),
                ),
                logicalWidth = 40,
                logicalHeight = 28,
            )
            /** Empty legacy Tooltip messages use the documented English accessibility fallback. */
            val tooltipNode = tester.semanticsNodesByLabel("Tooltip").single()
            assertEquals(PixelSemanticRole.GENERIC, tooltipNode.role)
        } finally {
            tester.dispose()
        }
    }

    /** Explicit theme label tokens remain the standard-component source without another provider. */
    @Test
    fun explicitThemeLabelsStillDriveStandardComponentsWithoutLocalizationProvider() {
        /** Sentinel labels cover both text-bearing controls and passive overlay/progress semantics. */
        val labels = PixelLabelTokens.Default.copy(
            button = "THEME BUTTON",
            textButton = "THEME TEXT BUTTON",
            dialog = "THEME DIALOG",
            tooltip = "THEME TOOLTIP",
            progress = "THEME PROGRESS",
        )
        /** Complete token graph installed directly, with no localization provider ancestor. */
        val tokens = PixelThemeTokens.Default.copy(labels = labels)
        /** Reused runtime exercises each historical facade at an explicit theme boundary. */
        val tester = PixelTester()
        try {
            assertSingleThemedSemanticLabel(
                tester = tester,
                tokens = tokens,
                expectedLabel = labels.button,
                legacyFallbackLabel = "Button",
                child = OutlinedButton(text = "", onPressed = {}),
            )
            assertSingleThemedSemanticLabel(
                tester = tester,
                tokens = tokens,
                expectedLabel = labels.textButton,
                legacyFallbackLabel = "Text button",
                child = TextButton(text = "", onPressed = {}),
            )
            assertSingleThemedSemanticLabel(
                tester = tester,
                tokens = tokens,
                expectedLabel = labels.progress,
                legacyFallbackLabel = "Progress",
                child = ProgressBar(progress = 0.5f),
            )
            assertSingleThemedSemanticLabel(
                tester = tester,
                tokens = tokens,
                expectedLabel = labels.dialog,
                legacyFallbackLabel = "Dialog",
                child = Dialog(
                    content = SizedBox(width = 4, height = 3),
                    modal = false,
                ),
            )
            assertSingleThemedSemanticLabel(
                tester = tester,
                tokens = tokens,
                expectedLabel = labels.tooltip,
                legacyFallbackLabel = "Tooltip",
                child = Tooltip(
                    message = "",
                    visible = true,
                    child = SizedBox(width = 4, height = 3),
                ),
            )

        } finally {
            tester.dispose()
        }
    }

    /** Pumps one real themed component and requires exactly one matching semantic node. */
    private fun assertSingleThemedSemanticLabel(
        tester: PixelTester,
        tokens: PixelThemeTokens,
        expectedLabel: String,
        legacyFallbackLabel: String,
        child: Widget,
    ) {
        tester.pumpWidget(
            widget = PixelTheme(tokens = tokens, child = child),
            logicalWidth = 96,
            logicalHeight = 48,
        )
        assertEquals(1, tester.semanticsNodesByLabel(expectedLabel).size)
        assertFalse(
            "Unexpected legacy fallback '$legacyFallbackLabel'",
            tester.semanticsNodesByLabel(legacyFallbackLabel).isNotEmpty(),
        )
    }
}
