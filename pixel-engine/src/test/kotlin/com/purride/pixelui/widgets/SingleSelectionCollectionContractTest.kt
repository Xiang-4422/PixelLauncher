package com.purride.pixelui.widgets

import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelSemanticsSelectionMode
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.Tabs
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks exact-one validation and collection identity for Tabs and SegmentedControl. */
class SingleSelectionCollectionContractTest {
    /** Both selectors export SINGLE metadata and keep semantic IDs through dynamic reordering. */
    @Test
    fun selectorsKeepBusinessIdentityAndFocusWhenLabelsReorder() {
        selectorCases().forEach { case ->
            /** Runtime retained across the initial and reordered controlled declarations. */
            val tester = PixelTester()
            try {
                /** Initial unique labels whose visible text is also their public stable identity. */
                val initialLabels = listOf("ALPHA", "BETA", "GAMMA")
                tester.pumpWidget(
                    widget = case.build(initialLabels, 1, "stable-selector"),
                    logicalWidth = 96,
                    logicalHeight = 24,
                )

                /** Group node carrying the selector's structured collection contract. */
                val initialGroup = tester.semanticsNodes().single { node ->
                    node.collectionInfo?.selectionMode == PixelSemanticsSelectionMode.SINGLE
                }
                assertEquals(1, initialGroup.collectionInfo?.rowCount)
                assertEquals(3, initialGroup.collectionInfo?.columnCount)
                /** Stable semantic IDs captured before the selected item changes column. */
                val initialIds = initialLabels.associateWith { label ->
                    tester.semanticsNodesByLabel(label).single().id
                }
                initialLabels.forEachIndexed { index, label ->
                    /** Item metadata proving current visual order and exact selected state. */
                    val item = tester.semanticsNodesByLabel(label).single()
                    assertEquals(0, item.collectionItemInfo?.rowIndex)
                    assertEquals(index, item.collectionItemInfo?.columnIndex)
                    assertEquals(label == "BETA", item.collectionItemInfo?.selected)
                }
                assertTrue(tester.pressKey(PixelKey.TAB))
                assertTrue(tester.semanticsNodesByLabel("BETA").single().focused)

                /** Reordered declaration keeps BETA selected while moving it to column zero. */
                val reorderedLabels = listOf("BETA", "GAMMA", "ALPHA")
                tester.pumpWidget(
                    widget = case.build(reorderedLabels, 0, "stable-selector"),
                    logicalWidth = 96,
                    logicalHeight = 24,
                )

                reorderedLabels.forEachIndexed { index, label ->
                    /** Retained node whose ID follows its label identity instead of old index. */
                    val item = tester.semanticsNodesByLabel(label).single()
                    assertEquals(initialIds.getValue(label), item.id)
                    assertEquals(index, item.collectionItemInfo?.columnIndex)
                    assertEquals(label == "BETA", item.selected)
                }
                assertTrue(tester.semanticsNodesByLabel("BETA").single().focused)
            } finally {
                tester.dispose()
            }
        }
    }

    /** Empty, duplicate, blank, and out-of-range declarations have one explicit contract. */
    @Test
    fun selectorsRejectAmbiguousSelectionAndDescribeAnEmptyCollection() {
        selectorCases().forEach { case ->
            assertThrows(IllegalArgumentException::class.java) {
                case.build(emptyList(), 0, "invalid-empty")
            }
            assertThrows(IllegalArgumentException::class.java) {
                case.build(listOf("A", "A"), 0, "invalid-duplicate")
            }
            assertThrows(IllegalArgumentException::class.java) {
                case.build(listOf("A", ""), 0, "invalid-blank")
            }
            assertThrows(IllegalArgumentException::class.java) {
                case.build(listOf("A", "B"), 2, "invalid-index")
            }

            /** Empty collection remains a valid non-focusable structural declaration at `-1`. */
            val tester = PixelTester()
            try {
                tester.pumpWidget(
                    widget = case.build(emptyList(), -1, "empty-selector"),
                    logicalWidth = 32,
                    logicalHeight = 12,
                )
                /** Sole empty group node with no phantom row or selectable descendant. */
                val emptyGroup = tester.semanticsNodes().single { node ->
                    node.collectionInfo?.selectionMode == PixelSemanticsSelectionMode.SINGLE
                }
                assertEquals(0, emptyGroup.collectionInfo?.rowCount)
                assertEquals(0, emptyGroup.collectionInfo?.columnCount)
                assertFalse(tester.pressKey(PixelKey.TAB))
            } finally {
                tester.dispose()
            }
        }
    }

    /** Creates both public selector families behind one identical controlled test contract. */
    private fun selectorCases(): List<SelectorCase> {
        return listOf(
            SelectorCase(name = "Tabs") { labels, selectedIndex, key ->
                Tabs(
                    labels = labels,
                    selectedIndex = selectedIndex,
                    onSelected = {},
                    key = key,
                )
            },
            SelectorCase(name = "SegmentedControl") { labels, selectedIndex, key ->
                SegmentedControl(
                    labels = labels,
                    selectedIndex = selectedIndex,
                    onSelected = {},
                    key = key,
                )
            },
        )
    }
}

/** Public selector factory used to enforce the same validation and semantics matrix. */
private data class SelectorCase(
    /** Human-readable family name retained for diagnostic test output. */
    val name: String,
    /** Builder receiving labels, controlled index, and stable retained identity. */
    val build: (List<String>, Int, Any) -> Widget,
)
