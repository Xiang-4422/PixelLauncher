package com.purride.pixelui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies exact-text dispatch isolation across two simultaneously attached Android Hosts. */
@RunWith(AndroidJUnit4::class)
class PixelHostTextInputDispatchInstrumentedTest {
    /** Each Host sends supplementary and multi-code-point text only to its own focused node chain. */
    @Test
    fun twoHostsDispatchExactTextIndependently() {
        /** Exact payload trace owned by the first Host. */
        val firstPayloads = mutableListOf<String>()
        /** Exact payload trace owned by the second Host. */
        val secondPayloads = mutableListOf<String>()
        /** Stable focused widget fixture rendered by the first Host. */
        val firstFixture = TextInputDispatchFixture("first", firstPayloads)
        /** Stable focused widget fixture rendered by the second Host. */
        val secondFixture = TextInputDispatchFixture("second", secondPayloads)

        ActivityScenario.launch(PixelHostFragmentTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** First independently attached runtime-local Host. */
                val firstHost = PixelHostView(activity).apply {
                    setContent(firstFixture::build)
                }
                /** Second independently attached runtime-local Host. */
                val secondHost = PixelHostView(activity).apply {
                    setContent(secondFixture::build)
                }
                activity.rootView.addView(firstHost, fullSizeLayoutParams())
                activity.rootView.addView(secondHost, fullSizeLayoutParams())
                renderSynchronously(firstHost)
                renderSynchronously(secondHost)

                assertTrue(firstHost.dispatchPixelTextInput(PixelTextInputEvent("\uD83D\uDE00")))
                assertEquals(listOf("\uD83D\uDE00"), firstPayloads)
                assertTrue(secondPayloads.isEmpty())

                assertTrue(secondHost.dispatchPixelTextInput(PixelTextInputEvent("e\u0301")))
                assertEquals(listOf("\uD83D\uDE00"), firstPayloads)
                assertEquals(listOf("e\u0301"), secondPayloads)
            }
        }
    }

    /** Draws one retained Host frame synchronously so autofocus is established before dispatch. */
    private fun renderSynchronously(host: PixelHostView) {
        /** Disposable bitmap supplying deterministic dimensions and a Canvas to the Host. */
        val bitmap = Bitmap.createBitmap(
            host.width.coerceAtLeast(1),
            host.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        host.draw(Canvas(bitmap))
        bitmap.recycle()
    }

    /** Creates layout parameters that attach each Host across the shared Activity container. */
    private fun fullSizeLayoutParams(): ViewGroup.LayoutParams {
        return ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
}

/** Stable focused widget and exact-payload recorder owned by one Android Host runtime. */
private class TextInputDispatchFixture(
    /** Prefix keeping retained focus identities distinct across simultaneous Hosts. */
    private val label: String,
    /** Runtime-local destination for every exact String received by this fixture. */
    private val payloads: MutableList<String>,
) {
    /** Stable node that receives initial focus in this fixture's Host. */
    private val node: FocusNode = FocusNode("$label-text-input")

    /** Builds the declarative Focus overload exposing an exact-text handler. */
    fun build(): Widget {
        return Focus(
            node = node,
            autofocus = true,
            child = Text(label),
            onTextInput = { event ->
                payloads += event.text
                true
            },
        )
    }
}
