package com.purride.pixelui

import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Navigator-level contracts for typed discrete back and gesture-controlled pop presentation. */
class PixelNavigatorPredictiveBackTest {
    /** A discrete Host back must cancel a typed result rather than inventing `Success(null)`. */
    @Test
    fun discreteSystemBackCancelsTypedEntryWithoutNullableSuccess() {
        // Deterministic tester owns the transition clock used by the mounted Navigator.
        val tester = PixelTester()
        // Host dispatcher exercises the same registration path used by Android API 33.
        val dispatcher = PixelBackDispatcher()
        // Mounted controller captured from the typed root's nearest Navigator scope.
        var navigator: PixelNavigatorState? = null
        // Explicit outcome proves system back remains distinct from nullable success.
        var outcome: PixelRouteOutcome<String?>? = null
        // 可持久化的类型化根让预测性返回断言始终跑在受支持的路由 API 上。
        val root = pixelRouteDestination<Unit, Unit>(
            id = "root",
            transition = PixelRouteTransition.None,
        ) { context, _ ->
            navigator = PixelNavigator.of(context)
            Text("ROOT")
        }
        // Nullable result type deliberately proves cancellation is not encoded as a null value.
        val detail = pixelRouteDestination<Unit, String?>(
            id = "detail",
            transition = PixelRouteTransition.None,
        ) { _, _ -> Text("DETAIL") }

        tester.pumpWidget(
            PixelBackHost(
                dispatcher = dispatcher,
                child = PixelNavigator(
                    initialRequest = PixelRouteRequest(root, Unit),
                    vsync = tester.vsync,
                    defaultTransition = PixelRouteTransition.None,
                ),
            ),
            logicalWidth = 32,
            logicalHeight = 12,
        )
        val state = checkNotNull(navigator)
        state.push(PixelRouteRequest(detail, Unit)) { resolved -> outcome = resolved }
        tester.pumpAndSettle()

        assertTrue(dispatcher.handleBack())
        tester.pumpAndSettle()

        assertEquals(1, state.entries.size)
        assertTrue(outcome is PixelRouteOutcome.Cancelled)
        assertEquals(
            PixelRouteCancellationReason.Back,
            (outcome as PixelRouteOutcome.Cancelled).reason,
        )
        tester.dispose()
    }

