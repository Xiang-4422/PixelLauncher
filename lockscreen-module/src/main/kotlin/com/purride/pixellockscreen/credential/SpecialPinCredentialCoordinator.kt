package com.purride.pixellockscreen.credential

import com.purride.pixellockscreen.ui.PinCredentialListener
import com.purride.pixellockscreen.ui.PinCredentialUiState

/**
 * 像素数字键盘与 Titan 2 原生 SIM/AntiTheft 页面之间的无凭据协调器。
 *
 * 协调器不建立输入缓冲；每个动作立即转发给原生控件，再只读取掩码长度和系统消息。
 */
internal class SpecialPinCredentialCoordinator(
    /** 当前特殊页的固定原生模式。 */
    private val mode: Titan2SpecialPinMode,
    /** 唯一允许接触原生页面的最小动作边界。 */
    private val actions: Titan2SpecialPinActions,
    /** 请求 ROM 原生紧急入口的动作。 */
    private val onEmergencyAction: () -> Unit,
    /** 读取 SystemUI 当前是否允许展示紧急入口。 */
    private val isEmergencyAvailable: () -> Boolean = { true },
    /** 接收不含凭据内容的像素状态。 */
    private val onStateChanged: (PinCredentialUiState) -> Unit,
    /** 接收交互异常并要求恢复原生页面的出口。 */
    private val onInteractionFailed: (Throwable) -> Unit,
) : PinCredentialListener, AutoCloseable {
    /** 协调器是否已经永久关闭。 */
    private var closed: Boolean = false

    /** 从原生页面读取并提交当前脱敏状态。 */
    fun refresh() {
        ensureOpen()
        onStateChanged(
            specialPinUiState(mode, actions.snapshot()).copy(
                isEmergencyAvailable = isEmergencyAvailable(),
            ),
        )
    }

    /** 立即把一个数字交给原生按键并刷新掩码长度。 */
    override fun onDigitEntered(digit: Char) {
        ensureOpen()
        require(digit in '0'..'9') { "special_pin_coordinator_digit" }
        actions.enterDigit(digit)
        refresh()
    }

    /** 立即请求原生删除并刷新掩码长度。 */
    override fun onDeleteRequested() {
        ensureOpen()
        actions.delete()
        refresh()
    }

    /** 立即请求原生控制器确认；校验进度和后续阶段仍由原生状态机决定。 */
    override fun onConfirmRequested() {
        ensureOpen()
        actions.confirm()
        refresh()
    }

    /** 复用 ROM 原生紧急入口。 */
    override fun onEmergencyRequested() {
        ensureOpen()
        onEmergencyAction()
    }

    /** 把像素宿主捕获的异常交给会话回退出口。 */
    override fun onInteractionFailure(throwable: Throwable) {
        onInteractionFailed(throwable)
    }

    /** 幂等停止后续动作；凭据由原生页面持有，因此没有本地缓冲需要清零。 */
    override fun close() {
        closed = true
    }

    /** 拒绝关闭后继续触发原生安全页面。 */
    private fun ensureOpen() {
        check(!closed) { "special_pin_coordinator_closed" }
    }
}
