package com.purride.pixelui

import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.purride.pixelui.internal.host.mapAndroidKeyCodeToPixelKeyEvent
import com.purride.pixelui.internal.host.mapAndroidKeyCodeToPixelTextInputEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies Android key normalization for navigation, activation, exact text, and legacy Char input. */
class PixelAndroidKeyMapperTest {
    /** Tab and DPAD codes retain their platform-independent focus traversal meanings. */
    @Test
    fun dpadAndTabKeysMapToFocusTraversalKeys() {
        assertEquals(PixelKey.TAB, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_TAB).key)
        assertEquals(PixelKey.SHIFT_TAB, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_TAB, isShiftPressed = true).key)
        assertEquals(PixelKey.ARROW_UP, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_DPAD_UP).key)
        assertEquals(PixelKey.ARROW_DOWN, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN).key)
        assertEquals(PixelKey.ARROW_LEFT, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT).key)
        assertEquals(PixelKey.ARROW_RIGHT, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT).key)
    }

    /** Gamepad confirmation and cancellation buttons retain their standard Pixel key meanings. */
    @Test
    fun gamepadConfirmAndCancelButtonsMapToEnterAndBack() {
        assertEquals(PixelKey.ENTER, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_BUTTON_A).key)
        assertEquals(PixelKey.ENTER, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_BUTTON_START).key)
        assertEquals(PixelKey.ENTER, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER).key)
        assertEquals(PixelKey.BACK, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_BUTTON_B).key)
        assertEquals(PixelKey.BACK, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_BUTTON_SELECT).key)
        assertEquals(PixelKey.BACK, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_BUTTON_MODE).key)
    }

    /** Space remains a logical activation key instead of degrading into a printable character. */
    @Test
    fun spaceMapsToDedicatedActivationKey() {
        assertEquals(PixelKey.SPACE, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_SPACE).key)
    }

    /** One representable printable scalar remains available through the frozen Char event. */
    @Test
    fun printableCharactersStillUseUnicodeChar() {
        /** Legacy printable event produced from one exact BMP scalar. */
        val event = mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_A, unicodeChar = 'a'.code)
        assertEquals(PixelKey.CHARACTER, event.key)
        assertEquals('a', event.character)
    }

    /** Supplementary-plane scalars remain one exact String instead of truncating through Char. */
    @Test
    fun supplementaryUnicodeCharMapsToExactTextEvent() {
        /** Exact additive event produced from U+1F600 GRINNING FACE. */
        val textEvent = mapAndroidKeyCodeToPixelTextInputEvent(
            keyCode = KeyEvent.KEYCODE_UNKNOWN,
            unicodeChar = GRINNING_FACE_CODE_POINT,
        )
        /** Legacy event that must refuse lossy supplementary-plane narrowing. */
        val legacyEvent = mapAndroidKeyCodeToPixelKeyEvent(
            keyCode = KeyEvent.KEYCODE_UNKNOWN,
            unicodeChar = GRINNING_FACE_CODE_POINT,
        )

        assertEquals("\uD83D\uDE00", textEvent?.text)
        assertEquals(PixelKey.UNKNOWN, legacyEvent.key)
        assertNull(legacyEvent.character)
    }

    /** Printable BMP input is available to exact-text dispatch while preserving old key mapping. */
    @Test
    fun bmpUnicodeCharSupportsTextFirstAndLegacyFallback() {
        /** Exact String event used by the new dispatch phase. */
        val textEvent = mapAndroidKeyCodeToPixelTextInputEvent(
            keyCode = KeyEvent.KEYCODE_A,
            unicodeChar = 'a'.code,
        )
        /** Legacy Char event retained for an unconsumed String payload. */
        val legacyEvent = mapAndroidKeyCodeToPixelKeyEvent(
            keyCode = KeyEvent.KEYCODE_A,
            unicodeChar = 'a'.code,
        )

        assertEquals("a", textEvent?.text)
        assertEquals(PixelKey.CHARACTER, legacyEvent.key)
        assertEquals('a', legacyEvent.character)
    }

    /** Android's dead-key marker is removed without discarding its combining-accent scalar. */
    @Test
    fun combiningAccentFlagPreservesExactAccentText() {
        /** Android-encoded dead acute accent with the high combining marker bit set. */
        val encodedAccent = KeyCharacterMap.COMBINING_ACCENT or COMBINING_ACUTE_ACCENT_CODE_POINT
        /** Exact text event containing the combining mark without Android's transport flag. */
        val textEvent = mapAndroidKeyCodeToPixelTextInputEvent(
            keyCode = KeyEvent.KEYCODE_APOSTROPHE,
            unicodeChar = encodedAccent,
        )
        /** Legacy Char event preserving the same BMP combining mark for compatibility. */
        val legacyEvent = mapAndroidKeyCodeToPixelKeyEvent(
            keyCode = KeyEvent.KEYCODE_APOSTROPHE,
            unicodeChar = encodedAccent,
        )

        assertEquals("\u0301", textEvent?.text)
        assertEquals('\u0301', legacyEvent.character)
    }

    /** Dedicated keys and malformed scalar values never masquerade as printable text. */
    @Test
    fun nonTextAndInvalidUnicodeValuesDoNotCreateTextEvents() {
        assertNull(
            mapAndroidKeyCodeToPixelTextInputEvent(
                keyCode = KeyEvent.KEYCODE_SPACE,
                unicodeChar = ' '.code,
            ),
        )
        assertNull(mapAndroidKeyCodeToPixelTextInputEvent(KeyEvent.KEYCODE_UNKNOWN, unicodeChar = 0))
        assertNull(
            mapAndroidKeyCodeToPixelTextInputEvent(
                keyCode = KeyEvent.KEYCODE_UNKNOWN,
                unicodeChar = HIGH_SURROGATE_CODE_POINT,
            ),
        )
        assertNull(
            mapAndroidKeyCodeToPixelTextInputEvent(
                keyCode = KeyEvent.KEYCODE_UNKNOWN,
                unicodeChar = INVALID_CODE_POINT,
            ),
        )
    }

    /** Stable scalar constants keep malformed and supplementary test intent explicit. */
    private companion object {
        /** U+1F600 GRINNING FACE, represented by a surrogate pair in UTF-16. */
        const val GRINNING_FACE_CODE_POINT: Int = 0x1F600

        /** First UTF-16 high-surrogate code point, invalid as an independent Unicode scalar. */
        const val HIGH_SURROGATE_CODE_POINT: Int = 0xD800

        /** First integer above Unicode's maximum scalar U+10FFFF. */
        const val INVALID_CODE_POINT: Int = 0x110000

        /** U+0301 COMBINING ACUTE ACCENT used for Android dead-key transport coverage. */
        const val COMBINING_ACUTE_ACCENT_CODE_POINT: Int = 0x0301
    }
}
