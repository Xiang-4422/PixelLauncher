package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelTextOverflow
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
    private val textLayoutSupport = PixelTextLayoutSupport()
    private val textAlignmentSupport = PixelTextAlignmentSupport()

    /**
     * 按当前文本布局结果把文本绘制到 buffer。
     */
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
                val lineX = textAlignmentSupport.lineStartX(
                    left = bounds.left,
                    width = bounds.width,
                    lineWidth = line.width,
                    textAlign = node.textAlign,
                    textDirection = node.textDirection,
                )
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

    /**
     * 计算 legacy 文本节点的布局结果。
     */
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
                textLayoutSupport.appendWrappedLines(
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
            visibleLines[index] = textLayoutSupport.fitTextToWidth(
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

    /**
     * 判断指定对齐方式是否需要占满整行宽度。
     */
    fun textAlignNeedsFullWidth(node: PixelTextNode): Boolean {
        return textAlignmentSupport.needsFullWidth(
            textAlign = node.textAlign,
            textDirection = node.textDirection,
        )
    }

    /**
     * 解析文本输入节点要使用的栅格器。
     */
    fun resolveTextFieldRasterizer(node: PixelTextFieldNode): PixelTextRasterizer {
        return node.style.textStyle.textRasterizer ?: defaultTextRasterizer
    }

    /**
     * 对外暴露文本裁剪能力，供文本输入等相邻 support 复用。
     */
    fun clipTextToWidth(
        text: String,
        maxWidth: Int,
        rasterizer: PixelTextRasterizer,
    ): String {
        return textLayoutSupport.clipTextToWidth(
            text = text,
            maxWidth = maxWidth,
            rasterizer = rasterizer,
        )
    }

    /**
     * 解析普通文本节点要使用的栅格器。
     */
    private fun resolveTextRasterizer(node: PixelTextNode): PixelTextRasterizer {
        return node.style.textRasterizer ?: defaultTextRasterizer
    }
}
