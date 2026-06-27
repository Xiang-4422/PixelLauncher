package com.purride.pixelui.foundation

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Checkbox
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.PixelFocusScrollTarget
import com.purride.pixelui.FocusScope
import com.purride.pixelui.FocusScopeNode
import com.purride.pixelui.FocusTraversalGroup
import com.purride.pixelui.GridFocusTraversalPolicy
import com.purride.pixelui.GridView
import com.purride.pixelui.ListView
import com.purride.pixelui.ListTile
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelFocusManager
import com.purride.pixelui.PixelKey
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.Switch
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.TextField
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelFocusTest {
    private val focusColor = PixelColor.fromRgb(255, 200, 0)

    @Test
    fun autofocusAndTabTraversalMovePrimaryFocus() {
        val first = FocusNode("first")
        val second = FocusNode("second")
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                FocusScope(
                    node = FocusScopeNode(),
                    child = Column(
                        children = listOf(
                            Focus(node = first, autofocus = true, child = Text("FIRST")),
                            Focus(node = second, child = Text("SECOND")),
                        ),
                    ),
                ),
                logicalWidth = 60,
                logicalHeight = 24,
            )

            assertTrue(first.isFocused)
            assertFalse(second.isFocused)
            assertTrue(tester.pressKey(PixelKey.TAB))
            assertFalse(first.isFocused)
            assertTrue(second.isFocused)
        } finally {
            tester.dispose()
        }
    }

    @Test
    fun focusedButtonExportsSemanticsAndDrawsFocusBorder() {
        val node = FocusNode("button")
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Focus(
                    node = node,
                    autofocus = true,
                    child = OutlinedButton(text = "OK", onPressed = { }),
                ),
                logicalWidth = 32,
                logicalHeight = 12,
            )

            val semantics = tester.dumpSemanticsTree()
            assertTrue(semantics.contains("BUTTON label=\"OK\" enabled=true focused=true"))
            assertTrue(tester.renderResult!!.buffer.pixels.any { it == focusColor.argb })
        } finally {
            tester.dispose()
        }
    }

    @Test
    fun focusedSelectionControlsDrawFocusBorder() {
        assertFocusedControlDrawsBorder(
            name = "Checkbox",
            child = Checkbox(checked = false, onChanged = { }, semanticLabel = "Sync"),
            expectedSemantics = "CHECKBOX label=\"Sync\" enabled=true focused=true",
        )
        assertFocusedControlDrawsBorder(
            name = "Switch",
            child = Switch(checked = true, onChanged = { }, semanticLabel = "Wifi"),
            expectedSemantics = "SWITCH label=\"Wifi\" enabled=true focused=true",
        )
        assertFocusedControlDrawsBorder(
            name = "ListTile",
            child = ListTile(title = Text("Tile"), onTap = { }, semanticLabel = "Open tile"),
            expectedSemantics = "BUTTON label=\"Open tile\" enabled=true focused=true",
        )
        assertFocusedControlDrawsBorder(
            name = "Tabs",
            child = Tabs(labels = listOf("A", "B"), selectedIndex = 1, onSelected = { }),
            expectedSemantics = "TAB label=\"B\" enabled=true focused=true",
        )
        assertFocusedControlDrawsBorder(
            name = "SegmentedControl",
            child = SegmentedControl(labels = listOf("ONE", "TWO"), selectedIndex = 1, onSelected = { }),
            expectedSemantics = "TAB label=\"TWO\" enabled=true focused=true",
        )
    }

    @Test
    fun focusedNodeHandlesEnterKeyBeforeTraversal() {
        val node = FocusNode("button")
        var handled = 0
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Focus(
                    node = node,
                    autofocus = true,
                    onKeyEvent = { event ->
                        if (event.key == PixelKey.ENTER) {
                            handled += 1
                            true
                        } else {
                            false
                        }
                    },
                    child = OutlinedButton(text = "OK", onPressed = { }),
                ),
                logicalWidth = 32,
                logicalHeight = 12,
            )

            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertEquals(1, handled)
        } finally {
            tester.dispose()
        }
    }

    @Test
    fun disabledFocusNodeIsSkipped() {
        val first = FocusNode("first")
        val disabled = FocusNode("disabled", canRequestFocus = false)
        val third = FocusNode("third")
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                FocusScope(
                    child = Column(
                        children = listOf(
                            Focus(node = first, autofocus = true, child = Text("FIRST")),
                            Focus(node = disabled, canRequestFocus = false, child = Text("DISABLED")),
                            Focus(node = third, child = Text("THIRD")),
                        ),
                    ),
                ),
                logicalWidth = 72,
                logicalHeight = 36,
            )

            assertTrue(PixelFocusManager.dispatchKeyEvent(com.purride.pixelui.PixelKeyEvent(PixelKey.TAB)))
            assertTrue(third.isFocused)
            assertFalse(disabled.isFocused)
        } finally {
            tester.dispose()
        }
    }

    @Test
    fun focusTraversalGroupAppliesLocalPolicy() {
        val first = FocusNode("first")
        val second = FocusNode("second")
        val third = FocusNode("third")
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                FocusTraversalGroup(
                    traversalPolicy = GridFocusTraversalPolicy(columns = 2),
                    child = Column(
                        children = listOf(
                            Focus(node = first, autofocus = true, child = Text("FIRST")),
                            Focus(node = second, child = Text("SECOND")),
                            Focus(node = third, child = Text("THIRD")),
                        ),
                    ),
                ),
                logicalWidth = 72,
                logicalHeight = 36,
            )

            assertTrue(first.isFocused)
            assertTrue(tester.pressKey(PixelKey.ARROW_RIGHT))
            assertTrue(second.isFocused)
            assertFalse(tester.pressKey(PixelKey.ARROW_RIGHT))
            assertTrue(second.isFocused)
        } finally {
            tester.dispose()
        }
    }

    @Test
    fun textInputNextActionTraversesFocusScope() {
        val first = FocusNode("field")
        val second = FocusNode("next")
        val controller = PixelTextFieldController()
        val state = controller.create()
        var submitted = ""
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                FocusScope(
                    child = Column(
                        children = listOf(
                            Focus(
                                node = first,
                                autofocus = true,
                                child = TextField(
                                    state = state,
                                    controller = controller,
                                    placeholder = "NAME",
                                    textInputAction = PixelTextInputAction.NEXT,
                                    onSubmitted = { submitted = it },
                                ),
                            ),
                            Focus(node = second, child = Text("NEXT")),
                        ),
                    ),
                ),
                logicalWidth = 72,
                logicalHeight = 28,
            )

            tester.enterText(find.byText("NAME"), "Ada")
            tester.submitTextInput()

            assertEquals("Ada", submitted)
            assertTrue(second.isFocused)
        } finally {
            tester.dispose()
        }
    }

    @Test
    fun gridTraversalPolicyMovesByColumnsForArrowKeys() {
        val nodes = List(6) { index -> FocusNode("cell-$index") }
        val listState = PixelListState()
        val listController = PixelListController()
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                FocusScope(
                    node = FocusScopeNode(),
                    traversalPolicy = GridFocusTraversalPolicy(columns = 2),
                    child = GridView(
                        items = nodes.mapIndexed { index, node ->
                            Focus(
                                node = node,
                                autofocus = index == 0,
                                child = Text("CELL $index"),
                            )
                        },
                        cellWidth = 10,
                        cellHeight = 4,
                        state = listState,
                        controller = listController,
                        spacing = 1,
                        runSpacing = 1,
                    ),
                ),
                logicalWidth = 22,
                logicalHeight = 18,
            )

            assertTrue(nodes[0].isFocused)
            assertTrue(tester.pressKey(PixelKey.ARROW_RIGHT))
            assertTrue(nodes[1].isFocused)
            assertTrue(tester.pressKey(PixelKey.ARROW_DOWN))
            assertTrue(nodes[3].isFocused)
            assertTrue(tester.pressKey(PixelKey.ARROW_LEFT))
            assertTrue(nodes[2].isFocused)
            assertTrue(tester.pressKey(PixelKey.ARROW_UP))
            assertTrue(nodes[0].isFocused)
        } finally {
            tester.dispose()
        }
    }

    @Test
    fun focusedScrollTargetKeepsListItemVisible() {
        val nodes = List(8) { index -> FocusNode("row-$index") }
        val listController = PixelListController()
        val listState = listController.create()
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                FocusScope(
                    node = FocusScopeNode(),
                    child = ListView(
                        items = nodes.mapIndexed { index, node ->
                            Focus(
                                node = node,
                                autofocus = index == 5,
                                scrollTarget = PixelFocusScrollTarget(
                                    state = listState,
                                    controller = listController,
                                    itemIndex = index,
                                ),
                                child = Container(height = 6, child = Text("ROW $index")),
                            )
                        },
                        state = listState,
                        controller = listController,
                        spacing = 1,
                    ),
                ),
                logicalWidth = 72,
                logicalHeight = 18,
            )
            tester.pumpFrame(16)

            assertTrue(nodes[5].isFocused)
            assertTrue("focused row should be scrolled into view", listState.scrollOffsetPx > 0f)
            val offsetAfterFirstEnsure = listState.scrollOffsetPx

            tester.pumpFrame(16)

            assertEquals(offsetAfterFirstEnsure, listState.scrollOffsetPx, 0.001f)
        } finally {
            tester.dispose()
        }
    }

    private fun assertFocusedControlDrawsBorder(name: String, child: Widget, expectedSemantics: String) {
        val node = FocusNode("control")
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Focus(node = node, autofocus = true, child = child),
                logicalWidth = 48,
                logicalHeight = 18,
            )

            assertTrue("$name should draw focus border", tester.renderResult!!.buffer.pixels.any { it == focusColor.argb })
            assertTrue("$name should export focused semantics", tester.dumpSemanticsTree().contains(expectedSemantics))
        } finally {
            tester.dispose()
        }
    }
}
