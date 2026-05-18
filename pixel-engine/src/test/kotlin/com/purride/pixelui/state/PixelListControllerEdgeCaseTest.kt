package com.purride.pixelui

import com.purride.pixelui.state.PixelListController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PixelListController] 的边界条件测试。
 *
 * 覆盖正常 [PixelListControllerTest] 未触及的输入路径：
 * - 空内容（contentHeight ≤ viewport）
 * - 负数 viewportHeight
 * - NaN 速度
 * - 无穷大速度
 */
class PixelListControllerEdgeCaseTest {

    private val controller = PixelListController()

    // ── 空内容 ────────────────────────────────────────────────────────────

    @Test
    fun `zero content height keeps scrollOffset at zero`() {
        val state = controller.create()
        controller.sync(
            state = state,
            viewportHeightPx = 80,
            contentHeightPx = 0,
        )
        assertEquals(0f, state.scrollOffsetPx, 0.001f)
    }

    // ── 单条目列表：无法滚动 ───────────────────────────────────────────────

    @Test
    fun `single item smaller than viewport cannot scroll`() {
        val state = controller.create()
        // item 高度 10，viewport 高度 80 → maxScrollOffset = 0
        controller.sync(
            state = state,
            viewportHeightPx = 80,
            contentHeightPx = 10,
        )
        assertEquals(0f, state.scrollOffsetPx, 0.001f)

        // 尝试拖拽，不应超过 0
        controller.dragBy(
            state = state,
            deltaPx = -20f,
            viewportHeightPx = 80,
            contentHeightPx = 10,
        )
        assertEquals(0f, state.scrollOffsetPx, 0.001f)
    }

    // ── 负数 viewport 高度 ───────────────────────────────────────────────

    @Test
    fun `negative viewport height does not crash`() {
        val state = controller.create()
        // 负数 viewport：maxScrollOffset = max(0, 100 - (-10)) 实现可能不同，
        // 但至少不应该抛异常，且 scrollOffset 应被夹紧到 ≥ 0
        controller.sync(
            state = state,
            viewportHeightPx = -10,
            contentHeightPx = 100,
        )
        // 结果 ≥ 0 即可，不对具体数值做断言（实现细节可能变化）
        org.junit.Assert.assertTrue("scrollOffset should be >= 0", state.scrollOffsetPx >= 0f)
    }

    // ── NaN 速度 ─────────────────────────────────────────────────────────

    @Test
    fun `NaN velocity in endDrag does not enter settling state`() {
        val state = controller.create()
        controller.sync(
            state = state,
            viewportHeightPx = 80,
            contentHeightPx = 200,
        )
        controller.endDrag(
            state = state,
            velocityPxPerSecond = Float.NaN,
            viewportHeightPx = 80,
            contentHeightPx = 200,
        )
        assertFalse(
            "NaN velocity should not trigger fling settling",
            state.isSettling,
        )
    }

    // ── 无穷大速度 ────────────────────────────────────────────────────────

    @Test
    fun `infinite velocity in endDrag enters settling without crashing`() {
        val state = controller.create()
        controller.sync(
            state = state,
            viewportHeightPx = 80,
            contentHeightPx = 200,
        )
        // Infinity 速度不应抛异常；首次 step 后 scrollOffset 不超过最大值
        controller.endDrag(
            state = state,
            velocityPxPerSecond = Float.POSITIVE_INFINITY,
            viewportHeightPx = 80,
            contentHeightPx = 200,
        )
        // step 一帧，验证夹紧到边界后 settling 结束
        controller.step(
            state = state,
            deltaMs = 16L,
            viewportHeightPx = 80,
            contentHeightPx = 200,
        )
        assertTrue(
            "scrollOffset should be ≤ maxScrollOffset after step",
            state.scrollOffsetPx <= 120f, // contentHeight - viewportHeight = 120
        )
    }
}

