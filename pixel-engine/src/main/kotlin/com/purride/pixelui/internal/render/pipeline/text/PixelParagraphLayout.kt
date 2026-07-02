package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.TextDirection

internal data class PixelParagraphInput(
    val spans: List<PixelTextSpan>,
    val textAlign: PixelTextAlign,
    val textDirection: TextDirection,
    val softWrap: Boolean,
    val overflow: PixelTextOverflow,
    val maxLines: Int,
    val defaultTextRasterizer: PixelTextRasterizer,
)

internal data class PixelParagraphLayout(
    val lines: List<PixelParagraphLine>,
) {
    val width: Int = lines.maxOfOrNull { line -> line.width } ?: 0
    val height: Int = lines.sumOf { line -> line.height }
}

internal data class PixelParagraphLine(
    val runs: List<PixelParagraphRun>,
    val width: Int,
    val height: Int,
    val sourceStart: Int,
    val sourceEnd: Int,
)

internal data class PixelParagraphRun(
    val text: String,
    val style: PixelTextStyle,
    val width: Int,
)

internal object PixelParagraphLayouter {

    fun layout(
        input: PixelParagraphInput,
        availableWidth: Int,
    ): PixelParagraphLayout {
        if (input.maxLines <= 0 || availableWidth <= 0) {
            return PixelParagraphLayout(lines = emptyList())
        }
        val characters = flattenSpans(input.spans)
        if (characters.isEmpty()) {
            return PixelParagraphLayout(lines = emptyList())
        }
        val rawLines = if (input.softWrap) {
            wrapCharacters(
                characters = characters,
                availableWidth = availableWidth,
                defaultTextRasterizer = input.defaultTextRasterizer,
            )
        } else {
            val singleLine = characters.takeWhile { character -> character.value != '\n' }
            if (
                input.overflow == PixelTextOverflow.ELLIPSIS &&
                measureWidth(singleLine, input.defaultTextRasterizer) > availableWidth
            ) {
                listOf(
                    ellipsize(
                        characters = singleLine,
                        availableWidth = availableWidth,
                        defaultTextRasterizer = input.defaultTextRasterizer,
                    ),
                )
            } else {
                listOf(singleLine)
            }
        }.filterNot { line -> line.isEmpty() && characters.none { it.value == '\n' } }

        if (rawLines.isEmpty()) {
            return PixelParagraphLayout(lines = emptyList())
        }
        val truncated = rawLines.size > input.maxLines
        val visible = rawLines.take(input.maxLines).toMutableList()
        if (truncated && input.overflow == PixelTextOverflow.ELLIPSIS && visible.isNotEmpty()) {
            visible[visible.lastIndex] = ellipsize(
                characters = visible.last(),
                availableWidth = availableWidth,
                defaultTextRasterizer = input.defaultTextRasterizer,
            )
        }
        return PixelParagraphLayout(
            lines = visible.mapIndexed { index, line ->
                toLine(
                    characters = line,
                    defaultTextRasterizer = input.defaultTextRasterizer,
                    includeTrailingLineSpacing = index < visible.lastIndex,
                )
            },
        )
    }

