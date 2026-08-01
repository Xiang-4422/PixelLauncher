package com.purride.pixellockscreen.credential

import com.purride.pixellockscreen.ui.PinCredentialFeedback
import com.purride.pixellockscreen.ui.PinCredentialListener
import com.purride.pixellockscreen.ui.PinCredentialUiState

/**
 * 像素 PIN 键盘与 Android 系统校验任务之间的可清零输入协调器。
 *
 * 协调器只在 [CredentialInputSession] 的字符数组中保存尚未提交的数字，UI 状态只接收长度。
 */
internal class PinCredentialCoordinator(
    /** Android 设置启用自动确认时要求的精确 PIN 长度。 */
    private val autoConfirmLength: Int? = null,
    /** 每次有效键盘动作通知原生 Keyguard 用户活动的出口。 */
    private val onUserInput: () -> Unit,
    /** 接收一次独占 PIN lease 并立即交给系统校验桥的出口。 */
    private val onCredentialReady: (EphemeralCredentialLease.Characters) -> Unit,
    /** 请求 ROM 原生紧急操作的出口。 */
    private val onEmergencyAction: () -> Unit,
    /** 接收不含数字内容的像素 PIN 状态。 */
    private val onStateChanged: (PinCredentialUiState) -> Unit,
    /** 接收交互异常并要求恢复原生页面的出口。 */
    private val onInteractionFailed: (Throwable) -> Unit,
) : PinCredentialListener, AutoCloseable {
    /** 当前会话唯一的可清零 PIN 输入缓冲。 */
    private val inputSession: CredentialInputSession = CredentialInputSession(PixelCredentialMode.PIN)

    /** 协调器是否已经永久关闭。 */
    private var closed: Boolean = false

    /** 当前是否允许修改或提交 PIN 缓冲。 */
    private var inputEnabled: Boolean = false

    /** 拒绝系统不会接受的自动确认长度。 */
    init {
        require(autoConfirmLength == null || autoConfirmLength in MINIMUM_PIN_LENGTH..MAXIMUM_PIN_LENGTH) {
            "pin_auto_confirm_length"
        }
    }

    /** 清空输入并显示初始可输入状态。 */
    fun showReady() {
        ensureOpen()
        inputSession.clear()
        inputEnabled = true
        emitState(PinCredentialFeedback.READY, "")
    }

    /** 清空输入并显示 Android 已拒绝上一次 PIN 的状态。 */
    fun showRejected() {
        ensureOpen()
        inputSession.clear()
        inputEnabled = true
        emitState(PinCredentialFeedback.ERROR, REJECTED_TEXT)
    }

    /** 清空输入并显示系统截止时间剩余秒数。 */
    fun showLockedOut(remainingSeconds: Int) {
        ensureOpen()
        require(remainingSeconds > 0) { "pin_lockout_seconds" }
        inputSession.clear()
        inputEnabled = false
        emitState(PinCredentialFeedback.LOCKED_OUT, "WAIT ${remainingSeconds}S")
    }

    /** 追加一个 ASCII 数字并只把新长度提交给 UI。 */
    override fun onDigitEntered(digit: Char) {
        ensureOpen()
        require(digit in '0'..'9') { "pin_digit" }
        if (!inputEnabled) {
            return
        }
        if (!inputSession.appendCharacter(digit)) {
            emitState(PinCredentialFeedback.ERROR, LIMIT_TEXT)
            return
        }
        onUserInput()
        if (inputSession.inputLength == autoConfirmLength) {
            submitCredential()
        } else {
            emitState(PinCredentialFeedback.READY, "")
        }
    }

    /** 删除最后一个数字并立即覆写原数组位置。 */
    override fun onDeleteRequested() {
        ensureOpen()
        if (!inputEnabled) {
            return
        }
        if (inputSession.deleteLastCharacter()) {
            onUserInput()
        }
        emitState(PinCredentialFeedback.READY, "")
    }

    /** 校验最短长度并把合格 PIN 的唯一所有权移交给系统桥。 */
    override fun onConfirmRequested() {
        ensureOpen()
        if (!inputEnabled) {
            return
        }
        onUserInput()
        if (inputSession.inputLength < MINIMUM_PIN_LENGTH) {
            inputSession.clear()
            emitState(PinCredentialFeedback.ERROR, MINIMUM_PIN_TEXT)
            return
        }
        submitCredential()
    }

    /** 禁止后续键盘输入，并把当前合格 PIN 的唯一所有权移交给系统桥。 */
    private fun submitCredential() {
        inputEnabled = false
        emitState(PinCredentialFeedback.CHECKING, CHECKING_TEXT)
        // 本次系统校验独占的可清零字符 lease。
        val lease = inputSession.submit() as? EphemeralCredentialLease.Characters
            ?: error("pin_credential_lease")
        try {
            onCredentialReady(lease)
        } catch (throwable: Throwable) {
            lease.close()
            throw throwable
        }
    }

    /** 把紧急请求直接转交已验证的 ROM 原生桥。 */
    override fun onEmergencyRequested() {
        ensureOpen()
        onEmergencyAction()
    }

    /** 把宿主捕获的异常转交运行时回退出口。 */
    override fun onInteractionFailure(throwable: Throwable) {
        onInteractionFailed(throwable)
    }

    /** 幂等清零 PIN 并永久关闭协调器。 */
    override fun close() {
        if (closed) {
            return
        }
        closed = true
        inputSession.close()
    }

    /** 构建并提交只包含输入长度和反馈的非敏感状态。 */
    private fun emitState(feedback: PinCredentialFeedback, feedbackText: String) {
        onStateChanged(
            PinCredentialUiState(
                promptText = PROMPT_TEXT,
                inputLength = inputSession.inputLength,
                feedbackText = feedbackText,
                feedback = feedback,
            ),
        )
    }

    /** 拒绝关闭后继续接收数字或异步状态。 */
    private fun ensureOpen() {
        check(!closed) { "pin_coordinator_closed" }
    }

    private companion object {
        /** 与 Android 原生 PIN 最短校验边界一致的数字数量。 */
        const val MINIMUM_PIN_LENGTH: Int = 4

        /** 与可清零字符缓冲一致的防御性 PIN 长度上限。 */
        const val MAXIMUM_PIN_LENGTH: Int = 64

        /** PIN 页面主提示。 */
        const val PROMPT_TEXT: String = "ENTER PIN"

        /** 短 PIN 使用的紧凑提示。 */
        const val MINIMUM_PIN_TEXT: String = "NEED 4+"

        /** 系统异步校验阶段提示。 */
        const val CHECKING_TEXT: String = "CHECKING"

        /** 系统普通拒绝后的提示。 */
        const val REJECTED_TEXT: String = "TRY AGAIN"

        /** 防御性缓冲上限到达后的提示。 */
        const val LIMIT_TEXT: String = "LIMIT"
    }
}
