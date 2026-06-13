package com.purride.pixelui.state

/**
 * 通用列表状态。
 *
 * 这一版先采用最简单的绝对滚动偏移模型：
 * `scrollOffsetPx` 表示内容顶部相对视口顶部已经向上滚动了多少像素。
 * 这样可以先把列表视口、裁剪和触摸拖动打通，再决定后续是否增加虚拟化窗口等能力。
 */
public class PixelListState(
    initialScrollOffsetPx: Float = 0f,
) {
    public var scrollOffsetPx: Float = initialScrollOffsetPx.coerceAtLeast(0f)
        internal set

    public var isDragging: Boolean = false
        internal set

    public var isSettling: Boolean = false
        internal set

    public var scrollVelocityPxPerSecond: Float = 0f
        internal set

    internal var maxScrollOffsetPx: Float = 0f
    internal var viewportWidthPx: Int = 0
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

    /**
     * 变高 lazy list 的内部测量缓存。
     *
     * 0 表示该 item 尚未测量；render layout 会在真实子节点完成布局后回写。
     */
    internal var measuredItemHeightsPx: IntArray = intArrayOf()

    /**
     * 变高 lazy list 的"远端目标项尚未测量，等下一帧重测后微调"标记。
     *
     * 当调用 [PixelListController.scrollItemIntoView] 且目标 item 尚未测量时，
     * 控制器会先按 estimated 高度滚动到大致位置，并把目标 itemIndex 记录在这里。
     * RenderVariableLazyListViewport 在下一次 layout 完成测量后会重新调用一次
     * scrollItemIntoView，使用真实测量值进行二次微调；测量到位即清空。
     */
    internal var pendingScrollIntoViewItemIndex: Int? = null
}

/**
 * List/Grid/SingleChildScrollView 的可持久化滚动位置。
 */
public data class PixelListSavedState(
    public val scrollOffsetPx: Float,
    public val maxScrollOffsetPx: Float = 0f,
)

/**
 * 列表恢复到新 viewport/content 几何时的偏移映射策略。
 */
public enum class PixelListRestorationPolicy {
    AbsoluteOffset,
    RelativeProgress,
}
