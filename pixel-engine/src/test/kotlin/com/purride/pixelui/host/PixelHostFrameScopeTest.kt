package com.purride.pixelui.host

import com.purride.pixelui.animation.PixelTickerProviderDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lifecycle, isolation, and diagnostics contract for [PixelHostFrameScope]. */
class PixelHostFrameScopeTest {
    /** Verifies a 60-second background interval contributes zero active animation time. */
    @Test
    fun sixtySecondPauseFreezesTimeAndResumeContinuesFromThePreviousValue() {
        // Manual source timestamps make the long pause deterministic and instantaneous.
        val scheduler = ManualFrameScheduler()
        // One Host-owned scope controls both source requests and ticker active time.
        val scope = PixelHostFrameScope(scheduler)
        // Ticker values expose the exact elapsed active-time sequence.
        val ticks = mutableListOf<Long>()
        // Active ticker continuously requests the next frame through the scope.
        val ticker = scope.tickerProvider.createTicker { elapsedNanos -> ticks += elapsedNanos }
        ticker.start()

        scheduler.advanceFrame(1_000_000_000L)
        scheduler.advanceFrame(1_016_000_000L)
        assertEquals(listOf(0L, 16_000_000L), ticks)
        assertEquals(1, scheduler.pendingCount)

        scope.pause()
        assertEquals(0, scheduler.pendingCount)
        // Advancing the source clock by 60 seconds while paused cannot invoke or schedule work.
        scheduler.advanceFrame(61_016_000_000L)
        assertEquals(listOf(0L, 16_000_000L), ticks)
        assertEquals(0, scheduler.pendingCount)

        scope.resume()
        assertEquals(1, scheduler.pendingCount)
        // First resumed frame reanchors raw time and repeats the previous active elapsed value.
        scheduler.advanceFrame(61_016_000_000L)
        // Following source delta advances continuously from the pre-pause value.
        scheduler.advanceFrame(61_032_000_000L)

        assertEquals(listOf(0L, 16_000_000L, 16_000_000L, 32_000_000L), ticks)
        val diagnostics: PixelHostFrameScopeDiagnostics = scope.diagnostics()
        assertFalse(diagnostics.isPaused)
        assertEquals(32_000_000L, diagnostics.activeTimeNanos)
    }

    /** Verifies disposal during a frame prevents later tickers and listeners in that same frame. */
    @Test
    fun destroyDuringFrameCancelsRemainingSameFrameWork() {
        // Manual source exposes deterministic callback ordering.
        val scheduler = ManualFrameScheduler()
        // Scope owns every object that must be released by destroy.
        val scope = PixelHostFrameScope(scheduler)
        // First ticker performs terminal Host-style disposal from inside dispatch.
        var disposingTickerTicks = 0
        // Later ticker must never observe the partially disposed frame.
        var laterTickerTicks = 0
        // Repeating listener also must not run after same-frame disposal.
        var listenerTicks = 0
        val disposingTicker = scope.tickerProvider.createTicker {
            disposingTickerTicks += 1
            scope.dispose()
        }
        val laterTicker = scope.tickerProvider.createTicker { laterTickerTicks += 1 }
        val listener: PixelFrameListenerRegistration = scope.addFrameListener { listenerTicks += 1 }
        disposingTicker.start()
        laterTicker.start()

        scheduler.advanceFrame(10_000_000L)

        assertEquals(1, disposingTickerTicks)
        assertEquals(0, laterTickerTicks)
        assertEquals(0, listenerTicks)
        assertFalse(listener.isActive)
        assertTrue(disposingTicker.isDisposed)
        assertTrue(laterTicker.isDisposed)
        assertEquals(0, scheduler.pendingCount)
        val diagnostics: PixelHostFrameScopeDiagnostics = scope.diagnostics()
        assertTrue(diagnostics.isDisposed)
        assertEquals(0, diagnostics.pendingCallbackCount)
        assertEquals(0, diagnostics.frameListenerCount)
        assertEquals(0, diagnostics.activeTickerCount)
        assertEquals(0, diagnostics.liveTickerCount)
        assertFalse(diagnostics.sourceFramePending)
    }

