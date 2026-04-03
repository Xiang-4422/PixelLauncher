package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelTextOverflow

/**
 * 负责 legacy 文本节点的换行、裁剪和省略布局细节。
 */
internal class PixelTextLayoutSupport {
    /**
     * 按给定宽度把源文本追加为多行。
     */
    fun appendWrappedLines(
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

    /**
     * 按宽度约束裁剪文本，并在需要时附加省略号。
     */
    fun fitTextToWidth(
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

    /**
     * 按宽度截断文本，不附加额外装饰。
     */
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
