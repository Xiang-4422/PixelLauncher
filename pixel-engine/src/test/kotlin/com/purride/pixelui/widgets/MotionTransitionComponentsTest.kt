package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Container
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.PixelComponentColorTokens
import com.purride.pixelui.PixelComponentTokens
import com.purride.pixelui.PixelMotionRole
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionSettings
import com.purride.pixelui.PixelMotionSpec
import com.purride.pixelui.PixelMotionTheme
import com.purride.pixelui.PixelMotionThemeData
import com.purride.pixelui.PixelMotionTransitionPreset
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelStateProperty
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.Popover
import com.purride.pixelui.Semantics
import com.purride.pixelui.Slidable
import com.purride.pixelui.SlidableActionPane
import com.purride.pixelui.SlidableDirection
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.IntOffset
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** Locks Slidable and Popover motion to virtual-clock pixels and interaction contracts. */
class MotionTransitionComponentsTest {
    /** Opaque colors chosen so transition boundaries are exact and easy to count. */
    private val foregroundColor: PixelColor = PixelColor.fromRgb(240, 32, 32)
    private val paneColor: PixelColor = PixelColor.fromRgb(32, 64, 240)
    private val anchorColor: PixelColor = PixelColor.fromRgb(16, 180, 64)

    /** Verifies direct drag and the complete 0/25/50/75/100 settle frame sequence. */
    @Test
    fun slidableFollowsDragAndUsesLinearThemeSettleFrames() {
        val tester = PixelTester()
        tester.pumpWidget(
            motionRoot(
                tester = tester,
                theme = motionTheme(slidableDurationMs = 100),
                child = slidable(dismissible = false),
            ),
            logicalWidth = 40,
            logicalHeight = 10,
        )

        val gesture = tester.startGesture(find.byKey("slidable:gesture"))
        gesture.moveBy(-12, 0)
        assertEquals(28, countColor(tester, foregroundColor))
        gesture.up()
        tester.pumpFrame(0)
        assertEquals(28, countColor(tester, foregroundColor))

        tester.pumpFrame(25)
        assertEquals(26, countColor(tester, foregroundColor))
        tester.pumpFrame(25)
        assertEquals(24, countColor(tester, foregroundColor))
        tester.pumpFrame(25)
        assertEquals(22, countColor(tester, foregroundColor))
        tester.pumpFrame(25)
        assertEquals(20, countColor(tester, foregroundColor))
        assertEquals(0, tester.vsync.activeTickerCount)
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** Verifies an interrupted settle rebases the next drag on the sampled visual offset. */
    @Test
    fun slidableNewDragInterruptsFromCurrentVisualOffset() {
        val tester = PixelTester()
        tester.pumpWidget(
            motionRoot(tester, motionTheme(slidableDurationMs = 100), slidable(false)),
            40,
            10,
        )
        tester.startGesture(find.byKey("slidable:gesture")).moveBy(-12, 0).up()
        tester.pumpFrame(0)
        tester.pumpFrame(50)
        assertEquals(24, countColor(tester, foregroundColor))

        val interruptingGesture = tester.startGesture(find.byKey("slidable:gesture"))
        interruptingGesture.moveBy(4, 0)
        assertEquals(28, countColor(tester, foregroundColor))
        interruptingGesture.up()
        tester.pumpAndSettle()

        assertEquals(20, countColor(tester, foregroundColor))
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** Verifies dismiss delivery occurs at the exit endpoint and reduced motion is synchronous. */
    @Test
    fun slidableDismissesExactlyOnceAtEndpointAndReduceMotionHasNoTicker() {
        val tester = PixelTester()
        var dismissCount = 0
        tester.pumpWidget(
            motionRoot(
                tester = tester,
                theme = motionTheme(slidableDurationMs = 100),
                child = slidable(
                    dismissible = true,
                    onDismissed = { dismissCount += 1 },
                ),
            ),
            40,
            10,
        )
        tester.startGesture(find.byKey("slidable:gesture")).moveBy(-12, 0).up()
        tester.pumpFrame(0)
        tester.pumpFrame(99)
        assertEquals(0, dismissCount)
        tester.pumpFrame(1)
        assertEquals(1, dismissCount)
        assertEquals(0, countColor(tester, foregroundColor))
        tester.pumpFrame(100)
        assertEquals(1, dismissCount)
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()

        val reducedTester = PixelTester()
        var reducedDismissCount = 0
        reducedTester.pumpWidget(
            motionRoot(
                tester = reducedTester,
                theme = motionTheme(slidableDurationMs = 100),
                settings = PixelMotionSettings(reduceMotion = true),
                child = slidable(
                    dismissible = true,
                    onDismissed = { reducedDismissCount += 1 },
                ),
            ),
            40,
            10,
        )
        reducedTester.startGesture(find.byKey("slidable:gesture")).moveBy(-12, 0).up()
        assertEquals(1, reducedDismissCount)
        assertEquals(0, reducedTester.scheduler.pendingCount)
        assertEquals(0, reducedTester.vsync.liveTickerCount)
        reducedTester.dispose()
    }

    /** Verifies Popover delay, exit paint-only state, rapid reversal, and ticker cleanup. */
    @Test
    fun popoverRetainsVisualExitWithoutHitOrSemanticsAndReversesSmoothly() {
        val tester = PixelTester()
        val expanded = ValueNotifier(false)
        var anchorTaps = 0
        var contentTaps = 0
        val root = popoverRoot(
            tester = tester,
            expanded = expanded,
            onAnchorTap = { anchorTaps += 1 },
            onContentTap = { contentTaps += 1 },
        )
        tester.pumpWidget(root, 20, 10)

        expanded.value = true
        tester.pumpFrame(0)
        tester.pumpFrame(0)
        tester.pumpFrame(10)
        assertEquals(anchorColor, tester.pixelAt(1, 1))
        assertFalse(tester.dumpSemanticsTree().contains("POPOVER_CONTENT"))
        tester.pumpFrame(50)
        assertTrue(tester.dumpSemanticsTree().contains("POPOVER_CONTENT"))
        tester.pumpAndSettle()
        tester.tap(find.byKey("popover-content-tap"))
        assertEquals(1, contentTaps)

        expanded.value = false
        tester.pumpFrame(0)
        val exitStartPixel = tester.pixelAt(1, 1)
        assertEquals(foregroundColor, exitStartPixel)
        assertFalse(tester.dumpSemanticsTree().contains("POPOVER_CONTENT"))
        tester.tap(find.byKey("popover-anchor-tap"))
        assertEquals(1, anchorTaps)
        assertEquals(1, contentTaps)
        tester.pumpFrame(0)
        tester.pumpFrame(60)
        val interruptedPixel = tester.pixelAt(1, 1)

        expanded.value = true
        tester.pumpFrame(0)
        assertEquals(interruptedPixel, tester.pixelAt(1, 1))
        tester.pumpAndSettle()
        assertEquals(foregroundColor, tester.pixelAt(1, 1))
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** Verifies scale zero and missing MotionScope never manufacture a component clock. */
    @Test
    fun popoverImmediatePoliciesApplyTerminalStateWithoutPendingFrames() {
        val scaledTester = PixelTester()
        scaledTester.pumpWidget(
            PixelMotionTheme(
                data = motionTheme(popoverDurationMs = 100, popoverDelayMs = 40),
                child = PixelMotionScope(
                    vsync = scaledTester.vsync,
                    settings = PixelMotionSettings(animatorDurationScale = 0f),
                    child = Popover(
                        anchor = Container(width = 20, height = 10, fillColor = anchorColor),
                        content = Container(width = 20, height = 10, fillColor = foregroundColor),
                        expanded = true,
                        contentOffset = IntOffset(0, 0),
                    ),
                ),
            ),
            20,
            10,
        )
        assertEquals(foregroundColor, scaledTester.pixelAt(1, 1))
        assertEquals(0, scaledTester.scheduler.pendingCount)
        assertEquals(0, scaledTester.vsync.liveTickerCount)
        scaledTester.dispose()

        val scopeLessTester = PixelTester()
        scopeLessTester.pumpWidget(
            Popover(
                anchor = Container(width = 20, height = 10, fillColor = anchorColor),
                content = Container(width = 20, height = 10, fillColor = foregroundColor),
                expanded = true,
                contentOffset = IntOffset(0, 0),
            ),
            20,
            10,
        )
        assertEquals(foregroundColor, scopeLessTester.pixelAt(1, 1))
        assertEquals(0, scopeLessTester.scheduler.pendingCount)
        assertEquals(0, scopeLessTester.vsync.liveTickerCount)
        scopeLessTester.dispose()
    }

    /** Running Popover motion retargets continuously when theme or system preferences change. */
    @Test
    fun popoverRetargetsRunningMotionWhenEnvironmentChanges() {
        val tester = PixelTester()
        val theme = ValueNotifier(motionTheme(popoverDurationMs = 1_000, popoverDelayMs = 100))
        val settings = ValueNotifier(PixelMotionSettings.Default)
        tester.pumpWidget(
            ValueListenableBuilder(theme) { _, currentTheme ->
                ValueListenableBuilder(settings) { _, currentSettings ->
                    motionRoot(
                        tester = tester,
                        theme = currentTheme,
                        settings = currentSettings,
                        child = Popover(
                            anchor = Container(width = 20, height = 10, fillColor = anchorColor),
                            content = Container(width = 20, height = 10, fillColor = foregroundColor),
                            expanded = true,
                            contentOffset = IntOffset(0, 0),
                            key = "popover",
                        ),
                    )
                }
            },
            20,
            10,
        )
        tester.pumpFrame(0)
        tester.pumpFrame(0)
        tester.pumpFrame(600)
        val beforeThemeChange = tester.pixelAt(1, 1)
        assertTrue(beforeThemeChange != anchorColor && beforeThemeChange != foregroundColor)

        theme.value = motionTheme(popoverDurationMs = 200)
        tester.pumpFrame(0)
        assertEquals(beforeThemeChange, tester.pixelAt(1, 1))
        tester.pumpFrame(0)
        tester.pumpFrame(100)
        val afterThemeRetarget = tester.pixelAt(1, 1)
        assertTrue(afterThemeRetarget != beforeThemeChange && afterThemeRetarget != foregroundColor)

        settings.value = PixelMotionSettings(animatorDurationScale = 0f)
        tester.pumpFrame(0)
        assertEquals(foregroundColor, tester.pixelAt(1, 1))
        assertEquals(0, tester.scheduler.pendingCount)
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()

        val reducedTester = PixelTester()
        val reducedSettings = ValueNotifier(PixelMotionSettings.Default)
        reducedTester.pumpWidget(
            ValueListenableBuilder(reducedSettings) { _, currentSettings ->
                motionRoot(
                    tester = reducedTester,
                    theme = motionTheme(popoverDurationMs = 1_000, popoverDelayMs = 500),
                    settings = currentSettings,
                    child = Popover(
                        anchor = Container(width = 20, height = 10, fillColor = anchorColor),
                        content = Container(width = 20, height = 10, fillColor = foregroundColor),
                        expanded = true,
                        contentOffset = IntOffset(0, 0),
                    ),
                )
            },
            20,
            10,
        )
        reducedTester.pumpFrame(0)
        reducedTester.pumpFrame(0)
        reducedTester.pumpFrame(250)
        assertEquals(anchorColor, reducedTester.pixelAt(1, 1))
        reducedSettings.value = PixelMotionSettings(reduceMotion = true)
        reducedTester.pumpFrame(0)
        reducedTester.pumpFrame(0)
        reducedTester.pumpFrame(80)
        assertEquals(foregroundColor, reducedTester.pixelAt(1, 1))
        assertEquals(0, reducedTester.vsync.activeTickerCount)
        reducedTester.dispose()
        assertEquals(0, reducedTester.vsync.liveTickerCount)
    }

    /** Creates one fixed-width Slidable used by deterministic pixel-boundary assertions. */
    private fun slidable(
        dismissible: Boolean,
        onDismissed: ((SlidableDirection) -> Unit)? = null,
    ): Widget {
        return PixelTheme(
            tokens = undecoratedSlidableTheme,
            child = Slidable(
                child = Container(width = 40, height = 10, fillColor = foregroundColor),
                endActionPane = SlidableActionPane(
                    children = listOf(Container(fillColor = paneColor)),
                    extentRatio = 0.5f,
                    dismissible = dismissible,
                    dismissThreshold = 0.5f,
                ),
                onDismissed = onDismissed,
                key = "slidable",
            ),
        )
    }

    /**
     * 去除 Slidable 表面装饰的 token 图，使断言只测量水平位移而不受内边距与边框影响。
     */
    private val undecoratedSlidableTheme: PixelThemeTokens = PixelThemeTokens.Dark.copy(
        components = PixelComponentTokens.Default.copy(
            slidable = PixelComponentColorTokens(
                containerColor = PixelStateProperty.constant(null),
                contentColor = PixelComponentTokens.Default.slidable.contentColor,
                borderColor = PixelStateProperty.constant(null),
                focusIndicator = null,
                padding = EdgeInsets.all(0),
                borderWidth = 0,
                cornerRadius = 0,
            ),
        ),
    )

    /** Wraps a controlled Popover in deterministic enter and exit tokens. */
    private fun popoverRoot(
        tester: PixelTester,
        expanded: ValueNotifier<Boolean>,
        onAnchorTap: () -> Unit,
        onContentTap: () -> Unit,
    ): Widget {
        val theme = motionTheme(popoverDurationMs = 100, popoverDelayMs = 20)
        return motionRoot(
            tester = tester,
            theme = theme,
            child = ValueListenableBuilder(expanded) { _, isExpanded ->
                Popover(
                    anchor = GestureDetector(
                        onTap = onAnchorTap,
                        child = Container(width = 20, height = 10, fillColor = anchorColor),
                        key = "popover-anchor-tap",
                    ),
                    content = Semantics(
                        label = "POPOVER_CONTENT",
                        role = PixelSemanticRole.BUTTON,
                        child = GestureDetector(
                            onTap = onContentTap,
                            child = Container(width = 20, height = 10, fillColor = foregroundColor),
                            key = "popover-content-tap",
                        ),
                    ),
                    expanded = isExpanded,
                    contentOffset = IntOffset(0, 0),
                    key = "popover",
                )
            },
        )
    }

    /** Provides the Host clock and settings that all animated test components must consume. */
    private fun motionRoot(
        tester: PixelTester,
        theme: PixelMotionThemeData,
        child: Widget,
        settings: PixelMotionSettings = PixelMotionSettings.Default,
    ): Widget {
        return PixelMotionTheme(
            data = theme,
            child = PixelMotionScope(
                vsync = tester.vsync,
                settings = settings,
                child = child,
            ),
        )
    }

    /** Creates linear spatial tokens so frame percentages map directly to pixel positions. */
    private fun motionTheme(
        slidableDurationMs: Long = 100,
        popoverDurationMs: Long = 100,
        popoverDelayMs: Long = 0,
    ): PixelMotionThemeData {
        val slidable = PixelMotionSpec(
            duration = slidableDurationMs.milliseconds,
            curve = Curves.Linear,
            transition = PixelMotionTransitionPreset.SlideHorizontal,
            role = PixelMotionRole.Spatial,
        )
        val popover = PixelMotionSpec(
            duration = popoverDurationMs.milliseconds,
            delay = popoverDelayMs.milliseconds,
            curve = Curves.Linear,
            transition = PixelMotionTransitionPreset.Fade,
            role = PixelMotionRole.Spatial,
        )
        return PixelMotionThemeData.Default.copy(
            slidableSettle = slidable,
            popoverEnter = popover,
            popoverExit = popover.copy(delay = 0.milliseconds),
        )
    }

    /** Counts foreground pixels on the single rendered test row. */
    private fun countColor(tester: PixelTester, color: PixelColor): Int {
        var count = 0
        for (x in 0 until 40) {
            if (tester.pixelAt(x, 1) == color) count += 1
        }
        return count
    }
}
