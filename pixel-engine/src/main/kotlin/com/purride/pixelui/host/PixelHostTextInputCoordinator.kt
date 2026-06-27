package com.purride.pixelui

import com.purride.pixelui.internal.PixelTextInputTarget
import com.purride.pixelui.internal.host.findTextInputTargetForState
import com.purride.pixelui.state.PixelTextFieldState

/**
 * 管理 PixelHostView 的文本输入焦点、IME 请求、提交和光标闪烁。
 *
 * PixelHostView 仍然是 Android 入口；这里把文本输入状态机从绘制和手势路由中拆出来。
 */
internal class PixelHostTextInputCoordinator(
    private val host: PixelHostView,
) {
    fun updateFocusedTextInput(
        text: String,
        selectionStart: Int = text.length,
        selectionEnd: Int = selectionStart,
        compositionStart: Int = -1,
        compositionEnd: Int = -1,
    ) {
        val target = host.focusedTextInputTarget ?: return
        if (target.readOnly) return
        target.controller.updateText(
            state = target.state,
            text = text,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            compositionStart = compositionStart,
            compositionEnd = compositionEnd,
        )
        target.onChanged?.invoke(text)
        host.invalidate()
    }

    fun clearFocusedTextInput() {
        val target = host.focusedTextInputTarget ?: return
        target.controller.blur(target.state)
        target.focusNode?.unfocus()
        host.nestedScrollSession.clearTextInputOwner()
        host.hostBridge?.hideTextInput()
        host.invalidate()
    }

    fun submitFocusedTextInput() {
        val target = host.focusedTextInputTarget ?: return
        target.onSubmitted?.invoke(target.state.text)
        if (target.action == PixelTextInputAction.NEXT) {
            PixelFocusManager.dispatchKeyEvent(PixelKeyEvent(PixelKey.TAB))
        }
        host.invalidate()
    }

    fun performEditAction(action: PixelTextEditAction): Boolean {
        val target = host.focusedTextInputTarget ?: return false
        val bridge = host.hostBridge
        val changed = when (action) {
            PixelTextEditAction.COPY -> {
                val selected = target.controller.selectedText(target.state)
                if (selected.isEmpty()) return false
                bridge?.writeClipboardText(selected)
                false
            }
            PixelTextEditAction.CUT -> {
                if (target.readOnly) return false
                val selected = target.controller.cutSelection(target.state) ?: return false
                bridge?.writeClipboardText(selected)
                true
            }
            PixelTextEditAction.PASTE -> {
                if (target.readOnly) return false
                val clipboardText = bridge?.readClipboardText().orEmpty()
                if (clipboardText.isEmpty()) return false
                target.controller.paste(target.state, clipboardText)
                true
            }
            PixelTextEditAction.SELECT_ALL -> {
                if (target.state.text.isEmpty()) return false
                target.controller.selectAll(target.state)
                false
            }
        }
        if (changed) {
            target.onChanged?.invoke(target.state.text)
            focus(target)
        }
        host.invalidate()
        return true
    }

    fun stepCursorBlink(deltaMs: Long) {
        val target = host.focusedTextInputTarget ?: return
        target.controller.stepCursorBlink(target.state, deltaMs)
    }

    fun scheduleNextCursorBlinkInvalidate() {
        val target = host.focusedTextInputTarget ?: return
        val delayMs = target.controller.millisUntilNextCursorBlink(target.state)
        if (delayMs > 0L) host.postInvalidateDelayed(delayMs)
    }

    fun syncRequestedFocus(targets: List<PixelTextInputTarget>) {
        rebindFocusedTarget(targets)
        val blurTarget = host.focusedTextInputTarget?.takeIf { it.state.blurRequested }
        if (blurTarget != null) {
            blurTarget.state.blurRequested = false
            clearFocusedTextInput()
            return
        }
        val requestedTarget = targets.lastOrNull { it.state.focusRequested }
        if (requestedTarget != null) {
            requestedTarget.state.focusRequested = false
            focus(requestedTarget)
            requestedTarget.state.autofocusConsumed = true
            return
        }
        val autofocusTarget = targets.lastOrNull {
            it.autofocus &&
                !it.state.autofocusConsumed &&
                host.focusedTextInputTarget == null
        }
        if (autofocusTarget != null) {
            autofocusTarget.state.autofocusConsumed = true
            focus(autofocusTarget)
        }
    }

    private fun rebindFocusedTarget(targets: List<PixelTextInputTarget>) {
        val previous = host.focusedTextInputTarget ?: return
        val current = findTextInputTargetForState(targets, previous.state)
        if (current == null) {
            clearFocusedTextInput()
        } else {
            if (current !== previous) {
                host.nestedScrollSession.markTextInputOwner(current)
            }
            host.hostBridge?.updateTextInput(current.toTextInputRequest())
        }
    }

    fun focus(target: PixelTextInputTarget) {
        if (host.focusedTextInputTarget?.state !== target.state) {
            host.focusedTextInputTarget?.let { previous ->
                previous.controller.blur(previous.state)
            }
        }
        target.controller.focus(target.state)
        target.focusNode?.requestFocus()
        host.nestedScrollSession.markTextInputOwner(target)
        host.hostBridge?.showTextInput(target.toTextInputRequest())
    }
}

private fun PixelTextInputTarget.toTextInputRequest(): PixelTextInputRequest = PixelTextInputRequest(
    text = state.text,
    selectionStart = state.selectionStart,
    selectionEnd = state.selectionEnd,
    readOnly = readOnly,
    minLines = minLines,
    maxLines = maxLines,
    inputType = inputType,
    action = action,
)
