package com.purride.pixelui.state

/**
 * 通用文本输入状态。
 *
 * 第一版先只收敛最关键的几项：
 * 1. 当前文本
 * 2. 当前选区
 * 3. 当前是否聚焦
 *
 * 暂时不做组合输入、选择手柄和光标闪烁节拍，这些后续可以继续扩展。
 */
class PixelTextFieldState(
    initialText: String = "",
    selectionStart: Int = initialText.length,
    selectionEnd: Int = selectionStart,
) {
    var text: String = initialText
        internal set

    var selectionStart: Int = selectionStart.coerceIn(0, initialText.length)
        internal set

    var selectionEnd: Int = selectionEnd.coerceIn(this.selectionStart, initialText.length)
        internal set

    var isFocused: Boolean = false
        internal set
}
