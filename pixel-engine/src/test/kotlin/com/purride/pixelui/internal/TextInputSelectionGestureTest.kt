package com.purride.pixelui.internal

import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.TextInputSelectionHandle
import com.purride.pixelui.state.PixelTextFieldController
import org.junit.Assert.assertEquals
import org.junit.Test

class TextInputSelectionGestureTest {
    @Test
    fun resolveSelectionMapsMultilineFallbackCoordinates() {
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "AB\nCDE")
        val target = textInputTarget(controller, state)

        assertEquals(0, PixelTextInputSelectionGesture.resolveSelection(target, logicalX = 0, logicalY = 0))
        assertEquals(2, PixelTextInputSelectionGesture.resolveSelection(target, logicalX = 70, logicalY = 0))
        assertEquals(4, PixelTextInputSelectionGesture.resolveSelection(target, logicalX = 24, logicalY = 10))
    }

    @Test
    fun nearestHandleChoosesClosestSelectionEdge() {
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "ABCDEFG")
        controller.setSelection(state, 1, 6)
        val target = textInputTarget(controller, state)

        assertEquals(
            TextInputSelectionHandle.START,
            PixelTextInputSelectionGesture.nearestHandle(target, logicalX = 0, logicalY = 0),
        )
        assertEquals(
            TextInputSelectionHandle.END,
            PixelTextInputSelectionGesture.nearestHandle(target, logicalX = 70, logicalY = 0),
        )
    }

    @Test
    fun dragHandleKeepsSelectionOrdered() {
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "ABCDEFG")
        controller.setSelection(state, 2, 5)
        val target = textInputTarget(controller, state)

        PixelTextInputSelectionGesture.dragHandle(
            target = target,
            handle = TextInputSelectionHandle.START,
            logicalX = 70,
            logicalY = 0,
        )

        assertEquals(5, state.selectionStart)
        assertEquals(5, state.selectionEnd)

        controller.setSelection(state, 2, 5)
        PixelTextInputSelectionGesture.dragHandle(
            target = target,
            handle = TextInputSelectionHandle.END,
            logicalX = 0,
            logicalY = 0,
        )

        assertEquals(2, state.selectionStart)
        assertEquals(2, state.selectionEnd)
    }

    /** Tap offsets inside decomposed or supplementary graphemes use controller caret affinity. */
    @Test
    fun collapsedTapSelectionNeverStopsInsideAGrapheme(): Unit {
        /** Decomposed Latin followed by a supplementary emoji supplies two interior UTF-16 offsets. */
        val text = "e\u0301😀"
        /** Controller owning the public grapheme-boundary normalization contract. */
        val controller = PixelTextFieldController()
        /** Initial caret starts after the full source so both tap cases must move it. */
        val state = controller.create(initialText = text, selectionStart = text.length)
        /** Target mapper deliberately returns the interior of the decomposed cluster. */
        val decomposedTarget = textInputTarget(controller, state) { _, _ -> 1 }

        PixelTextInputSelectionGesture.setCollapsedSelection(decomposedTarget, 0, 0)
        assertEquals(2, state.selectionStart)
        assertEquals(2, state.selectionEnd)

        /** Target mapper now returns the interior low-surrogate boundary of the emoji. */
        val emojiTarget = textInputTarget(controller, state) { _, _ -> 3 }
        PixelTextInputSelectionGesture.setCollapsedSelection(emojiTarget, 0, 0)
        assertEquals(4, state.selectionStart)
        assertEquals(4, state.selectionEnd)
    }

    /** Dragging either handle through a decomposed cluster expands to its outer boundaries. */
    @Test
    fun dragHandleExpandsInteriorOffsetsToWholeGraphemes(): Unit {
        /** ASCII guards make the decomposed cluster boundaries observable as 1..3. */
        val text = "Ae\u0301B"
        /** Controller applying the same boundary map as IME and Accessibility paths. */
        val controller = PixelTextFieldController()
        /** Full-range state used to drag each endpoint independently. */
        val state = controller.create(initialText = text, selectionStart = 0, selectionEnd = text.length)
        /** Mapper always lands at UTF-16 offset 2, inside the decomposed cluster. */
        val target = textInputTarget(controller, state) { _, _ -> 2 }

        assertEquals(
            true,
            PixelTextInputSelectionGesture.dragHandle(
                target = target,
                handle = TextInputSelectionHandle.START,
                logicalX = 0,
                logicalY = 0,
            ),
        )
        assertEquals(1, state.selectionStart)
        assertEquals(4, state.selectionEnd)

        controller.setSelection(state, 0, text.length)
        assertEquals(
            true,
            PixelTextInputSelectionGesture.dragHandle(
                target = target,
                handle = TextInputSelectionHandle.END,
                logicalX = 0,
                logicalY = 0,
            ),
        )
        assertEquals(0, state.selectionStart)
        assertEquals(3, state.selectionEnd)
    }

    private fun textInputTarget(
        controller: PixelTextFieldController,
        state: com.purride.pixelui.state.PixelTextFieldState,
        textIndexAt: ((Int, Int) -> Int)? = null,
    ): PixelTextInputTarget {
        return PixelTextInputTarget(
            bounds = PixelRect(left = 0, top = 0, width = 70, height = 20),
            state = state,
            controller = controller,
            readOnly = false,
            autofocus = false,
            minLines = 1,
            maxLines = 2,
            inputType = com.purride.pixelui.PixelInputType.TEXT,
            action = PixelTextInputAction.DONE,
            onChanged = null,
            onSubmitted = null,
            textIndexAt = textIndexAt,
        )
    }
}
