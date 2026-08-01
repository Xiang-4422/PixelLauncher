package com.purride.pixellockscreen

import android.annotation.SuppressLint
import android.app.Notification
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import com.purride.pixellockscreen.ui.LockscreenMediaUiState
import com.purride.pixellockscreen.ui.LockscreenNotificationUiState
import java.lang.reflect.Field
import java.util.LinkedHashMap

/** Titan 2 通知栈和媒体轮播的一次只读、隐私受控快照。 */
internal data class Titan2LockscreenContentSnapshot(
    /** SystemUI 当前允许在锁屏展示的前三条通知摘要。 */
    val notifications: List<LockscreenNotificationUiState> = emptyList(),
    /** SystemUI 当前可见媒体播放器的摘要。 */
    val media: LockscreenMediaUiState = LockscreenMediaUiState(),
)

/**
 * 只读镜像 Titan 2 已完成隐私决策的通知行和当前可见媒体播放器。
 *
 * 通知正文只在原生行 `mShowingPublic=false` 时读取；隐私替代行只输出应用名和固定占位。
 * 适配器不注册通知监听器、不读取 RemoteInput，也不启动或控制媒体会话。
 */
internal class Titan2LockscreenContentAdapter private constructor(
    /** 原生通知栈视图。 */
    private val notificationStack: ViewGroup,
    /** Titan 2 最终通知行类。 */
    private val notificationRowClass: Class<*>,
    /** 通知行持有的条目字段。 */
    private val entryField: Field,
    /** 通知行已解析应用名称字段。 */
    private val appNameField: Field,
    /** 通知行当前是否展示隐私替代布局。 */
    private val showingPublicField: Field,
    /** 通知行当前是否处于 Keyguard。 */
    private val onKeyguardField: Field,
    /** 通知条目的稳定原生键字段。 */
    private val notificationKeyField: Field,
    /** 通知条目的 StatusBarNotification 字段。 */
    private val statusBarNotificationField: Field,
    /** MediaPlayerData 当前可见播放器静态表。 */
    private val visibleMediaPlayersField: Field,
    /** MediaControlPanel 当前绑定的 MediaData 字段。 */
    private val panelMediaDataField: Field,
    /** MediaControlPanel 当前绑定的原生媒体视图持有者字段。 */
    private val panelMediaViewHolderField: Field,
    /** 媒体视图持有者中的原生播放暂停按钮字段。 */
    private val mediaPlayPauseField: Field,
    /** MediaData 曲目字段。 */
    private val mediaSongField: Field,
    /** MediaData 艺术家字段。 */
    private val mediaArtistField: Field,
    /** MediaData 播放状态字段。 */
    private val mediaPlayingField: Field,
) {
    /** 最近一次完整快照中脱敏通知键到原生通知行的映射。 */
    private var notificationRowsBySafeKey: Map<String, View> = emptyMap()

    /** 最近一次完整快照选中的原生媒体控制面板。 */
    private var currentMediaPanel: Any? = null

    /** 读取一帧通知和媒体摘要；任何合同变化由上层立即触发原生回退。 */
    fun snapshot(): Titan2LockscreenContentSnapshot = Titan2LockscreenContentSnapshot(
        notifications = notificationSnapshot(),
        media = mediaSnapshot(),
    )

    /** 通过 SystemUI 当前通知行已有的点击监听器处理像素通知卡请求。 */
    fun performNotificationClick(notificationKey: String) {
        /** 最近一帧与脱敏键完全匹配的原生通知行。 */
        val row = notificationRowsBySafeKey[notificationKey]
            ?: error("lockscreen_notification_action_missing")
        check(notificationRowClass.isInstance(row)) { "lockscreen_notification_action_type" }
        check(row.visibility == View.VISIBLE && onKeyguardField.getBoolean(row)) {
            "lockscreen_notification_action_stale"
        }
        check(row.isEnabled && row.hasOnClickListeners()) {
            "lockscreen_notification_action_unavailable"
        }
        check(row.performClick()) { "lockscreen_notification_action_rejected" }
    }

    /** 通过当前 MediaViewHolder 已有的播放暂停按钮处理像素媒体卡请求。 */
    fun performMediaPlayPause() {
        /** 最近一帧选中的原生媒体控制面板。 */
        val panel = currentMediaPanel ?: error("lockscreen_media_action_missing")
        /** 当前控制面板绑定的原生媒体视图持有者。 */
        val holder = panelMediaViewHolderField.get(panel)
            ?: error("lockscreen_media_holder_missing")
        /** SystemUI 已配置权限、误触和日志策略的原生播放暂停按钮。 */
        val button = mediaPlayPauseField.get(holder) as? ImageButton
            ?: error("lockscreen_media_action_type")
        check(button.visibility == View.VISIBLE && button.isEnabled && button.hasOnClickListeners()) {
            "lockscreen_media_action_unavailable"
        }
        check(button.performClick()) { "lockscreen_media_action_rejected" }
    }

    /** 按原生栈顺序读取最多三条当前 Keyguard 通知。 */
    private fun notificationSnapshot(): List<LockscreenNotificationUiState> {
        /** 已加入结果的脱敏键，防止异常重复行破坏 UI 状态合同。 */
        val seenKeys = mutableSetOf<String>()
        /** 当前按原生排序收集的通知摘要。 */
        val result = mutableListOf<LockscreenNotificationUiState>()
        /** 与本次结果同帧提交的原生通知行映射。 */
        val rowsBySafeKey = linkedMapOf<String, View>()
        for (index in 0 until notificationStack.childCount) {
            if (result.size >= MAXIMUM_NOTIFICATION_COUNT) {
                break
            }
            /** 当前栈直属子视图。 */
            val row = notificationStack.getChildAt(index)
            if (
                !notificationRowClass.isInstance(row) ||
                row.visibility != View.VISIBLE ||
                row.alpha <= MINIMUM_VISIBLE_ALPHA ||
                !onKeyguardField.getBoolean(row)
            ) {
                continue
            }
            /** 当前通知行持有的 SystemUI 条目。 */
            val entry = entryField.get(row) ?: continue
            /** 当前原生通知键，只用于生成会话内脱敏标识。 */
            val rawKey = notificationKeyField.get(entry) as? String ?: continue
            /** 不包含包名、用户或通知 ID 的会话内稳定标识。 */
            val safeKey = notificationSafeKey(rawKey)
            if (!seenKeys.add(safeKey)) {
                continue
            }
            /** SystemUI 已解析的应用名。 */
            val appText = sanitizeLockscreenContentText(appNameField.get(row) as? CharSequence)
            if (appText.isBlank()) {
                continue
            }
            /** 原生行是否已经切换为公共隐私布局。 */
            val redacted = showingPublicField.getBoolean(row)
            /** 只有私有布局获准显示时才读取 Android 通知标题。 */
            val titleText = if (redacted) {
                ""
            } else {
                val sbn = statusBarNotificationField.get(entry) as? StatusBarNotification
                    ?: error("lockscreen_notification_sbn")
                sanitizeLockscreenContentText(
                    sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE),
                )
            }
            result += LockscreenNotificationUiState(
                key = safeKey,
                appText = appText,
                titleText = titleText,
                isRedacted = redacted,
            )
            rowsBySafeKey[safeKey] = row
        }
        notificationRowsBySafeKey = rowsBySafeKey
        return result
    }

    /** 从 SystemUI 可见播放器表读取当前首个真实媒体会话。 */
    private fun mediaSnapshot(): LockscreenMediaUiState {
        /** MediaPlayerData 当前可见播放器映射。 */
        val players = visibleMediaPlayersField.get(null) as? LinkedHashMap<*, *>
            ?: error("lockscreen_media_players")
        currentMediaPanel = null
        players.values.forEach { panel ->
            if (panel == null) {
                return@forEach
            }
            /** 当前播放器控制面板绑定的原生 MediaData。 */
            val data = panelMediaDataField.get(panel) ?: return@forEach
            /** SystemUI 已允许展示的曲目标题。 */
            val title = sanitizeLockscreenContentText(mediaSongField.get(data) as? CharSequence)
            if (title.isBlank()) {
                return@forEach
            }
            currentMediaPanel = panel
            return LockscreenMediaUiState(
                isVisible = true,
                titleText = title,
                artistText = sanitizeLockscreenContentText(
                    mediaArtistField.get(data) as? CharSequence,
                ),
                isPlaying = mediaPlayingField.get(data) as? Boolean ?: false,
            )
        }
        return LockscreenMediaUiState()
    }

    internal companion object {
        /** 按精确资源、类和字段签名绑定 Titan 2 已启动的遮罩窗口。 */
        @SuppressLint("DiscouragedApi", "BlockedPrivateApi", "PrivateApi")
        fun bind(shadeWindow: ViewGroup): Titan2LockscreenContentAdapter {
            /** SystemUI 最终类加载器。 */
            val classLoader = shadeWindow.javaClass.classLoader
                ?: error("lockscreen_content_class_loader")
            /** 原生通知栈资源 ID。 */
            val stackId = shadeWindow.resources.getIdentifier(
                NOTIFICATION_STACK_RESOURCE,
                "id",
                LockscreenModuleContract.SYSTEM_UI_PACKAGE,
            )
            check(stackId != 0) { "lockscreen_notification_stack_resource" }
            /** 原生通知栈最终类。 */
            val stackClass = Class.forName(NOTIFICATION_STACK_CLASS, false, classLoader)
            /** 原生通知栈实例。 */
            val stack = shadeWindow.findViewById<View>(stackId)
                ?: error("lockscreen_notification_stack")
            check(stack.javaClass == stackClass && stack is ViewGroup) {
                "lockscreen_notification_stack_type"
            }
            /** 原生通知行和条目类。 */
            val rowClass = Class.forName(NOTIFICATION_ROW_CLASS, false, classLoader)
            val entryClass = Class.forName(NOTIFICATION_ENTRY_CLASS, false, classLoader)
            /** 原生媒体控制面板和数据类。 */
            val playerDataClass = Class.forName(MEDIA_PLAYER_DATA_CLASS, false, classLoader)
            val controlPanelClass = Class.forName(MEDIA_CONTROL_PANEL_CLASS, false, classLoader)
            val mediaDataClass = Class.forName(MEDIA_DATA_CLASS, false, classLoader)
            val mediaViewHolderClass = Class.forName(MEDIA_VIEW_HOLDER_CLASS, false, classLoader)
            return Titan2LockscreenContentAdapter(
                notificationStack = stack,
                notificationRowClass = rowClass,
                entryField = exactField(rowClass, ENTRY_FIELD, entryClass),
                appNameField = exactField(rowClass, APP_NAME_FIELD, String::class.java),
                showingPublicField = exactField(
                    rowClass,
                    SHOWING_PUBLIC_FIELD,
                    Boolean::class.javaPrimitiveType!!,
                ),
                onKeyguardField = exactField(
                    rowClass,
                    ON_KEYGUARD_FIELD,
                    Boolean::class.javaPrimitiveType!!,
                ),
                notificationKeyField = exactField(entryClass, KEY_FIELD, String::class.java),
                statusBarNotificationField = exactField(
                    entryClass,
                    STATUS_BAR_NOTIFICATION_FIELD,
                    StatusBarNotification::class.java,
                ),
                visibleMediaPlayersField = exactStaticField(
                    playerDataClass,
                    VISIBLE_MEDIA_PLAYERS_FIELD,
                    LinkedHashMap::class.java,
                ),
                panelMediaDataField = exactField(
                    controlPanelClass,
                    PANEL_MEDIA_DATA_FIELD,
                    mediaDataClass,
                ),
                panelMediaViewHolderField = exactField(
                    controlPanelClass,
                    PANEL_MEDIA_VIEW_HOLDER_FIELD,
                    mediaViewHolderClass,
                ),
                mediaPlayPauseField = exactField(
                    mediaViewHolderClass,
                    MEDIA_PLAY_PAUSE_FIELD,
                    ImageButton::class.java,
                ),
                mediaSongField = exactField(mediaDataClass, MEDIA_SONG_FIELD, CharSequence::class.java),
                mediaArtistField = exactField(
                    mediaDataClass,
                    MEDIA_ARTIST_FIELD,
                    CharSequence::class.java,
                ),
                mediaPlayingField = exactField(
                    mediaDataClass,
                    MEDIA_PLAYING_FIELD,
                    java.lang.Boolean::class.java,
                ),
            )
        }

        /** 解析实例字段并要求精确类型。 */
        private fun exactField(owner: Class<*>, name: String, type: Class<*>): Field =
            owner.getDeclaredField(name).apply {
                check(this.type == type) { "lockscreen_content_field_type:$name" }
                isAccessible = true
            }

        /** 解析静态字段并要求精确类型。 */
        private fun exactStaticField(owner: Class<*>, name: String, type: Class<*>): Field =
            exactField(owner, name, type).apply {
                check(java.lang.reflect.Modifier.isStatic(modifiers)) {
                    "lockscreen_content_field_not_static:$name"
                }
            }

        /** Titan 2 通知栈资源名称。 */
        private const val NOTIFICATION_STACK_RESOURCE: String = "notification_stack_scroller"
        /** Titan 2 通知栈最终类名。 */
        private const val NOTIFICATION_STACK_CLASS: String =
            "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout"
        /** Titan 2 单条通知行类名。 */
        private const val NOTIFICATION_ROW_CLASS: String =
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow"
        /** Titan 2 通知条目类名。 */
        private const val NOTIFICATION_ENTRY_CLASS: String =
            "com.android.systemui.statusbar.notification.collection.NotificationEntry"
        /** Titan 2 媒体播放器静态目录类名。 */
        private const val MEDIA_PLAYER_DATA_CLASS: String =
            "com.android.systemui.media.controls.ui.controller.MediaPlayerData"
        /** Titan 2 媒体控制面板类名。 */
        private const val MEDIA_CONTROL_PANEL_CLASS: String =
            "com.android.systemui.media.controls.ui.controller.MediaControlPanel"
        /** Titan 2 媒体数据模型类名。 */
        private const val MEDIA_DATA_CLASS: String =
            "com.android.systemui.media.controls.shared.model.MediaData"
        /** Titan 2 媒体视图持有者类名。 */
        private const val MEDIA_VIEW_HOLDER_CLASS: String =
            "com.android.systemui.media.controls.ui.view.MediaViewHolder"
        /** 通知行持有条目的字段名。 */
        private const val ENTRY_FIELD: String = "mEntry"
        /** 通知行应用名称字段名。 */
        private const val APP_NAME_FIELD: String = "mAppName"
        /** 通知行公共隐私布局标记字段名。 */
        private const val SHOWING_PUBLIC_FIELD: String = "mShowingPublic"
        /** 通知行锁屏状态字段名。 */
        private const val ON_KEYGUARD_FIELD: String = "mOnKeyguard"
        /** 通知条目原生键字段名。 */
        private const val KEY_FIELD: String = "mKey"
        /** 通知条目系统通知对象字段名。 */
        private const val STATUS_BAR_NOTIFICATION_FIELD: String = "mSbn"
        /** 当前可见媒体播放器映射字段名。 */
        private const val VISIBLE_MEDIA_PLAYERS_FIELD: String = "visibleMediaPlayers"
        /** 媒体控制面板数据字段名。 */
        private const val PANEL_MEDIA_DATA_FIELD: String = "mMediaData"
        /** 媒体控制面板视图持有者字段名。 */
        private const val PANEL_MEDIA_VIEW_HOLDER_FIELD: String = "mMediaViewHolder"
        /** 媒体视图持有者播放暂停按钮字段名。 */
        private const val MEDIA_PLAY_PAUSE_FIELD: String = "actionPlayPause"
        /** 媒体曲目字段名。 */
        private const val MEDIA_SONG_FIELD: String = "song"
        /** 媒体艺术家字段名。 */
        private const val MEDIA_ARTIST_FIELD: String = "artist"
        /** 媒体播放状态字段名。 */
        private const val MEDIA_PLAYING_FIELD: String = "isPlaying"
        /** UI 状态最多镜像的通知数量。 */
        private const val MAXIMUM_NOTIFICATION_COUNT: Int = 3
        /** 低于该透明度的原生通知不视为可见。 */
        private const val MINIMUM_VISIBLE_ALPHA: Float = 0.01f
    }
}

/** 把系统可见内容文字折叠为 UI 状态允许的有界单行。 */
internal fun sanitizeLockscreenContentText(text: CharSequence?): String = text
    ?.toString()
    ?.replace('\n', ' ')
    ?.replace('\r', ' ')
    ?.trim()
    ?.replace(LOCKSCREEN_CONTENT_WHITESPACE, " ")
    ?.take(MAXIMUM_LOCKSCREEN_CONTENT_TEXT_LENGTH)
    .orEmpty()

/** 将原生通知键转换为不暴露包名、用户或通知 ID 的稳定摘要标识。 */
internal fun notificationSafeKey(rawKey: String): String =
    "N-${rawKey.hashCode().toUInt().toString(radix = 16)}"

/** 用于折叠通知和媒体文本连续空白的表达式。 */
private val LOCKSCREEN_CONTENT_WHITESPACE: Regex = Regex("\\s+")
/** 单条通知或媒体文字允许进入像素 UI 的最大字符数。 */
private const val MAXIMUM_LOCKSCREEN_CONTENT_TEXT_LENGTH: Int = 120
