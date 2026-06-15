package com.purride.pixelui.internal

import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelItemExtentIndex
import com.purride.pixelui.state.PixelSeparatedExtentIndex
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 变高 lazy list 内部 helper 函数的回归测试。
 *
 * 覆盖：
 *  - [ensureMeasuredItemCapacity] 在 itemCount 变化下保留 / 截断 / 扩展条目
 *  - [variableItemHeightPx] 在 measured == 0 时回退到 [estimatedItemExtent]
 *  - [variableItemTopPx] / [variableItemContentHeightPx] 在混合测量 / 未测场景下
 *    的累加正确性
 *
 * 这些 helper 是 RenderLazyListViewport 计算 content height / offsets 的底座；
 * 一旦有 off-by-one 或脏缓存遗留，整个变高列表都会跑偏。
 */
class VariableHeightListTest {

    // ── ensureMeasuredItemCapacity ──────────────────────────────────────────

    @Test
    fun ensureCapacityFromEmptyAllocatesZeros() {
        val state = PixelListState()
        state.ensureMeasuredItemCapacity(itemCount = 5)
        assertEquals(5, state.measuredItemHeightsPx.size)
        state.measuredItemHeightsPx.forEach { assertEquals(0, it) }
    }

    @Test
    fun ensureCapacityZeroResetsToEmpty() {
        val state = PixelListState().also {
            it.ensureMeasuredItemCapacity(3)
            it.measuredItemHeightsPx[0] = 10
        }
        state.ensureMeasuredItemCapacity(0)
        assertEquals(0, state.measuredItemHeightsPx.size)
    }

    @Test
    fun ensureCapacityGrowKeepsExistingMeasurements() {
        val state = PixelListState()
        state.ensureMeasuredItemCapacity(3)
        state.measuredItemHeightsPx[0] = 12
        state.measuredItemHeightsPx[2] = 18

        state.ensureMeasuredItemCapacity(6)
        assertEquals(6, state.measuredItemHeightsPx.size)
        // 已有条目被保留
        assertEquals(12, state.measuredItemHeightsPx[0])
        assertEquals(0, state.measuredItemHeightsPx[1])
        assertEquals(18, state.measuredItemHeightsPx[2])
        // 新增条目初始化为 0
        assertEquals(0, state.measuredItemHeightsPx[3])
        assertEquals(0, state.measuredItemHeightsPx[5])
    }

    @Test
    fun ensureCapacityShrinkDropsTailEntries() {
        val state = PixelListState()
        state.ensureMeasuredItemCapacity(5)
        state.measuredItemHeightsPx[0] = 11
        state.measuredItemHeightsPx[3] = 33  // 这一项在缩容后应当被丢弃
        state.measuredItemHeightsPx[4] = 44

        state.ensureMeasuredItemCapacity(3)
        assertEquals(3, state.measuredItemHeightsPx.size)
        assertEquals(11, state.measuredItemHeightsPx[0])
        assertEquals(0, state.measuredItemHeightsPx[1])
        assertEquals(0, state.measuredItemHeightsPx[2])
        // 后续再扩回 5：丢弃的尾部条目不应"借尸还魂"
        state.ensureMeasuredItemCapacity(5)
        assertEquals(0, state.measuredItemHeightsPx[3])
        assertEquals(0, state.measuredItemHeightsPx[4])
    }

    @Test
    fun ensureCapacitySameSizeIsNoOp() {
        val state = PixelListState()
        state.ensureMeasuredItemCapacity(4)
        val originalArray = state.measuredItemHeightsPx
        state.measuredItemHeightsPx[1] = 7
        state.ensureMeasuredItemCapacity(4)
        // 数组实例与内容都不变
        assertEquals(originalArray, state.measuredItemHeightsPx)
        assertEquals(7, state.measuredItemHeightsPx[1])
    }

    // ── variableItemHeightPx ────────────────────────────────────────────────

    @Test
    fun variableItemHeightFallsBackToEstimate() {
        val state = PixelListState()
        state.ensureMeasuredItemCapacity(3)
        // 全部未测量 → 回退到 estimated
        assertEquals(20, variableItemHeightPx(state, itemIndex = 0, estimatedItemExtent = 20))
        assertEquals(20, variableItemHeightPx(state, itemIndex = 2, estimatedItemExtent = 20))
    }

