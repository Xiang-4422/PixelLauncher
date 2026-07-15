package com.purride.pixelui.widgets

import com.purride.pixelui.Checkbox
import com.purride.pixelui.Column
import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.ListTile
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelKey
import com.purride.pixelui.RefreshIndicator
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.Slider
import com.purride.pixelui.Switch
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextField
import com.purride.pixelui.ValueAdjuster
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelRefreshIndicatorController
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies standard controls work without caller-authored Focus wrappers or duplicate actions. */
class StandardComponentKeyboardInputTest {
    /** Buttons retain reading order, skip disabled peers, and share semantics/key activation. */
    @Test
    fun buttonsAutomaticallyFocusAndActivateFromEnterSpace() {
        /** Number of activations observed through every input adapter. */
        var activations = 0
        /** Off-screen runtime that owns this test's isolated focus tree. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Column(
                    children = listOf(
                        OutlinedButton(text = "FIRST", onPressed = { activations += 1 }),
                        Focus(
                            node = FocusNode(debugLabel = "disabled-explicit-focus"),
                            child = OutlinedButton(
                                text = "DISABLED",
                                onPressed = { activations += 100 },
                                enabled = false,
                            ),
                        ),
                        TextButton(text = "SECOND", onPressed = { activations += 10 }),
                    ),
                ),
                logicalWidth = 72,
                logicalHeight = 36,
            )

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("FIRST").single().focused)
            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertEquals(1, activations)

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertFalse(tester.semanticsNodesByLabel("DISABLED").single().focused)
            assertTrue(tester.semanticsNodesByLabel("SECOND").single().focused)
            assertTrue(tester.pressKey(PixelKey.SPACE))
            assertEquals(11, activations)
        } finally {
            tester.dispose()
        }
    }

    /** Text fields join traversal automatically while a disabled field remains outside the order. */
    @Test
    fun textFieldsAutomaticallyFocusAndSkipDisabledPeers() {
        /** Controller shared by the enabled field and its retained text state. */
        val enabledController = PixelTextFieldController()
        /** Editable state that should receive focus through keyboard traversal. */
        val enabledState = enabledController.create()
        /** Controller owned by the disabled field. */
        val disabledController = PixelTextFieldController()
        /** Disabled state that must never receive focus through traversal. */
        val disabledState = disabledController.create()
        /** Off-screen runtime that owns this test's isolated focus tree. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Column(
                    children = listOf(
                        TextField(
                            state = enabledState,
                            controller = enabledController,
                            placeholder = "NAME",
                            semanticLabel = "Name",
                        ),
                        Focus(
                            node = FocusNode(debugLabel = "disabled-field-explicit-focus"),
                            child = TextField(
                                state = disabledState,
                                controller = disabledController,
                                placeholder = "LOCKED",
                                semanticLabel = "Locked",
                                enabled = false,
                            ),
                        ),
                        OutlinedButton(text = "NEXT", onPressed = {}),
                    ),
                ),
                logicalWidth = 72,
                logicalHeight = 40,
            )

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(enabledState.isFocused)
            assertTrue(tester.semanticsNodesByLabel("Name").single().focused)

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertFalse(disabledState.isFocused)
            assertFalse(tester.semanticsNodesByLabel("Locked").single().focused)
            assertTrue(tester.semanticsNodesByLabel("NEXT").single().focused)
        } finally {
            tester.dispose()
        }
    }

    /** Selection controls and menu-row primitives use the same callbacks for pointer and key input. */
    @Test
    fun selectionControlsAndListTileActivateFromKeyboard() {
        /** Ordered callback trace proving each focused control invokes exactly one action. */
        val actions = mutableListOf<String>()
        /** Off-screen runtime that owns this test's isolated focus tree. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Column(
                    children = listOf(
                        Checkbox(
                            checked = false,
                            onChanged = { checked -> actions += "checkbox:$checked" },
                            semanticLabel = "Sync",
                        ),
                        Switch(
                            checked = true,
                            onChanged = { checked -> actions += "switch:$checked" },
                            semanticLabel = "Wifi",
                        ),
                        ListTile(
                            title = Text("Open"),
                            onTap = { actions += "tile" },
                            semanticLabel = "Open item",
                        ),
                    ),
                ),
                logicalWidth = 72,
                logicalHeight = 40,
            )

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.pressKey(PixelKey.SPACE))
            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.pressKey(PixelKey.ENTER))

            assertEquals(listOf("checkbox:true", "switch:false", "tile"), actions)
        } finally {
            tester.dispose()
        }
    }

    /** Slider arrows commit one discrete value through the same callback used by set-progress. */
    @Test
    fun sliderArrowKeysUseDeclaredSemanticStep() {
        /** Values reported continuously before their matching release callbacks. */
        val draggedValues = mutableListOf<Float>()
        /** Values committed by keyboard release semantics. */
        val releasedValues = mutableListOf<Float>()
        /** Off-screen runtime that owns this test's isolated focus tree. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Slider(
                    value = 0.5f,
                    semanticSteps = 3,
                    onDrag = draggedValues::add,
                    onRelease = releasedValues::add,
                ),
                logicalWidth = 48,
                logicalHeight = 12,
            )

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.pressKey(PixelKey.ARROW_RIGHT))
            assertEquals(listOf(0.75f), draggedValues)
            assertEquals(listOf(0.75f), releasedValues)

            assertTrue(tester.pressKey(PixelKey.ARROW_LEFT))
            assertEquals(listOf(0.75f, 0.25f), draggedValues)
            assertEquals(listOf(0.75f, 0.25f), releasedValues)
        } finally {
            tester.dispose()
        }
    }

    /** Value adjusters form one tab stop, consume directional changes, and skip inert peers. */
    @Test
    fun valueAdjusterUsesDirectionalKeysAndSkipsInertPeer() {
        /** Ordered changes proving horizontal and vertical DPAD directions share one action route. */
        val changes = mutableListOf<String>()
        /** Off-screen runtime that owns this test's isolated focus tree. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Column(
                    children = listOf(
                        ValueAdjuster(
                            valueText = "5",
                            onDecrease = { changes += "decrease" },
                            onIncrease = { changes += "increase" },
                        ),
                        ValueAdjuster(
                            valueText = "0",
                            onDecrease = null,
                            onIncrease = null,
                            label = "Unavailable",
                        ),
                        OutlinedButton(text = "NEXT", onPressed = {}),
                    ),
                ),
                logicalWidth = 80,
                logicalHeight = 48,
            )

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertEquals(1, tester.semanticsNodes().count { node -> node.focused })
            assertTrue(tester.semanticsNodesByLabel("ValueAdjuster").single().focused)
            assertTrue(tester.semanticsNodesByLabel("Decrease").none { node -> node.focused })
            assertTrue(tester.semanticsNodesByLabel("Increase").none { node -> node.focused })
            assertTrue(tester.pressKey(PixelKey.ARROW_LEFT))
            assertTrue(tester.pressKey(PixelKey.ARROW_UP))
            assertEquals(listOf("decrease", "increase"), changes)

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("NEXT").single().focused)
        } finally {
            tester.dispose()
        }
    }

    /** Tabs and segmented controls are one tab stop with cyclic horizontal selection. */
    @Test
    fun compoundSelectorsUseArrowKeysAndActivation() {
        /** Last requested Tabs selection. */
        var tabSelection = -1
        /** Last requested SegmentedControl selection. */
        var segmentSelection = -1
        /** Off-screen runtime that owns this test's isolated focus tree. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Column(
                    children = listOf(
                        Tabs(
                            labels = listOf("A", "B", "C"),
                            selectedIndex = 1,
                            onSelected = { tabSelection = it },
                        ),
                        SegmentedControl(
                            labels = listOf("ONE", "TWO", "THREE"),
                            selectedIndex = 0,
                            onSelected = { segmentSelection = it },
                        ),
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 28,
            )

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.pressKey(PixelKey.ARROW_RIGHT))
            assertEquals(2, tabSelection)
            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertEquals(1, tabSelection)

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.pressKey(PixelKey.ARROW_LEFT))
            assertEquals(2, segmentSelection)
            assertTrue(tester.pressKey(PixelKey.SPACE))
            assertEquals(0, segmentSelection)
        } finally {
            tester.dispose()
        }
    }

    /** Disabled Slider, Tabs, and SegmentedControl export no actions and remain outside traversal. */
    @Test
    fun disabledCompoundControlsAreSkipped() {
        /** Off-screen runtime that owns this test's isolated focus tree. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Column(
                    children = listOf(
                        Slider(
                            value = 0.5f,
                            semanticLabel = "Locked slider",
                            enabled = false,
                        ),
                        Tabs(
                            labels = listOf("LOCKED A", "LOCKED B"),
                            selectedIndex = 0,
                            onSelected = {},
                            enabled = false,
                        ),
                        SegmentedControl(
                            labels = listOf("LOCKED C", "LOCKED D"),
                            selectedIndex = 0,
                            onSelected = {},
                            enabled = false,
                        ),
                        OutlinedButton(text = "NEXT", onPressed = {}),
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 50,
            )

            /** Disabled Slider node used to verify Switch Access receives no value action. */
            val lockedSlider = tester.semanticsNodesByLabel("Locked slider").single()
            assertFalse(lockedSlider.enabled)
            assertFalse(PixelSemanticsAction.SET_PROGRESS in lockedSlider.actions)
            assertTrue(
                listOf("LOCKED A", "LOCKED B", "LOCKED C", "LOCKED D").all { label ->
                    /** Disabled selector item whose click action must not be advertised. */
                    val node = tester.semanticsNodesByLabel(label).single()
                    !node.enabled && PixelSemanticsAction.CLICK !in node.actions
                },
            )
            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("NEXT").single().focused)
        } finally {
            tester.dispose()
        }
    }

    /** RefreshIndicator shares one guarded action across keyboard and accessibility activation. */
    @Test
    fun refreshIndicatorActivatesFromKeyboardOncePerRefreshLifecycle() {
        /** Controller and state shared by pointer, keyboard, and semantic refresh routes. */
        val controller = PixelRefreshIndicatorController()
        /** Refresh lifecycle state that prevents duplicate activation while already refreshing. */
        val state = controller.create()
        /** Number of refresh starts observed through the shared action. */
        var refreshCount = 0
        /** Off-screen runtime that owns this test's isolated focus tree. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                RefreshIndicator(
                    child = Text("CONTENT"),
                    state = state,
                    controller = controller,
                    onRefresh = { refreshCount += 1 },
                    semanticLabel = "Reload feed",
                ),
                logicalWidth = 72,
                logicalHeight = 24,
            )

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("Reload feed").single().focused)
            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertTrue(state.isRefreshing)
            assertEquals(1, refreshCount)
            assertFalse(tester.pressKey(PixelKey.SPACE))
            assertEquals(1, refreshCount)

            controller.completeRefresh(state)
            tester.pumpFrame(0)
            /** Refreshed semantic node used by the Switch Access-compatible click route. */
            val refreshNode = tester.semanticsNodesByLabel("Reload feed").single()
            assertTrue(tester.performSemanticsAction(refreshNode.id, PixelSemanticsAction.CLICK))
            assertEquals(2, refreshCount)
        } finally {
            tester.dispose()
        }
    }

    /** Caller shortcuts remain higher priority than a standard component's activation fallback. */
    @Test
    fun explicitFocusHandlerRunsBeforeButtonDefaultAction() {
        /** Caller-owned node retained across the explicit compatibility wrapper. */
        val node = FocusNode(debugLabel = "custom-button")
        /** Number of events consumed by the caller shortcut. */
        var shortcutCount = 0
        /** Number of events reaching the button fallback. */
        var buttonCount = 0
        /** Off-screen runtime that owns this test's isolated focus tree. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Focus(
                    node = node,
                    autofocus = true,
                    onKeyEvent = { event ->
                        if (event.key == PixelKey.ENTER) {
                            shortcutCount += 1
                            true
                        } else {
                            false
                        }
                    },
                    child = OutlinedButton(text = "SAVE", onPressed = { buttonCount += 1 }),
                ),
                logicalWidth = 48,
                logicalHeight = 14,
            )

            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertEquals(1, shortcutCount)
            assertEquals(0, buttonCount)

            assertTrue(tester.pressKey(PixelKey.SPACE))
            assertEquals(1, shortcutCount)
            assertEquals(1, buttonCount)
        } finally {
            tester.dispose()
        }
    }


    /** One ancestor Focus never collapses multiple descendant controls into a shared tab stop. */
    @Test
    fun ancestorFocusKeepsDescendantControlsIndependent() {
        /** Ancestor node claimed by only the first automatic descendant control. */
        val ancestorNode = FocusNode(debugLabel = "ancestor-shortcut")
        /** Ordered activations proving the disabled middle control neither blocks nor steals actions. */
        val actions = mutableListOf<String>()
        /** Ancestor shortcut count proving key bubbling from a separately focused descendant. */
        var shortcutCount = 0
        /** Off-screen runtime that owns this test's isolated focus tree. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Focus(
                    node = ancestorNode,
                    onKeyEvent = { event ->
                        if (event.key == PixelKey.ARROW_UP) {
                            shortcutCount += 1
                            true
                        } else {
                            false
                        }
                    },
                    child = Column(
                        children = listOf(
                            OutlinedButton(text = "FIRST", onPressed = { actions += "first" }),
                            OutlinedButton(
                                text = "DISABLED",
                                onPressed = { actions += "disabled" },
                                enabled = false,
                            ),
                            OutlinedButton(text = "LAST", onPressed = { actions += "last" }),
                        ),
                    ),
                ),
                logicalWidth = 72,
                logicalHeight = 40,
            )

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("FIRST").single().focused)
            assertFalse(tester.semanticsNodesByLabel("LAST").single().focused)
            assertTrue(tester.pressKey(PixelKey.ENTER))

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertFalse(tester.semanticsNodesByLabel("DISABLED").single().focused)
            assertTrue(tester.semanticsNodesByLabel("LAST").single().focused)
            assertFalse(tester.semanticsNodesByLabel("FIRST").single().focused)
            assertTrue(tester.pressKey(PixelKey.ARROW_UP))
            assertEquals(1, shortcutCount)
            assertTrue(tester.semanticsNodesByLabel("LAST").single().focused)
            assertTrue(tester.pressKey(PixelKey.SPACE))
            assertEquals(listOf("first", "last"), actions)
        } finally {
            tester.dispose()
        }
    }

    /** Multiple TextFields below one ancestor Focus retain distinct logical and IME focus state. */
    @Test
    fun ancestorFocusKeepsTextFieldsIndependent() {
        /** Ancestor compatibility node available to at most one automatic field. */
        val ancestorNode = FocusNode(debugLabel = "form-shortcuts")
        /** Controller and state owned by the first editable field. */
        val firstController = PixelTextFieldController()
        /** First editable state used to observe traversal focus. */
        val firstState = firstController.create()
        /** Controller and state owned by the second editable field. */
        val secondController = PixelTextFieldController()
        /** Second editable state used to reject shared-node focus. */
        val secondState = secondController.create()
        /** Off-screen runtime that owns this test's isolated focus tree. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Focus(
                    node = ancestorNode,
                    child = Column(
                        children = listOf(
                            TextField(
                                state = firstState,
                                controller = firstController,
                                semanticLabel = "First field",
                            ),
                            TextField(
                                state = secondState,
                                controller = secondController,
                                semanticLabel = "Second field",
                            ),
                        ),
                    ),
                ),
                logicalWidth = 72,
                logicalHeight = 30,
            )

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(firstState.isFocused)
            assertFalse(secondState.isFocused)

            assertTrue(tester.pressKey(PixelKey.TAB))
            assertFalse(firstState.isFocused)
            assertTrue(secondState.isFocused)
            assertFalse(tester.semanticsNodesByLabel("First field").single().focused)
            assertTrue(tester.semanticsNodesByLabel("Second field").single().focused)
        } finally {
            tester.dispose()
        }
    }
}
