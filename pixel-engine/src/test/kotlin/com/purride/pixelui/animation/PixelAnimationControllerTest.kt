package com.purride.pixelui.animation

import com.purride.pixelui.host.ManualFrameScheduler
import com.purride.pixelui.host.PixelHostFrameScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

/** Virtual-clock behavior tests for [PixelAnimationController]. */
class PixelAnimationControllerTest {

    /** Source scheduler whose absolute frame timestamps are controlled by each test. */
    private val scheduler = ManualFrameScheduler()

    /** Ticker provider used by the default controller fixture. */
    private val provider = PixelTickerProvider(scheduler)

    /** Full-range duration used by the default controller fixture. */
    private val duration = 1_000.milliseconds

    /** Creates one normalized controller attached to the default virtual scheduler. */
    private fun makeController(initial: Float = 0f): PixelAnimationController {
        return PixelAnimationController(duration = duration, vsync = provider, initialValue = initial)
    }

    /** Dispatches the next pending frame at absolute virtual time [ms]. */
    private fun advanceMs(ms: Long) {
        scheduler.advanceFrame(ms * 1_000_000L)
    }

    /** Verifies the default lower-bound value, status, and inactive ticker. */
    @Test
    fun defaultStatusAndValue() {
        val controller = makeController()

        assertEquals(PixelAnimationStatus.Dismissed, controller.status)
        assertEquals(0f, controller.value, 1e-4f)
        assertFalse(controller.isAnimating)
    }

    /** Verifies initial values are clamped and the upper boundary is completed. */
    @Test
    fun initialValueIsClampedAndMappedToBoundaryStatus() {
        val completedController = makeController(initial = 2f)
        val dismissedController = makeController(initial = -2f)
        val interiorController = makeController(initial = 0.4f)

        assertEquals(1f, completedController.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Completed, completedController.status)
        assertEquals(0f, dismissedController.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Dismissed, dismissedController.status)
        assertEquals(0.4f, interiorController.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Dismissed, interiorController.status)
    }

    /** Verifies a forward command exposes its direction before the first frame. */
    @Test
    fun forwardSetsStatusForward() {
        val controller = makeController()

        controller.forward()

        assertEquals(PixelAnimationStatus.Forward, controller.status)
        assertTrue(controller.isAnimating)
    }

    /** Verifies exact values and statuses at 0/25/50/75/100 percent virtual time. */
    @Test
    fun forwardProducesDeterministicQuarterFrameStates() {
        val localScheduler = ManualFrameScheduler()
        val localProvider = PixelTickerProvider(localScheduler)
        val controller = PixelAnimationController(100.milliseconds, localProvider)

        controller.forward()
        localScheduler.advanceFrame(0L)
        assertEquals(0f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Forward, controller.status)

        localScheduler.advanceFrame(25_000_000L)
        assertEquals(0.25f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Forward, controller.status)

        localScheduler.advanceFrame(50_000_000L)
        assertEquals(0.5f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Forward, controller.status)

        localScheduler.advanceFrame(75_000_000L)
        assertEquals(0.75f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Forward, controller.status)

        localScheduler.advanceFrame(100_000_000L)
        assertEquals(1f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Completed, controller.status)
        assertFalse(controller.isAnimating)
    }

    /** Verifies a full forward segment settles exactly at the upper boundary. */
    @Test
    fun forwardCompletesAtEnd() {
        val controller = makeController()

        controller.forward()
        advanceMs(0)
        advanceMs(1_000)

        assertEquals(PixelAnimationStatus.Completed, controller.status)
        assertEquals(1f, controller.value, 1e-4f)
        assertFalse(controller.isAnimating)
    }

    /** Verifies `forward(from)` applies immediately and scales time by remaining distance. */
    @Test
    fun forwardFromUsesExplicitStartWithoutFirstFrameJump() {
        val controller = makeController()

        controller.forward(from = 0.4f)
        assertEquals(0.4f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Forward, controller.status)

        advanceMs(10)
        assertEquals(0.4f, controller.value, 1e-4f)
        advanceMs(310)
        assertEquals(0.7f, controller.value, 1e-4f)
        advanceMs(610)

        assertEquals(1f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Completed, controller.status)
        assertFalse(controller.isAnimating)
    }

    /** Verifies explicit forward values outside the normalized range are clamped. */
    @Test
    fun forwardFromClampsToNormalizedRange() {
        val controller = makeController(initial = 0.5f)

        controller.forward(from = -4f)
        assertEquals(0f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Forward, controller.status)

        controller.forward(from = Float.POSITIVE_INFINITY)
        assertEquals(1f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Completed, controller.status)
        assertFalse(controller.isAnimating)
    }

