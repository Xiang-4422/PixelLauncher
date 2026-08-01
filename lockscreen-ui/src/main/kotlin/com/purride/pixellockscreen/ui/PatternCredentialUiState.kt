package com.purride.pixellockscreen.ui

/** 像素图案认证界面的非敏感反馈状态。 */
public enum class PatternCredentialFeedback {
    /** 等待用户绘制图案。 */
    READY,

    /** 系统正在异步校验，暂时禁止新输入。 */
    CHECKING,

    /** 上一次有效图案被系统拒绝，允许立即重新输入。 */
    ERROR,

    /** 系统已进入凭据限流期。 */
    LOCKED_OUT,
}

/**
 * 图案认证界面可长期渲染的非敏感状态。
 *
 * 图案格子和路径不属于该状态，不能进入 Bundle、SavedState、日志或跨进程存储。
 */
public data class PatternCredentialUiState(
    /** 主提示文字，例如 `DRAW PATTERN`。 */
    public val promptText: String,
    /** 当前反馈文字；无反馈时允许为空。 */
    public val feedbackText: String = "",
    /** 当前系统校验或限流阶段。 */
    public val feedback: PatternCredentialFeedback = PatternCredentialFeedback.READY,
    /** SystemUI 当前是否允许展示并执行原生紧急操作。 */
    public val isEmergencyAvailable: Boolean = true,
    /** 纵屏像素按钮显示的紧急入口文字。 */
    public val emergencyText: String = "EMERGENCY",
    /** 横屏紧凑像素按钮显示的紧急入口文字。 */
    public val compactEmergencyText: String = "SOS",
    /** Android 无障碍节点朗读的完整紧急入口说明。 */
    public val emergencyAccessibilityLabel: String = emergencyText,
) {
    /** 拒绝无法展示的主提示。 */
    init {
        require(promptText.isNotBlank()) { "pattern_prompt_blank" }
        require(emergencyText.isNotBlank()) { "pattern_emergency_text_blank" }
        require(compactEmergencyText.isNotBlank()) { "pattern_compact_emergency_text_blank" }
        require(emergencyAccessibilityLabel.isNotBlank()) { "pattern_emergency_accessibility_blank" }
    }

    /** 当前是否允许开始一条新图案路径。 */
    public val isInputEnabled: Boolean
        get() = feedback == PatternCredentialFeedback.READY ||
            feedback == PatternCredentialFeedback.ERROR
}

/** 图案触摸宿主向 SystemUI 认证会话发送的最小事件接口。 */
public interface PatternCredentialListener {
    /** 第一枚格子命中时通知原生用户活动链路。 */
    public fun onPatternStarted()

    /** 按经过顺序提交单个 0–8 格子编号。 */
    public fun onPatternCellAdded(cellId: Int)

    /** 手指抬起后只提交路径长度，原始顺序已逐格进入安全会话。 */
    public fun onPatternCompleted(cellCount: Int)

    /** 手势取消时要求认证会话清零当前路径。 */
    public fun onPatternCancelled()

    /** 请求 SystemUI 执行现有紧急呼叫入口。 */
    public fun onEmergencyRequested()

    /** 事件接收方异常时要求模块立即恢复原生 Bouncer。 */
    public fun onInteractionFailure(throwable: Throwable)
}
