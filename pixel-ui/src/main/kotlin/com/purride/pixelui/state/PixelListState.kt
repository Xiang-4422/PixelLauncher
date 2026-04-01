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

    var isDragging: Boolean = false
        internal set

    var isSettling: Boolean = false
        internal set

    var scrollVelocityPxPerSecond: Float = 0f
        internal set

    internal var maxScrollOffsetPx: Float = 0f
    internal var viewportHeightPx: Int = 0
    internal var contentHeightPx: Int = 0

    /**
     * 列表运行时最近一次测量出的项布局信息。
     *
     * 当前先把每一项在内容坐标系里的顶部位置和高度回填进状态，
     * 这样控制器就能在不依赖业务侧布局代码的前提下做“滚动到某一项”。
     */
    internal var itemTopOffsetsPx: IntArray = intArrayOf()
    internal var itemHeightsPx: IntArray = intArrayOf()
}
