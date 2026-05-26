package com.purride.pixelui.state

import com.purride.pixelcore.AxisMotionState
import com.purride.pixelcore.PixelAxis

/**
 * 通用分页状态。
 *
 * 分页语义明确归 pixel-engine UI layer：
 * 当前页、页数、轴向，以及吸附过程中的目标页都由这里持有。
 */
public class PixelPagerState(
    axis: PixelAxis = PixelAxis.HORIZONTAL,
    currentPage: Int = 0,
    pageCount: Int = 1,
) {
    public var axis: PixelAxis = axis
        internal set

    public var currentPage: Int = currentPage.coerceAtLeast(0)
        internal set

    public var pageCount: Int = pageCount.coerceAtLeast(1)
        internal set

    public var settleTargetPage: Int = this.currentPage
        internal set

    internal var lastDispatchedPage: Int = this.currentPage

    internal var motionState: AxisMotionState = AxisMotionState()

    public val isDragging: Boolean
        get() = motionState.isDragging

    public val isSettling: Boolean
        get() = motionState.isSettling
}

/**
 * PageView 的可持久化页位置。
 */
public data class PixelPagerSavedState(
    public val currentPage: Int,
    public val axis: PixelAxis,
)
