package com.purride.pixelui.widgets

import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionSettings
import com.purride.pixelui.PixelSnackbarQueueController
import com.purride.pixelui.PixelSnackbarQueueItem
import com.purride.pixelui.PixelToastQueueController
import com.purride.pixelui.SnackbarQueue
import com.purride.pixelui.ToastQueue
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.VoidCallback
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** Verifies FIFO notification dwell time, actions, lifecycle pause, and deterministic cleanup. */
class NotificationQueueTest {
    /** Toast items advance in FIFO order using each item's independent active-time timeout. */
    @Test
    fun toastTimeoutAdvancesFifoAndReleasesEveryTicker() {
        /** Queue whose observable head and size prove FIFO advancement. */
        val controller = PixelToastQueueController()
        /** Virtual-frame harness that exposes ticker and scheduler diagnostics. */
        val tester = PixelTester()
        /** First item, configured with the longer initial dwell. */
        val first = controller.enqueue(message = "ONE", timeout = 100.milliseconds)
        /** Second item, whose own timeout begins only after it reaches the head. */
        val second = controller.enqueue(message = "TWO", timeout = 50.milliseconds)

        tester.pumpWidget(
            widget = motionRoot(tester, ToastQueue(controller)),
            logicalWidth = 64,
            logicalHeight = 24,
        )

        assertEquals(first, controller.current)
        assertEquals(1, tester.vsync.liveTickerCount)
        assertTrue(tester.exists(find.byText("ONE")))
        tester.pumpFrame(0)
        tester.pumpFrame(99)
        assertEquals(first, controller.current)

        tester.pumpFrame(1)
        assertEquals(second, controller.current)
        assertFalse(tester.exists(find.byText("ONE")))
        assertTrue(tester.exists(find.byText("TWO")))
        assertEquals(1, tester.vsync.liveTickerCount)

        tester.pumpFrame(0)
        tester.pumpFrame(50)
        assertEquals(0, controller.size)
        assertEquals(0, tester.vsync.activeTickerCount)
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)
        tester.dispose()
    }

    /** Host pause excludes background wall time and resume continues from the retained elapsed value. */
    @Test
    fun toastTimeoutFreezesWhileHostTickerProviderIsPaused() {
        /** Queue retaining the item across the simulated background interval. */
        val controller = PixelToastQueueController()
        /** Virtual-frame harness used to pause and resume Host active time. */
        val tester = PixelTester()
        controller.enqueue(message = "PAUSE", timeout = 100.milliseconds)
        tester.pumpWidget(motionRoot(tester, ToastQueue(controller)), 64, 24)

        tester.pumpFrame(0)
        tester.pumpFrame(40)
        tester.vsync.pause()
        assertEquals(1, tester.vsync.activeTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)

        tester.pumpFrame(500)
        assertEquals(1, controller.size)
        tester.vsync.resume()
        tester.pumpFrame(0)
        tester.pumpFrame(59)
        assertEquals(1, controller.size)

        tester.pumpFrame(1)
        assertEquals(0, controller.size)
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)
        tester.dispose()
    }

    /** Reduce motion and zero animator scale do not collapse an accessibility-readable dwell timeout. */
    @Test
    fun toastDwellTimeIsIndependentFromReduceMotionAndAnimatorScale() {
        /** Queue whose timeout must remain independent from visual motion policy. */
        val controller = PixelToastQueueController()
        /** Virtual-frame harness providing explicit reduced-motion settings. */
        val tester = PixelTester()
        controller.enqueue(message = "READABLE", timeout = 100.milliseconds)
        tester.pumpWidget(
            widget = motionRoot(
                tester = tester,
                child = ToastQueue(controller),
                settings = PixelMotionSettings(
                    animatorDurationScale = 0f,
                    reduceMotion = true,
                ),
            ),
            logicalWidth = 64,
            logicalHeight = 24,
        )

        tester.pumpFrame(0)
        tester.pumpFrame(99)
        assertTrue(tester.exists(find.byText("READABLE")))
        assertEquals(1, controller.size)

        tester.pumpFrame(1)
        assertEquals(0, controller.size)
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** Manual dismissal and clear synchronously cancel old timing ownership before the next frame. */
    @Test
    fun toastManualMutationsCancelOrTransferTimingSynchronously() {
        /** Queue exercised through non-head removal, head removal, and clear. */
        val controller = PixelToastQueueController()
        /** Virtual-frame harness exposing immediate ticker ownership changes. */
        val tester = PixelTester()
        /** Initially visible queue head. */
        val first = controller.enqueue(message = "FIRST", timeout = 1_000.milliseconds)
        /** Non-head item removed without disturbing the current ticker. */
        val removedBeforeDisplay = controller.enqueue(message = "REMOVE", timeout = 1_000.milliseconds)
        /** Successor that receives a new ticker after head dismissal. */
        val third = controller.enqueue(message = "THIRD", timeout = 1_000.milliseconds)
        tester.pumpWidget(motionRoot(tester, ToastQueue(controller)), 64, 24)

        assertEquals(1, tester.vsync.liveTickerCount)
        assertTrue(controller.dismiss(removedBeforeDisplay.id))
        assertEquals(first, controller.current)
        assertEquals(1, tester.vsync.liveTickerCount)
        assertEquals(1L, tester.vsync.diagnostics().createdTickerCount)

        assertTrue(controller.dismissCurrent())
        assertEquals(third, controller.current)
        assertEquals(1, tester.vsync.liveTickerCount)
        assertEquals(2L, tester.vsync.diagnostics().createdTickerCount)
        assertEquals(1L, tester.vsync.diagnostics().disposedTickerCount)

        controller.clear()
        assertEquals(0, controller.size)
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)
        tester.dispose()
    }

    /** Switching colliding controller-local ids restarts Toast dwell and semantic identity. */
    @Test
    fun toastControllerSwitchRestartsTimeoutAndSemanticIdentity() {
        /** Original controller whose first item has already consumed most of its dwell. */
        val originalController = PixelToastQueueController()
        /** Replacement controller whose first item intentionally reuses local id 1. */
        val replacementController = PixelToastQueueController()
        /** Original controller item retained to prove a switch does not dismiss it. */
        val originalItem = originalController.enqueue(message = "OWNER-A", timeout = 100.milliseconds)
        /** Replacement item that must receive a complete independent timeout. */
        val replacementItem = replacementController.enqueue(message = "OWNER-B", timeout = 100.milliseconds)
        /** Observable owner selection that updates one retained ToastQueue key in place. */
        val selectedController = ValueNotifier(originalController)
        /** Virtual-frame harness exposing ticker generations and stable semantic ids. */
        val tester = PixelTester()
        tester.pumpWidget(
            widget = motionRoot(
                tester = tester,
                child = ValueListenableBuilder(selectedController) { _, controller ->
                    ToastQueue(controller = controller, key = "shared-toast-queue")
                },
            ),
            logicalWidth = 72,
            logicalHeight = 24,
        )

        tester.pumpFrame(0)
        tester.pumpFrame(80)
        /** Semantic id allocated to controller A's local item 1. */
        val originalSemanticId = tester.semanticsNodesByLabel("OWNER-A").single().id
        assertEquals(originalItem, originalController.current)
        assertEquals(1L, tester.vsync.diagnostics().createdTickerCount)

        selectedController.value = replacementController
        tester.pumpFrame(0)
        /** Semantic id allocated after controller B replaces the colliding local item id. */
        val replacementSemanticId = tester.semanticsNodesByLabel("OWNER-B").single().id
        assertTrue(originalSemanticId != replacementSemanticId)
        assertEquals(replacementItem, replacementController.current)
        assertEquals(originalItem, originalController.current)
        assertEquals(2L, tester.vsync.diagnostics().createdTickerCount)
        assertEquals(1L, tester.vsync.diagnostics().disposedTickerCount)
        assertEquals(1, tester.vsync.liveTickerCount)

        tester.pumpFrame(0)
        tester.pumpFrame(99)
        assertEquals(replacementItem, replacementController.current)
        tester.pumpFrame(1)
        assertEquals(0, replacementController.size)
        assertEquals(1, originalController.size)
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)
        tester.dispose()
    }

    /** Snackbar action consumes exactly once, transfers timing to the next item, then timeout advances FIFO. */
    @Test
    fun snackbarActionCancelsCurrentTimerAndRunsExactlyOnce() {
        /** Queue retaining action ownership separately from its public item value. */
        val controller = PixelSnackbarQueueController()
        /** Virtual-frame and pointer harness for action and timeout behavior. */
        val tester = PixelTester()
        /** Number of business callback deliveries observed by the test. */
        var actionCount = 0
        /** Initially visible item with one consumable action. */
        val actionable: PixelSnackbarQueueItem = controller.enqueue(
            message = "SAVED",
            actionLabel = "UNDO",
            onAction = { actionCount += 1 },
            timeout = 1_000.milliseconds,
        )
        /** FIFO successor whose timeout starts after action consumption. */
        val next = controller.enqueue(message = "NEXT", timeout = 50.milliseconds)
        tester.pumpWidget(motionRoot(tester, SnackbarQueue(controller)), 96, 24)

        tester.tap(find.byText("UNDO"))
        assertEquals(1, actionCount)
        assertEquals(next, controller.current)
        assertFalse(controller.performAction(actionable.id))
        assertEquals(1, actionCount)
        assertEquals(1, tester.vsync.liveTickerCount)
        assertEquals(2L, tester.vsync.diagnostics().createdTickerCount)
        assertEquals(1L, tester.vsync.diagnostics().disposedTickerCount)

        tester.pumpFrame(0)
        tester.pumpFrame(50)
        assertEquals(0, controller.size)
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)
        tester.dispose()
    }

    /** Snackbar State also treats controller ownership as part of colliding local item identity. */
    @Test
    fun snackbarControllerSwitchRestartsTimeoutAndSemanticIdentity() {
        /** Original snackbar controller whose local item 1 reaches 80 percent dwell. */
        val originalController = PixelSnackbarQueueController()
        /** Replacement snackbar controller that independently allocates local item 1. */
        val replacementController = PixelSnackbarQueueController()
        /** Original snackbar item that must remain queued after presentation ownership changes. */
        val originalItem = originalController.enqueue(message = "SNACK-A", timeout = 100.milliseconds)
        /** Replacement snackbar item that must receive a complete independent timeout. */
        val replacementItem = replacementController.enqueue(message = "SNACK-B", timeout = 100.milliseconds)
        /** Observable owner selection that preserves one retained SnackbarQueue key. */
        val selectedController = ValueNotifier(originalController)
        /** Virtual-frame harness exposing ticker generations and semantic identities. */
        val tester = PixelTester()
        tester.pumpWidget(
            widget = motionRoot(
                tester = tester,
                child = ValueListenableBuilder(selectedController) { _, controller ->
                    SnackbarQueue(controller = controller, key = "shared-snackbar-queue")
                },
            ),
            logicalWidth = 80,
            logicalHeight = 24,
        )

        tester.pumpFrame(0)
        tester.pumpFrame(80)
        /** Semantic id allocated to controller A's snackbar message. */
        val originalSemanticId = tester.semanticsNodesByLabel("SNACK-A").single().id

        selectedController.value = replacementController
        tester.pumpFrame(0)
        /** Semantic id allocated to controller B's independently owned snackbar message. */
        val replacementSemanticId = tester.semanticsNodesByLabel("SNACK-B").single().id
        assertTrue(originalSemanticId != replacementSemanticId)
        assertEquals(originalItem, originalController.current)
        assertEquals(replacementItem, replacementController.current)
        assertEquals(2L, tester.vsync.diagnostics().createdTickerCount)
        assertEquals(1L, tester.vsync.diagnostics().disposedTickerCount)

        tester.pumpFrame(0)
        tester.pumpFrame(99)
        assertEquals(replacementItem, replacementController.current)
        tester.pumpFrame(1)
        assertEquals(0, replacementController.size)
        assertEquals(1, originalController.size)
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)
        tester.dispose()
    }

    /** A failing observer cannot prevent the queue widget from cancelling its active ticker. */
    @Test
    fun throwingControllerListenerStillCancelsQueueTickerAndNotifiesWidget() {
        /** Queue whose external throwing listener is registered before the widget watcher. */
        val controller = PixelToastQueueController()
        /** Finite item that starts one Host-owned active-time ticker after mount. */
        controller.enqueue(message = "THROWING OBSERVER", timeout = 100.milliseconds)
        /** Exact failure expected after listener fan-out has completed. */
        val listenerFailure = IllegalStateException("notification-listener-failure")
        controller.addListener(VoidCallback { throw listenerFailure })
        /** Virtual Host proving the later widget listener still synchronizes its timeout driver. */
        val tester = PixelTester()
        tester.pumpWidget(
            widget = motionRoot(tester, ToastQueue(controller)),
            logicalWidth = 64,
            logicalHeight = 24,
        )
        assertEquals(1, tester.vsync.liveTickerCount)

        /** Mutation failure observed only after the queue widget has cancelled the ticker. */
        var observedFailure: Throwable? = null
        try {
            controller.dismissCurrent()
        } catch (failure: Throwable) {
            observedFailure = failure
        }

        assertTrue(observedFailure === listenerFailure)
        assertEquals(0, controller.size)
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.pumpFrame(0)
        assertFalse(tester.exists(find.byText("THROWING OBSERVER")))
        assertEquals(0, tester.scheduler.pendingCount)
        tester.dispose()
    }

    /** Widget disposal removes its controller listener and ticker so no delayed callback can mutate the queue. */
    @Test
    fun queueDisposeLeavesNoTickerOrDelayedDismissCallback() {
        /** Queue that must remain unchanged after its presentation is disposed. */
        val controller = PixelToastQueueController()
        /** Harness whose runtime disposal unmounts Queue State synchronously. */
        val tester = PixelTester()
        controller.enqueue(message = "KEEP", timeout = 100.milliseconds)
        tester.pumpWidget(motionRoot(tester, ToastQueue(controller)), 64, 24)
        assertEquals(1, tester.vsync.liveTickerCount)
        assertEquals(1, tester.scheduler.pendingCount)

        tester.dispose()
        assertEquals(0, tester.vsync.activeTickerCount)
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)
        tester.scheduler.advanceFrame(10_000_000_000L)
        assertEquals(1, controller.size)
    }

    /** A queue outside PixelMotionScope stays manually controlled and never manufactures a hidden clock. */
    @Test
    fun queueWithoutMotionScopeDoesNotCreateImplicitTimer() {
        /** Queue rendered without any Host motion environment. */
        val controller = PixelToastQueueController()
        /** Harness advanced far beyond timeout to prove no implicit clock exists. */
        val tester = PixelTester()
        controller.enqueue(message = "MANUAL", timeout = 10.milliseconds)
        tester.pumpWidget(ToastQueue(controller), 64, 24)

        tester.pumpFrame(1_000)
        assertEquals(1, controller.size)
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)
        tester.dispose()
    }

    /** Wraps queue content in a deterministic Host-like motion scope for virtual-clock tests. */
    private fun motionRoot(
        tester: PixelTester,
        child: Widget,
        settings: PixelMotionSettings = PixelMotionSettings(),
    ): Widget {
        return PixelMotionScope(
            vsync = tester.vsync,
            settings = settings,
            child = child,
        )
    }
}
