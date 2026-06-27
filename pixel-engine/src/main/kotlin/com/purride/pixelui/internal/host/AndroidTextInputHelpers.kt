package com.purride.pixelui.internal.host

import android.text.InputFilter
import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.purride.pixelui.PixelInputType
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.PixelTextInputRequest
import com.purride.pixelui.internal.PixelTextInputTarget
import com.purride.pixelui.state.PixelTextFieldState

internal data class AndroidTextInputEditorConfig(
    val readOnly: Boolean,
    val minLines: Int,
    val maxLines: Int,
    val inputType: PixelInputType,
    val action: PixelTextInputAction,
)

internal fun PixelTextInputRequest.toAndroidEditorConfig(): AndroidTextInputEditorConfig {
    val safeMinLines = minLines.coerceAtLeast(1)
    return AndroidTextInputEditorConfig(
        readOnly = readOnly,
        minLines = safeMinLines,
        maxLines = maxLines.coerceAtLeast(safeMinLines),
        inputType = inputType,
        action = action,
    )
}

internal fun shouldRestartAndroidTextInput(
    wasFocused: Boolean,
    previous: AndroidTextInputEditorConfig?,
    next: AndroidTextInputEditorConfig,
): Boolean = !wasFocused || previous != next

internal fun findTextInputTargetForState(
    targets: List<PixelTextInputTarget>,
    state: PixelTextFieldState,
): PixelTextInputTarget? = targets.lastOrNull { it.state === state }

internal fun normalizePrintableAsciiUppercase(text: CharSequence): String = buildString(text.length) {
    text.forEach { char ->
        if (char.code in PRINTABLE_ASCII_RANGE) {
            append(char.uppercaseChar())
        }
    }
}

private val PRINTABLE_ASCII_RANGE = 32..126
internal val EMPTY_INPUT_FILTERS: Array<InputFilter> = emptyArray()
internal val ASCII_INPUT_FILTERS: Array<InputFilter> = arrayOf(
    InputFilter { source, start, end, _, _, _ ->
        val original = source.subSequence(start, end)
        val normalized = normalizePrintableAsciiUppercase(original)
        if (normalized.contentEquals(original)) null else normalized
    },
)

/**
 * 把 [PixelInputType] 映射到 Android 的 InputType 位组合。
 *
 * 数字/邮件/电话/URL/密码这些专属类型的 IME 强制单行（即便上层
 * 设置了 maxLines>1，多行数字键盘对实际产品意义不大）。
 *
 * 提取为 internal top-level 函数方便单元测试直接调用，无需构造真实
 * Android `EditText` 实例。
 */
internal fun resolveAndroidInputType(
    pixelType: PixelInputType,
    multiLine: Boolean,
): Int {
    return when (pixelType) {
        PixelInputType.TEXT -> {
            if (multiLine) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            } else {
                InputType.TYPE_CLASS_TEXT
            }
        }
        PixelInputType.ASCII -> {
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        PixelInputType.NUMBER -> {
            InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_SIGNED or
                InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        PixelInputType.NUMBER_PASSWORD -> {
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        PixelInputType.EMAIL -> {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        PixelInputType.PHONE -> InputType.TYPE_CLASS_PHONE
        PixelInputType.URL -> {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        PixelInputType.PASSWORD -> {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
    }
}

internal fun resolveAndroidImeOptions(
    action: PixelTextInputAction,
    inputType: PixelInputType,
): Int {
    val actionOption = when (action) {
        PixelTextInputAction.DONE -> EditorInfo.IME_ACTION_DONE
        PixelTextInputAction.NEXT -> EditorInfo.IME_ACTION_NEXT
        PixelTextInputAction.GO -> EditorInfo.IME_ACTION_GO
        PixelTextInputAction.SEARCH -> EditorInfo.IME_ACTION_SEARCH
        PixelTextInputAction.SEND -> EditorInfo.IME_ACTION_SEND
    }
    return if (inputType == PixelInputType.ASCII) {
        actionOption or EditorInfo.IME_FLAG_FORCE_ASCII
    } else {
        actionOption
    }
}
