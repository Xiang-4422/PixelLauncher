package com.purride.pixelui.state

import android.os.Bundle
import com.purride.pixelui.internal.PixelArtifactInternalApi

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
    /** 保存 `PixelListState` 的 `scrollOffsetPx` 逻辑坐标或位移；写入后由所属对象在下一次状态同步时生效。 */
    public var scrollOffsetPx: Float = initialScrollOffsetPx.coerceAtLeast(0f)
        internal set

    /** 表示 `PixelListState` 当前是否满足 `isDragging` 对应条件；写入后由所属对象在下一次状态同步时生效。 */
    public var isDragging: Boolean = false
        internal set

    /** 表示 `PixelListState` 当前是否满足 `isSettling` 对应条件；写入后由所属对象在下一次状态同步时生效。 */
    public var isSettling: Boolean = false
        internal set

    /** 记录 `PixelListState` 的 `scrollVelocityPxPerSecond` 配置或运行值，读取与更新均遵守所属类型约束；写入后由所属对象在下一次状态同步时生效。 */
    public var scrollVelocityPxPerSecond: Float = 0f
        internal set

    /** 供 runtime 视口回填的最大滚动边界。 */
    @PixelArtifactInternalApi
    public var maxScrollOffsetPx: Float = 0f
    /** 供 runtime 视口回填的最近布局宽度。 */
    @PixelArtifactInternalApi
    public var viewportWidthPx: Int = 0
    /** 供 runtime 视口回填的最近布局高度。 */
    @PixelArtifactInternalApi
    public var viewportHeightPx: Int = 0
    /** 供 runtime 视口回填的最近内容高度。 */
    @PixelArtifactInternalApi
    public var contentHeightPx: Int = 0

    /**
     * 列表运行时最近一次测量出的项布局信息。
     *
     * 当前先把每一项在内容坐标系里的顶部位置和高度回填进状态，
     * 这样控制器就能在不依赖业务侧布局代码的前提下做“滚动到某一项”。
     */
    @PixelArtifactInternalApi
    public var itemTopOffsetsPx: IntArray = intArrayOf()
    @PixelArtifactInternalApi
    /** 定义 `PixelListState` 布局中的 `itemHeightsPx` 逻辑像素度量；写入后由所属对象在下一次状态同步时生效。 */
    public var itemHeightsPx: IntArray = intArrayOf()

    /**
     * 变高 lazy list 的内部测量缓存。
     *
     * 0 表示该 item 尚未测量；render layout 会在真实子节点完成布局后回写。
     */
    @PixelArtifactInternalApi
    public var measuredItemHeightsPx: IntArray = intArrayOf()
    @PixelArtifactInternalApi
    /** 定义 `PixelListState` 布局中的 `itemExtentIndex` 逻辑像素度量。 */
    public val itemExtentIndex: PixelItemExtentIndex = PixelItemExtentIndex()
    @PixelArtifactInternalApi
    /** 定义 `PixelListState` 布局中的 `measuredSeparatedVirtualHeightsPx` 逻辑像素度量；写入后由所属对象在下一次状态同步时生效。 */
    public var measuredSeparatedVirtualHeightsPx: IntArray = intArrayOf()
    @PixelArtifactInternalApi
    /** 定义 `PixelListState` 布局中的 `separatedExtentIndex` 逻辑像素度量。 */
    public val separatedExtentIndex: PixelSeparatedExtentIndex = PixelSeparatedExtentIndex()
    @PixelArtifactInternalApi
    /** 表示 `PixelListState` 当前是否满足 `separatedItemGeometryActive` 对应条件；写入后由所属对象在下一次状态同步时生效。 */
    public var separatedItemGeometryActive: Boolean = false
    @PixelArtifactInternalApi
    /** 表示 `PixelListState` 当前是否满足 `separatedItemExtentVariable` 对应条件；写入后由所属对象在下一次状态同步时生效。 */
    public var separatedItemExtentVariable: Boolean = false

    /**
     * 变高 lazy list 的"远端目标项尚未测量，等下一帧重测后微调"标记。
     *
     * 当调用 [PixelListController.scrollItemIntoView] 且目标 item 尚未测量时，
     * 控制器会先按 estimated 高度滚动到大致位置，并把目标 itemIndex 记录在这里。
     * RenderVariableLazyListViewport 在下一次 layout 完成测量后会重新调用一次
     * scrollItemIntoView，使用真实测量值进行二次微调；测量到位即清空。
     */
    @PixelArtifactInternalApi
    public var pendingScrollIntoViewItemIndex: Int? = null
    internal var pendingRestorationState: PixelListSavedState? = null
    internal var pendingRestorationPolicy: PixelListRestorationPolicy = PixelListRestorationPolicy.AbsoluteOffset
    internal var pendingJumpToEnd: Boolean = false
    @PixelArtifactInternalApi
    /** 保存 `PixelListState` 当前的 `scrollSnapRanges` 集合；元素顺序和所有权遵守所属类型契约；写入后由所属对象在下一次状态同步时生效。 */
    public var scrollSnapRanges: List<PixelScrollSnapRange> = emptyList()
    internal var snapTargetOffsetPx: Float? = null
    @PixelArtifactInternalApi
    /** 保存 `PixelListState` 的 `lastFloatingScrollOffsetPx` 逻辑坐标或位移；写入后由所属对象在下一次状态同步时生效。 */
    public var lastFloatingScrollOffsetPx: Float? = null
    @PixelArtifactInternalApi
    /** 保存 `PixelListState` 的 `floatingRevealBySliverIndex` 计数或索引边界。 */
    public val floatingRevealBySliverIndex: MutableMap<Int, Int> = mutableMapOf()
    @PixelArtifactInternalApi
    /** 保存 `PixelListState` 当前的 `sliverListGeometries` 集合；元素顺序和所有权遵守所属类型契约。 */
    public val sliverListGeometries: MutableMap<Int, PixelSliverListGeometry> = mutableMapOf()
    @PixelArtifactInternalApi
    /** 记录 `PixelListState` 的 `pendingSliverScrollIntoView` 配置或运行值，读取与更新均遵守所属类型约束；写入后由所属对象在下一次状态同步时生效。 */
    public var pendingSliverScrollIntoView: PixelPendingSliverScrollIntoView? = null
}

