package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelAxis
import com.purride.pixelui.Checkbox
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CustomScrollView
import com.purride.pixelui.Dialog
import com.purride.pixelui.Dropdown
import com.purride.pixelui.GridViewBuilder
import com.purride.pixelui.ListTile
import com.purride.pixelui.ListView
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.ListViewSeparatedBuilder
import com.purride.pixelui.Menu
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PageView
import com.purride.pixelui.PixelMenuItem
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.PixelSemanticsLiveRegion
import com.purride.pixelui.SliverListBuilder
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Slider
import com.purride.pixelui.Snackbar
import com.purride.pixelui.Switch
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextField
import com.purride.pixelui.Toast
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.PixelSemanticsActionArguments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks the Semantics v2 contract of standard controls and scrollable component families. */
class StandardComponentSemanticsTest {
    /** Explicit labels suppress visual Text descendants and repeated labels retain action ownership. */
    @Test
    fun buttonsAndListTilesExportOneDirectActionNodeEach() {
        /** Distinct counters prove actions do not fall back to coordinate hit testing. */
        var firstCount = 0
        var secondCount = 0
        val tester = PixelTester()
        tester.pumpWidget(
            widget = Column(
                children = listOf(
                    OutlinedButton("DELETE", onPressed = { firstCount += 1 }, key = "first"),
                    TextButton("DELETE", onPressed = { secondCount += 1 }, key = "second"),
                    ListTile(
                        title = Text("DETAIL"),
                        semanticLabel = "OPEN DETAIL",
                        onTap = { secondCount += 10 },
                        key = "tile",
                    ),
                ),
            ),
            logicalWidth = 80,
            logicalHeight = 40,
        )

        val deleteNodes = tester.semanticsNodesByLabel("DELETE")
        assertEquals(2, deleteNodes.size)
        assertNotEquals(deleteNodes[0].id, deleteNodes[1].id)
        assertEquals(1, tester.semanticsNodesByLabel("OPEN DETAIL").size)
        assertTrue(tester.performSemanticsAction(deleteNodes[0].id, PixelSemanticsAction.CLICK))
        assertTrue(tester.performSemanticsAction(deleteNodes[1].id, PixelSemanticsAction.CLICK))
        assertEquals(1, firstCount)
        assertEquals(1, secondCount)

        val tile = tester.semanticsNodesByLabel("OPEN DETAIL").single()
        assertTrue(tester.performSemanticsAction(tile.id, PixelSemanticsAction.CLICK))
        assertEquals(11, secondCount)
        assertTrue(tester.semanticsNodesByLabel("DETAIL").isEmpty())
        tester.dispose()
    }

    /** Checked and selected values are structured state rather than English label suffixes. */
    @Test
    fun selectionControlsExportCheckedAndSelectedState() {
        var checkboxRequest: Boolean? = null
        var switchRequest: Boolean? = null
        var selectedTab = -1
        val tester = PixelTester()
        tester.pumpWidget(
            widget = Column(
                children = listOf(
                    Checkbox(
                        checked = true,
                        onChanged = { checkboxRequest = it },
                        semanticLabel = "Archive",
                        key = "check",
                    ),
                    Switch(
                        checked = false,
                        onChanged = { switchRequest = it },
                        semanticLabel = "Notifications",
                        key = "switch",
                    ),
                    Tabs(
                        labels = listOf("HOME", "SETTINGS"),
                        selectedIndex = 1,
                        onSelected = { selectedTab = it },
                        key = "tabs",
                    ),
                ),
            ),
            logicalWidth = 100,
            logicalHeight = 48,
        )

        val checkbox = tester.semanticsNodesByLabel("Archive").single()
        val toggle = tester.semanticsNodesByLabel("Notifications").single()
        val tabs = tester.semanticsNodes().filter { node -> node.role == PixelSemanticRole.TAB }
        assertEquals(true, checkbox.checked)
        assertEquals(false, toggle.checked)
        assertEquals(listOf(false, true), tabs.map { node -> node.selected })
        assertTrue(tester.performSemanticsAction(checkbox.id, PixelSemanticsAction.CLICK))
        assertTrue(tester.performSemanticsAction(toggle.id, PixelSemanticsAction.CLICK))
        assertTrue(tester.performSemanticsAction(tabs.first().id, PixelSemanticsAction.CLICK))
        assertEquals(false, checkboxRequest)
        assertEquals(true, switchRequest)
        assertEquals(0, selectedTab)
        tester.dispose()
    }

