package com.purride.pixelui.widgets.animated

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Container
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Retained-tree, pixel, key, interruption, and disposal contract for [AnimatedSwitcher]. */
class AnimatedSwitcherTest {
    /** Verifies real outgoing/incoming subtrees and pixels at 0/25/50/75/100 percent. */
    @Test
    fun crossFadeMountsIndependentLayoutsAndAppliesObservableOpacityAtFiveFrames() {
        // Tester owns the deterministic controller ticker and retained render runtime.
        val tester = PixelTester()
        // Outgoing state must remain mounted through every non-terminal frame.
        var outgoingState: SwitchProbeState? = null
        // Incoming state must mount immediately even while its opacity is zero.
        var incomingState: SwitchProbeState? = null
        tester.pumpWidget(
            switcher(
                tester = tester,
                child = probe(
                    label = "A",
                    key = OutgoingChildKey,
                    color = OutgoingColor,
                    width = 4,
                    height = 1,
                    onReady = { state -> outgoingState = state },
                ),
            ),
            logicalWidth = LogicalWidth,
            logicalHeight = LogicalHeight,
        )
        tester.pumpWidget(
            switcher(
                tester = tester,
                child = probe(
                    label = "B",
                    key = IncomingChildKey,
                    color = IncomingColor,
                    width = 2,
                    height = 2,
                    onReady = { state -> incomingState = state },
                ),
            ),
            logicalWidth = LogicalWidth,
            logicalHeight = LogicalHeight,
        )

        assertCrossFadeFrame(
            tester = tester,
            label = "0%",
            overlap = OutgoingColor,
            outgoingOnly = OutgoingColor,
            incomingOnly = PixelColor.Transparent,
            entryCount = 2,
        )
        assertTrue(tester.exists(find.byKey(OutgoingChildKey)))
        assertTrue(tester.exists(find.byKey(IncomingChildKey)))
        assertFalse(checkNotNull(outgoingState).isDisposed)
        assertFalse(checkNotNull(incomingState).isDisposed)
        assertTrue(tester.dumpRenderTree().contains("RenderOpacity [4x1]"))
        assertTrue(tester.dumpRenderTree().contains("RenderOpacity [2x2]"))

        // First ticker frame anchors elapsed time at the transition's zero point.
        tester.pumpFrame(0)
        tester.pumpFrame(100)
        assertCrossFadeFrame(
            tester = tester,
            label = "25%",
            overlap = HalfIncomingOverHalfOutgoing,
            outgoingOnly = HalfOutgoingColor,
            incomingOnly = HalfIncomingColor,
            entryCount = 2,
        )
        assertFalse(checkNotNull(outgoingState).isDisposed)
        assertFalse(checkNotNull(incomingState).isDisposed)

        tester.pumpFrame(100)
        assertCrossFadeFrame(
            tester = tester,
            label = "50%",
            overlap = HalfIncomingOverHalfOutgoing,
            outgoingOnly = HalfOutgoingColor,
            incomingOnly = HalfIncomingColor,
            entryCount = 2,
        )
        assertFalse(checkNotNull(outgoingState).isDisposed)
        assertFalse(checkNotNull(incomingState).isDisposed)

        tester.pumpFrame(100)
        assertCrossFadeFrame(
            tester = tester,
            label = "75%",
            overlap = HalfIncomingOverHalfOutgoing,
            outgoingOnly = HalfOutgoingColor,
            incomingOnly = HalfIncomingColor,
            entryCount = 2,
        )
        assertFalse(checkNotNull(outgoingState).isDisposed)
        assertFalse(checkNotNull(incomingState).isDisposed)

        tester.pumpFrame(100)
        assertCrossFadeFrame(
            tester = tester,
            label = "100%",
            overlap = IncomingColor,
            outgoingOnly = PixelColor.Transparent,
            incomingOnly = IncomingColor,
            entryCount = 1,
        )
        assertTrue(checkNotNull(outgoingState).isDisposed)
        assertFalse(checkNotNull(incomingState).isDisposed)
        assertFalse(tester.exists(find.byKey(OutgoingChildKey)))
        assertTrue(tester.exists(find.byKey(IncomingChildKey)))
        assertEquals(0, tester.vsync.activeTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)

        tester.dispose()
        assertTrue(checkNotNull(incomingState).isDisposed)
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)
    }

    /** Verifies same type/key updates in place while a same key with another type transitions. */
    @Test
    fun sameTypeAndKeyRetainsStateButSameKeyWithDifferentTypeCrossFades() {
        // Tester drives both the no-op compatible update and the later type replacement.
        val tester = PixelTester()
        // Initial state identity must survive the compatible same-key update.
        var initialState: SwitchProbeState? = null
        // Updated callback exposes the State resolved from the new compatible widget instance.
        var updatedState: SwitchProbeState? = null
        tester.pumpWidget(
            switcher(
                tester = tester,
                child = probe(
                    label = "same-a",
                    key = SameChildKey,
                    color = OutgoingColor,
                    onReady = { state -> initialState = state },
                ),
            ),
            LogicalWidth,
            LogicalHeight,
        )
        tester.pumpWidget(
            switcher(
                tester = tester,
                child = probe(
                    label = "same-b",
                    key = SameChildKey,
                    color = IncomingColor,
                    onReady = { state -> updatedState = state },
                ),
            ),
            LogicalWidth,
            LogicalHeight,
        )

        assertSame(initialState, updatedState)
        assertEquals(1, checkNotNull(updatedState).updateCount)
        assertFalse(checkNotNull(updatedState).isDisposed)
        assertEquals(0, tester.vsync.activeTickerCount)
        assertEquals(1, treeCount(tester.dumpElementTree(), "OpacityWidget"))
        assertEquals(IncomingColor, tester.pixelAt(0, 0))

        // Runtime type differs even though the user key is equal, so retained update is illegal.
        tester.pumpWidget(
            switcher(
                tester = tester,
                child = Text("ALT", key = SameChildKey),
            ),
            LogicalWidth,
            LogicalHeight,
        )

        assertEquals(1, tester.vsync.activeTickerCount)
        assertEquals(2, treeCount(tester.dumpElementTree(), "OpacityWidget"))
        assertFalse(checkNotNull(updatedState).isDisposed)
        tester.pumpFrame(0)
        tester.pumpFrame(SwitchDurationMillis)
        assertTrue(checkNotNull(updatedState).isDisposed)
        assertEquals(1, treeCount(tester.dumpElementTree(), "OpacityWidget"))
        assertEquals(0, tester.vsync.activeTickerCount)

        tester.dispose()
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)
    }

    /** Verifies rapid replacements retain every still-visible outgoing subtree until completion. */
    @Test
    fun rapidInterruptionsContinueFromCurrentOpacityAndDisposeAllSupersededChildren() {
        // Tester supplies deterministic quarter-progress interruption points.
        val tester = PixelTester()
        // State registry proves no visually present child is prematurely unmounted.
        val states = linkedMapOf<String, SwitchProbeState>()
        tester.pumpWidget(switcher(tester, probeForRegistry("A", states)), LogicalWidth, LogicalHeight)
        tester.pumpWidget(switcher(tester, probeForRegistry("B", states)), LogicalWidth, LogicalHeight)
        tester.pumpFrame(0)
        tester.pumpFrame(100)

        // New zero-opacity C must not change the already presented interrupted frame.
        val pixelsBeforeC = checkNotNull(tester.renderResult).buffer.pixels.copyOf()
        tester.pumpWidget(switcher(tester, probeForRegistry("C", states)), LogicalWidth, LogicalHeight)
        // Rendered pixels after C mount must exactly match the captured pre-interruption frame.
        val pixelsAfterC = checkNotNull(tester.renderResult).buffer.pixels
        assertTrue(pixelsBeforeC.contentEquals(pixelsAfterC))
        assertEquals(3, treeCount(tester.dumpElementTree(), "OpacityWidget"))
        assertTrue(listOf("A", "B", "C").all { label -> states[label]?.isDisposed == false })
        tester.pumpFrame(0)
        tester.pumpFrame(100)

        // D likewise starts invisibly while all three captured opacity layers remain unchanged.
        val pixelsBeforeD = checkNotNull(tester.renderResult).buffer.pixels.copyOf()
        tester.pumpWidget(switcher(tester, probeForRegistry("D", states)), LogicalWidth, LogicalHeight)
        // Rendered pixels after D mount must exactly match the second interrupted frame.
        val pixelsAfterD = checkNotNull(tester.renderResult).buffer.pixels
        assertTrue(pixelsBeforeD.contentEquals(pixelsAfterD))
        assertEquals(4, treeCount(tester.dumpElementTree(), "OpacityWidget"))
        assertTrue(listOf("A", "B", "C", "D").all { label -> states[label]?.isDisposed == false })
        assertEquals(1, tester.vsync.activeTickerCount)
        tester.pumpFrame(0)
        tester.pumpFrame(SwitchDurationMillis)

        assertTrue(listOf("A", "B", "C").all { label -> states[label]?.isDisposed == true })
        assertFalse(checkNotNull(states["D"]).isDisposed)
        assertEquals(1, treeCount(tester.dumpElementTree(), "OpacityWidget"))
        assertEquals(0, tester.vsync.activeTickerCount)

        tester.dispose()
        assertTrue(checkNotNull(states["D"]).isDisposed)
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)
    }

    /** Verifies reversing to an outgoing identity promotes its existing retained State. */
    @Test
    fun reverseToOutgoingKeyPromotesExistingSubtreeWithoutDuplicateMount() {
        // Tester advances the first A-to-B transition to exactly one quarter.
        val tester = PixelTester()
        // Original A State must be promoted rather than replaced by another A State.
        var originalAState: SwitchProbeState? = null
        // Reversed A callback receives the State owned by the promoted outgoing entry.
        var reversedAState: SwitchProbeState? = null
        // B State remains outgoing until the reverse segment completes.
        var bState: SwitchProbeState? = null
        tester.pumpWidget(
            switcher(
                tester,
                probe("A", ReverseAKey, OutgoingColor) { state -> originalAState = state },
            ),
            LogicalWidth,
            LogicalHeight,
        )
        tester.pumpWidget(
            switcher(
                tester,
                probe("B", ReverseBKey, IncomingColor) { state -> bState = state },
            ),
            LogicalWidth,
            LogicalHeight,
        )
        tester.pumpFrame(0)
        tester.pumpFrame(100)
        tester.pumpWidget(
            switcher(
                tester,
                probe("A-reversed", ReverseAKey, OutgoingColor) { state -> reversedAState = state },
            ),
            LogicalWidth,
            LogicalHeight,
        )

        assertSame(originalAState, reversedAState)
        assertEquals(1, checkNotNull(reversedAState).initCount)
        assertTrue(checkNotNull(reversedAState).updateCount >= 1)
        assertFalse(checkNotNull(reversedAState).isDisposed)
        assertFalse(checkNotNull(bState).isDisposed)
        assertEquals(2, treeCount(tester.dumpElementTree(), "OpacityWidget"))
        assertTrue(tester.exists(find.byKey(ReverseAKey)))
        assertFalse(tester.exists(find.byKey(ReverseAKey).nth(1)))

        tester.pumpFrame(0)
        tester.pumpFrame(SwitchDurationMillis)
        assertFalse(checkNotNull(reversedAState).isDisposed)
        assertTrue(checkNotNull(bState).isDisposed)
        assertEquals(1, treeCount(tester.dumpElementTree(), "OpacityWidget"))

        tester.dispose()
        assertTrue(checkNotNull(reversedAState).isDisposed)
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)
    }

    /** Verifies disposing mid-transition releases both subtrees and the controller ticker. */
    @Test
    fun disposeDuringCrossFadeClearsOutgoingIncomingTickerAndPendingFrame() {
        // Tester is disposed while its controller still owns one scheduled frame.
        val tester = PixelTester()
        // Outgoing probe records eager subtree disposal.
        var outgoingState: SwitchProbeState? = null
        // Incoming probe records eager subtree disposal even at zero opacity.
        var incomingState: SwitchProbeState? = null
        tester.pumpWidget(
            switcher(
                tester,
                probe("A", "dispose-a", OutgoingColor) { state -> outgoingState = state },
            ),
            LogicalWidth,
            LogicalHeight,
        )
        tester.pumpWidget(
            switcher(
                tester,
                probe("B", "dispose-b", IncomingColor) { state -> incomingState = state },
            ),
            LogicalWidth,
            LogicalHeight,
        )

        assertEquals(2, treeCount(tester.dumpElementTree(), "OpacityWidget"))
        assertEquals(1, tester.vsync.activeTickerCount)
        assertEquals(1, tester.vsync.liveTickerCount)
        assertEquals(1, tester.scheduler.pendingCount)
        tester.dispose()

        assertTrue(checkNotNull(outgoingState).isDisposed)
        assertTrue(checkNotNull(incomingState).isDisposed)
        assertEquals(0, tester.vsync.activeTickerCount)
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)
    }

    /** Asserts exact overlap, outgoing-only, incoming-only pixels and retained tree entry counts. */
    private fun assertCrossFadeFrame(
        tester: PixelTester,
        label: String,
        overlap: PixelColor,
        outgoingOnly: PixelColor,
        incomingOnly: PixelColor,
        entryCount: Int,
    ) {
        assertEquals("$label overlap pixel", overlap, tester.pixelAt(0, 0))
        assertEquals("$label outgoing layout pixel", outgoingOnly, tester.pixelAt(3, 0))
        assertEquals("$label incoming layout pixel", incomingOnly, tester.pixelAt(0, 1))
        assertEquals(
            "$label retained opacity entries",
            entryCount,
            treeCount(tester.dumpElementTree(), "OpacityWidget"),
        )
        assertEquals(
            "$label attached opacity render nodes",
            entryCount,
            treeCount(tester.dumpRenderTree(), "RenderOpacity"),
        )
    }

    /** Creates the stable switcher root used by declarative update tests. */
    private fun switcher(tester: PixelTester, child: Widget): Widget {
        return AnimatedSwitcher(
            duration = SwitchDurationMillis.milliseconds,
            vsync = tester.vsync,
            curve = Curves.Linear,
            key = SwitcherKey,
            child = child,
        )
    }

    /** Creates a colored StatefulWidget with observable retained State identity. */
    private fun probe(
        label: String,
        key: Any,
        color: PixelColor,
        width: Int = 2,
        height: Int = 2,
        onReady: (SwitchProbeState) -> Unit,
    ): SwitchProbe {
        return SwitchProbe(
            label = label,
            color = color,
            width = width,
            height = height,
            onReady = onReady,
            key = key,
        )
    }

    /** Creates a registry-backed probe for rapid interruption assertions. */
    private fun probeForRegistry(
        label: String,
        states: MutableMap<String, SwitchProbeState>,
    ): SwitchProbe {
        // Alternating colors keep every rapid target independently renderable.
        val color = if (label == "A" || label == "C") OutgoingColor else IncomingColor
        return probe(
            label = label,
            key = "rapid-$label",
            color = color,
            onReady = { state -> states[label] = state },
        )
    }

    /** Counts diagnostic tree lines containing [nodeName]. */
    private fun treeCount(tree: String, nodeName: String): Int {
        return tree.lineSequence().count { line -> line.contains(nodeName) }
    }

    /** Stable colors, keys, geometry, and expected alpha blends for all switcher tests. */
    private companion object {
        /** Duration whose quarter frames map to exact 100 ms increments. */
        const val SwitchDurationMillis: Long = 400L

        /** Logical canvas width large enough to expose outgoing-only layout pixels. */
        const val LogicalWidth: Int = 6

        /** Logical canvas height large enough to expose incoming-only layout pixels. */
        const val LogicalHeight: Int = 3

        /** Stable parent key retaining one AnimatedSwitcher State across root updates. */
        const val SwitcherKey: String = "animated-switcher-test"

        /** Initial outgoing child key. */
        const val OutgoingChildKey: String = "outgoing-child"

        /** Replacement incoming child key. */
        const val IncomingChildKey: String = "incoming-child"

        /** Compatible-update key shared by same-type and different-type test children. */
        const val SameChildKey: String = "same-child"

        /** A key used to verify reverse promotion of an outgoing entry. */
        const val ReverseAKey: String = "reverse-a"

        /** B key used as the outgoing entry after reverse promotion. */
        const val ReverseBKey: String = "reverse-b"

        /** Opaque red outgoing pixels. */
        val OutgoingColor: PixelColor = PixelColor.fromRgb(255, 0, 0)

        /** Opaque blue incoming pixels. */
        val IncomingColor: PixelColor = PixelColor.fromRgb(0, 0, 255)

        /** Red after the switcher's quantized half-opacity wrapper. */
        val HalfOutgoingColor: PixelColor = PixelColor.fromArgb(128, 255, 0, 0)

        /** Blue after the switcher's quantized half-opacity wrapper. */
        val HalfIncomingColor: PixelColor = PixelColor.fromArgb(128, 0, 0, 255)

        /** Incoming half-blue composited over outgoing half-red in Stack paint order. */
        val HalfIncomingOverHalfOutgoing: PixelColor = PixelColor(
            PixelBuffer.blendSrcOver(
                src = HalfIncomingColor.argb,
                dst = HalfOutgoingColor.argb,
            ),
        )
    }
}

