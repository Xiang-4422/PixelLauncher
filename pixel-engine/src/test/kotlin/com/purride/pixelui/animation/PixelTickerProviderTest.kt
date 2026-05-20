package com.purride.pixelui.animation

import com.purride.pixelui.host.ManualFrameScheduler
import org.junit.Assert.assertEquals
import org.junit.Test

class PixelTickerProviderTest {

    private val scheduler = ManualFrameScheduler()
    private val provider = PixelTickerProvider(scheduler)

    @Test
    fun noActiveTickerDoesNotScheduleFrame() {
        provider.createTicker { }
        assertEquals(0, scheduler.pendingCount)
    }

    @Test
    fun oneActiveTickerSchedulesExactlyOneFrame() {
        val ticker = provider.createTicker { }
        ticker.start()
        assertEquals(1, scheduler.pendingCount)
    }

    @Test
    fun nActiveTickersStillScheduleOneFrame() {
        val t1 = provider.createTicker { }
        val t2 = provider.createTicker { }
        val t3 = provider.createTicker { }
        t1.start(); t2.start(); t3.start()
        assertEquals(1, scheduler.pendingCount)
    }

    @Test
    fun allTickersReceiveTickOnSingleFrame() {
        val counts = mutableListOf(0, 0)
        val t1 = provider.createTicker { counts[0]++ }
        val t2 = provider.createTicker { counts[1]++ }
        t1.start(); t2.start()
        scheduler.advanceFrame(1_000_000L)
        assertEquals(1, counts[0])
        assertEquals(1, counts[1])
    }

    @Test
    fun stopLastTickerStopsScheduling() {
        val ticker = provider.createTicker { }
        ticker.start()
        scheduler.advanceFrame(1_000_000L)
        ticker.stop()
        // One lingering pending frame may exist; draining it should not reschedule
        scheduler.advanceFrame(2_000_000L)
        assertEquals(0, scheduler.pendingCount)
    }

    @Test
    fun frameRescheduledWhileTickersActive() {
        val ticks = mutableListOf<Long>()
        val ticker = provider.createTicker { elapsed -> ticks += elapsed }
        ticker.start()
        scheduler.advanceFrame(1_000_000L)
        scheduler.advanceFrame(17_000_000L)
        ticker.stop()
        assertEquals(2, ticks.size)
        // Drain any lingering no-op frame; should not reschedule afterward
        scheduler.advanceFrame(18_000_000L)
        assertEquals(0, scheduler.pendingCount)
    }
}
