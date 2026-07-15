package com.purride.pixelui.foundation

import com.purride.pixelui.Column
import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.FocusScope
import com.purride.pixelui.FocusScopeNode
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelKeyEvent
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.PixelUiRuntime
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that focus state is retained and dispatched by one runtime instead of a process singleton.
 */
class PixelFocusOwnerIsolationTest {
    /** Two simultaneous test runtimes keep independent primary focus and traversal state. */
    @Test
    fun simultaneousPixelTestersDispatchOnlyWithinTheirOwnRuntime() {
        val firstNodes = listOf(FocusNode("first-a"), FocusNode("first-b"))
        val secondNodes = listOf(FocusNode("second-a"), FocusNode("second-b"))
        val firstTester = PixelTester()
        val secondTester = PixelTester()
        try {
            firstTester.pumpWidget(focusPair(firstNodes, autofocus = true), 48, 24)
            secondTester.pumpWidget(focusPair(secondNodes, autofocus = true), 48, 24)

            assertTrue(firstNodes[0].isFocused)
            assertTrue(secondNodes[0].isFocused)

            assertTrue(firstTester.pressKey(PixelKey.TAB))
            assertTrue(firstNodes[1].isFocused)
            assertTrue(secondNodes[0].isFocused)
            assertFalse(secondNodes[1].isFocused)

            assertTrue(secondTester.pressKey(PixelKey.TAB))
            assertTrue(firstNodes[1].isFocused)
            assertTrue(secondNodes[1].isFocused)
        } finally {
            firstTester.dispose()
            secondTester.dispose()
        }
    }

    /** Disposing one runtime clears its nodes without clearing a sibling runtime's focus. */
    @Test
    fun disposingOneTesterDoesNotClearTheOtherTester() {
        val firstNodes = listOf(FocusNode("disposed-a"), FocusNode("disposed-b"))
        val secondNodes = listOf(FocusNode("survivor-a"), FocusNode("survivor-b"))
        val firstTester = PixelTester()
        val secondTester = PixelTester()
        var firstDisposed = false
        try {
            firstTester.pumpWidget(focusPair(firstNodes, autofocus = true), 48, 24)
            secondTester.pumpWidget(focusPair(secondNodes, autofocus = true), 48, 24)

            firstTester.dispose()
            firstDisposed = true

            assertFalse(firstNodes[0].isFocused)
            assertTrue(secondNodes[0].isFocused)
            assertTrue(secondTester.pressKey(PixelKey.TAB))
            assertTrue(secondNodes[1].isFocused)
        } finally {
            if (!firstDisposed) firstTester.dispose()
            secondTester.dispose()
        }
    }

    /** Raw retained runtimes expose the same isolation without relying on PixelTester cleanup. */
    @Test
    fun rawPixelUiRuntimesOwnIndependentFocusManagers() {
        val firstNodes = listOf(FocusNode("raw-first-a"), FocusNode("raw-first-b"))
        val secondNodes = listOf(FocusNode("raw-second-a"), FocusNode("raw-second-b"))
        val firstRuntime = PixelUiRuntime()
        val secondRuntime = PixelUiRuntime()
        try {
            firstRuntime.render(focusPair(firstNodes, autofocus = true), 48, 24)
            secondRuntime.render(focusPair(secondNodes, autofocus = true), 48, 24)

            assertTrue(firstRuntime.focusOwner.dispatchKeyEvent(PixelKeyEvent(PixelKey.TAB)))

            assertTrue(firstNodes[1].isFocused)
            assertTrue(secondNodes[0].isFocused)
            assertFalse(secondNodes[1].isFocused)
        } finally {
            firstRuntime.dispose()
            secondRuntime.dispose()
        }
    }

    /** A forward traversal with no primary focus starts at the first enabled node. */
    @Test
    fun firstTabWithoutPrimaryFocusSelectsFirstNode() {
        val nodes = listOf(FocusNode("tab-first"), FocusNode("tab-second"))
        val tester = PixelTester()
        try {
            tester.pumpWidget(focusPair(nodes, autofocus = false), 48, 24)

            assertTrue(tester.pressKey(PixelKey.TAB))

            assertTrue(nodes[0].isFocused)
            assertFalse(nodes[1].isFocused)
        } finally {
            tester.dispose()
        }
    }

