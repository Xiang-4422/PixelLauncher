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

    fun firstRenderableIndex(listStartIndex: Int): Int {
        return (listStartIndex - 1).coerceAtLeast(0)
    }

    fun rowTop(
        viewport: TextListViewport,
        rowIndex: Int,
        listStartIndex: Int,
        scrollOffsetPx: Int,
    ): Int {
        val relativeRowIndex = rowIndex - listStartIndex
        return viewport.top + (relativeRowIndex * viewport.rowHeight) + scrollOffsetPx
    }

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

    fun hasScrollableContent(rowCount: Int, viewport: TextListViewport): Boolean {
        return rowCount > viewport.visibleRows
    }

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
