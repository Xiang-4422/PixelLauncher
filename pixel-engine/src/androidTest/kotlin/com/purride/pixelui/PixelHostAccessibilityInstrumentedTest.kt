package com.purride.pixelui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.internal.host.PixelHostAccessibilityNodeProvider
import com.purride.pixelui.state.PixelTextFieldController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/** Real Android acceptance for virtual hierarchy, state, actions, focus, events, and hover. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
class PixelHostAccessibilityInstrumentedTest {
    /** Provider maps a nested semantic tree and executes every non-text typed action. */
    @Test
    fun providerMapsHierarchyStateFocusHoverAndTypedActions() {
        var clickCount = 0
        var longClickCount = 0
        var scrollCount = 0
        var progress = 0.5f
        var dismissed = false
        var expanded = false
        var customCount = 0

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = activity.hostView
                val provider = host.accessibilityProvider()
                val eventTypes = mutableListOf<Int>()
                provider.eventObserverForTesting = { event -> eventTypes += event.eventType }
                host.setContent {
                    Semantics(
                        label = "SETTINGS",
                        role = PixelSemanticRole.LIST,
                        collectionInfo = PixelSemanticsCollectionInfo(
                            rowCount = 1,
                            columnCount = 1,
                            selectionMode = PixelSemanticsSelectionMode.SINGLE,
                        ),
                        actions = PixelSemanticsActions(
                            onScrollForward = {
                                scrollCount += 1
                                true
                            },
                        ),
                        child = Semantics(
                            label = "VOLUME",
                            role = PixelSemanticRole.SLIDER,
                            value = "${(progress * 100).toInt()}%",
                            selected = true,
                            expanded = expanded,
                            rangeInfo = PixelSemanticsRangeInfo(
                                current = progress,
                                minimum = 0f,
                                maximum = 1f,
                                steps = 4,
                            ),
                            collectionItemInfo = PixelSemanticsCollectionItemInfo(
                                rowIndex = 0,
                                columnIndex = 0,
                                selected = true,
                            ),
                            actions = PixelSemanticsActions(
                                onClick = {
                                    clickCount += 1
                                    true
                                },
                                onLongClick = {
                                    longClickCount += 1
                                    true
                                },
                                onSetProgress = { requested ->
                                    progress = requested
                                    true
                                },
                                onDismiss = {
                                    dismissed = true
                                    true
                                },
                                onExpand = {
                                    expanded = true
                                    true
                                },
                                onCollapse = {
                                    expanded = false
                                    true
                                },
                                customActions = listOf(
                                    PixelSemanticsCustomAction(
                                        id = "archive",
                                        label = "Archive",
                                        onInvoke = {
                                            customCount += 1
                                            true
                                        },
                                    ),
                                ),
                            ),
                            excludeDescendants = true,
                            child = Container(
                                width = 8,
                                height = 6,
                                fillColor = PixelColor.White,
                                borderColor = null,
                            ),
                        ),
                    )
                }
                renderSynchronously(host)
                eventTypes.clear()

                val tree = provider.snapshotForTesting()
                val list = tree.nodes.single { snapshot -> snapshot.node.role == PixelSemanticRole.LIST }
                val slider = tree.nodes.single { snapshot -> snapshot.node.role == PixelSemanticRole.SLIDER }
                assertEquals(list.virtualViewId, slider.parentVirtualViewId)
                assertEquals(listOf(slider.virtualViewId), list.childVirtualViewIds)
                assertEquals(listOf(list.virtualViewId), tree.rootVirtualViewIds)

                val hostInfo = provider.createAccessibilityNodeInfo(android.view.View.NO_ID)!!
                val listInfo = provider.createAccessibilityNodeInfo(list.virtualViewId)!!
                val sliderInfo = provider.createAccessibilityNodeInfo(slider.virtualViewId)!!
                assertEquals(1, hostInfo.childCount)
                assertEquals("android.widget.ListView", listInfo.className.toString())
                assertEquals(1, listInfo.collectionInfo.rowCount)
                assertEquals("android.widget.SeekBar", sliderInfo.className.toString())
                assertTrue(sliderInfo.isSelected)
                assertEquals(0.5f, sliderInfo.rangeInfo.current, 0f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    assertEquals("50%", sliderInfo.stateDescription.toString())
                }

                assertTrue(provider.performAction(slider.virtualViewId, AccessibilityNodeInfo.ACTION_CLICK, null))
                assertTrue(
                    provider.performAction(slider.virtualViewId, AccessibilityNodeInfo.ACTION_LONG_CLICK, null),
                )
                assertTrue(
                    provider.performAction(list.virtualViewId, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null),
                )
                val progressArguments = Bundle().apply {
                    putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 0.75f)
                }
                assertTrue(
                    provider.performAction(
                        slider.virtualViewId,
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
                        progressArguments,
                    ),
                )
                assertTrue(provider.performAction(slider.virtualViewId, AccessibilityNodeInfo.ACTION_EXPAND, null))
                renderSynchronously(host)
                assertTrue(
                    provider.performAction(
                        provider.snapshotForTesting().nodes.single { it.node.role == PixelSemanticRole.SLIDER }
                            .virtualViewId,
                        AccessibilityNodeInfo.ACTION_COLLAPSE,
                        null,
                    ),
                )
                assertTrue(provider.performAction(slider.virtualViewId, AccessibilityNodeInfo.ACTION_DISMISS, null))
                val customActionId = sliderInfo.actionList
                    .single { action -> action.label?.toString() == "Archive" }
                    .id
                assertTrue(provider.performAction(slider.virtualViewId, customActionId, null))

                assertEquals(1, clickCount)
                assertEquals(1, longClickCount)
                assertEquals(1, scrollCount)
                assertEquals(0.75f, progress, 0f)
                assertTrue(dismissed)
                assertFalse(expanded)
                assertEquals(1, customCount)
                assertTrue(AccessibilityEvent.TYPE_VIEW_CLICKED in eventTypes)
                assertTrue(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED in eventTypes)
                assertTrue(AccessibilityEvent.TYPE_VIEW_SCROLLED in eventTypes)

                assertTrue(
                    provider.performAction(
                        slider.virtualViewId,
                        AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                        null,
                    ),
                )
                val focused = provider.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                assertNotNull(focused)
                assertTrue(focused!!.isAccessibilityFocused)
                assertTrue(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED in eventTypes)
                assertTrue(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED in eventTypes)
                assertTrue(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED in eventTypes)

                eventTypes.clear()
                val centerX = (slider.bounds.left + slider.bounds.right) / 2f
                val centerY = (slider.bounds.top + slider.bounds.bottom) / 2f
                host.onHoverEvent(hoverEvent(MotionEvent.ACTION_HOVER_ENTER, centerX, centerY))
                host.onHoverEvent(hoverEvent(MotionEvent.ACTION_HOVER_EXIT, centerX, centerY))
                assertEquals(
                    listOf(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT),
                    eventTypes,
                )

                host.pause()
                assertFalse(provider.performAction(slider.virtualViewId, AccessibilityNodeInfo.ACTION_CLICK, null))
                host.resume()
            }
        }
    }

    /** TextField accessibility click uses exact render-source ownership and supports editing actions. */
    @Test
    fun textFieldActionsFocusExactHostTargetAndMutateTextSelectionClipboard() {
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "HELLO", selectionStart = 1, selectionEnd = 4)
        val bridge = RecordingHostBridge()
        val changedValues = mutableListOf<String>()

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = activity.hostView
                host.hostBridge = bridge
                val provider = host.accessibilityProvider()
                val eventTypes = mutableListOf<Int>()
                provider.eventObserverForTesting = { event -> eventTypes += event.eventType }
                host.setContent {
                    TextField(
                        state = state,
                        controller = controller,
                        semanticLabel = "NAME",
                        semanticHint = "Enter a name",
                        onChanged = changedValues::add,
                        key = "name-field",
                    )
                }
                renderSynchronously(host)
                eventTypes.clear()
                val field = provider.snapshotForTesting().nodes.single { snapshot ->
                    snapshot.node.role == PixelSemanticRole.TEXT_FIELD
                }
                val fieldInfo = provider.createAccessibilityNodeInfo(field.virtualViewId)!!
                assertEquals("HELLO", fieldInfo.text.toString())
                assertEquals("NAME", fieldInfo.contentDescription.toString())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    assertEquals("Enter a name", fieldInfo.hintText.toString())
                }

                assertTrue(provider.performAction(field.virtualViewId, AccessibilityNodeInfo.ACTION_CLICK, null))
                assertTrue(state.isFocused)
                assertEquals(1, bridge.showTextInputCount)

                val setTextArguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "WORLD")
                }
                assertTrue(
                    provider.performAction(field.virtualViewId, AccessibilityNodeInfo.ACTION_SET_TEXT, setTextArguments),
                )
                assertEquals("WORLD", state.text)

                val selectionArguments = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 1)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 3)
                }
                assertTrue(
                    provider.performAction(
                        field.virtualViewId,
                        AccessibilityNodeInfo.ACTION_SET_SELECTION,
                        selectionArguments,
                    ),
                )
                assertEquals(1, state.selectionStart)
                assertEquals(3, state.selectionEnd)
                assertTrue(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED in eventTypes)
                assertTrue(AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED in eventTypes)

                assertTrue(provider.performAction(field.virtualViewId, AccessibilityNodeInfo.ACTION_COPY, null))
                assertEquals("OR", bridge.clipboardText)
                assertTrue(provider.performAction(field.virtualViewId, AccessibilityNodeInfo.ACTION_CUT, null))
                assertEquals("WLD", state.text)
                bridge.clipboardText = "XY"
                assertTrue(provider.performAction(field.virtualViewId, AccessibilityNodeInfo.ACTION_PASTE, null))
                assertEquals("WXYLD", state.text)
                assertEquals(listOf("WORLD", "WLD", "WXYLD"), changedValues)
            }
        }
    }

    /** API 26 character extras reuse grapheme-safe mixed-Bidi TextField paragraph geometry. */
    @Suppress("DEPRECATION")
    @SdkSuppress(minSdkVersion = 26)
    @Test
    fun textFieldCharacterLocationsShareClusterAndBidiGeometry() {
        /** Combining sequence, supplementary emoji, and RTL run addressed by UTF-16 offsets. */
        val text = "e\u0301🙂אב"
        /** Controlled field retaining the exact non-normalized backing text. */
        val controller = PixelTextFieldController()
        /** Editable state exported by both TextField semantics and its input target. */
        val state = controller.create(initialText = text, selectionStart = 0, selectionEnd = 0)

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Real Pixel Host whose physical grid transform defines screen-space bounds. */
                val host = activity.hostView
                /** Production virtual-node provider under test. */
                val provider = host.accessibilityProvider()
                host.setContent {
                    TextField(
                        state = state,
                        controller = controller,
                        semanticLabel = "UNICODE",
                        key = "unicode-field",
                    )
                }
                renderSynchronously(host)
                /** Stable virtual TextField snapshot for the committed render frame. */
                val field = provider.snapshotForTesting().nodes.single { snapshot ->
                    snapshot.node.role == PixelSemanticRole.TEXT_FIELD
                }
                /** Framework node receiving the requested extra-data result. */
                val fieldInfo = provider.createAccessibilityNodeInfo(field.virtualViewId)!!
                assertTrue(
                    fieldInfo.availableExtraData.contains(
                        AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
                    ),
                )
                /** Request includes two beyond-text slots that must remain null. */
                val arguments = Bundle().apply {
                    putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
                    putInt(
                        AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH,
                        text.length + 2,
                    )
                }
                provider.addExtraDataToAccessibilityNodeInfo(
                    field.virtualViewId,
                    fieldInfo,
                    AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
                    arguments,
                )
                /** Nullable RectF array indexed exactly like Android's exposed CharSequence. */
                val locations = requireNotNull(
                    fieldInfo.extras.getParcelableArray(
                        AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
                    ),
                ).map { item -> item as RectF? }
                /** Screen-space field clip used to validate the provider coordinate transform. */
                val fieldBounds = Rect()
                fieldInfo.getBoundsInScreen(fieldBounds)
                /** Exact input target whose paragraph callback also drives pointer hit testing. */
                val inputTarget = host.lastRenderResult!!.textInputTargets.single()
                /** Absolute logical rectangles exported directly from the shared RenderText. */
                val logicalLocations = inputTarget.characterBoundsForRange!!.invoke(0, text.length)
                /** Physical grid transform used by Host paint and accessibility conversion. */
                val geometry = host.resolveGridGeometry()!!
                /** Host screen origin added after logical-to-physical grid scaling. */
                val hostScreenOffset = IntArray(2)
                host.getLocationOnScreen(hostScreenOffset)
                /** First cluster's expected screen left edge from the input target callback. */
                val expectedFirstLeft = (
                    hostScreenOffset[0] +
                        geometry.originX +
                        requireNotNull(logicalLocations[0]).left * geometry.cellSize
                    ).roundToInt().toFloat()

                assertEquals(text.length + 2, locations.size)
                assertEquals(locations[0], locations[1])
                assertEquals(locations[2], locations[3])
                assertTrue(requireNotNull(locations[5]).left < requireNotNull(locations[4]).left)
                assertEquals(expectedFirstLeft, requireNotNull(locations[0]).left, 0f)
                assertTrue(requireNotNull(locations[0]).left >= fieldBounds.left)
                assertTrue(requireNotNull(locations[0]).top >= fieldBounds.top)
                /** Pointer lookup at the Hebrew glyph center must return a grapheme boundary. */
                val hebrewRect = requireNotNull(logicalLocations[5])
                /** Logical hit-test index returned from the same paragraph visual rectangle. */
                val hitIndex = inputTarget.textIndexAt!!.invoke(
                    hebrewRect.left + hebrewRect.width / 2,
                    hebrewRect.top + hebrewRect.height / 2,
                )
                assertTrue(PixelGraphemeBoundaryMap(text).isBoundary(hitIndex))
                assertEquals(null, locations[6])
                assertEquals(null, locations[7])
            }
        }
    }

    /** Plain paragraph semantics advertises the same grapheme-safe character-location contract. */
    @Suppress("DEPRECATION")
    @SdkSuppress(minSdkVersion = 26)
    @Test
    fun plainTextCharacterLocationsUseParagraphGeometry() {
        /** Non-editable paragraph retaining a combining cluster followed by a Hebrew run. */
        val text = "e\u0301 אב"
        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Real Host exporting RenderText directly as a semantic TEXT node. */
                val host = activity.hostView
                /** Production provider converting logical paragraph geometry to screen RectF. */
                val provider = host.accessibilityProvider()
                host.setContent { Text(text) }
                renderSynchronously(host)
                /** Plain text virtual node carrying the RenderText character resolver. */
                val paragraph = provider.snapshotForTesting().nodes.single { snapshot ->
                    snapshot.node.role == PixelSemanticRole.TEXT
                }
                /** Framework node receiving the exact-length character-location array. */
                val paragraphInfo = provider.createAccessibilityNodeInfo(paragraph.virtualViewId)!!
                assertTrue(
                    paragraphInfo.availableExtraData.contains(
                        AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
                    ),
                )
                /** Full UTF-16 range requested through the platform provider API. */
                val arguments = Bundle().apply {
                    putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
                    putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, text.length)
                }
                provider.addExtraDataToAccessibilityNodeInfo(
                    paragraph.virtualViewId,
                    paragraphInfo,
                    AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
                    arguments,
                )
                /** Screen rectangles returned in logical UTF-16 order rather than visual order. */
                val locations = requireNotNull(
                    paragraphInfo.extras.getParcelableArray(
                        AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
                    ),
                ).map { item -> item as RectF? }

                assertEquals(text.length, locations.size)
                assertEquals(locations[0], locations[1])
                assertTrue(requireNotNull(locations[4]).left < requireNotNull(locations[3]).left)
            }
        }
    }

    /** Accessibility focus and action ownership survive keyed insertion, deletion, and reversal. */
    @Test
    fun dynamicReorderKeepsFocusedLogicalItemAndNeverReusesRemovedVirtualId() {
        var items = listOf(
            AccessibilityItem("a", "A"),
            AccessibilityItem("b", "B"),
            AccessibilityItem("c", "C"),
        )
        var clickedValue: String? = null

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = activity.hostView
                host.setContent {
                    Column(
                        children = items.map { item ->
                            Semantics(
                                label = "ROW",
                                value = item.value,
                                role = PixelSemanticRole.BUTTON,
                                excludeDescendants = true,
                                actions = PixelSemanticsActions(
                                    onClick = {
                                        clickedValue = item.value
                                        true
                                    },
                                ),
                                child = Container(
                                    width = 8,
                                    height = 3,
                                    fillColor = PixelColor.White,
                                    borderColor = null,
                                ),
                                key = item.key,
                            )
                        },
                    )
                }
                renderSynchronously(host)
                val provider = host.accessibilityProvider()
                val originalB = provider.snapshotForTesting().nodes.single { snapshot ->
                    snapshot.node.value == "B"
                }
                assertTrue(
                    provider.performAction(
                        originalB.virtualViewId,
                        AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                        null,
                    ),
                )

                items = listOf(AccessibilityItem("x", "X")) + items
                host.invalidate()
                renderSynchronously(host)
                val insertedB = provider.snapshotForTesting().nodes.single { snapshot ->
                    snapshot.node.value == "B"
                }
                assertEquals(originalB.virtualViewId, insertedB.virtualViewId)
                val focusedB = provider.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)!!
                assertTrue(focusedB.isAccessibilityFocused)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    assertEquals("B", focusedB.stateDescription.toString())
                } else {
                    assertEquals("B", focusedB.text.toString())
                }

                items = listOf(
                    AccessibilityItem("c", "C"),
                    AccessibilityItem("b", "B"),
                    AccessibilityItem("a", "A"),
                )
                host.invalidate()
                renderSynchronously(host)
                val reversedB = provider.snapshotForTesting().nodes.single { snapshot ->
                    snapshot.node.value == "B"
                }
                assertEquals(originalB.virtualViewId, reversedB.virtualViewId)
                assertTrue(
                    provider.performAction(reversedB.virtualViewId, AccessibilityNodeInfo.ACTION_CLICK, null),
                )
                assertEquals("B", clickedValue)

                items = listOf(AccessibilityItem("c", "C"), AccessibilityItem("a", "A"))
                host.invalidate()
                renderSynchronously(host)
                assertEquals(null, provider.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
                assertFalse(
                    provider.performAction(originalB.virtualViewId, AccessibilityNodeInfo.ACTION_CLICK, null),
                )

                items = items + AccessibilityItem("d", "D")
                host.invalidate()
                renderSynchronously(host)
                val newD = provider.snapshotForTesting().nodes.single { snapshot -> snapshot.node.value == "D" }
                assertNotEquals(originalB.virtualViewId, newD.virtualViewId)
                assertTrue(newD.virtualViewId > originalB.virtualViewId)
            }
        }
    }

    /** Returns the internal Provider instance owned by this real Host. */
    private fun PixelHostView.accessibilityProvider(): PixelHostAccessibilityNodeProvider {
        return accessibilityNodeProvider as PixelHostAccessibilityNodeProvider
    }

    /** Creates one touchscreen hover event in Host physical coordinates. */
    private fun hoverEvent(action: Int, x: Float, y: Float): MotionEvent {
        return MotionEvent.obtain(0L, 0L, action, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
    }

    /** Draws a Host frame synchronously so Provider snapshots and events are deterministic. */
    private fun renderSynchronously(host: PixelHostView) {
        val bitmap = Bitmap.createBitmap(
            host.width.coerceAtLeast(1),
            host.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        host.draw(Canvas(bitmap))
        bitmap.recycle()
    }
}

/** Business-keyed dynamic item used to prove stable virtual identity under reorder. */
private data class AccessibilityItem(
    /** Stable widget and logical-item key. */
    val key: String,
    /** Distinct semantic value while every item deliberately shares one label. */
    val value: String,
)

/** Minimal deterministic Host bridge for TextField focus and clipboard instrumentation. */
private class RecordingHostBridge : PixelHostBridge {
    /** Number of Host-coordinated text focus requests. */
    var showTextInputCount: Int = 0

    /** In-memory clipboard used by copy, cut, and paste actions. */
    var clipboardText: String? = null

    /** Records one text-input focus request without opening a real IME. */
    override fun showTextInput(request: PixelTextInputRequest) {
        showTextInputCount += 1
    }

    /** No real IME is retained by this bridge. */
    override fun hideTextInput(): Unit = Unit

    /** Haptic output is outside this accessibility test. */
    override fun performHapticFeedback(type: PixelHapticType): Unit = Unit

    /** Host frames are drawn explicitly by the test. */
    override fun requestFrame(): Unit = Unit

    /** System actions are outside this accessibility test. */
    override fun dispatchSystemAction(action: PixelSystemAction): Unit = Unit

    /** Returns the deterministic in-memory clipboard value. */
    override fun readClipboardText(): String? = clipboardText

    /** Replaces the deterministic in-memory clipboard value. */
    override fun writeClipboardText(text: String) {
        clipboardText = text
    }
}
