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
    internal var measuredSeparatedVirtualHeightsPx: IntArray = intArrayOf()
    internal val separatedExtentIndex: PixelSeparatedExtentIndex = PixelSeparatedExtentIndex()
    internal var separatedItemGeometryActive: Boolean = false
    internal var separatedItemExtentVariable: Boolean = false

    /**
     * 变高 lazy list 的"远端目标项尚未测量，等下一帧重测后微调"标记。
     *
     * 当调用 [PixelListController.scrollItemIntoView] 且目标 item 尚未测量时，
     * 控制器会先按 estimated 高度滚动到大致位置，并把目标 itemIndex 记录在这里。
     * RenderVariableLazyListViewport 在下一次 layout 完成测量后会重新调用一次
     * scrollItemIntoView，使用真实测量值进行二次微调；测量到位即清空。
     */
    internal var pendingScrollIntoViewItemIndex: Int? = null
    internal var pendingRestorationState: PixelListSavedState? = null
    internal var pendingRestorationPolicy: PixelListRestorationPolicy = PixelListRestorationPolicy.AbsoluteOffset
    internal var scrollSnapRanges: List<PixelScrollSnapRange> = emptyList()
    internal var snapTargetOffsetPx: Float? = null
    internal var lastFloatingScrollOffsetPx: Float? = null
    internal val floatingRevealBySliverIndex: MutableMap<Int, Int> = mutableMapOf()
    internal val sliverListGeometries: MutableMap<Int, PixelSliverListGeometry> = mutableMapOf()
    internal var pendingSliverScrollIntoView: PixelPendingSliverScrollIntoView? = null
}

internal data class PixelScrollSnapRange(
    val startOffsetPx: Float,
    val endOffsetPx: Float,
)

internal class PixelSliverListGeometry(
    var contentStartPx: Int,
    var itemCount: Int,
    var estimatedItemExtent: Int,
    var spacing: Int,
    var variableHeight: Boolean,
    measuredItemHeightsPx: IntArray = intArrayOf(),
) {
    var measuredItemHeightsPx: IntArray = IntArray(itemCount.coerceAtLeast(0)) { index ->
        measuredItemHeightsPx.getOrNull(index) ?: 0
    }
        private set

    fun update(
        contentStartPx: Int,
        itemCount: Int,
        estimatedItemExtent: Int,
        spacing: Int,
        variableHeight: Boolean,
    ) {
        this.contentStartPx = contentStartPx
        this.itemCount = itemCount.coerceAtLeast(0)
        this.estimatedItemExtent = estimatedItemExtent.coerceAtLeast(1)
        this.spacing = spacing.coerceAtLeast(0)
        this.variableHeight = variableHeight
        if (this.measuredItemHeightsPx.size != this.itemCount) {
            val previous = this.measuredItemHeightsPx
            this.measuredItemHeightsPx = IntArray(this.itemCount) { index ->
                previous.getOrNull(index) ?: 0
            }
        }
    }

    fun itemHeightPx(itemIndex: Int): Int {
        val measured = measuredItemHeightsPx.getOrNull(itemIndex) ?: 0
        return if (variableHeight && measured > 0) measured else estimatedItemExtent
    }

    fun itemTopPx(itemIndex: Int): Int {
        var top = contentStartPx
        for (index in 0 until itemIndex.coerceIn(0, itemCount)) {
            top += itemHeightPx(index)
            if (index < itemCount - 1) top += spacing
        }
        return top
    }

    fun itemBottomPx(itemIndex: Int): Int = itemTopPx(itemIndex) + itemHeightPx(itemIndex)

    fun contentHeightPx(): Int {
        if (itemCount <= 0) return 0
        var height = 0
        repeat(itemCount) { index ->
            height += itemHeightPx(index)
            if (index < itemCount - 1) height += spacing
        }
        return height
    }
}

internal data class PixelPendingSliverScrollIntoView(
    val sliverIndex: Int,
    val itemIndex: Int,
)

