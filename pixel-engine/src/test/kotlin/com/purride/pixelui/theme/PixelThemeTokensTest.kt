package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Locks theme presets, role propagation, validation, and legacy visual compatibility. */
class PixelThemeTokensTest {
    /** Default remains the original dark palette and projects to the exact legacy style graph. */
    @Test
    fun defaultPresetPreservesLegacyDarkVisuals() {
        assertSame(PixelThemeTokens.Dark, PixelThemeTokens.Default)
        assertSame(PixelColorScheme.Dark, PixelThemeTokens.Default.colors)
        assertEquals(PixelThemeBrightness.Dark, PixelThemeTokens.Default.brightness)
        assertEquals(PixelThemeContrast.Standard, PixelThemeTokens.Default.contrast)
        assertEquals(PixelThemeData.Default, PixelThemeTokens.Default.toLegacyThemeData())
    }

    /** Four presets expose correct brightness and contrast metadata with distinct schemes. */
    @Test
    fun presetsDeclareTheirBrightnessAndContrastModes() {
        assertEquals(PixelThemeBrightness.Light, PixelThemeTokens.Light.brightness)
        assertEquals(PixelThemeContrast.Standard, PixelThemeTokens.Light.contrast)
        assertEquals(PixelThemeBrightness.Dark, PixelThemeTokens.Dark.brightness)
        assertEquals(PixelThemeContrast.Standard, PixelThemeTokens.Dark.contrast)
        assertEquals(PixelThemeBrightness.Light, PixelThemeTokens.HighContrastLight.brightness)
        assertEquals(PixelThemeContrast.High, PixelThemeTokens.HighContrastLight.contrast)
        assertEquals(PixelThemeBrightness.Dark, PixelThemeTokens.HighContrastDark.brightness)
        assertEquals(PixelThemeContrast.High, PixelThemeTokens.HighContrastDark.contrast)
        assertNotEquals(PixelThemeTokens.Light.colors, PixelThemeTokens.Dark.colors)
        assertNotEquals(
            PixelThemeTokens.HighContrastLight.colors,
            PixelThemeTokens.HighContrastDark.colors,
        )
    }

    /** High-contrast text exceeds 4.5:1 and focus exceeds 3:1 against primary backgrounds. */
    @Test
    fun highContrastPresetsMeetTextAndFocusThresholds() {
        listOf(
            PixelThemeTokens.HighContrastLight,
            PixelThemeTokens.HighContrastDark,
        ).forEach { theme ->
            /** High-contrast concrete scheme under test. */
            val colors = theme.colors
            assertEquals(2, theme.borders.focus)
            assertTrue(
                "onBackground contrast for ${theme.brightness}",
                contrastRatio(colors.onBackground, colors.background) >= 4.5,
            )
            assertTrue(
                "onSurface contrast for ${theme.brightness}",
                contrastRatio(colors.onSurface, colors.surface) >= 4.5,
            )
            assertTrue(
                "focus/background contrast for ${theme.brightness}",
                contrastRatio(colors.focus, colors.background) >= 3.0,
            )
            assertTrue(
                "focus/surface contrast for ${theme.brightness}",
                contrastRatio(colors.focus, colors.surface) >= 3.0,
            )
            /** Status surfaces and their content pairs covered by component state roles. */
            val statusPairs = listOf(
                "primary" to (colors.primary to colors.onPrimary),
                "danger" to (colors.danger to colors.onDanger),
                "warning" to (colors.warning to colors.onWarning),
                "disabled" to (colors.disabled to colors.onDisabled),
            )
            statusPairs.forEach { (name, pair) ->
                assertTrue(
                    "$name content contrast for ${theme.brightness}",
                    contrastRatio(pair.first, pair.second) >= 4.5,
                )
            }
            /** Focus and semantic status colors must remain pairwise distinguishable. */
            val stateColors = listOf(
                colors.focus,
                colors.primary,
                colors.danger,
                colors.warning,
                colors.disabled,
            )
            assertEquals(stateColors.size, stateColors.distinct().size)
        }
    }

