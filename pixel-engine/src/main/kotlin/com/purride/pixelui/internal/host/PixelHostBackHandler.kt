package com.purride.pixelui.internal.host

import com.purride.pixelui.PixelBackDispatcher

internal fun handlePixelHostBack(
    hasFocusedTextInput: Boolean,
    clearFocusedTextInput: () -> Unit,
    backDispatcher: PixelBackDispatcher?,
    onUnhandledBack: (() -> Boolean)?,
    onHandled: () -> Unit,
): Boolean {
    // 宿主 back 顺序必须稳定：先让输入框失焦，再给 widget 栈，最后才交给 app。
    if (hasFocusedTextInput) {
        clearFocusedTextInput()
        onHandled()
        return true
    }
    if (backDispatcher?.handleBack() == true) {
        onHandled()
        return true
    }
    if (onUnhandledBack?.invoke() == true) {
        onHandled()
        return true
    }
    return false
}
