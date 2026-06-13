package com.purride.pixelui

import com.purride.pixelui.internal.PixelTextInputTarget

/**
 * Owns PixelHostView text input focus, IME request, submit, and cursor blink coordination.
 *
 * PixelHostView remains the Android entry point; this class keeps the text input state machine
 * out of the view drawing and gesture routing code.
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

    fun focus(target: PixelTextInputTarget) {
        if (host.focusedTextInputTarget?.state !== target.state) {
            host.focusedTextInputTarget?.let { previous ->
                previous.controller.blur(previous.state)
            }
        }
        target.controller.focus(target.state)
        target.focusNode?.requestFocus()
        host.nestedScrollSession.markTextInputOwner(target)
        host.hostBridge?.showTextInput(
            PixelTextInputRequest(
                text = target.state.text,
                selectionStart = target.state.selectionStart,
                selectionEnd = target.state.selectionEnd,
                readOnly = target.readOnly,
                minLines = target.minLines,
                maxLines = target.maxLines,
                inputType = target.inputType,
                action = target.action,
            ),
        )
    }
}
