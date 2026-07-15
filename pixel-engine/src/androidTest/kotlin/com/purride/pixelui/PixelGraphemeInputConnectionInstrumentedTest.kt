package com.purride.pixelui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixelui.internal.host.PixelEngineTextInputView
import com.purride.pixelui.internal.host.PixelHostAccessibilityNodeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the production engine-owned EditText and framework InputConnection on API 24+. */
@RunWith(AndroidJUnit4::class)
class PixelGraphemeInputConnectionInstrumentedTest {
    /** Selection, composition, insertion, and both deletion units preserve exact grapheme state. */
    @Test
    fun productionConnectionNormalizesEveryBasicEditingCommand(): Unit {
        withProductionInput(initialText = "e\u0301X", initialSelection = 2) { fixture ->
            /** Framework connection returned by the real default bridge rather than a recording fake. */
            val connection = fixture.connection

            assertTrue(connection.setSelection(1, 1))
            assertEquals(2, fixture.state.selectionStart)
            assertEquals(2, fixture.state.selectionEnd)
            assertEquals(0, fixture.changedTexts.size)

            assertTrue(connection.setComposingRegion(1, 2))
            assertEquals(0, fixture.state.compositionStart)
            assertEquals(2, fixture.state.compositionEnd)
            assertEquals(0, fixture.changedTexts.size)

            assertTrue(connection.finishComposingText())
            assertEquals(-1, fixture.state.compositionStart)
            assertEquals(-1, fixture.state.compositionEnd)

            assertTrue(connection.deleteSurroundingText(1, 0))
            assertEquals("X", fixture.state.text)
            assertEquals(0, fixture.state.selectionStart)

            assertTrue(connection.commitText("😀", 1))
            assertEquals("😀X", fixture.state.text)
            assertEquals(2, fixture.state.selectionStart)

            assertTrue(connection.setSelection(1, 1))
            assertEquals(2, fixture.state.selectionStart)
            assertTrue(connection.deleteSurroundingTextInCodePoints(1, 0))
            assertEquals("X", fixture.state.text)

            assertFalse(connection.commitText("\uD83D", 1))
            assertEquals("X", fixture.state.text)

            assertTrue(connection.setSelection(0, 0))
            assertTrue(connection.commitText("e\u0301", 1))
            assertEquals("e\u0301X", fixture.state.text)
            assertEquals(4, fixture.changedTexts.size)
        }
    }

    /** An outer Android batch publishes one state while CRLF and emoji remain indivisible. */
    @Test
    fun productionConnectionCoalescesBatchAndKeepsCrLfAtomic(): Unit {
        withProductionInput(initialText = "A\r\nB", initialSelection = 3) { fixture ->
            /** Framework connection used for one nested batch of heterogeneous commands. */
            val connection = fixture.connection

            assertTrue(connection.setSelection(2, 2))
            assertEquals(3, fixture.state.selectionStart)
            assertTrue(connection.beginBatchEdit())
            assertTrue(connection.beginBatchEdit())
            assertTrue(connection.deleteSurroundingText(1, 0))
            assertTrue(connection.commitText("😀", 1))
            assertTrue(connection.setComposingRegion(2, 3))
            assertTrue(connection.endBatchEdit())
            assertEquals("A\r\nB", fixture.state.text)
            /** Delegate return differs by Android version; wrapper release/publication is invariant. */
            connection.endBatchEdit()

            assertEquals("A😀B", fixture.state.text)
            assertEquals(3, fixture.state.selectionStart)
            assertEquals(1, fixture.state.compositionStart)
            assertEquals(3, fixture.state.compositionEnd)
            assertEquals(listOf("A😀B"), fixture.changedTexts)
        }
    }

