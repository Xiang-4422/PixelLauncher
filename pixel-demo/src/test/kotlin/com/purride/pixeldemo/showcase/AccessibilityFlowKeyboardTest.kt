package com.purride.pixeldemo.showcase

import com.purride.pixelui.PixelKey
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM acceptance coverage for the Demo's complete keyboard and DPAD accessibility workflow. */
class AccessibilityFlowKeyboardTest {
    /**
     * Completes the real scene from text entry through collection, popup, dialog, and page change.
     */
    @Test
    fun tabEnterAndSpaceCompleteTheCoreWorkflow() {
        /** Off-screen Demo runtime that owns focus, overlays, and retained scene state. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = accessibilityFlowBodyForTest(key = "keyboard-flow"),
                logicalWidth = 160,
                logicalHeight = 180,
            )

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("Name").single().focused)
            tester.enterText(find.byKey("name-field"), "ADA")
            assertEquals("ADA", tester.semanticsNodesByLabel("Name").single().value)

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("Volume").single().focused)
            assertTrue(tester.pressKey(PixelKey.ARROW_RIGHT))
            assertEquals("50 percent", tester.semanticsNodesByLabel("Volume").single().value)

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("ADD").single().focused)
            assertTrue(tester.pressKey(PixelKey.SPACE))
            assertTrue(tester.semanticsNodesByLabel("ITEM ADDED").isNotEmpty())

            pressTabUntilFocused(tester, label = "NEW ITEM")
            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertTrue(tester.semanticsNodesByLabel("SELECTED item-3").isNotEmpty())

            pressTabUntilFocused(tester, label = "Mode")
            assertEquals("A", tester.semanticsNodesByLabel("Mode").single().value)
            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertTrue(tester.semanticsNodesByLabel("Menu").isNotEmpty())
            assertTrue(tester.semanticsNodesByLabel("A").single().focused)
            assertTrue(tester.pressKey(PixelKey.ARROW_DOWN))
            assertTrue(tester.semanticsNodesByLabel("B").single().focused)
            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertEquals("B", tester.semanticsNodesByLabel("Mode").single().value)

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("OPEN DIALOG").single().focused)
            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertTrue(tester.semanticsNodesByLabel("Dialog").isNotEmpty())
            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("CLOSE").single().focused)
            assertTrue(tester.pressKey(PixelKey.SPACE))
            assertFalse(tester.semanticsNodesByLabel("Dialog").any { node -> node.enabled })
            assertTrue(tester.semanticsNodesByLabel("DIALOG CLOSED").isNotEmpty())

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("OPEN DETAILS").single().focused)
            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertTrue(tester.semanticsNodesByLabel("BACK TO FLOW").isNotEmpty())
            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("BACK TO FLOW").single().focused)
            assertTrue(tester.pressKey(PixelKey.SPACE))
            assertTrue(tester.semanticsNodesByLabel("Name").isNotEmpty())
            assertTrue(tester.semanticsNodesByLabel("RETURNED").isNotEmpty())
        } finally {
            tester.dispose()
        }
    }

    /** Verifies logical DPAD directions adjust ranges and move inside a modal Menu focus scope. */
    @Test
    fun logicalDpadKeysAdjustSliderAndNavigateMenu() {
        /** Off-screen Demo runtime isolated from the full-closure test. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = accessibilityFlowBodyForTest(key = "dpad-flow"),
                logicalWidth = 160,
                logicalHeight = 180,
            )

            pressTab(tester, count = 2)
            assertTrue(tester.semanticsNodesByLabel("Volume").single().focused)
            assertTrue(tester.pressKey(PixelKey.ARROW_UP))
            assertEquals("50 percent", tester.semanticsNodesByLabel("Volume").single().value)
            assertTrue(tester.pressKey(PixelKey.ARROW_RIGHT))
            assertEquals("60 percent", tester.semanticsNodesByLabel("Volume").single().value)
            assertTrue(tester.pressKey(PixelKey.ARROW_LEFT))
            assertEquals("50 percent", tester.semanticsNodesByLabel("Volume").single().value)

            pressTabUntilFocused(tester, label = "Mode")
            assertEquals("A", tester.semanticsNodesByLabel("Mode").single().value)
            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertTrue(tester.semanticsNodesByLabel("A").single().focused)
            assertTrue(tester.pressKey(PixelKey.ARROW_DOWN))
            assertTrue(tester.semanticsNodesByLabel("B").single().focused)
            assertTrue(tester.pressKey(PixelKey.ARROW_UP))
            assertTrue(tester.semanticsNodesByLabel("A").single().focused)
            assertTrue(tester.pressKey(PixelKey.ARROW_DOWN))
            assertTrue(tester.pressKey(PixelKey.SPACE))
            assertEquals("B", tester.semanticsNodesByLabel("Mode").single().value)
        } finally {
            tester.dispose()
        }
    }

    /** Sends [count] handled Tab events without introducing pointer or semantics actions. */
    private fun pressTab(tester: PixelTester, count: Int) {
        repeat(count) {
            assertTrue(tester.pressKey(PixelKey.TAB))
        }
    }

    /** Traverses a dynamic keyed flow until [label] owns focus, independent from insertion order. */
    private fun pressTabUntilFocused(tester: PixelTester, label: String, maximumSteps: Int = 16) {
        repeat(maximumSteps) {
            if (tester.semanticsNodesByLabel(label).any { node -> node.focused }) return
            assertTrue(tester.pressKey(PixelKey.TAB))
        }
        throw AssertionError("Focus did not reach $label within $maximumSteps Tab steps")
    }
}
