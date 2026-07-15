package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Dropdown
import com.purride.pixelui.ListTile
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelMenuItem
import com.purride.pixelui.PixelOverlayController
import com.purride.pixelui.PixelOverlayHost
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Slider
import com.purride.pixelui.Snackbar
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextField
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Accent
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Muted

/** Coherent TalkBack flow covering form input, range, dynamic collection, popup, dialog, and page change. */
val AccessibilityFlowScene: DemoScene by lazy {
    // Lazy construction keeps the test-only body factory independent from DemoCatalog's static scene registry.
    ComponentExampleScene(
        id = "input_accessibility_flow",
        title = "Accessibility Flow",
        summary = "TalkBack 表单、动态列表、Slider、Menu、Dialog 与页面切换闭环",
        category = DemoCatalog.input,
        tags = setOf("component", "accessibility", "talkback", "semantics", "form"),
        apis = setOf(
            "Semantics",
            "PixelSemanticsActions",
            "TextField",
            "ListViewBuilder",
            "Slider",
            "Dropdown",
            "PixelOverlayHost",
        ),
        bodyBuilder = { accessibilityFlowBodyForTest() },
    )
}

/**
 * Creates the real retained accessibility workflow for JVM keyboard acceptance tests.
 *
 * Keeping this factory internal lets tests exercise the catalog body without widening the SDK or
 * duplicating the scene's business state in a test-only fixture.
 */
internal fun accessibilityFlowBodyForTest(
    /** Optional retained identity used by a test or catalog host. */
    key: Any? = null,
): Widget = AccessibilityFlowBody(key = key)

/** Stable business item used to prove repeated labels remain distinct through list reordering. */
private data class AccessibilityFlowItem(
    /** Stable key independent from list position and label. */
    val id: String,
    /** Spoken row label; two rows deliberately share the same value. */
    val label: String,
)

/** Retained owner for the complete accessibility showcase workflow. */
private class AccessibilityFlowBody(
    /** Optional scene identity supplied by the catalog runtime. */
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    /** Creates isolated flow state each time the scene is mounted. */
    override fun createState(): State<out StatefulWidget> = AccessibilityFlowState()
}

/** Mutable state behind [AccessibilityFlowBody]. */
private class AccessibilityFlowState : State<AccessibilityFlowBody>() {
    /** Text controller shared by visual input and semantic set-text actions. */
    private val nameController = PixelTextFieldController()

    /** Editable name restored across ordinary scene rebuilds. */
    private val nameState = nameController.create()

    /** Scroll controller for the dynamic keyed collection. */
    private val listController = PixelListController()

    /** Scroll offset and measured geometry for the collection. */
    private val listState = listController.create()

    /** Overlay owner whose Dialog supplies dismiss semantics and retained exit motion. */
    private val overlayController = PixelOverlayController()

    /** Controlled Slider value. */
    private var volume = 0.4f

    /** Controlled Dropdown state. */
    private var menuExpanded = false

    /** Current structured Dropdown value. */
    private var selectedMode = "A"

    /** Whether the local details page replaces the main flow content. */
    private var detailsVisible = false

    /** Monotonic key source for newly inserted rows. */
    private var nextItemId = 3

    /** Dynamic rows with two repeated labels and stable business keys. */
    private var items = listOf(
        AccessibilityFlowItem(id = "item-1", label = "DELETE"),
        AccessibilityFlowItem(id = "item-2", label = "DELETE"),
    )

    /** Polite live-region message reflecting the latest completed action. */
    private var status = "READY"

    /** Builds either the main workflow or its local destination under the same Overlay host. */
    override fun build(context: BuildContext): Widget {
        val content = if (detailsVisible) buildDetailsPage() else buildMainPage()
        return PixelOverlayHost(
            controller = overlayController,
            child = content,
            key = "accessibility-overlay-host",
        )
    }

