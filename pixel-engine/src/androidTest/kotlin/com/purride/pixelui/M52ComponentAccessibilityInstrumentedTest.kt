package com.purride.pixelui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.internal.host.PixelAccessibilityNodeSnapshot
import com.purride.pixelui.internal.host.PixelAccessibilityTreeSnapshot
import com.purride.pixelui.internal.host.PixelHostAccessibilityNodeProvider
import com.purride.pixelui.state.PixelTextFieldController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * API 24+ Android virtual-node acceptance for the M5-2 production component contracts.
 *
 * Every assertion crosses an Activity-attached [PixelHostView] and its real
 * [PixelHostAccessibilityNodeProvider]. JVM semantics snapshots are used only to address the
 * provider-owned virtual IDs; Android [AccessibilityNodeInfo] remains the asserted platform API.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
@Suppress("DEPRECATION")
class M52ComponentAccessibilityInstrumentedTest {
    /**
     * Verifies Radio, RadioGroup, and IconButton Android roles, actions, and retained identity.
     */
    @Test
    fun selectionControlsExposeExecutableAndroidNodesAndStableBusinessIdentity() {
        /** Number of standalone Radio accessibility clicks delivered to business code. */
        var radioClickCount = 0
        /** Number of IconButton accessibility clicks delivered to business code. */
        var iconClickCount = 0
        /** Caller-owned RadioGroup selection keyed by a stable business identifier. */
        var selectedGroupId = GROUP_BETA_ID
        /** Reorderable options whose business IDs must outlive their current row positions. */
        var groupOptions = listOf(
            PixelRadioOption(id = GROUP_ALPHA_ID, label = GROUP_ALPHA_LABEL),
            PixelRadioOption(id = GROUP_BETA_ID, label = GROUP_BETA_LABEL),
        )
        /** Opaque alpha-mask icon whose visual descendant must not create a second Button node. */
        val icon = PixelIconData(
            PixelBitmap(
                width = 3,
                height = 3,
                pixels = IntArray(9) { PixelColor.White.argb },
            ),
        )

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Real Host attached to the Activity window and lifecycle owner. */
                val host = activity.hostView
                host.setContent {
                    Column(
                        children = listOf(
                            Radio(
                                selected = false,
                                onSelected = { radioClickCount += 1 },
                                semanticLabel = STANDALONE_RADIO_LABEL,
                                key = "m52-standalone-radio",
                            ),
                            RadioGroup(
                                options = groupOptions,
                                selectedId = selectedGroupId,
                                onSelected = { nextId -> selectedGroupId = nextId },
                                semanticLabel = RADIO_GROUP_LABEL,
                                key = "m52-radio-group",
                            ),
                            IconButton(
                                icon = icon,
                                onPressed = { iconClickCount += 1 },
                                semanticLabel = ICON_BUTTON_LABEL,
                                key = "m52-icon-button",
                            ),
                        ),
                        spacing = 1,
                        key = "m52-selection-controls",
                    )
                }
                renderM52Synchronously(host)

                /** Provider owned by the Activity-attached Host. */
                val provider = host.m52AccessibilityProvider()
                /** Initial Android virtual tree before the business-option reorder. */
                val initialTree = provider.snapshotForTesting()

                /** Standalone Radio virtual node exported by the production component. */
                val standaloneRadio = initialTree.requireM52Node(
                    label = STANDALONE_RADIO_LABEL,
                    role = PixelSemanticRole.RADIO_BUTTON,
                )
                /** Android platform representation of the standalone Radio node. */
                val standaloneRadioInfo = provider.requireM52Info(standaloneRadio)
                assertEquals("android.widget.RadioButton", standaloneRadioInfo.className.toString())
                assertTrue(standaloneRadioInfo.isCheckable)
                assertFalse(standaloneRadioInfo.isChecked)
                assertTrue(standaloneRadioInfo.isClickable)
                assertTrue(AccessibilityNodeInfo.ACTION_CLICK in standaloneRadioInfo.m52ActionIds())
                assertTrue(
                    provider.performAction(
                        standaloneRadio.virtualViewId,
                        AccessibilityNodeInfo.ACTION_CLICK,
                        null,
                    ),
                )
                assertEquals(1, radioClickCount)

                /** RadioGroup collection node carrying Android single-selection metadata. */
                val radioGroup = initialTree.requireM52Node(
                    label = RADIO_GROUP_LABEL,
                    role = PixelSemanticRole.GENERIC,
                )
                /** Android collection representation of the complete RadioGroup. */
                val radioGroupInfo = provider.requireM52Info(radioGroup)
                /** Non-null Android collection contract published by RadioGroup. */
                val groupCollection = requireNotNull(radioGroupInfo.collectionInfo)
                assertEquals(2, groupCollection.rowCount)
                assertEquals(1, groupCollection.columnCount)
                assertEquals(
                    AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE,
                    groupCollection.selectionMode,
                )

                /** Initially selected Beta business option before visual reordering. */
                val initialBeta = initialTree.requireM52Node(
                    label = GROUP_BETA_LABEL,
                    role = PixelSemanticRole.RADIO_BUTTON,
                )
                /** Android RadioButton representation of the selected Beta option. */
                val initialBetaInfo = provider.requireM52Info(initialBeta)
                /** Android item position and selection metadata for the Beta option. */
                val initialBetaItem = requireNotNull(initialBetaInfo.collectionItemInfo)
                assertEquals(radioGroup.virtualViewId, initialBeta.parentVirtualViewId)
                assertEquals(1, initialBetaItem.rowIndex)
                assertEquals(0, initialBetaItem.columnIndex)
                assertTrue(initialBetaItem.isSelected)
                assertTrue(initialBetaInfo.isCheckable)
                assertTrue(initialBetaInfo.isChecked)

                /** Every virtual node carrying the icon button name, including descendants. */
                val iconNamedNodes = initialTree.nodes.filter { snapshot ->
                    snapshot.node.label == ICON_BUTTON_LABEL || snapshot.node.value == ICON_BUTTON_LABEL
                }
                assertEquals(1, iconNamedNodes.size)
                /** Unique merged IconButton node that owns its visual icon descendant. */
                val iconButton = initialTree.requireM52Node(
                    label = ICON_BUTTON_LABEL,
                    role = PixelSemanticRole.BUTTON,
                )
                /** Android platform representation of the merged IconButton. */
                val iconButtonInfo = provider.requireM52Info(iconButton)
                assertTrue(iconButton.childVirtualViewIds.isEmpty())
                assertEquals("android.widget.Button", iconButtonInfo.className.toString())
                assertTrue(iconButtonInfo.isClickable)
                assertTrue(AccessibilityNodeInfo.ACTION_CLICK in iconButtonInfo.m52ActionIds())
                assertTrue(
                    provider.performAction(
                        iconButton.virtualViewId,
                        AccessibilityNodeInfo.ACTION_CLICK,
                        null,
                    ),
                )
                assertEquals(1, iconClickCount)

                // Move Beta from row one to row zero without changing its business identity.
                groupOptions = listOf(groupOptions[1], groupOptions[0])
                host.invalidate()
                renderM52Synchronously(host)

                /** Rebuilt tree after the two stable business options swap visual rows. */
                val reorderedTree = provider.snapshotForTesting()
                /** Reordered Beta node addressed by its unchanged spoken label and business key. */
                val reorderedBeta = reorderedTree.requireM52Node(
                    label = GROUP_BETA_LABEL,
                    role = PixelSemanticRole.RADIO_BUTTON,
                )
                /** Updated Android item metadata for the same retained Beta virtual node. */
                val reorderedBetaItem = requireNotNull(
                    provider.requireM52Info(reorderedBeta).collectionItemInfo,
                )
                assertEquals(initialBeta.virtualViewId, reorderedBeta.virtualViewId)
                assertEquals(0, reorderedBetaItem.rowIndex)
                assertTrue(reorderedBetaItem.isSelected)

                /** Alpha option now at row one and still wired to its stable business ID. */
                val reorderedAlpha = reorderedTree.requireM52Node(
                    label = GROUP_ALPHA_LABEL,
                    role = PixelSemanticRole.RADIO_BUTTON,
                )
                assertTrue(
                    provider.performAction(
                        reorderedAlpha.virtualViewId,
                        AccessibilityNodeInfo.ACTION_CLICK,
                        null,
                    ),
                )
                assertEquals(GROUP_ALPHA_ID, selectedGroupId)
                renderM52Synchronously(host)
                /** Alpha node after the caller rebuilds with the requested controlled selection. */
                val selectedAlpha = provider.snapshotForTesting().requireM52Node(
                    label = GROUP_ALPHA_LABEL,
                    role = PixelSemanticRole.RADIO_BUTTON,
                )
                assertTrue(provider.requireM52Info(selectedAlpha).isChecked)
            }
        }
    }

    /**
     * Verifies decorated TextField merging, validation state, hint search, and edit actions.
     */
    @Test
    fun decoratedTextFieldExportsOneEditableAndroidNodeWithHintAndError() {
        /** Controller owning the real TextField edit and selection operations. */
        val controller = PixelTextFieldController()
        /** Controlled text state mutated by Android accessibility edit actions. */
        val state = controller.create(initialText = "AD")
        /** Business callbacks emitted after successful Android set-text operations. */
        val changedValues = mutableListOf<String>()

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Real Activity-attached Host used for input-target and provider association. */
                val host = activity.hostView
                host.setContent {
                    TextField(
                        state = state,
                        controller = controller,
                        decoration = FormFieldDecoration(
                            label = DECORATED_FIELD_LABEL,
                            helper = DECORATED_FIELD_HIDDEN_HELPER,
                            error = DECORATED_FIELD_ERROR,
                            required = true,
                            counter = DECORATED_FIELD_COUNTER,
                        ),
                        semanticHint = DECORATED_FIELD_INSTRUCTION,
                        onChanged = changedValues::add,
                        key = "m52-decorated-field",
                    )
                }
                renderM52Synchronously(host)

                /** Host-owned Android provider resolving the exact TextField render source. */
                val provider = host.m52AccessibilityProvider()
                /** Current virtual tree after visible decoration has been merged. */
                val tree = provider.snapshotForTesting()
                /** All semantic sources claiming the editable TextField role. */
                val fieldNodes = tree.nodes.filter { snapshot ->
                    snapshot.node.role == PixelSemanticRole.TEXT_FIELD
                }
                assertEquals(1, fieldNodes.size)
                /** The unique merged decorated TextField virtual node. */
                val field = fieldNodes.single()
                /** Android EditText representation generated for the merged node. */
                val fieldInfo = provider.requireM52Info(field)
                /** Number of Android EditText classes produced across the whole virtual tree. */
                val androidEditTextCount = tree.nodes.count { snapshot ->
                    provider.requireM52Info(snapshot).className.toString() == "android.widget.EditText"
                }
                assertEquals(1, androidEditTextCount)
                assertEquals("android.widget.EditText", fieldInfo.className.toString())
                assertEquals(DECORATED_FIELD_ACCESSIBLE_LABEL, fieldInfo.contentDescription.toString())
                assertEquals(DECORATED_FIELD_EXPECTED_HINT, field.node.hint)
                assertEquals(DECORATED_FIELD_ERROR, fieldInfo.error.toString())
                assertTrue(fieldInfo.isContentInvalid)
                assertTrue(fieldInfo.isEditable)
                assertTrue(AccessibilityNodeInfo.ACTION_SET_TEXT in fieldInfo.m52ActionIds())
                assertTrue(AccessibilityNodeInfo.ACTION_SET_SELECTION in fieldInfo.m52ActionIds())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    assertEquals(DECORATED_FIELD_EXPECTED_HINT, fieldInfo.hintText.toString())
                }
                /** Provider text search result proving hints remain indexed on API 24 and 25 too. */
                val hintSearchResults = provider.findAccessibilityNodeInfosByText(
                    DECORATED_FIELD_INSTRUCTION,
                    View.NO_ID,
                )
                assertEquals(1, hintSearchResults.size)

                /** Android arguments replacing the complete controlled field value. */
                val setTextArguments = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        DECORATED_FIELD_REPLACEMENT,
                    )
                }
                assertTrue(
                    provider.performAction(
                        field.virtualViewId,
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        setTextArguments,
                    ),
                )
                assertEquals(DECORATED_FIELD_REPLACEMENT, state.text)
                assertEquals(listOf(DECORATED_FIELD_REPLACEMENT), changedValues)

                /** Android arguments selecting an interior range in the replacement value. */
                val selectionArguments = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 1)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 4)
                }
                assertTrue(
                    provider.performAction(
                        field.virtualViewId,
                        AccessibilityNodeInfo.ACTION_SET_SELECTION,
                        selectionArguments,
                    ),
                )
                assertEquals(1, state.selectionStart)
                assertEquals(4, state.selectionEnd)
            }
        }
    }

    /**
     * Verifies Tabs and SegmentedControl publish horizontal SINGLE Android collections and items.
     */
    @Test
    fun tabsAndSegmentedControlExposeSingleSelectionAndroidCollections() {
        /** Caller-owned selected tab used only to keep the fixture fully controlled. */
        var selectedTabIndex = 1
        /** Caller-owned selected segment used only to keep the fixture fully controlled. */
        var selectedSegmentIndex = 0

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Real Activity-attached Host for the two independent single-selection groups. */
                val host = activity.hostView
                host.setContent {
                    Column(
                        children = listOf(
                            Tabs(
                                labels = TAB_LABELS,
                                selectedIndex = selectedTabIndex,
                                onSelected = { nextIndex -> selectedTabIndex = nextIndex },
                                key = "m52-tabs",
                            ),
                            SegmentedControl(
                                labels = SEGMENT_LABELS,
                                selectedIndex = selectedSegmentIndex,
                                onSelected = { nextIndex -> selectedSegmentIndex = nextIndex },
                                key = "m52-segmented-control",
                            ),
                        ),
                        spacing = 1,
                        key = "m52-single-selection-groups",
                    )
                }
                renderM52Synchronously(host)

                /** Host-owned provider mapping both group and item semantics to Android. */
                val provider = host.m52AccessibilityProvider()
                /** Immutable Android virtual tree for the two controlled selectors. */
                val tree = provider.snapshotForTesting()
                assertHorizontalSingleSelectionCollection(
                    provider = provider,
                    tree = tree,
                    groupLabel = TABS_GROUP_LABEL,
                    expectedItemLabels = TAB_LABELS,
                    expectedSelectedIndex = selectedTabIndex,
                )
                assertHorizontalSingleSelectionCollection(
                    provider = provider,
                    tree = tree,
                    groupLabel = SEGMENTED_GROUP_LABEL,
                    expectedItemLabels = SEGMENT_LABELS,
                    expectedSelectedIndex = selectedSegmentIndex,
                )
            }
        }
    }

    /**
     * Verifies Slidable's expand, collapse, dismiss, and Loading capability through Android actions.
     */
    @Test
    fun slidableAndroidActionsDriveItsPaneStateMachineAndLoadingRejectsMutation() {
        /** Dismissed directions delivered only after Android invokes the open pane's action. */
        val dismissals = mutableListOf<SlidableDirection>()

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Real Activity-attached Host whose immediate motion makes endpoints deterministic. */
                val host = activity.hostView
                host.capabilitiesOverride =
                    host.hostCapabilities.copy(motionSettings = PixelMotionSettings(animatorDurationScale = 0f))
                host.setContent {
                    Column(
                        children = listOf(
                            Slidable(
                                child = Container(
                                    width = 40,
                                    height = 10,
                                    fillColor = PixelColor.Black,
                                ),
                                states = PixelControlStateSet.Normal,
                                endActionPane = m52DismissiblePane(NORMAL_SLIDABLE_ACTION_LABEL),
                                onDismissed = dismissals::add,
                                semanticLabel = NORMAL_SLIDABLE_LABEL,
                                key = "m52-normal-slidable",
                            ),
                            Slidable(
                                child = Container(
                                    width = 40,
                                    height = 10,
                                    fillColor = PixelColor.Black,
                                ),
                                states = PixelControlStateSet.of(PixelControlState.Loading),
                                endActionPane = m52DismissiblePane(LOADING_SLIDABLE_ACTION_LABEL),
                                onDismissed = dismissals::add,
                                semanticLabel = LOADING_SLIDABLE_LABEL,
                                key = "m52-loading-slidable",
                            ),
                        ),
                        spacing = 1,
                        key = "m52-slidable-actions",
                    )
                }
                renderM52Synchronously(host)

                /** Host-owned provider executing standard Android accessibility actions. */
                val provider = host.m52AccessibilityProvider()
                /** Initial closed virtual tree containing normal and Loading rows. */
                val initialTree = provider.snapshotForTesting()
                /** Closed normal Slidable row before ACTION_EXPAND. */
                val closed = initialTree.requireM52Node(
                    label = NORMAL_SLIDABLE_LABEL,
                    role = PixelSemanticRole.BUTTON,
                )
                /** Android actions advertised by the closed normal row. */
                val closedActions = provider.requireM52Info(closed).m52ActionIds()
                assertTrue(AccessibilityNodeInfo.ACTION_EXPAND in closedActions)
                assertFalse(AccessibilityNodeInfo.ACTION_COLLAPSE in closedActions)
                assertFalse(AccessibilityNodeInfo.ACTION_DISMISS in closedActions)

                /** Loading Slidable row that remains discoverable while all mutations are blocked. */
                val loading = initialTree.requireM52Node(
                    label = LOADING_SLIDABLE_LABEL,
                    role = PixelSemanticRole.BUTTON,
                )
                /** Android disabled state and action set for the Loading row. */
                val loadingInfo = provider.requireM52Info(loading)
                /** Concrete Android actions that must exclude every pane mutation. */
                val loadingActions = loadingInfo.m52ActionIds()
                assertFalse(loadingInfo.isEnabled)
                assertFalse(AccessibilityNodeInfo.ACTION_EXPAND in loadingActions)
                assertFalse(AccessibilityNodeInfo.ACTION_COLLAPSE in loadingActions)
                assertFalse(AccessibilityNodeInfo.ACTION_DISMISS in loadingActions)
                assertFalse(
                    provider.performAction(
                        loading.virtualViewId,
                        AccessibilityNodeInfo.ACTION_EXPAND,
                        null,
                    ),
                )

                assertTrue(
                    provider.performAction(
                        closed.virtualViewId,
                        AccessibilityNodeInfo.ACTION_EXPAND,
                        null,
                    ),
                )
                renderM52Synchronously(host)
                /** Open normal row after its provider-owned ACTION_EXPAND executes. */
                val opened = provider.snapshotForTesting().requireM52Node(
                    label = NORMAL_SLIDABLE_LABEL,
                    role = PixelSemanticRole.BUTTON,
                )
                /** Android actions advertised only while the dismissible end pane is open. */
                val openedActions = provider.requireM52Info(opened).m52ActionIds()
                assertEquals(closed.virtualViewId, opened.virtualViewId)
                assertFalse(AccessibilityNodeInfo.ACTION_EXPAND in openedActions)
                assertTrue(AccessibilityNodeInfo.ACTION_COLLAPSE in openedActions)
                assertTrue(AccessibilityNodeInfo.ACTION_DISMISS in openedActions)

                assertTrue(
                    provider.performAction(
                        opened.virtualViewId,
                        AccessibilityNodeInfo.ACTION_COLLAPSE,
                        null,
                    ),
                )
                renderM52Synchronously(host)
                /** Re-closed row proving ACTION_COLLAPSE returned to the expand-only state. */
                val collapsed = provider.snapshotForTesting().requireM52Node(
                    label = NORMAL_SLIDABLE_LABEL,
                    role = PixelSemanticRole.BUTTON,
                )
                /** Android actions after collapse removes the pane from logical availability. */
                val collapsedActions = provider.requireM52Info(collapsed).m52ActionIds()
                assertTrue(AccessibilityNodeInfo.ACTION_EXPAND in collapsedActions)
                assertFalse(AccessibilityNodeInfo.ACTION_COLLAPSE in collapsedActions)
                assertFalse(AccessibilityNodeInfo.ACTION_DISMISS in collapsedActions)

                assertTrue(
                    provider.performAction(
                        collapsed.virtualViewId,
                        AccessibilityNodeInfo.ACTION_EXPAND,
                        null,
                    ),
                )
                renderM52Synchronously(host)
                /** Second open row whose live dismiss callback is now executable. */
                val dismissible = provider.snapshotForTesting().requireM52Node(
                    label = NORMAL_SLIDABLE_LABEL,
                    role = PixelSemanticRole.BUTTON,
                )
                assertTrue(
                    provider.performAction(
                        dismissible.virtualViewId,
                        AccessibilityNodeInfo.ACTION_DISMISS,
                        null,
                    ),
                )
                renderM52Synchronously(host)
                assertEquals(listOf(SlidableDirection.END), dismissals)
            }
        }
    }

    /**
     * Asserts one group and all of its direct Android items form a horizontal SINGLE collection.
     *
     * @param provider Host-owned provider used to create real Android node information.
     * @param tree Current immutable provider tree used only for virtual hierarchy addressing.
     * @param groupLabel Exact localized group label exported by the production component.
     * @param expectedItemLabels Item labels in their expected visual and collection order.
     * @param expectedSelectedIndex Zero-based item expected to own Android selected state.
     */
    private fun assertHorizontalSingleSelectionCollection(
        provider: PixelHostAccessibilityNodeProvider,
        tree: PixelAccessibilityTreeSnapshot,
        groupLabel: String,
        expectedItemLabels: List<String>,
        expectedSelectedIndex: Int,
    ) {
        /** Generic semantic group carrying Android collection metadata. */
        val group = tree.requireM52Node(label = groupLabel, role = PixelSemanticRole.GENERIC)
        /** Android platform representation of the complete selector group. */
        val groupInfo = provider.requireM52Info(group)
        /** Non-null Android collection metadata mapped from Pixel SINGLE selection. */
        val collection = requireNotNull(groupInfo.collectionInfo)
        assertEquals(1, collection.rowCount)
        assertEquals(expectedItemLabels.size, collection.columnCount)
        assertEquals(
            AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE,
            collection.selectionMode,
        )
        assertEquals(expectedItemLabels.size, group.childVirtualViewIds.size)

        expectedItemLabels.forEachIndexed { index, label ->
            /** Current item addressed by its exact production label and Tab-compatible role. */
            val item = tree.requireM52Node(label = label, role = PixelSemanticRole.TAB)
            /** Android collection item metadata for the current visual column. */
            val itemInfo = requireNotNull(provider.requireM52Info(item).collectionItemInfo)
            assertEquals(group.virtualViewId, item.parentVirtualViewId)
            assertEquals(0, itemInfo.rowIndex)
            assertEquals(index, itemInfo.columnIndex)
            assertEquals(index == expectedSelectedIndex, itemInfo.isSelected)
        }
    }

    /** Creates one dismissible end pane used by a Slidable Android action fixture. */
    private fun m52DismissiblePane(actionLabel: String): SlidableActionPane {
        return SlidableActionPane(
            children = listOf(
                SlidableAction(
                    label = actionLabel,
                    backgroundColor = PixelColor.fromRgb(160, 48, 48),
                    foregroundColor = PixelColor.White,
                    onPressed = {},
                    key = "$actionLabel-action",
                ),
            ),
            extentRatio = 0.5f,
            dismissible = true,
        )
    }
}

