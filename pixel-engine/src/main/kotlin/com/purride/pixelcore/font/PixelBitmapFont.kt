package com.purride.pixelcore

/**
 * 最小可用位图字体。
 *
 * 这一层给 pixel-engine UI 层和宿主应用提供零资产依赖的默认文本能力，
 * 但内部已经改为复用 `PixelFontEngine`。这样后续无论切到真实字形包还是保留内置字体，
 * 上层都走同一条文本测量与绘制链路。
 */
public class PixelBitmapFont(
    /** 定义 `PixelBitmapFont` 布局中的 `glyphWidth` 逻辑像素度量。 */
    public val glyphWidth: Int = 5,
    /** 定义 `PixelBitmapFont` 布局中的 `glyphHeight` 逻辑像素度量。 */
    public val glyphHeight: Int = 7,
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

    /** 定义 `PixelBitmapFont` 布局中的 `lineHeight` 逻辑像素度量。 */
    public val lineHeight: Int
        get() = glyphHeight + lineSpacing

    override fun measureText(text: String): Int {
        if (!text.hasExplicitCrLfLineBreak()) {
            return engine.measureText(text, glyphStyle)
        }
        /** 仅多行输入才创建显式行集合。 */
        val lines = text.lines()
        return lines.maxOfOrNull { line -> engine.measureText(line, glyphStyle) } ?: 0
    }

    /** 连续测量两个无硬换行片段，避免段落 pair 测量创建拼接字符串。 */
    internal fun measureAdjacentText(first: String, second: String): Int {
        return engine.measureAdjacentText(first = first, second = second, style = glyphStyle)
    }

    override fun measureHeight(text: String): Int {
        if (!text.hasExplicitCrLfLineBreak()) {
            return glyphHeight
        }
        /** 仅多行输入才统计显式行集合。 */
        val lineCount = text.lines().size.coerceAtLeast(1)
        return (lineCount * glyphHeight) + ((lineCount - 1) * lineSpacing)
    }

    override fun fontMetrics(text: String): PixelFontMetrics {
        return engine.fontMetrics(text = text, style = glyphStyle)
    }

    override fun drawText(
        buffer: PixelBuffer,
        text: String,
        x: Int,
        y: Int,
        color: PixelColor,
    ) {
        if (!text.hasExplicitCrLfLineBreak()) {
            engine.drawText(
                buffer = buffer,
                text = text,
                startX = x,
                startY = y,
                maxWidth = Int.MAX_VALUE,
                color = color,
                style = glyphStyle,
            )
            return
        }
        /** 下一条显式文本行的纵向原点。 */
        var cursorY = y
        text.lines().forEach { line ->
            engine.drawText(
                buffer = buffer,
                text = line,
                startX = x,
                startY = cursorY,
                maxWidth = Int.MAX_VALUE,
                color = color,
                style = glyphStyle,
            )
            cursorY += lineHeight
        }
    }

    /** 集中提供 `PixelBitmapFont` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 提供 `PixelBitmapFont` 的 `Default` 稳定默认值或常量。 */
        public val Default: PixelBitmapFont = PixelBitmapFont()

        private val GLYPHS = mapOf(
            ' ' to glyph("00000", "00000", "00000", "00000", "00000", "00000", "00000"),
            '!' to glyph("00100", "00100", "00100", "00100", "00100", "00000", "00100"),
            '-' to glyph("00000", "00000", "00000", "11111", "00000", "00000", "00000"),
            ':' to glyph("00000", "00100", "00100", "00000", "00100", "00100", "00000"),
            '/' to glyph("00001", "00010", "00100", "01000", "10000", "00000", "00000"),
            '.' to glyph("00000", "00000", "00000", "00000", "00000", "00100", "00100"),
            '?' to glyph("11110", "00001", "00001", "00110", "00100", "00000", "00100"),
            '+' to glyph("00000", "00100", "00100", "11111", "00100", "00100", "00000"),
            '<' to glyph("00010", "00100", "01000", "10000", "01000", "00100", "00010"),
            '>' to glyph("01000", "00100", "00010", "00001", "00010", "00100", "01000"),
            ',' to glyph("00000", "00000", "00000", "00000", "00000", "00100", "01000"),
            ';' to glyph("00000", "00100", "00100", "00000", "00100", "00100", "01000"),
            '(' to glyph("00010", "00100", "01000", "01000", "01000", "00100", "00010"),
            ')' to glyph("01000", "00100", "00010", "00010", "00010", "00100", "01000"),
            '[' to glyph("01110", "01000", "01000", "01000", "01000", "01000", "01110"),
            ']' to glyph("01110", "00010", "00010", "00010", "00010", "00010", "01110"),
            '=' to glyph("00000", "00000", "11111", "00000", "11111", "00000", "00000"),
            '_' to glyph("00000", "00000", "00000", "00000", "00000", "00000", "11111"),
            '*' to glyph("00000", "00100", "10101", "01110", "10101", "00100", "00000"),
            '#' to glyph("01010", "01010", "11111", "01010", "11111", "01010", "01010"),
            '%' to glyph("11000", "11001", "00010", "00100", "01000", "10011", "00011"),
            '&' to glyph("01100", "10010", "10100", "01000", "10101", "10010", "01101"),
            '\'' to glyph("00100", "00100", "00000", "00000", "00000", "00000", "00000"),
            '"' to glyph("01010", "01010", "00000", "00000", "00000", "00000", "00000"),
            '@' to glyph("01110", "10001", "10111", "10101", "10110", "10000", "01111"),
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
     * 这样 pixel-engine UI layer 默认文本链路也能走 `PixelFontEngine` 的测量与绘制逻辑。
     */
    private class BuiltinBitmapGlyphSource(
        private val glyphWidth: Int,
        private val glyphHeight: Int,
    ) : GlyphSource {

        private val glyphCache = mutableMapOf<Char, GlyphBitmap>()

        /**
         * 按完整 Unicode scalar 查内置 5x7 字模。
         *
         * 内置字模只覆盖 ASCII，因此 BMP 之外的 scalar 直接返回 `null` 交给下一个 source；
         * 这里的 `Char` 只是内部查表键，不是对外 SPI。
         */
        override fun findGlyph(codePoint: Int, style: GlyphStyle): GlyphBitmap? {
            if (codePoint > Char.MAX_VALUE.code) return null
            /** 内置 ASCII 字模表的内部 BMP 查表键。 */
            val character = codePoint.toChar()
            val normalizedCharacter = when {
                character == ' ' -> ' '
                character.isLowerCase() -> character.uppercaseChar()
                else -> character
            }
            if (!GLYPHS.containsKey(normalizedCharacter)) return null
            return glyphCache.getOrPut(normalizedCharacter) {
                val rows = GLYPHS.getValue(normalizedCharacter)
                val sourceHeight = rows.size.coerceAtLeast(1)
                val sourceWidth = rows.maxOfOrNull { row -> row.length }?.coerceAtLeast(1) ?: 1
                val pixels = ByteArray(glyphWidth * glyphHeight)
                var inkLeft = glyphWidth
                var inkRight = -1

                /**
                 * 内置字模源固定是 5x7，但 `PixelBitmapFont` 允许上层请求别的尺寸。
                 *
                 * 这里统一把源字模按目标 `glyphWidth/glyphHeight` 重新采样，避免再出现
                 * “4x5 字体去直接写 5x7 源像素”导致的数组越界。
                 */
                for (targetY in 0 until glyphHeight) {
                    val sourceY = (targetY * sourceHeight) / glyphHeight.coerceAtLeast(1)
                    val sourceRow = rows[sourceY]
                    for (targetX in 0 until glyphWidth) {
                        val sourceX = (targetX * sourceWidth) / glyphWidth.coerceAtLeast(1)
                        val sourcePixel = sourceRow.getOrNull(sourceX) ?: '0'
                        if (sourcePixel == '1') {
                            pixels[(targetY * glyphWidth) + targetX] = 1
                            if (targetX < inkLeft) {
                                inkLeft = targetX
                            }
                            if (targetX > inkRight) {
                                inkRight = targetX
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
