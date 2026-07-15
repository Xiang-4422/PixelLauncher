package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Container
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.PixelColorRole
import com.purride.pixelui.PixelColorScheme
import com.purride.pixelui.PixelComponentTokens
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelElevationRole
import com.purride.pixelui.PixelElevationTokens
import com.purride.pixelui.PixelLabelTokens
import com.purride.pixelui.PixelStateProperty
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.Row
import com.purride.pixelui.Scrollbar
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Slider
import com.purride.pixelui.TextField
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Compatibility and live-token contracts for Slider, Scrollbar, and legacy field labels. */
class SliderScrollbarThemeCompatibilityTest {
    /** Scope-less legacy Slider colors stay exact, while explicit themes resolve each sentinel alone. */
    @Test
    fun sliderLegacyAndThemedColorChannelsResolveIndependently() {
        /** Scope-less runtime proving that no implicit default token recolors the old facade. */
        val legacyTester = PixelTester()
        try {
            legacyTester.pumpWidget(
                widget = Slider(value = 0.5f),
                logicalWidth = SliderWidth,
                logicalHeight = LegacySliderHeight,
            )
            assertEquals(LegacyTrackColor, legacyTester.pixelAt(0, 0))
            assertEquals(LegacyActiveColor, legacyTester.pixelAt(1, 2))
            assertEquals(PixelColor.Transparent, legacyTester.pixelAt(SliderWidth - 2, 2))
        } finally {
            legacyTester.dispose()
        }

        /** Explicit-theme runtime checking both default sentinel channels. */
        val themedTester = PixelTester()
        try {
            themedTester.pumpWidget(
                widget = sliderTheme(Slider(value = 0.5f)),
                logicalWidth = SliderWidth,
                logicalHeight = LegacySliderHeight,
            )
            assertEquals(TokenActiveColor, themedTester.pixelAt(1, 2))
            assertEquals(TokenTrackColor, themedTester.pixelAt(SliderWidth - 2, 2))
        } finally {
            themedTester.dispose()
        }

        /** Caller active override paired with only the default track sentinel. */
        val activeOverrideTester = PixelTester()
        try {
            activeOverrideTester.pumpWidget(
                widget = sliderTheme(
                    Slider(value = 0.5f, activeColor = ExplicitActiveColor),
                ),
                logicalWidth = SliderWidth,
                logicalHeight = LegacySliderHeight,
            )
            assertEquals(ExplicitActiveColor, activeOverrideTester.pixelAt(1, 2))
            assertEquals(TokenTrackColor, activeOverrideTester.pixelAt(SliderWidth - 2, 2))
        } finally {
            activeOverrideTester.dispose()
        }

        /** Caller track override paired with only the default active sentinel. */
        val trackOverrideTester = PixelTester()
        try {
            trackOverrideTester.pumpWidget(
                widget = sliderTheme(
                    Slider(value = 0.5f, trackColor = ExplicitTrackColor),
                ),
                logicalWidth = SliderWidth,
                logicalHeight = LegacySliderHeight,
            )
            assertEquals(TokenActiveColor, trackOverrideTester.pixelAt(1, 2))
            assertEquals(ExplicitTrackColor, trackOverrideTester.pixelAt(SliderWidth - 2, 2))
        } finally {
            trackOverrideTester.dispose()
        }
    }