    /** Verifies two Host scopes sharing one source remain independently pausable and disposable. */
    @Test
    fun twoScopesSharingOneSchedulerRemainIsolated() {
        // Shared source simulates two Hosts attached to the same platform frame clock.
        val scheduler = ManualFrameScheduler()
        // Each Host owns a separate active-time and ticker boundary.
        val firstScope = PixelHostFrameScope(scheduler)
        val secondScope = PixelHostFrameScope(scheduler)
        // Independent tick sequences prove no provider or callback is shared.
        val firstTicks = mutableListOf<Long>()
        val secondTicks = mutableListOf<Long>()
        val firstTicker = firstScope.tickerProvider.createTicker { elapsed -> firstTicks += elapsed }
        val secondTicker = secondScope.tickerProvider.createTicker { elapsed -> secondTicks += elapsed }
        firstTicker.start()
        secondTicker.start()

        assertEquals(2, scheduler.pendingCount)
        scheduler.advanceFrame(1_000_000L)
        firstScope.pause()
        assertEquals(1, scheduler.pendingCount)
        scheduler.advanceFrame(17_000_000L)

        assertEquals(listOf(0L), firstTicks)
        assertEquals(listOf(0L, 16_000_000L), secondTicks)
        firstScope.resume()
        assertEquals(2, scheduler.pendingCount)
        scheduler.advanceFrame(33_000_000L)

        assertEquals(listOf(0L, 0L), firstTicks)
        assertEquals(listOf(0L, 16_000_000L, 32_000_000L), secondTicks)
        firstScope.dispose()
        assertEquals(1, scheduler.pendingCount)
        scheduler.advanceFrame(49_000_000L)
        assertEquals(2, firstTicks.size)
        assertEquals(4, secondTicks.size)
        secondScope.dispose()
        assertEquals(0, scheduler.pendingCount)
    }

    /** Verifies pause retains logical callbacks but schedules no source frame until resume. */
    @Test
    fun callbacksQueuedDuringPauseWaitWithoutScheduling() {
        // Manual source exposes pending upstream requests directly.
        val scheduler = ManualFrameScheduler()
        // Scope begins paused before any callback is registered.
        val scope = PixelHostFrameScope(scheduler)
        scope.pause()
        // Callback registration remains pending without touching the source queue.
        var receivedTime: Long? = null
        val registration: PixelFrameCallbackRegistration = scope.scheduleFrame { time ->
            receivedTime = time
        }

        assertTrue(registration.isPending)
        assertEquals(0, scheduler.pendingCount)
        scope.resume()
        assertEquals(1, scheduler.pendingCount)
        scheduler.advanceFrame(5_000_000_000L)
        assertEquals(0L, receivedTime)
        assertFalse(registration.isPending)
    }

    /** Verifies diagnostics prove complete release after 10,000 active ticker/listener pairs. */
    @Test
    fun tenThousandOwnedObjectsAreReleasedByScopeDispose() {
        // One manual source should still receive only one coalesced scope request.
        val scheduler = ManualFrameScheduler()
        // Scope owns every ticker and listener created by this stress fixture.
        val scope = PixelHostFrameScope(scheduler)
        repeat(10_000) {
            scope.tickerProvider.createTicker { }.start()
            scope.addFrameListener { }
        }

        assertEquals(1, scheduler.pendingCount)
        assertEquals(10_000, scope.tickerProvider.activeTickerCount)
        scope.dispose()

        val diagnostics: PixelHostFrameScopeDiagnostics = scope.diagnostics()
        val tickerDiagnostics: PixelTickerProviderDiagnostics = scope.tickerProvider.diagnostics()
        assertTrue(diagnostics.isDisposed)
        assertEquals(0, diagnostics.pendingCallbackCount)
        assertEquals(0, diagnostics.frameListenerCount)
        assertEquals(0, diagnostics.activeTickerCount)
        assertEquals(0, diagnostics.liveTickerCount)
        assertFalse(diagnostics.sourceFramePending)
        assertEquals(10_000L, diagnostics.registeredListenerCount)
        assertEquals(10_000L, diagnostics.disposedListenerCount)
        assertEquals(10_000L, tickerDiagnostics.createdTickerCount)
        assertEquals(10_000L, tickerDiagnostics.disposedTickerCount)
        assertEquals(0, scheduler.pendingCount)
    }
}
