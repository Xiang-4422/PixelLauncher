package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Align
import com.purride.pixelui.Alignment
import com.purride.pixelui.Container
import com.purride.pixelui.PixelMotionRole
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionSettings
import com.purride.pixelui.PixelMotionSpec
import com.purride.pixelui.PixelMotionTheme
import com.purride.pixelui.PixelMotionThemeData
import com.purride.pixelui.PixelMotionTransitionPreset
import com.purride.pixelui.PixelSpringSpec
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Slidable
import com.purride.pixelui.SlidableAction
import com.purride.pixelui.SlidableActionPane
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.internal.PixelRect
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** Regression contracts for Slidable layout, hit ownership, and live motion adaptation. */
class SlidableMotionContractTest {
    /** Opaque foreground color whose visible width exposes the exact horizontal offset. */
    private val foregroundColor: PixelColor = PixelColor.fromRgb(236, 40, 40)

    /** Opaque action color that remains behind translated foreground content. */
    private val actionColor: PixelColor = PixelColor.fromRgb(36, 72, 232)

    /** Verifies action callbacks are exported only inside the actually revealed strip. */
    @Test
    fun actionPaneClickTargetIsClippedToExposedPixels() {
        // Tester owns the deterministic render result and gesture clock.
        val tester = PixelTester()
        // Callback identity lets the test select the action target without layout assumptions.
        var actionPresses = 0
        val onActionPressed = { actionPresses += 1 }
        tester.pumpWidget(
            motionRoot(
                tester = tester,
                child = slidable(
                    width = 40,
                    dismissible = false,
                    action = SlidableAction(
                        label = "ACTION",
                        backgroundColor = actionColor,
                        foregroundColor = PixelColor.White,
                        onPressed = onActionPressed,
                        key = "slidable-action",
                    ),
                ),
            ),
            logicalWidth = 40,
            logicalHeight = 10,
        )

        tester.startGesture(find.byKey("slidable:gesture")).moveBy(-8, 0)

        // Only x=32..39 is revealed; the pane's covered x=20..31 area must not be clickable.
        val actionTarget = checkNotNull(
            tester.renderResult?.clickTargets?.single { target ->
                target.onClick === onActionPressed
            },
        )
        assertEquals(PixelRect(left = 32, top = 0, width = 8, height = 10), actionTarget.bounds)
        tester.tap(find.byKey("slidable-action"))
        assertEquals(1, actionPresses)
        tester.dispose()
    }

