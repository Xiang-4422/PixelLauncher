package com.purride.pixellauncherv2.launcher

import com.purride.pixelcore.PixelShape
import com.purride.pixellauncherv2.layout.LauncherLayoutProfileFactory
import com.purride.pixellauncherv2.model.SmsMessageEntry
import com.purride.pixellauncherv2.model.SmsThreadSummary

/** 保存 Launcher reducer 使用的完整不可变状态快照。 */
data class LauncherState(
    val apps: List<AppEntry> = emptyList(),
    val drawerVisibleApps: List<AppEntry> = emptyList(),
    val drawerQuery: String = "",
    val isDrawerSearchFocused: Boolean = false,
    val isDrawerRailSliding: Boolean = false,
    val isAppActionMenuVisible: Boolean = false,
    val selectedIndex: Int = 0,
    val listStartIndex: Int = 0,
    val drawerPageIndex: Int = 0,
    val drawerFocus: DrawerFocus = DrawerFocus.LIST,
    val isLoading: Boolean = true,
    val currentTimeText: String = "",
    val currentDateText: String = "",
    val currentWeekdayText: String = "",
    val mode: LauncherMode = LauncherMode.HOME,
    val returnMode: LauncherMode = LauncherMode.HOME,
    val settingsSelectedIndex: Int = 0,
    val settingsListStartIndex: Int = 0,
    val appEditorSelectedIndex: Int = 0,
    val appEditorNameDraft: String = "",
    val appEditorAliasDraft: String = "",
    val unreadSmsEntries: List<SmsMessageEntry> = emptyList(),
    val smsPageIndex: Int = SmsPageIndex.UNREAD,
    val smsSelectedIndex: Int = 0,
    val smsListStartIndex: Int = 0,
    val smsThreads: List<SmsThreadSummary> = emptyList(),
    val isSmsThreadsLoading: Boolean = false,
    val smsThreadSelectedIndex: Int = 0,
    val smsThreadListStartIndex: Int = 0,
    val smsAllMessages: List<SmsMessageEntry> = emptyList(),
    val smsCurrentConversationKey: String = "",
    val smsCurrentConversationTitle: String = "",
    val smsCurrentIsServiceConversation: Boolean = false,
    val smsCurrentThreadId: Long? = null,
    val smsCurrentAddress: String = "",
    val smsMessages: List<SmsMessageEntry> = emptyList(),
    val smsThreadSearchQuery: String = "",
    val smsDraftText: String = "",
    val smsSendStatus: SmsSendStatus = SmsSendStatus.NONE,
    /** 详情页消息长按浮层菜单是否可见。 */
    val isSmsMessageMenuVisible: Boolean = false,
    /** 浮层菜单对应的消息 id；菜单不可见时为 -1。 */
    val smsMessageMenuMessageId: Long = -1L,
    /** 会话列表长按浮层菜单是否可见。 */
    val isSmsThreadMenuVisible: Boolean = false,
    /** 会话浮层菜单对应的会话键；菜单不可见时为空。 */
    val smsThreadMenuConversationKey: String = "",
    /** 被静音（不弹通知）的会话键集合。 */
    val smsMutedConversationKeys: Set<String> = emptySet(),
    val isDefaultSmsApp: Boolean = false,
    val smsPermissionState: SmsPermissionState = SmsPermissionState.MISSING,
    val selectedPixelShape: PixelShape = PixelShape.SQUARE,
    val selectedDotSizePx: Int = LauncherLayoutProfileFactory.defaultDotSizePx,
    val isPixelGapEnabled: Boolean = true,
    val selectedTheme: PixelTheme = PixelTheme.DAY,
    val drawerListAlignment: DrawerListAlignment = DrawerListAlignment.LEFT,
    val isIdlePageEnabled: Boolean = false,
    val chargeAutoIdleEnabled: Boolean = false,
    val inactivityAutoIdleEnabled: Boolean = true,
    val idleTimeoutSeconds: Int = IdleSettings.DEFAULT_TIMEOUT_SECONDS,
    val openDrawerInSearchMode: Boolean = false,
    val chargeIdleEffect: ChargeIdleEffect = ChargeIdleEffect.FLUID,
    val isPixelMatterEffectEnabled: Boolean = true,
    val pixelMatterEffectMode: PixelMatterEffectMode = PixelMatterEffectMode.SAND,
    val isPixelMatterHandControlEnabled: Boolean = false,
    val isPixelMatterHandDebugEnabled: Boolean = true,
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val recentApps: List<String> = emptyList(),
    val lastInteractionUptimeMs: Long = 0L,
    val launchCount: Int = 0,
    val lastLaunchPackageName: String? = null,
    val nextAlarmText: String = "--:--",
    val missedCallCount: Int = 0,
    val unreadSmsCount: Int = 0,
    val mediaPlayback: MediaPlaybackSnapshot = MediaPlaybackSnapshot(),
    val notificationSummaryText: String = "",
    val notificationCount: Int = 0,
    val notificationSources: List<NotificationSourceInfo> = emptyList(),
    val notificationItems: List<NotificationSignal> = emptyList(),
    val mutedNotificationSourceIds: Set<String> = emptySet(),
    val priorityNotificationSourceIds: Set<String> = emptySet(),
    val rainHintText: String = "",
    val rainUpdatedTimeText: String = "",
    val screenUsageTimeText: String = "--:--",
    val screenOpenCountText: String = "--",
    val statusBarMessageText: String = "",
    val statusBarActionLeadingText: String = "",
    val statusBarActionLabel: String = "",
    val isStatusBarActionDanger: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val hasCallLogPermission: Boolean = false,
    val hasSmsReadPermission: Boolean = false,
    val hasPostNotificationPermission: Boolean = false,
    val hasNotificationListenerAccess: Boolean = false,
    val dataHealthUpdatedTimeText: String = "",
)

enum class DrawerFocus {
    LIST,
}

enum class DrawerListAlignment {
    LEFT,
    CENTER,
    RIGHT,
}

enum class LauncherMode {
    HOME,
    APP_DRAWER,
    SETTINGS,
    SMS_ROLE_PROMPT,
    SMS_THREADS,
    SMS_THREAD_DETAIL,
    APP_MANAGEMENT,
    DATA_HEALTH,
    NOTIFICATION_SETTINGS,
    LOADING_PREVIEW,
    DIAGNOSTICS,
    IDLE,
}

enum class SmsPermissionState {
    MISSING,
    READ_ONLY,
    READY,
}
