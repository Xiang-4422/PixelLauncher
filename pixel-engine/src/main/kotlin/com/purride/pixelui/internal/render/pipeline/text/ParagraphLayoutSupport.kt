package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelGraphemeBoundaryMap
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.TextDirection

/**
 * 文本段落测量的内部工具。
 *
 * 这层只承载 `RenderText` 和 `RenderRichText` 共享的行对齐、字素簇换行和
 * ellipsis 规则，不作为页面层 API。
 */
internal object ParagraphLayoutSupport {
    /** 1.0 兼容的可见三点省略号。 */
    const val Ellipsis = "..."

    /** 根据逻辑方向把 START/END 对齐解析为当前行的物理 x 坐标。 */
    fun resolveLineStartX(
        /** 当前段落的逻辑对齐方式。 */
        textAlign: PixelTextAlign,
        /** START/END 使用的基础方向。 */
        textDirection: TextDirection,
        /** 当前行可用的完整宽度。 */
        availableWidth: Int,
        /** 当前行的已测量视觉宽度。 */
        lineWidth: Int,
    ): Int {
        /** 对齐可分配的非负剩余空间。 */
        val freeWidth = (availableWidth - lineWidth).coerceAtLeast(0)
        return when (textAlign) {
            PixelTextAlign.CENTER -> freeWidth / 2
            PixelTextAlign.END -> if (textDirection == TextDirection.RTL) 0 else freeWidth
            PixelTextAlign.START -> if (textDirection == TextDirection.RTL) freeWidth else 0
        }
    }

    /** 为仍使用旧内部 helper 的调用方返回 cluster-safe 可见文本行。 */
    fun resolvePlainTextLines(
        /** 保持原样且不做 Unicode normalization 的 backing text。 */
        text: String,
        /** 为候选 cluster 序列提供宽度的文本栅格器。 */
        rasterizer: PixelTextRasterizer,
        /** 单行最大可用宽度。 */
        availableWidth: Int,
        /** 是否允许在完整 grapheme cluster 之间软换行。 */
        softWrap: Boolean,
        /** 超出可见范围时使用的裁剪策略。 */
        overflow: PixelTextOverflow,
        /** 最多返回的可见行数。 */
        maxLines: Int,
    ): List<String> {
        if (maxLines <= 0 || availableWidth <= 0 || text.isEmpty()) {
            return emptyList()
        }
        if (!softWrap) {
            /** 第一个 Unicode hard-break 之前的完整 cluster 序列。 */
            val singleLineText = splitHardLines(text).firstOrNull().orEmpty()
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

        /** 在完整 cluster 边界形成的所有软行。 */
        val wrappedLines = wrapPlainTextByGrapheme(
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

    /** 在 Unicode 17 extended grapheme cluster 边界换行，并保留所有物理空行。 */
    fun wrapPlainTextByGrapheme(
        /** 待换行的精确 UTF-16 source。 */
        text: String,
        /** 测量完整 cluster 候选序列的栅格器。 */
        rasterizer: PixelTextRasterizer,
        /** 每一软行的最大宽度。 */
        availableWidth: Int,
    ): List<String> {
        /** 包含首尾和连续空行的最终文本行。 */
        val lines = mutableListOf<String>()
        splitHardLines(text).forEach { paragraph ->
            if (paragraph.isEmpty()) {
                lines += ""
                return@forEach
            }
            /** 当前软行的完整 cluster 文本。 */
            val builder = StringBuilder()
            forEachGrapheme(paragraph) { cluster ->
                /** 加入下一个完整 cluster 后的测量候选。 */
                val candidate = builder.toString() + cluster
                if (builder.isNotEmpty() && rasterizer.measureText(candidate) > availableWidth) {
                    lines += builder.toString()
                    builder.clear()
                }
                builder.append(cluster)
            }
            if (builder.isNotEmpty()) {
                lines += builder.toString()
            }
        }
        return lines
    }

    /** 在完整 grapheme cluster 边界截断并追加历史兼容的三点省略号。 */
    fun ellipsizePlainText(
        /** 不允许被拆分的精确 backing text。 */
        text: String,
        /** 测量 source prefix 与省略号组合的栅格器。 */
        rasterizer: PixelTextRasterizer,
        /** 可绘制的最大宽度。 */
        availableWidth: Int,
    ): String {
        if (rasterizer.measureText(Ellipsis) > availableWidth) {
            return ""
        }
        /** 已确认能够与省略号共同放入宽度的完整 cluster prefix。 */
        val builder = StringBuilder()
        forEachGrapheme(text) { cluster ->
            /** 仅在完整 cluster 之后尝试省略号的候选。 */
            val candidate = builder.toString() + cluster + Ellipsis
            if (rasterizer.measureText(candidate) > availableWidth) {
                return builder.toString() + Ellipsis
            }
            builder.append(cluster)
        }
        return builder.toString()
    }

    /** 按 LF/CR/CRLF/NEL/LS/PS 拆分，并保留首尾及连续空行。 */
    private fun splitHardLines(text: String): List<String> {
        if (text.isEmpty()) return listOf("")
        /** 所有物理行，包括 hard break 两侧的空文本。 */
        val lines = mutableListOf<String>()
        /** 当前物理行的非 break cluster。 */
        val line = StringBuilder()
        forEachGrapheme(text) { cluster ->
            if (PixelParagraphClusterSupport.isHardLineBreak(cluster)) {
                lines += line.toString()
                line.clear()
            } else {
                line.append(cluster)
            }
        }
        lines += line.toString()
        return lines
    }

    /** 以递增 source range 遍历 Unicode 17 extended grapheme cluster。 */
    private inline fun forEachGrapheme(text: String, block: (String) -> Unit) {
        /** 固定 Unicode 17 规则的边界查询对象。 */
        val boundaries = PixelGraphemeBoundaryMap(text)
        /** 下一个 cluster 的 UTF-16 起点。 */
        var start = 0
        while (start < text.length) {
            /** 当前完整 cluster 的 UTF-16 终点。 */
            val end = boundaries.next(start)
            block(text.substring(start, end))
            start = end
        }
    }
}