    /** Text editing and Slider progress actions mutate their controlled state through typed callbacks. */
    @Test
    fun textFieldAndSliderOwnTypedActionsAndStructuredValues() {
        val textController = PixelTextFieldController()
        val textState = textController.create(initialText = "Ada")
        var draggedValue = -1f
        var releasedValue = -1f
        val tester = PixelTester()
        tester.pumpWidget(
            widget = Column(
                children = listOf(
                    TextField(
                        state = textState,
                        controller = textController,
                        placeholder = "Enter name",
                        semanticLabel = "Name",
                        semanticHint = "Required",
                        semanticError = "Too short",
                        key = "name",
                    ),
                    Slider(
                        value = 0.25f,
                        onDrag = { draggedValue = it },
                        onRelease = { releasedValue = it },
                        semanticLabel = "Volume",
                        semanticSteps = 3,
                        key = "volume",
                    ),
                ),
            ),
            logicalWidth = 80,
            logicalHeight = 32,
        )

        val field = tester.semanticsNodesByLabel("Name").single()
        assertEquals("Ada", field.value)
        assertEquals("Required", field.hint)
        assertEquals("Too short", field.error)
        assertTrue(
            tester.performSemanticsAction(
                field.id,
                PixelSemanticsAction.SET_TEXT,
                PixelSemanticsActionArguments(text = "Grace"),
            ),
        )
        assertEquals("Grace", textState.text)
        assertTrue(
            tester.performSemanticsAction(
                field.id,
                PixelSemanticsAction.SET_SELECTION,
                PixelSemanticsActionArguments(selectionStart = 1, selectionEnd = 4),
            ),
        )
        assertEquals(1, textState.selectionStart)
        assertEquals(4, textState.selectionEnd)

        val slider = tester.semanticsNodesByLabel("Volume").single()
        assertEquals(0.25f, slider.rangeInfo?.current ?: -1f, 0.001f)
        assertEquals(3, slider.rangeInfo?.steps)
        assertTrue(
            tester.performSemanticsAction(
                slider.id,
                PixelSemanticsAction.SET_PROGRESS,
                PixelSemanticsActionArguments(progress = 0.75f),
            ),
        )
        assertEquals(0.75f, draggedValue, 0.001f)
        assertEquals(0.75f, releasedValue, 0.001f)
        tester.dispose()
    }