/** Returns the internal Android provider owned by this real Activity-attached Host. */
private fun PixelHostView.m52AccessibilityProvider(): PixelHostAccessibilityNodeProvider {
    return accessibilityNodeProvider as PixelHostAccessibilityNodeProvider
}

/** Draws one Host frame synchronously so virtual-node changes are immediately observable. */
private fun renderM52Synchronously(host: PixelHostView) {
    /** Temporary bitmap providing the real Android Canvas required by [View.draw]. */
    val bitmap = Bitmap.createBitmap(
        host.width.coerceAtLeast(1),
        host.height.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    host.draw(Canvas(bitmap))
    bitmap.recycle()
}

/** Finds exactly one current virtual node with the requested label and semantic role. */
private fun PixelAccessibilityTreeSnapshot.requireM52Node(
    label: String,
    role: PixelSemanticRole,
): PixelAccessibilityNodeSnapshot {
    return nodes.single { snapshot -> snapshot.node.label == label && snapshot.node.role == role }
}

/** Creates Android node information for one provider snapshot or fails with its virtual ID. */
private fun PixelHostAccessibilityNodeProvider.requireM52Info(
    snapshot: PixelAccessibilityNodeSnapshot,
): AccessibilityNodeInfo {
    return requireNotNull(createAccessibilityNodeInfo(snapshot.virtualViewId)) {
        "Missing AccessibilityNodeInfo for virtual id ${snapshot.virtualViewId}."
    }
}

/** Returns the exact Android action IDs advertised by this virtual node. */
private fun AccessibilityNodeInfo.m52ActionIds(): Set<Int> {
    return actionList.mapTo(linkedSetOf()) { action -> action.id }
}

/** Accessible name of the standalone Radio fixture. */
private const val STANDALONE_RADIO_LABEL: String = "Standalone radio"

/** Accessible name of the RadioGroup collection fixture. */
private const val RADIO_GROUP_LABEL: String = "Priority options"

/** Stable business ID for the first RadioGroup option. */
private const val GROUP_ALPHA_ID: String = "alpha-id"

/** Visible and accessible label for the first RadioGroup option. */
private const val GROUP_ALPHA_LABEL: String = "Alpha priority"

/** Stable business ID for the second RadioGroup option. */
private const val GROUP_BETA_ID: String = "beta-id"

/** Visible and accessible label for the second RadioGroup option. */
private const val GROUP_BETA_LABEL: String = "Beta priority"

/** Required accessible name of the icon-only button fixture. */
private const val ICON_BUTTON_LABEL: String = "Open shortcuts"

/** Visible label supplied to the decorated TextField. */
private const val DECORATED_FIELD_LABEL: String = "Account"

/** Required-marker form of the decorated TextField's Android accessible name. */
private const val DECORATED_FIELD_ACCESSIBLE_LABEL: String = "Account *"

/** Helper deliberately hidden by the active decoration error. */
private const val DECORATED_FIELD_HIDDEN_HELPER: String = "This helper is hidden"

/** Validation message mapped to Android error and invalid-content state. */
private const val DECORATED_FIELD_ERROR: String = "Account is invalid"

/** Caller-formatted counter preserved beside the active validation error. */
private const val DECORATED_FIELD_COUNTER: String = "2/20"

/** Explicit input instruction merged into the Android hint contract. */
private const val DECORATED_FIELD_INSTRUCTION: String = "Type an account"

/** Expected merged hint after the error hides helper text but preserves the counter. */
private const val DECORATED_FIELD_EXPECTED_HINT: String = "Type an account. 2/20"

/** Replacement value sent through Android ACTION_SET_TEXT. */
private const val DECORATED_FIELD_REPLACEMENT: String = "GRACE"

/** Localized default semantic label emitted by Tabs. */
private const val TABS_GROUP_LABEL: String = "Tabs"

/** Ordered Tab labels used to verify Android item columns. */
private val TAB_LABELS: List<String> = listOf("Overview", "Details")

/** Localized default semantic label emitted by SegmentedControl. */
private const val SEGMENTED_GROUP_LABEL: String = "SegmentedControl"

/** Ordered segment labels used to verify Android item columns. */
private val SEGMENT_LABELS: List<String> = listOf("Grid", "List")

/** Accessible label of the interactive Slidable row. */
private const val NORMAL_SLIDABLE_LABEL: String = "Message actions"

/** Accessible label of the Loading Slidable row. */
private const val LOADING_SLIDABLE_LABEL: String = "Loading message actions"

/** End-pane action label for the interactive Slidable fixture. */
private const val NORMAL_SLIDABLE_ACTION_LABEL: String = "Delete message"

/** End-pane action label for the Loading Slidable fixture. */
private const val LOADING_SLIDABLE_ACTION_LABEL: String = "Delete loading message"