    /** Builds the primary end-to-end TalkBack workflow. */
    private fun buildMainPage(): Widget {
        val nameError = if (nameState.text.isNotEmpty() && nameState.text.length < 2) "Name is too short" else null
        return Column(
            children = listOf(
                Text("ACCESSIBILITY CORE FLOW", key = "flow-heading"),
                TextField(
                    state = nameState,
                    controller = nameController,
                    placeholder = "Enter name",
                    semanticLabel = "Name",
                    semanticHint = "Required",
                    semanticError = nameError,
                    onChanged = { value ->
                        status = "NAME ${value.length}"
                        setState { }
                    },
                    key = "name-field",
                ),
                Slider(
                    value = volume,
                    onDrag = { next ->
                        volume = next
                        status = "VOLUME ${(next * 100).toInt()}"
                        setState { }
                    },
                    onRelease = { next ->
                        volume = next
                        setState { }
                    },
                    semanticLabel = "Volume",
                    semanticValue = "${(volume * 100).toInt()} percent",
                    semanticSteps = 9,
                    key = "volume-slider",
                ),
                Row(
                    children = listOf(
                        TextButton("ADD", onPressed = ::insertItem, key = "add-item"),
                        TextButton("REORDER", onPressed = ::reorderItems, key = "reorder-items"),
                        TextButton("REMOVE", onPressed = ::removeItem, key = "remove-item"),
                    ),
                    spacing = 2,
                ),
                SizedBox(
                    height = 24,
                    child = ListViewBuilder(
                        itemCount = items.size,
                        itemBuilder = { index -> buildListItem(items[index]) },
                        itemExtent = 10,
                        state = listState,
                        controller = listController,
                        key = "dynamic-list",
                    ),
                ),
                Dropdown(
                    label = "Mode",
                    selectedText = selectedMode,
                    expanded = menuExpanded,
                    onToggle = {
                        menuExpanded = !menuExpanded
                        setState { }
                    },
                    items = listOf(
                        menuItem("A"),
                        menuItem("B"),
                    ),
                    key = "mode-menu",
                ),
                Row(
                    children = listOf(
                        OutlinedButton("OPEN DIALOG", onPressed = ::showDialog, borderColor = Cyan, key = "open-dialog"),
                        OutlinedButton(
                            "OPEN DETAILS",
                            onPressed = {
                                detailsVisible = true
                                status = "DETAILS"
                                setState { }
                            },
                            borderColor = Green,
                            key = "open-details",
                        ),
                    ),
                    spacing = 2,
                ),
                Snackbar(message = status, textStyle = com.purride.pixelui.TextStyle(color = Muted), key = "flow-status"),
            ),
            spacing = 3,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            key = "accessibility-main",
        )
    }

    /** Builds the destination used to verify window replacement and explicit return. */
    private fun buildDetailsPage(): Widget {
        return Column(
            children = listOf(
                Text("DETAILS PAGE", key = "details-heading"),
                Text("Use BACK TO FLOW to restore the previous controls."),
                OutlinedButton(
                    "BACK TO FLOW",
                    onPressed = {
                        detailsVisible = false
                        status = "RETURNED"
                        setState { }
                    },
                    borderColor = Accent,
                    key = "back-to-flow",
                ),
            ),
            spacing = 3,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            key = "accessibility-details",
        )
    }

    /** Builds one keyed row whose repeated spoken label still owns its exact callback. */
    private fun buildListItem(item: AccessibilityFlowItem): Widget {
        return ListTile(
            title = Text(item.label),
            semanticLabel = item.label,
            onTap = {
                status = "SELECTED ${item.id}"
                setState { }
            },
            key = item.id,
        )
    }

    /** Builds a stable selected menu item. */
    private fun menuItem(value: String): PixelMenuItem {
        return PixelMenuItem(
            label = value,
            selected = selectedMode == value,
            key = "mode-$value",
            onSelected = {
                selectedMode = value
                menuExpanded = false
                status = "MODE $value"
                setState { }
            },
        )
    }

    /** Inserts one new stable row before the repeated labels. */
    private fun insertItem() {
        val newItem = AccessibilityFlowItem(id = "item-${nextItemId++}", label = "NEW ITEM")
        items = listOf(newItem) + items
        status = "ITEM ADDED"
        setState { }
    }

    /** Reverses visual order without changing any business identity. */
    private fun reorderItems() {
        items = items.reversed()
        status = "ITEMS REORDERED"
        setState { }
    }

    /** Removes the first row while leaving remaining ids untouched. */
    private fun removeItem() {
        if (items.isEmpty()) return
        items = items.drop(1)
        status = "ITEM REMOVED"
        setState { }
    }

    /** Opens a controller-owned modal dialog with both button and semantic dismiss paths. */
    private fun showDialog() {
        overlayController.showDialog(
            title = Text("CONFIRM"),
            content = Text("Close this dialog to return to OPEN DIALOG."),
            actions = listOf(
                TextButton(
                    "CLOSE",
                    onPressed = {
                        overlayController.dismissTop()
                        status = "DIALOG CLOSED"
                        setState { }
                    },
                    key = "close-dialog",
                ),
            ),
            borderColor = Cyan,
        )
        status = "DIALOG OPEN"
        setState { }
    }
}
