package com.purride.pixelui.state

/**
 * 通用列表控制器。
 *
 * 第一版先聚焦最小滚动能力：
 * 1. 根据内容高度和视口高度夹紧滚动范围
 * 2. 响应手指拖动更新滚动偏移
 *
 * 暂时不做惯性滚动、回弹或锚点定位，这些可以在后续迭代继续补。
 */
class PixelListController {

    companion object {
        private const val SETTLE_DECELERATION_PX_PER_SECOND_SQUARED = 2400f
        private const val MIN_SETTLE_VELOCITY_PX_PER_SECOND = 12f
    }

    fun create(initialScrollOffsetPx: Float = 0f): PixelListState {
        return PixelListState(initialScrollOffsetPx = initialScrollOffsetPx)
    }

    fun sync(
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
        state.scrollOffsetPx = state.scrollOffsetPx.coerceIn(0f, state.maxScrollOffsetPx)
        if (state.scrollOffsetPx <= 0f || state.scrollOffsetPx >= state.maxScrollOffsetPx) {
            if (state.isSettling) {
                stopSettling(state)
            }
        }
    }

    fun dragBy(
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
        state.scrollOffsetPx = (state.scrollOffsetPx - deltaPx).coerceIn(0f, state.maxScrollOffsetPx)
    }

    fun startDrag(state: PixelListState) {
        state.isDragging = true
        state.isSettling = false
        state.scrollVelocityPxPerSecond = 0f
    }

    /**
     * 判断当前这次拖动是否还能被列表消费。
     *
     * `deltaPx` 使用触摸点的位移方向：
     * - 手指向下拖动时，`deltaPx > 0`，列表只有在“顶部上方还有内容”时才能继续跟手下移
     * - 手指向上拖动时，`deltaPx < 0`，列表只有在“底部下方还有内容”时才能继续向上滚
     */
    fun canConsumeDrag(
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

    fun scrollTo(
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
        state.scrollOffsetPx = targetOffsetPx.coerceIn(0f, state.maxScrollOffsetPx)
    }

    fun endDrag(
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
        if (!canScroll || kotlin.math.abs(velocityPxPerSecond) < MIN_SETTLE_VELOCITY_PX_PER_SECOND) {
            stopSettling(state)
            return
        }

        state.isSettling = true
        state.scrollVelocityPxPerSecond = velocityPxPerSecond
    }

    fun step(
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
        if (kotlin.math.abs(velocity) < MIN_SETTLE_VELOCITY_PX_PER_SECOND) {
            stopSettling(state)
            return
        }

        state.scrollOffsetPx = (state.scrollOffsetPx - (velocity * deltaSeconds))
            .coerceIn(0f, state.maxScrollOffsetPx)

        val deceleration = if (velocity > 0f) {
            -SETTLE_DECELERATION_PX_PER_SECOND_SQUARED
        } else {
            SETTLE_DECELERATION_PX_PER_SECOND_SQUARED
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
        if (kotlin.math.abs(state.scrollVelocityPxPerSecond) < MIN_SETTLE_VELOCITY_PX_PER_SECOND) {
            stopSettling(state)
        }
    }

    fun isActive(state: PixelListState): Boolean {
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
    fun scrollItemIntoView(
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
        state.isSettling = false
        state.scrollVelocityPxPerSecond = 0f
    }
}