    /** Verifies snap thresholds use the constrained row width rather than MediaQuery width. */
    @Test
    fun settleThresholdAndEndpointUseActualLayoutWidth() {
        // Screen is four times wider than the explicitly constrained Slidable row.
        val tester = PixelTester()
        tester.pumpWidget(
            motionRoot(
                tester = tester,
                child = Align(
                    alignment = Alignment.TOP_START,
                    child = SizedBox(
                        width = 20,
                        height = 10,
                        child = slidable(width = 20, dismissible = false),
                    ),
                ),
            ),
            logicalWidth = 80,
            logicalHeight = 10,
        )

        // Six pixels cross the 5px threshold of the actual 10px action pane.
        tester.startGesture(find.byKey("slidable:gesture")).moveBy(-6, 0).up()
        tester.pumpAndSettle()

        assertEquals(10, countColor(tester, foregroundColor, scanWidth = 80))
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** Verifies dismissible configuration cannot strand content without a removal callback. */
    @Test
    fun dismissiblePaneWithoutCallbackSettlesOpenInsteadOfOffscreen() {
        // A missing onDismissed callback means the data owner cannot remove the dismissed row.
        val tester = PixelTester()
        tester.pumpWidget(
            motionRoot(
                tester = tester,
                child = slidable(width = 40, dismissible = true),
            ),
            logicalWidth = 40,
            logicalHeight = 10,
        )

        tester.startGesture(find.byKey("slidable:gesture")).moveBy(-12, 0).up()
        tester.pumpAndSettle()

        // The row remains snapped to its 20px pane instead of exiting to -40px forever.
        assertEquals(20, countColor(tester, foregroundColor, scanWidth = 40))
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** Verifies scale-zero and reduce-motion changes terminate an in-flight settle immediately. */
    @Test
    fun activeSettleReactsImmediatelyToHostMotionPolicyChanges() {
        verifyImmediatePolicyRetarget(
            nextSettings = PixelMotionSettings(animatorDurationScale = 0f),
        )
        verifyImmediatePolicyRetarget(
            nextSettings = PixelMotionSettings(reduceMotion = true),
        )
    }

    /** Verifies a duration-token change rebases from the current visual without a jump. */
    @Test
    fun activeSettleSmoothlyRetargetsWhenThemeChanges() {
        // Theme notifier rebuilds the inherited token while retaining the keyed Slidable State.
        val tester = PixelTester()
        val theme = ValueNotifier(motionTheme(durationMs = 100))
        tester.pumpWidget(
            ValueListenableBuilder(theme) { _, currentTheme ->
                PixelMotionTheme(
                    data = currentTheme,
                    child = PixelMotionScope(
                        vsync = tester.vsync,
                        settings = PixelMotionSettings.Default,
                        child = slidable(width = 40, dismissible = false),
                    ),
                )
            },
            logicalWidth = 40,
            logicalHeight = 10,
        )
        tester.startGesture(find.byKey("slidable:gesture")).moveBy(-12, 0).up()
        tester.pumpFrame(0)
        tester.pumpFrame(50)
        assertEquals(24, countColor(tester, foregroundColor, scanWidth = 40))

        theme.value = motionTheme(durationMs = 200)
        tester.pumpFrame(0)
        assertEquals(24, countColor(tester, foregroundColor, scanWidth = 40))
        // The replacement ticker is created during the rebuild and anchors on the next frame.
        tester.pumpFrame(0)
        tester.pumpFrame(100)
        assertEquals(22, countColor(tester, foregroundColor, scanWidth = 40))
        tester.pumpFrame(100)
        assertEquals(20, countColor(tester, foregroundColor, scanWidth = 40))
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** Verifies spring stiffness changes the sampled settle trajectory before the exact endpoint. */
    @Test
    fun settleConsumesSpringToken() {
        // A soft critically damped spring advances more slowly than the linear reference at 50%.
        val tester = PixelTester()
        val softSpring = PixelSpringSpec(stiffness = 25f, dampingRatio = 1f, mass = 1f)
        tester.pumpWidget(
            motionRoot(
                tester = tester,
                theme = motionTheme(durationMs = 100, spring = softSpring),
                child = slidable(width = 40, dismissible = false),
            ),
            logicalWidth = 40,
            logicalHeight = 10,
        )
        tester.startGesture(find.byKey("slidable:gesture")).moveBy(-12, 0).up()
        tester.pumpFrame(0)
        tester.pumpFrame(50)

        assertEquals(26, countColor(tester, foregroundColor, scanWidth = 40))
        tester.pumpFrame(50)
        assertEquals(20, countColor(tester, foregroundColor, scanWidth = 40))
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** Runs one in-flight settle through a Host policy update and asserts synchronous cleanup. */
    private fun verifyImmediatePolicyRetarget(nextSettings: PixelMotionSettings) {
        // Settings notifier models an Android duration-scale or accessibility observer update.
        val tester = PixelTester()
        val settings = ValueNotifier(PixelMotionSettings.Default)
        tester.pumpWidget(
            PixelMotionTheme(
                data = motionTheme(durationMs = 100),
                child = ValueListenableBuilder(settings) { _, currentSettings ->
                    PixelMotionScope(
                        vsync = tester.vsync,
                        settings = currentSettings,
                        child = slidable(width = 40, dismissible = false),
                    )
                },
            ),
            logicalWidth = 40,
            logicalHeight = 10,
        )
        tester.startGesture(find.byKey("slidable:gesture")).moveBy(-12, 0).up()
        tester.pumpFrame(0)
        tester.pumpFrame(50)
        assertEquals(24, countColor(tester, foregroundColor, scanWidth = 40))

        settings.value = nextSettings
        tester.pumpFrame(0)

        assertEquals(20, countColor(tester, foregroundColor, scanWidth = 40))
        assertEquals(0, tester.scheduler.pendingCount)
        assertEquals(0, tester.vsync.activeTickerCount)
        assertEquals(0, tester.vsync.liveTickerCount)
        tester.dispose()
    }

    /** Creates one fixed-width Slidable with a half-width end action pane. */
    private fun slidable(
        width: Int,
        dismissible: Boolean,
        action: Widget = Container(fillColor = actionColor),
    ): Widget {
        return Slidable(
            child = Container(width = width, height = 10, fillColor = foregroundColor),
            endActionPane = SlidableActionPane(
                children = listOf(action),
                extentRatio = 0.5f,
                dismissible = dismissible,
                dismissThreshold = 0.5f,
            ),
            key = "slidable",
        )
    }

    /** Wraps one Slidable in deterministic theme and Host motion inherited scopes. */
    private fun motionRoot(
        tester: PixelTester,
        child: Widget,
        theme: PixelMotionThemeData = motionTheme(durationMs = 100),
    ): Widget {
        return PixelMotionTheme(
            data = theme,
            child = PixelMotionScope(
                vsync = tester.vsync,
                settings = PixelMotionSettings.Default,
                child = child,
            ),
        )
    }

    /** Creates one linear Slidable token, optionally carrying a physical spring. */
    private fun motionTheme(
        durationMs: Long,
        spring: PixelSpringSpec? = null,
    ): PixelMotionThemeData {
        return PixelMotionThemeData.Default.copy(
            slidableSettle = PixelMotionSpec(
                duration = durationMs.milliseconds,
                curve = Curves.Linear,
                transition = PixelMotionTransitionPreset.SlideHorizontal,
                spring = spring,
                role = PixelMotionRole.Spatial,
            ),
        )
    }

    /** Counts exact foreground pixels across one rendered scan line. */
    private fun countColor(
        tester: PixelTester,
        color: PixelColor,
        scanWidth: Int,
    ): Int {
        var count = 0
        for (x in 0 until scanWidth) {
            if (tester.pixelAt(x, 1) == color) count += 1
        }
        return count
    }
}
