package com.purride.pixelui.animation

import com.purride.pixelui.host.ManualFrameScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    /** Provider-level pause excludes long raw-time gaps even without a Host scope wrapper. */
    @Test
    fun pauseFreezesProviderActiveTime() {
        // Tick values reveal provider active time directly.
        val ticks = mutableListOf<Long>()
        // Active ticker keeps requesting source frames until disposal.
        val ticker = provider.createTicker { elapsedNanos -> ticks += elapsedNanos }
        ticker.start()
        scheduler.advanceFrame(1_000_000L)
        scheduler.advanceFrame(17_000_000L)

        provider.pause()
        assertEquals(0, scheduler.pendingCount)
        scheduler.advanceFrame(60_017_000_000L)
        provider.resume()
        scheduler.advanceFrame(60_017_000_000L)
        scheduler.advanceFrame(60_033_000_000L)

        assertEquals(listOf(0L, 16_000_000L, 16_000_000L, 32_000_000L), ticks)
        val diagnostics: PixelTickerProviderDiagnostics = provider.diagnostics()
        assertFalse(diagnostics.isPaused)
        assertEquals(32_000_000L, diagnostics.activeTimeNanos)
    }

    /** Provider disposal releases active and inactive tickers plus its pending callback. */
    @Test
    fun disposeReleasesEveryTickerAndPendingFrame() {
        // One active ticker owns the provider source callback.
        val activeTicker = provider.createTicker { }
        // One inactive ticker proves disposal covers more than the active set.
        val inactiveTicker = provider.createTicker { }
        activeTicker.start()
        assertEquals(1, scheduler.pendingCount)

        provider.dispose()

        assertTrue(activeTicker.isDisposed)
        assertTrue(inactiveTicker.isDisposed)
        assertEquals(0, scheduler.pendingCount)
        val diagnostics: PixelTickerProviderDiagnostics = provider.diagnostics()
        assertTrue(diagnostics.isDisposed)
        assertEquals(0, diagnostics.activeTickerCount)
        assertEquals(0, diagnostics.liveTickerCount)
        assertEquals(2L, diagnostics.createdTickerCount)
        assertEquals(2L, diagnostics.disposedTickerCount)
        assertEquals(0, diagnostics.pendingFrameCallbackCount)
    }
}
