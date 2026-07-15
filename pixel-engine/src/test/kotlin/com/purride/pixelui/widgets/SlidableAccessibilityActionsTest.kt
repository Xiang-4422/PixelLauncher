package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Container
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.Slidable
import com.purride.pixelui.SlidableAction
import com.purride.pixelui.SlidableActionPane
import com.purride.pixelui.SlidableDirection
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks Slidable's standard accessibility expand, collapse, and dismiss action contract. */
class SlidableAccessibilityActionsTest {
    /** End-pane color used to keep the accessibility fixture independent from theme presets. */
    private val actionColor: PixelColor = PixelColor.fromRgb(160, 48, 48)

    /** Accessibility opens the preferred pane, closes it, and dismisses it exactly once. */
    @Test
    fun semanticsExpandCollapseAndDismissUseTheSamePaneStateMachine() {
        /** Directions delivered only after an explicit semantic dismiss reaches its endpoint. */
        val dismissals = mutableListOf<SlidableDirection>()
        /** Isolated retained runtime used to execute typed semantics actions. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Slidable(
                    child = Container(width = 40, height = 10, fillColor = PixelColor.Black),
                    states = PixelControlStateSet.Normal,
                    startActionPane = actionPane(label = "ARCHIVE", dismissible = false),
                    endActionPane = actionPane(label = "DELETE", dismissible = true),
                    onDismissed = dismissals::add,
                    semanticLabel = "MESSAGE ACTIONS",
                    key = "accessible-slidable",
                ),
                logicalWidth = 40,
                logicalHeight = 10,
            )

            /** Closed row exposes only the deterministic standard expand action. */
            val closed = tester.semanticsNodesByLabel("MESSAGE ACTIONS").single()
            assertEquals(false, closed.expanded)
            assertTrue(PixelSemanticsAction.EXPAND in closed.actions)
            assertFalse(PixelSemanticsAction.COLLAPSE in closed.actions)
            assertFalse(PixelSemanticsAction.DISMISS in closed.actions)

            assertTrue(tester.performSemanticsAction(closed.id, PixelSemanticsAction.EXPAND))
            tester.pumpFrame(0)

            /** End is the preferred accessibility pane when both directions are available. */
            val opened = tester.semanticsNodesByLabel("MESSAGE ACTIONS").single()
            assertEquals(true, opened.expanded)
            assertTrue(opened.selected)
            assertTrue(PixelSemanticsAction.COLLAPSE in opened.actions)
            assertTrue(PixelSemanticsAction.DISMISS in opened.actions)
            assertTrue(tester.semanticsNodesByLabel("DELETE").isNotEmpty())
            assertTrue(tester.semanticsNodesByLabel("ARCHIVE").isEmpty())

            assertTrue(tester.performSemanticsAction(opened.id, PixelSemanticsAction.COLLAPSE))
            tester.pumpFrame(0)
            /** Collapse removes pane descendants before any retained visual exit can finish. */
            val collapsed = tester.semanticsNodesByLabel("MESSAGE ACTIONS").single()
            assertEquals(false, collapsed.expanded)
            assertTrue(tester.semanticsNodesByLabel("DELETE").isEmpty())
            assertTrue(dismissals.isEmpty())

            assertTrue(tester.performSemanticsAction(collapsed.id, PixelSemanticsAction.EXPAND))
            tester.pumpFrame(0)
            /** Re-resolved node owns the live dismiss callback for the currently open pane. */
            val dismissible = tester.semanticsNodesByLabel("MESSAGE ACTIONS").single()
            assertTrue(tester.performSemanticsAction(dismissible.id, PixelSemanticsAction.DISMISS))
            tester.pumpFrame(0)
            assertEquals(listOf(SlidableDirection.END), dismissals)
            assertEquals(0, tester.vsync.liveTickerCount)
        } finally {
            tester.dispose()
        }
    }

    /** Loading preserves row semantics but exports no mutation actions. */
    @Test
    fun loadingSuppressesEveryAccessibilityMutation() {
        /** Callback count that must remain zero while Loading is authoritative. */
        var dismissals = 0
        /** Isolated runtime used to reject a direct virtual-node expand request. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Slidable(
                    child = Container(width = 40, height = 10, fillColor = PixelColor.Black),
                    states = PixelControlStateSet.of(PixelControlState.Loading),
                    endActionPane = actionPane(label = "DELETE", dismissible = true),
                    onDismissed = { dismissals += 1 },
                    semanticLabel = "LOADING ACTIONS",
                    key = "loading-slidable",
                ),
                logicalWidth = 40,
                logicalHeight = 10,
            )

            /** Loading node remains discoverable but cannot expose or execute pane mutations. */
            val loading = tester.semanticsNodesByLabel("LOADING ACTIONS").single()
            assertFalse(loading.enabled)
            assertFalse(PixelSemanticsAction.EXPAND in loading.actions)
            assertFalse(PixelSemanticsAction.COLLAPSE in loading.actions)
            assertFalse(PixelSemanticsAction.DISMISS in loading.actions)
            assertFalse(tester.performSemanticsAction(loading.id, PixelSemanticsAction.EXPAND))
            assertEquals(0, dismissals)
        } finally {
            tester.dispose()
        }
    }

    /** Creates one deterministic pane whose action label also proves which direction opened. */
    private fun actionPane(label: String, dismissible: Boolean): SlidableActionPane {
        return SlidableActionPane(
            children = listOf(
                SlidableAction(
                    label = label,
                    backgroundColor = actionColor,
                    foregroundColor = PixelColor.White,
                    onPressed = {},
                    key = "$label-action",
                ),
            ),
            extentRatio = 0.5f,
            dismissible = dismissible,
        )
    }
}
