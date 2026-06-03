package com.purride.pixelui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelTextFieldCursorBlinkTest {
    @Test
    fun cursorBlinkTogglesOnFrameProgression() {
        val controller = PixelTextFieldController()
        val state = controller.create("abc")
        controller.focus(state)
        controller.syncCursorBlinkConfig(state, enabled = true, periodMs = 1_000)

        assertTrue(state.cursorVisible)
        controller.stepCursorBlink(state, 500)
        assertFalse(state.cursorVisible)
        controller.stepCursorBlink(state, 500)
        assertTrue(state.cursorVisible)
    }

    @Test
    fun blinkDisabledKeepsCursorVisible() {
        val controller = PixelTextFieldController()
        val state = controller.create("abc")
        controller.focus(state)
        controller.syncCursorBlinkConfig(state, enabled = false, periodMs = 1_000)

        controller.stepCursorBlink(state, 2_000)

        assertTrue(state.cursorVisible)
    }

    @Test
    fun readOnlyOrDisabledBlinkConfigKeepsCursorVisible() {
        val controller = PixelTextFieldController()
        val state = controller.create("abc")
        controller.focus(state)
        controller.syncCursorBlinkConfig(state, enabled = false, periodMs = 1_000)

        assertTrue(state.cursorVisible)
        controller.stepCursorBlink(state, 500)
        assertTrue(state.cursorVisible)
    }

    /**
     * 半周期（500ms）以内的逐帧 step 不得翻转可见态、不得通知监听者。否则监听该
     * controller 的文本框 widget 会被每帧标脏 → 宿主在聚焦期间 ~60fps 全量重绘
     * （本次修复要消除的空转）。对应 PixelListControllerTest.stepDoesNotNotifyWhenListIsIdle
     * 的光标版本。
     */
    @Test
    fun stepCursorBlinkDoesNotSignalChangeWithinHalfPeriod() {
        val controller = PixelTextFieldController()
        val state = controller.create("abc")
        controller.focus(state)
        controller.syncCursorBlinkConfig(state, enabled = true, periodMs = 1_000)

        var notifications = 0
        controller.addListener { notifications++ }

        var signalled = false
        repeat(30) { signalled = controller.stepCursorBlink(state, 16L) || signalled } // 30×16 = 480ms < 500ms

        assertFalse(signalled)
        assertEquals(0, notifications)
        assertTrue(state.cursorVisible)
    }

    /**
     * 累计跨过半周期边界时恰好翻转一次、通知一次——逐帧 step 既不能提前翻转也
     * 不能反复通知，闪烁节拍必须精确。
     */
    @Test
    fun stepCursorBlinkSignalsChangeOnceAtHalfPeriodBoundary() {
        val controller = PixelTextFieldController()
        val state = controller.create("abc")
        controller.focus(state)
        controller.syncCursorBlinkConfig(state, enabled = true, periodMs = 1_000)

        var notifications = 0
        controller.addListener { notifications++ }

        // 边界前（496ms）：没有任何翻转 / 通知。
        repeat(31) { assertFalse(controller.stepCursorBlink(state, 16L)) } // 31×16 = 496ms < 500ms
        assertEquals(0, notifications)
        assertTrue(state.cursorVisible)

        // 跨过 500ms 边界：翻转一次（可见 → 隐藏），通知一次。
        assertTrue(controller.stepCursorBlink(state, 16L)) // 512ms
        assertEquals(1, notifications)
        assertFalse(state.cursorVisible)
    }

    /**
     * 宿主调度合约：聚焦时 millisUntilNextCursorBlink 给出距下一次翻转的剩余毫秒
     * （onDraw 据此 postInvalidateDelayed，取代每帧 postInvalidateOnAnimation）；
     * 失焦时返回 0L，宿主据此停止调度，循环不再空转。
     */
    @Test
    fun millisUntilNextCursorBlinkCountsDownAndZeroesWhenUnfocused() {
        val controller = PixelTextFieldController()
        val state = controller.create("abc")
        controller.focus(state)
        controller.syncCursorBlinkConfig(state, enabled = true, periodMs = 1_000)

        // 刚聚焦：要等满半周期（500ms）才翻转。
        assertEquals(500L, controller.millisUntilNextCursorBlink(state))

        controller.stepCursorBlink(state, 200L)
        assertEquals(300L, controller.millisUntilNextCursorBlink(state)) // 500 - 200

        // 失焦后归零：宿主不再安排延迟重绘。
        controller.blur(state)
        assertEquals(0L, controller.millisUntilNextCursorBlink(state))
    }
}
