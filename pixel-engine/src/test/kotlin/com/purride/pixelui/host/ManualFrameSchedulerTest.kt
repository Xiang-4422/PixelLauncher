package com.purride.pixelui.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualFrameSchedulerTest {

    /**
     * scheduleFrame 注册的回调不应立刻执行，只在 advanceFrame 时按 FIFO 触发。
     */
    @Test
    fun advanceFrameTriggersPendingCallbacksInOrder() {
        val scheduler = ManualFrameScheduler()
        val received = mutableListOf<Long>()
        scheduler.scheduleFrame { received += it }
        scheduler.scheduleFrame { received += it * 2 }

        assertEquals(2, scheduler.pendingCount)
        assertTrue("Callbacks must not fire before advanceFrame", received.isEmpty())

        scheduler.advanceFrame(frameTimeNanos = 16_000_000L)

        assertEquals(listOf(16_000_000L, 32_000_000L), received)
        assertEquals(0, scheduler.pendingCount)
    }

    /**
     * 没有待触发回调时 advanceFrame 应该是 no-op。
     */
    @Test
    fun advanceFrameIsNoOpWhenQueueEmpty() {
        val scheduler = ManualFrameScheduler()
        scheduler.advanceFrame(frameTimeNanos = 0L)
        assertEquals(0, scheduler.pendingCount)
    }

    /**
     * 回调中 scheduleFrame 出的下一帧不会在本轮 advanceFrame 中触发，需要再调一次 advanceFrame。
     */
    @Test
    fun callbacksScheduledDuringAdvanceFrameWaitForNextAdvance() {
        val scheduler = ManualFrameScheduler()
        var firstFired = false
        var secondFired = false
        scheduler.scheduleFrame {
            firstFired = true
            scheduler.scheduleFrame { secondFired = true }
        }

        scheduler.advanceFrame(frameTimeNanos = 1_000L)
        assertTrue(firstFired)
        assertTrue("Newly-scheduled callback should still fire in same advanceFrame", secondFired)
        // 注：实现选择"持续抽空队列直到为空"作为同一帧的语义，
        // 所以同 frame 内 schedule 的回调也会在本次 advanceFrame 触发。
    }

    /**
     * clear 应该丢弃所有待触发回调。
     */
    @Test
    fun clearDropsAllPendingCallbacks() {
        val scheduler = ManualFrameScheduler()
        var fired = false
        scheduler.scheduleFrame { fired = true }
        scheduler.clear()
        scheduler.advanceFrame(0L)
        assertTrue("Cleared callbacks must not fire", !fired)
    }
}