/** Stateful colored box used to observe retained identity, updates, layout, and disposal. */
private class SwitchProbe(
    /** Human-readable configuration label. */
    val label: String,
    /** Opaque fill rendered by this child subtree. */
    val color: PixelColor,
    /** Independently observable child width. */
    val width: Int,
    /** Independently observable child height. */
    val height: Int,
    /** Callback exposing the exact retained State after mount and update. */
    val onReady: (SwitchProbeState) -> Unit,
    /** Consumer child identity participating in switch decisions. */
    override val key: Any,
) : StatefulWidget(key = key) {
    /** Creates the observable colored-box State. */
    override fun createState(): State<out StatefulWidget> = SwitchProbeState()
}

/** Retained probe State with exact mount, update, and disposal counters. */
private class SwitchProbeState : State<SwitchProbe>() {
    /** Number of times this concrete State completed initialization. */
    var initCount: Int = 0
        private set

    /** Number of compatible widget configurations applied to this State. */
    var updateCount: Int = 0
        private set

    /** Whether terminal subtree disposal has occurred. */
    var isDisposed: Boolean = false
        private set

    /** Records the first mount and exposes this State identity. */
    override fun initState() {
        initCount += 1
        widget.onReady(this)
    }

    /** Records a compatible update and exposes the retained State through the new callback. */
    override fun didUpdateWidget(oldWidget: SwitchProbe) {
        updateCount += 1
        widget.onReady(this)
    }

    /** Builds the exact colored extent used by pixel and layout assertions. */
    override fun build(context: BuildContext): Widget {
        return Container(
            width = widget.width,
            height = widget.height,
            fillColor = widget.color,
        )
    }

    /** Records terminal disposal exactly once. */
    override fun dispose() {
        isDisposed = true
    }
}