    /** A delegate-rejected begin and an unmatched end cannot retain a hidden callback batch. */
    @Test
    fun productionWrapperDoesNotHoldBatchWhenDelegateRejectsBegin(): Unit {
        withProductionInput(initialText = "A", initialSelection = 1) { fixture ->
            /** Engine-owned editor used by the wrapper under test. */
            val inputView = fixture.setup.textInputBridge.inputView as PixelEngineTextInputView
            /** Base wrapper class shared by API-specific production subclasses. */
            var wrapperClass: Class<*> = fixture.connection.javaClass
            while (wrapperClass.simpleName != "PixelGraphemeInputConnection") {
                wrapperClass = checkNotNull(wrapperClass.superclass)
            }
            /** Current generation copied from the active production wrapper. */
            val generationField = wrapperClass.getDeclaredField("generation").apply {
                isAccessible = true
            }
            /** Delegate whose BaseInputConnection.beginBatchEdit implementation returns false. */
            val rejectingDelegate = object : BaseInputConnection(inputView, true) {
                /** Shares the production Editable while retaining BaseInputConnection batch behavior. */
                override fun getEditable(): Editable? = inputView.text
            }
            /** Reflective construction reaches an otherwise unobservable platform failure branch. */
            val constructor = wrapperClass.getDeclaredConstructor(
                PixelEngineTextInputView::class.java,
                InputConnection::class.java,
                java.lang.Long.TYPE,
            ).apply {
                isAccessible = true
            }
            /** Production wrapper using the rejecting delegate and current session token. */
            val rejectingConnection = constructor.newInstance(
                inputView,
                rejectingDelegate,
                generationField.getLong(fixture.connection),
            ) as InputConnection

            assertFalse(rejectingConnection.beginBatchEdit())
            assertFalse(rejectingConnection.endBatchEdit())
            assertTrue(rejectingConnection.commitText("B", 1))
            assertEquals("AB", fixture.state.text)
            assertEquals(listOf("AB"), fixture.changedTexts)
        }
    }

    /** Host-restored and expanded composition remains visible to Android across consecutive updates. */
    @Test
    fun productionConnectionRestoresPlatformCompositionAndUpdatesItContinuously(): Unit {
        withProductionInput(initialText = "Ae\u0301B", initialSelection = 4) { fixture ->
            /** Host update that simulates a retained composition restored during target rebind. */
            fixture.setup.hostView.updateFocusedTextInput(
                text = "Ae\u0301B",
                selectionStart = 3,
                selectionEnd = 3,
                compositionStart = 1,
                compositionEnd = 3,
            )
            layoutAndRender(fixture.setup)
            /** Live Editable whose composing marker must be Android's private platform marker. */
            val editable = fixture.setup.textInputBridge.inputView.text
            assertEquals(1, BaseInputConnection.getComposingSpanStart(editable))
            assertEquals(3, BaseInputConnection.getComposingSpanEnd(editable))

            assertTrue(fixture.connection.setComposingText("😀", 1))
            assertEquals("A😀B", fixture.state.text)
            assertEquals(1, fixture.state.compositionStart)
            assertEquals(3, fixture.state.compositionEnd)

            /** Recreate a composition that begins as an Extend joined to the preceding ASCII base. */
            fixture.setup.hostView.updateFocusedTextInput(
                text = "e",
                selectionStart = 1,
                selectionEnd = 1,
            )
            layoutAndRender(fixture.setup)
            /** 同一 retained target 的原位文本替换必须保留当前 InputConnection generation。 */
            assertTrue(fixture.connection.setComposingText("\u0301", 1))
            assertEquals("e\u0301", fixture.state.text)
            assertEquals(0, fixture.state.compositionStart)
            assertEquals(
                2,
                BaseInputConnection.getComposingSpanEnd(
                    fixture.setup.textInputBridge.inputView.text,
                ),
            )

            assertTrue(fixture.connection.setComposingText("x", 1))
            assertEquals("x", fixture.state.text)
            assertEquals(0, fixture.state.compositionStart)
            assertEquals(1, fixture.state.compositionEnd)
        }
    }