    /** Verifies a reverse command exposes its direction before the first frame. */
    @Test
    fun reverseSetsStatusReverse() {
        val controller = makeController(initial = 1f)

        controller.reverse()

        assertEquals(PixelAnimationStatus.Reverse, controller.status)
        assertTrue(controller.isAnimating)
    }

    /** Verifies a full reverse segment settles exactly at the lower boundary. */
    @Test
    fun reverseDismissesAtZero() {
        val controller = makeController(initial = 1f)

        controller.reverse()
        advanceMs(0)
        advanceMs(1_000)

        assertEquals(PixelAnimationStatus.Dismissed, controller.status)
        assertEquals(0f, controller.value, 1e-4f)
        assertFalse(controller.isAnimating)
    }

    /** Verifies `reverse(from)` applies immediately and scales time by remaining distance. */
    @Test
    fun reverseFromUsesExplicitStartWithoutFirstFrameJump() {
        val controller = makeController()

        controller.reverse(from = 0.8f)
        assertEquals(0.8f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Reverse, controller.status)

        advanceMs(0)
        assertEquals(0.8f, controller.value, 1e-4f)
        advanceMs(400)
        assertEquals(0.4f, controller.value, 1e-4f)
        advanceMs(800)

        assertEquals(0f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Dismissed, controller.status)
        assertFalse(controller.isAnimating)
    }

    /** Verifies explicit reverse values outside the normalized range are clamped. */
    @Test
    fun reverseFromClampsToNormalizedRange() {
        val controller = makeController(initial = 0.5f)

        controller.reverse(from = 4f)
        assertEquals(1f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Reverse, controller.status)

        controller.reverse(from = Float.NEGATIVE_INFINITY)
        assertEquals(0f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Dismissed, controller.status)
        assertFalse(controller.isAnimating)
    }

    /** Verifies reversing an in-flight forward segment preserves the displayed value. */
    @Test
    fun reverseDuringForwardRetargetsContinuously() {
        val controller = makeController()

        controller.forward()
        advanceMs(0)
        advanceMs(250)
        assertEquals(0.25f, controller.value, 1e-4f)

        controller.reverse()
        assertEquals(0.25f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Reverse, controller.status)
        advanceMs(250)
        assertEquals(0.25f, controller.value, 1e-4f)
        advanceMs(375)
        assertEquals(0.125f, controller.value, 1e-4f)
        advanceMs(500)

        assertEquals(0f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Dismissed, controller.status)
    }

    /** Verifies repeating a same-direction command preserves value and starts a fresh segment. */
    @Test
    fun repeatedForwardDoesNotJumpOrReuseStaleElapsedTime() {
        val controller = makeController()

        controller.forward()
        advanceMs(0)
        advanceMs(250)
        controller.forward()

        assertEquals(0.25f, controller.value, 1e-4f)
        advanceMs(250)
        assertEquals(0.25f, controller.value, 1e-4f)
        advanceMs(625)
        assertEquals(0.625f, controller.value, 1e-4f)
        advanceMs(1_000)

        assertEquals(1f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Completed, controller.status)
    }

    /** Verifies repeated reverse uses the current value and a fresh elapsed-time origin. */
    @Test
    fun repeatedReverseDoesNotJumpOrReuseStaleElapsedTime() {
        val controller = makeController(initial = 1f)

        controller.reverse()
        advanceMs(0)
        advanceMs(250)
        controller.reverse()

        assertEquals(0.75f, controller.value, 1e-4f)
        advanceMs(250)
        assertEquals(0.75f, controller.value, 1e-4f)
        advanceMs(625)
        assertEquals(0.375f, controller.value, 1e-4f)
        advanceMs(1_000)

        assertEquals(0f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Dismissed, controller.status)
    }

    /** Verifies a regular full-range forward animation reaches its midpoint. */
    @Test
    fun valueAtMidpoint() {
        val controller = makeController()

        controller.forward()
        advanceMs(0)
        advanceMs(500)

        assertEquals(0.5f, controller.value, 0.01f)
    }

    /** Verifies forward repeat wraps to zero at the exact period boundary. */
    @Test
    fun repeatForwardLoopsBackToZero() {
        val controller = makeController()

        controller.repeat(reverse = false)
        advanceMs(0)
        advanceMs(1_000)

        assertEquals(PixelAnimationStatus.Forward, controller.status)
        assertEquals(0f, controller.value, 0.01f)
        assertTrue(controller.isAnimating)
    }

