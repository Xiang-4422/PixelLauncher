package com.purride.pixellockscreen.ui

/** 普通锁屏内容卡向 SystemUI 原生操作链转发的最小事件接口。 */
public interface LockscreenContentListener {
    /** 请求 SystemUI 处理指定脱敏键对应的原生通知点击。 */
    public fun onNotificationRequested(notificationKey: String)

    /** 请求 SystemUI 执行当前媒体会话已有的播放或暂停按钮。 */
    public fun onMediaPlayPauseRequested()

    /** 请求 SystemUI 执行当前槽位已经配置的锁屏快捷操作。 */
    public fun onQuickActionRequested(actionKey: String)

    /** 事件接收方异常时要求模块立即恢复原生锁屏。 */
    public fun onInteractionFailure(throwable: Throwable)
}

/** SystemUI 当前锁屏快捷槽位允许公开展示的一项操作。 */
public data class LockscreenQuickActionUiState(
    /** 当前会话内稳定的 START 或 END 槽位标识。 */
    public val key: String,
    /** SystemUI 当前 View 提供的无障碍操作名称。 */
    public val labelText: String,
) {
    /** 拒绝未知槽位、多行或异常无界的操作名称。 */
    init {
        require(key == START_KEY || key == END_KEY) { "lockscreen_quick_action_key" }
        require(labelText.isNotBlank() && labelText.length <= MAXIMUM_LABEL_LENGTH) {
            "lockscreen_quick_action_label"
        }
        require('\n' !in labelText && '\r' !in labelText) {
            "lockscreen_quick_action_label_multiline"
        }
    }

    public companion object {
        /** 左侧快捷槽位稳定标识。 */
        public const val START_KEY: String = "START"

        /** 右侧快捷槽位稳定标识。 */
        public const val END_KEY: String = "END"

        /** 单行快捷操作名称的防御性长度上限。 */
        private const val MAXIMUM_LABEL_LENGTH: Int = 80
    }
}

/** 锁屏允许公开展示的一条通知摘要。 */
public data class LockscreenNotificationUiState(
    /** 当前会话内稳定但不包含通知正文的条目标识。 */
    public val key: String,
    /** SystemUI 已允许展示的应用名称。 */
    public val appText: String,
    /** SystemUI 已允许展示的标题；敏感通知可以为空。 */
    public val titleText: String = "",
    /** 当前条目是否使用系统隐私替代内容。 */
    public val isRedacted: Boolean = false,
) {
    /** 拒绝无标识、无应用名、多行或异常无界的通知摘要。 */
    init {
        require(key.isNotBlank() && key.length <= MAXIMUM_NOTIFICATION_KEY_LENGTH) {
            "lockscreen_notification_key"
        }
        require(appText.isNotBlank()) { "lockscreen_notification_app" }
        require(appText.length <= MAXIMUM_NOTIFICATION_TEXT_LENGTH) {
            "lockscreen_notification_app_length"
        }
        require(titleText.length <= MAXIMUM_NOTIFICATION_TEXT_LENGTH) {
            "lockscreen_notification_title_length"
        }
        require('\n' !in appText && '\r' !in appText) { "lockscreen_notification_app_multiline" }
        require('\n' !in titleText && '\r' !in titleText) {
            "lockscreen_notification_title_multiline"
        }
    }

    private companion object {
        /** 会话内通知标识的防御性长度上限。 */
        const val MAXIMUM_NOTIFICATION_KEY_LENGTH: Int = 200

        /** 单行像素通知文字的防御性长度上限。 */
        const val MAXIMUM_NOTIFICATION_TEXT_LENGTH: Int = 120
    }
}

/** Android 媒体会话允许在锁屏公开展示的一帧摘要。 */
public data class LockscreenMediaUiState(
    /** 当前是否存在由 SystemUI 选中的锁屏媒体会话。 */
    public val isVisible: Boolean = false,
    /** SystemUI 已允许展示的曲目标题。 */
    public val titleText: String = "",
    /** SystemUI 已允许展示的艺术家或来源。 */
    public val artistText: String = "",
    /** 当前原生媒体会话是否处于播放状态。 */
    public val isPlaying: Boolean = false,
) {
    /** 可见媒体必须具有标题，所有文字必须是有界单行。 */
    init {
        require(!isVisible || titleText.isNotBlank()) { "lockscreen_media_title" }
        require(titleText.length <= MAXIMUM_MEDIA_TEXT_LENGTH) { "lockscreen_media_title_length" }
        require(artistText.length <= MAXIMUM_MEDIA_TEXT_LENGTH) {
            "lockscreen_media_artist_length"
        }
        require('\n' !in titleText && '\r' !in titleText) { "lockscreen_media_title_multiline" }
        require('\n' !in artistText && '\r' !in artistText) {
            "lockscreen_media_artist_multiline"
        }
    }

    private companion object {
        /** 单行像素媒体文字的防御性长度上限。 */
        const val MAXIMUM_MEDIA_TEXT_LENGTH: Int = 120
    }
}
