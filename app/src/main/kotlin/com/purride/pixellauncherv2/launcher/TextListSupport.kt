package com.purride.pixellauncherv2.launcher


data class TextListViewport(
    val top: Int,
    val bottomExclusive: Int,
    val rowHeight: Int,
) {
    val height: Int = (bottomExclusive - top).coerceAtLeast(rowHeight)
    val visibleRows: Int = (height / rowHeight).coerceAtLeast(1)
}

data class TextListLayoutMetrics(
    val viewport: TextListViewport,
)

object TextListSupport {

    /**
     * 创建统一的正文列表 viewport 指标，让抽屉和设置页共享同一套列表几何语义。
     */
    fun createLayoutMetrics(
        top: Int,
        bottomExclusive: Int,
        rowHeight: Int,
    ): TextListLayoutMetrics {
        return TextListLayoutMetrics(
            viewport = TextListViewport(
                top = top,
                bottomExclusive = bottomExclusive.coerceAtLeast(top + rowHeight),
                rowHeight = rowHeight.coerceAtLeast(1),
            ),
        )
    }
}