    /** Surrounding delete protects composition and malformed code-point traversal is a true no-op. */
    @Test
    fun productionSurroundingDeleteProtectsCompositionAndRejectsMalformedCodePointRange(): Unit {
        withProductionInput(initialText = "Ae\u0301B", initialSelection = 3) { fixture ->
            /** Composition wider than the collapsed selection defines Android's protected middle. */
            assertTrue(fixture.connection.setComposingRegion(1, 3))
            assertTrue(fixture.connection.deleteSurroundingText(1, 1))
            assertEquals("e\u0301", fixture.state.text)
            assertEquals(0, fixture.state.compositionStart)
            assertEquals(2, fixture.state.compositionEnd)

            /** Legacy malformed state is preserved until an explicit whole-cluster edit removes it. */
            fixture.setup.hostView.updateFocusedTextInput(
                text = "A\uD83DB",
                selectionStart = 2,
                selectionEnd = 2,
            )
            layoutAndRender(fixture.setup)
            /** 同一 target 的 host 替换不得退休仍负责该 target 的当前连接。 */
            assertTrue(fixture.connection.deleteSurroundingTextInCodePoints(1, 0))
            assertEquals("A\uD83DB", fixture.state.text)
            assertEquals(2, fixture.state.selectionStart)
        }
    }

