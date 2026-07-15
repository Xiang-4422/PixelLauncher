package com.purride.pixelui.internal.host

import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelKeyEvent
import com.purride.pixelui.PixelTextInputEvent

/**
 * Maps one Android key code to the legacy navigation/activation/Char event model.
 *
 * Supplementary-plane [unicodeChar] values intentionally map to [PixelKey.UNKNOWN] because
 * narrowing them to [Char] would truncate the scalar. Call
 * [mapAndroidKeyCodeToPixelTextInputEvent] first when exact text delivery is available.
 *
 * @param keyCode Android key code identifying navigation, activation, or a printable key.
 * @param isShiftPressed Whether Shift modifies Tab into reverse traversal.
 * @param unicodeChar Unicode scalar reported by Android, or zero when no printable value exists.
 */
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
        KeyEvent.KEYCODE_SPACE -> PixelKeyEvent(PixelKey.SPACE)
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
            /** Scalar with Android's dead-key marker removed while retaining the combining accent. */
            val normalizedUnicodeChar = unicodeChar.withoutAndroidCombiningAccentFlag()
            /** Legacy-compatible scalar that fits exactly in one non-surrogate UTF-16 unit. */
            val char = normalizedUnicodeChar
                .takeIf(::isLegacyCompatibleBmpScalar)
                ?.toChar()
            if (char != null) PixelKeyEvent(PixelKey.CHARACTER, char) else PixelKeyEvent(PixelKey.UNKNOWN)
        }
    }
}

/**
 * Maps printable Android input to the exact String event used by the additive text path.
 *
 * Key codes already assigned navigation, activation, or dismissal semantics return `null`, even if
 * Android also reports a printable value. Valid supplementary-plane scalars are encoded as one
 * well-formed surrogate pair inside [PixelTextInputEvent.text]. Invalid scalars and isolated UTF-16
 * surrogate values are rejected instead of manufacturing malformed text.
 *
 * @param keyCode Android key code whose dedicated non-text meaning takes precedence.
 * @param unicodeChar Unicode scalar reported by Android, or zero when no text is available.
 * @return Exact text input event, or `null` when this Android event is not valid printable input.
 */
internal fun mapAndroidKeyCodeToPixelTextInputEvent(
    keyCode: Int,
    unicodeChar: Int = 0,
): PixelTextInputEvent? {
    if (keyCode.hasDedicatedPixelKeyMeaning()) return null
    /** Scalar with Android's dead-key marker removed while retaining the combining accent. */
    val normalizedUnicodeChar = unicodeChar.withoutAndroidCombiningAccentFlag()
    if (
        !Character.isValidCodePoint(normalizedUnicodeChar) ||
        normalizedUnicodeChar == 0 ||
        normalizedUnicodeChar.isSurrogateCodePoint()
    ) {
        return null
    }
    /** Exact UTF-16 encoding of one validated Unicode scalar, including supplementary pairs. */
    val text = String(Character.toChars(normalizedUnicodeChar))
    return PixelTextInputEvent(text)
}

/** Removes Android's dead-key bit while preserving the actual combining-accent scalar. */
private fun Int.withoutAndroidCombiningAccentFlag(): Int {
    return if (this and KeyCharacterMap.COMBINING_ACCENT != 0) {
        this and KeyCharacterMap.COMBINING_ACCENT_MASK
    } else {
        this
    }
}

/** Returns whether this key code already represents a non-text Pixel key. */
private fun Int.hasDedicatedPixelKeyMeaning(): Boolean {
    return when (this) {
        KeyEvent.KEYCODE_TAB,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_START,
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_BUTTON_MODE,
        KeyEvent.KEYCODE_ESCAPE,
        -> true
        else -> false
    }
}

/** Returns whether this Unicode scalar can be represented losslessly by the legacy Char API. */
private fun isLegacyCompatibleBmpScalar(codePoint: Int): Boolean {
    return codePoint in MIN_BMP_TEXT_CODE_POINT..MAX_BMP_CODE_POINT && !codePoint.isSurrogateCodePoint()
}

/** Returns whether this integer occupies the reserved UTF-16 surrogate code-point interval. */
private fun Int.isSurrogateCodePoint(): Boolean = this in MIN_SURROGATE_CODE_POINT..MAX_SURROGATE_CODE_POINT

/** Lowest non-zero BMP scalar considered printable input by the mapper. */
private const val MIN_BMP_TEXT_CODE_POINT: Int = 0x0001

/** Highest scalar encoded by exactly one UTF-16 code unit. */
private const val MAX_BMP_CODE_POINT: Int = 0xFFFF

/** First scalar reserved for UTF-16 high-surrogate code units. */
private const val MIN_SURROGATE_CODE_POINT: Int = 0xD800

/** Last scalar reserved for UTF-16 low-surrogate code units. */
private const val MAX_SURROGATE_CODE_POINT: Int = 0xDFFF
