package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.regression.ReviewedGoldenVerifier
import com.purride.pixelui.testing.PixelTester
import org.junit.Test
import java.io.File

/** Locks real standard-component pixels across every built-in theme and one consumer theme. */
class PixelThemeRenderGoldenTest {
    /** Every canonical Button state must match the reviewed exact-ARGB row-run golden. */
    @Test
    fun buttonStateMatrixMatchesExactArgbGoldenAcrossPresetAndCustomThemes() {
        /** Fresh render output generated from production widgets and the retained pipeline. */
        val actual = buildGolden()
        ReviewedGoldenVerifier.assertMatches(
            baselineFile = File(GOLDEN_PATH),
            actual = actual,
            reportStem = File("build/reports/golden/theme/theme-button-state-matrix"),
        )
    }

    /** Renders one isolated Button frame for every theme/state pair and serializes painted bounds. */
    private fun buildGolden(): String {
        /** Ordered themes covering all supported presets plus consumer foundation overrides. */
        val themes = listOf(
            "dark" to PixelThemeTokens.Dark,
            "light" to PixelThemeTokens.Light,
            "highContrastDark" to PixelThemeTokens.HighContrastDark,
            "highContrastLight" to PixelThemeTokens.HighContrastLight,
            "custom" to CUSTOM_THEME,
        )
        return buildString {
            appendLine("schemaVersion=1")
            themes.forEach { (themeName, theme) ->
                PixelControlState.entries.forEach { state ->
                    appendFrame(themeName = themeName, theme = theme, state = state)
                }
            }
        }
    }

    /** Appends one exact frame using row-wise run-length encoding without losing pixel positions. */
    private fun StringBuilder.appendFrame(
        themeName: String,
        theme: PixelThemeTokens,
        state: PixelControlState,
    ) {
        /** Independent deterministic runtime preventing retained state from leaking between frames. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = theme,
                    child = OutlinedButton(
                        text = "I",
                        onPressed = {},
                        states = PixelControlStateSet.of(state),
                        key = "$themeName-${state.name}",
                    ),
                ),
                logicalWidth = CANVAS_WIDTH,
                logicalHeight = CANVAS_HEIGHT,
            )
            /** Completed frame containing the exact ARGB buffer and exported interaction channels. */
            val result = requireNotNull(tester.renderResult)
            /** Smallest non-transparent rectangle, including focus, border, content, and shadow. */
            val bounds = paintedBounds(
                pixels = result.buffer.pixels,
                width = result.buffer.width,
                height = result.buffer.height,
            )
            appendLine("[$themeName.${state.name}]")
            appendLine(
                "canvas=${result.buffer.width}x${result.buffer.height};" +
                    "bounds=${bounds.left},${bounds.top},${bounds.width},${bounds.height};" +
                    "clickTargets=${result.clickTargets.size}",
            )
            for (y in bounds.top until bounds.bottom) {
                /** Exact painted row cropped only by the recorded absolute bounds. */
                val row = IntArray(bounds.width) { relativeX ->
                    result.buffer.pixels[y * result.buffer.width + bounds.left + relativeX]
                }
                appendLine("y=$y:${row.toArgbRuns()}")
            }
        } finally {
            tester.dispose()
        }
    }

    /** Finds the absolute rectangle containing every non-transparent pixel in [pixels]. */
    private fun paintedBounds(pixels: IntArray, width: Int, height: Int): GoldenBounds {
        /** Leftmost painted x coordinate. */
        var left = width
        /** Topmost painted y coordinate. */
        var top = height
        /** Exclusive right edge of all painted pixels. */
        var right = 0
        /** Exclusive bottom edge of all painted pixels. */
        var bottom = 0
        pixels.forEachIndexed { index, argb ->
            if ((argb ushr 24) == 0) return@forEachIndexed
            /** Absolute x coordinate derived from the row-major index. */
            val x = index % width
            /** Absolute y coordinate derived from the row-major index. */
            val y = index / width
            left = minOf(left, x)
            top = minOf(top, y)
            right = maxOf(right, x + 1)
            bottom = maxOf(bottom, y + 1)
        }
        require(right > left && bottom > top) { "The themed Button frame must paint at least one pixel" }
        return GoldenBounds(left = left, top = top, right = right, bottom = bottom)
    }

    /** Encodes an exact ARGB row as `COUNT*ARGB` runs for compact, reviewable golden files. */
    private fun IntArray.toArgbRuns(): String {
        if (isEmpty()) return ""
        /** Completed encoded runs in their original left-to-right order. */
        val runs = mutableListOf<String>()
        /** ARGB value owned by the currently open run. */
        var current = first()
        /** Number of adjacent pixels in the currently open run. */
        var count = 1
        for (index in 1 until size) {
            /** Next exact pixel considered for extending or closing the current run. */
            val next = this[index]
            if (next == current) {
                count += 1
            } else {
                runs += "$count*${current.hexArgb()}"
                current = next
                count = 1
            }
        }
        runs += "$count*${current.hexArgb()}"
        return runs.joinToString(",")
    }

    /** Formats one packed ARGB value as eight uppercase hexadecimal digits. */
    private fun Int.hexArgb(): String = toUInt().toString(radix = 16).padStart(8, '0').uppercase()

    /** Absolute painted rectangle recorded beside every cropped row. */
    private data class GoldenBounds(
        /** Inclusive left edge. */
        val left: Int,
        /** Inclusive top edge. */
        val top: Int,
        /** Exclusive right edge. */
        val right: Int,
        /** Exclusive bottom edge. */
        val bottom: Int,
    ) {
        /** Painted width in logical pixels. */
        val width: Int get() = right - left

        /** Painted height in logical pixels. */
        val height: Int get() = bottom - top
    }

    private companion object {
        /** Fixed viewport width large enough for both preset and expanded custom geometry. */
        const val CANVAS_WIDTH: Int = 48

        /** Fixed viewport height large enough for both preset and expanded custom geometry. */
        const val CANVAS_HEIGHT: Int = 24

        /** 已审阅 exact-pixel 源码基线路径。 */
        const val GOLDEN_PATH: String = "src/test/resources/golden/theme-button-state-matrix.txt"

        /** Consumer-defined theme proving color, typography, spacing, border, and radius overrides. */
        val CUSTOM_THEME: PixelThemeTokens = PixelThemeTokens.Dark.copy(
            colors = PixelColorScheme.Dark.copy(
                onBackground = PixelColor.fromRgb(11, 237, 181),
                surfaceVariant = PixelColor.fromRgb(53, 17, 91),
                onSurface = PixelColor.fromRgb(241, 199, 23),
                outline = PixelColor.fromRgb(229, 31, 97),
                primary = PixelColor.fromRgb(17, 101, 233),
                onPrimary = PixelColor.fromRgb(249, 235, 211),
                danger = PixelColor.fromRgb(197, 19, 47),
                onDanger = PixelColor.fromRgb(255, 227, 229),
                warning = PixelColor.fromRgb(239, 151, 7),
                onWarning = PixelColor.fromRgb(37, 21, 3),
                disabled = PixelColor.fromRgb(73, 79, 83),
                onDisabled = PixelColor.fromRgb(207, 213, 219),
                focus = PixelColor.fromRgb(7, 223, 251),
            ),
            typography = PixelTypographyTokens.Default.copy(
                button = PixelTypographyTokens.Default.button.copy(letterSpacing = 1),
            ),
            spacing = PixelSpacingTokens.Default.copy(small = 3),
            radii = PixelRadiusTokens.Default.copy(small = 2),
            borders = PixelBorderTokens.Default.copy(thin = 2, focus = 3),
        )
    }
}
