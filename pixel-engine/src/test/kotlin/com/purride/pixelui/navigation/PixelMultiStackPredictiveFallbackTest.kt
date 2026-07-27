package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** Regression contracts for multi-stack predictive child ownership and root fallback previews. */
class PixelMultiStackPredictiveFallbackTest {
    /** Solid initial-stack color used to assert an exact root-return destination. */
    private val homeColor: PixelColor = PixelColor.fromRgb(32, 72, 224)

    /** Solid secondary-stack color used to assert cancellation restores the source exactly. */
    private val settingsColor: PixelColor = PixelColor.fromRgb(232, 48, 48)

    /** Verifies an accepted child session cannot turn into an unrelated root fallback at commit. */
    @Test
    fun invalidatedAcceptedChildSessionDoesNotFallbackToInitialStack() {
        // Tester and root dispatcher model one platform predictive-back lifecycle.
        val tester = PixelTester()
        val rootDispatcher = PixelBackDispatcher()
        // Controller starts at home before selecting the secondary stack under test.
        val controller = PixelMultiStackNavigatorController(initialStackId = "home")
        // Disabling this retained handler after start invalidates only the accepted child session.
        val childEnabled = ValueNotifier(true)
        val childCallback = RejectingPredictiveCallback()
        val settingsRoot = ValueListenableBuilder(childEnabled) { _, enabled ->
            PixelPredictiveBackHandler(
                enabled = enabled,
                callback = childCallback,
                child = Text("SETTINGS"),
                key = "settings-child-back",
            )
        }
        tester.pumpWidget(
            PixelBackHost(
                dispatcher = rootDispatcher,
                child = multiStack(
                    tester = tester,
                    controller = controller,
                    home = Text("HOME"),
                    settings = settingsRoot,
                    transition = PixelRouteTransition.None,
                ),
            ),
            logicalWidth = 40,
            logicalHeight = 10,
        )
        controller.selectStack("settings")
        tester.pumpFrame(0)

        assertTrue(rootDispatcher.startPredictiveBack(predictiveEvent(progress = 0f)))
        rootDispatcher.updatePredictiveBack(predictiveEvent(progress = 0.4f))
        childEnabled.value = false
        tester.pumpFrame(0)

        assertEquals(listOf("start:0.0", "progress:0.4", "cancel"), childCallback.events)
        assertFalse(rootDispatcher.commitPredictiveBack())
        assertEquals("settings", controller.activeStackId)
        assertNull(controller.activePredictiveReturnProgress)
        tester.dispose()
    }

