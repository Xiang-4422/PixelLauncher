package com.purride.pixelui.state

import com.purride.pixelcore.AxisMotionController
import com.purride.pixelcore.PixelAxis
import com.purride.pixelui.ChangeNotifier
import kotlin.math.abs

/**
 * 通用分页控制器。
 *
 * 它基于 pixel-engine core layer 提供的轴向位移原语，负责真正的分页语义：
 * 当前页、页数、翻页阈值、速度翻页和边界夹紧。
 *
 * 监听变化：本类继承 [ChangeNotifier]，可直接
 * `controller.addListener { /* on changed */ }` 注册回调，或用
 * `controller.observe { ... }` 扩展拿到句柄方便后续 removeListener。
 */
public class PixelPagerController(
    private val distanceThresholdFraction: Float = 0.4f,
    private val velocityThresholdPagesPerSecond: Float = 0.35f,
    private val motionController: AxisMotionController = AxisMotionController(),
) : ChangeNotifier() {
    /** 创建一个 pager 状态，并把页码夹紧到可用页数内。 */
    public fun create(
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

    /** 同步轴向和页数，通常由 PageView layout 阶段调用。 */
    public fun sync(
        state: PixelPagerState,
        axis: PixelAxis,
        pageCount: Int,
    ) {
        state.axis = axis
        state.pageCount = pageCount.coerceAtLeast(1)
        state.currentPage = state.currentPage.coerceIn(0, state.pageCount - 1)
        state.settleTargetPage = state.settleTargetPage.coerceIn(0, state.pageCount - 1)
    }

    /** 立即跳到指定页并取消正在进行的拖动/吸附。 */
    public fun syncToPage(state: PixelPagerState, targetPage: Int) {
        val safeTargetPage = targetPage.coerceIn(0, state.pageCount - 1)
        state.currentPage = safeTargetPage
        state.settleTargetPage = safeTargetPage
        state.motionState = motionController.reset()
        state.dragStartOffsetPx = 0f
        notifyListeners()
    }

    /** 创建可保存到 Bundle 的当前页快照。 */
    public fun saveState(state: PixelPagerState): PixelPagerSavedState {
        return PixelPagerSavedState(
            currentPage = state.currentPage.coerceIn(0, state.pageCount - 1),
            axis = state.axis,
        )
    }

    /** 恢复保存过的页位置，并按当前页数夹紧。 */
    public fun restoreState(
        state: PixelPagerState,
        savedState: PixelPagerSavedState,
        pageCount: Int = state.pageCount,
        axis: PixelAxis = savedState.axis,
    ) {
        sync(state = state, axis = axis, pageCount = pageCount)
        val safePage = savedState.currentPage.coerceIn(0, state.pageCount - 1)
        state.currentPage = safePage
        state.settleTargetPage = safePage
        state.lastDispatchedPage = safePage
        state.motionState = motionController.reset()
        state.dragStartOffsetPx = 0f
        notifyListeners()
    }

    /** 开始一次 pager 拖动；若正在 settling，会先接管当前视觉偏移。 */
    public fun startDrag(state: PixelPagerState, viewportSizePx: Int) {
        val safeViewportSizePx = viewportSizePx.coerceAtLeast(1).toFloat()
        var dragStartOffset = motionController.visualOffsetPx(state.motionState)
        if (state.motionState.isSettling) {
            val targetPage = state.settleTargetPage.coerceIn(0, state.pageCount - 1)
            val pageDelta = targetPage - state.currentPage
            state.currentPage = targetPage
            dragStartOffset += pageDelta * safeViewportSizePx
        }
        state.settleTargetPage = state.currentPage
        state.dragStartOffsetPx = dragStartOffset
        state.motionState = motionController.startDrag(
            state.motionState.copy(dragOffsetPx = dragStartOffset),
        )
        notifyListeners()
    }

    /** 按触摸主轴增量更新当前拖动偏移。 */
    public fun dragBy(
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
        notifyListeners()
    }

    /** 结束拖动，并按距离/速度阈值决定目标页。 */
    public fun endDrag(
        state: PixelPagerState,
        viewportSizePx: Int,
        velocityPxPerSecond: Float,
    ) {
        val safeViewportSizePx = viewportSizePx.coerceAtLeast(1).toFloat()
        val distanceThreshold = safeViewportSizePx * distanceThresholdFraction
        val velocityThreshold = safeViewportSizePx * velocityThresholdPagesPerSecond
        val offsetPx = motionController.visualOffsetPx(state.motionState)
        val gestureOffsetPx = offsetPx - state.dragStartOffsetPx
        // 非有限速度（NaN / Infinity）显式归零，避免依赖 IEEE 754 比较语义。
        val sanitizedVelocity = if (velocityPxPerSecond.isFinite()) velocityPxPerSecond else 0f
        val direction = resolveDirection(
            offsetPx = gestureOffsetPx,
            distanceThreshold = distanceThreshold,
            velocityPxPerSecond = sanitizedVelocity,
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
            state.dragStartOffsetPx = 0f
        }
        notifyListeners()
    }

    /** 取消拖动并吸附回当前页。 */
    public fun cancelDrag(state: PixelPagerState) {
        state.settleTargetPage = state.currentPage
        state.motionState = motionController.settleTo(
            state = state.motionState,
            targetOffsetPx = 0f,
        )
        if (!state.motionState.isSettling) {
            state.motionState = motionController.reset()
            state.dragStartOffsetPx = 0f
        }
        notifyListeners()
    }

    /** 推进 settling 动画，静止时不通知监听者。 */
    public fun step(state: PixelPagerState, deltaMs: Long) {
        // 没有 settling 动画在跑时，step 不会改变任何状态。此处提前返回、
        // 不调用 notifyListeners()，避免静止的 pager 每帧都触发监听者重建 →
        // 宿主 postInvalidateOnAnimation 永不停的空转重绘循环。
        if (!state.motionState.isSettling) {
            return
        }
        state.motionState = motionController.step(state.motionState, deltaMs)
        if (!state.motionState.isSettling) {
            state.currentPage = state.settleTargetPage.coerceIn(0, state.pageCount - 1)
            state.motionState = motionController.reset()
            state.dragStartOffsetPx = 0f
        }
        notifyListeners()
    }

    /** 返回当前渲染帧所需的 anchor/adjacent page 信息。 */
    public fun snapshot(state: PixelPagerState): PixelPagerSnapshot {
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

    /** 当前 pager 是否仍有拖动或吸附动画未结束。 */
    public fun isActive(state: PixelPagerState): Boolean = motionController.isActive(state.motionState)

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

    public companion object {
        private const val DRAG_EPSILON_PX = 0.5f
    }
}