    /** Start/progress/cancel is side-effect free, while commit disposes and resolves exactly once. */
    @Test
    fun predictiveBackPreviewsCancelsAndCommitsWithOrderedLifecycle() {
        // Tester renders both sides of the interactive pop at deterministic progress values.
        val tester = PixelTester()
        // Dispatcher locks one callback for each predictive gesture session.
        val dispatcher = PixelBackDispatcher()
        // Mounted Navigator controller under test.
        var navigator: PixelNavigatorState? = null
        // Ordered lifecycle/result log distinguishes preview work from committed mutation.
        val events = mutableListOf<String>()
        // Root callbacks prove it stays inactive until commit and re-enters before disposal.
        val root = pixelRouteDestination<Unit, Unit>(
            id = "root",
            transition = PixelRouteTransition.SlideHorizontal,
            onEnter = { events += "root-enter" },
            onExit = { events += "root-exit" },
        ) { context, _ ->
            navigator = PixelNavigator.of(context)
            Text("ROOT")
        }
        // Detail callbacks prove cancellation does not exit or dispose the active entry.
        val detail = pixelRouteDestination<Unit, String>(
            id = "detail",
            transition = PixelRouteTransition.SlideHorizontal,
            onEnter = { events += "detail-enter" },
            onExit = { events += "detail-exit" },
            onDispose = { events += "detail-dispose" },
        ) { _, _ -> Text("DETAIL") }

        tester.pumpWidget(
            PixelBackHost(
                dispatcher = dispatcher,
                child = PixelNavigator(
                    initialRequest = PixelRouteRequest(root, Unit),
                    vsync = tester.vsync,
                ),
            ),
            logicalWidth = 40,
            logicalHeight = 12,
        )
        val state = checkNotNull(navigator)
        val detailEntry = state.push(PixelRouteRequest(detail, Unit)) { outcome ->
            events += when (outcome) {
                is PixelRouteOutcome.Success -> "success:${outcome.value}"
                is PixelRouteOutcome.Cancelled -> "cancel:${outcome.reason}"
            }
        }
        tester.pumpAndSettle()
        events.clear()

        assertTrue(dispatcher.startPredictiveBack(event(progress = 0f)))
        dispatcher.updatePredictiveBack(event(progress = 0.55f))
        tester.pumpFrame(1)

        assertEquals(0.55f, state.predictiveBackProgress)
        assertSame(detailEntry, state.currentEntry)
        assertEquals(PixelRouteLifecycleState.Active, detailEntry.lifecycleState)
        assertEquals(PixelRouteResultState.Pending, detailEntry.resultChannel.state)
        assertTrue(events.isEmpty())
        assertTrue(tester.exists(find.byText("ROOT")))
        assertTrue(tester.exists(find.byText("DETAIL")))
        assertEquals(detailEntry.id, state.inspectionSnapshot().transition?.outgoingEntryId)

        dispatcher.cancelPredictiveBack()
        tester.pumpFrame(1)

        assertNull(state.predictiveBackProgress)
        assertSame(detailEntry, state.currentEntry)
        assertEquals(PixelRouteResultState.Pending, detailEntry.resultChannel.state)
        assertTrue(events.isEmpty())
        assertFalse(tester.exists(find.byText("ROOT")))
        assertTrue(tester.exists(find.byText("DETAIL")))

        assertTrue(dispatcher.startPredictiveBack(event(progress = 0.1f)))
        dispatcher.updatePredictiveBack(event(progress = 0.8f))
        tester.pumpFrame(1)
        assertTrue(dispatcher.commitPredictiveBack())

        assertNull(state.predictiveBackProgress)
        assertEquals(1, state.entries.size)
        assertEquals(PixelRouteLifecycleState.Disposed, detailEntry.lifecycleState)
        assertEquals(PixelRouteResultState.Cancelled, detailEntry.resultChannel.state)
        assertEquals(
            listOf(
                "detail-exit",
                "root-enter",
                "detail-dispose",
                "cancel:Back",
            ),
            events,
        )
        tester.pumpFrame(1)
        assertTrue(tester.exists(find.byText("ROOT")))
        assertFalse(dispatcher.commitPredictiveBack())
        assertEquals(1, events.count { item -> item == "detail-dispose" })
        tester.dispose()
    }

    /** A stack mutation invalidates a preview so its delayed platform commit cannot pop a new page. */
    @Test
    fun navigationMutationInvalidatesStalePredictiveCommit() {
        // Tester and dispatcher reproduce a platform terminal event arriving after app navigation.
        val tester = PixelTester()
        val dispatcher = PixelBackDispatcher()
        // Mounted state is captured by the typed root.
        var navigator: PixelNavigatorState? = null
        // Shared destination keeps the fixture focused on entry identity rather than route setup.
        val destination = pixelRouteDestination<String, Unit>(
            id = "page",
            transition = PixelRouteTransition.None,
        ) { context, scope ->
            navigator = PixelNavigator.of(context)
            Text(scope.arguments)
        }

        tester.pumpWidget(
            PixelBackHost(
                dispatcher = dispatcher,
                child = PixelNavigator(
                    initialRequest = PixelRouteRequest(destination, "ROOT"),
                    vsync = tester.vsync,
                    defaultTransition = PixelRouteTransition.None,
                ),
            ),
            logicalWidth = 32,
            logicalHeight = 12,
        )
        val state = checkNotNull(navigator)
        state.push(PixelRouteRequest(destination, "DETAIL"))
        tester.pumpAndSettle()
        assertTrue(dispatcher.startPredictiveBack(event(progress = 0.4f)))

        val replacementForeground = state.push(PixelRouteRequest(destination, "NEW"))

        assertNull(state.predictiveBackProgress)
        assertFalse(dispatcher.commitPredictiveBack())
        assertSame(replacementForeground, state.currentEntry)
        assertEquals(3, state.entries.size)
        tester.pumpAndSettle()
        tester.dispose()
    }

    /** Creates one finite normalized gesture frame for the requested [progress]. */
    private fun event(progress: Float): PixelPredictiveBackEvent {
        return PixelPredictiveBackEvent(
            progress = progress,
            touchX = 1f,
            touchY = 6f,
            swipeEdge = PixelPredictiveBackSwipeEdge.Left,
        )
    }
}