    @Test
    fun variableItemHeightUsesMeasuredWhenAvailable() {
        val state = PixelListState()
        state.ensureMeasuredItemCapacity(3)
        state.measuredItemHeightsPx[1] = 30
        assertEquals(20, variableItemHeightPx(state, itemIndex = 0, estimatedItemExtent = 20))
        assertEquals(30, variableItemHeightPx(state, itemIndex = 1, estimatedItemExtent = 20))
        assertEquals(20, variableItemHeightPx(state, itemIndex = 2, estimatedItemExtent = 20))
    }

    @Test
    fun variableItemHeightOutOfRangeFallsBackToEstimate() {
        val state = PixelListState()
        state.ensureMeasuredItemCapacity(2)
        assertEquals(15, variableItemHeightPx(state, itemIndex = 5, estimatedItemExtent = 15))
        assertEquals(15, variableItemHeightPx(state, itemIndex = -1, estimatedItemExtent = 15))
    }

    @Test
    fun variableItemHeightEnforcesMinimumOfOne() {
        val state = PixelListState()
        state.ensureMeasuredItemCapacity(1)
        // estimatedItemExtent <= 0 不应当被透传
        assertEquals(1, variableItemHeightPx(state, itemIndex = 0, estimatedItemExtent = 0))
        assertEquals(1, variableItemHeightPx(state, itemIndex = 0, estimatedItemExtent = -5))
    }

    // ── variableItemTopPx / variableItemContentHeightPx ─────────────────────

    @Test
    fun variableTopOffsetAccumulatesHeightsAndSpacing() {
        val state = PixelListState()
        state.ensureMeasuredItemCapacity(4)
        state.measuredItemHeightsPx[0] = 10
        state.measuredItemHeightsPx[1] = 20
        state.measuredItemHeightsPx[2] = 30
        state.measuredItemHeightsPx[3] = 40

        // 累加 + 2px spacing 间隔
        assertEquals(0, variableItemTopPx(state, itemIndex = 0, estimatedItemExtent = 50, spacing = 2))
        assertEquals(12, variableItemTopPx(state, itemIndex = 1, estimatedItemExtent = 50, spacing = 2))
        assertEquals(34, variableItemTopPx(state, itemIndex = 2, estimatedItemExtent = 50, spacing = 2))
        assertEquals(66, variableItemTopPx(state, itemIndex = 3, estimatedItemExtent = 50, spacing = 2))
    }

    @Test
    fun variableTopOffsetMixesMeasuredAndEstimated() {
        val state = PixelListState()
        state.ensureMeasuredItemCapacity(4)
        state.measuredItemHeightsPx[0] = 10
        // item 1 / 2 未测量 → 用 estimatedItemExtent
        state.measuredItemHeightsPx[3] = 40

        assertEquals(10, variableItemTopPx(state, itemIndex = 1, estimatedItemExtent = 25, spacing = 0))
        assertEquals(35, variableItemTopPx(state, itemIndex = 2, estimatedItemExtent = 25, spacing = 0))
        assertEquals(60, variableItemTopPx(state, itemIndex = 3, estimatedItemExtent = 25, spacing = 0))
    }

    @Test
    fun variableContentHeightSumsAllItemsAndInteriorSpacing() {
        val state = PixelListState()
        state.ensureMeasuredItemCapacity(3)
        state.measuredItemHeightsPx[0] = 10
        state.measuredItemHeightsPx[1] = 20
        state.measuredItemHeightsPx[2] = 30
        // 总高 = 10 + 2 + 20 + 2 + 30 = 64（spacing 仅出现在 item 之间）
        assertEquals(64, variableItemContentHeightPx(state, itemCount = 3, estimatedItemExtent = 50, spacing = 2))
    }

    @Test
    fun variableContentHeightZeroItemsIsZero() {
        val state = PixelListState()
        assertEquals(0, variableItemContentHeightPx(state, itemCount = 0, estimatedItemExtent = 100, spacing = 8))
    }

    @Test
    fun variableContentHeightWithAllEstimatedItems() {
        val state = PixelListState()
        state.ensureMeasuredItemCapacity(5)
        // 全部未测量；总高 = 5 * 25 + 4 * 3 = 137
        assertEquals(137, variableItemContentHeightPx(state, itemCount = 5, estimatedItemExtent = 25, spacing = 3))
    }

