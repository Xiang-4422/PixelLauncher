package com.purride.pixelui.testing

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Container
import com.purride.pixelui.Dialog
import com.purride.pixelui.Opacity
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Stack
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.InteractionDetector
import com.purride.pixelui.internal.SliderWidget
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies PixelTester virtual down/up/cancel/hover interaction-state delivery. */
class PixelTesterInteractionTest {
    /** Click targets receive a balanced pressed pair before tap, with a paintable down frame. */
    @Test
    fun virtualDownAndUpDeliverPressedThenTap() {
        val events = mutableListOf<String>()
        val tester = PixelTester()
        tester.pumpWidget(interactionWidget(events), logicalWidth = 8, logicalHeight = 8)

        tester.down(find.byKey(InteractionKey))
        assertEquals(listOf("pressed:true"), events)

        tester.up()
        assertEquals(listOf("pressed:true", "pressed:false", "tap"), events)
        tester.dispose()
    }

    /** Cancel and pointer movement both clear pressed without invoking tap. */
    @Test
    fun virtualCancelAndTakeoverClearPressedExactlyOnce() {
        val events = mutableListOf<String>()
        val tester = PixelTester()
        tester.pumpWidget(interactionWidget(events), logicalWidth = 8, logicalHeight = 8)

        tester.down(find.byKey(InteractionKey))
        tester.cancel()
        tester.down(find.byKey(InteractionKey)).moveBy(dx = 2, dy = 0).up()

        assertEquals(
            listOf("pressed:true", "pressed:false", "pressed:true", "pressed:false"),
            events,
        )
        tester.dispose()
    }

    /** Virtual hover transfers a balanced enter/exit pair without synthesizing a click. */
    @Test
    fun virtualHoverAndExitDeliverHoverOnly() {
        val events = mutableListOf<String>()
        val tester = PixelTester()
        tester.pumpWidget(interactionWidget(events), logicalWidth = 8, logicalHeight = 8)

        tester.hover(find.byKey(InteractionKey))
        tester.hover(find.byKey(InteractionKey))
        tester.exitHover()

        assertEquals(listOf("hovered:true", "hovered:false"), events)
        tester.dispose()
    }

    /** Slider down/move/up keeps release separate from drag and balances pressed feedback. */
    @Test
    fun sliderVirtualPointerReleasesOnlyOnUp() {
        val events = mutableListOf<String>()
        val tester = PixelTester()
        tester.pumpWidget(
            SliderWidget(
                value = 0.5f,
                onDrag = { events += "drag" },
                onRelease = { events += "release" },
                onPressedChanged = { events += "pressed:$it" },
                onHoveredChanged = { events += "hovered:$it" },
                key = SliderKey,
            ),
            logicalWidth = 12,
            logicalHeight = 8,
        )

        val gesture = tester.down(find.byKey(SliderKey))
        gesture.moveBy(dx = 2, dy = 0)
        assertEquals(listOf("pressed:true", "drag"), events)
        gesture.up()
        assertEquals(listOf("pressed:true", "drag", "pressed:false", "release"), events)

        tester.hover(find.byKey(SliderKey))
        tester.exitHover()
        assertEquals("hovered:true", events[4])
        assertEquals("hovered:false", events[5])
        tester.dispose()
    }

    /** Exiting a dialog while pressed cancels its target and never calls the dismissed action. */
    @Test
    fun dialogExitCancelsCapturedClickBeforeUp() {
        val events = mutableListOf<String>()
        val tester = PixelTester()
        tester.pumpWidget(
            widget = Dialog(content = interactionWidget(events), key = DialogKey),
            logicalWidth = 20,
            logicalHeight = 20,
        )

        val gesture = tester.down(find.byKey(InteractionKey))
        tester.pumpWidget(emptySurface(), logicalWidth = 20, logicalHeight = 20)
        gesture.up()

        assertEquals(listOf("pressed:true", "pressed:false"), events)
        tester.dispose()
    }

    /** Removing an ordinary target balances pressed feedback and makes the pending up inert. */
    @Test
    fun removedControlCannotReceivePendingUp() {
        val events = mutableListOf<String>()
        val tester = PixelTester()
        tester.pumpWidget(interactionWidget(events), logicalWidth = 8, logicalHeight = 8)

        tester.down(find.byKey(InteractionKey))
        tester.pumpWidget(emptySurface(), logicalWidth = 8, logicalHeight = 8)
        tester.up()

        assertEquals(listOf("pressed:true", "pressed:false"), events)
        tester.dispose()
    }

