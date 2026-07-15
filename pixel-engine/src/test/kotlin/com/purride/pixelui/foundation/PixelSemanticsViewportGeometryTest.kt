package com.purride.pixelui

import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks less common viewport paths to the shared semantic visibility and clipping contract. */
class PixelSemanticsViewportGeometryTest {
    /** Variable-height lazy rows are clipped to the viewport and retain logical row metadata. */
    @Test
    fun variableLazyListClipsPartiallyVisibleRows() {
        val controller = PixelListController()
        val state = controller.create(initialScrollOffsetPx = 3f)
        val tester = PixelTester()
        tester.pumpWidget(
            widget = SizedBox(
                height = 10,
                child = ListViewBuilder(
                    itemCount = 6,
                    itemBuilder = { index -> semanticRow(label = "VARIABLE $index", height = 6) },
                    estimatedItemExtent = 6,
                    state = state,
                    controller = controller,
                    key = "variable-list",
                ),
            ),
            logicalWidth = 30,
            logicalHeight = 10,
        )
        tester.pumpFrame(0)

        val list = tester.semanticsNodes().single { node -> node.role == PixelSemanticRole.LIST }
        val rows = tester.semanticsNodes().filter { node -> node.label.startsWith("VARIABLE ") }
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { node -> node.top >= list.top && node.top + node.height <= list.top + list.height })
        assertEquals(3, rows.first { node -> node.label == "VARIABLE 0" }.height)
        assertEquals(0, rows.first { node -> node.label == "VARIABLE 0" }.collectionItemInfo?.rowIndex)
        assertTrue(tester.semanticsNodesByLabel("VARIABLE 3").isEmpty())
        assertTrue(rows.all { node -> node.parentId == list.id })
        tester.dispose()
    }

    /** Separated lazy lists attach item metadata only to items, never to decorative separators. */
    @Test
    fun separatedLazyListClipsItemsAndKeepsSeparatorsOutOfCollectionPositions() {
        val controller = PixelListController()
        val state = controller.create(initialScrollOffsetPx = 5f)
        val tester = PixelTester()
        tester.pumpWidget(
            widget = SizedBox(
                height = 10,
                child = ListViewSeparatedBuilder(
                    itemCount = 5,
                    itemBuilder = { index -> semanticRow(label = "ITEM $index", height = 6) },
                    separatorBuilder = { index -> semanticRow(label = "SEPARATOR $index", height = 2) },
                    itemExtent = 6,
                    separatorExtent = 2,
                    state = state,
                    controller = controller,
                    key = "separated-list",
                ),
            ),
            logicalWidth = 30,
            logicalHeight = 10,
        )
        tester.pumpFrame(0)

        val list = tester.semanticsNodes().single { node -> node.role == PixelSemanticRole.LIST }
        val items = tester.semanticsNodes().filter { node -> node.label.startsWith("ITEM ") }
        val separators = tester.semanticsNodes().filter { node -> node.label.startsWith("SEPARATOR ") }
        assertEquals(1, items.first { node -> node.label == "ITEM 0" }.height)
        assertEquals(list.id, items.first().parentId)
        assertEquals(listOf(0, 1), items.mapNotNull { node -> node.collectionItemInfo?.rowIndex })
        assertTrue(separators.isNotEmpty())
        assertTrue(separators.all { node -> node.collectionItemInfo == null })
        assertTrue((items + separators).all { node ->
            node.top >= list.top && node.top + node.height <= list.top + list.height
        })
        tester.dispose()
    }

    /** CustomScrollView forwards only visible sliver semantics through its collection parent. */
    @Test
    fun customScrollClipsVisibleSliverDescendants() {
        val controller = PixelListController()
        val state = controller.create(initialScrollOffsetPx = 5f)
        val tester = PixelTester()
        tester.pumpWidget(
            widget = SizedBox(
                height = 10,
                child = CustomScrollView(
                    slivers = listOf(
                        SliverList(
                            items = List(5) { index -> semanticRow(label = "SLIVER $index", height = 6) },
                            key = "sliver-list",
                        ),
                    ),
                    state = state,
                    controller = controller,
                    key = "custom-scroll",
                ),
            ),
            logicalWidth = 30,
            logicalHeight = 10,
        )
        tester.pumpFrame(0)

        val list = tester.semanticsNodes().single { node -> node.role == PixelSemanticRole.LIST }
        val rows = tester.semanticsNodes().filter { node -> node.label.startsWith("SLIVER ") }
        assertTrue(rows.isNotEmpty())
        assertEquals(1, rows.first { node -> node.label == "SLIVER 0" }.height)
        assertTrue(tester.semanticsNodesByLabel("SLIVER 3").isEmpty())
        assertTrue(rows.all { node -> node.top >= list.top && node.top + node.height <= list.top + list.height })
        assertTrue(rows.all { node -> node.parentId == list.id })
        // Custom sliver collection positions are not inferred from unrelated render-child indices.
        assertTrue(rows.all { node -> node.collectionItemInfo == null })
        assertNull(list.parentId)
        tester.dispose()
    }

    /** Creates one exact-height semantic row without an additional visual Text node. */
    private fun semanticRow(label: String, height: Int): Widget {
        return Semantics(
            label = label,
            excludeDescendants = true,
            child = SizedBox(width = 30, height = height),
        )
    }
}
