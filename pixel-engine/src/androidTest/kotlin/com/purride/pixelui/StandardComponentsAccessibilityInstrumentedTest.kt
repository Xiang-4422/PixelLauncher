package com.purride.pixelui

import android.app.UiAutomation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.purride.pixelui.internal.host.PixelAccessibilityNodeSnapshot
import com.purride.pixelui.internal.host.PixelAccessibilityTreeSnapshot
import com.purride.pixelui.internal.host.PixelHostAccessibilityNodeProvider
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelTextFieldController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.ArrayDeque

/** Android acceptance coverage for semantics emitted by the public standard components. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
@Suppress("DEPRECATION")
class StandardComponentsAccessibilityInstrumentedTest {
    /**
     * Mounts every standard component through a real Host and verifies its Android contract.
     */
    @Test
    fun standardComponentsExposeClassesStatesRangesCollectionsBoundsAndExecutableActions() {
        /** Stateful fixture that starts with only base controls and a collapsed Dropdown. */
        val fixture = StandardComponentsFixture()

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Actual Host attached to the Activity window. */
                val host = activity.hostView
                // Immediate motion makes modal semantics deterministic without waiting on wall-clock frames.
                host.motionSettingsOverride = PixelMotionSettings(animatorDurationScale = 0f)
                host.setContent(fixture::build)
                renderSynchronously(host)

                /** Provider created by the actual Host rather than a synthetic mapper. */
                val provider = host.accessibilityProvider()
                /** Base virtual tree generated before any modal semantic boundary is inserted. */
                val baseTree = provider.snapshotForTesting()

                /** Merged OutlinedButton node; its visual Text descendant must be excluded. */
                val saveButton = baseTree.requireNode(label = SAVE_LABEL, role = PixelSemanticRole.BUTTON)
                /** Every semantic node that could independently announce the button text. */
                val saveMatches = baseTree.nodes.filter { snapshot ->
                    snapshot.node.label == SAVE_LABEL || snapshot.node.value == SAVE_LABEL
                }
                assertEquals(1, saveMatches.size)
                assertTrue(saveButton.childVirtualViewIds.isEmpty())
                /** Android representation of the merged enabled button. */
                val saveInfo = provider.requireInfo(saveButton)
                assertEquals("android.widget.Button", saveInfo.className.toString())
                assertTrue(saveInfo.isClickable)
                assertTrue(AccessibilityNodeInfo.ACTION_CLICK in saveInfo.actionIds())

                /** Disabled OutlinedButton whose callback must not leak into Android actions. */
                val disabledButton = baseTree.requireNode(label = DISABLED_LABEL, role = PixelSemanticRole.BUTTON)
                /** Android representation used to inspect disabled state and actions. */
                val disabledInfo = provider.requireInfo(disabledButton)
                assertFalse(disabledInfo.isEnabled)
                assertFalse(disabledInfo.isClickable)
                assertFalse(AccessibilityNodeInfo.ACTION_CLICK in disabledInfo.actionIds())

                /** Checked Checkbox mapped to Android checkable state and click action. */
                val checkbox = baseTree.requireNode(label = CHECKBOX_LABEL, role = PixelSemanticRole.CHECKBOX)
                /** Android representation of the checked Checkbox. */
                val checkboxInfo = provider.requireInfo(checkbox)
                assertEquals("android.widget.CheckBox", checkboxInfo.className.toString())
                assertTrue(checkboxInfo.isCheckable)
                assertTrue(checkboxInfo.isChecked)
                assertTrue(AccessibilityNodeInfo.ACTION_CLICK in checkboxInfo.actionIds())

                /** Unchecked Switch mapped independently from its spoken label. */
                val switch = baseTree.requireNode(label = SWITCH_LABEL, role = PixelSemanticRole.SWITCH)
                /** Android representation of the unchecked Switch. */
                val switchInfo = provider.requireInfo(switch)
                assertEquals("android.widget.Switch", switchInfo.className.toString())
                assertTrue(switchInfo.isCheckable)
                assertFalse(switchInfo.isChecked)
                assertTrue(AccessibilityNodeInfo.ACTION_CLICK in switchInfo.actionIds())

                /** Selected tab whose selection does not depend on label mutation. */
                val selectedTab = baseTree.requireNode(label = SELECTED_TAB_LABEL, role = PixelSemanticRole.TAB)
                /** Android representation of the selected Tab. */
                val selectedTabInfo = provider.requireInfo(selectedTab)
                assertEquals("android.widget.Button", selectedTabInfo.className.toString())
                assertTrue(selectedTabInfo.isSelected)
                assertTrue(AccessibilityNodeInfo.ACTION_CLICK in selectedTabInfo.actionIds())

                /** Editable TextField with typed text and selection actions. */
                val textField = baseTree.requireNode(label = TEXT_FIELD_LABEL, role = PixelSemanticRole.TEXT_FIELD)
                /** Android representation of the editable TextField. */
                val textFieldInfo = provider.requireInfo(textField)
                assertEquals("android.widget.EditText", textFieldInfo.className.toString())
                assertEquals(INITIAL_TEXT, textFieldInfo.text.toString())
                assertTrue(textFieldInfo.isEditable)
                assertTrue(AccessibilityNodeInfo.ACTION_SET_TEXT in textFieldInfo.actionIds())
                assertTrue(AccessibilityNodeInfo.ACTION_SET_SELECTION in textFieldInfo.actionIds())

                /** Slider carrying structured range state and the Android progress action. */
                val slider = baseTree.requireNode(label = SLIDER_LABEL, role = PixelSemanticRole.SLIDER)
                /** Android representation of the Slider range. */
                val sliderInfo = provider.requireInfo(slider)
                assertEquals("android.widget.SeekBar", sliderInfo.className.toString())
                assertNotNull(sliderInfo.rangeInfo)
                assertEquals(INITIAL_SLIDER_VALUE, sliderInfo.rangeInfo.current, 0f)
                assertEquals(0f, sliderInfo.rangeInfo.min, 0f)
                assertEquals(1f, sliderInfo.rangeInfo.max, 0f)
                assertTrue(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id in sliderInfo.actionIds(),
                )

                /** ListView container exposing collection metadata and bidirectional actions. */
                val list = baseTree.nodes.single { snapshot -> snapshot.node.role == PixelSemanticRole.LIST }
                /** Android representation of the ListView collection. */
                val listInfo = provider.requireInfo(list)
                assertEquals("android.widget.ListView", listInfo.className.toString())
                assertNotNull(listInfo.collectionInfo)
                assertEquals(LIST_ITEM_COUNT, listInfo.collectionInfo.rowCount)
                assertEquals(1, listInfo.collectionInfo.columnCount)
                assertTrue(listInfo.isScrollable)
                assertTrue(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD in listInfo.actionIds())
                assertTrue(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD in listInfo.actionIds())

                /** Collapsed Dropdown anchor carrying its current value and expand action. */
                val dropdown = baseTree.requireNode(label = DROPDOWN_LABEL, role = PixelSemanticRole.BUTTON)
                /** Android representation of the collapsed Dropdown anchor. */
                val dropdownInfo = provider.requireInfo(dropdown)
                assertEquals("android.widget.Button", dropdownInfo.className.toString())
                assertTrue(AccessibilityNodeInfo.ACTION_CLICK in dropdownInfo.actionIds())
                assertTrue(AccessibilityNodeInfo.ACTION_EXPAND in dropdownInfo.actionIds())
                assertFalse(AccessibilityNodeInfo.ACTION_COLLAPSE in dropdownInfo.actionIds())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    assertEquals(DROPDOWN_VALUE, dropdownInfo.stateDescription.toString())
                }

                /** Parent-relative bounds returned for a top-level semantic node. */
                val boundsInParent = Rect()
                /** Screen-space bounds returned after applying the Host window offset. */
                val boundsInScreen = Rect()
                saveInfo.getBoundsInParent(boundsInParent)
                saveInfo.getBoundsInScreen(boundsInScreen)
                assertTrue(boundsInParent.width() > 0)
                assertTrue(boundsInParent.height() > 0)
                /** Actual screen position of the Host used to verify coordinate conversion. */
                val hostLocation = IntArray(2)
                host.getLocationOnScreen(hostLocation)
                assertEquals(boundsInParent.left + hostLocation[0], boundsInScreen.left)
                assertEquals(boundsInParent.top + hostLocation[1], boundsInScreen.top)
                assertEquals(boundsInParent.right + hostLocation[0], boundsInScreen.right)
                assertEquals(boundsInParent.bottom + hostLocation[1], boundsInScreen.bottom)

                /** List offset before invoking the advertised backward action. */
                val offsetBeforeBackwardAction = fixture.listState.scrollOffsetPx
                assertTrue(offsetBeforeBackwardAction > 0f)
                assertTrue(
                    provider.performAction(
                        list.virtualViewId,
                        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
                        null,
                    ),
                )
                assertTrue(fixture.listState.scrollOffsetPx < offsetBeforeBackwardAction)

                /** Inserted standalone Menu is the only modal semantic subtree in this frame. */
                fixture.showMenu = true
                host.invalidate()
                renderSynchronously(host)
                /** Modal Menu snapshot used to verify background semantic isolation. */
                val menuTree = provider.snapshotForTesting()
                assertFalse(menuTree.containsNodeWithLabel(SAVE_LABEL))
                /** Actual Menu collection node with an executable dismiss action. */
                val menu = menuTree.requireNode(label = MENU_LABEL, role = PixelSemanticRole.MENU)
                /** Android representation of the Menu collection. */
                val menuInfo = provider.requireInfo(menu)
                assertEquals("android.widget.ListView", menuInfo.className.toString())
                assertNotNull(menuInfo.collectionInfo)
                assertEquals(MENU_ITEM_COUNT, menuInfo.collectionInfo.rowCount)
                assertTrue(AccessibilityNodeInfo.ACTION_DISMISS in menuInfo.actionIds())
                assertTrue(
                    provider.performAction(menu.virtualViewId, AccessibilityNodeInfo.ACTION_DISMISS, null),
                )
                assertFalse(fixture.showMenu)
                host.invalidate()
                renderSynchronously(host)

                /** Inserted standalone Dialog follows Menu removal and owns the next modal snapshot. */
                fixture.showDialog = true
                host.invalidate()
                renderSynchronously(host)
                /** Modal Dialog snapshot used to verify background semantic isolation. */
                val dialogTree = provider.snapshotForTesting()
                assertFalse(dialogTree.containsNodeWithLabel(SAVE_LABEL))
                /** Actual Dialog window node with an executable dismiss action. */
                val dialog = dialogTree.requireNode(label = DIALOG_LABEL, role = PixelSemanticRole.DIALOG)
                /** Android representation of the Dialog pane. */
                val dialogInfo = provider.requireInfo(dialog)
                assertEquals("android.app.Dialog", dialogInfo.className.toString())
                assertTrue(AccessibilityNodeInfo.ACTION_DISMISS in dialogInfo.actionIds())
                assertTrue(
                    provider.performAction(dialog.virtualViewId, AccessibilityNodeInfo.ACTION_DISMISS, null),
                )
                assertFalse(fixture.showDialog)
            }
        }
    }

    /** Verifies that real Dialog and Menu insertion and removal dispatch window-state events. */
    @Test
    fun dialogAndMenuInsertionAndRemovalEmitWindowStateEvents() {
        /** Fixture starts without window-like semantic nodes. */
        val fixture = StandardComponentsFixture().apply {
            showDialog = false
            showMenu = false
            dropdownExpanded = false
        }

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Actual Activity-attached Host under observation. */
                val host = activity.hostView
                // Immediate motion isolates window-event assertions from animation frame timing.
                host.motionSettingsOverride = PixelMotionSettings(animatorDurationScale = 0f)
                /** Host-owned provider whose test observer receives emitted Android events. */
                val provider = host.accessibilityProvider()
                /** Immutable copies of event type and Android class captured before framework reuse. */
                val observedEvents = mutableListOf<ObservedAccessibilityEvent>()
                provider.eventObserverForTesting = { event ->
                    observedEvents += ObservedAccessibilityEvent(
                        type = event.eventType,
                        className = event.className?.toString(),
                    )
                }
                host.setContent(fixture::build)
                renderSynchronously(host)

                observedEvents.clear()
                fixture.showDialog = true
                host.invalidate()
                renderSynchronously(host)
                assertTrue(observedEvents.containsWindowEventFor("android.app.Dialog"))

                observedEvents.clear()
                fixture.showDialog = false
                host.invalidate()
                renderSynchronously(host)
                assertTrue(observedEvents.containsWindowEventFor("android.app.Dialog"))

                observedEvents.clear()
                fixture.showMenu = true
                host.invalidate()
                renderSynchronously(host)
                assertTrue(observedEvents.containsWindowEventFor("android.widget.ListView"))

                observedEvents.clear()
                fixture.showMenu = false
                host.invalidate()
                renderSynchronously(host)
                assertTrue(observedEvents.containsWindowEventFor("android.widget.ListView"))
            }
        }
    }

    /**
     * Completes the standard control workflow through Android's public accessibility connection.
     *
     * [UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES] keeps a concurrently enabled
     * TalkBack service bound, so the same test is reusable as a real-service acceptance run while
     * remaining a deterministic bridge test on devices that do not install TalkBack.
     */
    @Test
    fun uiAutomationCompletesControlsAndKeepsLogicalFocusAcrossReorder() {
        /** Instrumentation that owns the platform accessibility automation connection. */
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        /** Automation client that intentionally leaves other accessibility services running. */
        val automation = instrumentation.getUiAutomation(
            UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES,
        )
        /** Stateful standard-component tree starting from a collapsed, background-visible Dropdown. */
        val fixture = StandardComponentsFixture()
        /** Whether a real touch-exploration service should receive injected physical gestures. */
        val touchExplorationEnabled =
            instrumentation.targetContext.accessibilityManager().isTouchExplorationEnabled
        /** Latest internal labels captured when the controlled Dropdown becomes modal. */
        var dropdownModalLabels: List<String> = emptyList()

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Real Activity-attached Host exported through Android's virtual-node connection. */
                val host = activity.hostView
                // Immediate motion exposes each logically opened modal in the same rendered frame.
                host.motionSettingsOverride = PixelMotionSettings(animatorDurationScale = 0f)
                host.setContent(fixture::build)
                renderSynchronously(host)
            }
            instrumentation.waitForIdleSync()

            /** Current application window queried through Android rather than the internal provider. */
            var root = requireNotNull(automation.rootInActiveWindow) {
                "Missing active accessibility window for the standard-component workflow."
            }
            /** Enabled Button node resolved by its spoken label. */
            val saveButton = root.requireDescendantWithDescription(SAVE_LABEL)
            automation.focusThroughTouchExplorationIfEnabled(
                node = saveButton,
                touchExplorationEnabled = touchExplorationEnabled,
                performPhysicalGesture = true,
            )
            if (fixture.saveClickCount == 0) {
                assertTrue(saveButton.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            }
            assertEquals(1, fixture.saveClickCount)

            /** Editable form field resolved by its semantic label. */
            val nameField = root.requireDescendantWithDescription(TEXT_FIELD_LABEL)
            /** Public Android arguments used by services to replace editable text. */
            val textArguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    UPDATED_TEXT,
                )
            }
            assertTrue(nameField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, textArguments))
            assertEquals(UPDATED_TEXT, fixture.textValue)

            /** Structured range node adjusted through the standard progress action. */
            val slider = root.requireDescendantWithDescription(SLIDER_LABEL)
            /** Public Android arguments carrying the requested absolute progress. */
            val progressArguments = Bundle().apply {
                putFloat(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,
                    UPDATED_SLIDER_VALUE,
                )
            }
            assertTrue(
                slider.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
                    progressArguments,
                ),
            )
            assertEquals(UPDATED_SLIDER_VALUE, fixture.sliderValue, 0f)
            automation.focusThroughTouchExplorationIfEnabled(slider, touchExplorationEnabled)

            /** Stable logical row that receives global accessibility focus before reordering. */
            val focusedRow = root.requireDescendantWithDescription(FOCUSED_ROW_LABEL)
            automation.focusThroughTouchExplorationIfEnabled(focusedRow, touchExplorationEnabled)
            scenario.onActivity { activity ->
                fixture.reorderVisibleRows()
                activity.hostView.invalidate()
                renderSynchronously(activity.hostView)
            }
            instrumentation.waitForIdleSync()

            /** Refreshed window after retained keyed rows changed visual positions. */
            root = requireNotNull(automation.rootInActiveWindow) {
                "Missing active accessibility window after list reordering."
            }
            /** Globally focused node, expected to remain attached to the same business row. */
            val focusedAfterReorder = root.requireDescendant { node ->
                node.isAccessibilityFocused
            }
            assertEquals(FOCUSED_ROW_LABEL, focusedAfterReorder.contentDescription?.toString())
            assertTrue(focusedAfterReorder.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            assertEquals(FOCUSED_ROW_INDEX, fixture.lastSelectedRowIndex)

            /** Fresh root after the focused item emitted its click event. */
            root = requireNotNull(automation.rootInActiveWindow) {
                "Missing active accessibility window after activating the reordered row."
            }
            /** Collapsed Dropdown anchor expanded through its role-specific Android action. */
            val dropdown = root.requireDescendantWithDescription(DROPDOWN_LABEL)
            automation.focusThroughTouchExplorationIfEnabled(dropdown, touchExplorationEnabled)
            assertFalse(fixture.dropdownExpanded)
            assertTrue(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND.id in dropdown.actionIds(),
            )
            assertFalse(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE.id in dropdown.actionIds(),
            )
            assertTrue(
                dropdown.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND.id),
            )
            assertTrue(fixture.dropdownExpanded)
            scenario.onActivity { activity ->
                activity.hostView.invalidate()
                renderSynchronously(activity.hostView)
                dropdownModalLabels = activity.hostView.accessibilityProvider()
                    .snapshotForTesting()
                    .nodes
                    .mapNotNull { snapshot -> snapshot.node.label }
            }
            instrumentation.waitForIdleSync()
            assertTrue(
                "Expanded Dropdown labels=$dropdownModalLabels",
                MENU_ITEM_LABEL in dropdownModalLabels,
            )

            /** Refreshed modal Dropdown Menu whose snapshot excludes every background control. */
            root = requireNotNull(automation.rootInActiveWindow) {
                "Missing active accessibility window after expanding the Dropdown."
            }
            assertFalse(root.containsDescendantWithDescription(SAVE_LABEL))
            /** Dropdown Menu item whose selection closes the controlled Popover. */
            val dropdownMenuItem = automation.requireDescendantWithDescriptionEventually(MENU_ITEM_LABEL)
            automation.focusThroughTouchExplorationIfEnabled(dropdownMenuItem, touchExplorationEnabled)
            assertTrue(dropdownMenuItem.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            assertEquals(MENU_ITEM_LABEL, fixture.lastMenuSelection)
            assertFalse(fixture.dropdownExpanded)
            scenario.onActivity { activity ->
                activity.hostView.invalidate()
                renderSynchronously(activity.hostView)
            }
            instrumentation.waitForIdleSync()

            /** Base window restored after Dropdown item selection closed its modal Menu. */
            root = requireNotNull(automation.rootInActiveWindow) {
                "Missing active accessibility window after selecting the Dropdown item."
            }
            assertTrue(root.containsDescendantWithDescription(SAVE_LABEL))
            /** Restored Dropdown anchor must advertise expand rather than collapse. */
            val restoredDropdown = root.requireDescendantWithDescription(DROPDOWN_LABEL)
            assertTrue(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND.id in restoredDropdown.actionIds(),
            )
            assertFalse(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE.id in restoredDropdown.actionIds(),
            )

            /** Programmatic insertion exposes a standalone Menu without Popover clipping. */
            scenario.onActivity { activity ->
                fixture.showMenu = true
                activity.hostView.invalidate()
                renderSynchronously(activity.hostView)
            }
            instrumentation.waitForIdleSync()
            /** Refreshed window containing the standalone Menu collection and items. */
            root = requireNotNull(automation.rootInActiveWindow) {
                "Missing active accessibility window after inserting the Menu."
            }
            assertFalse(root.containsDescendantWithDescription(SAVE_LABEL))
            /** Menu window traversed and dismissed through its role-specific actions. */
            val menu = root.requireDescendantWithDescription(MENU_LABEL)
            automation.focusThroughTouchExplorationIfEnabled(menu, touchExplorationEnabled)
            /** Menu item selected through the same click action used by screen readers. */
            val menuItem = root.requireDescendantWithDescription(MENU_ITEM_LABEL)
            automation.focusThroughTouchExplorationIfEnabled(menuItem, touchExplorationEnabled)
            assertTrue(menuItem.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            assertEquals(MENU_ITEM_LABEL, fixture.lastMenuSelection)
            assertTrue(menu.performAction(AccessibilityNodeInfo.ACTION_DISMISS))
            assertFalse(fixture.showMenu)

            /** Programmatic window insertion isolates Dialog traversal from the already tested Button. */
            scenario.onActivity { activity ->
                fixture.showDialog = true
                activity.hostView.invalidate()
                renderSynchronously(activity.hostView)
            }
            assertTrue(fixture.showDialog)
            instrumentation.waitForIdleSync()

            /** Refreshed window containing the real Dialog role. */
            root = requireNotNull(automation.rootInActiveWindow) {
                "Missing active accessibility window after opening the Dialog."
            }
            assertFalse(root.containsDescendantWithDescription(SAVE_LABEL))

            /** Dialog window dismissed through its role-specific Android action. */
            val dialog = root.requireDescendantWithDescription(DIALOG_LABEL)
            automation.focusThroughTouchExplorationIfEnabled(dialog, touchExplorationEnabled)
            assertTrue(dialog.performAction(AccessibilityNodeInfo.ACTION_DISMISS))
            assertFalse(fixture.showDialog)
            scenario.onActivity { activity ->
                activity.hostView.invalidate()
                renderSynchronously(activity.hostView)
            }
            instrumentation.waitForIdleSync()

            /** Final base window restored after Dialog dismissal for editable-form traversal. */
            root = requireNotNull(automation.rootInActiveWindow) {
                "Missing active accessibility window after dismissing the Dialog."
            }
            /** Refreshed scrollable List container exercised after all overlay workflows. */
            val list = root.requireDescendant { node ->
                node.className?.toString() == "android.widget.ListView" &&
                    node.collectionInfo?.rowCount == LIST_ITEM_COUNT
            }
            /** Offset before the system accessibility scroll action. */
            val offsetBeforeScroll = fixture.listState.scrollOffsetPx
            assertTrue(list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
            assertTrue(fixture.listState.scrollOffsetPx > offsetBeforeScroll)
            /** Refreshed TextField node focused last because activation may open the system IME. */
            val refreshedNameField = root.requireDescendantWithDescription(TEXT_FIELD_LABEL)
            automation.focusThroughTouchExplorationIfEnabled(refreshedNameField, touchExplorationEnabled)
        }
    }

    /** Returns the internal Provider instance owned by this real Host. */
    private fun PixelHostView.accessibilityProvider(): PixelHostAccessibilityNodeProvider {
        return accessibilityNodeProvider as PixelHostAccessibilityNodeProvider
    }

    /** Draws one Host frame synchronously so semantics and events are deterministic. */
    private fun renderSynchronously(host: PixelHostView) {
        /** Temporary bitmap that supplies the Canvas required by View.draw. */
        val bitmap = Bitmap.createBitmap(
            host.width.coerceAtLeast(1),
            host.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        host.draw(Canvas(bitmap))
        bitmap.recycle()
    }
}

/** Mutable state and public widget composition shared by the instrumentation scenarios. */
private class StandardComponentsFixture {
    /** Number of enabled SAVE Button activations completed through any input route. */
    var saveClickCount: Int = 0
        private set

    /** Stable row index selected after the most recent list-item activation. */
    var lastSelectedRowIndex: Int? = null
        private set

    /** Label of the most recently selected standalone Menu item. */
    var lastMenuSelection: String? = null
        private set

    /** Controller that owns the real ListView scroll contract. */
    val listController: PixelListController = PixelListController()

    /** List state starts away from the leading edge so scroll-backward is available. */
    val listState = listController.create(initialScrollOffsetPx = INITIAL_LIST_OFFSET)

    /** Controller that owns the real TextField editing contract. */
    private val textController: PixelTextFieldController = PixelTextFieldController()

    /** Text state exported through the TextField semantic value and selection. */
    private val textState = textController.create(initialText = INITIAL_TEXT)

    /** Current editable value exposed for platform-action assertions. */
    val textValue: String
        get() = textState.text

    /** Controlled Checkbox state used by its typed action. */
    private var checkboxChecked: Boolean = true

    /** Controlled Switch state used by its typed action. */
    private var switchChecked: Boolean = false

    /** Controlled selected tab index. */
    private var selectedTabIndex: Int = 1

    /** Controlled Slider value updated by drag and accessibility progress actions. */
    var sliderValue: Float = INITIAL_SLIDER_VALUE
        private set

    /** Stable logical row order, intentionally independent from each row's business key. */
    private var rowOrder: List<Int> = List(LIST_ITEM_COUNT) { index -> index }

    /** Whether the standalone Dialog exists in the current retained tree. */
    var showDialog: Boolean = false

    /** Whether the standalone Menu exists in the current retained tree. */
    var showMenu: Boolean = false

    /** Controlled expanded state of the real Dropdown. */
    var dropdownExpanded: Boolean = false

    /** Builds the complete public-component tree for the next Host frame. */
    fun build(): Widget {
        /** Primary controls laid out below the optional standalone windows. */
        val controls = Column(
            children = listOf(
                OutlinedButton(
                    text = SAVE_LABEL,
                    onPressed = { saveClickCount += 1 },
                    key = "save-button",
                ),
                OutlinedButton(
                    text = DISABLED_LABEL,
                    onPressed = {},
                    enabled = false,
                    key = "disabled-button",
                ),
                Checkbox(
                    checked = checkboxChecked,
                    onChanged = { next -> checkboxChecked = next },
                    semanticLabel = CHECKBOX_LABEL,
                    key = "checkbox",
                ),
                Switch(
                    checked = switchChecked,
                    onChanged = { next -> switchChecked = next },
                    semanticLabel = SWITCH_LABEL,
                    key = "switch",
                ),
                Tabs(
                    labels = listOf(FIRST_TAB_LABEL, SELECTED_TAB_LABEL),
                    selectedIndex = selectedTabIndex,
                    onSelected = { next -> selectedTabIndex = next },
                    key = "tabs",
                ),
                TextField(
                    state = textState,
                    controller = textController,
                    placeholder = "Enter a name",
                    semanticLabel = TEXT_FIELD_LABEL,
                    semanticHint = "Enter a name",
                    key = "name-field",
                ),
                Slider(
                    value = sliderValue,
                    onDrag = { next -> sliderValue = next },
                    onRelease = { next -> sliderValue = next },
                    semanticLabel = SLIDER_LABEL,
                    semanticValue = "40%",
                    semanticSteps = 4,
                    key = "volume-slider",
                ),
                SizedBox(
                    width = 80,
                    height = LIST_VIEWPORT_HEIGHT,
                    child = ListView(
                        items = rowOrder.map(::listItem),
                        state = listState,
                        controller = listController,
                        key = "list",
                    ),
                    key = "list-bounds",
                ),
                Dropdown(
                    label = DROPDOWN_LABEL,
                    selectedText = DROPDOWN_VALUE,
                    expanded = dropdownExpanded,
                    onToggle = { dropdownExpanded = !dropdownExpanded },
                    items = dropdownItems(),
                    key = "theme-dropdown",
                ),
            ),
            spacing = 1,
            key = "standard-controls",
        )
        /** Stack layers include optional window roles without replacing the primary controls. */
        val layers = buildList {
            add(controls)
            if (showMenu) add(standaloneMenu())
            if (showDialog) add(standaloneDialog())
        }
        return Stack(children = layers, key = "standard-component-stack")
    }

    /** Swaps two visible rows while retaining every row's stable business key. */
    fun reorderVisibleRows() {
        rowOrder = listOf(FOCUSED_ROW_INDEX, 0) + rowOrder.drop(2)
    }

    /** Builds one keyed semantic row rendered by the real ListView viewport. */
    private fun listItem(index: Int): Widget {
        /** Stable spoken row label. */
        val label = "ROW $index"
        return ListTile(
            title = Text(label),
            onTap = { lastSelectedRowIndex = index },
            semanticLabel = label,
            semanticRole = PixelSemanticRole.LIST_ITEM,
            key = "row-$index",
        )
    }

    /** Builds the standalone real Dialog used for mapping and lifecycle event assertions. */
    private fun standaloneDialog(): Widget {
        return Dialog(
            title = Text("Settings"),
            content = Text("Dialog content"),
            semanticLabel = DIALOG_LABEL,
            onDismissRequest = { showDialog = false },
            key = "settings-dialog",
        )
    }

    /** Builds the standalone real Menu used for collection and lifecycle event assertions. */
    private fun standaloneMenu(): Widget {
        return Menu(
            items = listOf(
                PixelMenuItem(
                    label = MENU_ITEM_LABEL,
                    onSelected = { lastMenuSelection = MENU_ITEM_LABEL },
                    key = "copy",
                ),
                PixelMenuItem(label = "Delete", onSelected = {}, key = "delete"),
            ),
            semanticLabel = MENU_LABEL,
            onDismissRequest = { showMenu = false },
            key = "actions-menu",
        )
    }

    /** Builds the stable options displayed by the real Dropdown menu. */
    private fun dropdownItems(): List<PixelMenuItem> {
        return listOf(
            PixelMenuItem(
                label = MENU_ITEM_LABEL,
                onSelected = {
                    lastMenuSelection = MENU_ITEM_LABEL
                    dropdownExpanded = false
                },
                key = "light",
            ),
            PixelMenuItem(label = DROPDOWN_VALUE, onSelected = {}, selected = true, key = "dark"),
        )
    }
}

/** Immutable event copy retained after Android may recycle the source AccessibilityEvent. */
private data class ObservedAccessibilityEvent(
    /** Android accessibility event type. */
    val type: Int,
    /** Android class name emitted for the virtual semantic source. */
    val className: String?,
)

/** Finds exactly one semantic node with the requested public label and role. */
private fun PixelAccessibilityTreeSnapshot.requireNode(
    label: String,
    role: PixelSemanticRole,
): PixelAccessibilityNodeSnapshot {
    return nodes.single { snapshot -> snapshot.node.label == label && snapshot.node.role == role }
}

/** Returns whether this immutable semantic snapshot still exposes one exact spoken label. */
private fun PixelAccessibilityTreeSnapshot.containsNodeWithLabel(label: String): Boolean {
    return nodes.any { snapshot -> snapshot.node.label == label || snapshot.node.value == label }
}

/** Creates Android node info for one current virtual snapshot or fails with its id. */
private fun PixelHostAccessibilityNodeProvider.requireInfo(
    snapshot: PixelAccessibilityNodeSnapshot,
): AccessibilityNodeInfo {
    return requireNotNull(createAccessibilityNodeInfo(snapshot.virtualViewId)) {
        "Missing AccessibilityNodeInfo for virtual id ${snapshot.virtualViewId}."
    }
}

/** Returns the concrete Android action identifiers advertised by this node. */
private fun AccessibilityNodeInfo.actionIds(): Set<Int> {
    return actionList.mapTo(linkedSetOf()) { action -> action.id }
}

/** Returns Android's accessibility manager for touch-exploration acceptance routing. */
private fun Context.accessibilityManager(): AccessibilityManager {
    return requireNotNull(getSystemService(AccessibilityManager::class.java))
}

/**
 * Establishes focus through a real touchscreen exploration gesture when a service is enabled.
 *
 * Devices without touch exploration, and roles whose service intentionally keeps the prior focus
 * during raw exploration, retain deterministic coverage through the equivalent public focus action.
 * Ordinary connected tests therefore neither skip nor depend on TalkBack.
 */
private fun UiAutomation.focusThroughTouchExplorationIfEnabled(
    node: AccessibilityNodeInfo,
    touchExplorationEnabled: Boolean,
    performPhysicalGesture: Boolean = false,
) {
    if (!touchExplorationEnabled || !performPhysicalGesture) {
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
        return
    }
    /** Screen-space target bounds supplied by the virtual Android node. */
    val bounds = Rect().also(node::getBoundsInScreen)
    /** Horizontal target point kept inside the node when its width is one pixel. */
    val centerX = bounds.centerX().toFloat()
    /** Vertical target point kept inside the node when its height is one pixel. */
    val centerY = bounds.centerY().toFloat()
    /** Monotonic time anchoring all events in the physical exploration gesture. */
    val downTime = SystemClock.uptimeMillis()
    injectTouchEvent(MotionEvent.ACTION_DOWN, centerX, centerY, downTime, downTime)
    SystemClock.sleep(80)
    injectTouchEvent(
        MotionEvent.ACTION_MOVE,
        (centerX + 1f).coerceAtMost(bounds.right.toFloat()),
        centerY,
        downTime,
        SystemClock.uptimeMillis(),
    )
    SystemClock.sleep(120)
    injectTouchEvent(
        MotionEvent.ACTION_UP,
        (centerX + 1f).coerceAtMost(bounds.right.toFloat()),
        centerY,
        downTime,
        SystemClock.uptimeMillis(),
    )

    /** Stable platform identity expected to own global focus after the gesture. */
    val expectedUniqueId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) node.uniqueId else null
    /** Spoken fallback identity used on API levels without public unique ids. */
    val expectedDescription = node.contentDescription?.toString()
    /** Deadline preventing a slow external service from hanging the instrumentation run. */
    val deadline = SystemClock.uptimeMillis() + TOUCH_EXPLORATION_FOCUS_TIMEOUT_MS
    /** Most recently observed global focus while TalkBack processes the gesture asynchronously. */
    var focused: AccessibilityNodeInfo? = null
    while (SystemClock.uptimeMillis() < deadline) {
        /** Fresh active window used because accessibility focus is global and changes asynchronously. */
        val root = rootInActiveWindow
        focused = root?.findFocusedDescendant()
        /** Stable unique-id equality when available, otherwise exact spoken-label equality. */
        val matchesExpectedNode = if (expectedUniqueId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            focused?.uniqueId == expectedUniqueId
        } else {
            focused?.contentDescription?.toString() == expectedDescription
        }
        if (matchesExpectedNode) return
        SystemClock.sleep(50)
    }
    /** Service-compatible fallback for roles whose gesture policy retained the previous node. */
    assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
    /** Fresh focused node after the explicit public focus request. */
    val focusedAfterAction = rootInActiveWindow?.findFocusedDescendant()
    assertEquals(expectedDescription, focusedAfterAction?.contentDescription?.toString())
}

