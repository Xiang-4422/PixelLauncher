package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.TextDirection

/**
 * 新渲染管线里的富文本对象。
 *
 * 第一版采用字符级换行和 span 样式切换，保持与 `RenderText` 的基础行为一致。
 */
internal class RenderRichText(
    private var spans: List<PixelTextSpan>,
    private var textAlign: PixelTextAlign,
    private var textDirection: TextDirection,
    private var softWrap: Boolean,
    private var overflow: PixelTextOverflow,
    private var maxLines: Int,
    private val defaultTextRasterizer: PixelTextRasterizer,
) : RenderBox() {
    private var lines: List<RichLine> = emptyList()

    fun updateRichText(
        spans: List<PixelTextSpan>,
        textAlign: PixelTextAlign,
        textDirection: TextDirection,
        softWrap: Boolean,
        overflow: PixelTextOverflow,
        maxLines: Int,
    ) {
        if (
            this.spans == spans &&
            this.textAlign == textAlign &&
            this.textDirection == textDirection &&
            this.softWrap == softWrap &&
            this.overflow == overflow &&
            this.maxLines == maxLines
        ) {
            return
        }
        this.spans = spans
        this.textAlign = textAlign
        this.textDirection = textDirection
        this.softWrap = softWrap
        this.overflow = overflow
        this.maxLines = maxLines
        markNeedsLayout()
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        val availableWidth = constraints.maxWidth.coerceAtLeast(0)
        lines = resolveLines(availableWidth = availableWidth)
        val measuredWidth = lines.maxOfOrNull { line -> line.width } ?: 0
        val measuredHeight = lines.sumOf { line -> line.height }
        size = RenderSize(
            width = constraints.constrainWidth(measuredWidth),
            height = constraints.constrainHeight(measuredHeight),
        )
    }

    override fun paint(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    ) {
        var cursorY = offsetY
        val contentWidth = size.width
        val contentHeight = size.height
        lines.forEach { line ->
            if (cursorY - offsetY >= contentHeight) {
                return
            }
            var cursorX = offsetX + resolveLineStartX(
                availableWidth = contentWidth,
                lineWidth = line.width,
            )
            line.characters.forEach { character ->
                if (cursorX - offsetX >= contentWidth) {
                    return@forEach
                }
                val rasterizer = character.style.textRasterizer ?: defaultTextRasterizer
                rasterizer.drawText(
                    buffer = context.buffer,
                    text = character.value.toString(),
                    x = cursorX,
                    y = cursorY,
                    value = character.style.tone.value,
                )
                cursorX += rasterizer.measureText(character.value.toString())
            }
            cursorY += line.height
        }
    }

    private fun resolveLines(availableWidth: Int): List<RichLine> {
        if (maxLines <= 0 || availableWidth <= 0) {
            return emptyList()
        }
        val characters = flattenSpans()
        if (characters.isEmpty()) {
            return emptyList()
        }
        val rawLines = if (softWrap) {
            wrapCharacters(characters = characters, availableWidth = availableWidth)
        } else {
            listOf(characters.takeWhile { character -> character.value != '\n' })
        }
        if (rawLines.isEmpty()) {
            return emptyList()
        }
        val truncated = rawLines.size > maxLines
        val visible = rawLines.take(maxLines).toMutableList()
        if (truncated && overflow == PixelTextOverflow.ELLIPSIS && visible.isNotEmpty()) {
            visible[visible.lastIndex] = ellipsize(
                characters = visible.last(),
                availableWidth = availableWidth,
            )
        }
        return visible.map(::toLine)
    }

    private fun wrapCharacters(
        characters: List<RichCharacter>,
        availableWidth: Int,
    ): List<List<RichCharacter>> {
        val lines = mutableListOf<List<RichCharacter>>()
        val current = mutableListOf<RichCharacter>()
        characters.forEach { character ->
            if (character.value == '\n') {
                lines += current.toList()
                current.clear()
                return@forEach
            }
            val candidate = current + character
            if (current.isNotEmpty() && measureWidth(candidate) > availableWidth) {
                lines += current.toList()
                current.clear()
            }
            current += character
        }
        if (current.isNotEmpty()) {
            lines += current.toList()
        }
        return lines
    }

    private fun ellipsize(
        characters: List<RichCharacter>,
        availableWidth: Int,
    ): List<RichCharacter> {
        val ellipsisStyle = characters.lastOrNull()?.style ?: PixelTextStyle.Default
        val ellipsis = listOf('.', '.', '.').map { value ->
            RichCharacter(value = value, style = ellipsisStyle)
        }
        if (measureWidth(ellipsis) > availableWidth) {
            return emptyList()
        }
        val result = characters.toMutableList()
        while (result.isNotEmpty() && measureWidth(result + ellipsis) > availableWidth) {
            result.removeAt(result.lastIndex)
        }
        return result + ellipsis
    }

    private fun toLine(characters: List<RichCharacter>): RichLine {
        val height = characters.maxOfOrNull { character ->
            val rasterizer = character.style.textRasterizer ?: defaultTextRasterizer
            rasterizer.measureHeight(character.value.toString().ifEmpty { " " }) + character.style.lineSpacing
        } ?: defaultTextRasterizer.measureHeight(" ")
        return RichLine(
            characters = characters,
            width = measureWidth(characters),
            height = height,
        )
    }

    private fun measureWidth(characters: List<RichCharacter>): Int {
        return characters.sumOf { character ->
            val rasterizer = character.style.textRasterizer ?: defaultTextRasterizer
            rasterizer.measureText(character.value.toString())
        }
    }

    private fun flattenSpans(): List<RichCharacter> {
        return spans.flatMap { span ->
            span.text.map { character ->
                RichCharacter(value = character, style = span.style)
            }
        }
    }

    private fun resolveLineStartX(
        availableWidth: Int,
        lineWidth: Int,
    ): Int {
        val freeWidth = (availableWidth - lineWidth).coerceAtLeast(0)
        return when (textAlign) {
            PixelTextAlign.CENTER -> freeWidth / 2
            PixelTextAlign.END -> if (textDirection == TextDirection.RTL) 0 else freeWidth
            PixelTextAlign.START -> if (textDirection == TextDirection.RTL) freeWidth else 0
        }
    }

    private data class RichCharacter(
        val value: Char,
        val style: PixelTextStyle,
    )

    private data class RichLine(
        val characters: List<RichCharacter>,
        val width: Int,
        val height: Int,
    )
}
