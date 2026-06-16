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

    private fun textInputTarget(
        controller: PixelTextFieldController,
        state: com.purride.pixelui.state.PixelTextFieldState,
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
        )
    }
}