/** 供 runtime 视口跨 artifact 复用的 Fenwick item 高度索引。 */
@PixelArtifactInternalApi
public class PixelItemExtentIndex {
    private var itemCount: Int = 0
    private var estimatedItemExtent: Int = 1
    private var spacing: Int = 0
    private var extents: IntArray = intArrayOf()
    private var fenwickTree: IntArray = intArrayOf()

    /** 按 `configure` 规则校验或配置 `PixelListState`，不满足不变量的输入会被明确拒绝。 */
    public fun configure(
        itemCount: Int,
        estimatedItemExtent: Int,
        spacing: Int,
        measuredHeightsPx: IntArray,
    ) {
        val resolvedItemCount = itemCount.coerceAtLeast(0)
        val safeEstimate = estimatedItemExtent.coerceAtLeast(1)
        val safeSpacing = spacing.coerceAtLeast(0)
        if (
            this.itemCount == resolvedItemCount &&
            this.estimatedItemExtent == safeEstimate &&
            this.spacing == safeSpacing &&
            extents.size == resolvedItemCount
        ) {
            return
        }

        this.itemCount = resolvedItemCount
        this.estimatedItemExtent = safeEstimate
        this.spacing = safeSpacing
        extents = IntArray(resolvedItemCount)
        fenwickTree = IntArray(resolvedItemCount + 1)
        repeat(resolvedItemCount) { index ->
            val measured = measuredHeightsPx.getOrNull(index) ?: 0
            val extent = measured.takeIf { it > 0 } ?: safeEstimate
            extents[index] = extent.coerceAtLeast(1)
            addToFenwick(index, extents[index])
        }
    }

    /** 更新 `PixelListState` 的 `updateMeasured` 状态，并保持相关边界与派生状态一致。 */
    public fun updateMeasured(itemIndex: Int, measuredExtent: Int) {
        if (itemIndex !in 0 until itemCount) return
        val safeExtent = measuredExtent.coerceAtLeast(1)
        val delta = safeExtent - extents[itemIndex]
        if (delta == 0) return
        extents[itemIndex] = safeExtent
        addToFenwick(itemIndex, delta)
    }

    /** 查询 `PixelListState` 的 `extentPx` 派生结果；该读取不会改变已保存状态。 */
    public fun extentPx(itemIndex: Int): Int = extents.getOrElse(itemIndex) { 0 }

    /** 查询 `PixelListState` 的 `topPx` 派生结果；该读取不会改变已保存状态。 */
    public fun topPx(itemIndex: Int): Int {
        val safeIndex = itemIndex.coerceIn(0, itemCount)
        return prefixSum(safeIndex) + safeIndex * spacing
    }

    /** 查询 `PixelListState` 的 `bottomPx` 派生结果；该读取不会改变已保存状态。 */
    public fun bottomPx(itemIndex: Int): Int = topPx(itemIndex) + extentPx(itemIndex)

