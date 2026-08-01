package com.purride.pixellockscreen.ui

/** 像素 PIN 认证界面的非敏感反馈状态。 */
public enum class PinCredentialFeedback {
    /** 等待用户输入或确认 PIN。 */
    READY,

    /** 系统正在异步校验，暂时禁止键盘输入。 */
    CHECKING,

    /** 上一次有效 PIN 被系统拒绝，允许重新输入。 */
    ERROR,

    /** 系统已进入凭据限流期。 */
    LOCKED_OUT,
}

/**
 * PIN 页面可长期渲染的非敏感状态。
 *
 * 该状态只保存输入长度，不保存任何数字、字符序列或可还原凭据。
 */
public data class PinCredentialUiState(
    /** 主提示文字。 */
    public val promptText: String,
    /** 当前已输入数字数量。 */
    public val inputLength: Int = 0,
    /** 当前系统反馈文字。 */
    public val feedbackText: String = "",
    /** 当前系统校验阶段。 */
    public val feedback: PinCredentialFeedback = PinCredentialFeedback.READY,
    /** 纵屏紧急入口文字。 */
    public val emergencyText: String = "EMERGENCY",
    /** 横屏紧急入口文字。 */
    public val compactEmergencyText: String = "SOS",
    /** Android 无障碍节点朗读的紧急入口说明。 */
    public val emergencyAccessibilityLabel: String = emergencyText,
) {
    /** 拒绝无法显示或异常无界的非敏感状态。 */
    init {
        require(promptText.isNotBlank()) { "pin_prompt_blank" }
        require(inputLength in 0..MAXIMUM_PIN_LENGTH) { "pin_input_length" }
        require(emergencyText.isNotBlank()) { "pin_emergency_text_blank" }
        require(compactEmergencyText.isNotBlank()) { "pin_compact_emergency_text_blank" }
        require(emergencyAccessibilityLabel.isNotBlank()) { "pin_emergency_accessibility_blank" }
    }

    /** 当前是否允许数字、删除和确认操作。 */
    public val isInputEnabled: Boolean
        get() = feedback == PinCredentialFeedback.READY || feedback == PinCredentialFeedback.ERROR

    private companion object {
        /** 与模块可清零字符缓冲一致的防御性长度上限。 */
        const val MAXIMUM_PIN_LENGTH: Int = 64
    }
}

/** PIN 触摸宿主向外部安全会话发送的最小事件接口。 */
public interface PinCredentialListener {
    /** 追加一个 ASCII 数字；调用方不得保存不可清零字符串。 */
    public fun onDigitEntered(digit: Char)

    /** 请求删除最后一个尚未提交的数字。 */
    public fun onDeleteRequested()

    /** 请求把当前可清零缓冲一次性交给系统校验。 */
    public fun onConfirmRequested()

    /** 请求 SystemUI 执行现有紧急呼叫入口。 */
    public fun onEmergencyRequested()

    /** 事件接收方异常时要求模块立即恢复原生 Bouncer。 */
    public fun onInteractionFailure(throwable: Throwable)
}
