package com.purride.pixelui.state

/**
 * 通用文本输入控制器。
 *
 * 第一版先把状态更新集中在这里，避免宿主和页面层直接改字段：
 * - 创建状态
 * - 更新文本与选区
 * - 聚焦与失焦
 */
class PixelTextFieldController {

    fun create(
        initialText: String = "",
        selectionStart: Int = initialText.length,
        selectionEnd: Int = selectionStart,
    ): PixelTextFieldState {
        return PixelTextFieldState(
            initialText = initialText,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
        )
    }

    fun updateText(
        state: PixelTextFieldState,
        text: String,
        selectionStart: Int = text.length,
        selectionEnd: Int = selectionStart,
    ) {
        state.text = text
        state.selectionStart = selectionStart.coerceIn(0, text.length)
        state.selectionEnd = selectionEnd.coerceIn(state.selectionStart, text.length)
    }

    fun focus(state: PixelTextFieldState) {
        state.isFocused = true
    }

    fun blur(state: PixelTextFieldState) {
        state.isFocused = false
    }
}
