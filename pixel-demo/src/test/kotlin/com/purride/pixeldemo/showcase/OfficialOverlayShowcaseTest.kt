package com.purride.pixeldemo.showcase

import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionSettings
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** Behavior coverage for the interactive M4-3 notification and production-overlay showcases. */
class OfficialOverlayShowcaseTest {
    /** Verifies one interaction enqueues two finite-time items and Snackbar action advances FIFO. */
    @Test
    fun notificationControlsExposeFifoTimeoutAndAction() {
        /** Off-screen Host clock used to prove finite active-time expiry without wall-clock sleeps. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = motionRoot(
                    tester = tester,
                    child = ToastQueueOfficialBody(itemTimeout = 100.milliseconds),
                ),
                logicalWidth = 180,
                logicalHeight = 260,
            )

            clickSemantics(tester, "+2 TOAST")
            assertTrue(tester.exists(find.byText("TOAST 1.1")))
            assertFalse(tester.exists(find.byText("TOAST 1.2")))
            clickSemantics(tester, "NEXT TOAST")
            assertFalse(tester.exists(find.byText("TOAST 1.1")))
            assertTrue(tester.exists(find.byText("TOAST 1.2")))

            clickSemantics(tester, "+2 SNACK")
            assertTrue(tester.exists(find.byText("SNACK 1.1")))
            assertTrue(tester.exists(find.byText("ACK")))
            clickSemantics(tester, "ACK")
            assertFalse(tester.exists(find.byText("SNACK 1.1")))
            assertTrue(tester.exists(find.byText("SNACK 1.2")))
            assertTrue(tester.exists(find.byText("ACTION ACK: SNACK 1.1")))

            tester.pumpFrame(deltaMs = 0)
            tester.pumpFrame(deltaMs = 99)
            assertTrue(tester.exists(find.byText("SNACK 1.2")))
            tester.pumpFrame(deltaMs = 1)
            assertFalse(tester.exists(find.byText("SNACK 1.2")))
        } finally {
            tester.dispose()
        }
    }

    /** Verifies modal controls close explicitly and one shared state switches non-modal Tooltip away. */
    @Test
    fun anchoredControlsNeverExposeTwoExpandedPresentations() {
        /** Off-screen interaction harness for the real official component body. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = motionRoot(tester = tester, child = OverlayControlsOfficialBody()),
                logicalWidth = 180,
                logicalHeight = 320,
            )

            clickSemantics(tester, "OPEN POPOVER")
            settleMotion(tester)
            assertTrue(tester.semanticsNodesByLabel("REAL ANCHOR BOUNDS").isNotEmpty())
            assertTrue(tester.semanticsNodesByLabel("COPY").isEmpty())
            clickSemantics(tester, "CLOSE")
            assertTrue(tester.semanticsNodesByLabel("REAL ANCHOR BOUNDS").isEmpty())
            settleMotion(tester)

            clickSemantics(tester, "OPEN MENU")
            settleMotion(tester)
            assertTrue(tester.semanticsNodesByLabel("COPY").isNotEmpty())
            assertTrue(tester.semanticsNodesByLabel("REAL ANCHOR BOUNDS").isEmpty())
            clickSemantics(tester, "COPY")
            assertTrue(tester.semanticsNodesByLabel("COPY").isEmpty())
            assertTrue(tester.exists(find.byText("MENU: COPY")))

            clickSemantics(tester, "MODE")
            settleMotion(tester)
            assertTrue(tester.semanticsNodesByLabel("B").isNotEmpty())
            clickSemantics(tester, "B")
            assertTrue(tester.exists(find.byText("MODE: B v")))

            clickSemantics(tester, "SHOW TIP")
            settleMotion(tester)
            assertTrue(tester.semanticsNodesByLabel("RESIZES WITH THE SAFE VIEWPORT").isNotEmpty())
            clickSemantics(tester, "OPEN POPOVER")
            settleMotion(tester)
            assertTrue(tester.semanticsNodesByLabel("RESIZES WITH THE SAFE VIEWPORT").isEmpty())
            assertTrue(tester.semanticsNodesByLabel("REAL ANCHOR BOUNDS").isNotEmpty())
        } finally {
            tester.dispose()
        }
    }

    /** Verifies typed completion, explicit layers, and locked Back policy through the Demo itself. */
    @Test
    fun productionRouteShowsTypedAndReasonedOutcomes() {
        /** Off-screen Host clock driving retained dialog enter and exit presentations. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = motionRoot(tester = tester, child = ProductionOverlayOfficialBody()),
                logicalWidth = 180,
                logicalHeight = 180,
            )

            clickSemantics(tester, "SHOW ROUTE")
            settleMotion(tester)
            assertTrue(tester.exists(find.byText("TYPED ROUTE")))
            assertTrue(tester.exists(find.byText("LAYER=MODAL")))
            clickSemantics(tester, "RETURN YES")
            settleMotion(tester)
            assertTrue(tester.exists(find.byText("RESULT: COMPLETED(YES)")))

            clickSemantics(tester, "LAYER MODAL")
            clickSemantics(tester, "BACK DISMISS")
            clickSemantics(tester, "SHOW ROUTE")
            settleMotion(tester)
            assertTrue(tester.exists(find.byText("LAYER=SYSTEM")))
            assertTrue(tester.pressKey(PixelKey.BACK))
            assertTrue(tester.exists(find.byText("TYPED ROUTE")))
            clickSemantics(tester, "DISMISS")
            settleMotion(tester)
            assertTrue(tester.exists(find.byText("RESULT: DISMISSED(PROGRAMMATIC)")))
        } finally {
            tester.dispose()
        }
    }

    /** Wraps showcase content in the same Host-owned motion clock used by the real Demo shell. */
    private fun motionRoot(tester: PixelTester, child: Widget): Widget {
        return PixelMotionScope(
            vsync = tester.vsync,
            settings = PixelMotionSettings.Default,
            child = child,
        )
    }

    /** Invokes the unique enabled semantic click target with [label], avoiding paint-only exits. */
    private fun clickSemantics(tester: PixelTester, label: String) {
        /** Current enabled target, unique because each showcase exposes one active presentation. */
        val node = tester.semanticsNodesByLabel(label).single { candidate -> candidate.enabled }
        assertTrue(tester.performSemanticsAction(node.id, PixelSemanticsAction.CLICK))
    }

    /** Advances beyond the longest default dialog token so delayed outcomes become observable. */
    private fun settleMotion(tester: PixelTester) {
        tester.pumpFrame(deltaMs = 0)
        tester.pumpFrame(deltaMs = 1_000)
    }
}
