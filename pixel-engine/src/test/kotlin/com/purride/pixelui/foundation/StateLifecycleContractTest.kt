package com.purride.pixelui.foundation

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Container
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Test

/** Locks the public State mutation boundary after retained-tree detachment. */
class StateLifecycleContractTest {
    /** A late interaction or asynchronous callback cannot mutate or dirty an unmounted State. */
    @Test
    fun setStateAfterUnmountIsAnInertNoOp() {
        val sink = LateSetStateSink()
        val tester = PixelTester()
        tester.pumpWidget(LateSetStateProbe(sink), logicalWidth = 4, logicalHeight = 4)
        val lateCallback = checkNotNull(sink.callback)

        tester.pumpWidget(
            Container(width = 4, height = 4, fillColor = ReplacementColor),
            logicalWidth = 4,
            logicalHeight = 4,
        )
        lateCallback()
        tester.pumpFrame(0)

        assertEquals(0, sink.executedActions)
        assertEquals(ReplacementColor, tester.pixelAt(0, 0))
        tester.dispose()
    }

    private companion object {
        /** Stable replacement color proving no detached probe rebuild was scheduled. */
        val ReplacementColor: PixelColor = PixelColor.fromRgb(24, 160, 88)
    }
}

/** Mutable assertion sink retaining a callback after its owning State is detached. */
private class LateSetStateSink {
    /** Callback captured from the mounted probe and invoked after replacement. */
    var callback: (() -> Unit)? = null

    /** Number of mutation actions actually executed by [State.setState]. */
    var executedActions: Int = 0
}

/** Stateful probe that exposes one late setState callback to [LateSetStateSink]. */
private class LateSetStateProbe(
    /** Sink receiving the callback and mutation count. */
    val sink: LateSetStateSink,
) : StatefulWidget() {
    /** Creates the retained State that owns the callback. */
    override fun createState(): State<out StatefulWidget> = LateSetStateProbeState()
}

/** Probe State whose callback must become inert immediately after unmount. */
private class LateSetStateProbeState : State<LateSetStateProbe>() {
    /** Publishes the callback once while painting a deterministic source surface. */
    override fun build(context: BuildContext): Widget {
        widget.sink.callback = {
            setState { widget.sink.executedActions += 1 }
        }
        return Container(width = 4, height = 4, fillColor = PixelColor.White)
    }
}
