package com.purride.pixelui.widgets

import com.purride.pixelui.BottomSheet
import com.purride.pixelui.Dialog
import com.purride.pixelui.Focus
import com.purride.pixelui.Menu
import com.purride.pixelui.PixelBackDispatcher
import com.purride.pixelui.PixelBackHost
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelMenuItem
import com.purride.pixelui.PixelOverlayController
import com.purride.pixelui.PixelOverlayDismissAction
import com.purride.pixelui.PixelOverlayDismissPolicy
import com.purride.pixelui.PixelOverlayDismissReason
import com.purride.pixelui.PixelOverlayHost
import com.purride.pixelui.PixelOverlayLayer
import com.purride.pixelui.PixelOverlayLifecycle
import com.purride.pixelui.PixelOverlayOutcome
import com.purride.pixelui.PixelPopupRoute
import com.purride.pixelui.PixelPredictiveBackEvent
import com.purride.pixelui.PixelPredictiveBackSwipeEdge
import com.purride.pixelui.Popover
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM behavior coverage for platform Back ownership of standard modal components. */
class StandaloneModalBackTest {
    /** Dialog and BottomSheet invoke their controlled callback exactly once per Back commit. */
    @Test
    fun standaloneSafeSurfacesHandleDiscreteAndPredictiveBack() {
        /** Public safe-surface factories exercised through an identical Back-host contract. */
        val surfaceFactories: List<((() -> Unit)) -> Widget> = listOf(
            { dismiss -> Dialog(content = Text("DIALOG"), onDismissRequest = dismiss) },
            { dismiss -> BottomSheet(content = Text("SHEET"), onDismissRequest = dismiss) },
        )
        surfaceFactories.forEachIndexed { index, surfaceFactory ->
            /** Runtime-local dispatcher whose stack represents PixelHostView platform Back. */
            val dispatcher = PixelBackDispatcher()
            /** Off-screen runtime mounting the standalone component below PixelBackHost. */
            val tester = PixelTester()
            /** Controlled callback count proving a single commit never double-dispatches. */
            var dismissCount = 0
            try {
                tester.pumpWidget(
                    widget = PixelBackHost(
                        dispatcher = dispatcher,
                        child = surfaceFactory { dismissCount += 1 },
                    ),
                    logicalWidth = 64,
                    logicalHeight = 32,
                )

                if (index == 0) {
                    /** Predictive start locks the discrete modal handler until commit. */
                    assertTrue(dispatcher.startPredictiveBack(predictiveStartEvent()))
                    assertEquals(0, dismissCount)
                    assertTrue(dispatcher.commitPredictiveBack())
                } else {
                    assertTrue(dispatcher.handleBack())
                }
                assertEquals(1, dismissCount)
            } finally {
                tester.dispose()
            }
            assertFalse(dispatcher.hasRegisteredHandlers)
        }
    }

    /** A modal without a callback consumes system Back just like its keyboard focus trap. */
    @Test
    fun nondismissibleStandaloneModalsConsumeSystemBack() {
        /** Standard modal variants whose keyboard contract consumes an unhandled dismissal key. */
        val modalFactories: List<() -> Widget> = listOf(
            { Dialog(content = Text("DIALOG"), onDismissRequest = null) },
            { BottomSheet(content = Text("SHEET"), onDismissRequest = null) },
            {
                Menu(
                    items = listOf(PixelMenuItem(label = "MENU ITEM", onSelected = {})),
                    onDismissRequest = null,
                )
            },
            {
                Popover(
                    anchor = Text("ANCHOR"),
                    content = Text("POPOVER"),
                    expanded = true,
                    dismissible = false,
                    onDismiss = null,
                )
            },
        )
        modalFactories.forEach { modalFactory ->
            /** Independent dispatcher prevents registration order leaking between variants. */
            val dispatcher = PixelBackDispatcher()
            /** Independent runtime owns one modal activation and registration lifecycle. */
            val tester = PixelTester()
            try {
                tester.pumpWidget(
                    widget = PixelBackHost(dispatcher = dispatcher, child = modalFactory()),
                    logicalWidth = 64,
                    logicalHeight = 32,
                )

                assertTrue(dispatcher.handleBack())
            } finally {
                tester.dispose()
            }
            assertFalse(dispatcher.hasRegisteredHandlers)
        }
    }

