package com.purride.pixelui

import com.purride.pixelui.internal.PixelUiRuntime
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies adaptive dp classes, immutable features and retained state across environment changes. */
class AdaptiveBuilderTest {
    /** Physical dp classification and defensive display-feature ownership are deterministic. */
    @Test
    fun adaptiveDataDerivesDpClassesOrientationAndOwnsFeatureSnapshot() {
        /** Mutable caller list used to prove defensive ownership. */
        val mutableFeatures = mutableListOf(
            PixelDisplayFeature(
                bounds = PixelLogicalRect(left = 50f, top = 0f, right = 51f, bottom = 40f),
                type = PixelDisplayFeatureType.HINGE,
                state = PixelDisplayFeatureState.HALF_OPENED,
            ),
        )
        /** Landscape 600dp-by-400dp adaptive snapshot. */
        val data = PixelAdaptiveLayoutData(
            physicalWidthPx = 1200,
            physicalHeightPx = 800,
            logicalWidth = 100,
            logicalHeight = 40,
            density = 2f,
            displayFeatures = mutableFeatures,
        )
        mutableFeatures.clear()

        assertEquals(600f, data.widthDp, 0f)
        assertEquals(400f, data.heightDp, 0f)
        assertEquals(PixelWindowSizeClass.MEDIUM, data.widthSizeClass)
        assertEquals(PixelWindowSizeClass.COMPACT, data.heightSizeClass)
        assertEquals(PixelWindowOrientation.LANDSCAPE, data.orientation)
        assertEquals(1, data.displayFeatures.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (data.displayFeatures as MutableList).clear()
        }
    }

