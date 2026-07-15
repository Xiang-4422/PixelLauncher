package com.purride.pixelui

import com.purride.pixelui.internal.PixelTextInputTarget
import com.purride.pixelui.internal.RenderSurface
import com.purride.pixelui.internal.host.findTextInputTargetForState
import com.purride.pixelui.state.PixelTextFieldState

/**
 * 管理 PixelHostView 的文本输入焦点、IME 请求、提交和光标闪烁。
 *
 * PixelHostView 仍然是 Android 入口；这里把文本输入状态机从绘制和手势路由中拆出来。
 */
internal class PixelHostTextInputCoordinator(
    /** Host whose current retained text target and platform bridge are coordinated atomically. */
    private val host: PixelHostView,
) {
    /** Applies one normalized platform editing snapshot to the currently focused writable target. */
    fun updateFocusedTextInput(
        text: String,
        selectionStart: Int = text.length,
        selectionEnd: Int = selectionStart,
        compositionStart: Int = -1,
        compositionEnd: Int = -1,
    ) {
        /** Active retained target authorized to receive this platform snapshot. */
        val target = host.focusedTextInputTarget ?: return
        if (target.readOnly) return
        /** Previous exact text used to suppress callbacks for offset-only mutations. */
        val previousText = target.state.text
        target.controller.updateText(
            state = target.state,
            text = text,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            compositionStart = compositionStart,
            compositionEnd = compositionEnd,
        )
        if (target.state.text != previousText) {
            target.onChanged?.invoke(target.state.text)
        }
        host.invalidate()
    }

    /** Ends composition, clears logical focus ownership, then retires the platform IME session. */
    fun clearFocusedTextInput() {
        /** Active target whose composition must end before its editor generation retires. */
        val target = host.focusedTextInputTarget ?: return
        target.controller.blur(target.state)
        host.hostBridge?.updateTarget(target)
        target.focusNode?.unfocus()
        host.nestedScrollSession.clearTextInputOwner()
        host.hostBridge?.hideTextInput()
        host.invalidate()
    }

    /** Invokes the target submit contract and performs NEXT traversal when requested. */
    fun submitFocusedTextInput() {
        /** Active target supplying submitted text and action semantics. */
        val target = host.focusedTextInputTarget ?: return
        target.onSubmitted?.invoke(target.state.text)
        if (target.action == PixelTextInputAction.NEXT) {
            host.dispatchPixelKeyEvent(PixelKeyEvent(PixelKey.TAB))
        }
        host.invalidate()
    }

    /** Routes clipboard and select-all commands through the focused target's controller. */
    fun performEditAction(action: PixelTextEditAction): Boolean {
        /** Active target whose read-only and boundary invariants govern the edit. */
        val target = host.focusedTextInputTarget ?: return false
        /** Optional platform bridge providing clipboard capability. */
        val bridge = host.hostBridge
        /** Whether the action changed text and therefore needs callback plus editor rebind. */
        val changed = when (action) {
            PixelTextEditAction.COPY -> {
                /** Whole-grapheme selection copied without changing retained text. */
                val selected = target.controller.selectedText(target.state)
                if (selected.isEmpty()) return false
                bridge?.writeClipboardText(selected)
                false
            }
            PixelTextEditAction.CUT -> {
                if (target.readOnly) return false
                /** Removed whole-grapheme selection written to clipboard after successful cut. */
                val selected = target.controller.cutSelection(target.state) ?: return false
                bridge?.writeClipboardText(selected)
                true
            }
            PixelTextEditAction.PASTE -> {
                if (target.readOnly) return false
                /** Exact clipboard payload inserted without Unicode normalization. */
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

    /** Advances the focused field's deterministic cursor-blink clock. */
    fun stepCursorBlink(deltaMs: Long) {
        /** Active target whose controller owns the blink phase. */
        val target = host.focusedTextInputTarget ?: return
        /** 当前 retained 文本表面；真实 pipeline target 始终由 RenderSurface 导出。 */
        val retainedSurface = target.source as? RenderSurface
        /**
         * 真实 retained surface 可以直接 paint-only 更新；兼容手工构造的无 source target 时，
         * 继续走公开通知路径，保证旧调用方仍能通过 controller listener 请求重建。
         */
        val visibilityChanged = if (retainedSurface != null) {
            target.controller.stepCursorBlinkForHost(target.state, deltaMs)
        } else {
            target.controller.stepCursorBlink(target.state, deltaMs)
        }
        if (visibilityChanged) {
            retainedSurface?.updateTextInputCursorVisibility(target.state.cursorVisible)
        }
    }

    /** Schedules the next host invalidation at the controller's cursor-blink boundary. */
    fun scheduleNextCursorBlinkInvalidate() {
        /** Active target supplying the remaining blink interval. */
        val target = host.focusedTextInputTarget ?: return
        /** Positive delay until the next visibility transition. */
        val delayMs = target.controller.millisUntilNextCursorBlink(target.state)
        if (delayMs > 0L) host.postInvalidateDelayed(delayMs)
    }

    /** Reconciles retained targets with explicit blur/focus requests and one-shot autofocus. */
    fun syncRequestedFocus(targets: List<PixelTextInputTarget>) {
        rebindFocusedTarget(targets)
        /** Focused target whose external FocusNode no longer grants logical ownership. */
        val logicallyBlurredTarget = host.focusedTextInputTarget?.takeIf { target ->
            /** Optional node that mirrors focus ownership outside the TextField state. */
            val node = target.focusNode
            node != null && !node.isFocused
        }
        if (logicallyBlurredTarget != null) {
            clearFocusedTextInput()
        }
        /** Focused target carrying an explicit controller blur request. */
        val blurTarget = host.focusedTextInputTarget?.takeIf { it.state.blurRequested }
        if (blurTarget != null) {
            blurTarget.state.blurRequested = false
            clearFocusedTextInput()
            return
        }
        /** Last explicit focus request, matching retained traversal conflict semantics. */
        val requestedTarget = targets.lastOrNull { it.state.focusRequested }
        if (requestedTarget != null) {
            requestedTarget.state.focusRequested = false
            focus(requestedTarget)
            requestedTarget.state.autofocusConsumed = true
            return
        }
        /** Last eligible one-shot autofocus target when no field currently owns the editor. */
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

    /** Rebinds a rebuilt target sharing the same state without creating a new logical IME session. */
    private fun rebindFocusedTarget(targets: List<PixelTextInputTarget>) {
        /** Previously focused target retained from the preceding render tree. */
        val previous = host.focusedTextInputTarget ?: return
        /** Rebuilt target sharing the exact state identity, or null when it left the tree. */
        val current = findTextInputTargetForState(targets, previous.state)
        if (current == null) {
            clearFocusedTextInput()
        } else {
            if (current !== previous) {
                host.nestedScrollSession.markTextInputOwner(current)
            }
            host.hostBridge?.updateTarget(current)
        }
    }

    /** Focuses [target], ending any previous field's composition before switching sessions. */
    fun focus(target: PixelTextInputTarget) {
        if (host.focusedTextInputTarget?.state !== target.state) {
            host.focusedTextInputTarget?.let { previous ->
                previous.controller.blur(previous.state)
            }
        }
        target.controller.focus(target.state)
        target.focusNode?.requestFocus()
        host.nestedScrollSession.markTextInputOwner(target)
        host.hostBridge?.showTarget(target)
    }
}

/**
 * 显示 [target] 的输入会话，并仅向 opt-in bridge 发送 composition 扩展值。
 */
private fun PixelHostBridge.showTarget(target: PixelTextInputTarget) {
    /** Frozen compatibility request shared by full and legacy bridge paths. */
    val request = target.toTextInputRequest()
    if (this is PixelTextInputBridge) {
        showTextEditingForTarget(
            request = request,
            value = target.toTextEditingValue(),
            targetIdentity = target.state,
        )
    } else if (this is PixelTextEditingHostBridge) {
        showTextEditing(request = request, value = target.toTextEditingValue())
    } else {
        showTextInput(request)
    }
}

/**
 * 更新 [target] 的输入会话，同时保留旧 bridge 的八字段请求兼容路径。
 */
private fun PixelHostBridge.updateTarget(target: PixelTextInputTarget) {
    /** Frozen compatibility request shared by full and legacy bridge paths. */
    val request = target.toTextInputRequest()
    if (this is PixelTextInputBridge) {
        updateTextEditingForTarget(
            request = request,
            value = target.toTextEditingValue(),
            targetIdentity = target.state,
        )
    } else if (this is PixelTextEditingHostBridge) {
        updateTextEditing(request = request, value = target.toTextEditingValue())
    } else {
        updateTextInput(request)
    }
}

/** Converts one retained target into the frozen request-only compatibility contract. */
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

/** Converts the retained text-field state into the additive full editing-value contract. */
private fun PixelTextInputTarget.toTextEditingValue(): PixelTextEditingValue = PixelTextEditingValue(
    text = state.text,
    selectionStart = state.selectionStart,
    selectionEnd = state.selectionEnd,
    compositionStart = state.compositionStart,
    compositionEnd = state.compositionEnd,
)
