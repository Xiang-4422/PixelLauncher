package com.purride.pixelui

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelClusterTextRasterizer
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelFontMetrics
import com.purride.pixelui.internal.PixelTextScaleRasterizer
import com.purride.pixelui.internal.PixelUiRuntime
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies Host text scale, high-contrast preset selection and RTL Row visual ordering. */
class AdaptiveTextContrastRtlTest {
    /** 拆分后的 engine 应通过 core 桥测量内置字体，不得递归调用同名扩展。 */
    @Test
    fun scaledCoreBitmapFontMeasuresAdjacentTextWithoutRecursion() {
        /** 来自 pixel-engine 核心层的默认位图字体。 */
        val base = PixelBitmapFont.Default
        /** 来自 pixel-engine artifact 的 Host 缩放适配器。 */
        val scaled = PixelTextScaleRasterizer(base, scaleFactor = 1.5f)

        assertEquals(17, scaled.measureAdjacentText(first = "A", second = "B"))
    }

    /** Fractional scaling keeps measurement, font metrics and nearest-neighbor paint consistent. */
    @Test
    fun fractionalTextScaleRasterizerUsesMatchingMeasureAndPaintExtents() {
        /** Deterministic filled two-by-two glyph rasterizer. */
        val base = FilledGlyphRasterizer()
        /** One-and-a-half scale adapter under test. */
        val scaled = PixelTextScaleRasterizer(base, scaleFactor = 1.5f)
        /** Destination containing padding around the scaled glyph. */
        val buffer = PixelBuffer(width = 5, height = 5)

        scaled.drawText(
            buffer = buffer,
            text = "A",
            x = 1,
            y = 1,
            color = PixelColor.White,
        )

        assertEquals(3, scaled.measureText("A"))
        assertEquals(3, scaled.measureHeight("A"))
        assertEquals(
            PixelFontMetrics(
                cellHeight = 3,
                baseline = 2,
                ascent = 2,
                descent = 2,
                inkTop = 0,
                inkBottom = 2,
            ),
            scaled.fontMetrics("A"),
        )
        assertEquals(9, buffer.pixels.count { pixel -> pixel == PixelColor.White.argb })
    }

