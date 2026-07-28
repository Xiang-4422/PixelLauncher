package com.purride.pixelui

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.os.LocaleList
import android.view.KeyEvent
import android.view.InputDevice
import android.view.HapticFeedbackConstants
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
 * Android 平台编辑器 capability 实现。
 *
 * 这层把“隐藏输入框 + IME 同步”收进 pixel-engine UI layer，这样宿主页面不需要每次都手写
 * 一整套 `EditText + InputMethodManager` 样板。
 *
 * 当前职责很克制：
 * - 持有唯一的引擎自有隐藏编辑器
 * - 把 runtime 的焦点会话同步到隐藏编辑器
 * - 把隐藏编辑器的编辑值回写到 `PixelHostView`
 * - 以 [PixelImeCapability]、[PixelClipboardCapability]、[PixelHapticCapability] 的形式
 *   参与 Host 的 typed capability 装配
 *
 * [inputView] 固定为引擎自有编辑器，提供完整 grapheme、selection 和 composition 保证；
 * 不接受外部替换，避免出现无法拦截 InputConnection 的弱兼容编辑器。
 *
 * @param context 提供 Android IME、剪贴板和隐藏编辑器服务的 Context。
 * @param hostView 接收平台编辑事件的 Pixel Host。
 */
public class PixelTextInputBridge(
    context: Context,
    /** 接收平台输入并持有当前 retained TextField target 的 Host。 */
    private val hostView: PixelHostView,
) : PixelImeCapability, PixelClipboardCapability, PixelHapticCapability {

    /** Android IME 实际连接的引擎自有隐藏编辑器。 */
    private val engineInputView: PixelEngineTextInputView = PixelEngineTextInputView(context)

    /** 隐藏编辑器的 Android View 视图，供宿主容器挂载。 */
    public val inputView: EditText get() = engineInputView

    /** Android 输入法管理器；测试或无服务 Context 下允许为空。 */
    private val inputMethodManager = context.getSystemService(InputMethodManager::class.java)

    /** Android 系统剪贴板；不可用时读操作降级为空。 */
    private val clipboardManager = context.getSystemService(ClipboardManager::class.java)

    /** Host→View 同步的可重入深度，防止嵌套 watcher 把回写误判为用户输入。 */
    private var syncingFromHostDepth: Int = 0

    /** 当前平台编辑器配置，用于只在配置变化时重启同一目标的 IME。 */
    private var editorConfig: AndroidTextInputEditorConfig? = null

    /** 当前活动会话的引用身份，用于隔离复用同一隐藏 View 的多个字段会话。 */
    private var activeSessionId: Any? = null

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
        engineInputView.setEditingValueListener(::dispatchPlatformEditingValue)
    }

    /** 启动或切换会话；切换字段时先作废上一代 InputConnection，避免跨字段串写。 */
    override fun showTextInput(session: PixelTextEditingSession) {
        /** Identity changes only for a logically different retained text state. */
        val targetChanged = activeSessionId !== session.id
        if (targetChanged) {
            engineInputView.retireInputConnections()
            activeSessionId = session.id
        }
        showSessionInternal(session, forceRestartInput = targetChanged)
    }

    /** Shared show path with an explicit restart bit for logical session changes. */
    private fun showSessionInternal(
        session: PixelTextEditingSession,
        forceRestartInput: Boolean,
    ) {
        /** 当前会话的编辑器配置。 */
        val request = session.request
        /** Android editor configuration derived from the request fields. */
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
            applyEditingValue(session.value)
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

    /** 同步已激活会话；身份或编辑器配置变化时升级为一次完整 show。 */
    override fun updateTextInput(session: PixelTextEditingSession) {
        /** Android editor configuration used to detect metadata-changing updates. */
        val nextEditorConfig = session.request.toAndroidEditorConfig()
        if (activeSessionId !== session.id || !inputView.hasFocus() || editorConfig != nextEditorConfig) {
            showTextInput(session)
            return
        }
        syncingFromHostDepth += 1
        try {
            applyEditingValue(session.value)
        } finally {
            syncingFromHostDepth -= 1
        }
    }

    /** Hides the IME and retires its connection generation before another field can reuse the view. */
    override fun hideTextInput() {
        inputMethodManager?.hideSoftInputFromWindow(inputView.windowToken, 0)
        inputView.clearFocus()
        activeSessionId = null
        engineInputView.retireInputConnections()
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

    /** 把完整 text/selection/composition 状态写入引擎自有编辑器。 */
    private fun applyEditingValue(value: PixelTextEditingValue) {
        engineInputView.applyHostEditingValue(value)
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
