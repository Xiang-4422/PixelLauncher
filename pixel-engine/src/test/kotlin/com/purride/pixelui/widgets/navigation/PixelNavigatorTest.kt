package com.purride.pixelui.widgets.navigation

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.ListView
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class PixelNavigatorTest {
    @Test
    fun pushPopPopToRootAndReplaceUpdateStack() {
        val tester = PixelTester()
        var navigator: PixelNavigatorState? = null
        val root = route("root") { context ->
            navigator = PixelNavigator.of(context)
            Text("ROOT")
        }
        tester.pumpWidget(PixelNavigator(root, tester.vsync), 32, 12)

        val state = navigator!!
        assertFalse(state.canPop)
        state.push(route("details") { Text("DETAILS") })
        tester.pumpFrame(16)
        assertEquals("details", state.currentRoute.name)
        assertTrue(state.canPop)

        assertTrue(state.pop())
        tester.pumpFrame(16)
        assertEquals("root", state.currentRoute.name)
        assertFalse(state.pop())

        state.push(route("a") { Text("A") })
        state.push(route("b") { Text("B") })
        state.popToRoot()
        assertEquals(listOf("root"), state.stack.map { it.name })

        state.replace(route("replacement") { Text("R") })
        assertEquals("replacement", state.currentRoute.name)
        tester.dispose()
    }

    @Test
    fun routeButtonsDriveStackThroughTesterDsl() {
        val tester = PixelTester()
        var navigator: PixelNavigatorState? = null

        lateinit var root: PixelRoute
        lateinit var details: PixelRoute
        lateinit var replacement: PixelRoute

        root = route("root") { context ->
            navigator = PixelNavigator.of(context)
            Column(
                children = listOf(
                    Text("ROOT"),
                    OutlinedButton(
                        text = "PUSH",
                        onPressed = { PixelNavigator.of(context).push(details) },
                    ),
                ),
            )
        }
        details = route("details") { context ->
            Column(
                children = listOf(
                    Text("DETAILS"),
                    OutlinedButton(
                        text = "REPLACE",
                        onPressed = { PixelNavigator.of(context).replace(replacement, animated = false) },
                    ),
                    OutlinedButton(
                        text = "BACK",
                        onPressed = { PixelNavigator.of(context).pop() },
                    ),
                ),
            )
        }
        replacement = route("replacement") { context ->
            Column(
                children = listOf(
                    Text("REPLACEMENT"),
                    OutlinedButton(
                        text = "ROOT",
                        onPressed = { PixelNavigator.of(context).popToRoot(animated = false) },
                    ),
                ),
            )
        }

        tester.pumpWidget(PixelNavigator(root, tester.vsync), 48, 28)

        tester.tap(find.byText("PUSH"))
        tester.pumpAndSettle()
        assertEquals("details", navigator!!.currentRoute.name)

        tester.tap(find.byText("BACK"))
        tester.pumpAndSettle()
        assertEquals("root", navigator!!.currentRoute.name)

        tester.tap(find.byText("PUSH"))
        tester.pumpAndSettle()
        tester.tap(find.byText("REPLACE"))
        assertEquals("replacement", navigator!!.currentRoute.name)

        tester.tap(find.byText("ROOT"))
        assertEquals(listOf("root"), navigator!!.stack.map { it.name })
        tester.dispose()
    }

    @Test
    fun maybeOfResolvesInsideRoute() {
        val tester = PixelTester()
        var resolved: PixelNavigatorState? = null
        tester.pumpWidget(
            PixelNavigator(route("root") { context ->
                resolved = PixelNavigator.maybeOf(context)
                Text("ROOT")
            }, tester.vsync),
            32,
            12,
        )

        assertNotNull(resolved)
        tester.dispose()
    }

    @Test
    fun maybePopAndPopRespectRouteGuard() {
        val tester = PixelTester()
        var navigator: PixelNavigatorState? = null
        tester.pumpWidget(
            PixelNavigator(route("root") { context ->
                navigator = PixelNavigator.of(context)
                Text("ROOT")
            }, tester.vsync),
            32,
            12,
        )

        val state = navigator!!
        state.push(PixelRoute(name = "blocked", builder = { Text("BLOCKED") }, canPop = { false }))
        assertFalse(state.maybePop())
        assertEquals("blocked", state.currentRoute.name)
        assertFalse(state.pop())
        assertEquals("blocked", state.currentRoute.name)

        state.replace(PixelRoute(name = "allowed", builder = { Text("ALLOWED") }, canPop = { true }))
        assertTrue(state.maybePop())
        assertEquals("root", state.currentRoute.name)
        tester.dispose()
    }

    @Test
    fun lifecycleCallbacksRunAcrossPushPopAndDisposeAfterSettle() {
        val tester = PixelTester()
        val events = mutableListOf<String>()
        var navigator: PixelNavigatorState? = null
        val root = PixelRoute(
            name = "root",
            builder = { context ->
                navigator = PixelNavigator.of(context)
                Text("ROOT")
            },
            onEnter = { events += "root-enter" },
            onExit = { events += "root-exit" },
            onDispose = { events += "root-dispose" },
        )
        val details = PixelRoute(
            name = "details",
            builder = { Text("DETAILS") },
            onEnter = { events += "details-enter" },
            onExit = { events += "details-exit" },
            onDispose = { events += "details-dispose" },
        )
        tester.pumpWidget(PixelNavigator(root, tester.vsync), 32, 12)

        val state = navigator!!
        state.push(details)
        assertNotNull(state.activeTransition)
        assertEquals(listOf("root-enter", "root-exit", "details-enter"), events)
        tester.pumpAndSettle()
        assertTrue(state.pop())
        assertNotNull(state.activeTransition)
        assertEquals(
            listOf("root-enter", "root-exit", "details-enter", "details-exit", "root-enter"),
            events,
        )

        tester.pumpAndSettle()
        assertNull(state.activeTransition)
        assertTrue(events.contains("details-dispose"))
        tester.dispose()
    }

    @Test
    fun customTransitionBuilderReceivesProgressAndOwnsPushAndPopFrames() {
        val tester = PixelTester()
        val frames = mutableListOf<Pair<PixelNavigatorOperation, Float>>()
        val disposed = mutableListOf<String>()
        var navigator: PixelNavigatorState? = null
        val root = route("root") { context ->
            navigator = PixelNavigator.of(context)
            Text("ROOT")
        }
        val details = PixelRoute(
            name = "details",
            builder = { Text("DETAILS") },
            onDispose = { disposed += "details" },
            transitionBuilder = PixelRouteTransitionBuilder { progress, operation, _, incoming ->
                frames += operation to progress
                incoming
            },
        )
        tester.pumpWidget(
            PixelNavigator(
                initialRoute = root,
                vsync = tester.vsync,
                transitionDuration = 200.milliseconds,
            ),
            32,
            12,
        )

        navigator!!.push(details)
        tester.pumpFrame(1)
        tester.pumpFrame(1)
        tester.pumpFrame(100)
        assertTrue(frames.any { (operation, progress) ->
            operation == PixelNavigatorOperation.Push && progress in 0.01f..0.99f
        })
        tester.pumpAndSettle()
        assertTrue(tester.exists(com.purride.pixelui.testing.find.byText("DETAILS")))

        frames.clear()
        assertTrue(navigator!!.pop())
        tester.pumpFrame(1)
        tester.pumpFrame(1)
        tester.pumpFrame(100)
        assertTrue(frames.any { (operation, progress) ->
            operation == PixelNavigatorOperation.Pop && progress in 0.01f..0.99f
        })
        assertTrue(disposed.isEmpty())
        tester.pumpAndSettle()
        assertEquals(listOf("details"), disposed)
        tester.dispose()
    }

    @Test
    fun routeTransitionBuilderOverridesNavigatorFallback() {
        val tester = PixelTester()
        var navigator: PixelNavigatorState? = null
        var fallbackFrames = 0
        var routeFrames = 0
        val root = route("root") { context ->
            navigator = PixelNavigator.of(context)
            Text("ROOT")
        }
        val plain = route("plain") { Text("PLAIN") }
        val custom = PixelRoute(
            name = "custom",
            builder = { Text("CUSTOM") },
            transitionBuilder = PixelRouteTransitionBuilder { _, _, _, incoming ->
                routeFrames += 1
                incoming
            },
        )
        tester.pumpWidget(
            PixelNavigator(
                initialRoute = root,
                vsync = tester.vsync,
                transitionBuilder = PixelRouteTransitionBuilder { _, _, _, incoming ->
                    fallbackFrames += 1
                    incoming
                },
            ),
            32,
            12,
        )

        navigator!!.push(plain)
        tester.pumpAndSettle()
        assertTrue(fallbackFrames > 0)

        fallbackFrames = 0
        navigator!!.push(custom)
        tester.pumpAndSettle()
        assertTrue(routeFrames > 0)
        assertEquals(0, fallbackFrames)
        tester.dispose()
    }

    @Test
    fun transitionRecordsOperationForPushPopReplaceAndPopToRoot() {
        val tester = PixelTester()
        var navigator: PixelNavigatorState? = null
        val root = route("root") { context ->
            navigator = PixelNavigator.of(context)
            Text("ROOT")
        }
        val first = route("first") { Text("FIRST") }
        val second = route("second") { Text("SECOND") }
        val third = route("third") { Text("THIRD") }
        tester.pumpWidget(PixelNavigator(root, tester.vsync), 32, 12)

        val state = navigator!!
        state.push(first)
        assertEquals(PixelNavigatorOperation.Push, state.activeTransition?.operation)
        tester.pumpAndSettle()

        state.replace(second)
        assertEquals(PixelNavigatorOperation.Replace, state.activeTransition?.operation)
        tester.pumpAndSettle()

        state.push(third)
        tester.pumpAndSettle()
        assertTrue(state.pop())
        assertEquals(PixelNavigatorOperation.Pop, state.activeTransition?.operation)
        tester.pumpAndSettle()

        state.push(third)
        tester.pumpAndSettle()
        state.popToRoot()
        assertEquals(PixelNavigatorOperation.Pop, state.activeTransition?.operation)
        tester.dispose()
    }

    @Test
    fun startingNewTransitionDisposesSupersededPendingRoutes() {
        val tester = PixelTester()
        val disposed = mutableListOf<String>()
        var navigator: PixelNavigatorState? = null
        val root = route("root") { context ->
            navigator = PixelNavigator.of(context)
            Text("ROOT")
        }
        val details = PixelRoute(
            name = "details",
            builder = { Text("DETAILS") },
            onDispose = { disposed += "details" },
        )
        val replacement = route("replacement") { Text("REPLACEMENT") }
        tester.pumpWidget(PixelNavigator(root, tester.vsync), 32, 12)

        val state = navigator!!
        state.push(details)
        tester.pumpAndSettle()
        assertTrue(state.pop())
        assertTrue(disposed.isEmpty())

        state.push(replacement)
        assertEquals(listOf("details"), disposed)
        assertEquals(PixelNavigatorOperation.Push, state.activeTransition?.operation)
        tester.dispose()
    }

    @Test
    fun popToRootDisposesAllRemovedRoutesAfterAnimatedSettle() {
        val tester = PixelTester()
        val disposed = mutableListOf<String>()
        var navigator: PixelNavigatorState? = null
        val root = route("root") { context ->
            navigator = PixelNavigator.of(context)
            Text("ROOT")
        }
        val first = PixelRoute(
            name = "first",
            builder = { Text("FIRST") },
            onDispose = { disposed += "first" },
        )
        val second = PixelRoute(
            name = "second",
            builder = { Text("SECOND") },
            onDispose = { disposed += "second" },
        )
        tester.pumpWidget(PixelNavigator(root, tester.vsync), 32, 12)

        val state = navigator!!
        state.push(first)
        tester.pumpAndSettle()
        state.push(second)
        tester.pumpAndSettle()

        state.popToRoot(animated = true)
        assertTrue(disposed.isEmpty())
        tester.pumpAndSettle()

        assertEquals(listOf("first", "second"), disposed)
        assertEquals(listOf("root"), state.stack.map { it.name })
        tester.dispose()
    }

    @Test
    fun snapshotAndRestoreUseRouteNameStack() {
        val tester = PixelTester()
        var navigator: PixelNavigatorState? = null
        val root = route("root") { context ->
            navigator = PixelNavigator.of(context)
            Text("ROOT")
        }
        val details = route("details") { Text("DETAILS") }
        tester.pumpWidget(PixelNavigator(root, tester.vsync), 32, 12)

        val state = navigator!!
        state.push(details)
        val snapshot = state.snapshot()
        state.popToRoot(animated = false)
        assertEquals(listOf("root"), state.stack.map { it.name })

        state.restore(snapshot, mapOf("root" to root, "details" to details))
        assertEquals(listOf("root", "details"), state.stack.map { it.name })
        tester.dispose()
    }

    @Test
    fun restoreReportsMissingRouteName() {
        val tester = PixelTester()
        var navigator: PixelNavigatorState? = null
        tester.pumpWidget(
            PixelNavigator(route("root") { context ->
                navigator = PixelNavigator.of(context)
                Text("ROOT")
            }, tester.vsync),
            32,
            12,
        )

        try {
            navigator!!.restore(PixelNavigatorSnapshot(listOf("missing")), emptyMap())
            fail("restore should reject missing route names")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("missing route 'missing'"))
        }
        tester.dispose()
    }

    @Test
    fun restoreFromNullBundleReturnsFalseWithoutChangingStack() {
        val tester = PixelTester()
        var navigator: PixelNavigatorState? = null
        val root = route("root") { context ->
            navigator = PixelNavigator.of(context)
            Text("ROOT")
        }
        val details = route("details") { Text("DETAILS") }
        tester.pumpWidget(PixelNavigator(root, tester.vsync), 32, 12)

        val state = navigator!!
        state.push(details)
        assertFalse(state.restoreFromBundle(null, mapOf("root" to root, "details" to details)))
        assertEquals(listOf("root", "details"), state.stack.map { it.name })
        tester.dispose()
    }

    @Test
    fun routeScrollRestorationRestoresRecreatedListStateAfterPop() {
        val tester = PixelTester()
        val controller = PixelListController()
        var rootState = controller.create()
        var navigator: PixelNavigatorState? = null
        val root = route("root") { context ->
            navigator = PixelNavigator.of(context)
            PixelRouteScrollRestoration(
                restorationId = "feed",
                state = rootState,
                controller = controller,
                child = ListView(
                    items = List(30) { index ->
                        SizedBox(height = 4, child = Text("ROW $index"))
                    },
                    state = rootState,
                    controller = controller,
                ),
            )
        }
        val details = route("details") { Text("DETAILS") }
        tester.pumpWidget(PixelNavigator(root, tester.vsync), 40, 16)

        controller.scrollTo(
            state = rootState,
            targetOffsetPx = 48f,
            viewportHeightPx = rootState.viewportHeightPx,
            contentHeightPx = rootState.contentHeightPx,
        )
        tester.pumpFrame(16)
        assertEquals(48f, rootState.scrollOffsetPx)

        navigator!!.push(details)
        tester.pumpAndSettle()
        rootState = controller.create()
        assertEquals(0f, rootState.scrollOffsetPx)

        assertTrue(navigator!!.pop())
        tester.pumpAndSettle()
        assertEquals(48f, rootState.scrollOffsetPx)
        tester.dispose()
    }

    @Test
    fun disposedRouteDoesNotRetainScrollRestorationBucket() {
        val tester = PixelTester()
        val controller = PixelListController()
        var detailsState = controller.create()
        var navigator: PixelNavigatorState? = null
        val root = route("root") { context ->
            navigator = PixelNavigator.of(context)
            Text("ROOT")
        }
        val details = route("details") {
            PixelRouteScrollRestoration(
                restorationId = "feed",
                state = detailsState,
                controller = controller,
                child = ListView(
                    items = List(30) { index ->
                        SizedBox(height = 4, child = Text("DETAIL $index"))
                    },
                    state = detailsState,
                    controller = controller,
                ),
            )
        }
        tester.pumpWidget(PixelNavigator(root, tester.vsync), 40, 16)

        navigator!!.push(details)
        tester.pumpAndSettle()
        controller.scrollTo(
            state = detailsState,
            targetOffsetPx = 40f,
            viewportHeightPx = detailsState.viewportHeightPx,
            contentHeightPx = detailsState.contentHeightPx,
        )
        tester.pumpFrame(16)
        assertEquals(40f, detailsState.scrollOffsetPx)

        assertTrue(navigator!!.pop())
        tester.pumpAndSettle()
        detailsState = controller.create()
        navigator!!.push(details)
        tester.pumpAndSettle()

        assertEquals(0f, detailsState.scrollOffsetPx)
        tester.dispose()
    }

    private fun route(name: String, builder: (BuildContext) -> Widget): PixelRoute {
        return PixelRoute(name = name, builder = builder)
    }
}