    /** Invalid physical, logical or density inputs fail before an adaptive builder observes them. */
    @Test
    fun adaptiveDataRejectsInvalidGeometry() {
        assertThrows(IllegalArgumentException::class.java) {
            adaptiveData(physicalWidthPx = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            adaptiveData(logicalHeight = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            adaptiveData(density = Float.NaN)
        }
    }

    /** Resize, IME, safe-area and hinge changes rebuild without losing focus, scroll or selection. */
    @Test
    fun adaptiveBuilderRebuildPreservesRetainedFocusScrollAndTextSelection() {
        /** Adaptive snapshots received directly by the public builder callback. */
        val builderSnapshots = mutableListOf<PixelAdaptiveLayoutData>()
        /** State allocations and terminal disposal tracked across both environments. */
        val tracker = AdaptiveRetainedTracker()
        /** Stable builder instance retained below consecutive environment providers. */
        val builder = AdaptiveBuilder(
            builder = { _, data ->
                builderSnapshots += data
                AdaptiveRetainedProbe(
                    tracker = tracker,
                    key = "adaptive-retained-probe",
                )
            },
            key = "adaptive-builder",
        )
        /** Initial portrait compact/expanded environment with stable top safe area. */
        val initial = PixelAdaptiveLayoutData(
            physicalWidthPx = 500,
            physicalHeightPx = 1000,
            logicalWidth = 50,
            logicalHeight = 100,
            density = 1f,
            viewPadding = PixelWindowInsets(top = 4),
            padding = PixelWindowInsets(top = 4),
        )
        /** Resized landscape environment with IME, right cutout padding and a half-opened hinge. */
        val resized = PixelAdaptiveLayoutData(
            physicalWidthPx = 1400,
            physicalHeightPx = 1000,
            logicalWidth = 70,
            logicalHeight = 50,
            density = 2f,
            viewInsets = PixelWindowInsets(bottom = 12),
            viewPadding = PixelWindowInsets(top = 2, right = 3),
            padding = PixelWindowInsets(top = 2, right = 3),
            displayFeatures = listOf(
                PixelDisplayFeature(
                    bounds = PixelLogicalRect(left = 34f, top = 0f, right = 36f, bottom = 50f),
                    type = PixelDisplayFeatureType.FOLD,
                    state = PixelDisplayFeatureState.HALF_OPENED,
                ),
            ),
        )
        /** Retained runtime reconciling the same adaptive builder under two providers. */
        val runtime = PixelUiRuntime()

        try {
            runtime.render(
                root = PixelAdaptiveEnvironment(data = initial, child = builder),
                logicalWidth = initial.logicalWidth,
                logicalHeight = initial.logicalHeight,
            )
            /** Sole State allocated during initial mount. */
            val retainedState = tracker.states.single()
            assertTrue(retainedState.focusNode.requestFocus())
            assertTrue(retainedState.focusNode.isFocused)

            runtime.render(
                root = PixelAdaptiveEnvironment(data = resized, child = builder),
                logicalWidth = resized.logicalWidth,
                logicalHeight = resized.logicalHeight,
            )

            assertEquals(listOf(initial, resized), builderSnapshots)
            assertEquals(1, tracker.states.size)
            assertSame(retainedState, tracker.states.single())
            assertTrue(retainedState.focusNode.isFocused)
            assertEquals(12f, retainedState.listState.scrollOffsetPx, 0f)
            assertEquals("e\u0301", retainedState.textState.text)
            assertEquals(2, retainedState.textState.selectionStart)
            assertEquals(2, retainedState.textState.selectionEnd)
            assertEquals(PixelWindowOrientation.LANDSCAPE, resized.orientation)
            assertEquals(PixelWindowSizeClass.MEDIUM, resized.widthSizeClass)
            assertEquals(PixelWindowSizeClass.MEDIUM, resized.heightSizeClass)
            assertEquals(PixelWindowInsets(bottom = 12), resized.viewInsets)
            assertEquals(PixelDisplayFeatureType.FOLD, resized.displayFeatures.single().type)
            assertEquals(0, tracker.disposeCount)
        } finally {
            runtime.dispose()
        }

        assertEquals(1, tracker.disposeCount)
        assertFalse(tracker.states.single().focusNode.isFocused)
    }

    /** Creates a minimal valid adaptive snapshot with selected invalid-test replacements. */
    private fun adaptiveData(
        /** Physical width supplied to the constructor. */
        physicalWidthPx: Int = 100,
        /** Logical height supplied to the constructor. */
        logicalHeight: Int = 10,
        /** Density supplied to dp conversion. */
        density: Float = 1f,
    ): PixelAdaptiveLayoutData {
        return PixelAdaptiveLayoutData(
            physicalWidthPx = physicalWidthPx,
            physicalHeightPx = 100,
            logicalWidth = 10,
            logicalHeight = logicalHeight,
            density = density,
        )
    }
}

/** Tracks the sole adaptive retained State and its terminal release. */
private class AdaptiveRetainedTracker {
    /** State instances allocated under the stable adaptive subtree key. */
    val states: MutableList<AdaptiveRetainedProbeState> = mutableListOf()

    /** Number of terminal State releases. */
    var disposeCount: Int = 0
}

/** Stable keyed probe whose State owns focus, scroll and editable selection state. */
private class AdaptiveRetainedProbe(
    /** Shared allocation and disposal tracker. */
    val tracker: AdaptiveRetainedTracker,
    key: Any,
) : StatefulWidget(key = key) {
    /** Creates the single State expected to survive all adaptive updates. */
    override fun createState(): State<out StatefulWidget> = AdaptiveRetainedProbeState()
}

/** Retained adaptive state containing representative focus, scroll and text state objects. */
private class AdaptiveRetainedProbeState : State<AdaptiveRetainedProbe>() {
    /** Focus identity expected to remain primary across adaptive rebuilds. */
    val focusNode: FocusNode = FocusNode(debugLabel = "adaptive-retained-focus")

    /** Controller owning the preserved list offset. */
    private val listController: PixelListController = PixelListController()

    /** Scroll state initialized away from the leading edge. */
    val listState: PixelListState = listController.create(initialScrollOffsetPx = 12f)

    /** Controller owning grapheme-safe editable state. */
    private val textController: PixelTextFieldController = PixelTextFieldController()

    /** Decomposed Latin value whose trailing selection must remain a legal grapheme boundary. */
    val textState: PixelTextFieldState = textController.create(
        initialText = "e\u0301",
        selectionStart = 2,
        selectionEnd = 2,
    )

    /** Records this exact State identity at first mount. */
    override fun initState() {
        widget.tracker.states += this
    }

    /** Mounts the real focus and TextField paths below the adaptive builder. */
    override fun build(context: BuildContext): Widget {
        return Focus(
            node = focusNode,
            child = TextField(
                state = textState,
                controller = textController,
                semanticLabel = "Adaptive field",
                key = "adaptive-field",
            ),
            key = "adaptive-focus",
        )
    }

    /** Records the sole terminal State disposal after Focus unbinds the caller-owned node. */
    override fun dispose() {
        widget.tracker.disposeCount += 1
    }
}
