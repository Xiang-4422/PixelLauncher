package com.purride.pixelui

import android.view.KeyEvent
import com.purride.pixelui.internal.host.mapAndroidKeyCodeToPixelKeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class PixelAndroidKeyMapperTest {
    @Test
    fun dpadAndTabKeysMapToFocusTraversalKeys() {
        assertEquals(PixelKey.TAB, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_TAB).key)
        assertEquals(PixelKey.SHIFT_TAB, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_TAB, isShiftPressed = true).key)
        assertEquals(PixelKey.ARROW_UP, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_DPAD_UP).key)
        assertEquals(PixelKey.ARROW_DOWN, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN).key)
        assertEquals(PixelKey.ARROW_LEFT, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT).key)
        assertEquals(PixelKey.ARROW_RIGHT, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT).key)
    }

    @Test
    fun gamepadConfirmAndCancelButtonsMapToEnterAndBack() {
        assertEquals(PixelKey.ENTER, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_BUTTON_A).key)
        assertEquals(PixelKey.ENTER, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_BUTTON_START).key)
        assertEquals(PixelKey.ENTER, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER).key)
        assertEquals(PixelKey.BACK, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_BUTTON_B).key)
        assertEquals(PixelKey.BACK, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_BUTTON_SELECT).key)
        assertEquals(PixelKey.BACK, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_BUTTON_MODE).key)
    }

    @Test
    fun printableCharactersStillUseUnicodeChar() {
        val event = mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_A, unicodeChar = 'a'.code)
        assertEquals(PixelKey.CHARACTER, event.key)
        assertEquals('a', event.character)
    }
}
