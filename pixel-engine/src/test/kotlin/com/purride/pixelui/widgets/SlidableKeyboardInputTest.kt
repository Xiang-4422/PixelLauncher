package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.Slidable
import com.purride.pixelui.SlidableAction
import com.purride.pixelui.SlidableActionPane
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks the keyboard, focus, and semantic contract of Slidable rows and action panes. */
class SlidableKeyboardInputTest {
    /** Opaque row color used by every deterministic test fixture. */
    private val rowColor: PixelColor = PixelColor.fromRgb(32, 48, 64)

    /** Opaque start action color that does not depend on inherited theme state. */
    private val startColor: PixelColor = PixelColor.fromRgb(40, 120, 80)

    /** Opaque end action color that does not depend on inherited theme state. */
    private val endColor: PixelColor = PixelColor.fromRgb(160, 48, 48)

    /** Verifies row activation and the closed-state Left/Right pane mapping. */
    @Test
    fun rowKeysActivateAndOpenTheExpectedPane() {
        /** Number of Enter/Space activations delivered to the Slidable row. */
        var rowActivations = 0
        /** Runtime-local focus owner and render pipeline used by this keyboard contract. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Slidable(
                    child = Container(width = 40, height = 10, fillColor = rowColor),
                    startActionPane = SlidableActionPane(
                        children = listOf(action(label = "START", color = startColor)),
                        extentRatio = 0.5f,
                    ),
                    endActionPane = SlidableActionPane(
                        children = listOf(action(label = "END", color = endColor)),
                        extentRatio = 0.5f,
                    ),
                    onTap = { rowActivations += 1 },
                    key = "directional-row",
                ),
                logicalWidth = 40,
                logicalHeight = 10,
            )

            assertTrue(tester.semanticsNodesByLabel("START").isEmpty())
            assertTrue(tester.semanticsNodesByLabel("END").isEmpty())
            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertTrue(tester.pressKey(PixelKey.SPACE))
            assertEquals(2, rowActivations)

            assertTrue(tester.pressKey(PixelKey.ARROW_LEFT))
            assertEquals(1, tester.semanticsNodesByLabel("END").size)
            assertTrue(tester.semanticsNodesByLabel("START").isEmpty())

            assertTrue(tester.pressKey(PixelKey.ARROW_RIGHT))
            assertTrue(tester.semanticsNodesByLabel("END").isEmpty())
            assertTrue(tester.pressKey(PixelKey.ARROW_RIGHT))
            assertEquals(1, tester.semanticsNodesByLabel("START").size)
            assertTrue(tester.semanticsNodesByLabel("END").isEmpty())

            assertTrue(tester.pressKey(PixelKey.ARROW_LEFT))
            assertTrue(tester.semanticsNodesByLabel("START").isEmpty())
        } finally {
            tester.dispose()
        }
    }

    /** Verifies hidden-pane exclusion, action activation, and row focus restoration after close. */
    @Test
    fun paneActionIsFocusableOnlyWhileOpenAndCloseRestoresTheRow() {
        /** Number of keyboard, semantics, and pointer-equivalent action activations. */
        var actionActivations = 0
        /** Number of activations proving focus returned to the Slidable row after closing. */
        var rowActivations = 0
        /** Runtime-local focus owner and render pipeline used by this pane lifecycle contract. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Column(
                    children = listOf(
                        Slidable(
                            child = Container(width = 40, height = 10, fillColor = rowColor),
                            endActionPane = SlidableActionPane(
                                children = listOf(
                                    SlidableAction(
                                        label = "DELETE",
                                        backgroundColor = endColor,
                                        foregroundColor = PixelColor.White,
                                        onPressed = { actionActivations += 1 },
                                        key = "delete-action",
                                    ),
                                ),
                                // Keep the exposed action away from the outer gesture target's center.
                                extentRatio = 0.25f,
                            ),
                            onTap = { rowActivations += 1 },
                            key = "focus-row",
                        ),
                        OutlinedButton(text = "AFTER", onPressed = {}, key = "after"),
                    ),
                ),
                logicalWidth = 40,
                logicalHeight = 24,
            )

            assertTrue(tester.semanticsNodesByLabel("DELETE").isEmpty())
            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("AFTER").single().focused)
            assertTrue(tester.pressKey(PixelKey.SHIFT_TAB))
            assertFalse(tester.semanticsNodesByLabel("AFTER").single().focused)

            assertTrue(tester.pressKey(PixelKey.ARROW_LEFT))
            /** Visible action node used to verify both its role and typed click action. */
            val actionNode = tester.semanticsNodesByLabel("DELETE").single()
            assertEquals(PixelSemanticRole.BUTTON, actionNode.role)
            assertTrue(PixelSemanticsAction.CLICK in actionNode.actions)
            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("DELETE").single().focused)
            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertTrue(tester.pressKey(PixelKey.SPACE))
            assertTrue(
                tester.performSemanticsAction(
                    actionNode.id,
                    PixelSemanticsAction.CLICK,
                ),
            )
            assertEquals(3, actionActivations)

            // The focused action declines Right, allowing the parent Slidable row to close its end pane.
            assertTrue(tester.pressKey(PixelKey.ARROW_RIGHT))
            assertTrue(tester.semanticsNodesByLabel("DELETE").isEmpty())
            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertEquals(1, rowActivations)

            assertTrue(tester.pressKey(PixelKey.ARROW_LEFT))
            assertTrue(tester.pressKey(PixelKey.TAB))
            /** Pointer close that must hide pane semantics before the gesture is released. */
            val closingGesture = tester.startGesture(find.byKey("focus-row:gesture")).moveBy(10, 0)
            assertTrue(tester.semanticsNodesByLabel("DELETE").isEmpty())
            closingGesture.up()
            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertEquals(2, rowActivations)
            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("AFTER").single().focused)
        } finally {
            tester.dispose()
        }
    }

    /**
     * Creates one no-op SlidableAction used only to expose pane direction and semantics.
     *
     * @param label Stable semantic label identifying the pane side under test.
     * @param color Opaque background color that keeps the fixture independent from theme state.
     */
    private fun action(label: String, color: PixelColor): Widget {
        return SlidableAction(
            label = label,
            backgroundColor = color,
            foregroundColor = PixelColor.White,
            onPressed = {},
            key = "$label-action",
        )
    }
}
