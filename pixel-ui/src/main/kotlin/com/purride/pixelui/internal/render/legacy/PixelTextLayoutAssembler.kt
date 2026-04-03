package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTextRasterizer

/**
 * 负责把行文本和栅格器信息组装成最终文本布局结果。
 */
internal class PixelTextLayoutAssembler {
    /**
     * 组装文本布局结果。
     */
    fun assemble(
        lines: List<String>,
        constrainedWidth: Int,
        lineHeight: Int,
        lineSpacing: Int,
        rasterizer: PixelTextRasterizer,
    ): PixelTextLayout {
        val measuredLines = lines.map { line ->
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
}