    /** Scroll containers expose collection metadata, clipped item positions, and page-sized actions. */
    @Test
    fun listAndPagerExposeCollectionItemsAndSemanticScrolling() {
        val listController = PixelListController()
        val listState = listController.create()
        val pagerController = PixelPagerController()
        val pagerState = pagerController.create(pageCount = 3, axis = PixelAxis.HORIZONTAL)
        val tester = PixelTester()
        tester.pumpWidget(
            widget = Column(
                children = listOf(
                    SizedBox(
                        height = 12,
                        child = ListViewBuilder(
                            itemCount = 8,
                            itemBuilder = { index -> Text("ROW $index", key = "row-$index") },
                            itemExtent = 6,
                            state = listState,
                            controller = listController,
                            key = "list",
                        ),
                    ),
                    SizedBox(
                        height = 8,
                        child = PageView(
                            axis = PixelAxis.HORIZONTAL,
                            controller = pagerController,
                            state = pagerState,
                            pages = List(3) { index -> Text("PAGE $index", key = "page-$index") },
                            key = "pager",
                        ),
                    ),
                ),
            ),
            logicalWidth = 70,
            logicalHeight = 24,
        )

        val list = tester.semanticsNodes().single { node ->
            node.role == PixelSemanticRole.LIST && node.collectionInfo?.rowCount == 8
        }
        val visibleRows = tester.semanticsNodes().filter { node -> node.label.startsWith("ROW ") }
        assertTrue(visibleRows.isNotEmpty())
        assertTrue(visibleRows.all { node -> node.collectionItemInfo != null })
        assertTrue(visibleRows.all { node -> node.top >= list.top && node.top + node.height <= list.top + list.height })
        assertTrue(tester.performSemanticsAction(list.id, PixelSemanticsAction.SCROLL_FORWARD))
        assertTrue(listState.scrollOffsetPx > 0f)

        val pager = tester.semanticsNodes().single { node ->
            node.role == PixelSemanticRole.SCROLL_VIEW && node.collectionInfo?.columnCount == 3
        }
        assertEquals("1/3", pager.value)
        assertTrue(tester.performSemanticsAction(pager.id, PixelSemanticsAction.SCROLL_FORWARD))
        assertEquals(1, pagerState.currentPage)
        assertEquals(1, tester.semanticsNodesByLabel("PAGE 1").size)
        assertTrue(tester.semanticsNodesByLabel("PAGE 0").isEmpty())
        tester.dispose()
    }

    /** Keyed list identities survive insertion and reorder while item position metadata changes. */
    @Test
    fun keyedListItemsKeepSemanticIdsAcrossStructuralChanges() {
        /** Business ids are also used as Widget keys; labels are display-only. */
        val rows = ValueNotifier(listOf("alpha" to "ALPHA", "beta" to "BETA"))
        val listController = PixelListController()
        val listState = listController.create()
        val tester = PixelTester()
        tester.pumpWidget(
            widget = ValueListenableBuilder(rows) { _, currentRows ->
                SizedBox(
                    height = 20,
                    child = ListViewBuilder(
                        itemCount = currentRows.size,
                        itemBuilder = { index ->
                            val row = currentRows[index]
                            Text(row.second, key = row.first)
                        },
                        itemExtent = 6,
                        state = listState,
                        controller = listController,
                        key = "stable-list",
                    ),
                )
            },
            logicalWidth = 60,
            logicalHeight = 20,
        )
        val alphaId = tester.semanticsNodesByLabel("ALPHA").single().id
        val betaId = tester.semanticsNodesByLabel("BETA").single().id

        rows.value = listOf("new" to "NEW", "beta" to "BETA", "alpha" to "ALPHA")
        tester.pumpFrame(0)

        val alphaAfter = tester.semanticsNodesByLabel("ALPHA").single()
        val betaAfter = tester.semanticsNodesByLabel("BETA").single()
        assertEquals(alphaId, alphaAfter.id)
        assertEquals(betaId, betaAfter.id)
        assertEquals(2, alphaAfter.collectionItemInfo?.rowIndex)
        assertEquals(1, betaAfter.collectionItemInfo?.rowIndex)
        tester.dispose()
    }

