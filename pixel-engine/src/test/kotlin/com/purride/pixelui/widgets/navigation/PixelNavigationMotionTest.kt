package com.purride.pixelui.widgets.navigation

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Container
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Stack
import com.purride.pixelui.PixelMotionRole
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionSettings
import com.purride.pixelui.PixelMotionSpec
import com.purride.pixelui.PixelMotionTheme
import com.purride.pixelui.PixelMotionThemeData
import com.purride.pixelui.PixelMotionTransitionPreset
import com.purride.pixelui.PixelPredictiveBackEvent
import com.purride.pixelui.PixelPredictiveBackSwipeEdge
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.Semantics
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** Verifies route and multi-stack MotionTheme integration with a deterministic Host clock. */
class PixelNavigationMotionTest {
    /** Solid route colors used for exact spatial and fade-frame assertions. */
    private val rootColor: PixelColor = PixelColor.fromRgb(240, 32, 32)
    private val detailsColor: PixelColor = PixelColor.fromRgb(32, 64, 240)

    /** Locks explicit slide priority and exact 0/25/50/75/100 route frames. */
    @Test
    fun routeSlideUsesThemeCurveAndExplicitDurationAcrossPercentFrames() {
        val tester = PixelTester()
        var navigator: PixelNavigatorState? = null
        val rootRoute = coloredRoute("ROOT", rootColor) { context ->
            navigator = PixelNavigator.of(context)
        }
        tester.pumpWidget(
            navigationMotionRoot(
                tester = tester,
                child = PixelNavigator(
                    initialRoute = rootRoute,
                    vsync = tester.vsync,
                    transitionDuration = 100.milliseconds,
                    defaultTransition = PixelRouteTransition.SlideHorizontal,
                ),
            ),
            40,
            10,
        )

        navigator!!.push(coloredRoute("DETAILS", detailsColor))
        tester.pumpFrame(0)
        tester.pumpFrame(0)
        assertEquals(40, countColor(tester, rootColor))
        assertEquals(0, countColor(tester, detailsColor))

        tester.pumpFrame(25)
        assertEquals(30, countColor(tester, rootColor))
        assertEquals(10, countColor(tester, detailsColor))
        tester.pumpFrame(25)
        assertEquals(20, countColor(tester, rootColor))
        assertEquals(20, countColor(tester, detailsColor))
        tester.pumpFrame(25)
        assertEquals(10, countColor(tester, rootColor))
        assertEquals(30, countColor(tester, detailsColor))
        tester.pumpFrame(25)
        assertEquals(0, countColor(tester, rootColor))
        assertEquals(40, countColor(tester, detailsColor))
        assertNull(navigator!!.activeTransition)
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** Verifies the route token delay precedes the explicitly configured interpolation duration. */
    @Test
    fun routeTransitionHonorsThemeDelayBeforeInterpolation() {
        val tester = PixelTester()
        var navigator: PixelNavigatorState? = null
        val rootRoute = coloredRoute("ROOT", rootColor) { context ->
            navigator = PixelNavigator.of(context)
        }
        tester.pumpWidget(
            navigationMotionRoot(
                tester = tester,
                routeDelayMs = 20,
                child = PixelNavigator(
                    initialRoute = rootRoute,
                    vsync = tester.vsync,
                    transitionDuration = 80.milliseconds,
                    defaultTransition = PixelRouteTransition.SlideHorizontal,
                ),
            ),
            40,
            10,
        )

        navigator!!.push(coloredRoute("DETAILS", detailsColor))
        tester.pumpFrame(0)
        tester.pumpFrame(0)
        tester.pumpFrame(20)
        assertEquals(40, countColor(tester, rootColor))
        tester.pumpFrame(40)
        assertEquals(20, countColor(tester, rootColor))
        assertEquals(20, countColor(tester, detailsColor))
        tester.pumpFrame(40)
        assertEquals(40, countColor(tester, detailsColor))
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** Verifies reduce motion replaces spatial/custom motion with a short displacement-free fade. */
    @Test
    fun reducedRouteUsesFadeAndSafelySkipsCustomBuilder() {
        val tester = PixelTester()
        var navigator: PixelNavigatorState? = null
        var customBuildCount = 0
        val rootRoute = coloredRoute("ROOT", rootColor) { context ->
            navigator = PixelNavigator.of(context)
        }
        val customDetails = PixelRoute(
            name = "DETAILS",
            builder = { coloredSurface("DETAILS", detailsColor) },
            transitionBuilder = PixelRouteTransitionBuilder { _, _, _, incoming ->
                customBuildCount += 1
                incoming
            },
        )
        tester.pumpWidget(
            navigationMotionRoot(
                tester = tester,
                settings = PixelMotionSettings(reduceMotion = true),
                child = PixelNavigator(
                    initialRoute = rootRoute,
                    vsync = tester.vsync,
                    transitionDuration = 100.milliseconds,
                    defaultTransition = PixelRouteTransition.SlideHorizontal,
                ),
            ),
            40,
            10,
        )

        navigator!!.push(customDetails)
        tester.pumpFrame(0)
        tester.pumpFrame(0)
        tester.pumpFrame(40)

        val leftPixel = tester.pixelAt(1, 1)
        val rightPixel = tester.pixelAt(38, 1)
        assertEquals(leftPixel, rightPixel)
        assertFalse(leftPixel == rootColor)
        assertFalse(leftPixel == detailsColor)
        assertEquals(0, customBuildCount)
        tester.pumpFrame(40)
        assertNull(navigator!!.activeTransition)
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** Verifies duration scale zero synchronously finalizes lifecycle without allocating a ticker. */
    @Test
    fun zeroDurationScaleCompletesRouteTransitionSynchronously() {
        val tester = PixelTester()
        var navigator: PixelNavigatorState? = null
        val rootRoute = coloredRoute("ROOT", rootColor) { context ->
            navigator = PixelNavigator.of(context)
        }
        tester.pumpWidget(
            navigationMotionRoot(
                tester = tester,
                settings = PixelMotionSettings(animatorDurationScale = 0f),
                child = PixelNavigator(rootRoute, tester.vsync),
            ),
            40,
            10,
        )

        navigator!!.push(coloredRoute("DETAILS", detailsColor))
        tester.pumpFrame(0)
        assertNull(navigator!!.activeTransition)
        assertEquals(40, countColor(tester, detailsColor))
        assertEquals(0, tester.scheduler.pendingCount)
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** A route token with `None` bypasses built-in and custom transition timelines. */
    @Test
    fun nonePresetCompletesRouteAndSkipsCustomBuilderSynchronously() {
        val tester = PixelTester()
        var navigator: PixelNavigatorState? = null
        var customBuildCount = 0
        val rootRoute = coloredRoute("ROOT", rootColor) { context ->
            navigator = PixelNavigator.of(context)
        }
        val customDetails = PixelRoute(
            name = "DETAILS",
            builder = { coloredSurface("DETAILS", detailsColor) },
            transitionBuilder = PixelRouteTransitionBuilder { _, _, _, incoming ->
                customBuildCount += 1
                incoming
            },
        )
        tester.pumpWidget(
            navigationMotionRoot(
                tester = tester,
                routeTransition = PixelMotionTransitionPreset.None,
                child = PixelNavigator(rootRoute, tester.vsync),
            ),
            40,
            10,
        )

        navigator!!.push(customDetails)
        tester.pumpFrame(0)

        assertNull(navigator!!.activeTransition)
        assertEquals(0, customBuildCount)
        assertEquals(40, countColor(tester, detailsColor))
        assertEquals(0, tester.scheduler.pendingCount)
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** Verifies predictive progress is preserved while reduced motion removes large translation. */
    @Test
    fun reducedPredictiveBackKeepsPlatformProgressWithoutSpatialDisplacement() {
        val tester = PixelTester()
        var navigator: PixelNavigatorState? = null
        val rootRoute = coloredRoute("ROOT", rootColor) { context ->
            navigator = PixelNavigator.of(context)
        }
        tester.pumpWidget(
            navigationMotionRoot(
                tester = tester,
                settings = PixelMotionSettings(reduceMotion = true),
                child = PixelNavigator(
                    initialRoute = rootRoute,
                    vsync = tester.vsync,
                    transitionDuration = 100.milliseconds,
                    defaultTransition = PixelRouteTransition.SlideHorizontal,
                ),
            ),
            40,
            10,
        )
        navigator!!.push(coloredRoute("DETAILS", detailsColor))
        tester.pumpAndSettle()

        assertTrue(navigator!!.onBackStarted(predictiveEvent(0f)))
        navigator!!.onBackProgressed(predictiveEvent(0.5f))
        tester.pumpFrame(0)

        assertEquals(0.5f, navigator!!.predictiveBackProgress)
        assertEquals(tester.pixelAt(1, 1), tester.pixelAt(38, 1))
        assertEquals(0, tester.vsync.activeTickerCount)
        navigator!!.onBackCancelled()
        tester.pumpFrame(0)
        assertNull(navigator!!.predictiveBackProgress)
        tester.dispose()
    }

    /** Verifies an interrupted transition ID owns a fresh zero-based timeline. */
    @Test
    fun interruptedRouteTransitionDoesNotInheritPreviousProgress() {
        val tester = PixelTester()
        var navigator: PixelNavigatorState? = null
        val thirdColor = PixelColor.fromRgb(32, 220, 96)
        val rootRoute = coloredRoute("ROOT", rootColor) { context ->
            navigator = PixelNavigator.of(context)
        }
        tester.pumpWidget(
            navigationMotionRoot(
                tester = tester,
                child = PixelNavigator(
                    initialRoute = rootRoute,
                    vsync = tester.vsync,
                    transitionDuration = 100.milliseconds,
                    defaultTransition = PixelRouteTransition.SlideHorizontal,
                ),
            ),
            40,
            10,
        )

        navigator!!.push(coloredRoute("DETAILS", detailsColor))
        tester.pumpFrame(0)
        tester.pumpFrame(0)
        tester.pumpFrame(50)
        assertEquals(20, countColor(tester, detailsColor))

        navigator!!.push(coloredRoute("THIRD", thirdColor))
        tester.pumpFrame(0)
        assertEquals(40, countColor(tester, detailsColor))
        assertEquals(0, countColor(tester, thirdColor))

        tester.pumpFrame(0)
        tester.pumpFrame(50)
        assertEquals(20, countColor(tester, detailsColor))
        assertEquals(20, countColor(tester, thirdColor))
        tester.pumpAndSettle()
        assertNull(navigator!!.activeTransition)
        tester.dispose()
    }

    /** Verifies a positive animator-scale change rebases from the exact current visual frame. */
    @Test
    fun durationScaleChangeRetargetsCurrentRouteWithoutVisualJump() {
        val tester = PixelTester()
        var navigator: PixelNavigatorState? = null
        val rootRoute = coloredRoute("ROOT", rootColor) { context ->
            navigator = PixelNavigator.of(context)
        }
        val navigatorWidget = PixelNavigator(
            initialRoute = rootRoute,
            vsync = tester.vsync,
            transitionDuration = 100.milliseconds,
            defaultTransition = PixelRouteTransition.SlideHorizontal,
        )
        tester.pumpWidget(
            navigationMotionRoot(tester = tester, child = navigatorWidget),
            40,
            10,
        )
        navigator!!.push(coloredRoute("DETAILS", detailsColor))
        tester.pumpFrame(0)
        tester.pumpFrame(0)
        tester.pumpFrame(40)
        val rootPixelsBeforeRetarget = countColor(tester, rootColor)
        val detailPixelsBeforeRetarget = countColor(tester, detailsColor)

        tester.pumpWidget(
            navigationMotionRoot(
                tester = tester,
                settings = PixelMotionSettings(animatorDurationScale = 2f),
                child = navigatorWidget,
            ),
            40,
            10,
        )
        assertEquals(rootPixelsBeforeRetarget, countColor(tester, rootColor))
        assertEquals(detailPixelsBeforeRetarget, countColor(tester, detailsColor))

        tester.pumpFrame(0)
        tester.pumpFrame(100)
        assertTrue(countColor(tester, detailsColor) > detailPixelsBeforeRetarget)
        tester.pumpAndSettle()
        assertEquals(40, countColor(tester, detailsColor))
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** Verifies enabling reduce motion mid-slide converges immediately instead of jumping policy. */
    @Test
    fun reduceMotionChangeSettlesActiveSpatialRouteImmediately() {
        val tester = PixelTester()
        var navigator: PixelNavigatorState? = null
        val rootRoute = coloredRoute("ROOT", rootColor) { context ->
            navigator = PixelNavigator.of(context)
        }
        val navigatorWidget = PixelNavigator(
            initialRoute = rootRoute,
            vsync = tester.vsync,
            transitionDuration = 100.milliseconds,
            defaultTransition = PixelRouteTransition.SlideHorizontal,
        )
        tester.pumpWidget(
            navigationMotionRoot(tester = tester, child = navigatorWidget),
            40,
            10,
        )
        navigator!!.push(coloredRoute("DETAILS", detailsColor))
        tester.pumpFrame(0)
        tester.pumpFrame(0)
        tester.pumpFrame(25)
        assertTrue(countColor(tester, detailsColor) in 1..39)

        tester.pumpWidget(
            navigationMotionRoot(
                tester = tester,
                settings = PixelMotionSettings(reduceMotion = true),
                child = navigatorWidget,
            ),
            40,
            10,
        )
        assertNull(navigator!!.activeTransition)
        assertEquals(40, countColor(tester, detailsColor))
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** Verifies custom transition proxies never mount duplicate route State instances. */
    @Test
    fun customTransitionPresentsEachRetainedRouteStateExactlyOnce() {
        val tester = PixelTester()
        var navigator: PixelNavigatorState? = null
        var rootStateCreations = 0
        var detailStateCreations = 0
        var rootTaps = 0
        var detailTaps = 0
        val rootRoute = PixelRoute(
            name = "ROOT",
            builder = { context ->
                navigator = PixelNavigator.of(context)
                RouteStateProbe(
                    label = "ROOT",
                    color = rootColor,
                    onCreated = { rootStateCreations += 1 },
                    onTap = { rootTaps += 1 },
                    key = "root-state-probe",
                )
            },
        )
        val detailRoute = PixelRoute(
            name = "DETAILS",
            builder = {
                RouteStateProbe(
                    label = "DETAILS",
                    color = detailsColor,
                    onCreated = { detailStateCreations += 1 },
                    onTap = { detailTaps += 1 },
                    key = "detail-state-probe",
                )
            },
            transitionBuilder = PixelRouteTransitionBuilder { _, _, outgoing, incoming ->
                Stack(children = listOf(outgoing, incoming))
            },
        )
        tester.pumpWidget(
            navigationMotionRoot(
                tester = tester,
                child = PixelNavigator(
                    initialRoute = rootRoute,
                    vsync = tester.vsync,
                    transitionDuration = 100.milliseconds,
                ),
            ),
            40,
            10,
        )
        assertEquals(1, rootStateCreations)

        navigator!!.push(detailRoute)
        tester.pumpFrame(0)
        tester.pumpFrame(0)
        tester.pumpFrame(50)
        assertEquals(1, rootStateCreations)
        assertEquals(1, detailStateCreations)
        assertTrue(tester.exists(find.byKey("root-state-probe")))
        assertTrue(tester.exists(find.byKey("detail-state-probe")))
        assertFalse(tester.dumpSemanticsTree().contains("ROOT"))
        assertTrue(tester.dumpSemanticsTree().contains("DETAILS"))
        tester.tap(find.byKey("DETAILS-probe-tap"))
        assertEquals(0, rootTaps)
        assertEquals(1, detailTaps)

        tester.pumpAndSettle()
        assertEquals(1, rootStateCreations)
        assertEquals(1, detailStateCreations)
        assertEquals(40, countColor(tester, detailsColor))
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** Verifies outgoing stacks become visual-only while the new active stack owns semantics. */
    @Test
    fun multiStackRetainsPaintOnlyOutgoingAndCleansTickerOnCompletionAndDispose() {
        val tester = PixelTester()
        val controller = PixelMultiStackNavigatorController(initialStackId = "home")
        var homeTaps = 0
        var searchTaps = 0
        tester.pumpWidget(
            navigationMotionRoot(
                tester = tester,
                child = PixelMultiStackNavigator(
                    stacks = listOf(
                        PixelNavigatorStack(
                            id = "home",
                            initialRoute = interactiveRoute("HOME", rootColor) { homeTaps += 1 },
                        ),
                        PixelNavigatorStack(
                            id = "search",
                            initialRoute = interactiveRoute("SEARCH", detailsColor) { searchTaps += 1 },
                        ),
                    ),
                    controller = controller,
                    vsync = tester.vsync,
                    transitionDuration = 100.milliseconds,
                    defaultTransition = PixelRouteTransition.SlideHorizontal,
                ),
            ),
            40,
            10,
        )

        assertEquals(PixelStackSelectionResult.Activated, controller.selectStack("search"))
        tester.pumpFrame(0)
        assertTrue(tester.dumpSemanticsTree().contains("SEARCH"))
        assertFalse(tester.dumpSemanticsTree().contains("HOME"))
        assertFalse(tester.exists(find.byKey("HOME-tap")))
        assertTrue(tester.exists(find.byKey("SEARCH-tap")))
        tester.tap(find.byKey("SEARCH-tap"))
        assertEquals(0, homeTaps)
        assertEquals(1, searchTaps)
        assertEquals(rootColor, tester.pixelAt(1, 1))

        tester.pumpFrame(0)
        tester.pumpFrame(50)
        val midPixel = tester.pixelAt(1, 1)
        assertFalse(midPixel == rootColor)
        assertFalse(midPixel == detailsColor)

        assertEquals(PixelStackSelectionResult.Activated, controller.selectStack("home"))
        tester.pumpFrame(0)
        assertEquals(midPixel, tester.pixelAt(1, 1))
        assertTrue(tester.dumpSemanticsTree().contains("HOME"))
        assertFalse(tester.dumpSemanticsTree().contains("SEARCH"))
        tester.pumpFrame(0)
        tester.pumpFrame(100)
        assertEquals(rootColor, tester.pixelAt(1, 1))
        assertEquals(0, tester.vsync.liveTickerCount)

        assertEquals(PixelStackSelectionResult.Activated, controller.selectStack("search"))
        tester.pumpFrame(0)
        tester.pumpFrame(0)
        tester.pumpFrame(25)
        assertTrue(tester.vsync.liveTickerCount > 0)
        tester.dispose()
        assertEquals(0, tester.vsync.activeTickerCount)
        assertEquals(0, tester.vsync.liveTickerCount)
    }

    /** Creates a route that captures context before returning one deterministic colored surface. */
    private fun coloredRoute(
        name: String,
        color: PixelColor,
        beforeBuild: ((com.purride.pixelui.BuildContext) -> Unit)? = null,
    ): PixelRoute {
        return PixelRoute(
            name = name,
            builder = { context ->
                beforeBuild?.invoke(context)
                coloredSurface(name, color)
            },
        )
    }

    /** Creates an active-stack surface with a semantics label and a full-size tap target. */
    private fun interactiveRoute(
        name: String,
        color: PixelColor,
        onTap: () -> Unit,
    ): PixelRoute {
        return PixelRoute(
            name = name,
            transition = PixelRouteTransition.None,
            builder = {
                Semantics(
                    label = name,
                    role = PixelSemanticRole.BUTTON,
                    child = GestureDetector(
                        onTap = onTap,
                        child = Container(width = 40, height = 10, fillColor = color),
                        key = "$name-tap",
                    ),
                )
            },
        )
    }

    /** Creates a non-interactive full-size colored route child. */
    private fun coloredSurface(name: String, color: PixelColor): Widget {
        return Semantics(
            label = name,
            child = Container(width = 40, height = 10, fillColor = color),
        )
    }

    /** Supplies linear route tokens and one Host-owned virtual ticker provider. */
    private fun navigationMotionRoot(
        tester: PixelTester,
        child: Widget,
        settings: PixelMotionSettings = PixelMotionSettings.Default,
        routeDelayMs: Long = 0,
        routeTransition: PixelMotionTransitionPreset = PixelMotionTransitionPreset.Fade,
    ): Widget {
        val routeSpec = PixelMotionSpec(
            duration = 500.milliseconds,
            delay = routeDelayMs.milliseconds,
            curve = Curves.Linear,
            transition = routeTransition,
            role = PixelMotionRole.Spatial,
        )
        return PixelMotionTheme(
            data = PixelMotionThemeData.Default.copy(route = routeSpec),
            child = PixelMotionScope(
                vsync = tester.vsync,
                settings = settings,
                child = child,
            ),
        )
    }

    /** Creates one deterministic platform predictive-back progress event. */
    private fun predictiveEvent(progress: Float): PixelPredictiveBackEvent {
        return PixelPredictiveBackEvent(
            progress = progress,
            touchX = 2f,
            touchY = 4f,
            swipeEdge = PixelPredictiveBackSwipeEdge.Left,
        )
    }

    /** Counts exact route-color pixels across one horizontal test row. */
    private fun countColor(tester: PixelTester, color: PixelColor): Int {
        var count = 0
        for (x in 0 until 40) {
            if (tester.pixelAt(x, 1) == color) count += 1
        }
        return count
    }

    /** Stateful colored route used to detect accidental duplicate subtree mounting. */
    private class RouteStateProbe(
        /** Accessibility label and descendant tap-target key prefix. */
        val label: String,
        /** Solid color painted by this route instance. */
        val color: PixelColor,
        /** Callback invoked once for every real State instance. */
        val onCreated: () -> Unit,
        /** Callback used to verify that only the foreground proxy exports interaction. */
        val onTap: () -> Unit,
        /** Stable route-local widget identity. */
        override val key: Any? = null,
    ) : StatefulWidget(key = key) {
        /** Creates the lifecycle probe state. */
        override fun createState(): State<out StatefulWidget> = RouteStateProbeState()
    }

    /** State that reports creation and paints its owning route color. */
    private class RouteStateProbeState : State<RouteStateProbe>() {
        /** Reports this unique State instance exactly once. */
        override fun initState() {
            widget.onCreated()
        }

        /** Builds one deterministic full-size route surface. */
        override fun build(context: com.purride.pixelui.BuildContext): Widget {
            return Semantics(
                label = widget.label,
                child = GestureDetector(
                    onTap = widget.onTap,
                    child = Container(width = 40, height = 10, fillColor = widget.color),
                    key = "${widget.label}-probe-tap",
                ),
            )
        }
    }
}