    /** Component tokens retain roles, so a scheme copy changes resolved output without token copy. */
    @Test
    fun copiedColorSchemePropagatesThroughUnchangedComponentTokens() {
        /** Base graph whose component token identity is retained. */
        val base = PixelThemeTokens.Dark
        /** Replacement accent used to prove late role resolution. */
        val replacementPrimary = PixelColor.fromRgb(201, 17, 233)
        /** Theme copy changing only the concrete semantic scheme. */
        val customized = base.copy(
            colors = base.colors.copy(primary = replacementPrimary),
        )

        assertSame(base.components, customized.components)
        assertEquals(
            replacementPrimary,
            customized.components.slider.resolveContentColor(
                PixelControlStateSet.Normal,
                customized.colors,
            ),
        )
        assertEquals(PixelColorRole.Primary, customized.components.slider.contentColor.resolve(
            PixelControlStateSet.Normal,
        ))
    }

    /** Loading differs from Normal and Hovered for every standard interactive token family. */
    @Test
    fun interactiveTokensGiveLoadingAnExplicitDistinctVisual() {
        /** Named interactive component token families covered by the state contract. */
        val interactiveTokens = mapOf(
            "button" to PixelComponentTokens.Default.button,
            "textButton" to PixelComponentTokens.Default.textButton,
            "textField" to PixelComponentTokens.Default.textField,
            "listTile" to PixelComponentTokens.Default.listTile,
            "checkbox" to PixelComponentTokens.Default.checkbox,
            "switch" to PixelComponentTokens.Default.switch,
            "slider" to PixelComponentTokens.Default.slider,
            "tabs" to PixelComponentTokens.Default.tabs,
            "segmented" to PixelComponentTokens.Default.segmented,
            "valueAdjuster" to PixelComponentTokens.Default.valueAdjuster,
            "menu" to PixelComponentTokens.Default.menu,
            "dropdown" to PixelComponentTokens.Default.dropdown,
            "slidable" to PixelComponentTokens.Default.slidable,
            "scrollbar" to PixelComponentTokens.Default.scrollbar,
        )
        /** Canonical states compared for every interactive family. */
        val normal = PixelControlStateSet.Normal
        val hovered = PixelControlStateSet.of(PixelControlState.Hovered)
        val loading = PixelControlStateSet.of(PixelControlState.Loading)

        interactiveTokens.forEach { (name, token) ->
            /** Base role triple for a state, intentionally unresolved to prove role-level coverage. */
            fun roles(states: PixelControlStateSet): Triple<PixelColorRole?, PixelColorRole?, PixelColorRole?> {
                return Triple(
                    token.containerColor.resolve(states),
                    token.contentColor.resolve(states),
                    token.borderColor.resolve(states),
                )
            }
            assertNotEquals("$name Loading must differ from Normal", roles(normal), roles(loading))
            assertNotEquals("$name Loading must differ from Hovered", roles(hovered), roles(loading))
        }
    }

    /** Component elevation roles resolve through the aggregate elevation scale. */
    @Test
    fun componentElevationUsesAggregateElevationTokens() {
        /** Custom scale proving that the component does not store a copied offset. */
        val elevations = PixelElevationTokens(none = 0, low = 3, medium = 7, high = 11)

        assertEquals(11, PixelComponentTokens.Default.dialog.resolveElevation(elevations))
        assertEquals(7, PixelComponentTokens.Default.tooltip.resolveElevation(elevations))
        assertEquals(3, PixelComponentTokens.Default.toast.resolveElevation(elevations))
        assertEquals(0, PixelComponentTokens.Default.button.resolveElevation(elevations))
    }

    /** Standard geometry encodings resolve through shared spacing, border, and radius scales. */
    @Test
    fun componentGeometryUsesAggregateFoundationScales() {
        /** Non-default scale values that cannot be mistaken for built-in literal geometry. */
        val spacing = PixelSpacingTokens(
            none = 0,
            extraSmall = 3,
            small = 5,
            medium = 7,
            large = 9,
            extraLarge = 13,
        )
        /** Custom border widths proving semantic none/thin/thick resolution. */
        val borders = PixelBorderTokens(none = 0, thin = 3, thick = 5, focus = 2)
        /** Custom stair radii proving component values retain a semantic scale encoding. */
        val radii = PixelRadiusTokens(none = 0, small = 3, medium = 5, large = 7, pill = 31)
        /** Custom component extents proving minimum geometry is not copied into each family. */
        val sizes = PixelSizeTokens(
            iconSmall = 11,
            iconMedium = 13,
            iconLarge = 17,
            selectionControlExtent = 15,
            switchWidth = 19,
            trackHeight = 6,
            compactControlHeight = 14,
            controlHeight = 20,
            touchTarget = 28,
            overlayMinimumWidth = 44,
        )
        /** Standard button token used without a component-specific copy. */
        val button = PixelComponentTokens.Default.button

        assertEquals(EdgeInsets.all(5), button.resolvePadding(spacing))
        assertEquals(3, button.resolveBorderWidth(borders))
        assertEquals(3, button.resolveCornerRadius(radii))
        assertEquals(0, PixelComponentTokens.Default.textButton.resolveBorderWidth(borders))
        assertEquals(15, PixelComponentTokens.Default.checkbox.resolveMinimumWidth(sizes))
        assertEquals(19, PixelComponentTokens.Default.switch.resolveMinimumWidth(sizes))
        assertEquals(6, PixelComponentTokens.Default.slider.resolveMinimumHeight(sizes))
    }