    /** Eager lists and lazy grids attach exact collection coordinates to keyed child roots. */
    @Test
    fun eagerListAndGridBuilderExposeCollectionCoordinates() {
        val eagerController = PixelListController()
        val eagerState = eagerController.create()
        val gridController = PixelListController()
        val gridState = gridController.create()
        val tester = PixelTester()
        tester.pumpWidget(
            widget = Column(
                children = listOf(
                    SizedBox(
                        height = 12,
                        child = ListView(
                            items = List(3) { index -> Text("EAGER $index", key = "eager-$index") },
                            state = eagerState,
                            controller = eagerController,
                            key = "eager-list",
                        ),
                    ),
                    SizedBox(
                        height = 12,
                        child = GridViewBuilder(
                            itemCount = 6,
                            itemBuilder = { index -> Text("CELL $index", key = "cell-$index") },
                            cellWidth = 10,
                            cellHeight = 6,
                            state = gridState,
                            controller = gridController,
                            key = "grid",
                        ),
                    ),
                ),
            ),
            logicalWidth = 24,
            logicalHeight = 24,
        )
        tester.pumpFrame(0)

        val eagerItems = tester.semanticsNodes().filter { node -> node.label.startsWith("EAGER ") }
        assertTrue(eagerItems.isNotEmpty())
        assertTrue(
            eagerItems.all { node ->
                node.collectionItemInfo?.rowIndex == node.label.substringAfterLast(' ').toInt()
            },
        )
        val grid = tester.semanticsNodes().single { node ->
            node.role == PixelSemanticRole.LIST && node.collectionInfo?.columnCount == 2
        }
        assertEquals(3, grid.collectionInfo?.rowCount)
        val gridItems = tester.semanticsNodes().filter { node -> node.label.startsWith("CELL ") }
        assertEquals(
            listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1),
            gridItems.take(4).map { node ->
                val info = checkNotNull(node.collectionItemInfo)
                info.rowIndex to info.columnIndex
            },
        )
        tester.dispose()
    }

    /** Variable lazy semantics are clipped to the viewport and omit fully invisible cached rows. */
    @Test
    fun variableLazyListClipsPartiallyVisibleRows() {
        val controller = PixelListController()
        val state = controller.create(initialScrollOffsetPx = 3f)
        val tester = PixelTester()
        tester.pumpWidget(
            widget = SizedBox(
                height = 10,
                child = ListViewBuilder(
                    itemCount = 8,
                    itemBuilder = { index ->
                        Container(height = 7, child = Text("VAR $index", key = "var-$index"))
                    },
                    estimatedItemExtent = 7,
                    state = state,
                    controller = controller,
                    cacheExtent = 2,
                    key = "variable-list",
                ),
            ),
            logicalWidth = 50,
            logicalHeight = 10,
        )

        val list = tester.semanticsNodes().single { node -> node.role == PixelSemanticRole.LIST }
        val first = tester.semanticsNodesByLabel("VAR 0").single()
        assertEquals(list.top, first.top)
        assertTrue(first.height in 1 until 7)
        assertTrue(tester.semanticsNodesByLabel("VAR 3").isEmpty())
        assertEquals(0, first.collectionItemInfo?.rowIndex)
        tester.dispose()
    }

    /** Separated lazy lists attach item metadata only to real items, never separators. */
    @Test
    fun separatedLazyListKeepsSeparatorsOutsideCollectionItemMetadata() {
        val controller = PixelListController()
        val state = controller.create()
        val tester = PixelTester()
        tester.pumpWidget(
            widget = SizedBox(
                height = 18,
                child = ListViewSeparatedBuilder(
                    itemCount = 5,
                    itemBuilder = { index -> Text("ITEM $index", key = "item-$index") },
                    separatorBuilder = { index -> Text("SEP $index", key = "sep-$index") },
                    itemExtent = 7,
                    separatorExtent = 2,
                    state = state,
                    controller = controller,
                    key = "separated-list",
                ),
            ),
            logicalWidth = 50,
            logicalHeight = 18,
        )

        val itemNodes = tester.semanticsNodes().filter { node -> node.label.startsWith("ITEM ") }
        val separatorNodes = tester.semanticsNodes().filter { node -> node.label.startsWith("SEP ") }
        assertTrue(itemNodes.isNotEmpty())
        assertTrue(itemNodes.all { node -> node.collectionItemInfo != null })
        assertTrue(separatorNodes.isNotEmpty())
        assertTrue(separatorNodes.all { node -> node.collectionItemInfo == null })
        tester.dispose()
    }

    /** CustomScroll exports clipped visible sliver children and hides cached offscreen items. */
    @Test
    fun customScrollClipsVisibleSliverSemantics() {
        val controller = PixelListController()
        val state = controller.create(initialScrollOffsetPx = 4f)
        val tester = PixelTester()
        tester.pumpWidget(
            widget = SizedBox(
                height = 11,
                child = CustomScrollView(
                    slivers = listOf(
                        SliverListBuilder(
                            itemCount = 10,
                            itemBuilder = { index -> Text("SLIVER $index", key = "sliver-$index") },
                            itemExtent = 7,
                            cacheExtent = 2,
                        ),
                    ),
                    state = state,
                    controller = controller,
                    key = "custom-scroll",
                ),
            ),
            logicalWidth = 60,
            logicalHeight = 11,
        )

        val list = tester.semanticsNodes().single { node -> node.role == PixelSemanticRole.LIST }
        val first = tester.semanticsNodesByLabel("SLIVER 0").single()
        assertEquals(list.top, first.top)
        assertTrue(first.height in 1 until 7)
        assertTrue(tester.semanticsNodesByLabel("SLIVER 4").isEmpty())
        tester.dispose()
    }

    /** Dialog, Menu, Dropdown, Toast, and Snackbar expose transient-surface semantics and actions. */
    @Test
    fun overlayComponentsExposeDismissExpandSelectionAndLiveRegions() {
        var dismissed = 0
        var selected = 0
        var toggled = 0
        val tester = PixelTester()
        tester.pumpWidget(
            widget = Column(
                children = listOf(
                    Dialog(
                        title = Text("TITLE"),
                        content = Text("BODY"),
                        semanticLabel = "Delete dialog",
                        onDismissRequest = { dismissed += 1 },
                        modal = false,
                        key = "dialog",
                    ),
                    Menu(
                        items = listOf(
                            PixelMenuItem("COPY", onSelected = { selected += 1 }, selected = true, key = "copy"),
                            PixelMenuItem("COPY", onSelected = { selected += 10 }, key = "copy-2"),
                        ),
                        onDismissRequest = { dismissed += 10 },
                        modal = false,
                        key = "menu",
                    ),
                    Dropdown(
                        label = "Mode",
                        selectedText = "A",
                        expanded = false,
                        onToggle = { toggled += 1 },
                        items = listOf(PixelMenuItem("A", onSelected = {})),
                        key = "dropdown",
                    ),
                    Toast("Saved", key = "toast"),
                    Snackbar("Queued", key = "snackbar"),
                ),
            ),
            logicalWidth = 120,
            logicalHeight = 120,
        )

        val dialog = tester.semanticsNodesByLabel("Delete dialog").single()
        assertTrue(tester.performSemanticsAction(dialog.id, PixelSemanticsAction.DISMISS))
        assertEquals(1, dismissed)

        val menu = tester.semanticsNodes().single { node -> node.role == PixelSemanticRole.MENU }
        assertEquals(2, menu.collectionInfo?.rowCount)
        val copyItems = tester.semanticsNodesByLabel("COPY")
        assertEquals(2, copyItems.size)
        assertEquals(listOf(true, false), copyItems.map { node -> node.selected })
        assertTrue(tester.performSemanticsAction(copyItems[1].id, PixelSemanticsAction.CLICK))
        assertEquals(10, selected)
        assertTrue(tester.performSemanticsAction(menu.id, PixelSemanticsAction.DISMISS))
        assertEquals(11, dismissed)

        val dropdown = tester.semanticsNodesByLabel("Mode").single()
        assertEquals(false, dropdown.expanded)
        assertTrue(tester.performSemanticsAction(dropdown.id, PixelSemanticsAction.EXPAND))
        assertEquals(1, toggled)
        val liveNodes = tester.semanticsNodes().filter { node ->
            node.liveRegion == PixelSemanticsLiveRegion.POLITE
        }
        assertEquals(setOf("Saved", "Queued"), liveNodes.map { node -> node.label }.toSet())
        assertFalse(liveNodes.any { node -> node.label.isBlank() })
        assertNull(tester.semanticsNodesByLabel("Saved").single().checked)
        tester.dispose()
    }
}