    /** Verifies ping-pong repeat enters reverse at the upper boundary. */
    @Test
    fun repeatPingPongReversesAtEnd() {
        val controller = makeController()

        controller.repeat(reverse = true)
        advanceMs(0)
        advanceMs(1_000)

        assertEquals(1f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Reverse, controller.status)
        assertTrue(controller.isAnimating)
    }

    /** Verifies repeat arithmetic remains defined at the largest possible elapsed delta. */
    @Test
    fun repeatHandlesMaximumFrameDeltaWithoutOverflow() {
        val localScheduler = ManualFrameScheduler()
        val localProvider = PixelTickerProvider(localScheduler)
        val controller = PixelAnimationController(1.nanoseconds, localProvider)

        controller.repeat(reverse = true)
        localScheduler.advanceFrame(0L)
        localScheduler.advanceFrame(Long.MAX_VALUE)

        assertEquals(1f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Reverse, controller.status)
        assertTrue(controller.isAnimating)
    }

    /** Verifies stop cancels scheduling while retaining value and direction status. */
    @Test
    fun stopPreservesStateAndIsIdempotent() {
        val controller = makeController()

        controller.forward()
        advanceMs(0)
        advanceMs(400)
        val valueAtStop = controller.value
        controller.stop()
        controller.stop()

        assertEquals(PixelAnimationStatus.Forward, controller.status)
        assertEquals(valueAtStop, controller.value, 1e-4f)
        assertFalse(controller.isAnimating)
        assertEquals(0, scheduler.pendingCount)

        advanceMs(900)
        assertEquals(valueAtStop, controller.value, 1e-4f)
    }

    /** Verifies a stopped controller resumes from its retained value without a first-frame jump. */
    @Test
    fun forwardAfterStopContinuesFromRetainedValue() {
        val controller = makeController()

        controller.forward()
        advanceMs(0)
        advanceMs(400)
        controller.stop()
        controller.forward()
        assertEquals(0.4f, controller.value, 1e-4f)

        advanceMs(900)
        assertEquals(0.4f, controller.value, 1e-4f)
        advanceMs(1_200)
        assertEquals(0.7f, controller.value, 1e-4f)
        advanceMs(1_500)

        assertEquals(1f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Completed, controller.status)
    }

    /** Verifies manual assignment clamps values, updates boundary status, and stops motion. */
    @Test
    fun setValueStopsAnimationAndMapsBoundaryStatus() {
        val controller = makeController()

        controller.forward()
        advanceMs(0)
        advanceMs(200)
        controller.setValue(0.8f)
        assertEquals(0.8f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Dismissed, controller.status)
        assertFalse(controller.isAnimating)

        controller.setValue(5f)
        assertEquals(1f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Completed, controller.status)

        controller.setValue(-5f)
        assertEquals(0f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Dismissed, controller.status)
    }

    /** Verifies reset cancels motion and restores the dismissed lower boundary. */
    @Test
    fun resetGoesToZeroDismissed() {
        val controller = makeController()

        controller.forward()
        advanceMs(0)
        advanceMs(600)
        controller.reset()

        assertEquals(0f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Dismissed, controller.status)
        assertFalse(controller.isAnimating)
        assertEquals(0, scheduler.pendingCount)
    }

    /** Verifies zero duration settles every command synchronously without scheduling frames. */
    @Test
    fun zeroDurationUsesDocumentedSynchronousTerminalStates() {
        val localScheduler = ManualFrameScheduler()
        val localProvider = PixelTickerProvider(localScheduler)
        val controller = PixelAnimationController(Duration.ZERO, localProvider, initialValue = 0.5f)

        controller.forward()
        assertEquals(1f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Completed, controller.status)
        assertFalse(controller.isAnimating)

        controller.reverse(from = 0.75f)
        assertEquals(0f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Dismissed, controller.status)

        controller.repeat(reverse = false)
        assertEquals(1f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Completed, controller.status)

        controller.repeat(reverse = true)
        assertEquals(0f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Dismissed, controller.status)
        assertFalse(controller.isAnimating)
        assertEquals(0, localScheduler.pendingCount)
    }

