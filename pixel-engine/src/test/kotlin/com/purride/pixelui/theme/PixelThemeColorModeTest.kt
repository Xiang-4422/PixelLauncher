package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelColorMode
import com.purride.pixelcore.PixelTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PixelThemeColorModeTest {

    private val orange = PixelColor.fromRgb(255, 165, 0)
    private val blue = PixelColor.fromRgb(0, 0, 255)

    @Test
    fun monoModeReturnsNullColor() {
        val theme = PixelThemeData(
            tokens = PixelThemeTokens.Dark,
        )
        val style = theme.resolveTextStyle(colorMode = PixelColorMode.Mono)
        assertNull(style.color)
    }

    @Test
    fun colorModeResolvesTextColorFromTokens() {
        val theme = PixelThemeData(
            tokens = PixelThemeTokens.Dark,
        )
        val style = theme.resolveTextStyle(colorMode = PixelColorMode.Color)
        assertNotNull(style.color)
        assertEquals(PixelThemeTokens.Dark.textColor, style.color)
    }

    @Test
    fun colorModeResolvesAccentColorFromTokens() {
        val theme = PixelThemeData(
            tokens = PixelThemeTokens.Dark,
        )
        val style = theme.resolveAccentTextStyle(colorMode = PixelColorMode.Color)
        assertNotNull(style.color)
        assertEquals(PixelThemeTokens.Dark.accentColor, style.color)
    }

    @Test
    fun explicitStyleColorOverridesToken() {
        val theme = PixelThemeData(
            textStyle = PixelTextStyle(tone = PixelTone.ON, color = blue),
            tokens = PixelThemeTokens.Dark,
        )
        val style = theme.resolveTextStyle(colorMode = PixelColorMode.Color)
        assertEquals(blue, style.color)
    }

    @Test
    fun colorModeDefaultIsMonoSoNoColorSet() {
        val theme = PixelThemeData(tokens = PixelThemeTokens.Dark)
        val style = theme.resolveTextStyle()
        assertNull(style.color)
    }

    @Test
    fun missingTokenColorInColorModeThrows() {
        val theme = PixelThemeData(
            tokens = PixelThemeTokens.Default,
        )
        try {
            theme.resolveTextStyle(colorMode = PixelColorMode.Color)
            throw AssertionError("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assert(e.message?.contains("textColor") == true)
        }
    }

    @Test
    fun missingAccentTokenColorInColorModeThrows() {
        val theme = PixelThemeData(
            tokens = PixelThemeTokens.Default,
        )
        try {
            theme.resolveAccentTextStyle(colorMode = PixelColorMode.Color)
            throw AssertionError("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assert(e.message?.contains("accentColor") == true)
        }
    }

    @Test
    fun lightPresetHasAllColorFields() {
        val tokens = PixelThemeTokens.Light
        assertNotNull(tokens.textColor)
        assertNotNull(tokens.accentColor)
        assertNotNull(tokens.mutedColor)
        assertNotNull(tokens.surfaceColor)
        assertNotNull(tokens.borderColor)
        assertNotNull(tokens.accentBorderColor)
        assertNotNull(tokens.selectedBorderColor)
        assertNotNull(tokens.pressedBorderColor)
        assertNotNull(tokens.focusedBorderColor)
        assertNotNull(tokens.disabledBorderColor)
        assertNotNull(tokens.readOnlyBorderColor)
    }

    @Test
    fun darkPresetColorsAreDistinct() {
        val tokens = PixelThemeTokens.Dark
        assert(tokens.textColor != tokens.accentColor) {
            "text and accent colors should be different in Dark preset"
        }
    }

    @Test
    fun colorModeDoesNotMutateMonoTone() {
        val theme = PixelThemeData(tokens = PixelThemeTokens.Dark)
        val style = theme.resolveTextStyle(colorMode = PixelColorMode.Color)
        assertEquals(PixelTone.ON, style.tone)
    }
}
