package com.purride.pixelui

import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.state.PixelTextFieldController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks retained semantic identity, hierarchy, boundary policy, and direct callback ownership. */
class PixelSemanticsRenderTest {
    /** Every v2 property reaches the frame snapshot and property updates retain the node id. */
    @Test
    fun completeConfigurationUpdatesOneRetainedSnapshot() {
        val tester = PixelTester()
        /** The factory changes semantic state without changing retained widget identity. */
        fun configured(selected: Boolean): Widget = Semantics(
            label = "VOLUME",
            value = if (selected) "75" else "50",
            hint = "Swipe to adjust",
            error = if (selected) null else "Too quiet",
            role = PixelSemanticRole.SLIDER,
            enabled = true,
            focused = selected,
            selected = selected,
            checked = null,
            expanded = selected,
            selectionStart = 1,
            selectionEnd = 2,
            rangeInfo = PixelSemanticsRangeInfo(
                current = if (selected) 0.75f else 0.5f,
                minimum = 0f,
                maximum = 1f,
                steps = 4,
            ),
            liveRegion = PixelSemanticsLiveRegion.POLITE,
            collectionInfo = PixelSemanticsCollectionInfo(rowCount = 1, columnCount = 1),
            collectionItemInfo = PixelSemanticsCollectionItemInfo(rowIndex = 0, columnIndex = 0),
            actions = PixelSemanticsActions(onSetProgress = { true }),
            key = "configured",
            child = SizedBox(width = 12, height = 4),
        )
        tester.pumpWidget(configured(selected = false), logicalWidth = 20, logicalHeight = 10)

        val initial = tester.renderResult!!.semanticsNodes.single()
        assertEquals("VOLUME", initial.label)
        assertEquals("50", initial.value)
        assertEquals("Swipe to adjust", initial.hint)
        assertEquals("Too quiet", initial.error)
        assertFalse(initial.selected)
        assertEquals(false, initial.expanded)
        assertEquals(0.5f, initial.rangeInfo!!.current, 0f)
        assertEquals(PixelSemanticsLiveRegion.POLITE, initial.liveRegion)
        assertEquals(1, initial.collectionInfo!!.rowCount)
        assertEquals(0, initial.collectionItemInfo!!.rowIndex)
        assertEquals(setOf(PixelSemanticsAction.SET_PROGRESS), initial.actions)

        tester.pumpWidget(configured(selected = true), logicalWidth = 20, logicalHeight = 10)
        val updated = tester.renderResult!!.semanticsNodes.single()
        assertEquals(initial.id, updated.id)
        assertEquals("75", updated.value)
        assertNull(updated.error)
        assertTrue(updated.focused)
        assertTrue(updated.selected)
        assertEquals(true, updated.expanded)
        assertEquals(0.75f, updated.rangeInfo!!.current, 0f)
        tester.dispose()
    }

    /** Nested non-merged boundaries retain ids while only direct semantic roots are reparented. */
    @Test
    fun nestedBoundariesBuildStableParentRelationships() {
        val tester = PixelTester()
        tester.pumpWidget(
            widget = nestedSemanticsTree(outerLabel = "OUTER", innerLabel = "INNER"),
            logicalWidth = 40,
            logicalHeight = 20,
        )

        /** Preorder is stable and therefore makes direct-parent assertions unambiguous. */
        val firstTargets = tester.renderResult!!.semanticsTargets
        assertEquals(3, firstTargets.size)
        val outer = firstTargets[0].node
        val inner = firstTargets[1].node
        val text = firstTargets[2].node
        assertNotEquals(0L, outer.id)
        assertNotEquals(0L, inner.id)
        assertNotEquals(0L, text.id)
        assertNull(outer.parentId)
        assertEquals(outer.id, inner.parentId)
        assertEquals(inner.id, text.parentId)

        tester.pumpWidget(
            widget = nestedSemanticsTree(outerLabel = "OUTER 2", innerLabel = "INNER 2"),
            logicalWidth = 40,
            logicalHeight = 20,
        )

        /** Updating immutable widget properties must not replace retained semantic identities. */
        val secondNodes = tester.renderResult!!.semanticsNodes
        assertEquals(listOf(outer.id, inner.id, text.id), secondNodes.map { node -> node.id })
        assertEquals("OUTER 2", secondNodes[0].label)
        assertEquals("INNER 2", secondNodes[1].label)
        tester.dispose()
    }