    /** Verifies negative duration is rejected before a ticker can leak into the provider. */
    @Test
    fun negativeDurationIsRejectedBeforeTickerCreation() {
        val localScheduler = ManualFrameScheduler()
        val localProvider = PixelTickerProvider(localScheduler)

        assertThrows(IllegalArgumentException::class.java) {
            PixelAnimationController((-1).milliseconds, localProvider)
        }

        assertEquals(0, localProvider.liveTickerCount)
        assertEquals(0, localScheduler.pendingCount)
    }

    /** Verifies a maximum frame delta clamps a finite segment exactly to its endpoint. */
    @Test
    fun maximumFrameDeltaCompletesFiniteAnimation() {
        val controller = makeController()

        controller.forward()
        scheduler.advanceFrame(0L)
        scheduler.advanceFrame(Long.MAX_VALUE)

        assertEquals(1f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Completed, controller.status)
        assertFalse(controller.isAnimating)
        assertEquals(0, scheduler.pendingCount)
    }

    /** Verifies invalid NaN inputs fail without changing active controller state. */
    @Test
    fun nanInputsAreRejectedWithoutSideEffects() {
        val controller = makeController()

        assertThrows(IllegalArgumentException::class.java) {
            PixelAnimationController(duration, provider, initialValue = Float.NaN)
        }
        assertEquals(1, provider.liveTickerCount)

        controller.forward()
        assertThrows(IllegalArgumentException::class.java) {
            controller.forward(from = Float.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            controller.reverse(from = Float.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            controller.setValue(Float.NaN)
        }

        assertEquals(0f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Forward, controller.status)
        assertTrue(controller.isAnimating)
    }

    /** Verifies Host pause time is excluded and the resumed first frame is continuous. */
    @Test
    fun hostPauseAndResumePreserveActiveTimeContinuity() {
        val sourceScheduler = ManualFrameScheduler()
        val frameScope = PixelHostFrameScope(sourceScheduler)
        val controller = PixelAnimationController(duration, frameScope.tickerProvider)

        controller.forward()
        sourceScheduler.advanceFrame(0L)
        sourceScheduler.advanceFrame(250_000_000L)
        assertEquals(0.25f, controller.value, 1e-4f)

        frameScope.pause()
        assertEquals(0, sourceScheduler.pendingCount)
        sourceScheduler.advanceFrame(60_000_000_000L)
        assertEquals(0.25f, controller.value, 1e-4f)

        frameScope.resume()
        sourceScheduler.advanceFrame(60_000_000_000L)
        assertEquals(0.25f, controller.value, 1e-4f)
        sourceScheduler.advanceFrame(60_250_000_000L)

        assertEquals(0.5f, controller.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Forward, controller.status)
        assertTrue(controller.isAnimating)
    }

    /** Verifies listeners observe the initial tick and subsequent value progression. */
    @Test
    fun listenersNotifiedOnValueChange() {
        val controller = makeController()
        var notifyCount = 0

        controller.addListener { notifyCount++ }
        controller.forward()
        notifyCount = 0
        advanceMs(0)
        advanceMs(200)

        assertTrue(notifyCount >= 2)
    }

    /** Verifies dispose releases scheduling and remains safe when invoked repeatedly. */
    @Test
    fun disposeStopsTickerAndIsIdempotent() {
        val controller = makeController()

        controller.forward()
        advanceMs(0)
        controller.dispose()
        controller.dispose()
        controller.stop()
        advanceMs(1_000)

        assertFalse(controller.isAnimating)
        assertEquals(0, scheduler.pendingCount)
        assertEquals(0, provider.liveTickerCount)
    }

    /** Verifies mutating commands fail explicitly after terminal controller disposal. */
    @Test
    fun commandsAfterDisposeThrowInsteadOfSilentlyDoingNothing() {
        val controller = makeController()

        controller.dispose()

        assertThrows(IllegalStateException::class.java) { controller.forward() }
        assertThrows(IllegalStateException::class.java) { controller.reverse() }
        assertThrows(IllegalStateException::class.java) { controller.repeat() }
        assertThrows(IllegalStateException::class.java) { controller.reset() }
        assertThrows(IllegalStateException::class.java) { controller.setValue(0.5f) }
    }

    /** Verifies external provider disposal is surfaced by later controller mutations. */
    @Test
    fun commandsAfterProviderDisposeThrowInsteadOfBeingDropped() {
        val controller = makeController()

        provider.dispose()

        assertThrows(IllegalStateException::class.java) { controller.forward() }
        assertThrows(IllegalStateException::class.java) { controller.reverse() }
        assertThrows(IllegalStateException::class.java) { controller.repeat() }
        assertFalse(controller.isAnimating)
    }
}
