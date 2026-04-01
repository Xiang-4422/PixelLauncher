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

    fun setSelection(
        state: PixelTextFieldState,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
    ) {
        state.selectionStart = selectionStart.coerceIn(0, state.text.length)
        state.selectionEnd = selectionEnd.coerceIn(state.selectionStart, state.text.length)
    }

    fun clear(state: PixelTextFieldState) {
        updateText(state = state, text = "")
    }

    fun selectAll(state: PixelTextFieldState) {
        setSelection(
            state = state,
            selectionStart = 0,
            selectionEnd = state.text.length,
        )
    }

    fun focus(state: PixelTextFieldState) {
        state.isFocused = true
        state.focusRequested = false
        state.blurRequested = false
    }

    fun blur(state: PixelTextFieldState) {
        state.isFocused = false
        state.focusRequested = false
        state.blurRequested = false
    }

    fun requestFocus(state: PixelTextFieldState) {
        state.focusRequested = true
        state.blurRequested = false
    }

    fun requestBlur(state: PixelTextFieldState) {
        state.blurRequested = true
        state.focusRequested = false
    }
}