    /** Duplicate labels cannot act as identity; keyed reorder preserves ids per logical item. */
    @Test
    fun keyedDuplicateLabelsKeepIdsAcrossInsertionAndReorder() {
        val tester = PixelTester()
        tester.pumpWidget(
            widget = duplicateLabelRows(listOf("a", "b", "c")),
            logicalWidth = 60,
            logicalHeight = 20,
        )
        /** Source identity lets the test correlate each logical key without using the duplicate label. */
        val initialSources = tester.renderResult!!.semanticsTargets.associate { target ->
            target.source to target.node.id
        }

        tester.pumpWidget(
            widget = duplicateLabelRows(listOf("x", "c", "a", "b")),
            logicalWidth = 60,
            logicalHeight = 20,
        )
        /** Existing retained render sources must still expose the exact same ids in the new order. */
        val reorderedTargets = tester.renderResult!!.semanticsTargets
        assertEquals(4, reorderedTargets.size)
        val retainedIds = reorderedTargets.mapNotNull { target -> initialSources[target.source] }
        assertEquals(listOf(initialSources.values.elementAt(2), initialSources.values.elementAt(0), initialSources.values.elementAt(1)), retainedIds)
        assertTrue(reorderedTargets.all { target -> target.node.label == "SAME" })
        assertEquals(4, reorderedTargets.map { target -> target.node.id }.distinct().size)
        tester.dispose()
    }

    /** Merge folds unique spoken content and missing callbacks while parent callbacks win conflicts. */
    @Test
    fun mergeDescendantsProducesOneNodeWithDirectActions() {
        val tester = PixelTester()
        var parentClicks = 0
        var childLongClicks = 0
        var parentCustomCalls = 0
        var childCustomCalls = 0
        /** The parent and child intentionally share one custom id to lock collision precedence. */
        val parentActions = PixelSemanticsActions(
            onClick = {
                parentClicks += 1
                true
            },
            customActions = listOf(
                PixelSemanticsCustomAction("shared", "Parent action") {
                    parentCustomCalls += 1
                    true
                },
            ),
        )
        val childActions = PixelSemanticsActions(
            onLongClick = {
                childLongClicks += 1
                true
            },
            customActions = listOf(
                PixelSemanticsCustomAction("shared", "Child action") {
                    childCustomCalls += 1
                    true
                },
            ),
        )
        tester.pumpWidget(
            widget = Semantics(
                label = "SAVE",
                role = PixelSemanticRole.BUTTON,
                mergeDescendants = true,
                actions = parentActions,
                key = "outer",
                child = Semantics(
                    label = "SAVE",
                    role = PixelSemanticRole.BUTTON,
                    value = "READY",
                    hint = "Hold for options",
                    error = "Offline",
                    focused = true,
                    selected = true,
                    checked = true,
                    expanded = true,
                    selectionStart = 0,
                    selectionEnd = 1,
                    rangeInfo = PixelSemanticsRangeInfo(current = 0.5f),
                    liveRegion = PixelSemanticsLiveRegion.ASSERTIVE,
                    actions = childActions,
                    key = "inner",
                    child = Text("DETAIL", key = "detail"),
                ),
            ),
            logicalWidth = 40,
            logicalHeight = 12,
        )

        val merged = tester.renderResult!!.semanticsTargets.single()
        assertEquals("SAVE, DETAIL", merged.node.label)
        assertEquals("READY", merged.node.value)
        assertEquals("Hold for options", merged.node.hint)
        assertEquals("Offline", merged.node.error)
        assertTrue(merged.node.focused)
        assertTrue(merged.node.selected)
        assertEquals(true, merged.node.checked)
        assertEquals(true, merged.node.expanded)
        assertEquals(0, merged.node.selectionStart)
        assertEquals(1, merged.node.selectionEnd)
        assertEquals(0.5f, merged.node.rangeInfo!!.current, 0f)
        assertEquals(PixelSemanticsLiveRegion.ASSERTIVE, merged.node.liveRegion)
        assertEquals(
            setOf(PixelSemanticsAction.CLICK, PixelSemanticsAction.LONG_CLICK, PixelSemanticsAction.CUSTOM),
            merged.node.actions,
        )
        assertEquals(mapOf("shared" to "Parent action"), merged.node.customActionLabels)
        assertTrue(merged.actions.onClick!!.invoke())
        assertTrue(merged.actions.onLongClick!!.invoke())
        assertTrue(merged.actions.customActions.single().onInvoke())
        assertEquals(1, parentClicks)
        assertEquals(1, childLongClicks)
        assertEquals(1, parentCustomCalls)
        assertEquals(0, childCustomCalls)
        tester.dispose()
    }

