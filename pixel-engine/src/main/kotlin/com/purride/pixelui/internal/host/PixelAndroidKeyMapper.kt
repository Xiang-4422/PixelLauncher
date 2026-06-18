package com.purride.pixelui.internal.host

import android.view.KeyEvent
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelKeyEvent

internal fun mapAndroidKeyCodeToPixelKeyEvent(
    keyCode: Int,
    isShiftPressed: Boolean = false,
    unicodeChar: Int = 0,
): PixelKeyEvent {
    return when (keyCode) {
        KeyEvent.KEYCODE_TAB -> PixelKeyEvent(if (isShiftPressed) PixelKey.SHIFT_TAB else PixelKey.TAB)
        KeyEvent.KEYCODE_DPAD_UP -> PixelKeyEvent(PixelKey.ARROW_UP)
        KeyEvent.KEYCODE_DPAD_DOWN -> PixelKeyEvent(PixelKey.ARROW_DOWN)
        KeyEvent.KEYCODE_DPAD_LEFT -> PixelKeyEvent(PixelKey.ARROW_LEFT)
        KeyEvent.KEYCODE_DPAD_RIGHT -> PixelKeyEvent(PixelKey.ARROW_RIGHT)
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_START,
        -> PixelKeyEvent(PixelKey.ENTER)
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_BUTTON_MODE,
        -> PixelKeyEvent(PixelKey.BACK)
        KeyEvent.KEYCODE_ESCAPE -> PixelKeyEvent(PixelKey.ESCAPE)
        else -> {
            val char = unicodeChar.takeIf { it != 0 }?.toChar()
            if (char != null) PixelKeyEvent(PixelKey.CHARACTER, char) else PixelKeyEvent(PixelKey.UNKNOWN)
        }
    }
}
