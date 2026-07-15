package com.purride.pixelui

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.text.Editable
import android.text.TextWatcher
import android.os.LocaleList
import android.view.KeyEvent
import android.view.InputDevice
import android.view.HapticFeedbackConstants
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.purride.pixelui.internal.host.ASCII_INPUT_FILTERS
import com.purride.pixelui.internal.host.AndroidTextInputEditorConfig
import com.purride.pixelui.internal.host.EMPTY_INPUT_FILTERS
import com.purride.pixelui.internal.host.PixelEngineTextInputView
import com.purride.pixelui.internal.host.resolveAndroidImeOptions
import com.purride.pixelui.internal.host.resolveAndroidInputType
import com.purride.pixelui.internal.host.shouldRestartAndroidTextInput
import com.purride.pixelui.internal.host.toAndroidEditorConfig
import com.purride.pixelui.internal.host.mapAndroidKeyCodeToPixelKeyEvent
import com.purride.pixelui.internal.host.mapAndroidKeyCodeToPixelTextInputEvent
import java.util.Locale

/**
 * 默认的 Android 文本输入桥接。
 *
 * 这层把“隐藏输入框 + IME 同步”收进 pixel-engine UI layer，这样宿主页面不需要每次都手写
 * 一整套 `EditText + TextWatcher + InputMethodManager` 样板。
 *
 * 当前职责很克制：
 * - 持有一个隐藏 `EditText`
 * - 把 runtime 的焦点文本同步到隐藏输入框
 * - 把隐藏输入框的文本变化回写到 `PixelHostView`
 * - 提供一个默认的 `PixelHostBridge` 实现
 *
 * 默认 [inputView] 是引擎自有编辑器，提供完整 grapheme、selection 和 composition 保证。
 * 显式传入普通 `EditText` 只保留旧版文本回调兼容，无法拦截其 InputConnection，也不能
 * 保证 selection-only/composition-only 同步；需要完整 1.0 输入契约时不得替换默认实例。
 *
 * @param context 提供 Android IME、剪贴板和隐藏编辑器服务的 Context。
 * @param hostView 接收平台编辑事件的 Pixel Host。
 * @param inputView 隐藏平台编辑器；默认值提供完整保证，自定义实例仅为弱兼容路径。
 */