    @Test
    fun itemExtentIndexMapsRemoteOffsetsAndUpdatesMeasurements() {
        val index = PixelItemExtentIndex()
        index.configure(
            itemCount = 100_000,
            estimatedItemExtent = 5,
            spacing = 1,
            measuredHeightsPx = IntArray(100_000),
        )

        val targetItem = 90_000
        val targetTop = targetItem * 6
        assertEquals(targetItem, index.indexAtOffsetPx(targetTop))
        assertEquals(targetTop, index.topPx(targetItem))
        assertEquals(599_999, index.totalHeightPx())

        index.updateMeasured(targetItem, measuredExtent = 11)
        assertEquals(targetItem, index.indexAtOffsetPx(targetTop + 10))
        assertEquals(targetTop + 12, index.topPx(targetItem + 1))
        assertEquals(600_005, index.totalHeightPx())
    }

    @Test
    fun separatedExtentIndexMapsOffsetsWithoutLinearTopScans() {
        val index = PixelSeparatedExtentIndex()
        index.configure(
            itemCount = 4,
            itemExtent = null,
            separatorExtent = null,
            estimatedItemExtent = 5,
            estimatedSeparatorExtent = 2,
            measuredHeightsPx = IntArray(7),
        )

        assertEquals(0, index.topPx(0))
        assertEquals(5, index.topPx(1))
        assertEquals(7, index.topPx(2))
        assertEquals(26, index.totalHeightPx())
        assertEquals(0, index.indexAtOffsetPx(0))
        assertEquals(0, index.indexAtOffsetPx(4))
        assertEquals(1, index.indexAtOffsetPx(5))
        assertEquals(6, index.indexAtOffsetPx(25))
    }

    @Test
    fun separatedExtentIndexUpdatesDownstreamOffsetsAfterMeasurement() {
        val index = PixelSeparatedExtentIndex()
        index.configure(
            itemCount = 3,
            itemExtent = null,
            separatorExtent = null,
            estimatedItemExtent = 5,
            estimatedSeparatorExtent = 1,
            measuredHeightsPx = IntArray(5),
        )

        index.updateMeasured(virtualIndex = 0, measuredExtent = 9)
        index.updateMeasured(virtualIndex = 1, measuredExtent = 3)

        assertEquals(9, index.topPx(1))
        assertEquals(12, index.topPx(2))
        assertEquals(23, index.totalHeightPx())
        assertEquals(1, index.indexAtOffsetPx(10))
        assertEquals(2, index.indexAtOffsetPx(12))
    }

    @Test
    fun separatedExtentIndexKeepsFixedExtentsAuthoritative() {
        val index = PixelSeparatedExtentIndex()
        index.configure(
            itemCount = 2,
            itemExtent = 6,
            separatorExtent = 2,
            estimatedItemExtent = 20,
            estimatedSeparatorExtent = 10,
            measuredHeightsPx = intArrayOf(30, 30, 30),
        )

        index.updateMeasured(virtualIndex = 0, measuredExtent = 40)
        index.updateMeasured(virtualIndex = 1, measuredExtent = 40)

        assertEquals(6, index.extentPx(0))
        assertEquals(2, index.extentPx(1))
        assertEquals(14, index.totalHeightPx())
    }

    @Test
    fun separatedExtentIndexLocatesRemoteRowsInLargeLists() {
        val itemCount = 100_000
        val index = PixelSeparatedExtentIndex()
        index.configure(
            itemCount = itemCount,
            itemExtent = null,
            separatorExtent = null,
            estimatedItemExtent = 5,
            estimatedSeparatorExtent = 1,
            measuredHeightsPx = IntArray(itemCount * 2 - 1),
        )

        val targetItem = 90_000
        val targetVirtualIndex = targetItem * 2
        val targetTop = targetItem * 6
        assertEquals(targetVirtualIndex, index.indexAtOffsetPx(targetTop))

        index.updateMeasured(targetVirtualIndex, measuredExtent = 11)
        assertEquals(targetVirtualIndex, index.indexAtOffsetPx(targetTop + 10))
        assertEquals(targetTop + 11, index.topPx(targetVirtualIndex + 1))
    }
}
