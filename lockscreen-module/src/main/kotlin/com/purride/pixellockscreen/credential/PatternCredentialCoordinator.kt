package com.purride.pixellockscreen.credential

import com.purride.pixellockscreen.ui.PatternCredentialFeedback
import com.purride.pixellockscreen.ui.PatternCredentialListener
import com.purride.pixellockscreen.ui.PatternCredentialUiState

/**
 * 像素图案触摸宿主与单次系统校验任务之间的安全协调器。
 *
 * 协调器只持有可主动清零的 [CredentialInputSession]，不会把路径复制到 UI 状态、日志或
 * 不可清零字符串。系统校验结果与失败次数仍由外部 Android Keyguard 桥处理。
 */
internal class PatternCredentialCoordinator(
    /** 通知原生 Keyguard 用户已经开始输入的动作。 */
    private val onUserInput: () -> Unit,
    /** 接收一次独占图案 lease 并立即交给系统校验桥的动作。 */
    private val onCredentialReady: (EphemeralCredentialLease.Pattern) -> Unit,
    /** 请求 ROM 原生紧急操作的动作。 */
    private val onEmergencyAction: () -> Unit,
    /** 接收不含路径的像素认证渲染状态。 */
    private val onStateChanged: (PatternCredentialUiState) -> Unit,
    /** 接收触摸链路异常并要求恢复原生页面的动作。 */
    private val onInteractionFailed: (Throwable) -> Unit,
) : PatternCredentialListener, AutoCloseable {
    /** 当前会话唯一的可清零图案输入缓冲。 */
    private val inputSession: CredentialInputSession = CredentialInputSession(PixelCredentialMode.PATTERN)

    /** 协调器是否已经完成清零并永久关闭。 */
    private var closed: Boolean = false

    /** 显示可输入的初始状态。 */
    fun showReady() {
        ensureOpen()
        inputSession.clear()
        emitState(feedback = PatternCredentialFeedback.READY, feedbackText = "")
    }

    /** 显示 Android 已拒绝上一次有效图案后的可重试状态。 */
    fun showRejected() {
        ensureOpen()
        inputSession.clear()
        emitState(feedback = PatternCredentialFeedback.ERROR, feedbackText = REJECTED_TEXT)
    }

    /** 显示系统截止时间尚未结束的剩余秒数。 */
    fun showLockedOut(remainingSeconds: Int) {
        ensureOpen()
        require(remainingSeconds > 0) { "pattern_lockout_seconds" }
        inputSession.clear()
        emitState(
            feedback = PatternCredentialFeedback.LOCKED_OUT,
            feedbackText = "WAIT ${remainingSeconds}S",
        )
    }

    /** 第一枚格子命中时清除旧反馈并复用原生用户活动语义。 */
    override fun onPatternStarted() {
        ensureOpen()
        inputSession.clear()
        onUserInput()
        emitState(feedback = PatternCredentialFeedback.READY, feedbackText = "")
    }

    /** 按安全路径顺序追加一个唯一格子。 */
    override fun onPatternCellAdded(cellId: Int) {
        ensureOpen()
        check(inputSession.appendPatternCell(cellId)) { "pattern_cell_duplicate" }
    }

    /** 校验路径长度并把合格图案的唯一所有权移交给系统桥。 */
    override fun onPatternCompleted(cellCount: Int) {
        ensureOpen()
        check(cellCount == inputSession.inputLength) { "pattern_cell_count_mismatch" }
        if (cellCount < MINIMUM_PATTERN_LENGTH) {
            inputSession.clear()
            emitState(
                feedback = PatternCredentialFeedback.ERROR,
                feedbackText = MINIMUM_PATTERN_TEXT,
            )
            return
        }
        emitState(feedback = PatternCredentialFeedback.CHECKING, feedbackText = CHECKING_TEXT)
        /** 本次系统校验独占、且只能由外部同步接收一次的图案 lease。 */
        val lease = inputSession.submit() as? EphemeralCredentialLease.Pattern
            ?: error("pattern_credential_lease")
        try {
            onCredentialReady(lease)
        } catch (throwable: Throwable) {
            lease.close()
            throw throwable
        }
    }

    /** 手势取消时立即覆写尚未提交的路径。 */
    override fun onPatternCancelled() {
        ensureOpen()
        inputSession.clear()
    }

    /** 把像素入口请求原样转交给已验证的 ROM 紧急操作桥。 */
    override fun onEmergencyRequested() {
        ensureOpen()
        onEmergencyAction()
    }

    /** 把宿主捕获的交互异常转交给运行时回退出口。 */
    override fun onInteractionFailure(throwable: Throwable) {
        onInteractionFailed(throwable)
    }

    /** 幂等清零尚未提交的路径并永久关闭协调器。 */
    override fun close() {
        if (closed) {
            return
        }
        closed = true
        inputSession.close()
    }

    /** 构建并提交只包含非敏感提示的认证状态。 */
    private fun emitState(feedback: PatternCredentialFeedback, feedbackText: String) {
        onStateChanged(
            PatternCredentialUiState(
                promptText = PROMPT_TEXT,
                feedbackText = feedbackText,
                feedback = feedback,
            ),
        )
    }

    /** 拒绝关闭后继续接收触摸或异步状态。 */
    private fun ensureOpen() {
        check(!closed) { "pattern_coordinator_closed" }
    }

    private companion object {
        /** Android 图案锁允许交给系统校验的最短路径。 */
        const val MINIMUM_PATTERN_LENGTH: Int = 4

        /** 主提示使用的稳定像素文字。 */
        const val PROMPT_TEXT: String = "DRAW PATTERN"

        /** 短图案使用的紧凑提示。 */
        const val MINIMUM_PATTERN_TEXT: String = "USE 4+ DOTS"

        /** 系统异步校验阶段的提示。 */
        const val CHECKING_TEXT: String = "CHECKING"

        /** 系统普通拒绝后的紧凑提示。 */
        const val REJECTED_TEXT: String = "TRY AGAIN"
    }
}
