package com.purride.pixelui

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
    fun fadeTransitionKeepsOutgoingAndIncomingRoutesDuringAnimation() {
        val tester = PixelTester()
        var navigator: PixelNavigatorState? = null
        val root = route("root") { context ->
            navigator = PixelNavigator.of(context)
            Text("ROOT")
        }
        val details = testRouteRequest(
            name = "details",
            transition = PixelRouteTransition.Fade,
            builder = { Text("DETAILS") },
        )
        tester.pumpWidget(
            PixelNavigator(
                initialRequest = root,
                vsync = tester.vsync,
                transitionDuration = 200.milliseconds,
            ),
            32,
            12,
        )

        navigator!!.push(details)
        tester.pumpFrame(100)

        assertTrue(tester.exists(com.purride.pixelui.testing.find.byText("ROOT")))
        assertTrue(tester.exists(com.purride.pixelui.testing.find.byText("DETAILS")))
        tester.pumpAndSettle()
        assertFalse(tester.exists(com.purride.pixelui.testing.find.byText("ROOT")))
        assertTrue(tester.exists(com.purride.pixelui.testing.find.byText("DETAILS")))
        tester.dispose()
    }

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
        assertEquals("details", state.currentEntry.destination.id)
        assertTrue(state.canPop)

        assertTrue(state.pop())
        tester.pumpFrame(16)
        assertEquals("root", state.currentEntry.destination.id)
        assertFalse(state.pop())

        state.push(route("a") { Text("A") })
        state.push(route("b") { Text("B") })
        state.popToRoot()
        assertEquals(listOf("root"), state.entries.map { entry -> entry.destination.id })

        state.replace(route("replacement") { Text("R") })
        assertEquals("replacement", state.currentEntry.destination.id)
        tester.dispose()
    }

    @Test
    fun routeButtonsDriveStackThroughTesterDsl() {
        val tester = PixelTester()
        var navigator: PixelNavigatorState? = null

        lateinit var root: PixelRouteRequest<Unit, Any?>
        lateinit var details: PixelRouteRequest<Unit, Any?>
        lateinit var replacement: PixelRouteRequest<Unit, Any?>

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
        assertEquals("details", navigator!!.currentEntry.destination.id)

        tester.tap(find.byText("BACK"))
        tester.pumpAndSettle()
        assertEquals("root", navigator!!.currentEntry.destination.id)

        tester.tap(find.byText("PUSH"))
        tester.pumpAndSettle()
        tester.tap(find.byText("REPLACE"))
        assertEquals("replacement", navigator!!.currentEntry.destination.id)

        tester.tap(find.byText("ROOT"))
        assertEquals(listOf("root"), navigator!!.entries.map { entry -> entry.destination.id })
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
        state.push(
            testRouteRequest(name = "blocked", builder = { Text("BLOCKED") }, canPop = { false }),
        )
        assertFalse(state.maybePop())
        assertEquals("blocked", state.currentEntry.destination.id)
        assertFalse(state.pop())
        assertEquals("blocked", state.currentEntry.destination.id)

        state.replace(
            testRouteRequest(name = "allowed", builder = { Text("ALLOWED") }, canPop = { true }),
        )
        assertTrue(state.maybePop())
        assertEquals("root", state.currentEntry.destination.id)
        tester.dispose()
    }

    @Test
    fun lifecycleCallbacksRunAcrossPushPopAndDisposeAfterSettle() {
        val tester = PixelTester()
        val events = mutableListOf<String>()
        var navigator: PixelNavigatorState? = null
        val root = testRouteRequest(
            name = "root",
            builder = { context ->
                navigator = PixelNavigator.of(context)
                Text("ROOT")
            },
            onEnter = { events += "root-enter" },
            onExit = { events += "root-exit" },
            onDispose = { events += "root-dispose" },
        )
        val details = testRouteRequest(
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
        val details = testRouteRequest(
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
                initialRequest = root,
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
        val custom = testRouteRequest(
            name = "custom",
            builder = { Text("CUSTOM") },
            transitionBuilder = PixelRouteTransitionBuilder { _, _, _, incoming ->
                routeFrames += 1
                incoming
            },
        )
        tester.pumpWidget(
            PixelNavigator(
                initialRequest = root,
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
    fun routeTransitionControlsPopDirectionForBuiltInSlides() {
        assertEquals(
            PixelRouteTransition.SlideVertical,
            resolvePixelRouteTransition(
                operation = PixelNavigatorOperation.Push,
                outgoingTransition = null,
                incomingTransition = PixelRouteTransition.SlideVertical,
                defaultTransition = PixelRouteTransition.SlideHorizontal,
            ),
        )
        assertEquals(
            PixelRouteTransition.SlideVertical,
            resolvePixelRouteTransition(
                operation = PixelNavigatorOperation.Pop,
                outgoingTransition = PixelRouteTransition.SlideVertical,
                incomingTransition = null,
                defaultTransition = PixelRouteTransition.SlideHorizontal,
            ),
        )
        assertEquals(
            PixelRouteTransition.SlideHorizontal,
            resolvePixelRouteTransition(
                operation = PixelNavigatorOperation.Pop,
                outgoingTransition = null,
                incomingTransition = PixelRouteTransition.SlideVertical,
                defaultTransition = PixelRouteTransition.SlideHorizontal,
            ),
        )
    }

    @Test
    fun popDeliversResultAfterRouteDisposes() {
        val tester = PixelTester()
        val events = mutableListOf<String>()
        var navigator: PixelNavigatorState? = null
        val root = route("root") { context ->
            navigator = PixelNavigator.of(context)
            Text("ROOT")
        }
        val details = testRouteRequest(
            name = "details",
            builder = { Text("DETAILS") },
            onDispose = { events += "dispose" },
        )
        tester.pumpWidget(PixelNavigator(root, tester.vsync), 32, 12)

        navigator!!.push(details) { outcome -> events += "result=${outcome.valueOrNull()}" }
        tester.pumpAndSettle()
        assertTrue(navigator!!.pop("saved"))
        assertTrue(events.isEmpty())
        tester.pumpAndSettle()

        assertEquals(listOf("dispose", "result=saved"), events)
        tester.dispose()
    }

    @Test
    fun duplicateRouteInstancesKeepIndependentResultCallbacksAndGuardDoesNotComplete() {
        val tester = PixelTester()
        val results = mutableListOf<String>()
        var navigator: PixelNavigatorState? = null
        val root = route("root") { context ->
            navigator = PixelNavigator.of(context)
            Text("ROOT")
        }
        var allowPop = false
        val shared = testRouteRequest(
            name = "shared",
            builder = { Text("SHARED") },
            canPop = { allowPop },
        )
        tester.pumpWidget(PixelNavigator(root, tester.vsync), 32, 12)

        navigator!!.push(shared) { outcome -> results += "first=${outcome.valueOrNull()}" }
        tester.pumpAndSettle()
        navigator!!.push(shared) { outcome -> results += "second=${outcome.valueOrNull()}" }
        tester.pumpAndSettle()
        assertFalse(navigator!!.pop("blocked"))
        assertTrue(results.isEmpty())

        allowPop = true
        assertTrue(navigator!!.pop("top"))
        tester.pumpAndSettle()
        assertEquals(listOf("second=top"), results)
        assertTrue(navigator!!.pop("bottom"))
        tester.pumpAndSettle()
        assertEquals(listOf("second=top", "first=bottom"), results)
        tester.dispose()
    }

    @Test
    fun popToRootAndClearCancelEveryRemovedEntryCallback() {
        val tester = PixelTester()
        val results = mutableListOf<String>()
        var navigator: PixelNavigatorState? = null
        val root = route("root") { context ->
            navigator = PixelNavigator.of(context)
            Text("ROOT")
        }
        val first = route("first") { Text("FIRST") }
        val second = route("second") { Text("SECOND") }
        tester.pumpWidget(PixelNavigator(root, tester.vsync), 32, 12)

        navigator!!.push(first) { outcome -> results += "first=${outcome.valueOrNull()}" }
        tester.pumpAndSettle()
        navigator!!.push(second) { outcome -> results += "second=${outcome.valueOrNull()}" }
        tester.pumpAndSettle()
        navigator!!.popToRoot(animated = false)
        assertEquals(listOf("first=null", "second=null"), results)

        navigator!!.push(first) { outcome -> results += "reopened=${outcome.valueOrNull()}" }
        tester.pumpAndSettle()
        navigator!!.clear(animated = false)
        assertEquals("reopened=null", results.last())
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
        val details = testRouteRequest(
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
        val first = testRouteRequest(
            name = "first",
            builder = { Text("FIRST") },
            onDispose = { disposed += "first" },
        )
        val second = testRouteRequest(
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
        assertEquals(listOf("root"), state.entries.map { entry -> entry.destination.id })
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

    @Test
    fun deepLinkParserDecodesPathRepeatedQueryAndFragment() {
        val link = PixelDeepLink.parse(
            "pixel://Example.COM/catalog/item%201?tag=a&tag=b+c&empty#section%201",
        )

        assertEquals("pixel", link.scheme)
        assertEquals("example.com", link.host)
        assertEquals(listOf("catalog", "item 1"), link.pathSegments)
        assertEquals(listOf("a", "b c"), link.queryParameters["tag"])
        assertEquals("", link.queryParameter("empty"))
        assertEquals("section 1", link.fragment)
    }

    @Test
    fun invalidDeepLinkUriReportsOriginalInput() {
        try {
            PixelDeepLink.parse("pixel://bad host/path")
            fail("invalid deep link URI should be rejected")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("pixel://bad host/path"))
        }
    }

    /** 构造被测用例只关心子树与名称的最小类型化路由请求。 */
    private fun route(
        name: String,
        builder: (BuildContext) -> Widget,
    ): PixelRouteRequest<Unit, Any?> {
        return testRouteRequest(name = name, builder = builder)
    }
}