    /** Text, RichText and TextField share the same inherited text-scale layout dependency. */
    @Test
    fun textRichTextAndTextFieldConsumeHostScaleWithoutChangingEditingState() {
        /** Runtime tester reused for successive environment snapshots. */
        val tester = PixelTester()
        /** Deterministic rasterizer used by inherited and explicit style paths. */
        val rasterizer = FilledGlyphRasterizer()
        /** Controlled editable state whose identity and UTF-16 selection must remain stable. */
        val controller = PixelTextFieldController()
        /** Initial one-character value with caret at its trailing grapheme boundary. */
        val fieldState = controller.create(initialText = "A")

        try {
            tester.pumpWidget(
                widget = textEnvironment(
                    scaleFactor = 1f,
                    rasterizer = rasterizer,
                    child = Text("A"),
                ),
                logicalWidth = 12,
                logicalHeight = 8,
            )
            /** Unscaled Text ink count from one two-by-two glyph. */
            val unscaledTextPixels = tester.renderResult!!.buffer.pixels.count { pixel ->
                pixel == PixelColor.White.argb
            }

            tester.pumpWidget(
                widget = textEnvironment(
                    scaleFactor = 1.5f,
                    rasterizer = rasterizer,
                    child = Text("A"),
                ),
                logicalWidth = 12,
                logicalHeight = 8,
            )
            /** Fractionally scaled Text ink count from the same backing glyph. */
            val scaledTextPixels = tester.renderResult!!.buffer.pixels.count { pixel ->
                pixel == PixelColor.White.argb
            }

            tester.pumpWidget(
                widget = textEnvironment(
                    scaleFactor = 1.5f,
                    rasterizer = rasterizer,
                    child = RichText(
                        spans = listOf(
                            PixelTextSpan(
                                text = "A",
                                style = PixelTextStyle(textRasterizer = rasterizer),
                            ),
                        ),
                    ),
                ),
                logicalWidth = 12,
                logicalHeight = 8,
            )
            /** Fractionally scaled RichText ink using its explicit style rasterizer. */
            val scaledRichTextPixels = tester.renderResult!!.buffer.pixels.count { pixel ->
                pixel == PixelColor.White.argb
            }

            tester.pumpWidget(
                widget = textEnvironment(
                    scaleFactor = 1f,
                    rasterizer = rasterizer,
                    child = TextField(
                        state = fieldState,
                        controller = controller,
                        style = TextFieldStyle.Default.copy(
                            textStyle = PixelTextStyle(textRasterizer = rasterizer),
                            padding = 0,
                        ),
                        semanticLabel = "Scale field",
                        key = "scale-field",
                    ),
                ),
                logicalWidth = 20,
                logicalHeight = 10,
            )
            /** Unscaled caret geometry derived from the TextField paragraph. */
            val unscaledCaret = requireNotNull(
                tester.renderResult!!.textInputTargets.single().caretBoundsForIndex?.invoke(1),
            )

            tester.pumpWidget(
                widget = textEnvironment(
                    scaleFactor = 2f,
                    rasterizer = rasterizer,
                    child = TextField(
                        state = fieldState,
                        controller = controller,
                        style = TextFieldStyle.Default.copy(
                            textStyle = PixelTextStyle(textRasterizer = rasterizer),
                            padding = 0,
                        ),
                        semanticLabel = "Scale field",
                        key = "scale-field",
                    ),
                ),
                logicalWidth = 20,
                logicalHeight = 10,
            )
            /** Scaled caret geometry from the same retained editing state. */
            val scaledCaret = requireNotNull(
                tester.renderResult!!.textInputTargets.single().caretBoundsForIndex?.invoke(1),
            )

            assertEquals(4, unscaledTextPixels)
            assertEquals(9, scaledTextPixels)
            assertEquals(9, scaledRichTextPixels)
            assertTrue(scaledCaret.height > unscaledCaret.height)
            assertEquals("A", fieldState.text)
            assertEquals(1, fieldState.selectionStart)
            assertEquals(1, fieldState.selectionEnd)
        } finally {
            tester.dispose()
        }
    }

    /** High-contrast helper selects brightness-matched presets and rebuilds inherited consumers. */
    @Test
    fun highContrastThemeHelperSelectsPresetAndTracksHostDependency() {
        /** Tokens observed by the same widget instance across inherited updates. */
        val observedTokens = mutableListOf<PixelThemeTokens>()
        /** Stable dependent proving [PixelThemeTokens.forHost] subscribes to capabilities. */
        val probe = AdaptiveThemeProbe(observedTokens)
        /** Retained runtime used for two complete Host snapshots. */
        val runtime = PixelUiRuntime()

        try {
            runtime.render(
                root = HostCapabilities(
                    data = HostCapabilitiesData.Default,
                    child = probe,
                ),
                logicalWidth = 4,
                logicalHeight = 4,
            )
            runtime.render(
                root = HostCapabilities(
                    data = HostCapabilitiesData.Default.copy(highContrast = true),
                    child = probe,
                ),
                logicalWidth = 4,
                logicalHeight = 4,
            )

            assertEquals(
                listOf(PixelThemeTokens.Dark, PixelThemeTokens.HighContrastDark),
                observedTokens,
            )
            assertSame(
                PixelThemeTokens.Light,
                PixelThemeTokens.forCapabilities(
                    capabilities = HostCapabilitiesData.Default,
                    brightness = PixelThemeBrightness.Light,
                ),
            )
            assertSame(
                PixelThemeTokens.HighContrastLight,
                PixelThemeTokens.forCapabilities(
                    capabilities = HostCapabilitiesData.Default.copy(highContrast = true),
                    brightness = PixelThemeBrightness.Light,
                ),
            )
        } finally {
            runtime.dispose()
        }
    }