/** Injects one touchscreen event through the unsuppressed UiAutomation connection. */
private fun UiAutomation.injectTouchEvent(
    action: Int,
    x: Float,
    y: Float,
    downTime: Long,
    eventTime: Long,
) {
    /** Physical-looking touchscreen event delivered through Android's input dispatcher. */
    val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0).apply {
        source = InputDevice.SOURCE_TOUCHSCREEN
    }
    try {
        assertTrue(injectInputEvent(event, true))
    } finally {
        event.recycle()
    }
}

/** Finds the single globally accessibility-focused node in the materialized platform subtree. */
private fun AccessibilityNodeInfo.findFocusedDescendant(): AccessibilityNodeInfo? {
    /** Pending platform nodes ordered by hierarchy position. */
    val pending = ArrayDeque<AccessibilityNodeInfo>()
    pending.add(this)
    while (pending.isNotEmpty()) {
        /** Current platform node tested for global accessibility focus. */
        val current = pending.removeFirst()
        if (current.isAccessibilityFocused) return current
        repeat(current.childCount) { childIndex ->
            /** Materialized child available from Android's public accessibility connection. */
            val child = current.getChild(childIndex)
            if (child != null) pending.add(child)
        }
    }
    return null
}

/** Finds one virtual descendant by exact spoken content description. */
private fun AccessibilityNodeInfo.requireDescendantWithDescription(
    description: String,
): AccessibilityNodeInfo {
    return requireDescendant { node -> node.contentDescription?.toString() == description }
}