    /** 查询 `PixelListState` 的 `totalHeightPx` 派生结果；该读取不会改变已保存状态。 */
    public fun totalHeightPx(): Int {
        if (itemCount <= 0) return 0
        return prefixSum(itemCount) + (itemCount - 1) * spacing
    }

    /** 依据 `PixelListState` 的公开契约执行 `indexAtOffsetPx`，并返回或提交经过边界校验的结果。 */
    public fun indexAtOffsetPx(offsetPx: Int): Int {
        if (itemCount <= 0) return 0
        val target = offsetPx.coerceIn(0, (totalHeightPx() - 1).coerceAtLeast(0))
        var low = 0
        var high = itemCount - 1
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            val bottom = bottomPx(mid)
            if (bottom <= target) {
                low = mid + 1
            } else {
                result = mid
                high = mid - 1
            }
        }
        return result
    }

    private fun prefixSum(endExclusive: Int): Int {
        var index = endExclusive.coerceIn(0, itemCount)
        var result = 0
        while (index > 0) {
            result += fenwickTree[index]
            index -= index and -index
        }
        return result
    }

    private fun addToFenwick(itemIndex: Int, delta: Int) {
        var index = itemIndex + 1
        while (index < fenwickTree.size) {
            fenwickTree[index] += delta
            index += index and -index
        }
    }
}

/** runtime 计算滚动吸附时使用的闭区间。 */
@PixelArtifactInternalApi
public data class PixelScrollSnapRange(
    /** 保存 `PixelListState` 的 `startOffsetPx` 逻辑坐标或位移。 */
    public val startOffsetPx: Float,
    /** 保存 `PixelListState` 的 `endOffsetPx` 逻辑坐标或位移。 */
    public val endOffsetPx: Float,
)

