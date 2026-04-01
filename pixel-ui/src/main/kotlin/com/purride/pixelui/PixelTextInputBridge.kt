package com.purride.pixelui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * 默认的 Android 文本输入桥接。
 *
 * 这层把“隐藏输入框 + IME 同步”收进 `pixel-ui`，这样宿主页面不需要每次都手写
 * 一整套 `EditText + TextWatcher + InputMethodManager` 样板。
 *
 * 当前职责很克制：
 * - 持有一个隐藏 `EditText`
 * - 把 runtime 的焦点文本同步到隐藏输入框
 * - 把隐藏输入框的文本变化回写到 `PixelHostView`
 * - 提供一个默认的 `PixelHostBridge` 实现
 */
class PixelTextInputBridge(
    context: Context,
    private val hostView: PixelHostView,
    val inputView: EditText = EditText(context),
) : PixelHostBridge {

    private val inputMethodManager = context.getSystemService(InputMethodManager::class.java)
    private var syncingFromHost = false

    init {
        inputView.alpha = 0f
        inputView.isFocusable = true
        inputView.isFocusableInTouchMode = true
        inputView.setSingleLine()
        inputView.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    if (syncingFromHost) {
                        return
                    }
                    hostView.updateFocusedTextInput(
                        text = s?.toString().orEmpty(),
                        selectionStart = inputView.selectionStart.coerceAtLeast(0),
                        selectionEnd = inputView.selectionEnd.coerceAtLeast(0),
                    )
                }
            },
        )
    }

    override fun showTextInput(request: PixelTextInputRequest) {
        syncingFromHost = true
        try {
            if (inputView.text?.toString() != request.text) {
                inputView.setText(request.text)
            }
            val textLength = inputView.text?.length ?: 0
            val safeSelectionStart = request.selectionStart.coerceIn(0, textLength)
            val safeSelectionEnd = request.selectionEnd.coerceIn(safeSelectionStart, textLength)
            inputView.setSelection(safeSelectionStart, safeSelectionEnd)
        } finally {
            syncingFromHost = false
        }

        inputView.requestFocus()
        inputMethodManager?.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun hideTextInput() {
        inputMethodManager?.hideSoftInputFromWindow(inputView.windowToken, 0)
        inputView.clearFocus()
    }

    override fun performHapticFeedback(type: PixelHapticType) {
        val feedbackConstant = when (type) {
            PixelHapticType.TAP -> HapticFeedbackConstants.KEYBOARD_TAP
            PixelHapticType.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
        }
        hostView.performHapticFeedback(feedbackConstant)
    }

    override fun requestFrame() {
        hostView.requestRender()
    }

    override fun dispatchSystemAction(action: PixelSystemAction) = Unit
}