    /** Reversed ranges are ordered, stale offsets are ignored, and Shift movement retains its anchor. */
    @Test
    fun productionConnectionAcceptsReversedRangesAndMaintainsShiftAnchor(): Unit {
        withProductionInput(initialText = "ABCD", initialSelection = 2) { fixture ->
            assertTrue(fixture.connection.setSelection(4, 1))
            assertEquals(1, fixture.state.selectionStart)
            assertEquals(4, fixture.state.selectionEnd)

            assertTrue(fixture.connection.setSelection(99, 99))
            assertEquals(1, fixture.state.selectionStart)
            assertEquals(4, fixture.state.selectionEnd)

            assertTrue(fixture.connection.setComposingRegion(3, 1))
            assertEquals(1, fixture.state.compositionStart)
            assertEquals(3, fixture.state.compositionEnd)
            assertTrue(fixture.connection.finishComposingText())
            assertTrue(fixture.connection.setSelection(2, 2))

            /** Shift modifier reused across directions to prove the original anchor survives crossing. */
            val shiftState = KeyEvent.META_SHIFT_ON
            assertTrue(
                fixture.connection.sendKeyEvent(
                    KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 0, shiftState),
                ),
            )
            assertTrue(
                fixture.connection.sendKeyEvent(
                    KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 0, shiftState),
                ),
            )
            assertEquals(0, fixture.state.selectionStart)
            assertEquals(2, fixture.state.selectionEnd)
            repeat(3) {
                assertTrue(
                    fixture.connection.sendKeyEvent(
                        KeyEvent(
                            0L,
                            0L,
                            KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_DPAD_RIGHT,
                            0,
                            shiftState,
                        ),
                    ),
                )
            }
            assertEquals(2, fixture.state.selectionStart)
            assertEquals(3, fixture.state.selectionEnd)
        }
    }

    /** Rebind cannot overwrite an open batch, while replaced or closed connections become inert. */
    @Test
    fun productionBatchSurvivesRebindAndStaleConnectionsCannotMutateNextSession(): Unit {
        withProductionInput(initialText = "A", initialSelection = 1) { fixture ->
            /** Original connection kept to verify generation isolation after a replacement. */
            val originalConnection = fixture.connection
            assertTrue(originalConnection.beginBatchEdit())
            assertTrue(originalConnection.commitText("B", 1))
            assertEquals("A", fixture.state.text)

            layoutAndRender(fixture.setup)
            originalConnection.endBatchEdit()
            assertEquals("AB", fixture.state.text)

            /** A genuine concurrent controller update wins over an unpublished platform mutation. */
            assertTrue(originalConnection.beginBatchEdit())
            assertTrue(originalConnection.commitText("C", 1))
            fixture.setup.hostView.updateFocusedTextInput(
                text = "HOST",
                selectionStart = 4,
                selectionEnd = 4,
            )
            layoutAndRender(fixture.setup)
            originalConnection.endBatchEdit()
            assertEquals("HOST", fixture.state.text)
            assertEquals("HOST", fixture.setup.textInputBridge.inputView.text.toString())

            /** New framework connection retires the old generation without changing controller text. */
            val replacementConnection = checkNotNull(
                fixture.setup.textInputBridge.inputView.onCreateInputConnection(EditorInfo()),
            )
            assertFalse(originalConnection.commitText("OLD", 1))
            assertEquals("HOST", fixture.state.text)

            assertTrue(replacementConnection.beginBatchEdit())
            assertTrue(replacementConnection.commitText("C", 1))
            replacementConnection.closeConnection()
            assertEquals("HOSTC", fixture.state.text)
            assertFalse(replacementConnection.commitText("CLOSED", 1))
            assertEquals("HOSTC", fixture.state.text)
        }
    }

    /** Blur clears transient composition before the hidden editor and connection are retired. */
    @Test
    fun productionBlurEndsCompositionBeforeEditorSessionIsRetired(): Unit {
        withProductionInput(initialText = "e\u0301", initialSelection = 2) { fixture ->
            assertTrue(fixture.connection.setComposingRegion(0, 2))
            assertEquals(0, fixture.state.compositionStart)

            fixture.setup.hostView.clearFocusedTextInput()

            assertEquals(-1, fixture.state.compositionStart)
            assertEquals(-1, fixture.state.compositionEnd)
            assertEquals(-1, BaseInputConnection.getComposingSpanStart(fixture.setup.textInputBridge.inputView.text))
            assertFalse(fixture.connection.commitText("stale", 1))
            assertEquals("e\u0301", fixture.state.text)
        }
    }

    /** Switching A(composing) -> B -> A retires each generation and preserves field-local state. */
    @Test
    fun productionTargetSwitchKeepsTextFieldsAndConnectionsIsolated(): Unit {
        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Two-field retained fixture exercising one shared production hidden editor. */
                val content = ProductionMultiInputContent()
                /** Real setup whose default bridge owns the editor reused across both targets. */
                val setup = createPixelHostSetup(
                    context = activity,
                    config = PixelHostSetupConfig(content = content::build),
                )
                activity.rootView.removeAllViews()
                activity.rootView.addView(
                    setup.rootView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                try {
                    layoutAndRender(setup)
                    /** Engine-owned editor whose contents must follow the active target only. */
                    val inputView = setup.textInputBridge.inputView
                    /** Connection generation created while field A owns the editor. */
                    val firstConnection = checkNotNull(
                        inputView.onCreateInputConnection(EditorInfo()),
                    )
                    assertTrue(firstConnection.setComposingRegion(0, 2))
                    assertEquals(0, content.firstState.compositionStart)
                    assertEquals(2, content.firstState.compositionEnd)

                    content.secondNode.requestFocus()
                    layoutAndRender(setup)
                    assertTrue(content.secondNode.isFocused)
                    assertEquals(-1, content.firstState.compositionStart)
                    assertEquals(-1, content.firstState.compositionEnd)
                    assertEquals("B", inputView.text.toString())
                    assertFalse(firstConnection.commitText("STALE-A", 1))
                    assertEquals("e\u0301", content.firstState.text)

                    /** Fresh generation created after field B becomes the active editor target. */
                    val secondConnection = checkNotNull(
                        inputView.onCreateInputConnection(EditorInfo()),
                    )
                    assertTrue(secondConnection.commitText("😀", 1))
                    assertEquals("B😀", content.secondState.text)
                    assertEquals("e\u0301", content.firstState.text)

                    content.firstNode.requestFocus()
                    layoutAndRender(setup)
                    assertTrue(content.firstNode.isFocused)
                    assertEquals("e\u0301", inputView.text.toString())
                    assertFalse(secondConnection.commitText("STALE-B", 1))
                    assertEquals("B😀", content.secondState.text)

                    /** Third generation proves returning to A does not revive its retired connection. */
                    val returnedConnection = checkNotNull(
                        inputView.onCreateInputConnection(EditorInfo()),
                    )
                    assertTrue(returnedConnection.commitText("X", 1))
                    assertEquals("e\u0301X", content.firstState.text)
                    assertEquals("B😀", content.secondState.text)
                } finally {
                    setup.dispose()
                    activity.rootView.removeAllViews()
                }
            }
        }
    }

    /** API 33 attributed composition and API 34 absolute replace use their guarded subclasses. */
    @Test
    fun productionConnectionUsesAvailableAttributedOverloads(): Unit {
        withProductionInput(initialText = "Ae\u0301B", initialSelection = 4) { fixture ->
            /** Real connection class selected by the running SDK level. */
            val connection = fixture.connection
            assertTrue(connection.javaClass.name.contains("GraphemeInputConnection"))

            if (Build.VERSION.SDK_INT >= 33) {
                /** Runtime-only TextAttribute class avoids loading an API 33 type on API 24. */
                val textAttributeClass = Class.forName("android.view.inputmethod.TextAttribute")
                /** Non-null TextAttribute built reflectively without API 24 verifier references. */
                val textAttribute = Class.forName("android.view.inputmethod.TextAttribute\$Builder")
                    .getConstructor()
                    .newInstance()
                    .let { builder ->
                        builder.javaClass.getMethod("build").invoke(builder)
                    }
                /** API 33 attributed composing-region overload declared by InputConnection. */
                val setComposingRegion = InputConnection::class.java.getMethod(
                    "setComposingRegion",
                    Integer.TYPE,
                    Integer.TYPE,
                    textAttributeClass,
                )
                assertEquals(true, setComposingRegion.invoke(connection, 2, 3, null))
                assertEquals(1, fixture.state.compositionStart)
                assertEquals(3, fixture.state.compositionEnd)

                /** API 33 attributed composing-text overload with a non-null attribute payload. */
                val setComposingText = InputConnection::class.java.getMethod(
                    "setComposingText",
                    CharSequence::class.java,
                    Integer.TYPE,
                    textAttributeClass,
                )
                assertEquals(true, setComposingText.invoke(connection, "x", 1, textAttribute))
                assertEquals("AxB", fixture.state.text)

                /** API 33 attributed commit overload declared by InputConnection. */
                val commitText = InputConnection::class.java.getMethod(
                    "commitText",
                    CharSequence::class.java,
                    Integer.TYPE,
                    textAttributeClass,
                )
                assertEquals(true, commitText.invoke(connection, "😀", 1, null))
                assertEquals("A😀B", fixture.state.text)
            }

            if (Build.VERSION.SDK_INT >= 34) {
                /** Runtime-only TextAttribute class for the API 34 replace overload. */
                val textAttributeClass = Class.forName("android.view.inputmethod.TextAttribute")
                /** API 34 absolute replacement method declared by InputConnection. */
                val replaceText = InputConnection::class.java.getMethod(
                    "replaceText",
                    Integer.TYPE,
                    Integer.TYPE,
                    CharSequence::class.java,
                    Integer.TYPE,
                    textAttributeClass,
                )
                assertEquals(true, replaceText.invoke(connection, 3, 1, "e\u0301", 1, null))
                assertEquals("Ae\u0301B", fixture.state.text)

                /** Negative API 34 absolute offsets must preserve the platform exception contract. */
                val negativeFailure = runCatching {
                    replaceText.invoke(connection, -1, 1, "x", 1, null)
                }.exceptionOrNull()
                assertTrue(negativeFailure?.cause is IllegalArgumentException)
            }
        }
    }

    /** Accessibility reports the controller's snapped offsets and rebinds the hidden editor. */
    @Test
    fun productionAccessibilitySelectionUsesActualGraphemeOffsets(): Unit {
        withProductionInput(initialText = "e\u0301😀", initialSelection = 4) { fixture ->
            /** Real provider attached to the same production Host and input bridge. */
            val provider = fixture.setup.hostView.productionAccessibilityProvider()
            /** Original virtual TextField identity before an accessibility mutation and redraw. */
            val beforeSnapshot = provider.snapshotForTesting().nodes.single { snapshot ->
                snapshot.node.role == PixelSemanticRole.TEXT_FIELD
            }
            /** Selection-event payload copied synchronously before Android recycles the event. */
            var selectionEventPayload: Triple<Int, Int, Int>? = null
            provider.eventObserverForTesting = { event ->
                if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
                    selectionEventPayload = Triple(event.fromIndex, event.toIndex, event.itemCount)
                }
            }
            /** Interior offsets that intersect both a decomposed cluster and a surrogate pair. */
            val arguments = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 1)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 3)
            }

            assertTrue(
                provider.performAction(
                    beforeSnapshot.virtualViewId,
                    AccessibilityNodeInfo.ACTION_SET_SELECTION,
                    arguments,
                ),
            )
            assertEquals(0, fixture.state.selectionStart)
            assertEquals(4, fixture.state.selectionEnd)
            assertEquals(Triple(0, 4, 4), selectionEventPayload)

            /** Reversed Android arguments describe the same range and remain grapheme-safe. */
            val reversedArguments = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 3)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 1)
            }
            assertTrue(
                provider.performAction(
                    beforeSnapshot.virtualViewId,
                    AccessibilityNodeInfo.ACTION_SET_SELECTION,
                    reversedArguments,
                ),
            )
            assertEquals(0, fixture.state.selectionStart)
            assertEquals(4, fixture.state.selectionEnd)

            layoutAndRender(fixture.setup)
            /** TextField snapshot after Host rebind; its virtual id must remain stable. */
            val afterSnapshot = provider.snapshotForTesting().nodes.single { snapshot ->
                snapshot.node.role == PixelSemanticRole.TEXT_FIELD
            }
            assertEquals(beforeSnapshot.virtualViewId, afterSnapshot.virtualViewId)
            assertEquals(0, fixture.setup.textInputBridge.inputView.selectionStart)
            assertEquals(4, fixture.setup.textInputBridge.inputView.selectionEnd)
            /** Framework node exported after redraw must agree with state, editor, and event. */
            val nodeInfo = provider.createAccessibilityNodeInfo(afterSnapshot.virtualViewId)
            assertEquals(0, nodeInfo?.textSelectionStart)
            assertEquals(4, nodeInfo?.textSelectionEnd)
        }
    }

    /** Creates and attaches a real default setup, renders autofocus, and exposes its framework IC. */
    private fun withProductionInput(
        initialText: String,
        initialSelection: Int,
        assertion: (ProductionInputFixture) -> Unit,
    ) {
        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Controlled retained fixture whose callbacks prove engine synchronization. */
                val content = ProductionInputContent(initialText, initialSelection)
                /** Real public setup that owns the production text-input bridge and hidden editor. */
                val setup = createPixelHostSetup(
                    context = activity,
                    config = PixelHostSetupConfig(content = content::build),
                )
                activity.rootView.removeAllViews()
                activity.rootView.addView(
                    setup.rootView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                layoutAndRender(setup)
                /** EditorInfo populated by the concrete EditText before wrapping its connection. */
                val editorInfo = EditorInfo()
                /** Production InputConnection returned by the attached engine-owned input view. */
                val connection = setup.textInputBridge.inputView.onCreateInputConnection(editorInfo)
                assertNotNull(connection)
                try {
                    assertion(
                        ProductionInputFixture(
                            setup = setup,
                            state = content.state,
                            changedTexts = content.changedTexts,
                            connection = checkNotNull(connection),
                        ),
                    )
                } finally {
                    setup.dispose()
                    activity.rootView.removeAllViews()
                }
            }
        }
    }

    /** Gives the attached root deterministic bounds and draws one frame to consume autofocus. */
    private fun layoutAndRender(setup: PixelHostSetup) {
        /** Stable test width when Activity layout has not yet produced physical bounds. */
        val width = setup.rootView.rootView.width.coerceAtLeast(TEST_WIDTH)
        /** Stable test height when Activity layout has not yet produced physical bounds. */
        val height = setup.rootView.rootView.height.coerceAtLeast(TEST_HEIGHT)
        setup.rootView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        setup.rootView.layout(0, 0, width, height)
        /** Disposable bitmap supplying a real Canvas for the Host focus/render pass. */
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            setup.hostView.draw(Canvas(bitmap))
        } finally {
            bitmap.recycle()
        }
    }

    /** Default deterministic test width in physical Android pixels. */
    private companion object {
        /** Width large enough to render a standard TextField. */
        const val TEST_WIDTH: Int = 720

        /** Height large enough to render a standard TextField and hidden editor. */
        const val TEST_HEIGHT: Int = 1280
    }
}

