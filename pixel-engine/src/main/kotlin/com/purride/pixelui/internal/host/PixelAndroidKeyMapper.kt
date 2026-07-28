package com.purride.pixelui.internal.host

import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelKeyEvent
import com.purride.pixelui.PixelTextInputEvent

/**
 * 把一个 Android key code 映射为导航/激活/取消语义的 [PixelKeyEvent]。
 *
 * 本函数只负责非文本键：硬件键盘、DPAD 和游戏手柄按键都在这里归一化。任何可打印输入都不
 * 经过这里，调用方应先用 [mapAndroidKeyCodeToPixelTextInputEvent] 取出精确文本；没有对应
 * 非文本语义的 key code 一律返回 [PixelKey.UNKNOWN]。
 *
 * @param keyCode Android key code identifying navigation, activation, or dismissal.
 * @param isShiftPressed Whether Shift modifies Tab into reverse traversal.
 */
internal fun mapAndroidKeyCodeToPixelKeyEvent(
    keyCode: Int,
    isShiftPressed: Boolean = false,
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
        else -> PixelKeyEvent(PixelKey.UNKNOWN)
    }
}

/**
 * 把可打印的 Android 输入映射为 canonical 文本链路使用的精确 String 事件。
 *
 * Maps printable Android input to the exact String event used by the canonical text path.
 *
 * 已经拥有导航、激活或取消语义的 key code 一律返回 `null`，即使 Android 同时报告了可打印值。
 * 合法的 supplementary code point 会被编码成一对完整代理项写入 [PixelTextInputEvent.text]；
 * 非法标量和孤立的 UTF-16 代理项会被拒绝，而不是拼出畸形文本。
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
    /** 去掉 Android 死键标记位、但保留组合音标本身的标量。 */
    val normalizedUnicodeChar = unicodeChar.withoutAndroidCombiningAccentFlag()
    if (
        !Character.isValidCodePoint(normalizedUnicodeChar) ||
        normalizedUnicodeChar == 0 ||
        normalizedUnicodeChar.isSurrogateCodePoint()
    ) {
        return null
    }
    /** 一个已校验 Unicode 标量的精确 UTF-16 编码，包含 supplementary 代理对。 */
    val text = String(Character.toChars(normalizedUnicodeChar))
    return PixelTextInputEvent(text)
}

/** 清除 Android 的死键标记位，同时保留真正的组合音标标量。 */
private fun Int.withoutAndroidCombiningAccentFlag(): Int {
    return if (this and KeyCharacterMap.COMBINING_ACCENT != 0) {
        this and KeyCharacterMap.COMBINING_ACCENT_MASK
    } else {
        this
    }
}

/** 判断该 key code 是否已经代表一个非文本的 Pixel 按键。 */
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

/** 判断该整数是否落在 UTF-16 保留的代理项码位区间内。 */
private fun Int.isSurrogateCodePoint(): Boolean = this in MIN_SURROGATE_CODE_POINT..MAX_SURROGATE_CODE_POINT

/** UTF-16 高位代理项码元的起始标量。 */
private const val MIN_SURROGATE_CODE_POINT: Int = 0xD800

/** UTF-16 低位代理项码元的结束标量。 */
private const val MAX_SURROGATE_CODE_POINT: Int = 0xDFFF
