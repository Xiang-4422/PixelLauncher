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
    private var defaultTextRasterizer: PixelTextRasterizer,
) : RenderBox() {
    private var lines: List<RichLine> = emptyList()

    fun updateRichText(
        spans: List<PixelTextSpan>,
        textAlign: PixelTextAlign,
        textDirection: TextDirection,
        softWrap: Boolean,
        overflow: PixelTextOverflow,
        maxLines: Int,
        defaultTextRasterizer: PixelTextRasterizer = this.defaultTextRasterizer,
    ) {
        if (
            this.spans == spans &&
            this.textAlign == textAlign &&
            this.textDirection == textDirection &&
            this.softWrap == softWrap &&
            this.overflow == overflow &&
            this.maxLines == maxLines &&
            this.defaultTextRasterizer === defaultTextRasterizer
        ) {
            return
        }
        this.spans = spans
        this.textAlign = textAlign
        this.textDirection = textDirection
        this.softWrap = softWrap
        this.overflow = overflow
        this.maxLines = maxLines
        this.defaultTextRasterizer = defaultTextRasterizer
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
            var cursorX = offsetX + ParagraphLayoutSupport.resolveLineStartX(
                textAlign = textAlign,
                textDirection = textDirection,
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

    /**
     * 单遍扫描完成字符级换行，按运行累加器判断溢出，
     * 避免每加一字符就重新汇总整行宽度（旧实现 O(n²)）。
     */
    private fun wrapCharacters(
        characters: List<RichCharacter>,
        availableWidth: Int,
    ): List<List<RichCharacter>> {
        val lines = mutableListOf<List<RichCharacter>>()
        val current = mutableListOf<RichCharacter>()
        var currentWidth = 0
        characters.forEach { character ->
            if (character.value == '\n') {
                lines += current.toList()
                current.clear()
                currentWidth = 0
                return@forEach
            }
            val advance = measureChar(character)
            if (current.isNotEmpty() && currentWidth + advance > availableWidth) {
                lines += current.toList()
                current.clear()
                currentWidth = 0
            }
            current += character
            currentWidth += advance
        }
        if (current.isNotEmpty()) {
            lines += current.toList()
        }
        return lines
    }

    /**
     * 在末尾追加省略号，必要时逐个剔除原尾字符直到整体宽度合规。
     *
     * 通过维护"已保留字符总宽 - 省略号宽"的运行累加器避免每次都 sum 全行。
     */
    private fun ellipsize(
        characters: List<RichCharacter>,
        availableWidth: Int,
    ): List<RichCharacter> {
        val ellipsisStyle = characters.lastOrNull()?.style ?: PixelTextStyle.Default
        val ellipsis = ParagraphLayoutSupport.Ellipsis.map { value ->
            RichCharacter(value = value, style = ellipsisStyle)
        }
        val ellipsisWidth = measureWidth(ellipsis)
        if (ellipsisWidth > availableWidth) {
            return emptyList()
        }
        val charWidths = IntArray(characters.size) { measureChar(characters[it]) }
        val result = characters.toMutableList()
        var combinedWidth = charWidths.sum() + ellipsisWidth
        var lastIndex = result.lastIndex
        while (lastIndex >= 0 && combinedWidth > availableWidth) {
            combinedWidth -= charWidths[lastIndex]
            result.removeAt(lastIndex)
            lastIndex -= 1
        }
        return result + ellipsis
    }

    /**
     * 单字符宽度查询。
     */
    private fun measureChar(character: RichCharacter): Int {
        val rasterizer = character.style.textRasterizer ?: defaultTextRasterizer
        return rasterizer.measureText(character.value.toString())
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