/** Polls Android's asynchronous accessibility connection until one exact description appears. */
private fun UiAutomation.requireDescendantWithDescriptionEventually(
    description: String,
): AccessibilityNodeInfo {
    /** Monotonic deadline that prevents a missing platform update from hanging instrumentation. */
    val deadline = SystemClock.uptimeMillis() + ACCESSIBILITY_TREE_UPDATE_TIMEOUT_MS
    while (SystemClock.uptimeMillis() < deadline) {
        /** Fresh window snapshot because virtual-node changes cross an asynchronous Binder boundary. */
        val root = rootInActiveWindow
        if (root != null) {
            /** Current exact matches; one match returns while duplicates remain a contract failure. */
            val matches = root.findDescendants { node ->
                node.contentDescription?.toString() == description
            }
            if (matches.size == 1) return matches.single()
            check(matches.size <= 1) { "Duplicate accessibility nodes for $description" }
        }
        SystemClock.sleep(50)
    }
    throw NoSuchElementException("Accessibility node $description did not appear before the timeout")
}

/** Returns whether the materialized platform subtree contains one exact spoken description. */
private fun AccessibilityNodeInfo.containsDescendantWithDescription(description: String): Boolean {
    /** Pending platform nodes ordered by hierarchy traversal position. */
    val pending = ArrayDeque<AccessibilityNodeInfo>()
    pending.add(this)
    while (pending.isNotEmpty()) {
        /** Current node checked before its materialized children are queued. */
        val current = pending.removeFirst()
        if (current.contentDescription?.toString() == description) return true
        repeat(current.childCount) { childIndex ->
            /** Materialized child returned by Android's public accessibility connection. */
            val child = current.getChild(childIndex)
            if (child != null) pending.add(child)
        }
    }
    return false
}