    private fun wrapCharacters(
        characters: List<ParagraphCharacter>,
        availableWidth: Int,
        defaultTextRasterizer: PixelTextRasterizer,
    ): List<List<ParagraphCharacter>> {
        val lines = mutableListOf<List<ParagraphCharacter>>()
        val current = mutableListOf<ParagraphCharacter>()
        characters.forEach { character ->
            if (character.value == '\n') {
                lines += current.toList()
                current.clear()
                return@forEach
            }
            val candidate = current + character
            if (
                current.isNotEmpty() &&
                measureWidth(
                    characters = candidate,
                    defaultTextRasterizer = defaultTextRasterizer,
                ) > availableWidth
            ) {
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
        characters: List<ParagraphCharacter>,
        availableWidth: Int,
        defaultTextRasterizer: PixelTextRasterizer,
    ): List<ParagraphCharacter> {
        val ellipsisStyle = characters.lastOrNull()?.style ?: PixelTextStyle.Default
        val ellipsisSourceIndex = (characters.lastOrNull()?.sourceIndex ?: -1) + 1
        val ellipsis = ParagraphLayoutSupport.Ellipsis.map { value ->
            ParagraphCharacter(value = value, style = ellipsisStyle, sourceIndex = ellipsisSourceIndex)
        }
        val ellipsisWidth = measureWidth(
            characters = ellipsis,
            defaultTextRasterizer = defaultTextRasterizer,
        )
        if (ellipsisWidth > availableWidth) {
            return emptyList()
        }
        val result = characters.toMutableList()
        while (
            result.isNotEmpty() &&
            measureWidth(
                characters = result + ellipsis,
                defaultTextRasterizer = defaultTextRasterizer,
            ) > availableWidth
        ) {
            result.removeAt(result.lastIndex)
        }
        return result + ellipsis
    }

    private fun toLine(
        characters: List<ParagraphCharacter>,
        defaultTextRasterizer: PixelTextRasterizer,
        includeTrailingLineSpacing: Boolean,
    ): PixelParagraphLine {
        val runs = mutableListOf<PixelParagraphRun>()
        val builder = StringBuilder()
        var currentStyle: PixelTextStyle? = null
        characters.forEach { character ->
            if (currentStyle != null && currentStyle != character.style) {
                runs += builder.toRun(
                    style = currentStyle ?: PixelTextStyle.Default,
                    defaultTextRasterizer = defaultTextRasterizer,
                )
                builder.clear()
            }
            currentStyle = character.style
            builder.append(character.value)
        }
        if (builder.isNotEmpty() || characters.isEmpty()) {
            runs += builder.toRun(
                style = currentStyle ?: PixelTextStyle.Default,
                defaultTextRasterizer = defaultTextRasterizer,
            )
        }
        val height = characters.maxOfOrNull { character ->
            val rasterizer = character.style.textRasterizer ?: defaultTextRasterizer
            measureLineHeight(
                rasterizer = rasterizer,
                text = character.value.toString(),
                style = character.style,
                includeTrailingLineSpacing = includeTrailingLineSpacing,
            )
        } ?: measureLineHeight(
            rasterizer = defaultTextRasterizer,
            text = " ",
            style = PixelTextStyle.Default,
            includeTrailingLineSpacing = includeTrailingLineSpacing,
        )
        return PixelParagraphLine(
            runs = runs,
            width = runs.sumOf { run -> run.width },
            height = height,
            sourceStart = characters.minOfOrNull { it.sourceIndex } ?: 0,
            sourceEnd = (characters.maxOfOrNull { it.sourceIndex } ?: -1) + 1,
        )
    }

    private fun measureLineHeight(
        rasterizer: PixelTextRasterizer,
        text: String,
        style: PixelTextStyle,
        includeTrailingLineSpacing: Boolean,
    ): Int {
        style.lineHeight?.let { return it.coerceAtLeast(1) }

        val sampleText = text.ifEmpty { " " }
        val glyphHeight = (rasterizer.measureHeight(sampleText).coerceAtLeast(1) * style.safeFontScale())
            .coerceAtLeast(1)
        if (!includeTrailingLineSpacing) {
            return glyphHeight
        }
        if (!style.usesPlainMultilineRasterizer()) {
            return glyphHeight + style.lineSpacing.coerceAtLeast(0)
        }

        val twoLineHeight = rasterizer.measureHeight("$sampleText\n$sampleText")
            .coerceAtLeast(glyphHeight)
        return (twoLineHeight - glyphHeight).coerceAtLeast(glyphHeight)
    }

    private fun StringBuilder.toRun(
        style: PixelTextStyle,
        defaultTextRasterizer: PixelTextRasterizer,
    ): PixelParagraphRun {
        val text = toString()
        return PixelParagraphRun(
            text = text,
            style = style,
            width = measureStyledText(
                text = text,
                style = style,
                defaultTextRasterizer = defaultTextRasterizer,
            ),
        )
    }

    private fun measureWidth(
        characters: List<ParagraphCharacter>,
        defaultTextRasterizer: PixelTextRasterizer,
    ): Int {
        if (characters.isEmpty()) {
            return 0
        }
        var width = 0
        val builder = StringBuilder()
        var currentStyle: PixelTextStyle? = null
        characters.forEach { character ->
            if (currentStyle != null && currentStyle != character.style) {
                width += measureStyledText(
                    text = builder.toString(),
                    style = currentStyle ?: PixelTextStyle.Default,
                    defaultTextRasterizer = defaultTextRasterizer,
                )
                builder.clear()
            }
            currentStyle = character.style
            builder.append(character.value)
        }
        if (builder.isNotEmpty()) {
            width += measureStyledText(
                text = builder.toString(),
                style = currentStyle ?: PixelTextStyle.Default,
                defaultTextRasterizer = defaultTextRasterizer,
            )
        }
        return width
    }

    private fun measureStyledText(
        text: String,
        style: PixelTextStyle,
        defaultTextRasterizer: PixelTextRasterizer,
    ): Int {
        if (text.isEmpty()) {
            return 0
        }
        val rasterizer = style.textRasterizer ?: defaultTextRasterizer
        if (style.usesPlainRasterizer()) {
            return rasterizer.measureText(text)
        }
        val scale = style.safeFontScale()
        val spacing = style.letterSpacing.coerceAtLeast(0)
        return text.sumOf { character ->
            (rasterizer.measureText(character.toString()) * scale) + spacing
        }
    }

    private fun PixelTextStyle.safeFontScale(): Int {
        return fontScale.coerceAtLeast(1)
    }

    private fun PixelTextStyle.usesPlainRasterizer(): Boolean {
        return letterSpacing <= 0 && fontScale <= 1
    }

    private fun PixelTextStyle.usesPlainMultilineRasterizer(): Boolean {
        return letterSpacing <= 0 && fontScale <= 1 && lineSpacing <= 0
    }

    private fun flattenSpans(spans: List<PixelTextSpan>): List<ParagraphCharacter> {
        var sourceIndex = 0
        return spans.flatMap { span ->
            span.text.map { character ->
                ParagraphCharacter(value = character, style = span.style, sourceIndex = sourceIndex++)
            }
        }
    }

    private data class ParagraphCharacter(
        val value: Char,
        val style: PixelTextStyle,
        val sourceIndex: Int,
    )
}
