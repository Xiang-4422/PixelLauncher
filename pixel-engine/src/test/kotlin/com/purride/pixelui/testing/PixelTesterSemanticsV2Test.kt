package com.purride.pixelui.testing

import com.purride.pixelui.Column
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.PixelSemanticsActions
import com.purride.pixelui.PixelSemanticsCustomAction
import com.purride.pixelui.Semantics
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM acceptance for stable semantic identity, hierarchy, merging, and direct actions. */
class PixelTesterSemanticsV2Test {
    /** Keyed nodes keep ids across insert/delete/reorder even when every spoken label is identical. */
    @Test
    fun keyedDuplicateLabelsKeepStableIdsAcrossDynamicReorder() {
        val tester = PixelTester()
        val invokedItems = mutableListOf<String>()

        /** Builds duplicate-label items whose value is test-only business identity evidence. */
        fun buildItems(order: List<String>) = Column(
            children = order.map { itemId ->
                Semantics(
                    label = "DELETE",
                    value = itemId,
                    role = PixelSemanticRole.BUTTON,
                    mergeDescendants = true,
                    actions = PixelSemanticsActions(
                        onClick = {
                            invokedItems += itemId
                            true
                        },
                    ),
                    child = SizedBox(width = 8, height = 4, child = Text("DELETE")),
                    key = itemId,
                )
            },
        )

        tester.pumpWidget(buildItems(listOf("a", "b", "c")), logicalWidth = 32, logicalHeight = 24)
        val firstIds = tester.semanticsNodesByLabel("DELETE").associate { node -> node.value to node.id }
        assertEquals(3, firstIds.size)
        assertNotEquals(firstIds["a"], firstIds["b"])

        tester.pumpWidget(buildItems(listOf("x", "c", "a", "b")), logicalWidth = 32, logicalHeight = 24)
        val reorderedIds = tester.semanticsNodesByLabel("DELETE").associate { node -> node.value to node.id }
        assertEquals(firstIds["a"], reorderedIds["a"])
        assertEquals(firstIds["b"], reorderedIds["b"])
        assertEquals(firstIds["c"], reorderedIds["c"])

        val removedId = checkNotNull(firstIds["b"])
        tester.pumpWidget(buildItems(listOf("c", "a", "x")), logicalWidth = 32, logicalHeight = 24)
        assertNull(tester.semanticsNode(removedId))
        assertFalse(tester.performSemanticsAction(removedId, PixelSemanticsAction.CLICK))

        val cId = checkNotNull(firstIds["c"])
        assertTrue(tester.performSemanticsAction(cId, PixelSemanticsAction.CLICK))
        assertEquals(listOf("c"), invokedItems)
        tester.dispose()
    }

    /** Parent ids, merge, and exclude rules form one unambiguous spoken tree. */
    @Test
    fun semanticBoundariesBuildHierarchyAndControlDescendants() {
        val tester = PixelTester()
        tester.pumpWidget(
            widget = Semantics(
                label = "FORM",
                role = PixelSemanticRole.GENERIC,
                child = Semantics(
                    label = "SAVE",
                    role = PixelSemanticRole.BUTTON,
                    mergeDescendants = true,
                    child = Text("SAVE"),
                    key = "save",
                ),
                key = "form",
            ),
            logicalWidth = 24,
            logicalHeight = 12,
        )
        val form = tester.semanticsNodesByLabel("FORM").single()
        val save = tester.semanticsNodesByLabel("SAVE").single()
        assertEquals(form.id, save.parentId)
        assertEquals(2, tester.semanticsNodes().size)

        tester.pumpWidget(
            widget = Semantics(
                label = "HIDDEN GROUP",
                excludeDescendants = true,
                child = Text("SECRET"),
                key = "hidden",
            ),
            logicalWidth = 24,
            logicalHeight = 12,
        )
        assertEquals(listOf("HIDDEN GROUP"), tester.semanticsNodes().map { node -> node.label })
        tester.dispose()
    }

    /** Every typed callback receives its own arguments without a coordinate hit-test fallback. */
    @Test
    fun typedSemanticActionsInvokeOwningCallbacks() {
        val tester = PixelTester()
        val events = mutableListOf<String>()
        val actions = PixelSemanticsActions(
            onClick = { events += "click"; true },
            onLongClick = { events += "long"; true },
            onScrollForward = { events += "forward"; true },
            onScrollBackward = { events += "backward"; true },
            onSetText = { text -> events += "text:$text"; true },
            onSetSelection = { start, end -> events += "selection:$start-$end"; true },
            onSetProgress = { progress -> events += "progress:$progress"; true },
            onDismiss = { events += "dismiss"; true },
            onExpand = { events += "expand"; true },
            onCollapse = { events += "collapse"; true },
            customActions = listOf(
                PixelSemanticsCustomAction(
                    id = "archive",
                    label = "Archive",
                    onInvoke = { events += "custom:archive"; true },
                ),
            ),
        )
        tester.pumpWidget(
            widget = Semantics(
                label = "OWNER",
                role = PixelSemanticRole.BUTTON,
                actions = actions,
                excludeDescendants = true,
                child = SizedBox(width = 8, height = 4),
                key = "owner",
            ),
            logicalWidth = 16,
            logicalHeight = 8,
        )
        val node = tester.semanticsNodesByLabel("OWNER").single()
        assertEquals(actions.capabilitySet(), node.actions)

        assertTrue(tester.performSemanticsAction(node.id, PixelSemanticsAction.CLICK))
        assertTrue(tester.performSemanticsAction(node.id, PixelSemanticsAction.LONG_CLICK))
        assertTrue(tester.performSemanticsAction(node.id, PixelSemanticsAction.SCROLL_FORWARD))
        assertTrue(tester.performSemanticsAction(node.id, PixelSemanticsAction.SCROLL_BACKWARD))
        assertTrue(
            tester.performSemanticsAction(
                node.id,
                PixelSemanticsAction.SET_TEXT,
                PixelSemanticsActionArguments(text = "hello"),
            ),
        )
        assertTrue(
            tester.performSemanticsAction(
                node.id,
                PixelSemanticsAction.SET_SELECTION,
                PixelSemanticsActionArguments(selectionStart = 1, selectionEnd = 4),
            ),
        )
        assertTrue(
            tester.performSemanticsAction(
                node.id,
                PixelSemanticsAction.SET_PROGRESS,
                PixelSemanticsActionArguments(progress = 0.75f),
            ),
        )
        assertTrue(tester.performSemanticsAction(node.id, PixelSemanticsAction.DISMISS))
        assertTrue(tester.performSemanticsAction(node.id, PixelSemanticsAction.EXPAND))
        assertTrue(tester.performSemanticsAction(node.id, PixelSemanticsAction.COLLAPSE))
        assertTrue(
            tester.performSemanticsAction(
                node.id,
                PixelSemanticsAction.CUSTOM,
                PixelSemanticsActionArguments(customActionId = "archive"),
            ),
        )
        assertEquals(
            listOf(
                "click",
                "long",
                "forward",
                "backward",
                "text:hello",
                "selection:1-4",
                "progress:0.75",
                "dismiss",
                "expand",
                "collapse",
                "custom:archive",
            ),
            events,
        )
        tester.dispose()
    }
}
