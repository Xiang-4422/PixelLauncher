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

    /** 创建文本输入状态，并规范化初始选区。 */
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

    /** 更新文本、选区和可选 IME composition 范围。 */
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

    /**
     * 距离下一次光标可见态翻转还有多少毫秒。
     *
     * 宿主用它把"聚焦期间每帧全量重绘"换成"只在下一个闪烁边界安排一次延迟
     * 重绘"（见 [com.purride.pixelui.PixelHostView] 的 onDraw）：光标可见态每
     * [PixelTextFieldState.cursorBlinkPeriodMs] 的一半才翻转一次，没必要逐帧
     * postInvalidate。
     *
     * 未聚焦 / 关闭闪烁时返回 0L，调用方据此停止调度，循环自然停下；否则返回
     * `(半周期 - 已累计 elapsed)` 的正数，最小 1L。
     */
    internal fun millisUntilNextCursorBlink(state: PixelTextFieldState): Long {
        if (!state.isFocused || !state.cursorBlinkEnabled) return 0L
        val halfPeriodMs = (state.cursorBlinkPeriodMs / 2L).coerceAtLeast(1L)
        return (halfPeriodMs - state.cursorBlinkElapsedMs).coerceIn(1L, halfPeriodMs)
    }

    /** 设置选区；传入单点时表示折叠光标。 */
    public fun setSelection(
        state: PixelTextFieldState,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
    ) {
        state.selectionStart = selectionStart.coerceIn(0, state.text.length)
        state.selectionEnd = selectionEnd.coerceIn(state.selectionStart, state.text.length)
        notifyListeners()
    }

    /** 清空文本并把光标重置到开头。 */
    public fun clear(state: PixelTextFieldState) {
        updateText(state = state, text = "")
    }

    /** 选中当前文本的全部内容。 */
    public fun selectAll(state: PixelTextFieldState) {
        setSelection(
            state = state,
            selectionStart = 0,
            selectionEnd = state.text.length,
        )
    }

    /**
     * 返回 [state] 当前选区的文本；折叠选区返回空字符串。
     */
    public fun selectedText(state: PixelTextFieldState): String {
        if (state.selectionStart >= state.selectionEnd) return ""
        return state.text.substring(state.selectionStart, state.selectionEnd)
    }

    /**
     * 删除并返回 [state] 当前选区；没有非空选区时返回 `null`。
     */
    public fun cutSelection(state: PixelTextFieldState): String? {
        val selected = selectedText(state)
        if (selected.isEmpty()) return null
        replaceSelection(state, "")
        return selected
    }

    /**
     * 用 [text] 替换 [state] 当前选区，并把光标折叠到插入文本之后。
     */
    public fun paste(state: PixelTextFieldState, text: String) {
        if (text.isEmpty()) return
        replaceSelection(state, text)
    }

    private fun replaceSelection(state: PixelTextFieldState, replacement: String) {
        val start = state.selectionStart.coerceIn(0, state.text.length)
        val end = state.selectionEnd.coerceIn(start, state.text.length)
        val nextText = buildString(state.text.length - (end - start) + replacement.length) {
            append(state.text, 0, start)
            append(replacement)
            append(state.text, end, state.text.length)
        }
        val caret = start + replacement.length
        updateText(
            state = state,
            text = nextText,
            selectionStart = caret,
            selectionEnd = caret,
        )
    }

    /** 根据字符位置选择 ASCII 单词、单个非空白字符或折叠到空白处。 */
    public fun selectWordAt(state: PixelTextFieldState, index: Int) {
        val text = state.text
        if (text.isEmpty()) {
            setSelection(state, 0, 0)
            return
        }
        val safeIndex = index.coerceIn(0, text.length - 1)
        val char = text[safeIndex]
        if (char.isAsciiWord()) {
            var start = safeIndex
            while (start > 0 && text[start - 1].isAsciiWord()) start -= 1
            var end = safeIndex + 1
            while (end < text.length && text[end].isAsciiWord()) end += 1
            setSelection(state, start, end)
            return
        }
        if (char.isWhitespace()) {
            setSelection(state, safeIndex, safeIndex)
            return
        }
        setSelection(state, safeIndex, safeIndex + 1)
    }

    /** 立即把状态标记为已聚焦，并重置光标闪烁。 */
    public fun focus(state: PixelTextFieldState) {
        state.isFocused = true
        state.focusRequested = false
        state.blurRequested = false
        resetCursorBlink(state)
        notifyListeners()
    }

    /** 立即把状态标记为失焦，并取消待处理焦点请求。 */
    public fun blur(state: PixelTextFieldState) {
        state.isFocused = false
        state.focusRequested = false
        state.blurRequested = false
        resetCursorBlink(state)
        notifyListeners()
    }

    /** 请求下一帧聚焦；已经聚焦或存在待处理请求时不重复派发。 */
    public fun requestFocus(state: PixelTextFieldState) {
        if (state.isFocused || state.focusRequested) return
        state.focusRequested = true
        state.blurRequested = false
        notifyListeners()
    }

    /** 请求下一帧失焦；尚未应用的聚焦请求会被直接取消。 */
    public fun requestBlur(state: PixelTextFieldState) {
        if (state.blurRequested) return
        if (!state.isFocused) {
            if (!state.focusRequested) return
            state.focusRequested = false
            notifyListeners()
            return
        }
        state.blurRequested = true
        state.focusRequested = false
        notifyListeners()
    }

    private fun Char.isAsciiWord(): Boolean {
        return this == '_' || this in '0'..'9' || this in 'A'..'Z' || this in 'a'..'z'
    }
}
