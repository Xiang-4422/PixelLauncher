package com.purride.pixelcore

/**
 * 最小可用位图字体。
 *
 * 这一层继续给 `pixel-ui` 和 `pixel-demo` 提供零资产依赖的默认文本能力，
 * 但内部已经改为复用 `PixelFontEngine`。这样后续无论切到真实字形包还是保留内置字体，
 * 上层都走同一条文本测量与绘制链路。
 */
class PixelBitmapFont(
    val glyphWidth: Int = 5,
    val glyphHeight: Int = 7,
    private val letterSpacing: Int = 1,
    private val lineSpacing: Int = 2,
) : PixelTextRasterizer {
    private val glyphStyle = GlyphStyle(
        cellHeight = glyphHeight,
        narrowAdvanceWidth = glyphWidth,
        wideAdvanceWidth = glyphWidth,
        oversampleFactor = 1,
        narrowMinimumSampleRatio = 1f,
        wideMinimumSampleRatio = 1f,
        narrowTextSizeRatio = 1f,
        wideTextSizeRatio = 1f,
        narrowFontWeight = PixelFontWeight.NORMAL,
        wideFontWeight = PixelFontWeight.NORMAL,
        narrowFontFamily = PixelFontFamily.MONOSPACE,
        wideFontFamily = PixelFontFamily.MONOSPACE,
        baseLetterSpacing = letterSpacing,
    )

    private val engine = PixelFontEngine(
        glyphProvider = CompositeGlyphProvider(
            sources = listOf(
                BuiltinBitmapGlyphSource(
                    glyphWidth = glyphWidth,
                    glyphHeight = glyphHeight,
                ),
            ),
        ),
    )

    val lineHeight: Int
        get() = glyphHeight + lineSpacing

    override fun measureText(text: String): Int {
        val lines = text.lines()
        return lines.maxOfOrNull { line -> engine.measureText(line, glyphStyle) } ?: 0
    }

    override fun measureHeight(text: String): Int {
        val lineCount = text.lines().size.coerceAtLeast(1)
        return (lineCount * glyphHeight) + ((lineCount - 1) * lineSpacing)
    }

    override fun drawText(
        buffer: PixelBuffer,
        text: String,
        x: Int,
        y: Int,
        value: Byte,
    ) {
        var cursorY = y
        text.lines().forEach { line ->
            engine.drawText(
                buffer = buffer,
                text = line,
                startX = x,
                startY = cursorY,
                maxWidth = Int.MAX_VALUE,
                value = value,
                style = glyphStyle,
            )
            cursorY += lineHeight
        }
    }

    companion object {
        val Default = PixelBitmapFont()

        private val GLYPHS = mapOf(
            ' ' to glyph("00000", "00000", "00000", "00000", "00000", "00000", "00000"),
            '!' to glyph("00100", "00100", "00100", "00100", "00100", "00000", "00100"),
            '-' to glyph("00000", "00000", "00000", "11111", "00000", "00000", "00000"),
            ':' to glyph("00000", "00100", "00100", "00000", "00100", "00100", "00000"),
            '/' to glyph("00001", "00010", "00100", "01000", "10000", "00000", "00000"),
            '.' to glyph("00000", "00000", "00000", "00000", "00000", "00100", "00100"),
            '?' to glyph("11110", "00001", "00001", "00110", "00100", "00000", "00100"),
            '0' to glyph("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
            '1' to glyph("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
            '2' to glyph("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
            '3' to glyph("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
            '4' to glyph("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
            '5' to glyph("11111", "10000", "10000", "11110", "00001", "00001", "11110"),
            '6' to glyph("01110", "10000", "10000", "11110", "10001", "10001", "01110"),
            '7' to glyph("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
            '8' to glyph("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
            '9' to glyph("01110", "10001", "10001", "01111", "00001", "00001", "01110"),
            'A' to glyph("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
            'B' to glyph("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
            'C' to glyph("01111", "10000", "10000", "10000", "10000", "10000", "01111"),
            'D' to glyph("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
            'E' to glyph("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
            'F' to glyph("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
            'G' to glyph("01111", "10000", "10000", "10111", "10001", "10001", "01111"),
            'H' to glyph("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
            'I' to glyph("11111", "00100", "00100", "00100", "00100", "00100", "11111"),
            'J' to glyph("00001", "00001", "00001", "00001", "10001", "10001", "01110"),
            'K' to glyph("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
            'L' to glyph("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
            'M' to glyph("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
            'N' to glyph("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
            'O' to glyph("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
            'P' to glyph("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
            'Q' to glyph("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
            'R' to glyph("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
            'S' to glyph("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
            'T' to glyph("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
            'U' to glyph("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
            'V' to glyph("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
            'W' to glyph("10001", "10001", "10001", "10101", "10101", "10101", "01010"),
            'X' to glyph("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
            'Y' to glyph("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
            'Z' to glyph("11111", "00001", "00010", "00100", "01000", "10000", "11111"),
        )

        private fun glyph(
            row1: String,
            row2: String,
            row3: String,
            row4: String,
            row5: String,
            row6: String,
            row7: String,
        ): List<String> = listOf(row1, row2, row3, row4, row5, row6, row7)
    }

    /**
     * 内置位图字形源。
     *
     * 这一层把原来硬编码在 `PixelBitmapFont` 里的 5x7 字模转成 `GlyphBitmap`，
     * 这样 `pixel-ui` 默认文本链路也能走 `PixelFontEngine` 的测量与绘制逻辑。
     */
    private class BuiltinBitmapGlyphSource(
        private val glyphWidth: Int,
        private val glyphHeight: Int,
    ) : GlyphSource {

        private val glyphCache = mutableMapOf<Char, GlyphBitmap>()

        override fun findGlyph(character: Char, style: GlyphStyle): GlyphBitmap {
            val normalizedCharacter = when {
                character == ' ' -> ' '
                character.isLowerCase() -> character.uppercaseChar()
                else -> character
            }
            return glyphCache.getOrPut(normalizedCharacter) {
                val rows = GLYPHS[normalizedCharacter] ?: GLYPHS.getValue('?')
                val pixels = ByteArray(glyphWidth * glyphHeight)
                var inkLeft = glyphWidth
                var inkRight = -1
                rows.forEachIndexed { rowIndex, row ->
                    row.forEachIndexed { columnIndex, pixel ->
                        if (pixel == '1') {
                            pixels[(rowIndex * glyphWidth) + columnIndex] = 1
                            if (columnIndex < inkLeft) {
                                inkLeft = columnIndex
                            }
                            if (columnIndex > inkRight) {
                                inkRight = columnIndex
                            }
                        }
                    }
                }
                GlyphBitmap(
                    width = glyphWidth,
                    height = glyphHeight,
                    pixels = pixels,
                    metrics = GlyphMetrics(
                        advanceWidth = style.narrowAdvanceWidth,
                        baselineOffset = glyphHeight - 1,
                        isWideGlyph = false,
                        requiresVisualGapProtection = false,
                        inkLeft = inkLeft,
                        inkRight = inkRight,
                    ),
                )
            }
        }

        override fun clearCache() {
            glyphCache.clear()
        }
    }
}
