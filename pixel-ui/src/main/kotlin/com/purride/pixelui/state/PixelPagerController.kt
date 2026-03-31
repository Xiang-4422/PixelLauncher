package com.purride.pixelui.state

import com.purride.pixelcore.AxisMotionController
import com.purride.pixelcore.PixelAxis
import kotlin.math.abs

/**
 * 通用分页控制器。
 *
 * 它基于 `:pixel-core` 提供的轴向位移原语，负责真正的分页语义：
 * 当前页、页数、翻页阈值、速度翻页和边界夹紧。
 */
class PixelPagerController(
    private val distanceThresholdFraction: Float = 0.4f,
    private val velocityThresholdPagesPerSecond: Float = 0.35f,
    private val motionController: AxisMotionController = AxisMotionController(),
) {
    fun create(
        pageCount: Int,
        currentPage: Int = 0,
        axis: PixelAxis = PixelAxis.HORIZONTAL,
    ): PixelPagerState {
        return PixelPagerState(
            axis = axis,
            currentPage = currentPage.coerceIn(0, pageCount.coerceAtLeast(1) - 1),
            pageCount = pageCount.coerceAtLeast(1),
        ).also { state ->
            state.settleTargetPage = state.currentPage
            state.motionState = motionController.create()
        }
    }

    fun sync(
        state: PixelPagerState,
        axis: PixelAxis,
        pageCount: Int,
    ) {
        state.axis = axis
        state.pageCount = pageCount.coerceAtLeast(1)
        state.currentPage = state.currentPage.coerceIn(0, state.pageCount - 1)
        state.settleTargetPage = state.settleTargetPage.coerceIn(0, state.pageCount - 1)
    }

    fun syncToPage(state: PixelPagerState, targetPage: Int) {
        val safeTargetPage = targetPage.coerceIn(0, state.pageCount - 1)
        state.currentPage = safeTargetPage
        state.settleTargetPage = safeTargetPage
        state.motionState = motionController.reset()
    }

    fun startDrag(state: PixelPagerState) {
        state.settleTargetPage = state.currentPage
        state.motionState = motionController.startDrag(state.motionState)
    }

    fun dragBy(
        state: PixelPagerState,
        deltaPx: Float,
        viewportSizePx: Int,
    ) {
        val safeViewportSizePx = viewportSizePx.coerceAtLeast(1).toFloat()
        val minOffset = if (state.currentPage < state.pageCount - 1) -safeViewportSizePx else 0f
        val maxOffset = if (state.currentPage > 0) safeViewportSizePx else 0f
        state.motionState = motionController.dragBy(
            state = state.motionState,
            deltaPx = deltaPx,
            minOffsetPx = minOffset,
            maxOffsetPx = maxOffset,
        )
    }

    fun endDrag(
        state: PixelPagerState,
        viewportSizePx: Int,
        velocityPxPerSecond: Float,
    ) {
        val safeViewportSizePx = viewportSizePx.coerceAtLeast(1).toFloat()
        val distanceThreshold = safeViewportSizePx * distanceThresholdFraction
        val velocityThreshold = safeViewportSizePx * velocityThresholdPagesPerSecond
        val offsetPx = motionController.visualOffsetPx(state.motionState)
        val direction = resolveDirection(
            offsetPx = offsetPx,
            distanceThreshold = distanceThreshold,
            velocityPxPerSecond = velocityPxPerSecond,
            velocityThreshold = velocityThreshold,
        )
        val targetPage = when {
            direction > 0 -> (state.currentPage - 1).coerceAtLeast(0)
            direction < 0 -> (state.currentPage + 1).coerceAtMost(state.pageCount - 1)
            else -> state.currentPage
        }
        val targetOffset = when {
            targetPage > state.currentPage -> -safeViewportSizePx
            targetPage < state.currentPage -> safeViewportSizePx
            else -> 0f
        }

        state.settleTargetPage = targetPage
        state.motionState = motionController.settleTo(
            state = state.motionState,
            targetOffsetPx = targetOffset,
        )
        if (!state.motionState.isSettling) {
            state.currentPage = targetPage
            state.motionState = motionController.reset()
        }
    }

    fun cancelDrag(state: PixelPagerState) {
        state.settleTargetPage = state.currentPage
        state.motionState = motionController.settleTo(
            state = state.motionState,
            targetOffsetPx = 0f,
        )
        if (!state.motionState.isSettling) {
            state.motionState = motionController.reset()
        }
    }

    fun step(state: PixelPagerState, deltaMs: Long) {
        val wasSettling = state.motionState.isSettling
        state.motionState = motionController.step(state.motionState, deltaMs)
        if (wasSettling && !state.motionState.isSettling) {
            state.currentPage = state.settleTargetPage.coerceIn(0, state.pageCount - 1)
            state.motionState = motionController.reset()
        }
    }

    fun snapshot(state: PixelPagerState): PixelPagerSnapshot {
        val offsetPx = motionController.visualOffsetPx(state.motionState)
        val adjacentPage = when {
            offsetPx > DRAG_EPSILON_PX && state.currentPage > 0 -> state.currentPage - 1
            offsetPx < -DRAG_EPSILON_PX && state.currentPage < state.pageCount - 1 -> state.currentPage + 1
            else -> null
        }
        return PixelPagerSnapshot(
            axis = state.axis,
            anchorPage = state.currentPage,
            adjacentPage = adjacentPage,
            pageCount = state.pageCount,
            dragOffsetPx = offsetPx,
        )
    }

    fun isActive(state: PixelPagerState): Boolean = motionController.isActive(state.motionState)

    private fun resolveDirection(
        offsetPx: Float,
        distanceThreshold: Float,
        velocityPxPerSecond: Float,
        velocityThreshold: Float,
    ): Int {
        return when {
            abs(offsetPx) >= distanceThreshold -> offsetPx.sign()
            abs(velocityPxPerSecond) >= velocityThreshold -> velocityPxPerSecond.sign()
            else -> 0
        }
    }

    private fun Float.sign(): Int = when {
        this > 0f -> 1
        this < 0f -> -1
        else -> 0
    }

    companion object {
        private const val DRAG_EPSILON_PX = 0.5f
    }
}
