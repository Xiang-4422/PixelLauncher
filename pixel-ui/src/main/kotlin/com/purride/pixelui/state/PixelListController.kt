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

    fun create(initialScrollOffsetPx: Float = 0f): PixelListState {
        return PixelListState(initialScrollOffsetPx = initialScrollOffsetPx)
    }

    fun sync(
        state: PixelListState,
        viewportHeightPx: Int,
        contentHeightPx: Int,
    ) {
        state.maxScrollOffsetPx = maxScrollOffsetPx(
            viewportHeightPx = viewportHeightPx,
            contentHeightPx = contentHeightPx,
        )
        state.scrollOffsetPx = state.scrollOffsetPx.coerceIn(0f, state.maxScrollOffsetPx)
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
        state.scrollOffsetPx = (state.scrollOffsetPx - deltaPx).coerceIn(0f, state.maxScrollOffsetPx)
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

    private fun maxScrollOffsetPx(
        viewportHeightPx: Int,
        contentHeightPx: Int,
    ): Float {
        return (contentHeightPx - viewportHeightPx).coerceAtLeast(0).toFloat()
    }
}