/** Breadth-first search over the platform hierarchy, requiring exactly one matching node. */
private fun AccessibilityNodeInfo.requireDescendant(
    predicate: (AccessibilityNodeInfo) -> Boolean,
): AccessibilityNodeInfo {
    return findDescendants(predicate).single()
}

/** Returns every platform descendant matching [predicate] in breadth-first hierarchy order. */
private fun AccessibilityNodeInfo.findDescendants(
    predicate: (AccessibilityNodeInfo) -> Boolean,
): List<AccessibilityNodeInfo> {
    /** Pending platform nodes ordered by their hierarchy traversal position. */
    val pending = ArrayDeque<AccessibilityNodeInfo>()
    /** All matching nodes used to reject duplicate spoken targets. */
    val matches = mutableListOf<AccessibilityNodeInfo>()
    pending.add(this)
    while (pending.isNotEmpty()) {
        /** Current platform node whose properties and children are inspected. */
        val current = pending.removeFirst()
        if (predicate(current)) matches += current
        repeat(current.childCount) { childIndex ->
            /** Materialized virtual child returned by Android's accessibility connection. */
            val child = current.getChild(childIndex)
            if (child != null) pending.add(child)
        }
    }
    return matches
}

/** Reports whether a captured virtual event describes one window-role transition. */
private fun List<ObservedAccessibilityEvent>.containsWindowEventFor(className: String): Boolean {
    return any { event ->
        event.type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.className == className
    }
}