    /** Verifies secondary-root progress previews, cancellation, and commit share one direct path. */
    @Test
    fun secondaryRootFallbackDirectlyPreviewsCancelsAndCommitsWithoutReplay() {
        // Motion scope enables ordinary stack switches so commit replay would leak a live ticker.
        val tester = PixelTester()
        val rootDispatcher = PixelBackDispatcher()
        val controller = PixelMultiStackNavigatorController(initialStackId = "home")
        val content = PixelMotionTheme(
            data = com.purride.pixelui.PixelMotionThemeData.Default,
            child = PixelMotionScope(
                vsync = tester.vsync,
                settings = PixelMotionSettings.Default,
                child = multiStack(
                    tester = tester,
                    controller = controller,
                    home = Container(width = 40, height = 10, fillColor = homeColor),
                    settings = Container(width = 40, height = 10, fillColor = settingsColor),
                    transition = PixelRouteTransition.Fade,
                ),
            ),
        )
        tester.pumpWidget(
            PixelBackHost(dispatcher = rootDispatcher, child = content),
            logicalWidth = 40,
            logicalHeight = 10,
        )
        controller.selectStack("settings")
        tester.pumpAndSettle()
        assertEquals(settingsColor, tester.pixelAt(1, 1))
        assertEquals(0, tester.vsync.liveTickerCount)

        // Progress=1 must reveal the already-mounted initial stack before any commit mutation.
        assertTrue(rootDispatcher.startPredictiveBack(predictiveEvent(progress = 0f)))
        rootDispatcher.updatePredictiveBack(predictiveEvent(progress = 1f))
        tester.pumpFrame(0)
        assertEquals(1f, controller.activePredictiveReturnProgress)
        assertEquals("settings", controller.activeStackId)
        assertEquals(homeColor, tester.pixelAt(1, 1))

        rootDispatcher.cancelPredictiveBack()
        tester.pumpFrame(0)
        assertNull(controller.activePredictiveReturnProgress)
        assertEquals("settings", controller.activeStackId)
        assertEquals(settingsColor, tester.pixelAt(1, 1))

        // A partial preview commits directly to its destination and creates no replay controller.
        assertTrue(rootDispatcher.startPredictiveBack(predictiveEvent(progress = 0f)))
        rootDispatcher.updatePredictiveBack(predictiveEvent(progress = 0.65f))
        tester.pumpFrame(0)
        assertEquals(0.65f, controller.activePredictiveReturnProgress)
        assertTrue(rootDispatcher.commitPredictiveBack())
        tester.pumpFrame(0)

        assertEquals("home", controller.activeStackId)
        assertNull(controller.activePredictiveReturnProgress)
        assertEquals(homeColor, tester.pixelAt(1, 1))
        assertEquals(0, tester.scheduler.pendingCount)
        assertEquals(0, tester.vsync.activeTickerCount)
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** An in-flight ordinary stack switch obeys a live scale-zero policy without visual replay. */
    @Test
    fun activeStackSwitchRetargetsWhenMotionSettingsChange() {
        val tester = PixelTester()
        val controller = PixelMultiStackNavigatorController(initialStackId = "home")
        val settings = ValueNotifier(PixelMotionSettings.Default)
        tester.pumpWidget(
            ValueListenableBuilder(settings) { _, currentSettings ->
                PixelMotionTheme(
                    data = com.purride.pixelui.PixelMotionThemeData.Default,
                    child = PixelMotionScope(
                        vsync = tester.vsync,
                        settings = currentSettings,
                        child = multiStack(
                            tester = tester,
                            controller = controller,
                            home = Container(width = 40, height = 10, fillColor = homeColor),
                            settings = Container(width = 40, height = 10, fillColor = settingsColor),
                            transition = PixelRouteTransition.Fade,
                            transitionDurationMs = 1_000,
                        ),
                    ),
                )
            },
            logicalWidth = 40,
            logicalHeight = 10,
        )

        controller.selectStack("settings")
        tester.pumpFrame(0)
        tester.pumpFrame(0)
        tester.pumpFrame(500)
        val midPixel = tester.pixelAt(1, 1)
        assertTrue(midPixel != homeColor && midPixel != settingsColor)

        settings.value = PixelMotionSettings(animatorDurationScale = 0f)
        tester.pumpFrame(0)

        assertEquals(settingsColor, tester.pixelAt(1, 1))
        assertEquals(0, tester.scheduler.pendingCount)
        assertEquals(0, tester.vsync.activeTickerCount)
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** Builds one two-stack host with deterministic root routes and transition policy. */
    private fun multiStack(
        tester: PixelTester,
        controller: PixelMultiStackNavigatorController,
        home: Widget,
        settings: Widget,
        transition: PixelRouteTransition,
        transitionDurationMs: Long = 100,
    ): Widget {
        return PixelMultiStackNavigator(
            stacks = listOf(
                PixelTypedNavigatorStack(
                    id = "home",
                    initialRequest = testRouteRequest(
                        name = "home",
                        transition = PixelRouteTransition.None,
                        builder = { home },
                    ),
                ),
                PixelTypedNavigatorStack(
                    id = "settings",
                    initialRequest = testRouteRequest(
                        name = "settings",
                        transition = PixelRouteTransition.None,
                        builder = { settings },
                    ),
                ),
            ),
            controller = controller,
            vsync = tester.vsync,
            transitionDuration = transitionDurationMs.milliseconds,
            defaultTransition = transition,
        )
    }

    /** Creates one deterministic left-edge predictive-back progress event. */
    private fun predictiveEvent(progress: Float): PixelPredictiveBackEvent {
        return PixelPredictiveBackEvent(
            progress = progress,
            touchX = 1f,
            touchY = 1f,
            swipeEdge = PixelPredictiveBackSwipeEdge.Left,
        )
    }
}

/** Child callback that accepts start but never claims a stale commit. */
private class RejectingPredictiveCallback : PixelPredictiveBackCallback {
    /** Ordered events proving which predictive lifecycle this callback actually received. */
    val events: MutableList<String> = mutableListOf()

    /** Accepts and records the child-owned session. */
    override fun onBackStarted(event: PixelPredictiveBackEvent): Boolean {
        events += "start:${event.progress}"
        return true
    }

    /** Records child-only progress before invalidation. */
    override fun onBackProgressed(event: PixelPredictiveBackEvent) {
        events += "progress:${event.progress}"
    }

    /** Records dispatcher-driven cancellation when the handler is removed. */
    override fun onBackCancelled() {
        events += "cancel"
    }

    /** Rejects any stale commit rather than mutating navigation state. */
    override fun onBackCommitted(): Boolean {
        events += "commit"
        return false
    }
}