/** Returns the concrete provider owned by the production Host under test. */
private fun PixelHostView.productionAccessibilityProvider(): PixelHostAccessibilityNodeProvider {
    return accessibilityNodeProvider as PixelHostAccessibilityNodeProvider
}

/** Attached retained TextField state and framework connection used by one assertion block. */
private data class ProductionInputFixture(
    /** Public setup proving the test did not inject a recording bridge. */
    val setup: PixelHostSetup,
    /** Controlled retained state updated synchronously by platform editing callbacks. */
    val state: PixelTextFieldState,
    /** Exact text callback trace; selection/composition-only edits must not append to it. */
    val changedTexts: List<String>,
    /** Real framework InputConnection returned from the default hidden editor. */
    val connection: InputConnection,
)

/** Stable content fixture that owns one autofocus TextField and its callback trace. */
private class ProductionInputContent(
    initialText: String,
    initialSelection: Int,
) {
    /** Controller shared by retained rendering and host callbacks. */
    private val controller: PixelTextFieldController = PixelTextFieldController()

    /** Grapheme-normalized state initially supplied to the production TextField. */
    val state: PixelTextFieldState = controller.create(
        initialText = initialText,
        selectionStart = initialSelection,
    )

    /** Every actual text change reported by the coordinator. */
    val changedTexts: MutableList<String> = mutableListOf()

    /** Builds the single editable focus target exercised through the framework connection. */
    fun build(): Widget {
        return TextField(
            state = state,
            controller = controller,
            placeholder = "Unicode input",
            autofocus = true,
            onChanged = changedTexts::add,
            key = "unicode-input",
        )
    }
}