/** Spoken label of the enabled OutlinedButton. */
private const val SAVE_LABEL: String = "SAVE"

/** Spoken label of the disabled OutlinedButton. */
private const val DISABLED_LABEL: String = "DISABLED"

/** Spoken label of the checked Checkbox. */
private const val CHECKBOX_LABEL: String = "ALLOW NOTIFICATIONS"

/** Spoken label of the unchecked Switch. */
private const val SWITCH_LABEL: String = "DARK MODE"

/** Spoken label of the first unselected tab. */
private const val FIRST_TAB_LABEL: String = "HOME"

/** Spoken label of the selected tab. */
private const val SELECTED_TAB_LABEL: String = "SETTINGS"

/** Spoken label of the editable TextField. */
private const val TEXT_FIELD_LABEL: String = "NAME"

/** Initial TextField value asserted through Android text. */
private const val INITIAL_TEXT: String = "ADA"

/** Text entered through the public Android set-text action. */
private const val UPDATED_TEXT: String = "GRACE"

/** Spoken label of the structured Slider range. */
private const val SLIDER_LABEL: String = "VOLUME"

/** Initial structured Slider progress. */
private const val INITIAL_SLIDER_VALUE: Float = 0.4f

/** Progress requested through the public Android range action. */
private const val UPDATED_SLIDER_VALUE: Float = 0.8f

