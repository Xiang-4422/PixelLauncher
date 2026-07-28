package com.purride.pixelui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.purride.pixelengine.PixelEngine
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises runtime-local keyboard and IME ownership with two live Hosts in one Activity.
 */
@RunWith(AndroidJUnit4::class)
class PixelHostMultiHostFocusInstrumentedTest {
    /** Normalized keys and Android DPAD mapping affect only the Host receiving the event. */
    @Test
    fun twoHostsDispatchTraversalAndActivationIndependently() {
        /** First Host's stable focus nodes and action counters. */
        val firstFixture = KeyboardFocusFixture("first")
        /** Second Host's stable focus nodes and action counters. */
        val secondFixture = KeyboardFocusFixture("second")

        ActivityScenario.launch(PixelHostFragmentTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** First independently rendered Host receiving direct normalized key events. */
                val firstHost = PixelHostView(activity).apply {
                    setContent(firstFixture::build)
                }
                /** Second independently rendered Host receiving Android DPAD events. */
                val secondHost = PixelHostView(activity).apply {
                    setContent(secondFixture::build)
                }
                activity.rootView.addView(firstHost, fullSizeLayoutParams())
                activity.rootView.addView(secondHost, fullSizeLayoutParams())
                renderSynchronously(firstHost)
                renderSynchronously(secondHost)

                assertTrue(firstFixture.firstNode.isFocused)
                assertTrue(secondFixture.firstNode.isFocused)

                assertTrue(firstHost.dispatchPixelKeyEvent(PixelKeyEvent(PixelKey.ENTER)))
                assertEquals(1, firstFixture.firstActivationCount)
                assertEquals(0, secondFixture.firstActivationCount)

                assertTrue(firstHost.dispatchPixelKeyEvent(PixelKeyEvent(PixelKey.TAB)))
                assertTrue(firstFixture.secondNode.isFocused)
                assertTrue(secondFixture.firstNode.isFocused)
                assertTrue(firstHost.dispatchPixelKeyEvent(PixelKeyEvent(PixelKey.SPACE)))
                assertEquals(1, firstFixture.secondActivationCount)
                assertEquals(0, secondFixture.secondActivationCount)

                /** Physical DPAD event normalized by the second Host's Android adapter. */
                val dpadRight = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
                assertTrue(secondHost.dispatchKeyEvent(dpadRight))
                assertTrue(secondFixture.secondNode.isFocused)
                assertTrue(firstFixture.secondNode.isFocused)
                assertTrue(secondHost.dispatchPixelKeyEvent(PixelKeyEvent(PixelKey.ENTER)))
                assertEquals(1, secondFixture.secondActivationCount)
                assertEquals(1, firstFixture.secondActivationCount)
            }
        }
    }

    /** Text updates, IME NEXT, clearing, and destroying Host A never mutate Host B. */
    @Test
    fun twoHostsKeepTextInputAndImeLifecycleIndependent() {
        /** Text state, focus nodes, and submission trace owned by Host A. */
        val firstFixture = TextInputFocusFixture("first")
        /** Text state, focus nodes, and submission trace owned by Host B. */
        val secondFixture = TextInputFocusFixture("second")
        /** IME capability recording only sessions originating from Host A. */
        val firstIme = MultiHostRecordingImeCapability()
        /** IME capability recording only sessions originating from Host B. */
        val secondIme = MultiHostRecordingImeCapability()

        ActivityScenario.launch(PixelHostFragmentTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Host A connected exclusively to [firstIme] through its own Engine. */
                val firstHost = PixelHostView(activity).apply {
                    engine = engineWithIme(firstIme)
                    setContent(firstFixture::build)
                }
                /** Host B connected exclusively to [secondIme] through its own Engine. */
                val secondHost = PixelHostView(activity).apply {
                    engine = engineWithIme(secondIme)
                    setContent(secondFixture::build)
                }
                activity.rootView.addView(firstHost, fullSizeLayoutParams())
                activity.rootView.addView(secondHost, fullSizeLayoutParams())
                renderSynchronously(firstHost)
                renderSynchronously(secondHost)

                assertTrue(firstFixture.fieldNode.isFocused)
                assertTrue(secondFixture.fieldNode.isFocused)
                assertTrue(firstIme.showSessions.isNotEmpty())
                assertTrue(secondIme.showSessions.isNotEmpty())
                assertTrue(firstIme.showSessions.all { session -> session.request.action == PixelTextInputAction.NEXT })
                assertTrue(secondIme.showSessions.all { session -> session.request.action == PixelTextInputAction.NEXT })
                /** Host B show-request count that must remain stable while only Host A is operated. */
                val secondInitialShowCount = secondIme.showSessions.size
                /** Host A lifecycle hide count before this test explicitly clears its field. */
                val firstInitialHideCount = firstIme.hideCount
                /** Host B lifecycle hide count that must remain unchanged by every Host A action. */
                val secondInitialHideCount = secondIme.hideCount

                firstHost.updateFocusedTextInput("Alpha")
                assertEquals("Alpha", firstFixture.fieldState.text)
                assertEquals("", secondFixture.fieldState.text)
                assertEquals(secondInitialShowCount, secondIme.showSessions.size)

                firstHost.submitFocusedTextInput()
                assertEquals("Alpha", firstFixture.lastSubmittedText)
                assertEquals("", secondFixture.lastSubmittedText)
                assertTrue(firstFixture.nextNode.isFocused)
                assertTrue(secondFixture.fieldNode.isFocused)
                assertEquals(secondInitialShowCount, secondIme.showSessions.size)

                firstHost.clearFocusedTextInput()
                assertFalse(firstFixture.fieldState.isFocused)
                assertTrue(secondFixture.fieldState.isFocused)
                assertTrue(firstIme.hideCount > firstInitialHideCount)
                assertEquals(secondInitialHideCount, secondIme.hideCount)

                firstHost.destroy()
                assertTrue(secondFixture.fieldNode.isFocused)
                assertTrue(secondFixture.fieldState.isFocused)
                assertEquals(secondInitialHideCount, secondIme.hideCount)

                secondHost.updateFocusedTextInput("Beta")
                assertEquals("Beta", secondFixture.fieldState.text)
                assertEquals("Alpha", firstFixture.fieldState.text)
            }
        }
    }

    /** Draws one retained frame synchronously on the Activity main thread. */
    private fun renderSynchronously(host: PixelHostView) {
        /** Temporary bitmap supplying a deterministic Canvas for the retained Host frame. */
        val bitmap = Bitmap.createBitmap(
            host.width.coerceAtLeast(1),
            host.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        host.draw(Canvas(bitmap))
        bitmap.recycle()
    }

    /** Creates a Host layout that fills the shared instrumentation container. */
    private fun fullSizeLayoutParams(): ViewGroup.LayoutParams {
        return ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
}

/** Retained two-node keyboard fixture with independent activation counters. */
private class KeyboardFocusFixture(
    /** Prefix used to keep debug labels distinct across simultaneous Hosts. */
    private val labelPrefix: String,
) {
    /** First node, initially focused in this fixture's runtime. */
    val firstNode: FocusNode = FocusNode("$labelPrefix-first")

    /** Second node reached through Tab or right-direction traversal. */
    val secondNode: FocusNode = FocusNode("$labelPrefix-second")

    /** Number of Enter or Space activations handled by [firstNode]. */
    var firstActivationCount: Int = 0

    /** Number of Enter or Space activations handled by [secondNode]. */
    var secondActivationCount: Int = 0

    /** Builds a stable focus scope whose callbacks mutate only this fixture. */
    fun build(): Widget {
        return FocusScope(
            key = "$labelPrefix-scope",
            child = Column(
                children = listOf(
                    Focus(
                        node = firstNode,
                        autofocus = true,
                        onKeyEvent = activationHandler { firstActivationCount += 1 },
                        key = "$labelPrefix-first-focus",
                        child = Text("$labelPrefix FIRST"),
                    ),
                    Focus(
                        node = secondNode,
                        onKeyEvent = activationHandler { secondActivationCount += 1 },
                        key = "$labelPrefix-second-focus",
                        child = Text("$labelPrefix SECOND"),
                    ),
                ),
            ),
        )
    }

    /** Converts one activation callback into an Enter/Space focus handler. */
    private fun activationHandler(action: () -> Unit): (PixelKeyEvent) -> Boolean = { event ->
        when (event.key) {
            PixelKey.ENTER,
            PixelKey.SPACE,
            -> {
                action()
                true
            }
            else -> false
        }
    }
}

/** Retained TextField fixture used to distinguish logical focus, state, and submission by Host. */
private class TextInputFocusFixture(
    /** Prefix used for stable keys and diagnostics in one Host tree. */
    private val labelPrefix: String,
) {
    /** Controller exclusively mutating [fieldState]. */
    val fieldController: PixelTextFieldController = PixelTextFieldController()

    /** Editable state exclusively owned by this fixture. */
    val fieldState: PixelTextFieldState = fieldController.create()

    /** Logical focus node shared by the field's IME and runtime focus paths. */
    val fieldNode: FocusNode = FocusNode("$labelPrefix-field")

    /** Next logical focus destination for the field's IME NEXT action. */
    val nextNode: FocusNode = FocusNode("$labelPrefix-next")

    /** Last text submitted through this fixture's Host, or empty before submission. */
    var lastSubmittedText: String = ""

    /** Builds one autofocus TextField followed by a distinct NEXT destination. */
    fun build(): Widget {
        return FocusScope(
            key = "$labelPrefix-text-scope",
            child = Column(
                children = listOf(
                    TextField(
                        state = fieldState,
                        controller = fieldController,
                        placeholder = "$labelPrefix FIELD",
                        autofocus = true,
                        textInputAction = PixelTextInputAction.NEXT,
                        onSubmitted = { submitted -> lastSubmittedText = submitted },
                        focusNode = fieldNode,
                        key = "$labelPrefix-field-widget",
                    ),
                    Focus(
                        node = nextNode,
                        key = "$labelPrefix-next-focus",
                        child = Text("$labelPrefix NEXT"),
                    ),
                ),
            ),
        )
    }
}

/** 只记录本测试关心的 IME 归属信号的最小 capability 实现。 */
private class MultiHostRecordingImeCapability : PixelImeCapability {
    /** All IME show sessions received from this capability's Host. */
    val showSessions: MutableList<PixelTextEditingSession> = mutableListOf()

    /** All non-restarting IME update sessions received from this capability's Host. */
    val updateSessions: MutableList<PixelTextEditingSession> = mutableListOf()

    /** Number of hide requests received from this capability's Host. */
    var hideCount: Int = 0

    /** Records one Host-owned IME show request. */
    override fun showTextInput(session: PixelTextEditingSession) {
        showSessions += session
    }

    /** Records one Host-owned non-restarting IME update. */
    override fun updateTextInput(session: PixelTextEditingSession) {
        updateSessions += session
    }

    /** Records one Host-owned IME hide request. */
    override fun hideTextInput() {
        hideCount += 1
    }
}

/** 为单个 Host 组装一个只声明 IME capability 的独立 Engine。 */
private fun engineWithIme(ime: PixelImeCapability): PixelEngine {
    return PixelEngine.Builder()
        .hostServices(PixelHostCapabilitySet(ime = ime))
        .build()
}
