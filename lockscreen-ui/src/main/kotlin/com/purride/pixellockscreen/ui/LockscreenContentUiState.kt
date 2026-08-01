package com.purride.pixellockscreen.ui

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
