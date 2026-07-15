package com.purride.pixelui.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
     * 与真实 Choreographer 行为一致：每次 postFrameCallback 只在下一个 vsync 触发。
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
        assertTrue("Newly-scheduled callback must wait for next advanceFrame", !secondFired)
        assertEquals(1, scheduler.pendingCount)

        scheduler.advanceFrame(frameTimeNanos = 2_000L)
        assertTrue(secondFired)
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

    /** Cancellable registrations are physically removed from the manual queue. */
    @Test
    fun cancellableCallbackIsRemovedBeforeAdvance() {
        // Explicit capability type proves the additive scheduler interface is consumable.
        val scheduler: PixelCancellableFrameScheduler = ManualFrameScheduler()
        // Delivery flag must remain false after cancellation and source advancement.
        var fired = false
        val registration: PixelFrameCallbackRegistration = scheduler.scheduleCancellableFrame {
            fired = true
        }

        assertTrue(registration.isPending)
        assertTrue(registration.cancel())
        assertFalse(registration.isPending)
        assertFalse(registration.cancel())
        val manualScheduler = scheduler as ManualFrameScheduler
        assertEquals(0, manualScheduler.pendingCount)
        manualScheduler.advanceFrame(1L)
        assertFalse(fired)
    }

    /** Third-party legacy schedulers receive logical cancellation without an ABI change. */
    @Test
    fun legacySchedulerFallbackSuppressesCancelledCallback() {
        // Minimal scheduler implements only the original Unit-returning method.
        val scheduler = LegacyTestFrameScheduler()
        // Extension registration guards the old callback when physical removal is unavailable.
        var fired = false
        val registration: PixelFrameCallbackRegistration =
            scheduler.scheduleCancellableFrame { fired = true }

        assertTrue(registration.cancel())
        scheduler.fire(2L)
        assertFalse(fired)
    }

    /** Test scheduler representing an existing third-party implementation of the old interface. */
    private class LegacyTestFrameScheduler : PixelFrameScheduler {
        /** Single callback retained by this minimal compatibility fixture. */
        private var callback: ((Long) -> Unit)? = null

        /** Implements the unchanged historical scheduler method. */
        override fun scheduleFrame(callback: (Long) -> Unit) {
            this.callback = callback
        }

        /** Delivers the retained callback once. */
        fun fire(frameTimeNanos: Long) {
            val pendingCallback = callback
            callback = null
            pendingCallback?.invoke(frameTimeNanos)
        }
    }
}