    /** Standard labels provide a non-blank localization seam for every component family. */
    @Test
    fun labelsCoverEveryStandardComponentFamily() {
        /** All public default labels that standard component implementations may consume. */
        val labels = PixelLabelTokens.Default.run {
            listOf(
                confirm,
                cancel,
                dismiss,
                empty,
                error,
                loading,
                button,
                textButton,
                textField,
                listTile,
                checkbox,
                switch,
                slider,
                tabs,
                segmentedControl,
                valueAdjuster,
                decrease,
                increase,
                menu,
                dropdown,
                dialog,
                bottomSheet,
                toast,
                snackbar,
                tooltip,
                progress,
                refresh,
                scrollbar,
                slidable,
            )
        }

        assertTrue(labels.all(String::isNotBlank))
    }

    /** Every integer-based token group rejects values outside its documented pixel domain. */
    @Test
    fun integerAndLabelTokensRejectInvalidValues() {
        assertIllegalArgument { PixelTypographyToken(lineSpacing = -1) }
        assertIllegalArgument { PixelTypographyToken(fontScale = 0) }
        assertIllegalArgument { PixelSpacingTokens(small = -1) }
        assertIllegalArgument { PixelSizeTokens(iconSmall = 0) }
        assertIllegalArgument { PixelRadiusTokens(medium = -1) }
        assertIllegalArgument { PixelBorderTokens(thin = 0) }
        assertIllegalArgument { PixelElevationTokens(low = -1) }
        assertIllegalArgument { PixelFocusIndicatorTokens(width = 0) }
        assertIllegalArgument { PixelFocusIndicatorTokens(inset = -1) }
        assertIllegalArgument {
            PixelComponentColorTokens(padding = EdgeInsets.only(left = -1))
        }
        assertIllegalArgument { PixelLabelTokens(button = "  ") }
    }

    /** Standard focus width follows the foundation token while uncommon widths stay literal. */
    @Test
    fun focusIndicatorResolvesFoundationWidthWithoutCapturingLiteralOverrides() {
        /** Custom foundation width proving the standard one-pixel encoding is late-bound. */
        val borders = PixelBorderTokens.Default.copy(focus = 3)

        assertEquals(3, PixelFocusIndicatorTokens.Default.resolveWidth(borders))
        assertEquals(4, PixelFocusIndicatorTokens(width = 4).resolveWidth(borders))
    }

    /** Computes WCAG contrast ratio for two opaque sRGB colors. */
    private fun contrastRatio(first: PixelColor, second: PixelColor): Double {
        /** Relative luminance of the lighter color. */
        val lighter = max(relativeLuminance(first), relativeLuminance(second))
        /** Relative luminance of the darker color. */
        val darker = min(relativeLuminance(first), relativeLuminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** Converts an sRGB PixelColor to WCAG relative luminance. */
    private fun relativeLuminance(color: PixelColor): Double {
        return 0.2126 * linearChannel(color.red) +
            0.7152 * linearChannel(color.green) +
            0.0722 * linearChannel(color.blue)
    }

    /** Linearizes one 8-bit sRGB channel. */
    private fun linearChannel(channel: Int): Double {
        /** Normalized sRGB channel in the closed 0..1 range. */
        val normalized = channel / 255.0
        return if (normalized <= 0.04045) {
            normalized / 12.92
        } else {
            ((normalized + 0.055) / 1.055).pow(2.4)
        }
    }

    /** Asserts that [block] rejects invalid public token input. */
    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected validation path.
        }
    }
}