/** runtime 在 sliver 之间共享的列表测量几何。 */
@PixelArtifactInternalApi
public class PixelSliverListGeometry(
    /** 记录 `PixelListState` 的 `contentStartPx` 配置或运行值，读取与更新均遵守所属类型约束；写入后由所属对象在下一次状态同步时生效。 */
    public var contentStartPx: Int,
    /** 保存 `PixelListState` 的 `itemCount` 计数或索引边界；写入后由所属对象在下一次状态同步时生效。 */
    public var itemCount: Int,
    /** 定义 `PixelListState` 布局中的 `estimatedItemExtent` 逻辑像素度量；写入后由所属对象在下一次状态同步时生效。 */
    public var estimatedItemExtent: Int,
    /** 定义 `PixelListState` 布局中的 `spacing` 逻辑像素度量；写入后由所属对象在下一次状态同步时生效。 */
    public var spacing: Int,
    /** 表示 `PixelListState` 当前是否满足 `variableHeight` 对应条件；写入后由所属对象在下一次状态同步时生效。 */
    public var variableHeight: Boolean,
    measuredItemHeightsPx: IntArray = intArrayOf(),
) {
    /** 定义 `PixelListState` 布局中的 `measuredItemHeightsPx` 逻辑像素度量；写入后由所属对象在下一次状态同步时生效。 */
    public var measuredItemHeightsPx: IntArray = IntArray(itemCount.coerceAtLeast(0)) { index ->
        measuredItemHeightsPx.getOrNull(index) ?: 0
    }
        private set

    /** 更新 `PixelListState` 的 `update` 状态，并保持相关边界与派生状态一致。 */
    public fun update(
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

    /** 查询 `PixelListState` 的 `itemHeightPx` 派生结果；该读取不会改变已保存状态。 */
    public fun itemHeightPx(itemIndex: Int): Int {
        val measured = measuredItemHeightsPx.getOrNull(itemIndex) ?: 0
        return if (variableHeight && measured > 0) measured else estimatedItemExtent
    }

    /** 查询 `PixelListState` 的 `itemTopPx` 派生结果；该读取不会改变已保存状态。 */
    public fun itemTopPx(itemIndex: Int): Int {
        var top = contentStartPx
        for (index in 0 until itemIndex.coerceIn(0, itemCount)) {
            top += itemHeightPx(index)
            if (index < itemCount - 1) top += spacing
        }
        return top
    }

    /** 查询 `PixelListState` 的 `itemBottomPx` 派生结果；该读取不会改变已保存状态。 */
    public fun itemBottomPx(itemIndex: Int): Int = itemTopPx(itemIndex) + itemHeightPx(itemIndex)

    /** 依据 `PixelListState` 的公开契约执行 `indexAtOffsetPx`，并返回或提交经过边界校验的结果。 */
    public fun indexAtOffsetPx(offsetPx: Int): Int {
        if (itemCount <= 0) return 0
        val localOffsetPx = (offsetPx - contentStartPx).coerceIn(0, (contentHeightPx() - 1).coerceAtLeast(0))
        var cursor = 0
        repeat(itemCount) { index ->
            cursor += itemHeightPx(index)
            if (localOffsetPx < cursor) {
                return index
            }
            if (index < itemCount - 1) {
                cursor += spacing
                if (localOffsetPx < cursor) {
                    return index
                }
            }
        }
        return itemCount - 1
    }

    /** 查询 `PixelListState` 的 `contentHeightPx` 派生结果；该读取不会改变已保存状态。 */
    public fun contentHeightPx(): Int {
        if (itemCount <= 0) return 0
        var height = 0
        repeat(itemCount) { index ->
            height += itemHeightPx(index)
            if (index < itemCount - 1) height += spacing
        }
        return height
    }
}

/** 等待 sliver 完成真实测量后再次校正的滚动目标。 */
@PixelArtifactInternalApi
public data class PixelPendingSliverScrollIntoView(
    /** 保存 `PixelListState` 的 `sliverIndex` 计数或索引边界。 */
    public val sliverIndex: Int,
    /** 保存 `PixelListState` 的 `itemIndex` 计数或索引边界。 */
    public val itemIndex: Int,
)

/** 供 separated list 跨 artifact 复用的虚拟项高度索引。 */
@PixelArtifactInternalApi
public class PixelSeparatedExtentIndex {
    private var virtualCount: Int = 0
    private var itemExtent: Int? = null
    private var separatorExtent: Int? = null
    private var estimatedItemExtent: Int = 1
    private var estimatedSeparatorExtent: Int = 1
    private var extents: IntArray = intArrayOf()
    private var fenwickTree: IntArray = intArrayOf()

    /** 按 `configure` 规则校验或配置 `PixelListState`，不满足不变量的输入会被明确拒绝。 */
    public fun configure(
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

    /** 更新 `PixelListState` 的 `updateMeasured` 状态，并保持相关边界与派生状态一致。 */
    public fun updateMeasured(virtualIndex: Int, measuredExtent: Int) {
        if (virtualIndex !in 0 until virtualCount) return
        val fixedExtent = if (virtualIndex % 2 == 0) itemExtent else separatorExtent
        if (fixedExtent != null) return
        val safeExtent = measuredExtent.coerceAtLeast(1)
        val delta = safeExtent - extents[virtualIndex]
        if (delta == 0) return
        extents[virtualIndex] = safeExtent
        addToFenwick(virtualIndex, delta)
    }

    /** 查询 `PixelListState` 的 `extentPx` 派生结果；该读取不会改变已保存状态。 */
    public fun extentPx(virtualIndex: Int): Int = extents.getOrElse(virtualIndex) { 0 }

    /** 查询 `PixelListState` 的 `topPx` 派生结果；该读取不会改变已保存状态。 */
    public fun topPx(virtualIndex: Int): Int = prefixSum(virtualIndex.coerceIn(0, virtualCount))

    /** 查询 `PixelListState` 的 `bottomPx` 派生结果；该读取不会改变已保存状态。 */
    public fun bottomPx(virtualIndex: Int): Int = topPx(virtualIndex) + extentPx(virtualIndex)

    /** 查询 `PixelListState` 的 `totalHeightPx` 派生结果；该读取不会改变已保存状态。 */
    public fun totalHeightPx(): Int = prefixSum(virtualCount)

    /** 依据 `PixelListState` 的公开契约执行 `indexAtOffsetPx`，并返回或提交经过边界校验的结果。 */
    public fun indexAtOffsetPx(offsetPx: Int): Int {
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
 * 公开 `PixelListState` 当前的 `PixelListSavedStateBundleKey` 状态维度。
 *
 * Android [Bundle] key used by [PixelListSavedState.saveToBundle] and [getPixelListSavedState].
 */
public const val PixelListSavedStateBundleKey: String = "com.purride.pixelui.list.savedState"

/**
 * 定义 `PixelListAnchor` 在 `PixelListState` 中承担的数据与行为边界。
 *
 * A stable item anchor captured with [PixelListSavedState].
 *
 * [itemIndex] is the first visible item at save time. [itemOffsetPx] is the number of pixels
 * scrolled past that item's top edge. [sliverIndex] is only set when the anchor belongs to a
 * lazy sliver inside `CustomScrollView`.
 */
public data class PixelListAnchor(
    /** 保存 `PixelListState` 的 `itemIndex` 计数或索引边界。 */
    public val itemIndex: Int,
    /** 保存 `PixelListState` 的 `itemOffsetPx` 逻辑坐标或位移。 */
    public val itemOffsetPx: Float,
    /** 保存 `PixelListState` 的 `sliverIndex` 计数或索引边界。 */
    public val sliverIndex: Int? = null,
)

/**
 * List/Grid/SingleChildScrollView 的可持久化滚动位置。
 */
public data class PixelListSavedState(
    /** 保存 `PixelListState` 的 `scrollOffsetPx` 逻辑坐标或位移。 */
    public val scrollOffsetPx: Float,
    /** 保存 `PixelListState` 的 `maxScrollOffsetPx` 逻辑坐标或位移。 */
    public val maxScrollOffsetPx: Float = 0f,
    /** 记录 `PixelListState` 的 `anchor` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val anchor: PixelListAnchor? = null,
)

/**
 * 执行 `PixelListState` 的 `saveToBundle` 公开行为；具体参数、返回和副作用见下文。
 *
 * Saves this list scroll snapshot into an Android [Bundle].
 */
public fun PixelListSavedState.saveToBundle(
    outState: Bundle,
    key: String = PixelListSavedStateBundleKey,
) {
    require(key.isNotBlank()) { "PixelListSavedState Bundle key must not be blank" }
    val bundle = Bundle()
    bundle.putFloat(PixelListSavedStateKeys.ScrollOffsetPx, scrollOffsetPx)
    bundle.putFloat(PixelListSavedStateKeys.MaxScrollOffsetPx, maxScrollOffsetPx)
    val savedAnchor = anchor
    if (savedAnchor != null) {
        bundle.putBoolean(PixelListSavedStateKeys.HasAnchor, true)
        bundle.putInt(PixelListSavedStateKeys.AnchorItemIndex, savedAnchor.itemIndex)
        bundle.putFloat(PixelListSavedStateKeys.AnchorItemOffsetPx, savedAnchor.itemOffsetPx)
        val sliverIndex = savedAnchor.sliverIndex
        if (sliverIndex != null) {
            bundle.putBoolean(PixelListSavedStateKeys.HasAnchorSliver, true)
            bundle.putInt(PixelListSavedStateKeys.AnchorSliverIndex, sliverIndex)
        }
    }
    outState.putBundle(key, bundle)
}

/**
 * 查询 `PixelListState` 的 `getPixelListSavedState` 结果，不产生额外状态变更。
 *
 * Reads a [PixelListSavedState] previously saved into this Android [Bundle].
 */
public fun Bundle.getPixelListSavedState(
    key: String = PixelListSavedStateBundleKey,
): PixelListSavedState? {
    require(key.isNotBlank()) { "PixelListSavedState Bundle key must not be blank" }
    val bundle = getBundle(key) ?: return null
    val anchor = if (bundle.getBoolean(PixelListSavedStateKeys.HasAnchor, false)) {
        PixelListAnchor(
            itemIndex = bundle.getInt(PixelListSavedStateKeys.AnchorItemIndex),
            itemOffsetPx = bundle.getFloat(PixelListSavedStateKeys.AnchorItemOffsetPx),
            sliverIndex = if (bundle.getBoolean(PixelListSavedStateKeys.HasAnchorSliver, false)) {
                bundle.getInt(PixelListSavedStateKeys.AnchorSliverIndex)
            } else {
                null
            },
        )
    } else {
        null
    }
    return PixelListSavedState(
        scrollOffsetPx = bundle.getFloat(PixelListSavedStateKeys.ScrollOffsetPx),
        maxScrollOffsetPx = bundle.getFloat(PixelListSavedStateKeys.MaxScrollOffsetPx),
        anchor = anchor,
    )
}

/**
 * 列表恢复到新 viewport/content 几何时的偏移映射策略。
 */
public enum class PixelListRestorationPolicy {
    AbsoluteOffset,
    RelativeProgress,
    AnchorItem,
}

private object PixelListSavedStateKeys {
    const val ScrollOffsetPx = "scrollOffsetPx"
    const val MaxScrollOffsetPx = "maxScrollOffsetPx"
    const val HasAnchor = "hasAnchor"
    const val AnchorItemIndex = "anchorItemIndex"
    const val AnchorItemOffsetPx = "anchorItemOffsetPx"
    const val HasAnchorSliver = "hasAnchorSliver"
    const val AnchorSliverIndex = "anchorSliverIndex"
}