    /** RTL Row reverses visual positions while semantics retain declaration order and identity. */
    @Test
    fun rtlRowReversesVisualChildrenButPreservesSemanticOrder() {
        /** Tester rendering one deterministic two-child row. */
        val tester = PixelTester()
        /** First declared child color. */
        val firstColor = PixelColor.fromRgb(255, 0, 0)
        /** Second declared child color. */
        val secondColor = PixelColor.fromRgb(0, 0, 255)

        try {
            tester.pumpWidget(
                widget = Directionality(
                    textDirection = TextDirection.RTL,
                    child = Row(
                        children = listOf(
                            Semantics(
                                label = "FIRST",
                                child = Container(
                                    width = 1,
                                    height = 1,
                                    fillColor = firstColor,
                                    borderColor = null,
                                ),
                            ),
                            Semantics(
                                label = "SECOND",
                                child = Container(
                                    width = 1,
                                    height = 1,
                                    fillColor = secondColor,
                                    borderColor = null,
                                ),
                            ),
                        ),
                    ),
                ),
                logicalWidth = 4,
                logicalHeight = 2,
            )
            /** Final frame whose first two cells reveal visual placement. */
            val buffer = tester.renderResult!!.buffer
            /** Semantics nodes returned in declaration order with visually reversed bounds. */
            val semantics = tester.semanticsNodes().filter { node ->
                node.label == "FIRST" || node.label == "SECOND"
            }

            assertEquals(secondColor, buffer.getPixel(0, 0))
            assertEquals(firstColor, buffer.getPixel(1, 0))
            assertEquals(listOf("FIRST", "SECOND"), semantics.map { node -> node.label })
            assertEquals(1, semantics[0].left)
            assertEquals(0, semantics[1].left)
        } finally {
            tester.dispose()
        }
    }

    /** Wraps a child with one complete Host snapshot and inherited deterministic rasterizer. */
    private fun textEnvironment(
        /** Positive Host text multiplier under test. */
        scaleFactor: Float,
        /** Rasterizer inherited by text without a style override. */
        rasterizer: PixelClusterTextRasterizer,
        /** Text, RichText or TextField subtree receiving the environment. */
        child: Widget,
    ): Widget {
        return HostCapabilities(
            data = HostCapabilitiesData.Default.copy(textScaleFactor = scaleFactor),
            child = DefaultTextRasterizer(
                rasterizer = rasterizer,
                child = child,
            ),
        )
    }
}

/** Widget recording the brightness-matched adaptive token preset selected during each build. */
private class AdaptiveThemeProbe(
    /** Ordered sink receiving the selected token instance. */
    private val observedTokens: MutableList<PixelThemeTokens>,
) : StatelessWidget() {
    /** Subscribes to Host capabilities through the production high-contrast helper. */
    override fun build(context: BuildContext): Widget {
        /** Dark preset selected from the current inherited high-contrast preference. */
        val tokens = PixelThemeTokens.forHost(context, PixelThemeBrightness.Dark)
        observedTokens += tokens
        return Container(
            width = 1,
            height = 1,
            fillColor = tokens.colors.background,
            borderColor = null,
        )
    }
}

/** Deterministic rasterizer painting every code point as one filled two-by-two glyph. */
private class FilledGlyphRasterizer : PixelClusterTextRasterizer {
    /** Returns two logical pixels per Unicode scalar. */
    override fun measureText(text: String): Int {
        return Character.codePointCount(text, 0, text.length) * 2
    }

    /** Every glyph occupies exactly two logical rows. */
    override fun measureHeight(text: String): Int = 2

    /** Returns stable metrics matching the filled two-row glyph cell. */
    override fun fontMetrics(text: String): PixelFontMetrics {
        return PixelFontMetrics(
            cellHeight = 2,
            baseline = 1,
            ascent = 1,
            descent = 1,
            inkTop = 0,
            inkBottom = 1,
        )
    }

    /** Treats every non-empty grapheme as one supported atomic paint payload. */
    override fun canRasterizeCluster(cluster: String): Boolean = cluster.isNotEmpty()

    /** Paints one filled two-by-two block for every scalar in [text]. */
    override fun drawText(
        buffer: PixelBuffer,
        text: String,
        x: Int,
        y: Int,
        color: PixelColor,
    ) {
        /** Number of scalar glyph blocks emitted from the exact text. */
        val glyphCount = Character.codePointCount(text, 0, text.length)
        repeat(glyphCount) { glyphIndex ->
            repeat(2) { row ->
                repeat(2) { column ->
                    buffer.setPixel(
                        x = x + glyphIndex * 2 + column,
                        y = y + row,
                        color = color,
                    )
                }
            }
        }
    }
}