    /** Popover owns one handler and coalesces its Menu instead of dispatching to both callbacks. */
    @Test
    fun popoverAndNestedMenuShareOneStandaloneBackOwner() {
        /** Dispatcher receiving the same discrete event a PixelHostView forwards. */
        val dispatcher = PixelBackDispatcher()
        /** Runtime mounting a Popover whose content is itself a modal Menu component. */
        val tester = PixelTester()
        /** Popover-level dismiss count expected to receive the single Back commit. */
        var popoverDismissCount = 0
        /** Nested Menu dismiss count that must remain untouched after modal coalescing. */
        var menuDismissCount = 0
        try {
            tester.pumpWidget(
                widget = PixelBackHost(
                    dispatcher = dispatcher,
                    child = Popover(
                        anchor = Text("ANCHOR"),
                        content = Menu(
                            items = listOf(
                                PixelMenuItem(label = "ACTION", onSelected = {}),
                            ),
                            onDismissRequest = { menuDismissCount += 1 },
                        ),
                        expanded = true,
                        dismissible = false,
                        onDismiss = { popoverDismissCount += 1 },
                    ),
                ),
                logicalWidth = 64,
                logicalHeight = 32,
            )

            assertTrue(dispatcher.handleBack())
            assertEquals(1, popoverDismissCount)
            assertEquals(0, menuDismissCount)
        } finally {
            tester.dispose()
        }
    }

    /** Unified routes keep canonical order and suppress embedded standard-component handlers. */
    @Test
    fun unifiedRoutesRemainTheOnlyBackOwnerForEmbeddedStandardSurfaces() {
        /** Dispatcher shared by the route host and every descendant standard component. */
        val dispatcher = PixelBackDispatcher()
        /** Controller whose layer order must remain authoritative for both Back commits. */
        val controller = PixelOverlayController()
        /** Runtime mounting one lower Dialog route and one upper Popover/Menu route. */
        val tester = PixelTester()
        /** Dialog callback that would expose an accidental nested handler registration. */
        var dialogDismissCount = 0
        /** Popover callback that must not bypass the upper route dismissal policy. */
        var popoverDismissCount = 0
        /** Menu callback that must coalesce through both Popover and route ownership. */
        var menuDismissCount = 0
        try {
            tester.pumpWidget(
                widget = PixelBackHost(
                    dispatcher = dispatcher,
                    child = PixelOverlayHost(controller = controller, child = Text("HOME")),
                ),
                logicalWidth = 64,
                logicalHeight = 32,
            )
            /** Lower canonical route whose Dialog surface must not register a second handler. */
            val dialogEntry = controller.show(
                PixelPopupRoute<Unit>(
                    content = Dialog(
                        content = Text("ROUTE DIALOG"),
                        onDismissRequest = { dialogDismissCount += 1 },
                    ),
                    layer = PixelOverlayLayer.Modal,
                    modal = true,
                ),
            )
            /** Higher route exercising Popover and Menu coalescing under the route owner. */
            val popoverEntry = controller.show(
                PixelPopupRoute<Unit>(
                    content = Popover(
                        anchor = Text("ROUTE ANCHOR"),
                        content = Menu(
                            items = listOf(
                                PixelMenuItem(label = "ROUTE ACTION", onSelected = {}),
                            ),
                            onDismissRequest = { menuDismissCount += 1 },
                        ),
                        expanded = true,
                        onDismiss = { popoverDismissCount += 1 },
                    ),
                    layer = PixelOverlayLayer.System,
                    modal = true,
                ),
            )
            tester.pumpFrame(0)

            assertTrue(dispatcher.handleBack())
            tester.pumpFrame(0)
            assertEquals(
                PixelOverlayOutcome.Dismissed(PixelOverlayDismissReason.Back),
                popoverEntry.outcome,
            )
            assertEquals(PixelOverlayLifecycle.Active, dialogEntry.lifecycle)
            assertEquals(0, popoverDismissCount)
            assertEquals(0, menuDismissCount)

            assertTrue(dispatcher.handleBack())
            tester.pumpFrame(0)
            assertEquals(
                PixelOverlayOutcome.Dismissed(PixelOverlayDismissReason.Back),
                dialogEntry.outcome,
            )
            assertEquals(0, dialogDismissCount)
            assertEquals(0, controller.size)
            assertFalse(dispatcher.hasRegisteredHandlers)
        } finally {
            tester.dispose()
        }
    }