    /** Up remains bound to the down source instead of re-hitting an exposed background control. */
    @Test
    fun removedForegroundDoesNotRetargetTapToSameCoordinateBackground() {
        val foregroundEvents = mutableListOf<String>()
        val backgroundEvents = mutableListOf<String>()
        val tester = PixelTester()
        val background = interactionWidget(backgroundEvents, key = BackgroundKey)
        tester.pumpWidget(
            widget = Stack(children = listOf(background, interactionWidget(foregroundEvents))),
            logicalWidth = 8,
            logicalHeight = 8,
        )

        tester.down(find.byKey(InteractionKey))
        tester.pumpWidget(background, logicalWidth = 8, logicalHeight = 8)
        tester.up()

        assertEquals(listOf("pressed:true", "pressed:false"), foregroundEvents)
        assertEquals(emptyList<String>(), backgroundEvents)
        tester.dispose()
    }

    /** Dismissing an active slider sends pressed=false but suppresses its stale release callback. */
    @Test
    fun dismissedSliderCannotReleaseIntoUnmountedTarget() {
        val events = mutableListOf<String>()
        val tester = PixelTester()
        tester.pumpWidget(
            SliderWidget(
                value = 0.5f,
                onDrag = { events += "drag" },
                onRelease = { events += "release" },
                onPressedChanged = { events += "pressed:$it" },
                key = SliderKey,
            ),
            logicalWidth = 12,
            logicalHeight = 8,
        )

        tester.down(find.byKey(SliderKey))
        tester.pumpWidget(emptySurface(), logicalWidth = 12, logicalHeight = 8)
        tester.up()

        assertEquals(listOf("pressed:true", "pressed:false"), events)
        tester.dispose()
    }

    /** Removing a hovered source emits one exit and does not hover the newly exposed surface. */
    @Test
    fun removedHoverTargetExitsExactlyOnce() {
        val events = mutableListOf<String>()
        val tester = PixelTester()
        tester.pumpWidget(interactionWidget(events), logicalWidth = 8, logicalHeight = 8)

        tester.hover(find.byKey(InteractionKey))
        tester.pumpWidget(emptySurface(), logicalWidth = 8, logicalHeight = 8)
        tester.exitHover()

        assertEquals(listOf("hovered:true", "hovered:false"), events)
        tester.dispose()
    }

    /** Disabling a retained control after down prevents its obsolete enabled callback from firing. */
    @Test
    fun disabledControlCannotReceiveCapturedUp() {
        var taps = 0
        val tester = PixelTester()
        val onPressed = { taps += 1 }
        tester.pumpWidget(
            OutlinedButton(text = "OK", onPressed = onPressed, enabled = true, key = ButtonKey),
            logicalWidth = 24,
            logicalHeight = 12,
        )

        tester.down(find.byKey(ButtonKey))
        tester.pumpWidget(
            OutlinedButton(text = "OK", onPressed = onPressed, enabled = false, key = ButtonKey),
            logicalWidth = 24,
            logicalHeight = 12,
        )
        tester.up()

        assertEquals(0, taps)
        tester.dispose()
    }

    /** A target entering opacity-zero paint-only mode is cancelled before the pending up. */
    @Test
    fun paintOnlyTargetCannotReceiveCapturedUp() {
        val events = mutableListOf<String>()
        val tester = PixelTester()
        tester.pumpWidget(
            Opacity(opacity = 1f, child = interactionWidget(events), key = OpacityKey),
            logicalWidth = 8,
            logicalHeight = 8,
        )

        tester.down(find.byKey(InteractionKey))
        tester.pumpWidget(
            Opacity(opacity = 0f, child = interactionWidget(events), key = OpacityKey),
            logicalWidth = 8,
            logicalHeight = 8,
        )
        tester.up()

        assertEquals(listOf("pressed:true", "pressed:false"), events)
        tester.dispose()
    }

    /** Builds one internal interaction target with stable key and event callbacks. */
    private fun interactionWidget(
        events: MutableList<String>,
        key: String = InteractionKey,
    ): InteractionDetector {
        return InteractionDetector(
            child = Container(
                width = 6,
                height = 6,
                fillColor = PixelColor.White,
                borderColor = null,
            ),
            onTap = { events += "tap" },
            onPressedChanged = { events += "pressed:$it" },
            onHoveredChanged = { events += "hovered:$it" },
            key = key,
        )
    }

    /** Builds a non-interactive replacement that still covers the previous logical coordinates. */
    private fun emptySurface(): Widget {
        return Container(
            width = 8,
            height = 8,
            fillColor = PixelColor.Black,
            borderColor = null,
        )
    }

    private companion object {
        /** Stable finder key for the click interaction target. */
        const val InteractionKey: String = "interaction-target"

        /** Stable finder key for the slider interaction target. */
        const val SliderKey: String = "slider-target"

        /** Stable finder key for the dialog wrapper. */
        const val DialogKey: String = "dialog-target"

        /** Stable finder key for the click surface behind a removable foreground target. */
        const val BackgroundKey: String = "background-target"

        /** Stable finder key for the retained enabled/disabled button. */
        const val ButtonKey: String = "button-target"

        /** Stable wrapper key for the interactive-to-paint-only opacity transition. */
        const val OpacityKey: String = "opacity-target"
    }
}
