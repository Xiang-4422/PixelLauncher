package com.purride.pixelui.state

import com.purride.pixelui.ChangeNotifier

/**
 * 通用文本输入控制器。
 *
 * 第一版先把状态更新集中在这里，避免宿主和页面层直接改字段：
 * - 创建状态
 * - 更新文本与选区
 * - 聚焦与失焦
 *
 * 监听变化：本类继承 [ChangeNotifier]，可直接
 * `controller.addListener { /* on changed */ }` 注册回调，或用
 * `controller.observe { ... }` 扩展拿到句柄方便后续 removeListener。
 */
public class PixelTextFieldController : ChangeNotifier() {

    public fun create(
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

    public fun updateText(
        state: PixelTextFieldState,
        text: String,
        selectionStart: Int = text.length,
        selectionEnd: Int = selectionStart,
        compositionStart: Int = -1,
        compositionEnd: Int = -1,
    ) {
        state.text = text
        state.selectionStart = selectionStart.coerceIn(0, text.length)
        state.selectionEnd = selectionEnd.coerceIn(state.selectionStart, text.length)
        applyComposition(state, compositionStart, compositionEnd)
        notifyListeners()
    }

    /**
     * 更新 IME composition 区段。`start < 0` 或 `end <= start` 都视作"无 composing"。
     * 范围会被 clamp 到当前 [PixelTextFieldState.text] 长度内。
     */
    public fun updateComposition(
        state: PixelTextFieldState,
        compositionStart: Int,
        compositionEnd: Int,
    ) {
        applyComposition(state, compositionStart, compositionEnd)
        notifyListeners()
    }

    private fun applyComposition(
        state: PixelTextFieldState,
        compositionStart: Int,
        compositionEnd: Int,
    ) {
        if (compositionStart < 0 || compositionEnd <= compositionStart) {
            state.compositionStart = -1
            state.compositionEnd = -1
            return
        }
        val length = state.text.length
        val clampedStart = compositionStart.coerceIn(0, length)
        val clampedEnd = compositionEnd.coerceIn(clampedStart, length)
        if (clampedStart >= clampedEnd) {
            state.compositionStart = -1
            state.compositionEnd = -1
            return
        }
        state.compositionStart = clampedStart
        state.compositionEnd = clampedEnd
    }

    internal fun syncCursorBlinkConfig(
        state: PixelTextFieldState,
        enabled: Boolean,
        periodMs: Long,
    ) {
        val safePeriodMs = periodMs.coerceAtLeast(1L)
        if (state.cursorBlinkEnabled == enabled && state.cursorBlinkPeriodMs == safePeriodMs) return
        state.cursorBlinkEnabled = enabled
        state.cursorBlinkPeriodMs = safePeriodMs
        resetCursorBlink(state)
    }

    internal fun resetCursorBlink(state: PixelTextFieldState) {
        state.cursorBlinkElapsedMs = 0L
        state.cursorVisible = true
    }

    internal fun stepCursorBlink(state: PixelTextFieldState, deltaMs: Long): Boolean {
        if (!state.isFocused || !state.cursorBlinkEnabled || deltaMs <= 0L) {
            val wasHidden = !state.cursorVisible
            state.cursorVisible = true
            state.cursorBlinkElapsedMs = 0L
            return wasHidden
        }
        state.cursorBlinkElapsedMs += deltaMs
        val halfPeriodMs = (state.cursorBlinkPeriodMs / 2L).coerceAtLeast(1L)
        if (state.cursorBlinkElapsedMs < halfPeriodMs) return false
        val toggles = state.cursorBlinkElapsedMs / halfPeriodMs
        state.cursorBlinkElapsedMs %= halfPeriodMs
        if (toggles % 2L == 0L) return false
        state.cursorVisible = !state.cursorVisible
        notifyListeners()
        return true
    }

    public fun setSelection(
        state: PixelTextFieldState,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
    ) {
        state.selectionStart = selectionStart.coerceIn(0, state.text.length)
        state.selectionEnd = selectionEnd.coerceIn(state.selectionStart, state.text.length)
        notifyListeners()
    }

    public fun clear(state: PixelTextFieldState) {
        updateText(state = state, text = "")
    }

    public fun selectAll(state: PixelTextFieldState) {
        setSelection(
            state = state,
            selectionStart = 0,
            selectionEnd = state.text.length,
        )
    }

    public fun focus(state: PixelTextFieldState) {
        state.isFocused = true
        state.focusRequested = false
        state.blurRequested = false
        resetCursorBlink(state)
        notifyListeners()
    }

    public fun blur(state: PixelTextFieldState) {
        state.isFocused = false
        state.focusRequested = false
        state.blurRequested = false
        resetCursorBlink(state)
        notifyListeners()
    }

    public fun requestFocus(state: PixelTextFieldState) {
        state.focusRequested = true
        state.blurRequested = false
        notifyListeners()
    }

    public fun requestBlur(state: PixelTextFieldState) {
        state.blurRequested = true
        state.focusRequested = false
        notifyListeners()
    }
}