/** Initial ListView offset ensuring both scroll directions are executable. */
private const val INITIAL_LIST_OFFSET: Float = 6f

/** Bounded ListView viewport height used to keep content scrollable. */
private const val LIST_VIEWPORT_HEIGHT: Int = 12

/** Logical row count exposed by the real ListView collection. */
private const val LIST_ITEM_COUNT: Int = 6

/** Stable row selected before and after reversing the visual order. */
private const val FOCUSED_ROW_INDEX: Int = 1

/** Spoken label of the stable row selected across visual reordering. */
private const val FOCUSED_ROW_LABEL: String = "ROW $FOCUSED_ROW_INDEX"

/** Spoken label of the standalone Dialog window. */
private const val DIALOG_LABEL: String = "SETTINGS DIALOG"

/** Spoken label of the standalone Menu window. */
private const val MENU_LABEL: String = "ACTIONS MENU"

/** Logical row count exposed by the standalone Menu collection. */
private const val MENU_ITEM_COUNT: Int = 2

/** Spoken label of the standalone Menu item selected through Android accessibility. */
private const val MENU_ITEM_LABEL: String = "Light"

/** Maximum wait for an external touch-exploration service to publish global focus. */
private const val TOUCH_EXPLORATION_FOCUS_TIMEOUT_MS: Long = 1_000L

/** Maximum wait for Android to materialize a newly published virtual-node subtree. */
private const val ACCESSIBILITY_TREE_UPDATE_TIMEOUT_MS: Long = 1_000L

/** Spoken label of the real Dropdown anchor. */
private const val DROPDOWN_LABEL: String = "THEME"

/** Structured selected value of the real Dropdown anchor. */
private const val DROPDOWN_VALUE: String = "Dark"
