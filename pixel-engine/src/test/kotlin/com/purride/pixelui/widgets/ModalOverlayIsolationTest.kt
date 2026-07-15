package com.purride.pixelui.widgets

import com.purride.pixelui.Column
import com.purride.pixelui.Menu
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelMenuItem
import com.purride.pixelui.Popover
import com.purride.pixelui.Text
import com.purride.pixelui.Tooltip
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies modal overlay target isolation independently from focus-owner behavior. */
class ModalOverlayIsolationTest {
    /** A later Column sibling must not leak clicks or semantics around an expanded Popover. */
    @Test
    fun expandedPopoverFiltersLaterSiblingTargetsAcrossTheCompletedTree() {
        /** Controlled logical expansion used to verify immediate isolation release on close. */
        val expanded = ValueNotifier(true)
        /** Number of activations delivered to the isolated background button. */
        var backgroundActivations = 0
        /** Deterministic retained runtime used for target and semantic inspection. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = ValueListenableBuilder(expanded) { _, isExpanded ->
                    Column(
                        children = listOf(
                            Popover(
                                anchor = OutlinedButton(text = "ANCHOR", onPressed = { }),
                                content = OutlinedButton(text = "POPOVER ACTION", onPressed = { }),
                                expanded = isExpanded,
                                modal = true,
                                key = "popover",
                            ),
                            OutlinedButton(
                                text = "BACKGROUND ACTION",
                                onPressed = { backgroundActivations += 1 },
                                key = "background",
                            ),
                        ),
                    )
                },
                logicalWidth = 96,
                logicalHeight = 48,
            )

            /** Modal semantic snapshot must contain only descendants of the Popover presentation. */
            val openSemantics = tester.dumpSemanticsTree()
            assertTrue(openSemantics.contains("POPOVER ACTION"))
            assertFalse(openSemantics.contains("ANCHOR"))
            assertFalse(openSemantics.contains("BACKGROUND ACTION"))
            /** Popup action plus its surface absorber remain; the background target is excluded. */
            assertEquals(2, tester.renderResult?.clickTargets.orEmpty().size)
            assertThrows(IllegalStateException::class.java) {
                tester.tap(find.byText("BACKGROUND ACTION"))
            }
            assertEquals(0, backgroundActivations)

            expanded.value = false
            tester.pumpFrame(0)

            /** Logical close restores background targets before any retained visual exit completes. */
            val closedSemantics = tester.dumpSemanticsTree()
            assertTrue(closedSemantics.contains("ANCHOR"))
            assertTrue(closedSemantics.contains("BACKGROUND ACTION"))
            assertFalse(closedSemantics.contains("POPOVER ACTION"))
            /** Anchor and background targets return while retained exit content is visual-only. */
            assertEquals(2, tester.renderResult?.clickTargets.orEmpty().size)
        } finally {
            tester.dispose()
        }
    }

    /** A standalone Menu owns semantics and click targets until it leaves the tree. */
    @Test
    fun standaloneMenuFiltersFollowingBackgroundSibling() {
        /** Deterministic retained runtime used for target and semantic inspection. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Column(
                    children = listOf(
                        Menu(
                            items = listOf(PixelMenuItem(label = "COPY", onSelected = { })),
                            key = "menu",
                        ),
                        OutlinedButton(text = "BACKGROUND", onPressed = { }),
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 48,
            )

            /** Menu collection and item remain, while the later sibling is globally excluded. */
            val semantics = tester.dumpSemanticsTree()
            assertTrue(semantics.contains("Menu"))
            assertTrue(semantics.contains("COPY"))
            assertFalse(semantics.contains("BACKGROUND"))
            assertEquals(1, tester.renderResult?.clickTargets.orEmpty().size)
        } finally {
            tester.dispose()
        }
    }

    /** Tooltip remains non-modal and therefore never hides or disables surrounding content. */
    @Test
    fun visibleTooltipDoesNotIsolateBackground() {
        /** Deterministic retained runtime used for target and semantic inspection. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Column(
                    children = listOf(
                        Tooltip(
                            message = "HELP TEXT",
                            visible = true,
                            child = OutlinedButton(text = "ANCHOR", onPressed = { }),
                        ),
                        OutlinedButton(text = "BACKGROUND", onPressed = { }),
                        Text("FOOTER"),
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 48,
            )

            /** Non-modal tooltip, anchor, and following content are all exported together. */
            val semantics = tester.dumpSemanticsTree()
            assertTrue(semantics.contains("HELP TEXT"))
            assertTrue(semantics.contains("ANCHOR"))
            assertTrue(semantics.contains("BACKGROUND"))
            assertTrue(semantics.contains("FOOTER"))
            /** Anchor, background, and the tooltip surface absorber coexist without isolation. */
            assertEquals(3, tester.renderResult?.clickTargets.orEmpty().size)
        } finally {
            tester.dispose()
        }
    }

    /** A default Menu inside Popover reuses the outer modal token and keeps its barrier exported. */
    @Test
    fun nestedDefaultMenuCoalescesWithPopoverModalOwner() {
        /** Runtime-local tester used to inspect the completed modal target snapshot. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Popover(
                    anchor = OutlinedButton("OPEN", onPressed = { }),
                    content = Menu(
                        items = listOf(PixelMenuItem("COPY", onSelected = { })),
                        onDismissRequest = { },
                        key = "nested-menu",
                    ),
                    expanded = true,
                    dismissible = true,
                    onDismiss = { },
                    modal = true,
                    key = "nested-popover",
                ),
                logicalWidth = 96,
                logicalHeight = 48,
            )

            val semantics = tester.dumpSemanticsTree()
            assertTrue(semantics.contains("Dismiss"))
            assertTrue(semantics.contains("COPY"))
            /** Barrier, surface absorber, and Menu item all share the one modal owner. */
            assertEquals(3, tester.renderResult?.clickTargets.orEmpty().size)
        } finally {
            tester.dispose()
        }
    }
}
