package com.purride.pixelui.state

import android.os.Bundle

/** 通用文本输入状态。 */
public class PixelTextFieldState(
    initialText: String = "",
    selectionStart: Int = initialText.length,
    selectionEnd: Int = selectionStart,
) {
    public var text: String = initialText
        internal set

    public var selectionStart: Int = selectionStart.coerceIn(0, initialText.length)
        internal set

    public var selectionEnd: Int = selectionEnd.coerceIn(this.selectionStart, initialText.length)
        internal set

    public var isFocused: Boolean = false
        internal set

    /**
     * IME composition 区段。-1 表示当前没有 composing 文本（IME 已提交所有按键）。
     *
     * 范围对应 [text] 的字符 index；end 是 exclusive。
     * 由宿主侧（PixelTextInputBridge / 测试）通过 [PixelTextFieldController.updateComposition]
     * 写入；RenderSurface 在聚焦且区段非空时绘制下划线。
     */
    public var compositionStart: Int = -1
        internal set

    public var compositionEnd: Int = -1
        internal set

    internal var focusRequested: Boolean = false
    internal var blurRequested: Boolean = false
    internal var autofocusConsumed: Boolean = false
    internal var cursorBlinkEnabled: Boolean = true
    internal var cursorBlinkPeriodMs: Long = 1_000L
    internal var cursorBlinkElapsedMs: Long = 0L
    internal var cursorVisible: Boolean = true
}

/** TextField 的可持久化文本和选区。 */
public data class PixelTextFieldSavedState(
    public val text: String,
    public val selectionStart: Int = text.length,
    public val selectionEnd: Int = selectionStart,
)

/** [PixelTextFieldSavedState.saveToBundle] 与 [getPixelTextFieldSavedState] 使用的默认 key。 */
public const val PixelTextFieldSavedStateBundleKey: String = "com.purride.pixelui.textField.savedState"

/** 把 TextField 文本和选区写入 Android [Bundle]。 */
public fun PixelTextFieldSavedState.saveToBundle(
    outState: Bundle,
    key: String = PixelTextFieldSavedStateBundleKey,
) {
    require(key.isNotBlank()) { "PixelTextFieldSavedState Bundle key must not be blank" }
    val bundle = Bundle()
    bundle.putString(PixelTextFieldSavedStateKeys.Text, text)
    bundle.putInt(PixelTextFieldSavedStateKeys.SelectionStart, selectionStart)
    bundle.putInt(PixelTextFieldSavedStateKeys.SelectionEnd, selectionEnd)
    outState.putBundle(key, bundle)
}

/** 从 Android [Bundle] 读取之前保存的 TextField 文本和选区。 */
public fun Bundle.getPixelTextFieldSavedState(
    key: String = PixelTextFieldSavedStateBundleKey,
): PixelTextFieldSavedState? {
    require(key.isNotBlank()) { "PixelTextFieldSavedState Bundle key must not be blank" }
    val bundle = getBundle(key) ?: return null
    val text = bundle.getString(PixelTextFieldSavedStateKeys.Text) ?: return null
    return PixelTextFieldSavedState(
        text = text,
        selectionStart = bundle.getInt(PixelTextFieldSavedStateKeys.SelectionStart, text.length),
        selectionEnd = bundle.getInt(PixelTextFieldSavedStateKeys.SelectionEnd, text.length),
    )
}

private object PixelTextFieldSavedStateKeys {
    const val Text = "text"
    const val SelectionStart = "selectionStart"
    const val SelectionEnd = "selectionEnd"
}
