package com.purride.pixelui

import com.purride.pixelui.state.PixelListController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelListControllerTest {

    private val controller = PixelListController()

    @Test
    fun dragByClampsScrollOffsetWithinContentRange() {
        val state = controller.create()

        controller.dragBy(
            state = state,
            deltaPx = -18f,
            viewportHeightPx = 20,
            contentHeightPx = 50,
        )
        assertEquals(18f, state.scrollOffsetPx, 0.001f)

        controller.dragBy(
            state = state,
            deltaPx = -40f,
            viewportHeightPx = 20,
            contentHeightPx = 50,
        )
        assertEquals(30f, state.scrollOffsetPx, 0.001f)

        controller.dragBy(
            state = state,
            deltaPx = 80f,
            viewportHeightPx = 20,
            contentHeightPx = 50,
        )
        assertEquals(0f, state.scrollOffsetPx, 0.001f)
    }

    @Test
    fun syncClampsInitialOffsetToViewportUpperBound() {
        val state = controller.create(initialScrollOffsetPx = 80f)

        controller.sync(
            state = state,
            viewportHeightPx = 24,
            contentHeightPx = 40,
        )

        assertEquals(16f, state.scrollOffsetPx, 0.001f)
    }

    @Test
    fun canConsumeDragReflectsTopAndBottomBoundaries() {
        val state = controller.create(initialScrollOffsetPx = 10f)

        controller.sync(
            state = state,
            viewportHeightPx = 20,
            contentHeightPx = 50,
        )
        assertTrue(controller.canConsumeDrag(state, deltaPx = 6f, viewportHeightPx = 20, contentHeightPx = 50))
        assertTrue(controller.canConsumeDrag(state, deltaPx = -6f, viewportHeightPx = 20, contentHeightPx = 50))

        controller.scrollTo(
            state = state,
            targetOffsetPx = 0f,
            viewportHeightPx = 20,
            contentHeightPx = 50,
        )
        assertFalse(controller.canConsumeDrag(state, deltaPx = 6f, viewportHeightPx = 20, contentHeightPx = 50))

        controller.scrollTo(
            state = state,
            targetOffsetPx = 30f,
            viewportHeightPx = 20,
            contentHeightPx = 50,
        )
        assertFalse(controller.canConsumeDrag(state, deltaPx = -6f, viewportHeightPx = 20, contentHeightPx = 50))
    }

    @Test
    fun saveAndRestoreStateKeepsClampedScrollOffsetAndClearsTransientMotion() {
        val source = controller.create(initialScrollOffsetPx = 18f)
        controller.sync(source, viewportHeightPx = 20, contentHeightPx = 80)
        val savedState = controller.saveState(source)

        val restored = controller.create()
        controller.startDrag(restored)
        controller.restoreState(
            state = restored,
            savedState = savedState,
            viewportHeightPx = 20,
            contentHeightPx = 50,
        )

        assertEquals(18f, restored.scrollOffsetPx, 0.001f)
        assertFalse(restored.isDragging)
        assertFalse(restored.isSettling)

        controller.restoreState(
            state = restored,
            savedState = savedState.copy(scrollOffsetPx = 100f),
            viewportHeightPx = 20,
            contentHeightPx = 50,
        )
        assertEquals(30f, restored.scrollOffsetPx, 0.001f)
    }

    @Test
    fun scrollItemIntoViewMovesOnlyWhenTargetLeavesViewport() {
        val state = controller.create(initialScrollOffsetPx = 0f)
        state.itemTopOffsetsPx = intArrayOf(0, 12, 24, 36)
        state.itemHeightsPx = intArrayOf(8, 8, 8, 8)

        controller.sync(
            state = state,
            viewportHeightPx = 20,
            contentHeightPx = 44,
        )

        controller.scrollItemIntoView(state, itemIndex = 1)
        assertEquals(0f, state.scrollOffsetPx, 0.001f)

        controller.scrollItemIntoView(state, itemIndex = 2)
        assertEquals(12f, state.scrollOffsetPx, 0.001f)

        controller.scrollItemIntoView(state, itemIndex = 0)
        assertEquals(0f, state.scrollOffsetPx, 0.001f)
    }

    @Test
    fun scrollItemIntoViewHandlesVariableItemHeightsAndSpacingOffsets() {
        val state = controller.create(initialScrollOffsetPx = 0f)
        state.itemTopOffsetsPx = intArrayOf(0, 10, 28, 44)
        state.itemHeightsPx = intArrayOf(8, 16, 12, 20)

        controller.sync(
            state = state,
            viewportHeightPx = 24,
            contentHeightPx = 64,
        )

        controller.scrollItemIntoView(state, itemIndex = 2)
        assertEquals(16f, state.scrollOffsetPx, 0.001f)

        controller.scrollItemIntoView(state, itemIndex = 3)
        assertEquals(40f, state.scrollOffsetPx, 0.001f)

        controller.scrollItemIntoView(state, itemIndex = 1)
        assertEquals(10f, state.scrollOffsetPx, 0.001f)
    }

    @Test
    fun scrollItemIntoViewIgnoresInvalidItemIndex() {
        val state = controller.create(initialScrollOffsetPx = 8f)
        state.itemTopOffsetsPx = intArrayOf(0, 12)
        state.itemHeightsPx = intArrayOf(8, 8)

        controller.sync(
            state = state,
            viewportHeightPx = 10,
            contentHeightPx = 24,
        )
        controller.scrollItemIntoView(state, itemIndex = 4)

        assertEquals(8f, state.scrollOffsetPx, 0.001f)
    }

    /**
     * 变高 lazy list 远端定位：目标 item 尚未被真实测量时，控制器先按 estimated
     * 高度滚动到大致位置，并把 itemIndex 记入 [PixelListState.pendingScrollIntoViewItemIndex]
     * 供下一帧 layout 重入校正。
     */
    @Test
    fun scrollItemIntoViewMarksPendingWhenTargetUnmeasured() {
        val state = controller.create(initialScrollOffsetPx = 0f)
        state.measuredItemHeightsPx = IntArray(10) // 全部 0 = 未测量
        // 假设 itemTopOffsetsPx / itemHeightsPx 已由 viewport.layout() 用 estimated 写入
        state.itemTopOffsetsPx = IntArray(10) { it * 20 }
        state.itemHeightsPx = IntArray(10) { 20 }

        controller.sync(
            state = state,
            viewportHeightPx = 40,
            contentHeightPx = 200,
        )

        controller.scrollItemIntoView(state, itemIndex = 7)

        // 已按 estimated 滚到位置（item 7 底部 160，视口高 40 → offset 120）
        assertEquals(120f, state.scrollOffsetPx, 0.001f)
        // 但目标项未测量 → pending 标志被打上
        assertEquals(7, state.pendingScrollIntoViewItemIndex)
    }

    /**
     * 目标 item 已经被测量过：控制器立即清空 pending 标志，避免下一帧 layout
     * 无意义地重入 scrollItemIntoView。
     */
    @Test
    fun scrollItemIntoViewClearsPendingWhenTargetMeasured() {
        val state = controller.create(initialScrollOffsetPx = 0f)
        state.measuredItemHeightsPx = IntArray(4) { 16 } // 全部已测
        state.itemTopOffsetsPx = intArrayOf(0, 16, 32, 48)
        state.itemHeightsPx = intArrayOf(16, 16, 16, 16)
        // 预先打上 pending 标志，模拟上一次远端定位遗留
        state.pendingScrollIntoViewItemIndex = 2

        controller.sync(
            state = state,
            viewportHeightPx = 32,
            contentHeightPx = 64,
        )

        controller.scrollItemIntoView(state, itemIndex = 3)

        assertEquals(null, state.pendingScrollIntoViewItemIndex)
    }

    /**
     * 模拟「先按 estimated 滚远端 → 测量到位 → 二次重入校正」的完整两帧流程。
     * 第二次调用使用真实 measured 高度回填的 itemTopOffsetsPx / itemHeightsPx，
     * 应当落到与估算偏移不同的更准确位置，并清空 pending 标志。
     */
    @Test
    fun scrollItemIntoViewRemeasureCorrectsRemoteTarget() {
        val state = controller.create(initialScrollOffsetPx = 0f)
        // 第一帧：item 0..6 全部未测量，目标 7 未测量
        state.measuredItemHeightsPx = IntArray(10)
        state.itemTopOffsetsPx = IntArray(10) { it * 20 }  // estimated 20px
        state.itemHeightsPx = IntArray(10) { 20 }
        controller.sync(state = state, viewportHeightPx = 40, contentHeightPx = 200)

        controller.scrollItemIntoView(state, itemIndex = 7)
        val firstFrameOffset = state.scrollOffsetPx
        assertEquals(7, state.pendingScrollIntoViewItemIndex)

        // 第二帧：item 0..7 都被真实测量为 30px（比 estimated 大）；
        // viewport.layout() 重建 itemTopOffsetsPx / itemHeightsPx 并发现 pending 命中。
        state.measuredItemHeightsPx = IntArray(10) { if (it <= 7) 30 else 0 }
        state.itemTopOffsetsPx = IntArray(10) { idx ->
            (0 until idx).sumOf { i -> if (i <= 7) 30 else 20 }
        }
        state.itemHeightsPx = IntArray(10) { if (it <= 7) 30 else 20 }
        controller.sync(state = state, viewportHeightPx = 40, contentHeightPx = 290)

        controller.scrollItemIntoView(state, itemIndex = 7)

        // 真实偏移：item 7 顶部 = 7*30 = 210；底部 240；offset = 240 - 40 = 200
        assertEquals(200f, state.scrollOffsetPx, 0.001f)
        // 二次校正后目标已测量 → pending 清空
        assertEquals(null, state.pendingScrollIntoViewItemIndex)
        // 二次偏移确实与第一次不同（证明发生了校正）
        assert(state.scrollOffsetPx != firstFrameOffset)
    }

    /**
     * 非变高路径（measuredItemHeightsPx 为空，比如固定高度 lazy 或 eager 列表）
     * 不应当被打 pending 标志，避免下一帧无谓重入。
     */
    @Test
    fun scrollItemIntoViewDoesNotMarkPendingForFixedHeightPath() {
        val state = controller.create(initialScrollOffsetPx = 0f)
        // measuredItemHeightsPx 留空，模拟固定高度 lazy / eager 路径
        state.itemTopOffsetsPx = intArrayOf(0, 20, 40, 60)
        state.itemHeightsPx = intArrayOf(20, 20, 20, 20)
        controller.sync(state = state, viewportHeightPx = 40, contentHeightPx = 80)

        controller.scrollItemIntoView(state, itemIndex = 3)

        assertEquals(null, state.pendingScrollIntoViewItemIndex)
    }

    @Test
    fun endDragStartsSettlingWhenVelocityIsLargeEnough() {
        val state = controller.create(initialScrollOffsetPx = 10f)

        controller.endDrag(
            state = state,
            velocityPxPerSecond = -600f,
            viewportHeightPx = 20,
            contentHeightPx = 80,
        )

        assertFalse(state.isDragging)
        assertTrue(state.isSettling)
        assertEquals(-600f, state.scrollVelocityPxPerSecond, 0.001f)
    }

    @Test
    fun stepMovesScrollOffsetAndGraduallySlowsDown() {
        val state = controller.create(initialScrollOffsetPx = 10f)

        controller.endDrag(
            state = state,
            velocityPxPerSecond = -600f,
            viewportHeightPx = 20,
            contentHeightPx = 120,
        )

        controller.step(
            state = state,
            deltaMs = 100,
            viewportHeightPx = 20,
            contentHeightPx = 120,
        )

        assertEquals(70f, state.scrollOffsetPx, 0.001f)
        assertEquals(-360f, state.scrollVelocityPxPerSecond, 0.001f)
        assertTrue(state.isSettling)
    }

    @Test
    fun stepStopsSettlingAtScrollBoundary() {
        val state = controller.create(initialScrollOffsetPx = 55f)

        controller.endDrag(
            state = state,
            velocityPxPerSecond = -600f,
            viewportHeightPx = 20,
            contentHeightPx = 80,
        )

        controller.step(
            state = state,
            deltaMs = 100,
            viewportHeightPx = 20,
            contentHeightPx = 80,
        )

        assertEquals(60f, state.scrollOffsetPx, 0.001f)
        assertFalse(state.isSettling)
        assertEquals(0f, state.scrollVelocityPxPerSecond, 0.001f)
    }

    @Test
    fun stepStopsSettlingWhenVelocityCrossesZero() {
        val state = controller.create(initialScrollOffsetPx = 30f)

        controller.endDrag(
            state = state,
            velocityPxPerSecond = 60f,
            viewportHeightPx = 20,
            contentHeightPx = 100,
        )

        controller.step(
            state = state,
            deltaMs = 100,
            viewportHeightPx = 20,
            contentHeightPx = 100,
        )

        assertFalse(state.isSettling)
        assertEquals(0f, state.scrollVelocityPxPerSecond, 0.001f)
        assertEquals(24f, state.scrollOffsetPx, 0.001f)
    }

    @Test
    fun stepStopsSettlingInsideSnapEpsilonNearBoundary() {
        val controller = PixelListController(
            physics = PixelScrollPhysics(
                decelerationPxPerSecondSquared = 2400f,
                minFlingVelocityPxPerSecond = 12f,
                snapEpsilonPx = 0.5f,
            ),
        )
        val state = controller.create(initialScrollOffsetPx = 0.2f)

        controller.endDrag(
            state = state,
            velocityPxPerSecond = -20f,
            viewportHeightPx = 20,
            contentHeightPx = 80,
        )
        controller.step(
            state = state,
            deltaMs = 1,
            viewportHeightPx = 20,
            contentHeightPx = 80,
        )

        assertFalse(state.isSettling)
        assertEquals(0f, state.scrollVelocityPxPerSecond, 0.001f)
    }

    @Test
    fun endDragIgnoresVelocityBelowPhysicsThreshold() {
        val controller = PixelListController(
            physics = PixelScrollPhysics(minFlingVelocityPxPerSecond = 100f),
        )
        val state = controller.create(initialScrollOffsetPx = 10f)

        controller.endDrag(
            state = state,
            velocityPxPerSecond = -80f,
            viewportHeightPx = 20,
            contentHeightPx = 80,
        )

        assertFalse(state.isSettling)
        assertEquals(0f, state.scrollVelocityPxPerSecond, 0.001f)
    }

    @Test
    fun customPhysicsCanEnableResistedOverscrollDuringDrag() {
        val controller = PixelListController(
            physics = PixelScrollPhysics(
                bounceEnabled = true,
                bounceOverscrollLimitPx = 10f,
                bounceResistance = 0.5f,
            ),
        )
        val state = controller.create()

        controller.dragBy(
            state = state,
            deltaPx = 8f,
            viewportHeightPx = 20,
            contentHeightPx = 50,
        )

        assertEquals(-4f, state.scrollOffsetPx, 0.001f)
    }

    @Test
    fun customPhysicsAppliesResistedOverscrollAtBottomBoundary() {
        val controller = PixelListController(
            physics = PixelScrollPhysics(
                bounceEnabled = true,
                bounceOverscrollLimitPx = 10f,
                bounceResistance = 0.5f,
            ),
        )
        val state = controller.create(initialScrollOffsetPx = 30f)

        controller.dragBy(
            state = state,
            deltaPx = -8f,
            viewportHeightPx = 20,
            contentHeightPx = 50,
        )

        assertEquals(34f, state.scrollOffsetPx, 0.001f)
    }

    @Test
    fun flingExtensionStartsSettlingFromCachedViewportMetrics() {
        val state = controller.create(initialScrollOffsetPx = 10f)

        controller.sync(
            state = state,
            viewportHeightPx = 20,
            contentHeightPx = 80,
        )
        controller.fling(
            state = state,
            velocityPxPerSecond = -300f,
        )

        assertTrue(state.isSettling)
        assertEquals(-300f, state.scrollVelocityPxPerSecond, 0.001f)
    }
}