    /** A reverse traversal with no primary focus starts at the last enabled node. */
    @Test
    fun firstShiftTabWithoutPrimaryFocusSelectsLastNode() {
        val nodes = listOf(FocusNode("shift-first"), FocusNode("shift-last"))
        val tester = PixelTester()
        try {
            tester.pumpWidget(focusPair(nodes, autofocus = false), 48, 24)

            assertTrue(tester.pressKey(PixelKey.SHIFT_TAB))

            assertFalse(nodes[0].isFocused)
            assertTrue(nodes[1].isFocused)
        } finally {
            tester.dispose()
        }
    }

    /** Default Focus and FocusScope nodes survive a declarative rebuild with stable widget keys. */
    @Test
    fun defaultFocusAndScopeNodesRetainFocusAcrossRebuild() {
        val tester = PixelTester()
        try {
            tester.pumpWidget(defaultOwnedFocusTree(autofocus = true), 48, 24)
            assertTrue(tester.dumpSemanticsTree().contains("focused=true"))

            tester.pumpWidget(defaultOwnedFocusTree(autofocus = false), 48, 24)

            assertTrue(tester.dumpSemanticsTree().contains("focused=true"))
        } finally {
            tester.dispose()
        }
    }

    /** Disabling the primary node immediately transfers focus to the next enabled sibling. */
    @Test
    fun disablingCurrentNodeTransfersFocusWithinItsScope() {
        val first = FocusNode("enabled-first")
        val second = FocusNode("enabled-second")
        val tester = PixelTester()
        try {
            tester.pumpWidget(focusPair(listOf(first, second), autofocus = true), 48, 24)
            assertTrue(first.isFocused)

            first.canRequestFocus = false

            assertFalse(first.isFocused)
            assertTrue(second.isFocused)
        } finally {
            tester.dispose()
        }
    }

    /** Replacing an explicit scope releases the old owner after its descendants migrate. */
    @Test
    fun replacingExplicitScopeAllowsOldScopeInAnotherRuntime() {
        /** Original scope replaced by the same keyed retained boundary. */
        val firstScope = FocusScopeNode()
        /** Replacement scope that remains in the first runtime. */
        val secondScope = FocusScopeNode()
        /** Stable node migrated from the original scope to its replacement. */
        val migratedNode = FocusNode("migrated")
        /** Independent node mounted under the released scope in a sibling runtime. */
        val siblingNode = FocusNode("sibling")
        /** First retained runtime performing the scope replacement. */
        val firstRuntime = PixelUiRuntime()
        /** Second runtime proving the original scope no longer retains its old owner. */
        val secondRuntime = PixelUiRuntime()
        try {
            firstRuntime.render(explicitScopeTree(firstScope, migratedNode), 48, 24)
            firstRuntime.render(explicitScopeTree(secondScope, migratedNode), 48, 24)

            assertSame(secondScope, migratedNode.scope)
            secondRuntime.render(explicitScopeTree(firstScope, siblingNode), 48, 24)
            assertSame(firstScope, siblingNode.scope)
        } finally {
            firstRuntime.dispose()
            secondRuntime.dispose()
        }
    }

    /** Builds a stable two-node traversal scope for runtime-isolation assertions. */
    private fun focusPair(nodes: List<FocusNode>, autofocus: Boolean): Widget {
        return FocusScope(
            key = "pair-scope",
            child = Column(
                children = nodes.mapIndexed { index, node ->
                    Focus(
                        node = node,
                        autofocus = autofocus && index == 0,
                        key = "pair-focus-$index",
                        child = Text("NODE $index"),
                    )
                },
            ),
        )
    }

    /** Builds a tree whose omitted Focus and FocusScope nodes must be retained by State. */
    private fun defaultOwnedFocusTree(autofocus: Boolean): Widget {
        return FocusScope(
            key = "default-scope",
            child = Focus(
                autofocus = autofocus,
                key = "default-focus",
                child = OutlinedButton(text = "DEFAULT", onPressed = { }),
            ),
        )
    }

    /** Builds one keyed explicit scope so a retained update can replace only its node owner. */
    private fun explicitScopeTree(scope: FocusScopeNode, node: FocusNode): Widget {
        return FocusScope(
            node = scope,
            key = "explicit-scope",
            child = Focus(
                node = node,
                key = "explicit-focus",
                child = Text("EXPLICIT"),
            ),
        )
    }
}
