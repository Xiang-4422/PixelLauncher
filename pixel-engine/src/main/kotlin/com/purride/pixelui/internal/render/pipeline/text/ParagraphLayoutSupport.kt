package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.TextDirection

/**
 * 文本段落测量的内部工具。
 *
 * 这层只承载 `RenderText` 和 `RenderRichText` 共享的行对齐、字符级换行和
 * ellipsis 规则，不作为页面层 API。
 */
internal object ParagraphLayoutSupport {
    const val Ellipsis = "..."

    fun resolveLineStartX(
        textAlign: PixelTextAlign,
        textDirection: TextDirection,
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

    fun resolvePlainTextLines(
        text: String,
        rasterizer: PixelTextRasterizer,
        availableWidth: Int,
        softWrap: Boolean,
        overflow: PixelTextOverflow,
        maxLines: Int,
    ): List<String> {
        if (maxLines <= 0 || availableWidth <= 0 || text.isEmpty()) {
            return emptyList()
        }
        if (!softWrap) {
            val singleLineText = text.lineSequence().firstOrNull().orEmpty()
            if (singleLineText.isEmpty()) {
                return emptyList()
            }
            return listOf(
                if (overflow == PixelTextOverflow.CLIP || rasterizer.measureText(singleLineText) <= availableWidth) {
                    singleLineText
                } else {
                    ellipsizePlainText(
                        text = singleLineText,
                        rasterizer = rasterizer,
                        availableWidth = availableWidth,
                    )
                },
            ).filter(String::isNotEmpty)
        }

        val wrappedLines = wrapPlainTextByCharacter(
            text = text,
            rasterizer = rasterizer,
            availableWidth = availableWidth,
        )
        if (wrappedLines.isEmpty()) {
            return emptyList()
        }
        val truncated = wrappedLines.size > maxLines
        val visibleLines = wrappedLines.take(maxLines).toMutableList()
        if (truncated && overflow == PixelTextOverflow.ELLIPSIS && visibleLines.isNotEmpty()) {
            visibleLines[visibleLines.lastIndex] = ellipsizePlainText(
                text = visibleLines.last(),
                rasterizer = rasterizer,
                availableWidth = availableWidth,
            )
        }
        return visibleLines
    }

    fun wrapPlainTextByCharacter(
        text: String,
        rasterizer: PixelTextRasterizer,
        availableWidth: Int,
    ): List<String> {
        val lines = mutableListOf<String>()
        text.lines().forEach { paragraph ->
            if (paragraph.isEmpty()) {
                lines += ""
                return@forEach
            }
            val builder = StringBuilder()
            paragraph.forEach { character ->
                val candidate = builder.toString() + character
                if (builder.isNotEmpty() && rasterizer.measureText(candidate) > availableWidth) {
                    lines += builder.toString()
                    builder.clear()
                }
                builder.append(character)
            }
            if (builder.isNotEmpty()) {
                lines += builder.toString()
            }
        }
        return lines
    }

    fun ellipsizePlainText(
        text: String,
        rasterizer: PixelTextRasterizer,
        availableWidth: Int,
    ): String {
        if (rasterizer.measureText(Ellipsis) > availableWidth) {
            return ""
        }
        val builder = StringBuilder()
        text.forEach { character ->
            val candidate = builder.toString() + character + Ellipsis
            if (rasterizer.measureText(candidate) > availableWidth) {
                return builder.toString() + Ellipsis
            }
            builder.append(character)
        }
        return builder.toString()
    }
}
