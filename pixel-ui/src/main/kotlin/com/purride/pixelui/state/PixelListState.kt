package com.purride.pixelui.state

/**
 * 通用列表状态。
 *
 * 这一版先采用最简单的绝对滚动偏移模型：
 * `scrollOffsetPx` 表示内容顶部相对视口顶部已经向上滚动了多少像素。
 * 这样可以先把列表视口、裁剪和触摸拖动打通，再决定后续是否增加虚拟化窗口等能力。
 */
class PixelListState(
    initialScrollOffsetPx: Float = 0f,
) {
    var scrollOffsetPx: Float = initialScrollOffsetPx.coerceAtLeast(0f)
        internal set

    internal var maxScrollOffsetPx: Float = 0f
}
