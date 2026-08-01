package com.purride.pixellockscreen.ui

/** 像素密码认证界面的非敏感反馈状态。 */
public enum class PasswordCredentialFeedback {
    /** 等待系统 IME 输入或提交密码。 */
    READY,

    /** Android 正在校验密码，暂时禁止继续编辑。 */
    CHECKING,

    /** 上一次密码被系统拒绝，允许重新输入。 */
    ERROR,

    /** Android 已进入设备凭据限流期。 */
    LOCKED_OUT,
}

/**
 * 密码页面可长期渲染的非敏感状态。
 *
 * 状态只保存原生 `EditText` 报告的字符数量，不接收、复制或持久化任何密码字符。
 */
public data class PasswordCredentialUiState(
    /** 主提示文字。 */
    public val promptText: String,
    /** 原生密码输入连接当前报告的字符数量。 */
    public val inputLength: Int = 0,
    /** 当前系统反馈文字。 */
    public val feedbackText: String = "",
    /** 当前系统校验阶段。 */
    public val feedback: PasswordCredentialFeedback = PasswordCredentialFeedback.READY,
    /** 原生密码输入连接当前是否拥有焦点。 */
    public val hasInputFocus: Boolean = false,
    /** 密码为空时显示在像素输入框中的操作提示。 */
    public val inputHintText: String = "TAP TO TYPE",
    /** 当前用户是否具有多个可切换的系统输入法或有效子类型。 */
    public val isImeSwitcherVisible: Boolean = false,
    /** SystemUI 当前是否允许展示并执行原生紧急操作。 */
    public val isEmergencyAvailable: Boolean = true,
    /** 方屏输入法切换入口文字。 */
    public val imeSwitcherText: String = "KEYBOARD",
    /** 方屏紧急入口文字。 */
    public val emergencyText: String = "EMERGENCY",
    /** Android 无障碍节点朗读的密码输入说明。 */
    public val inputAccessibilityLabel: String = "PASSWORD INPUT",
    /** Android 无障碍节点朗读的输入法切换说明。 */
    public val imeSwitcherAccessibilityLabel: String = imeSwitcherText,
    /** Android 无障碍节点朗读的紧急入口说明。 */
    public val emergencyAccessibilityLabel: String = emergencyText,
) {
    /** 拒绝无法显示、异常无界或缺少无障碍语义的非敏感状态。 */
    init {
        require(promptText.isNotBlank()) { "password_prompt_blank" }
        require(inputLength in 0..MAXIMUM_PASSWORD_LENGTH) { "password_input_length" }
        require(inputHintText.isNotBlank()) { "password_input_hint_blank" }
        require(imeSwitcherText.isNotBlank()) { "password_ime_switcher_text_blank" }
        require(emergencyText.isNotBlank()) { "password_emergency_text_blank" }
        require(inputAccessibilityLabel.isNotBlank()) { "password_input_accessibility_blank" }
        require(imeSwitcherAccessibilityLabel.isNotBlank()) {
            "password_ime_switcher_accessibility_blank"
        }
        require(emergencyAccessibilityLabel.isNotBlank()) {
            "password_emergency_accessibility_blank"
        }
    }

    /** 当前是否允许请求焦点并继续由系统 IME 修改原生输入连接。 */
    public val isInputEnabled: Boolean
        get() = feedback == PasswordCredentialFeedback.READY ||
            feedback == PasswordCredentialFeedback.ERROR

    private companion object {
        /** 与 Titan 2 原生密码输入框 `maxLength` 一致的非敏感长度上限。 */
        const val MAXIMUM_PASSWORD_LENGTH: Int = 500
    }
}

/** 密码像素宿主向运行时输入连接桥发送的非凭据动作。 */
public interface PasswordCredentialListener {
    /** 请求原生密码 `EditText` 获取焦点并显示系统 IME。 */
    public fun onInputRequested()

    /** 请求原生控制器打开 Android 输入法选择器。 */
    public fun onImeSwitcherRequested()

    /** 请求 SystemUI 执行现有紧急呼叫入口。 */
    public fun onEmergencyRequested()

    /** 事件接收方异常时要求模块立即恢复原生密码页面。 */
    public fun onInteractionFailure(throwable: Throwable)
}