internal class PixelSeparatedExtentIndex {
    private var virtualCount: Int = 0
    private var itemExtent: Int? = null
    private var separatorExtent: Int? = null
    private var estimatedItemExtent: Int = 1
    private var estimatedSeparatorExtent: Int = 1
    private var extents: IntArray = intArrayOf()
    private var fenwickTree: IntArray = intArrayOf()

    fun configure(
        itemCount: Int,
        itemExtent: Int?,
        separatorExtent: Int?,
        estimatedItemExtent: Int,
        estimatedSeparatorExtent: Int,
        measuredHeightsPx: IntArray,
    ) {
        val resolvedVirtualCount = if (itemCount <= 0) 0 else itemCount * 2 - 1
        val safeItemExtent = estimatedItemExtent.coerceAtLeast(1)
        val safeSeparatorExtent = estimatedSeparatorExtent.coerceAtLeast(1)
        if (
            virtualCount == resolvedVirtualCount &&
            this.itemExtent == itemExtent &&
            this.separatorExtent == separatorExtent &&
            this.estimatedItemExtent == safeItemExtent &&
            this.estimatedSeparatorExtent == safeSeparatorExtent
        ) {
            return
        }

        virtualCount = resolvedVirtualCount
        this.itemExtent = itemExtent
        this.separatorExtent = separatorExtent
        this.estimatedItemExtent = safeItemExtent
        this.estimatedSeparatorExtent = safeSeparatorExtent
        extents = IntArray(virtualCount)
        fenwickTree = IntArray(virtualCount + 1)
        repeat(virtualCount) { virtualIndex ->
            val fixedExtent = if (virtualIndex % 2 == 0) itemExtent else separatorExtent
            val measuredExtent = measuredHeightsPx.getOrNull(virtualIndex) ?: 0
            val extent = fixedExtent ?: measuredExtent.takeIf { it > 0 } ?: if (virtualIndex % 2 == 0) {
                safeItemExtent
            } else {
                safeSeparatorExtent
            }
            extents[virtualIndex] = extent.coerceAtLeast(1)
            addToFenwick(virtualIndex, extents[virtualIndex])
        }
    }

    fun updateMeasured(virtualIndex: Int, measuredExtent: Int) {
        if (virtualIndex !in 0 until virtualCount) return
        val fixedExtent = if (virtualIndex % 2 == 0) itemExtent else separatorExtent
        if (fixedExtent != null) return
        val safeExtent = measuredExtent.coerceAtLeast(1)
        val delta = safeExtent - extents[virtualIndex]
        if (delta == 0) return
        extents[virtualIndex] = safeExtent
        addToFenwick(virtualIndex, delta)
    }

    fun extentPx(virtualIndex: Int): Int = extents.getOrElse(virtualIndex) { 0 }

    fun topPx(virtualIndex: Int): Int = prefixSum(virtualIndex.coerceIn(0, virtualCount))

    fun bottomPx(virtualIndex: Int): Int = topPx(virtualIndex) + extentPx(virtualIndex)

    fun totalHeightPx(): Int = prefixSum(virtualCount)

    fun indexAtOffsetPx(offsetPx: Int): Int {
        if (virtualCount <= 0) return 0
        val target = offsetPx.coerceIn(0, (totalHeightPx() - 1).coerceAtLeast(0))
        var index = 0
        var accumulated = 0
        var bit = Integer.highestOneBit(virtualCount)
        while (bit != 0) {
            val next = index + bit
            if (next <= virtualCount && accumulated + fenwickTree[next] <= target) {
                index = next
                accumulated += fenwickTree[next]
            }
            bit = bit shr 1
        }
        return index.coerceAtMost(virtualCount - 1)
    }

    private fun prefixSum(endExclusive: Int): Int {
        var index = endExclusive.coerceIn(0, virtualCount)
        var result = 0
        while (index > 0) {
            result += fenwickTree[index]
            index -= index and -index
        }
        return result
    }

    private fun addToFenwick(virtualIndex: Int, delta: Int) {
        var index = virtualIndex + 1
        while (index < fenwickTree.size) {
            fenwickTree[index] += delta
            index += index and -index
        }
    }
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
