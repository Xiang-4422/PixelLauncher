package com.purride.pixelui.widgets

import com.purride.pixelui.BottomSheet
import com.purride.pixelui.Dialog
import com.purride.pixelui.Dropdown
import com.purride.pixelui.Menu
import com.purride.pixelui.PixelBackDispatcher
import com.purride.pixelui.PixelBackHost
import com.purride.pixelui.PixelMenuItem
import com.purride.pixelui.PixelOverlayController
import com.purride.pixelui.PixelOverlayDismissReason
import com.purride.pixelui.PixelOverlayHost
import com.purride.pixelui.PixelOverlayLayer
import com.purride.pixelui.PixelOverlayLifecycle
import com.purride.pixelui.PixelOverlayOutcome
import com.purride.pixelui.PixelPopupRoute
import com.purride.pixelui.Popover
import com.purride.pixelui.Semantics
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Tooltip
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Proves every public production overlay component coalesces under the unified typed route. */
class ProductionOverlayPublicContractTest {
    /**
     * Embeds all six component families in an identical typed route and verifies that route-level
     * Back remains the sole owner, inner callbacks never bypass it, and teardown is acknowledged.
     */
    @Test
    fun allPublicOverlayFamiliesConsumeOneTypedRouteContract() {
        /** Factories covering every component named by the M5-2 production Overlay contract. */
        val cases = productionOverlayCases()
        cases.forEach { case ->
            /** Canonical Back dispatcher shared by the route host and embedded component. */
            val dispatcher = PixelBackDispatcher()
            /** Typed route controller whose lifecycle and outcome are authoritative. */
            val controller = PixelOverlayController()
            /** Counts component-local dismiss callbacks that a route owner must coalesce. */
            var componentDismissals = 0
            /** Isolated runtime preventing modal, focus, or ticker ownership from crossing cases. */
            val tester = PixelTester()
            try {
                tester.pumpWidget(
                    widget = PixelBackHost(
                        dispatcher = dispatcher,
                        child = PixelOverlayHost(
                            controller = controller,
                            child = SizedBox(width = 1, height = 1),
                        ),
                    ),
                    logicalWidth = 72,
                    logicalHeight = 40,
                )
                /** Public component instance nested below the route's modal ownership marker. */
                val component = case.build { componentDismissals += 1 }
                /** Typed entry used to inspect canonical lifecycle and terminal Back outcome. */
                val entry = controller.show(
                    PixelPopupRoute<Unit>(
                        content = component,
                        layer = PixelOverlayLayer.Modal,
                        modal = true,
                    ),
                )
                tester.pumpFrame(0)

                assertTrue(
                    "${case.name} content must be mounted through the public entry",
                    tester.semanticsNodesByLabel(case.expectedSemanticLabel).isNotEmpty(),
                )
                assertTrue("${case.name} typed route must own Back", dispatcher.handleBack())
                tester.pumpFrame(0)

                assertEquals(
                    "${case.name} must report the typed route's Back outcome",
                    PixelOverlayOutcome.Dismissed(PixelOverlayDismissReason.Back),
                    entry.outcome,
                )
                assertEquals(
                    "${case.name} inner callback must not bypass its route owner",
                    0,
                    componentDismissals,
                )
                assertEquals(0, controller.size)
                assertEquals(PixelOverlayLifecycle.Disposed, entry.lifecycle)
                assertFalse(dispatcher.hasRegisteredHandlers)
                assertTrue(tester.semanticsNodesByLabel(case.expectedSemanticLabel).isEmpty())
                assertEquals(0, tester.vsync.liveTickerCount)
            } finally {
                tester.dispose()
            }
            assertFalse("${case.name} must unregister Back after disposal", dispatcher.hasRegisteredHandlers)
        }
    }

    /** Creates the exhaustive public component matrix with stable observable semantic labels. */
    private fun productionOverlayCases(): List<ProductionOverlayCase> {
        return listOf(
            ProductionOverlayCase(
                name = "Dialog",
                expectedSemanticLabel = "Dialog",
                build = { dismiss ->
                    Dialog(
                        content = Semantics(
                            label = "DIALOG CONTENT",
                            child = SizedBox(width = 8, height = 4),
                        ),
                        onDismissRequest = dismiss,
                    )
                },
            ),
            ProductionOverlayCase(
                name = "BottomSheet",
                expectedSemanticLabel = "Bottom sheet",
                build = { dismiss ->
                    BottomSheet(
                        content = Semantics(
                            label = "SHEET CONTENT",
                            child = SizedBox(width = 8, height = 4),
                        ),
                        onDismissRequest = dismiss,
                    )
                },
            ),
            ProductionOverlayCase(
                name = "Menu",
                expectedSemanticLabel = "MENU ITEM",
                build = { dismiss ->
                    Menu(
                        items = listOf(PixelMenuItem(label = "MENU ITEM", onSelected = {})),
                        onDismissRequest = dismiss,
                    )
                },
            ),
            ProductionOverlayCase(
                name = "Popover",
                expectedSemanticLabel = "POPOVER CONTENT",
                build = { dismiss ->
                    Popover(
                        anchor = SizedBox(width = 4, height = 3),
                        content = Semantics(
                            label = "POPOVER CONTENT",
                            child = SizedBox(width = 8, height = 4),
                        ),
                        expanded = true,
                        onDismiss = dismiss,
                    )
                },
            ),
            ProductionOverlayCase(
                name = "Dropdown",
                expectedSemanticLabel = "DROPDOWN OPTION",
                build = { dismiss ->
                    Dropdown(
                        label = "CHOOSER",
                        selectedText = "CURRENT",
                        expanded = true,
                        onToggle = dismiss,
                        items = listOf(
                            PixelMenuItem(label = "DROPDOWN OPTION", onSelected = {}),
                        ),
                    )
                },
            ),
            ProductionOverlayCase(
                name = "Tooltip",
                expectedSemanticLabel = "TOOLTIP CONTENT",
                build = {
                    Tooltip(
                        message = "TOOLTIP CONTENT",
                        visible = true,
                        child = SizedBox(width = 4, height = 3),
                    )
                },
            ),
        )
    }
}

/** One public Overlay component factory and the semantic evidence expected after mounting it. */
private data class ProductionOverlayCase(
    /** Human-readable component family used in assertion failures. */
    val name: String,
    /** Stable semantic label proving this component's real presentation mounted. */
    val expectedSemanticLabel: String,
    /** Component builder receiving a callback that must be coalesced by the route owner. */
    val build: ((() -> Unit)) -> Widget,
)
