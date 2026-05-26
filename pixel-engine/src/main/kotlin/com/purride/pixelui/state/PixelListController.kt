package com.purride.pixelui.state

import com.purride.pixelui.ChangeNotifier
import com.purride.pixelui.PixelScrollPhysics
import kotlin.math.abs

/**
 * 通用列表控制器。
 *
 * 第一版先聚焦最小滚动能力：
 * 1. 根据内容高度和视口高度夹紧滚动范围
 * 2. 响应手指拖动更新滚动偏移
 *
 * 暂时不做惯性滚动、回弹或锚点定位，这些可以在后续迭代继续补。
 *
 * 监听变化：本类继承 [ChangeNotifier]，可直接
 * `controller.addListener { /* on changed */ }` 注册回调，或用
 * `controller.observe { ... }` 扩展拿到句柄方便后续 removeListener。
 */
public class PixelListController(
    private val physics: PixelScrollPhysics = PixelScrollPhysics.Default,
) : ChangeNotifier() {

    public fun create(initialScrollOffsetPx: Float = 0f): PixelListState {
        return PixelListState(initialScrollOffsetPx = initialScrollOffsetPx)
    }

    public fun sync(
        state: PixelListState,
        viewportHeightPx: Int,
        contentHeightPx: Int,
    ) {
        state.viewportHeightPx = viewportHeightPx.coerceAtLeast(0)
        state.contentHeightPx = contentHeightPx.coerceAtLeast(0)
        state.maxScrollOffsetPx = maxScrollOffsetPx(
            viewportHeightPx = viewportHeightPx,
            contentHeightPx = contentHeightPx,
        )
        state.scrollOffsetPx = coerceOffset(state.scrollOffsetPx, state.maxScrollOffsetPx)
        if (state.scrollOffsetPx <= 0f || state.scrollOffsetPx >= state.maxScrollOffsetPx) {
            if (state.isSettling) {
                stopSettling(state)
            }
        }
        notifyListeners()
    }

    public fun dragBy(
        state: PixelListState,
        deltaPx: Float,
        viewportHeightPx: Int,
        contentHeightPx: Int,
    ) {
        sync(
            state = state,
            viewportHeightPx = viewportHeightPx,
            contentHeightPx = contentHeightPx,
        )
        state.isDragging = true
        state.isSettling = false
        state.scrollVelocityPxPerSecond = 0f
        state.scrollOffsetPx = coerceDraggedOffset(
            targetOffsetPx = state.scrollOffsetPx - deltaPx,
            maxScrollOffsetPx = state.maxScrollOffsetPx,
        )
        notifyListeners()
    }

    public fun startDrag(state: PixelListState) {
        state.isDragging = true
        state.isSettling = false
        state.scrollVelocityPxPerSecond = 0f
        notifyListeners()
    }

    /**
     * 判断当前这次拖动是否还能被列表消费。
     *
     * `deltaPx` 使用触摸点的位移方向：
     * - 手指向下拖动时，`deltaPx > 0`，列表只有在“顶部上方还有内容”时才能继续跟手下移
     * - 手指向上拖动时，`deltaPx < 0`，列表只有在“底部下方还有内容”时才能继续向上滚
     */
    public fun canConsumeDrag(
        state: PixelListState,
        deltaPx: Float,
        viewportHeightPx: Int,
        contentHeightPx: Int,
    ): Boolean {
        sync(
            state = state,
            viewportHeightPx = viewportHeightPx,
            contentHeightPx = contentHeightPx,
        )
        return when {
            deltaPx > 0f -> state.scrollOffsetPx > 0f
            deltaPx < 0f -> state.scrollOffsetPx < state.maxScrollOffsetPx
            else -> false
        }
    }

    public fun scrollTo(
        state: PixelListState,
        targetOffsetPx: Float,
        viewportHeightPx: Int,
        contentHeightPx: Int,
    ) {
        sync(
            state = state,
            viewportHeightPx = viewportHeightPx,
            contentHeightPx = contentHeightPx,
        )
        state.scrollOffsetPx = coerceOffset(targetOffsetPx, state.maxScrollOffsetPx)
        notifyListeners()
    }

    public fun saveState(state: PixelListState): PixelListSavedState {
        return PixelListSavedState(scrollOffsetPx = state.scrollOffsetPx.coerceAtLeast(0f))
    }

    public fun restoreState(
        state: PixelListState,
        savedState: PixelListSavedState,
        viewportHeightPx: Int = state.viewportHeightPx,
        contentHeightPx: Int = state.contentHeightPx,
    ) {
        sync(
            state = state,
            viewportHeightPx = viewportHeightPx,
            contentHeightPx = contentHeightPx,
        )
        state.scrollOffsetPx = coerceOffset(savedState.scrollOffsetPx, state.maxScrollOffsetPx)
        state.isDragging = false
        state.isSettling = false
        state.scrollVelocityPxPerSecond = 0f
        notifyListeners()
    }

    public fun endDrag(
        state: PixelListState,
        velocityPxPerSecond: Float,
        viewportHeightPx: Int,
        contentHeightPx: Int,
    ) {
        sync(
            state = state,
            viewportHeightPx = viewportHeightPx,
            contentHeightPx = contentHeightPx,
        )
        state.isDragging = false

        val canScroll = state.maxScrollOffsetPx > 0f
        if (!canScroll || !velocityPxPerSecond.isFinite() || abs(velocityPxPerSecond) < physics.minFlingVelocityPxPerSecond) {
            stopSettling(state)
            return
        }

        state.isSettling = true
        state.scrollVelocityPxPerSecond = velocityPxPerSecond
        notifyListeners()
    }

    public fun step(
        state: PixelListState,
        deltaMs: Long,
        viewportHeightPx: Int,
        contentHeightPx: Int,
    ) {
        sync(
            state = state,
            viewportHeightPx = viewportHeightPx,
            contentHeightPx = contentHeightPx,
        )
        if (!state.isSettling || deltaMs <= 0L) {
            return
        }

        val deltaSeconds = deltaMs / 1000f
        val velocity = state.scrollVelocityPxPerSecond
        if (abs(velocity) < physics.minFlingVelocityPxPerSecond) {
            stopSettling(state)
            return
        }

        state.scrollOffsetPx = (state.scrollOffsetPx - (velocity * deltaSeconds))
            .let { targetOffset ->
                if (physics.bounceEnabled) {
                    coerceDraggedOffset(targetOffset, state.maxScrollOffsetPx)
                } else {
                    coerceOffset(targetOffset, state.maxScrollOffsetPx)
                }
            }

        val deceleration = if (velocity > 0f) {
            -physics.decelerationPxPerSecondSquared
        } else {
            physics.decelerationPxPerSecondSquared
        }
        val nextVelocity = velocity + (deceleration * deltaSeconds)
        state.scrollVelocityPxPerSecond = when {
            velocity > 0f && nextVelocity < 0f -> 0f
            velocity < 0f && nextVelocity > 0f -> 0f
            else -> nextVelocity
        }

        if (state.scrollOffsetPx <= 0f || state.scrollOffsetPx >= state.maxScrollOffsetPx) {
            stopSettling(state)
            return
        }
        if (abs(state.scrollVelocityPxPerSecond) < physics.minFlingVelocityPxPerSecond ||
            abs(state.scrollOffsetPx) <= physics.snapEpsilonPx ||
            abs(state.scrollOffsetPx - state.maxScrollOffsetPx) <= physics.snapEpsilonPx
        ) {
            stopSettling(state)
        }
        notifyListeners()
    }

    public fun isActive(state: PixelListState): Boolean {
        return state.isDragging || state.isSettling
    }

    /**
     * 将指定项滚动到当前视口内。
     *
     * 第一版采用“尽量少移动”的规则：
     * - 如果目标项已经完整可见，则保持当前位置
     * - 如果目标项在视口上方，则把该项顶部对齐到视口顶部
     * - 如果目标项在视口下方，则把该项底部拉回到视口底部
     */
    public fun scrollItemIntoView(
        state: PixelListState,
        itemIndex: Int,
    ) {
        if (itemIndex !in state.itemTopOffsetsPx.indices || itemIndex !in state.itemHeightsPx.indices) {
            return
        }
        if (state.viewportHeightPx <= 0) {
            return
        }

        val itemTopPx = state.itemTopOffsetsPx[itemIndex].toFloat()
        val itemBottomPx = (state.itemTopOffsetsPx[itemIndex] + state.itemHeightsPx[itemIndex]).toFloat()
        val viewportTopPx = state.scrollOffsetPx
        val viewportBottomPx = viewportTopPx + state.viewportHeightPx
        val targetOffsetPx = when {
            itemTopPx < viewportTopPx -> itemTopPx
            itemBottomPx > viewportBottomPx -> itemBottomPx - state.viewportHeightPx
            else -> state.scrollOffsetPx
        }

        // 变高 lazy list 远端定位：如果目标项尚未被真实测量，本次只能按 estimated
        // 高度算 offset，下一帧测量到位后还需要再校正一次。把意图存进 state，由
        // RenderVariableLazyListViewport.layout() 在测量完成后重入。
        state.pendingScrollIntoViewItemIndex = if (
            itemIndex in state.measuredItemHeightsPx.indices &&
            state.measuredItemHeightsPx[itemIndex] > 0
        ) {
            null
        } else if (itemIndex in state.measuredItemHeightsPx.indices) {
            itemIndex
        } else {
            // 非变高路径（measuredItemHeightsPx 为空）不需要二次微调
            null
        }

        scrollTo(
            state = state,
            targetOffsetPx = targetOffsetPx,
            viewportHeightPx = state.viewportHeightPx,
            contentHeightPx = state.contentHeightPx,
        )
    }

    private fun maxScrollOffsetPx(
        viewportHeightPx: Int,
        contentHeightPx: Int,
    ): Float {
        return (contentHeightPx - viewportHeightPx).coerceAtLeast(0).toFloat()
    }

    private fun stopSettling(state: PixelListState) {
        state.scrollOffsetPx = coerceOffset(state.scrollOffsetPx, state.maxScrollOffsetPx)
        state.isSettling = false
        state.scrollVelocityPxPerSecond = 0f
    }

    private fun coerceOffset(
        targetOffsetPx: Float,
        maxScrollOffsetPx: Float,
    ): Float {
        return targetOffsetPx.coerceIn(0f, maxScrollOffsetPx)
    }

    private fun coerceDraggedOffset(
        targetOffsetPx: Float,
        maxScrollOffsetPx: Float,
    ): Float {
        if (!physics.bounceEnabled) {
            return coerceOffset(targetOffsetPx, maxScrollOffsetPx)
        }
        val limit = physics.bounceOverscrollLimitPx.coerceAtLeast(0f)
        if (limit == 0f) {
            return coerceOffset(targetOffsetPx, maxScrollOffsetPx)
        }
        val bounded = targetOffsetPx.coerceIn(-limit, maxScrollOffsetPx + limit)
        return when {
            bounded < 0f -> bounded * physics.bounceResistance.coerceIn(0f, 1f)
            bounded > maxScrollOffsetPx -> maxScrollOffsetPx +
                ((bounded - maxScrollOffsetPx) * physics.bounceResistance.coerceIn(0f, 1f))
            else -> bounded
        }
    }
}
