package com.purride.pixelui

import com.purride.pixelui.state.PixelTextFieldController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelTextFieldControllerTest {

    private val controller = PixelTextFieldController()

    @Test
    fun updateTextAlsoUpdatesSelection() {
        val state = controller.create(initialText = "ABC")

        controller.updateText(
            state = state,
            text = "HELLO",
            selectionStart = 2,
            selectionEnd = 4,
        )

        assertEquals("HELLO", state.text)
        assertEquals(2, state.selectionStart)
        assertEquals(4, state.selectionEnd)
    }

    @Test
    fun focusAndBlurToggleFocusedFlag() {
        val state = controller.create()

        controller.focus(state)
        assertTrue(state.isFocused)

        controller.blur(state)
        assertFalse(state.isFocused)
    }

    @Test
    fun requestFocusAndBlurSetPendingFlags() {
        val state = controller.create()

        controller.requestFocus(state)
        assertTrue(state.focusRequested)
        assertFalse(state.blurRequested)

        controller.requestBlur(state)
        assertTrue(state.blurRequested)
        assertFalse(state.focusRequested)
    }

    @Test
    fun clearResetsTextAndMovesSelectionToStart() {
        val state = controller.create(initialText = "HELLO")

        controller.clear(state)

        assertEquals("", state.text)
        assertEquals(0, state.selectionStart)
        assertEquals(0, state.selectionEnd)
    }

    @Test
    fun selectAllMarksWholeTextRange() {
        val state = controller.create(initialText = "PIXEL")

        controller.selectAll(state)

        assertEquals(0, state.selectionStart)
        assertEquals(5, state.selectionEnd)
    }

    @Test
    fun setSelectionClampsIntoCurrentTextRange() {
        val state = controller.create(initialText = "ABCD")

        controller.setSelection(
            state = state,
            selectionStart = -2,
            selectionEnd = 99,
        )

        assertEquals(0, state.selectionStart)
        assertEquals(4, state.selectionEnd)
    }
}
