package com.purride.pixellockscreen.ui

/** Android 信任代理与 Extend Unlock 可见提示的安全阶段。 */
public enum class LockscreenSecurityNoticePhase {
    /** 当前没有独立于生物识别的安全提示。 */
    NONE,

    /** Android 信任代理已授予当前用户信任。 */
    TRUSTED,

    /** Android 信任代理返回需要用户处理的错误。 */
    TRUST_ERROR,

    /** Android 正在显示 Extend Unlock 持续解锁说明。 */
    EXTENDED_UNLOCK,
}

/**
 * Android 信任系统输出给像素锁屏的一帧非敏感可见提示。
 *
 * 状态不表示模块自行授予的权限，也不包含信任令牌；文字只能来自 SystemUI 已决定展示的内容。
 */
public data class LockscreenSecurityNoticeUiState(
    /** 当前原生信任或持续解锁阶段。 */
    public val phase: LockscreenSecurityNoticePhase = LockscreenSecurityNoticePhase.NONE,
    /** 已清理为单行的原生提示。 */
    public val messageText: String = "",
) {
    /** 拒绝缺少文字、无状态带文字、超长或多行的提示。 */
    init {
        require(messageText.length <= MAXIMUM_SECURITY_NOTICE_LENGTH) {
            "security_notice_too_long"
        }
        require('\n' !in messageText && '\r' !in messageText) {
            "security_notice_multiline"
        }
        require(
            (phase == LockscreenSecurityNoticePhase.NONE && messageText.isBlank()) ||
                (phase != LockscreenSecurityNoticePhase.NONE && messageText.isNotBlank()),
        ) { "security_notice_phase_message" }
    }

    /** 当前提示是否应占用普通像素锁屏的安全状态区域。 */
    public val isVisible: Boolean
        get() = phase != LockscreenSecurityNoticePhase.NONE

    private companion object {
        /** 单行像素安全提示允许接收的最大系统消息长度。 */
        const val MAXIMUM_SECURITY_NOTICE_LENGTH: Int = 160
    }
}