public class PixelTextInputBridge(
    context: Context,
    /** 接收平台输入并持有当前 retained TextField target 的 Host。 */
    private val hostView: PixelHostView,
    /** Android IME 实际连接的隐藏编辑器。 */
    public val inputView: EditText = createDefaultTextInputView(context),
) : PixelTextEditingHostBridge {

    /** Android 输入法管理器；测试或无服务 Context 下允许为空。 */
    private val inputMethodManager = context.getSystemService(InputMethodManager::class.java)

    /** Android 系统剪贴板；不可用时读操作降级为空。 */
    private val clipboardManager = context.getSystemService(ClipboardManager::class.java)

    /** Host→View 同步的可重入深度，防止嵌套 watcher 把回写误判为用户输入。 */
    private var syncingFromHostDepth: Int = 0

    /** 当前平台编辑器配置，用于只在配置变化时重启同一目标的 IME。 */
    private var editorConfig: AndroidTextInputEditorConfig? = null

    /** 当前 retained state 的引用身份，用于隔离复用同一隐藏 View 的多个字段会话。 */
    private var activeTargetIdentity: Any? = null

    init {
        inputView.alpha = 0f
        // 隐藏编辑器只承载焦点和 InputConnection；禁止 TextView 绘制可避开光标、背景与字体 display-list 开销。
        inputView.setWillNotDraw(true)
        inputView.isFocusable = true
        inputView.isFocusableInTouchMode = true
        inputView.setSingleLine()
        inputView.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN || !shouldForwardFocusedEditorKey(keyCode, event)) {
                false
            } else {
                /** Exact supplementary-safe payload when this key represents text input. */
                val textInputEvent = mapAndroidKeyCodeToPixelTextInputEvent(
                    keyCode = keyCode,
                    unicodeChar = event.unicodeChar,
                )
                if (textInputEvent != null) {
                    hostView.dispatchPixelTextInput(textInputEvent)
                } else {
                    hostView.dispatchPixelKeyEvent(
                        mapAndroidKeyCodeToPixelKeyEvent(
                            keyCode = keyCode,
                            isShiftPressed = event.isShiftPressed,
                            unicodeChar = event.unicodeChar,
                        ),
                    )
                }
            }
        }
        inputView.setOnEditorActionListener { _, actionId, event ->
            /** Physical Enter down event that can submit a single-line editor. */
            val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            /** Software-IME action ids that request form submission or traversal. */
            val isSubmitAction = actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_NEXT ||
                actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_SEND
            /** Single-line physical Enter path kept equivalent to its software action. */
            val isSingleLineEnter = inputView.maxLines <= 1 && isEnterKey
            if (isSingleLineEnter || isSubmitAction) {
                hostView.submitFocusedTextInput()
                true
            } else {
                false
            }
        }
        if (inputView is PixelEngineTextInputView) {
            inputView.setEditingValueListener(::dispatchPlatformEditingValue)
        } else {
            installLegacyTextWatcher()
        }
    }

    /** Preserves the frozen request-only entry point by deriving an empty-composition value. */
    override fun showTextInput(request: PixelTextInputRequest) {
        showTextEditing(request = request, value = request.toEditingValue())
    }

    /** Shows the default Android editor with full selection and composition state. */
    override fun showTextEditing(
        request: PixelTextInputRequest,
        value: PixelTextEditingValue,
    ) {
        /** Direct callers cannot provide target identity, so every show establishes a fresh session. */
        val wasFocused = inputView.hasFocus()
        activeTargetIdentity = null
        (inputView as? PixelEngineTextInputView)?.retireInputConnections()
        showTextEditingInternal(request, value, forceRestartInput = wasFocused)
    }

    /** Starts a target-identified session so stale connections cannot cross TextField boundaries. */
    internal fun showTextEditingForTarget(
        request: PixelTextInputRequest,
        value: PixelTextEditingValue,
        targetIdentity: Any,
    ) {
        /** Identity changes only for a logically different retained text state. */
        val targetChanged = activeTargetIdentity !== targetIdentity
        if (targetChanged) {
            (inputView as? PixelEngineTextInputView)?.retireInputConnections()
            activeTargetIdentity = targetIdentity
        }
        showTextEditingInternal(request, value, forceRestartInput = targetChanged)
    }

    /** Shared show path with an explicit restart bit for logical target changes. */
    private fun showTextEditingInternal(
        request: PixelTextInputRequest,
        value: PixelTextEditingValue,
        forceRestartInput: Boolean,
    ) {
        /** Android editor configuration derived from the frozen request fields. */
        val nextEditorConfig = request.toAndroidEditorConfig()
        /** Whether Android must discard cached editor metadata for this show. */
        val shouldRestartInput = shouldRestartAndroidTextInput(
            wasFocused = inputView.hasFocus(),
            previous = editorConfig,
            next = nextEditorConfig,
        ) || (forceRestartInput && inputView.hasFocus())
        syncingFromHostDepth += 1
        try {
            if (editorConfig != nextEditorConfig) {
                configureLineMode(request)
                inputView.imeOptions = resolveAndroidImeOptions(request.action, request.inputType)
                inputView.imeHintLocales = if (request.inputType == PixelInputType.ASCII) {
                    LocaleList(Locale.ENGLISH)
                } else {
                    null
                }
                editorConfig = nextEditorConfig
            }
            applyEditingValue(value)
        } finally {
            syncingFromHostDepth -= 1
        }

        if (request.readOnly) {
            inputMethodManager?.hideSoftInputFromWindow(inputView.windowToken, 0)
            inputView.clearFocus()
        } else {
            inputView.requestFocus()
            if (shouldRestartInput) {
                inputMethodManager?.restartInput(inputView)
            }
            inputMethodManager?.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /** Preserves the frozen request-only update path for legacy bridge callers. */
    override fun updateTextInput(request: PixelTextInputRequest) {
        updateTextEditing(request = request, value = request.toEditingValue())
    }

    /** Updates the active Android editor without discarding an engine composition range. */
    override fun updateTextEditing(
        request: PixelTextInputRequest,
        value: PixelTextEditingValue,
    ) {
        /** Android editor configuration used to detect metadata-changing updates. */
        val nextEditorConfig = request.toAndroidEditorConfig()
        if (!inputView.hasFocus() || editorConfig != nextEditorConfig) {
            showTextEditing(request = request, value = value)
            return
        }
        syncingFromHostDepth += 1
        try {
            applyEditingValue(value)
        } finally {
            syncingFromHostDepth -= 1
        }
    }

    /** Updates one identified target, promoting an unexpected identity change to a new session. */
    internal fun updateTextEditingForTarget(
        request: PixelTextInputRequest,
        value: PixelTextEditingValue,
        targetIdentity: Any,
    ) {
        if (activeTargetIdentity !== targetIdentity) {
            showTextEditingForTarget(request, value, targetIdentity)
        } else {
            updateTextEditing(request, value)
        }
    }

    /** Hides the IME and retires its connection generation before another field can reuse the view. */
    override fun hideTextInput() {
        inputMethodManager?.hideSoftInputFromWindow(inputView.windowToken, 0)
        inputView.clearFocus()
        activeTargetIdentity = null
        (inputView as? PixelEngineTextInputView)?.retireInputConnections()
    }

    /** Maps the stable SDK haptic enum to the closest Android feedback constant. */
    override fun performHapticFeedback(type: PixelHapticType) {
        /** Android constant selected without exposing platform values through the SDK API. */
        val feedbackConstant = when (type) {
            PixelHapticType.TAP -> HapticFeedbackConstants.KEYBOARD_TAP
            PixelHapticType.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
        }
        hostView.performHapticFeedback(feedbackConstant)
    }

    /** Requests one retained Host render frame. */
    override fun requestFrame() {
        hostView.requestRender()
    }

    /** Default bridge has no generic string-based system action to execute. */
    override fun dispatchSystemAction(action: PixelSystemAction): Unit = Unit

    /** Reads the first primary-clip item when Android clipboard service is available. */
    override fun readClipboardText(): String? {
        /** Current primary clip, absent when the service or user content is unavailable. */
        val clip = clipboardManager?.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        return clip.getItemAt(0)?.coerceToText(inputView.context)?.toString()
    }

    /** Replaces the primary clip with an engine-owned plain-text item. */
    override fun writeClipboardText(text: String) {
        clipboardManager?.setPrimaryClip(ClipData.newPlainText("pixel-text", text))
    }

    /** Applies full state on the engine-owned view and the legacy text/selection subset otherwise. */
    private fun applyEditingValue(value: PixelTextEditingValue) {
        /** Full-contract editor, or null for the explicitly supplied weak compatibility path. */
        val engineInputView = inputView as? PixelEngineTextInputView
        if (engineInputView != null) {
            engineInputView.applyHostEditingValue(value)
            return
        }
        if (inputView.text?.toString() != value.text) {
            inputView.setText(value.text)
        }
        /** Post-write length constraining legacy EditText selection offsets. */
        val textLength = inputView.text?.length ?: 0
        /** Legacy selection start clipped to the actual filtered text. */
        val safeSelectionStart = value.selectionStart.coerceIn(0, textLength)
        /** Legacy selection end ordered after the clipped start. */
        val safeSelectionEnd = value.selectionEnd.coerceIn(safeSelectionStart, textLength)
        if (inputView.selectionStart != safeSelectionStart || inputView.selectionEnd != safeSelectionEnd) {
            inputView.setSelection(safeSelectionStart, safeSelectionEnd)
        }
    }

    /** Sends one engine-owned view snapshot back to the focused retained TextField. */
    private fun dispatchPlatformEditingValue(value: PixelTextEditingValue) {
        if (syncingFromHostDepth > 0) return
        hostView.updateFocusedTextInput(
            text = value.text,
            selectionStart = value.selectionStart,
            selectionEnd = value.selectionEnd,
            compositionStart = value.compositionStart,
            compositionEnd = value.compositionEnd,
        )
    }

    /**
     * Installs the compatibility watcher for explicitly supplied ordinary EditText instances.
     *
     * This path retains historical text callbacks but cannot observe composition-only changes or
     * wrap an arbitrary subclass's InputConnection; full guarantees require the default view.
     */
    private fun installLegacyTextWatcher() {
        inputView.addTextChangedListener(
            /** Compatibility observer used only when the consumer replaces the engine editor. */
            object : TextWatcher {
                /** Legacy watcher has no work before a text mutation. */
                override fun beforeTextChanged(
                    text: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ): Unit = Unit

                /** Legacy watcher waits for the final Editable snapshot. */
                override fun onTextChanged(
                    text: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ): Unit = Unit

                /** Publishes the best available text, selection, and composing-span snapshot. */
                override fun afterTextChanged(editable: Editable?) {
                    if (syncingFromHostDepth > 0) return
                    /** Platform composing marker start visible on the legacy Editable. */
                    val compositionStart = editable
                        ?.let(BaseInputConnection::getComposingSpanStart)
                        ?: -1
                    /** Platform composing marker end visible on the legacy Editable. */
                    val compositionEnd = editable
                        ?.let(BaseInputConnection::getComposingSpanEnd)
                        ?: -1
                    dispatchPlatformEditingValue(
                        PixelTextEditingValue(
                            text = editable?.toString().orEmpty(),
                            selectionStart = inputView.selectionStart.coerceAtLeast(0),
                            selectionEnd = inputView.selectionEnd.coerceAtLeast(0),
                            compositionStart = compositionStart,
                            compositionEnd = compositionEnd,
                        ),
                    )
                }
            },
        )
    }

    /** Applies validated line counts, input type, and ASCII filtering to the hidden editor. */
    private fun configureLineMode(request: PixelTextInputRequest) {
        /** At least one logical line is always required by Android EditText. */
        val safeMinLines = request.minLines.coerceAtLeast(1)
        /** Maximum line count ordered after the validated minimum. */
        val safeMaxLines = request.maxLines.coerceAtLeast(safeMinLines)
        /** Whether Android should expose newline-capable editor behavior. */
        val multiLine = safeMaxLines > 1
        if (multiLine) {
            inputView.setSingleLine(false)
            inputView.setMinLines(safeMinLines)
            inputView.setMaxLines(safeMaxLines)
        } else {
            inputView.setSingleLine()
            inputView.setMinLines(1)
            inputView.setMaxLines(1)
        }
        inputView.inputType = resolveAndroidInputType(request.inputType, multiLine)
        inputView.filters = if (request.inputType == PixelInputType.ASCII) {
            ASCII_INPUT_FILTERS
        } else {
            EMPTY_INPUT_FILTERS
        }
    }

    /**
     * Gives text editing priority to keyboard characters/arrows while reserving traversal keys.
     *
     * Tab always leaves the field through the Pixel focus owner. Direction and activation keys
     * are forwarded only for DPAD/gamepad sources; physical-keyboard arrows and Enter remain with
     * the hidden editor for cursor movement, newline, or IME submission.
     */
    private fun shouldForwardFocusedEditorKey(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_TAB || keyCode == KeyEvent.KEYCODE_ESCAPE) return true
        /** Whether the event comes from navigation hardware rather than a typing keyboard. */
        val isDirectionalSource = event.isFromSource(InputDevice.SOURCE_DPAD) ||
            event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK)
        if (!isDirectionalSource) return false
        return keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_BUTTON_A ||
            keyCode == KeyEvent.KEYCODE_BUTTON_B ||
            keyCode == KeyEvent.KEYCODE_BUTTON_START ||
            keyCode == KeyEvent.KEYCODE_BUTTON_SELECT ||
            keyCode == KeyEvent.KEYCODE_BUTTON_MODE
    }
}

/** Creates the engine-owned editor without exposing its internal implementation in public defaults. */
private fun createDefaultTextInputView(context: Context): EditText {
    return PixelEngineTextInputView(context)
}

/** Converts the frozen request subset into a complete value with no active composition. */
private fun PixelTextInputRequest.toEditingValue(): PixelTextEditingValue = PixelTextEditingValue(
    text = text,
    selectionStart = selectionStart,
    selectionEnd = selectionEnd,
)
