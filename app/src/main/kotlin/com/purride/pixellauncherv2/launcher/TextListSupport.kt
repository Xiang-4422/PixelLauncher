package com.purride.pixellauncherv2.launcher

import kotlin.math.floor

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

data class TextListRuntimeState(
    val selectedIndex: Int,
    val listStartIndex: Int,
    val residualOffsetPx: Float,
    val velocityPxPerSecond: Float,
    val settleTarget: DrawerSettleTarget?,
    val isDragging: Boolean,
    val isAnimating: Boolean,
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

    /**
     * 返回需要参与渲染的第一行索引。
     *
     * 这里会从 `listStartIndex - 1` 开始，目的是让顶部被裁剪的半行能够自然出现。
     */
    fun firstRenderableIndex(listStartIndex: Int): Int {
        return (listStartIndex - 1).coerceAtLeast(0)
    }

    /**
     * 把逻辑行索引换算成当前 viewport 中的顶部 Y 坐标。
     */
    fun rowTop(
        viewport: TextListViewport,
        rowIndex: Int,
        listStartIndex: Int,
        scrollOffsetPx: Int,
    ): Int {
        val relativeRowIndex = rowIndex - listStartIndex
        return viewport.top + (relativeRowIndex * viewport.rowHeight) + scrollOffsetPx
    }

    /**
     * 在考虑 viewport 和残余滚动偏移后，解析当前点击命中的逻辑行。
     *
     * 只要半行仍然处于裁剪窗口内，就允许被点击命中。
     */
    fun hitTestRow(
        viewport: TextListViewport,
        logicalY: Int,
        rowCount: Int,
        listStartIndex: Int,
        scrollOffsetPx: Int,
    ): Int? {
        if (rowCount <= 0) {
            return null
        }
        if (logicalY < viewport.top || logicalY >= viewport.bottomExclusive) {
            return null
        }
        val adjustedY = logicalY - viewport.top - scrollOffsetPx
        val row = floor(adjustedY.toFloat() / viewport.rowHeight.toFloat()).toInt()
        val rowIndex = listStartIndex + row
        return rowIndex.takeIf { it in 0 until rowCount }
    }

    /**
     * 判断在当前 viewport 高度下，列表是否需要滚动能力。
     */
    fun hasScrollableContent(rowCount: Int, viewport: TextListViewport): Boolean {
        return rowCount > viewport.visibleRows
    }

    /**
     * 在点击、按键、切页等显式操作前，统一清掉残余滚动状态。
     */
    fun settleBeforeExplicitAction(state: TextListRuntimeState): TextListRuntimeState {
        return state.copy(
            isDragging = false,
            isAnimating = false,
            residualOffsetPx = 0f,
            velocityPxPerSecond = 0f,
            settleTarget = null,
        )
    }
}