/** Stable two-field fixture proving that one Android editor never merges retained target state. */
private class ProductionMultiInputContent {
    /** Controller exclusively responsible for field A's text and transient composition. */
    private val firstController: PixelTextFieldController = PixelTextFieldController()

    /** Controller exclusively responsible for field B's text and transient composition. */
    private val secondController: PixelTextFieldController = PixelTextFieldController()

    /** Initially focused decomposed-text state owned by field A. */
    val firstState: PixelTextFieldState = firstController.create(
        initialText = "e\u0301",
        selectionStart = 2,
    )

    /** Independent ASCII state owned by field B before its first edit session. */
    val secondState: PixelTextFieldState = secondController.create(
        initialText = "B",
        selectionStart = 1,
    )

    /** Logical focus identity used to return from field B to field A. */
    val firstNode: FocusNode = FocusNode("production-multi-input-a")

    /** Logical focus identity used to move the shared editor from field A to field B. */
    val secondNode: FocusNode = FocusNode("production-multi-input-b")

    /** Builds both editable targets inside one focus scope and one production Host. */
    fun build(): Widget {
        return FocusScope(
            key = "production-multi-input-scope",
            child = Column(
                children = listOf(
                    TextField(
                        state = firstState,
                        controller = firstController,
                        placeholder = "First Unicode input",
                        autofocus = true,
                        focusNode = firstNode,
                        key = "production-multi-input-a-field",
                    ),
                    TextField(
                        state = secondState,
                        controller = secondController,
                        placeholder = "Second Unicode input",
                        focusNode = secondNode,
                        key = "production-multi-input-b-field",
                    ),
                ),
            ),
        )
    }
}
