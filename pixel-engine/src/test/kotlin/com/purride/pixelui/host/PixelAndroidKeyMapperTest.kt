package com.purride.pixelui

import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.purride.pixelui.internal.host.mapAndroidKeyCodeToPixelKeyEvent
import com.purride.pixelui.internal.host.mapAndroidKeyCodeToPixelTextInputEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 验证 Android 按键在导航、激活与精确文本输入三条路径上的归一化结果。 */
class PixelAndroidKeyMapperTest {
    /** Tab 与 DPAD 键码保持平台无关的焦点遍历语义。 */
    @Test
    fun dpadAndTabKeysMapToFocusTraversalKeys() {
        assertEquals(PixelKey.TAB, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_TAB).key)
        assertEquals(PixelKey.SHIFT_TAB, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_TAB, isShiftPressed = true).key)
        assertEquals(PixelKey.ARROW_UP, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_DPAD_UP).key)
        assertEquals(PixelKey.ARROW_DOWN, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN).key)
        assertEquals(PixelKey.ARROW_LEFT, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT).key)
        assertEquals(PixelKey.ARROW_RIGHT, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT).key)
    }

    /** 手柄的确认与取消按键保持标准 Pixel 按键语义。 */
    @Test
    fun gamepadConfirmAndCancelButtonsMapToEnterAndBack() {
        assertEquals(PixelKey.ENTER, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_BUTTON_A).key)
        assertEquals(PixelKey.ENTER, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_BUTTON_START).key)
        assertEquals(PixelKey.ENTER, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER).key)
        assertEquals(PixelKey.BACK, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_BUTTON_B).key)
        assertEquals(PixelKey.BACK, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_BUTTON_SELECT).key)
        assertEquals(PixelKey.BACK, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_BUTTON_MODE).key)
    }

    /** 空格保持为逻辑激活键，不会退化成可打印字符。 */
    @Test
    fun spaceMapsToDedicatedActivationKey() {
        assertEquals(PixelKey.SPACE, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_SPACE).key)
    }

    /** 可打印的硬件键盘输入只产生精确文本，不会产生按键事件。 */
    @Test
    fun printableCharactersOnlyProduceTextEvents() {
        /** 由一个可打印 BMP 标量产生的精确 String 事件。 */
        val textEvent = mapAndroidKeyCodeToPixelTextInputEvent(
            keyCode = KeyEvent.KEYCODE_A,
            unicodeChar = 'a'.code,
        )

        assertEquals("a", textEvent?.text)
        assertEquals(PixelKey.UNKNOWN, mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_A).key)
    }

    /** 补充平面标量保持为一个完整 String，不会被截断成 Char。 */
    @Test
    fun supplementaryUnicodeCharMapsToExactTextEvent() {
        /** 由 U+1F600 GRINNING FACE 产生的精确事件。 */
        val textEvent = mapAndroidKeyCodeToPixelTextInputEvent(
            keyCode = KeyEvent.KEYCODE_UNKNOWN,
            unicodeChar = GRINNING_FACE_CODE_POINT,
        )

        assertEquals("\uD83D\uDE00", textEvent?.text)
        assertEquals(
            PixelKey.UNKNOWN,
            mapAndroidKeyCodeToPixelKeyEvent(KeyEvent.KEYCODE_UNKNOWN).key,
        )
    }

    /** 去掉 Android 死键标记位的同时，不丢弃其组合音标标量。 */
    @Test
    fun combiningAccentFlagPreservesExactAccentText() {
        /** 带组合标记高位的 Android 死键锐音符编码值。 */
        val encodedAccent = KeyCharacterMap.COMBINING_ACCENT or COMBINING_ACUTE_ACCENT_CODE_POINT
        /** 只含组合标记、不含 Android 传输标记位的精确文本事件。 */
        val textEvent = mapAndroidKeyCodeToPixelTextInputEvent(
            keyCode = KeyEvent.KEYCODE_APOSTROPHE,
            unicodeChar = encodedAccent,
        )

        assertEquals("\u0301", textEvent?.text)
    }

    /** 专用按键和畸形标量值都不会被伪装成可打印文本。 */
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

    /** 稳定的标量常量，让畸形输入与补充平面用例的意图保持显式。 */
    private companion object {
        /** U+1F600 GRINNING FACE，在 UTF-16 中由一对代理项表示。 */
        const val GRINNING_FACE_CODE_POINT: Int = 0x1F600

        /** UTF-16 首个高位代理项码位，作为独立 Unicode 标量非法。 */
        const val HIGH_SURROGATE_CODE_POINT: Int = 0xD800

        /** 超出 Unicode 最大标量 U+10FFFF 的第一个整数。 */
        const val INVALID_CODE_POINT: Int = 0x110000

        /** U+0301 COMBINING ACUTE ACCENT，用于覆盖 Android 死键传输路径。 */
        const val COMBINING_ACUTE_ACCENT_CODE_POINT: Int = 0x0301
    }
}
