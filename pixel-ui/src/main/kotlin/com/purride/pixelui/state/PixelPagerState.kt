package com.purride.pixelui.state

import com.purride.pixelcore.AxisMotionState
import com.purride.pixelcore.PixelAxis

/**
 * 通用分页状态。
 *
 * 分页语义明确归 `pixel-ui`：
 * 当前页、页数、轴向，以及吸附过程中的目标页都由这里持有。
 */
class PixelPagerState(
    axis: PixelAxis = PixelAxis.HORIZONTAL,
    currentPage: Int = 0,
    pageCount: Int = 1,
) {
    var axis: PixelAxis = axis
        internal set

    var currentPage: Int = currentPage.coerceAtLeast(0)
        internal set

    var pageCount: Int = pageCount.coerceAtLeast(1)
        internal set

    var settleTargetPage: Int = this.currentPage
        internal set

    internal var motionState: AxisMotionState = AxisMotionState()

    val isDragging: Boolean
        get() = motionState.isDragging

    val isSettling: Boolean
        get() = motionState.isSettling
}