    /** Every declared Slider geometry and decoration token changes measured or painted output. */
    @Test
    fun sliderConsumesMinimumWidthPaddingBorderRadiusAndElevationTokens() {
        /** Token family whose geometry and decoration channels are all independently observable. */
        val sliderTokens = PixelComponentTokens.Default.slider.copy(
            containerColor = PixelStateProperty.constant(PixelColorRole.Track),
            contentColor = PixelStateProperty.constant(PixelColorRole.Primary),
            borderColor = PixelStateProperty.constant(PixelColorRole.Outline),
            focusIndicator = null,
            padding = EdgeInsets(left = 2, top = 1, right = 1, bottom = 2),
            minimumWidth = 11,
            minimumHeight = 9,
            borderWidth = 1,
            cornerRadius = 2,
            elevationRole = PixelElevationRole.Low,
        )
        /** Theme with a two-pixel low elevation and distinct paint colors. */
        val tokens = SliderThemeTokens.copy(
            elevations = PixelElevationTokens.Default.copy(low = 2),
            components = PixelComponentTokens.Default.copy(slider = sliderTokens),
        )
        /** Runtime using a shrink-wrapped Row so the Slider minimum width is measurable. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = tokens,
                    child = Row(
                        children = listOf(
                            Slider(
                                value = 1f,
                                states = PixelControlStateSet.Normal,
                                onDrag = {},
                                onRelease = {},
                                key = "token-slider",
                            ),
                        ),
                    ),
                ),
                logicalWidth = 24,
                logicalHeight = 16,
            )
            /** Drag target excludes the visual-only shadow but includes token minimum geometry. */
            val bounds = tester.renderResult!!.sliderTargets.single().bounds
            assertEquals(11, bounds.width)
            assertEquals(9, bounds.height)
            // Radius removes the outer corner; outline begins on the first stair-step pixel.
            assertEquals(PixelColor.Transparent, tester.pixelAt(0, 0))
            assertEquals(TokenBorderColor, tester.pixelAt(1, 0))
            // Left padding plus outline keeps active paint away from the component edge.
            assertEquals(TokenTrackColor, tester.pixelAt(2, 4))
            assertEquals(TokenActiveColor, tester.pixelAt(3, 4))
            // The two-pixel hard elevation remains visible beyond the eleven-pixel main surface.
            assertEquals(TokenShadowColor, tester.pixelAt(12, 4))
        } finally {
            tester.dispose()
        }
    }

    /** A null old Scrollbar track remains absent scope-less and becomes token-backed in a theme. */
    @Test
    fun scrollbarNullTrackSeparatesLegacyAndThemedPaths() {
        /** Legacy scroll owner used to derive proportional thumb geometry. */
        val legacyController = PixelListController()
        /** Legacy list state paired with the old facade. */
        val legacyState = legacyController.create()
        /** Runtime proving that child pixels remain visible beneath the omitted track. */
        val legacyTester = PixelTester()
        try {
            legacyTester.pumpWidget(
                widget = legacyScrollbar(legacyState, legacyController),
                logicalWidth = ScrollbarViewportWidth,
                logicalHeight = ScrollbarViewportHeight,
            )
            assertEquals(LegacyTrackColor, legacyTester.pixelAt(ScrollbarViewportWidth - 1, 0))
            assertEquals(ScrollContentColor, legacyTester.pixelAt(ScrollbarViewportWidth - 1, 6))
            assertEquals(1, legacyTester.renderResult!!.scrollbarTargets.single().bounds.width)
        } finally {
            legacyTester.dispose()
        }

        /** Themed scroll owner used to verify old-facade omission sentinels. */
        val themedController = PixelListController()
        /** Themed list state paired with the old facade. */
        val themedState = themedController.create()
        /** Runtime proving explicit theme roles replace all omitted historical defaults. */
        val themedTester = PixelTester()
        try {
            themedTester.pumpWidget(
                widget = scrollbarTheme(legacyScrollbar(themedState, themedController)),
                logicalWidth = ScrollbarViewportWidth,
                logicalHeight = ScrollbarViewportHeight,
            )
            assertEquals(TokenScrollbarThumbColor, themedTester.pixelAt(ScrollbarViewportWidth - 1, 0))
            assertEquals(TokenScrollbarTrackColor, themedTester.pixelAt(ScrollbarViewportWidth - 1, 6))
            assertEquals(2, themedTester.renderResult!!.scrollbarTargets.single().bounds.width)
        } finally {
            themedTester.dispose()
        }

        /** State-aware scroll owner proving its null track still resolves normal component tokens. */
        val stateAwareController = PixelListController()
        /** State-aware list state paired with the required-states overload. */
        val stateAwareState = stateAwareController.create()
        /** Runtime guarding the themed API against compatibility-path regression. */
        val stateAwareTester = PixelTester()
        try {
            stateAwareTester.pumpWidget(
                widget = scrollbarTheme(
                    Scrollbar(
                        child = scrollViewport(stateAwareState, stateAwareController),
                        state = stateAwareState,
                        states = PixelControlStateSet.Normal,
                    ),
                ),
                logicalWidth = ScrollbarViewportWidth,
                logicalHeight = ScrollbarViewportHeight,
            )
            assertEquals(TokenScrollbarTrackColor, stateAwareTester.pixelAt(ScrollbarViewportWidth - 1, 6))
        } finally {
            stateAwareTester.dispose()
        }
    }

    /** The old TextField default keeps a non-blank placeholder as its spoken label. */
    @Test
    fun textFieldPlaceholderDefaultRemainsSpokenLabel() {
        /** Controlled field owner used by the public legacy facade. */
        val controller = PixelTextFieldController()
        /** Empty controlled value allowing the placeholder to remain visible. */
        val state = controller.create()
        /** Runtime with a conflicting theme label exposing accidental placeholder omission. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = PixelThemeTokens.Default.copy(
                        labels = PixelLabelTokens.Default.copy(textField = "TOKEN FIELD"),
                    ),
                    child = TextField(
                        state = state,
                        controller = controller,
                        placeholder = "Email",
                    ),
                ),
                logicalWidth = 32,
                logicalHeight = 12,
            )
            assertEquals(1, tester.semanticsNodesByLabel("Email").size)
            assertTrue(tester.semanticsNodesByLabel("TOKEN FIELD").isEmpty())
        } finally {
            tester.dispose()
        }
    }

    /** Wraps [child] in the Slider color-token theme used by sentinel tests. */
    private fun sliderTheme(child: Widget): Widget = PixelTheme(tokens = SliderThemeTokens, child = child)

    /** Wraps [child] in the Scrollbar role and width-token theme. */
    private fun scrollbarTheme(child: Widget): Widget {
        /** Scrollbar tokens with an observable two-pixel width and both paint roles. */
        val scrollbarTokens = PixelComponentTokens.Default.scrollbar.copy(minimumWidth = 2)
        /** Complete token graph installed around the public old or state-aware facade. */
        val tokens = PixelThemeTokens.Default.copy(
            colors = PixelColorScheme.Dark.copy(
                onSurface = TokenScrollbarThumbColor,
                track = TokenScrollbarTrackColor,
            ),
            components = PixelComponentTokens.Default.copy(scrollbar = scrollbarTokens),
        )
        return PixelTheme(tokens = tokens, child = child)
    }

    /** Builds the old Scrollbar facade with its null track and one-pixel width defaults. */
    private fun legacyScrollbar(state: PixelListState, controller: PixelListController): Widget {
        return Scrollbar(
            child = scrollViewport(state, controller),
            state = state,
        )
    }

    /** Builds overflowing solid content so an absent track can be distinguished from paint. */
    private fun scrollViewport(state: PixelListState, controller: PixelListController): Widget {
        return ListViewBuilder(
            itemCount = 20,
            itemBuilder = {
                SizedBox(
                    height = 6,
                    child = Container(
                        width = ScrollbarViewportWidth,
                        height = 6,
                        fillColor = ScrollContentColor,
                    ),
                )
            },
            itemExtent = 6,
            state = state,
            controller = controller,
        )
    }

    private companion object {
        /** Shared Slider render width used by exact pixel assertions. */
        const val SliderWidth: Int = 12

        /** Exact legacy Slider height. */
        const val LegacySliderHeight: Int = 7

        /** Scrollable viewport width. */
        const val ScrollbarViewportWidth: Int = 12

        /** Scrollable viewport height. */
        const val ScrollbarViewportHeight: Int = 12

        /** Historical old-facade Slider active color. */
        val LegacyActiveColor: PixelColor = PixelColor.fromRgb(200, 100, 0)

        /** Historical white Slider frame and Scrollbar thumb. */
        val LegacyTrackColor: PixelColor = PixelColor.White

        /** Theme-resolved Slider active color. */
        val TokenActiveColor: PixelColor = PixelColor.fromRgb(19, 181, 103)

        /** Theme-resolved Slider track color. */
        val TokenTrackColor: PixelColor = PixelColor.fromRgb(23, 41, 73)

        /** Explicit old-facade active override. */
        val ExplicitActiveColor: PixelColor = PixelColor.fromRgb(211, 61, 83)

        /** Explicit old-facade track override. */
        val ExplicitTrackColor: PixelColor = PixelColor.fromRgb(73, 113, 229)

        /** Theme-resolved Slider border color. */
        val TokenBorderColor: PixelColor = PixelColor.fromRgb(239, 199, 47)

        /** Theme-resolved Slider hard-shadow color. */
        val TokenShadowColor: PixelColor = PixelColor.fromRgb(103, 43, 137)

        /** Solid child color visible when the legacy Scrollbar track is omitted. */
        val ScrollContentColor: PixelColor = PixelColor.fromRgb(31, 79, 127)

        /** Theme-resolved Scrollbar thumb color. */
        val TokenScrollbarThumbColor: PixelColor = PixelColor.fromRgb(241, 87, 47)

        /** Theme-resolved Scrollbar track color. */
        val TokenScrollbarTrackColor: PixelColor = PixelColor.fromRgb(47, 199, 157)

        /** Shared explicit Slider theme with exact paint sentinels. */
        val SliderThemeTokens: PixelThemeTokens = PixelThemeTokens.Default.copy(
            colors = PixelColorScheme.Dark.copy(
                primary = TokenActiveColor,
                track = TokenTrackColor,
                outline = TokenBorderColor,
                shadow = TokenShadowColor,
            ),
        )
    }
}