    /** Exclusion keeps the boundary but prevents descendant nodes and callbacks from escaping. */
    @Test
    fun excludeDescendantsHidesCompleteSemanticSubtree() {
        val tester = PixelTester()
        tester.pumpWidget(
            widget = Semantics(
                label = "DECORATIVE GROUP",
                excludeDescendants = true,
                key = "outer",
                child = Semantics(
                    label = "HIDDEN ACTION",
                    actions = PixelSemanticsActions(onClick = { true }),
                    key = "inner",
                    child = Text("HIDDEN TEXT"),
                ),
            ),
            logicalWidth = 40,
            logicalHeight = 12,
        )

        val visible = tester.renderResult!!.semanticsTargets.single()
        assertEquals("DECORATIVE GROUP", visible.node.label)
        assertTrue(visible.node.actions.isEmpty())
        assertNull(visible.actions.onClick)
        tester.dispose()
    }

    /** Text fields own value mutation and selection actions and suppress their visual text child. */
    @Test
    fun textFieldExportsOneStableNodeWithTypedActions() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "ABC", selectionStart = 1, selectionEnd = 2)
        var changedText: String? = null
        fun field(): Widget = TextField(
            state = state,
            controller = controller,
            onChanged = { text -> changedText = text },
            key = "field",
        )
        tester.pumpWidget(field(), logicalWidth = 40, logicalHeight = 12)

        val initial = tester.renderResult!!.semanticsTargets.single()
        assertEquals(PixelSemanticRole.TEXT_FIELD, initial.node.role)
        assertEquals("ABC", initial.node.value)
        assertEquals(1, initial.node.selectionStart)
        assertEquals(2, initial.node.selectionEnd)
        assertEquals(
            setOf(PixelSemanticsAction.CLICK, PixelSemanticsAction.SET_TEXT, PixelSemanticsAction.SET_SELECTION),
            initial.node.actions,
        )
        assertTrue(initial.actions.onSetText!!.invoke("HELLO"))
        assertEquals("HELLO", state.text)
        assertEquals("HELLO", changedText)
        assertFalse(initial.actions.onSetSelection!!.invoke(-1, 2))
        assertTrue(initial.actions.onSetSelection!!.invoke(1, 4))
        assertEquals(1, state.selectionStart)
        assertEquals(4, state.selectionEnd)

        tester.pumpWidget(field(), logicalWidth = 40, logicalHeight = 12)
        val updated = tester.renderResult!!.semanticsTargets.single()
        assertEquals(initial.node.id, updated.node.id)
        assertEquals("HELLO", updated.node.value)
        assertSame(initial.source, updated.source)
        tester.dispose()
    }

    /** One multi-node render object assigns stable local ids and callbacks to each action slot. */
    @Test
    fun valueAdjusterUsesDistinctStableLocalIdsAndCallbacks() {
        val tester = PixelTester()
        var decreases = 0
        var increases = 0
        /** Stable callback instances let retained update exercise node identity independently. */
        val decrease: () -> Unit = { decreases += 1 }
        val increase: () -> Unit = { increases += 1 }
        fun adjuster(value: String): Widget = ValueAdjuster(
            valueText = value,
            onDecrease = decrease,
            onIncrease = increase,
            key = "adjuster",
        )
        tester.pumpWidget(adjuster("1"), logicalWidth = 60, logicalHeight = 16)

        val initialButtons = tester.renderResult!!.semanticsTargets
            .filter { target -> target.node.role == PixelSemanticRole.BUTTON }
        assertEquals(2, initialButtons.size)
        assertNotEquals(initialButtons[0].node.id, initialButtons[1].node.id)
        assertTrue(initialButtons[0].actions.onClick!!.invoke())
        assertTrue(initialButtons[1].actions.onClick!!.invoke())
        assertEquals(1, decreases)
        assertEquals(1, increases)

        tester.pumpWidget(adjuster("2"), logicalWidth = 60, logicalHeight = 16)
        val updatedButtons = tester.renderResult!!.semanticsTargets
            .filter { target -> target.node.role == PixelSemanticRole.BUTTON }
        assertEquals(initialButtons.map { it.node.id }, updatedButtons.map { it.node.id })
        tester.dispose()
    }

    /** Creates two keyed boundaries so retained hierarchy survives label-only updates. */
    private fun nestedSemanticsTree(outerLabel: String, innerLabel: String): Widget {
        return Semantics(
            label = outerLabel,
            key = "outer",
            child = Semantics(
                label = innerLabel,
                key = "inner",
                child = Text("BODY", key = "body"),
            ),
        )
    }

    /** Creates independently keyed rows with intentionally indistinguishable spoken labels. */
    private fun duplicateLabelRows(keys: List<String>): Widget {
        return Row(
            children = keys.map { key ->
                Semantics(
                    label = "SAME",
                    mergeDescendants = true,
                    key = key,
                    child = Text("SAME", key = "$key-text"),
                )
            },
        )
    }
}
