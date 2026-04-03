package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.TextDirection
import com.purride.pixelui.internal.legacy.PixelTextAlign
import com.purride.pixelui.internal.legacy.PixelTextFieldNode
import com.purride.pixelui.internal.legacy.PixelTextNode

internal data class PixelTextLayoutLine(
    val text: String,
    val width: Int,
)

internal data class PixelTextLayout(
    val lines: List<PixelTextLayoutLine>,
    val width: Int,
    val height: Int,
    val lineHeight: Int,
    val lineSpacing: Int,
    val rasterizer: PixelTextRasterizer,
)

internal class PixelTextRenderSupport(
    private val defaultTextRasterizer: PixelTextRasterizer,
) {
    fun renderText(
        node: PixelTextNode,
        bounds: PixelRect,
        buffer: PixelBuffer,
    ) {
        val layout = layoutText(
            node = node,
            maxWidth = bounds.width,
        )
        var cursorY = bounds.top
        layout.lines.forEach { line ->
            if (line.text.isNotEmpty()) {
                val lineX = when (node.textAlign) {
                    PixelTextAlign.START -> when (node.textDirection) {
                        TextDirection.LTR -> bounds.left
                        TextDirection.RTL -> bounds.left + (bounds.width - line.width).coerceAtLeast(0)
                    }

                    PixelTextAlign.CENTER -> bounds.left + ((bounds.width - line.width).coerceAtLeast(0) / 2)
                    PixelTextAlign.END -> when (node.textDirection) {
                        TextDirection.LTR -> bounds.left + (bounds.width - line.width).coerceAtLeast(0)
                        TextDirection.RTL -> bounds.left
                    }
                }
                layout.rasterizer.drawText(
                    buffer = buffer,
                    text = line.text,
                    x = lineX,
                    y = cursorY,
                    value = node.style.tone.value,
                )
            }
            cursorY += layout.lineHeight + layout.lineSpacing
        }
    }

    fun layoutText(
        node: PixelTextNode,
        maxWidth: Int,
    ): PixelTextLayout {
        val rasterizer = resolveTextRasterizer(node)
        val constrainedWidth = maxWidth.coerceAtLeast(0)
        val effectiveMaxLines = node.maxLines.coerceAtLeast(1)
        val lineHeight = rasterizer.measureHeight(" ")
        val lineSpacing = node.style.lineSpacing.coerceAtLeast(0)
        val sourceLines = node.text.ifEmpty { "" }.split('\n')
        val laidOutLines = mutableListOf<String>()

        sourceLines.forEach { sourceLine ->
            if (laidOutLines.size >= effectiveMaxLines) {
                return@forEach
            }

            if (node.softWrap && constrainedWidth > 0) {
                appendWrappedLines(
                    sourceLine = sourceLine,
                    maxWidth = constrainedWidth,
                    maxLines = effectiveMaxLines,
                    target = laidOutLines,
                    rasterizer = rasterizer,
                )
            } else {
                laidOutLines += sourceLine
            }
        }

        if (laidOutLines.isEmpty()) {
            laidOutLines += ""
        }

        val visibleLines = laidOutLines.take(effectiveMaxLines).toMutableList()
        val truncatedByLineCount = laidOutLines.size > effectiveMaxLines
        visibleLines.indices.forEach { index ->
            val shouldEllipsize = truncatedByLineCount && index == visibleLines.lastIndex
            visibleLines[index] = fitTextToWidth(
                text = visibleLines[index],
                maxWidth = constrainedWidth,
                overflow = node.overflow,
                rasterizer = rasterizer,
                forceEllipsis = shouldEllipsize,
            )
        }

        val measuredLines = visibleLines.map { line ->
            PixelTextLayoutLine(
                text = line,
                width = rasterizer.measureText(line),
            )
        }
        val measuredWidth = measuredLines.maxOfOrNull { it.width } ?: 0
        val width = if (constrainedWidth > 0) {
            measuredWidth.coerceAtMost(constrainedWidth)
        } else {
            measuredWidth
        }
        val height = if (measuredLines.isEmpty()) {
            0
        } else {
            (measuredLines.size * lineHeight) + ((measuredLines.size - 1) * lineSpacing)
        }

        return PixelTextLayout(
            lines = measuredLines,
            width = width,
            height = height,
            lineHeight = lineHeight,
            lineSpacing = lineSpacing,
            rasterizer = rasterizer,
        )
    }

    fun textAlignNeedsFullWidth(node: PixelTextNode): Boolean {
        return when (node.textAlign) {
            PixelTextAlign.CENTER -> true
            PixelTextAlign.START -> node.textDirection == TextDirection.RTL
            PixelTextAlign.END -> node.textDirection == TextDirection.LTR
        }
    }

    fun resolveTextFieldRasterizer(node: PixelTextFieldNode): PixelTextRasterizer {
        return node.style.textStyle.textRasterizer ?: defaultTextRasterizer
    }

    private fun resolveTextRasterizer(node: PixelTextNode): PixelTextRasterizer {
        return node.style.textRasterizer ?: defaultTextRasterizer
    }

    private fun appendWrappedLines(
        sourceLine: String,
        maxWidth: Int,
        maxLines: Int,
        target: MutableList<String>,
        rasterizer: PixelTextRasterizer,
    ) {
        if (target.size >= maxLines) {
            return
        }
        if (sourceLine.isEmpty()) {
            target += ""
            return
        }

        var currentLine = StringBuilder()
        sourceLine.forEach { character ->
            val candidate = currentLine.toString() + character
            if (rasterizer.measureText(candidate) <= maxWidth || currentLine.isEmpty()) {
                currentLine.append(character)
            } else {
                target += currentLine.toString()
                if (target.size >= maxLines) {
                    return
                }
                currentLine = StringBuilder().append(character)
            }
        }

        if (target.size < maxLines) {
            target += currentLine.toString()
        }
    }

    private fun fitTextToWidth(
        text: String,
        maxWidth: Int,
        overflow: PixelTextOverflow,
        rasterizer: PixelTextRasterizer,
        forceEllipsis: Boolean,
    ): String {
        if (maxWidth <= 0 || text.isEmpty()) {
            return ""
        }
        if (rasterizer.measureText(text) <= maxWidth && !forceEllipsis) {
            return text
        }

        if (overflow == PixelTextOverflow.ELLIPSIS || forceEllipsis) {
            val ellipsis = "..."
            if (rasterizer.measureText(ellipsis) > maxWidth) {
                return clipTextToWidth(text, maxWidth, rasterizer)
            }

            val builder = StringBuilder(text)
            while (builder.isNotEmpty() &&
                rasterizer.measureText(builder.toString() + ellipsis) > maxWidth
            ) {
                builder.deleteCharAt(builder.lastIndex)
            }
            return if (builder.isEmpty()) {
                clipTextToWidth(ellipsis, maxWidth, rasterizer)
            } else {
                builder.toString() + ellipsis
            }
        }

        return clipTextToWidth(
            text = text,
            maxWidth = maxWidth,
            rasterizer = rasterizer,
        )
    }

    fun clipTextToWidth(
        text: String,
        maxWidth: Int,
        rasterizer: PixelTextRasterizer,
    ): String {
        if (maxWidth <= 0) {
            return ""
        }

        val builder = StringBuilder()
        text.forEach { character ->
            val candidate = builder.toString() + character
            if (rasterizer.measureText(candidate) <= maxWidth) {
                builder.append(character)
            } else {
                return builder.toString()
            }
        }
        return builder.toString()
    }
}
