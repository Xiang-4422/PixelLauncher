package com.purride.pixelui.widgets.navigation

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

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

    private fun route(name: String, builder: (BuildContext) -> Widget): PixelRoute {
        return PixelRoute(name = name, builder = builder)
    }
}
