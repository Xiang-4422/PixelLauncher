package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelComponentTokens
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelLabelTokens
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Stack
import com.purride.pixelui.Container
import com.purride.pixelui.ValueAdjuster
import com.purride.pixelui.ValueAdjusterStyle
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Focused regression coverage for ValueAdjuster and determinate ProgressBar production contracts. */
class ValueAdjusterProgressContractTest {
    /** The old adjuster facade preserves scope-less pixels but consumes an explicit token provider. */
    @Test
    fun valueAdjusterLegacyFacadeSwitchesOnlyAtAnExplicitThemeBoundary() {
        /** Reused off-screen runtime for both compatibility and explicit-theme branches. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                ValueAdjuster(
                    valueText = "5",
                    onDecrease = {},
                    onIncrease = {},
                    key = "legacy-adjuster",
                ),
                logicalWidth = 96,
                logicalHeight = 32,
            )
            /** Historical action targets proving the old 9px action size plus one-pixel outline. */
            val legacyTargets = requireNotNull(tester.renderResult).clickTargets.sortedBy { target ->
                target.bounds.left
            }
            assertEquals(2, legacyTargets.size)
            assertEquals(11, legacyTargets[0].bounds.width)
            assertEquals(11, legacyTargets[1].bounds.width)
            /** Historical group geometry must remain pixel-for-pixel stable without a provider. */
            val legacyGroup = tester.semanticsNodesByLabel("ValueAdjuster").single()
            assertEquals(50, legacyGroup.width)
            assertEquals(13, legacyGroup.height)

            /** Disabled action fill sentinel resolved from the explicit theme. */
            val disabledFill = PixelColor.fromRgb(173, 43, 67)
            /** Disabled glyph sentinel proving the old hard-coded black fallback is bypassed. */
            val disabledGlyph = PixelColor.fromRgb(211, 223, 79)
            /** Explicit token graph with independently observable labels, geometry, and colors. */
            val themedTokens = PixelThemeTokens.Default.copy(
                colors = PixelThemeTokens.Default.colors.copy(
                    disabled = disabledFill,
                    onDisabled = disabledGlyph,
                ),
                labels = PixelLabelTokens.Default.copy(
                    valueAdjuster = "TOKEN ADJUSTER",
                    decrease = "TOKEN DECREASE",
                    increase = "TOKEN INCREASE",
                ),
                components = PixelComponentTokens.Default.copy(
                    valueAdjuster = PixelComponentTokens.Default.valueAdjuster.copy(
                        minimumWidth = 17,
                        minimumHeight = 15,
                    ),
                ),
            )
            tester.pumpWidget(
                PixelTheme(
                    tokens = themedTokens,
                    child = ValueAdjuster(
                        valueText = "5",
                        onDecrease = null,
                        onIncrease = {},
                        key = "themed-legacy-adjuster",
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 32,
            )
            assertTrue(tester.hasPixel(disabledFill))
            assertTrue(tester.hasPixel(disabledGlyph))
            assertTrue(tester.semanticsNodesByLabel("TOKEN ADJUSTER").isNotEmpty())
            /** Token minimum width plus both one-pixel outline edges defines each action cell. */
            val themedIncrease = tester.semanticsNodesByLabel("TOKEN INCREASE").single()
            assertEquals(19, themedIncrease.width)
            assertEquals(15, themedIncrease.height)
        } finally {
            tester.dispose()
        }
    }

    /** Every style channel paints independently and the focused tree exports one focused node. */
    @Test
    fun valueAdjusterStyleChannelsAndGroupFocusRemainIndependent() {
        /** Outline sentinel for the base component border and dividers. */
        val border = PixelColor.fromRgb(19, 47, 83)
        /** Enabled action-cell fill sentinel. */
        val fill = PixelColor.fromRgb(101, 29, 59)
        /** Enabled plus/minus glyph sentinel. */
        val symbol = PixelColor.fromRgb(227, 191, 31)
        /** Controlled value text sentinel. */
        val value = PixelColor.fromRgb(37, 211, 173)
        /** Disabled action fill and glyph sentinel. */
        val disabled = PixelColor.fromRgb(127, 131, 137)
        /** Additive focus outline sentinel. */
        val focus = PixelColor.fromRgb(251, 71, 181)
        /** Complete explicit style whose six channels are all observable. */
        val style = ValueAdjusterStyle(
            borderColor = border,
            buttonFillColor = fill,
            buttonSymbolColor = symbol,
            valueTextColor = value,
            disabledColor = disabled,
            focusColor = focus,
        )
        /** Runtime owning the automatic focus node and rendered pixel buffer. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                PixelTheme(
                    tokens = PixelThemeTokens.Default,
                    child = ValueAdjuster(
                        valueText = "7",
                        onDecrease = null,
                        onIncrease = {},
                        style = style,
                        key = "styled-adjuster",
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 32,
            )
            listOf(border, fill, symbol, value, disabled).forEach { channel ->
                assertTrue("ValueAdjuster missed style channel $channel", tester.hasPixel(channel))
            }

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.hasPixel(focus))
            /** Entire accessibility tree must expose the real group focus exactly once. */
            val focusedNodes = tester.semanticsNodes().filter { node -> node.focused }
            assertEquals(1, focusedNodes.size)
            assertEquals("ValueAdjuster", focusedNodes.single().label)
            assertTrue(tester.semanticsNodesByLabel("Decrease").none { node -> node.focused })
            assertTrue(tester.semanticsNodesByLabel("Increase").none { node -> node.focused })
        } finally {
            tester.dispose()
        }
    }

