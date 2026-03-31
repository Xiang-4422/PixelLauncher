package com.purride.pixelui.state

/**
 * 通用列表状态。
 *
 * 第一版明确采用显式状态提升，列表的窗口位置和滚动偏移
 * 都由宿主或上层 scene 持有，不隐藏在组件内部。
 */
data class PixelListState(
    val firstVisibleIndex: Int = 0,
    val scrollOffsetPx: Float = 0f,
)
