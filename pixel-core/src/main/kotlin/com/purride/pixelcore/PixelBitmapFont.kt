package com.purride.pixelcore

/**
 * 最小可用位图字体。
 *
 * 这一版先提供稳定、零资产依赖的英文/数字位图字体，用来支撑 `pixel-ui`
 * 的文本组件与 demo 页面。更完整的字形包体系可以在后续阶段继续增强。
 */
class PixelBitmapFont(
    val glyphWidth: Int = 5,
    val glyphHeight: Int = 7,
    private val letterSpacing: Int = 1,
    private val lineSpacing: Int = 2,
) {
    val lineHeight: Int
        get() = glyphHeight + lineSpacing

    fun measureText(text: String): Int {
        val lines = text.lines()
        return lines.maxOfOrNull { measureSingleLine(it) } ?: 0
    }

    fun measureHeight(text: String): Int {
        val lineCount = text.lines().size.coerceAtLeast(1)
        return (lineCount * glyphHeight) + ((lineCount - 1) * lineSpacing)
    }

    fun drawText(
        buffer: PixelBuffer,
        text: String,
        x: Int,
        y: Int,
        value: Byte = PixelTone.ON.value,
    ) {
        var cursorY = y
        text.lines().forEach { line ->
            drawSingleLine(
                buffer = buffer,
                text = line,
                x = x,
                y = cursorY,
                value = value,
            )
            cursorY += lineHeight
        }
    }

    private fun measureSingleLine(text: String): Int {
        if (text.isEmpty()) {
            return 0
        }
        return (text.length * glyphWidth) + ((text.length - 1) * letterSpacing)
    }

    private fun drawSingleLine(
        buffer: PixelBuffer,
        text: String,
        x: Int,
        y: Int,
        value: Byte,
    ) {
        var cursorX = x
        text.forEach { character ->
            val glyphRows = glyphFor(character)
            glyphRows.forEachIndexed { rowIndex, row ->
                row.forEachIndexed { columnIndex, pixel ->
                    if (pixel == '1') {
                        buffer.setPixel(cursorX + columnIndex, y + rowIndex, value)
                    }
                }
            }
            cursorX += glyphWidth + letterSpacing
        }
    }

    private fun glyphFor(character: Char): List<String> {
        val normalizedCharacter = when {
            character == ' ' -> ' '
            character.isLowerCase() -> character.uppercaseChar()
            else -> character
        }
        return GLYPHS[normalizedCharacter] ?: GLYPHS.getValue('?')
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
}