    /** Narrow constraints clip paint, pointer targets, and semantics to one shared 21x7 viewport. */
    @Test
    fun valueAdjusterNarrowViewportKeepsEveryOutputChannelInsideBounds() {
        /** Guard background used to detect paint escaping the constrained adjuster viewport. */
        val guard = PixelColor.fromRgb(7, 13, 23)
        /** Number of successful decrement callbacks. */
        var decreases = 0
        /** Number of successful increment callbacks. */
        var increases = 0
        /** Runtime exposing rendered paint, click targets, and the complete semantics tree. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Stack(
                    children = listOf(
                        Container(width = 36, height = 16, fillColor = guard),
                        SizedBox(
                            width = 21,
                            height = 7,
                            child = ValueAdjuster(
                                valueText = "123456",
                                onDecrease = { decreases += 1 },
                                onIncrease = { increases += 1 },
                                key = "narrow-adjuster",
                            ),
                        ),
                    ),
                ),
                logicalWidth = 36,
                logicalHeight = 16,
            )
            /** Both constrained actions remain reachable after symmetric cell reflow. */
            val clickTargets = requireNotNull(tester.renderResult).clickTargets.sortedBy { target ->
                target.bounds.left
            }
            assertEquals(2, clickTargets.size)
            clickTargets.forEach { target ->
                assertTrue(target.bounds.left >= 0)
                assertTrue(target.bounds.top >= 0)
                assertTrue(target.bounds.right <= 21)
                assertTrue(target.bounds.bottom <= 7)
                target.onClick()
            }
            assertEquals(1, decreases)
            assertEquals(1, increases)

            tester.semanticsNodes().forEach { node ->
                assertTrue("semantic left escaped: $node", node.left >= 0)
                assertTrue("semantic top escaped: $node", node.top >= 0)
                assertTrue("semantic right escaped: $node", node.left + node.width <= 21)
                assertTrue("semantic bottom escaped: $node", node.top + node.height <= 7)
            }
            /** Pixels right of the viewport prove horizontal paint cannot leak through SizedBox. */
            val buffer = requireNotNull(tester.renderResult).buffer
            for (y in 0 until 16) {
                for (x in 21 until 36) {
                    assertEquals("paint escaped at ($x,$y)", guard, buffer.getPixel(x, y))
                }
            }
            /** Pixels below the viewport prove short-height glyphs and borders remain clipped. */
            for (y in 7 until 16) {
                for (x in 0 until 21) {
                    assertEquals("paint escaped at ($x,$y)", guard, buffer.getPixel(x, y))
                }
            }
        } finally {
            tester.dispose()
        }
    }

    /** Determinate progress safely normalizes non-finite input and malformed geometry. */
    @Test
    fun progressBarNormalizesNonFiniteValuesAndRejectsNegativeGeometryLeaks() {
        /** Runtime reused across every malformed progress input. */
        val tester = PixelTester()
        try {
            /** Input-to-semantic cases covering NaN and both infinity endpoints. */
            val cases = listOf(
                Float.NaN to "0%",
                Float.NEGATIVE_INFINITY to "0%",
                Float.POSITIVE_INFINITY to "100%",
            )
            cases.forEach { (input, expectedValue) ->
                tester.pumpWidget(
                    ProgressBar(
                        progress = input,
                        states = PixelControlStateSet.Normal,
                        width = 10,
                        height = 5,
                    ),
                    logicalWidth = 24,
                    logicalHeight = 16,
                )
                /** Progress semantics proving no non-finite value reaches percentage formatting. */
                val node = tester.semanticsNodesByLabel("Progress").single()
                assertEquals(expectedValue, node.value)
                assertTrue(node.width >= 0)
                assertTrue(node.height >= 0)
            }

            tester.pumpWidget(
                ProgressBar(
                    progress = 0.5f,
                    states = PixelControlStateSet.Normal,
                    width = -20,
                    height = -4,
                ),
                logicalWidth = 8,
                logicalHeight = 8,
            )
            /** Negative dimensions collapse safely before the component minimum is applied. */
            val malformedNode = tester.semanticsNodesByLabel("Progress").single()
            assertEquals(0, malformedNode.width)
            assertTrue(malformedNode.height >= 0)

            /** Scope-less old facade must also accept malformed dimensions without throwing. */
            tester.pumpWidget(
                ProgressBar(progress = 0.5f, width = -20, height = -4),
                logicalWidth = 8,
                logicalHeight = 8,
            )
            assertFalse(tester.hasPixel(PixelColor.fromRgb(80, 180, 110)))
        } finally {
            tester.dispose()
        }
    }

    /** Progress minimum-width tokens are observable while scope-less legacy pixels stay unchanged. */
    @Test
    fun progressBarConsumesMinimumWidthWithoutChangingScopeLessLegacyPixels() {
        /** Historical active fill sentinel. */
        val active = PixelColor.fromRgb(23, 149, 83)
        /** Historical track sentinel. */
        val track = PixelColor.fromRgb(41, 43, 47)
        /** Runtime reused for exact legacy pixels and the explicit-theme branch. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                ProgressBar(
                    progress = 0.5f,
                    width = 10,
                    height = 5,
                    color = active,
                    trackColor = track,
                ),
                logicalWidth = 16,
                logicalHeight = 8,
            )
            /** Historical stack paint remains unchanged when no PixelTheme provider exists. */
            val legacyBuffer = requireNotNull(tester.renderResult).buffer
            assertEquals(active, legacyBuffer.getPixel(0, 0))
            assertEquals(active, legacyBuffer.getPixel(4, 4))
            assertEquals(PixelColor.White, legacyBuffer.getPixel(5, 0))
            assertEquals(track, legacyBuffer.getPixel(6, 2))
            assertTrue(tester.semanticsNodesByLabel("Progress").isEmpty())

            /** Explicit progress token with a minimum wider than both caller and foundation defaults. */
            val progressTokens = PixelThemeTokens.Default.copy(
                labels = PixelLabelTokens.Default.copy(progress = "TOKEN PROGRESS"),
                components = PixelComponentTokens.Default.copy(
                    progress = PixelComponentTokens.Default.progress.copy(minimumWidth = 63),
                ),
            )
            tester.pumpWidget(
                PixelTheme(
                    tokens = progressTokens,
                    child = ProgressBar(progress = 0.5f, width = 10, height = 5),
                ),
                logicalWidth = 80,
                logicalHeight = 16,
            )
            /** Old facade inside an explicit provider delegates to the token-aware implementation. */
            val themedNode = tester.semanticsNodesByLabel("TOKEN PROGRESS").single()
            assertEquals(63, themedNode.width)
            assertEquals(7, themedNode.height)
        } finally {
            tester.dispose()
        }
    }
}
