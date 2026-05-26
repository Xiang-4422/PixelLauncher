package com.purride.pixelui.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelTextFieldCursorBlinkTest {
    @Test
    fun cursorBlinkTogglesOnFrameProgression() {
        val controller = PixelTextFieldController()
        val state = controller.create("abc")
        controller.focus(state)
        controller.syncCursorBlinkConfig(state, enabled = true, periodMs = 1_000)

        assertTrue(state.cursorVisible)
        controller.stepCursorBlink(state, 500)
        assertFalse(state.cursorVisible)
        controller.stepCursorBlink(state, 500)
        assertTrue(state.cursorVisible)
    }

    @Test
    fun blinkDisabledKeepsCursorVisible() {
        val controller = PixelTextFieldController()
        val state = controller.create("abc")
        controller.focus(state)
        controller.syncCursorBlinkConfig(state, enabled = false, periodMs = 1_000)

        controller.stepCursorBlink(state, 2_000)

        assertTrue(state.cursorVisible)
    }

    @Test
    fun readOnlyOrDisabledBlinkConfigKeepsCursorVisible() {
        val controller = PixelTextFieldController()
        val state = controller.create("abc")
        controller.focus(state)
        controller.syncCursorBlinkConfig(state, enabled = false, periodMs = 1_000)

        assertTrue(state.cursorVisible)
        controller.stepCursorBlink(state, 500)
        assertTrue(state.cursorVisible)
    }
}
