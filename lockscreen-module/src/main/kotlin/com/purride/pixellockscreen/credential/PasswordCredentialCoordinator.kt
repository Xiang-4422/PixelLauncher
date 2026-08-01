package com.purride.pixellockscreen.credential

import com.purride.pixellockscreen.ui.PasswordCredentialFeedback
import com.purride.pixellockscreen.ui.PasswordCredentialListener
import com.purride.pixellockscreen.ui.PasswordCredentialUiState

/**
 * 把原生密码输入连接的非敏感状态转换为像素密码界面状态。
 *
 * 协调器从不接收 `CharSequence`、`Editable` 或凭据对象，只接受经过边界校验的长度、焦点、
 * 输入法入口和系统认证阶段。所有输入、提交、失败计数与限流仍由 SystemUI 原控制器负责。
 */
internal class PasswordCredentialCoordinator(
    /** 请求原生密码输入连接获得焦点并显示系统 IME。 */
    private val onInputRequestedAction: () -> Unit,
    /** 请求原生 SystemUI 打开输入法选择器。 */
    private val onImeSwitcherRequestedAction: () -> Unit,
    /** 请求原生紧急按钮点击链。 */
    private val onEmergencyAction: () -> Unit,
    /** 读取 SystemUI 当前是否允许展示紧急入口。 */
    private val isEmergencyAvailable: () -> Boolean = { true },
    /** 输出最新非敏感密码界面状态。 */
    private val onStateChanged: (PasswordCredentialUiState) -> Unit,
    /** 任一公开动作异常时触发原生回退。 */
    private val onInteractionFailed: (Throwable) -> Unit,
) : PasswordCredentialListener, AutoCloseable {
    /** 原生输入框当前报告的字符数量。 */
    private var inputLength: Int = 0

    /** 原生输入框当前是否拥有焦点。 */
    private var hasInputFocus: Boolean = false

    /** 原生输入法切换入口当前是否可用。 */
    private var imeSwitcherVisible: Boolean = false

    /** 当前系统认证反馈。 */
    private var feedback: PasswordCredentialFeedback = PasswordCredentialFeedback.READY

    /** 当前系统认证反馈文字。 */
    private var feedbackText: String = ""

    /** 协调器是否已经结束。 */
    private var closed: Boolean = false

    /** 最近一次提交给像素宿主的完整非敏感状态。 */
    private var lastUiState: PasswordCredentialUiState? = null

    /** SystemUI 动态显隐紧急按钮时只提交发生变化的新状态。 */
    fun refreshEmergencyAvailability() {
        checkOpen()
        /** 尚未完成初始状态提交时无需单独刷新。 */
        val previous = lastUiState ?: return
        /** 当前原生紧急入口可用性。 */
        val available = isEmergencyAvailable()
        if (previous.isEmergencyAvailable == available) {
            return
        }
        previous.copy(isEmergencyAvailable = available).also { next ->
            lastUiState = next
            onStateChanged(next)
        }
    }

    /** 使用原生输入连接的当前状态初始化像素密码页面。 */
    fun showInitial(length: Int, focused: Boolean, imeVisible: Boolean) {
        checkOpen()
        inputLength = validatedNativePasswordLength(length)
        hasInputFocus = focused
        imeSwitcherVisible = imeVisible
        feedback = PasswordCredentialFeedback.READY
        feedbackText = ""
        emitState()
    }

    /** 只同步原生 `Editable.length`；错误后的首次继续输入同时清除旧反馈。 */
    fun onNativeInputLengthChanged(length: Int) {
        checkOpen()
        inputLength = validatedNativePasswordLength(length)
        if (feedback == PasswordCredentialFeedback.ERROR && inputLength > 0) {
            feedback = PasswordCredentialFeedback.READY
            feedbackText = ""
        }
        emitState()
    }

    /** 同步原生输入连接焦点变化。 */
    fun onNativeFocusChanged(focused: Boolean) {
        checkOpen()
        if (hasInputFocus == focused) {
            return
        }
        hasInputFocus = focused
        emitState()
    }

    /** 同步 SystemUI 原生输入法切换按钮的可见状态。 */
    fun onImeSwitcherVisibilityChanged(visible: Boolean) {
        checkOpen()
        if (imeSwitcherVisible == visible) {
            return
        }
        imeSwitcherVisible = visible
        emitState()
    }

    /** 原生控制器开始校验时禁止像素输入动作并显示等待反馈。 */
    fun showChecking() {
        checkOpen()
        feedback = PasswordCredentialFeedback.CHECKING
        feedbackText = CHECKING_TEXT
        emitState()
    }

    /** 原生控制器拒绝密码时显示错误，但继续保留系统输入连接。 */
    fun showRejected() {
        checkOpen()
        feedback = PasswordCredentialFeedback.ERROR
        feedbackText = REJECTED_TEXT
        emitState()
    }

    /** 按系统截止时间换算出的剩余秒数显示限流反馈。 */
    fun showLockedOut(remainingSeconds: Int) {
        checkOpen()
        require(remainingSeconds > 0) { "password_lockout_seconds" }
        feedback = PasswordCredentialFeedback.LOCKED_OUT
        feedbackText = "WAIT ${remainingSeconds}S"
        emitState()
    }

    /** 系统限流结束或原生页面重置后恢复可输入状态。 */
    fun showReady() {
        checkOpen()
        feedback = PasswordCredentialFeedback.READY
        feedbackText = ""
        emitState()
    }

    /** 像素输入区域只请求原生输入连接，不接收密码字符。 */
    override fun onInputRequested() = invokeSafely(onInputRequestedAction)

    /** 像素入口只复用原生输入法选择动作。 */
    override fun onImeSwitcherRequested() = invokeSafely(onImeSwitcherRequestedAction)

    /** 像素紧急入口只复用原生紧急按钮点击链。 */
    override fun onEmergencyRequested() = invokeSafely(onEmergencyAction)

    /** UI 宿主主动报告交互失败时立即要求上层回退。 */
    override fun onInteractionFailure(throwable: Throwable) {
        if (!closed) {
            onInteractionFailed(throwable)
        }
    }

    /** 幂等结束协调器；不保存任何可清理密码内容。 */
    override fun close() {
        closed = true
    }

    /** 输出不包含任何密码字符的完整界面状态。 */
    private fun emitState() {
        PasswordCredentialUiState(
            promptText = PROMPT_TEXT,
            inputLength = inputLength,
            feedbackText = feedbackText,
            feedback = feedback,
            hasInputFocus = hasInputFocus,
            isImeSwitcherVisible = imeSwitcherVisible,
            isEmergencyAvailable = isEmergencyAvailable(),
        ).also { state ->
            lastUiState = state
            onStateChanged(state)
        }
    }

    /** 在协调器有效期内执行公开动作，并把异常统一交给回退链。 */
    private fun invokeSafely(action: () -> Unit) {
        if (closed) {
            return
        }
        runCatching(action).onFailure(onInteractionFailed)
    }

    /** 拒绝生命周期结束后的状态更新。 */
    private fun checkOpen() {
        check(!closed) { "password_coordinator_closed" }
    }

    private companion object {
        /** 密码页面固定主提示。 */
        const val PROMPT_TEXT: String = "ENTER PASSWORD"

        /** 原生异步校验期间的固定反馈。 */
        const val CHECKING_TEXT: String = "CHECKING"

        /** 原生无锁定失败后的固定反馈。 */
        const val REJECTED_TEXT: String = "TRY AGAIN"
    }
}
