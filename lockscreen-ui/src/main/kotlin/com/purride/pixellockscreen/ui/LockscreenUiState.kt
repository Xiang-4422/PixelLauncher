package com.purride.pixellockscreen.ui

/** 与系统数据源解耦的一帧锁屏展示状态。 */
public data class LockscreenUiState(
    /** 已按用户制式格式化的时间文本。 */
    public val timeText: String,
    /** 已按用户语言和日期偏好格式化的日期文本。 */
    public val dateText: String,
    /** 当前电量百分比，取值范围固定为 0 到 100。 */
    public val batteryPercent: Int,
    /** 当前设备是否处于充电状态。 */
    public val isCharging: Boolean,
    /** 底部交给系统解锁手势解释的只读提示文字。 */
    public val unlockHint: String,
    /** 完全由 Android 安全后端驱动的非敏感生物识别展示状态。 */
    public val biometric: LockscreenBiometricUiState = LockscreenBiometricUiState(),
    /** 完全由 Android 信任系统驱动的非敏感安全提示。 */
    public val securityNotice: LockscreenSecurityNoticeUiState = LockscreenSecurityNoticeUiState(),
    /** SystemUI 已执行隐私裁剪后允许公开展示的通知摘要。 */
    public val notifications: List<LockscreenNotificationUiState> = emptyList(),
    /** SystemUI 当前选中的锁屏媒体摘要。 */
    public val media: LockscreenMediaUiState = LockscreenMediaUiState(),
) {
    /** 在状态进入渲染树之前拒绝不可展示或越界的数据。 */
    init {
        require(timeText.isNotBlank()) { "timeText 不能为空" }
        require(dateText.isNotBlank()) { "dateText 不能为空" }
        require(batteryPercent in 0..100) { "batteryPercent 必须位于 0..100" }
        require(unlockHint.isNotBlank()) { "unlockHint 不能为空" }
        require(notifications.size <= MAXIMUM_VISIBLE_NOTIFICATION_COUNT) {
            "lockscreen_notification_count"
        }
        require(notifications.map { notification -> notification.key }.distinct().size == notifications.size) {
            "lockscreen_notification_key_duplicate"
        }
    }

    private companion object {
        /** 首版像素锁屏为避免裁切允许接收的最大通知摘要数量。 */
        const val MAXIMUM_VISIBLE_NOTIFICATION_COUNT: Int = 3
    }
}
