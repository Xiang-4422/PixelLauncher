package com.purride.pixelui

import com.purride.pixelui.animation.IntOffset
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks semantic rectangles to the exact ClipRect and FittedBox visual geometry. */
class PixelSemanticsGeometryTest {
    /** Padding, Flex child placement, and Translate contribute each offset exactly once. */
    @Test
    fun ordinaryLayoutOffsetsComposeWithoutChangingBounds() {
        val tester = PixelTester()
        tester.pumpWidget(
            widget = Padding(
                padding = EdgeInsets.only(left = 2, top = 1),
                child = Row(
                    children = listOf(
                        SizedBox(width = 3, height = 1),
                        Transform.translate(
                            offset = IntOffset(x = 2, y = 1),
                            child = Semantics(
                                label = "OFFSET",
                                child = SizedBox(width = 4, height = 3),
                            ),
                        ),
                    ),
                    spacing = 1,
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 10,
        )

        assertEquals(listOf(8, 2, 4, 3), tester.semanticsNodes().single().boundsList())
        tester.dispose()
    }

    /** FittedBox applies its uniform scale and center offset without replacing ids or callbacks. */
    @Test
    fun fittedBoxScalesNestedBoundsAndPreservesOwnership() {
        val tester = PixelTester()
        var clicks = 0
        /** Stable callbacks allow direct identity comparison after the geometry transform. */
        val actions = PixelSemanticsActions(
            onClick = {
                clicks += 1
                true
            },
        )
        tester.pumpWidget(
            widget = FittedBox(
                key = "fit",
                child = Semantics(
                    label = "PARENT",
                    actions = actions,
                    key = "parent",
                    child = Semantics(
                        label = "CHILD",
                        key = "child",
                        child = SizedBox(width = 10, height = 4),
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 12,
        )

        val targets = tester.renderResult!!.semanticsTargets
        assertEquals(2, targets.size)
        val parent = targets[0]
        val child = targets[1]
        assertEquals(listOf(0, 2, 20, 8), parent.node.boundsList())
        assertEquals(listOf(0, 2, 20, 8), child.node.boundsList())
        assertEquals(parent.node.id, child.node.parentId)
        assertNotEquals(0L, parent.node.id)
        assertSame(actions.onClick, parent.actions.onClick)
        assertTrue(parent.actions.onClick!!.invoke())
        assertEquals(1, clicks)

        /** Repeating the same retained tree must not derive a new id from transformed geometry. */
        tester.pumpWidget(
            widget = FittedBox(
                key = "fit",
                child = Semantics(
                    label = "PARENT UPDATED",
                    actions = actions,
                    key = "parent",
                    child = Semantics(
                        label = "CHILD",
                        key = "child",
                        child = SizedBox(width = 10, height = 4),
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 12,
        )
        val updated = tester.renderResult!!.semanticsTargets
        assertEquals(listOf(parent.node.id, child.node.id), updated.map { target -> target.node.id })
        tester.dispose()
    }

    /** ClipRect intersects partially visible semantic rectangles while retaining direct actions. */
    @Test
    fun clipRectIntersectsPartiallyVisibleBounds() {
        val tester = PixelTester()
        var activations = 0
        tester.pumpWidget(
            widget = ClipRect(
                child = Transform.translate(
                    offset = IntOffset(x = -3, y = 1),
                    child = Semantics(
                        label = "PARTIAL",
                        actions = PixelSemanticsActions(
                            onClick = {
                                activations += 1
                                true
                            },
                        ),
                        child = SizedBox(width = 8, height = 4),
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 12,
        )

        val visible = tester.renderResult!!.semanticsTargets.single()
        assertEquals(listOf(0, 1, 5, 3), visible.node.boundsList())
        assertTrue(visible.actions.onClick!!.invoke())
        assertEquals(1, activations)
        tester.dispose()
    }

    /** A visible descendant is reparented to Host when its clipped semantic ancestor disappears. */
    @Test
    fun clipRectRepairsParentAfterInvisibleAncestorRemoval() {
        val tester = PixelTester()
        tester.pumpWidget(
            widget = ClipRect(
                child = Transform.translate(
                    offset = IntOffset(x = -20, y = 0),
                    child = Semantics(
                        label = "INVISIBLE PARENT",
                        key = "parent",
                        child = Transform.translate(
                            offset = IntOffset(x = 20, y = 0),
                            child = Semantics(
                                label = "VISIBLE CHILD",
                                key = "child",
                                child = SizedBox(width = 8, height = 4),
                            ),
                        ),
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 12,
        )

        val visible = tester.renderResult!!.semanticsNodes.single()
        assertEquals("VISIBLE CHILD", visible.label)
        assertEquals(listOf(0, 0, 8, 4), visible.boundsList())
        assertNull(visible.parentId)
        tester.dispose()
    }

    /** Nodes fully outside the clip do not remain addressable through the semantic snapshot. */
    @Test
    fun clipRectDropsFullyInvisibleNodes() {
        val tester = PixelTester()
        tester.pumpWidget(
            widget = ClipRect(
                child = Transform.translate(
                    offset = IntOffset(x = 9, y = 0),
                    child = Semantics(
                        label = "OFFSCREEN",
                        child = SizedBox(width = 8, height = 4),
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 12,
        )

        assertTrue(tester.renderResult!!.semanticsNodes.isEmpty())
        tester.dispose()
    }
}

/** Returns logical bounds in compact assertion order: left, top, width, height. */
private fun PixelSemanticsNode.boundsList(): List<Int> = listOf(left, top, width, height)
