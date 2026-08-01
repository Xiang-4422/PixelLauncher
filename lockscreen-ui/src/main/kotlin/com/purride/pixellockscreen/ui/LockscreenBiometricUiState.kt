package com.purride.pixellockscreen.ui

/** 像素锁屏可表达的系统生物识别传感器组合。 */
public enum class LockscreenBiometricModality {
    /** 当前用户没有可用于 Keyguard 的生物识别方式。 */
    NONE,

    /** 当前用户可使用系统指纹认证。 */
    FINGERPRINT,

    /** 当前用户可使用系统人脸认证。 */
    FACE,

    /** 当前用户同时具有系统指纹与人脸认证。 */
    FACE_AND_FINGERPRINT,
}

/** Android 安全后端报告给像素锁屏的生物识别阶段。 */
public enum class LockscreenBiometricPhase {
    /** 没有可展示的生物识别状态。 */
    UNAVAILABLE,

    /** 传感器可用，但当前尚未采集。 */
    READY,

    /** Android 正在监听或采集生物特征。 */
    SCANNING,

    /** Android 已认证成功，等待原生 Keyguard 完成 dismiss。 */
    SUCCESS,

    /** Android 返回可恢复的采集帮助或认证失败。 */
    ERROR,

    /** Android 已锁定当前生物识别方式。 */
    LOCKED_OUT,

    /** StrongAuth 要求先使用图案、PIN 或密码。 */
    STRONG_AUTH_REQUIRED,
}

/**
 * 与生物识别采集和安全决策完全解耦的一帧可见状态。
 *
 * 状态只包含传感器种类、系统阶段和可展示消息，不包含模板、图像、特征、令牌或失败次数。
 */
public data class LockscreenBiometricUiState(
    /** 当前用户可用于 Keyguard 的传感器组合。 */
    public val modality: LockscreenBiometricModality = LockscreenBiometricModality.NONE,
    /** Android 安全后端当前报告的认证阶段。 */
    public val phase: LockscreenBiometricPhase = LockscreenBiometricPhase.UNAVAILABLE,
    /** 已由 SystemUI 或适配器格式化的非敏感短消息。 */
    public val messageText: String = "",
) {
    /** 拒绝互相矛盾或无法安全排版的展示状态。 */
    init {
        require(messageText.length <= MAXIMUM_BIOMETRIC_MESSAGE_LENGTH) {
            "biometric_message_too_long"
        }
        require('\n' !in messageText && '\r' !in messageText) {
            "biometric_message_multiline"
        }
        if (phase == LockscreenBiometricPhase.UNAVAILABLE) {
            require(messageText.isBlank()) { "biometric_unavailable_message" }
        }
        require(
            modality != LockscreenBiometricModality.NONE ||
                phase == LockscreenBiometricPhase.UNAVAILABLE ||
                phase == LockscreenBiometricPhase.STRONG_AUTH_REQUIRED,
        ) { "biometric_modality_missing" }
    }

    /** 当前状态是否应占用普通锁屏中的像素安全提示区域。 */
    public val isVisible: Boolean
        get() = phase != LockscreenBiometricPhase.UNAVAILABLE

    private companion object {
        /** 单行像素提示允许接收的最大系统消息长度。 */
        const val MAXIMUM_BIOMETRIC_MESSAGE_LENGTH: Int = 160
    }
}