    /** Pure non-modal routes receive normalized Escape/Back before background key handlers. */
    @Test
    fun nonModalRoutesUseCanonicalControllerPolicyForNormalizedDismissKeys() {
        /** Controller whose route policy is shared by system and normalized keyboard Back paths. */
        val controller = PixelOverlayController()
        /** Runtime proving route keyboard handling does not require a focusable overlay child. */
        val tester = PixelTester()
        /** Background key count that must remain zero while a route consumes dismissal input. */
        var backgroundKeyCount = 0
        try {
            tester.pumpWidget(
                widget = PixelOverlayHost(
                    controller = controller,
                    child = Focus(
                        autofocus = true,
                        onKeyEvent = { event ->
                            if (event.key == PixelKey.ESCAPE || event.key == PixelKey.BACK) {
                                backgroundKeyCount += 1
                                true
                            } else {
                                false
                            }
                        },
                        child = Text("BACKGROUND"),
                    ),
                ),
                logicalWidth = 64,
                logicalHeight = 32,
            )
            /** Lower dismissible route selected after the higher passive route is skipped. */
            val dismissibleEntry = controller.show(
                PixelPopupRoute<Unit>(
                    content = Text("DISMISSIBLE NONMODAL"),
                    layer = PixelOverlayLayer.Popup,
                    modal = false,
                ),
            )
            /** Higher passive route proving normalized input uses canonical policy scanning. */
            val passiveEntry = controller.show(
                PixelPopupRoute<Unit>(
                    content = Text("PASSIVE NONMODAL"),
                    layer = PixelOverlayLayer.System,
                    dismissPolicy = PixelOverlayDismissPolicy.Passive,
                    modal = false,
                ),
            )
            tester.pumpFrame(0)

            assertTrue(tester.pressKey(PixelKey.ESCAPE))
            assertEquals(
                PixelOverlayOutcome.Dismissed(PixelOverlayDismissReason.DismissRequest),
                dismissibleEntry.outcome,
            )
            assertEquals(PixelOverlayLifecycle.Active, passiveEntry.lifecycle)
            assertEquals(0, backgroundKeyCount)

            /** Non-modal consuming route that traps normalized Back without closing itself. */
            val consumingEntry = controller.show(
                PixelPopupRoute<Unit>(
                    content = Text("CONSUMING NONMODAL"),
                    layer = PixelOverlayLayer.Modal,
                    dismissPolicy = PixelOverlayDismissPolicy(
                        back = PixelOverlayDismissAction.Consume,
                        barrierTap = PixelOverlayDismissAction.Ignore,
                    ),
                    modal = false,
                ),
            )
            tester.pumpFrame(0)

            assertTrue(tester.pressKey(PixelKey.BACK))
            assertEquals(PixelOverlayLifecycle.Active, consumingEntry.lifecycle)
            assertEquals(0, backgroundKeyCount)
        } finally {
            tester.dispose()
        }
    }

    /** Creates the normalized edge-neutral start event used by predictive Back tests. */
    private fun predictiveStartEvent(): PixelPredictiveBackEvent {
        return PixelPredictiveBackEvent(
            progress = 0f,
            touchX = 0f,
            touchY = 0f,
            swipeEdge = PixelPredictiveBackSwipeEdge.None,
        )
    }
}
