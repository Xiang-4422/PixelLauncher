package com.purride.pixellauncherv2.launcher

import com.purride.pixelcore.PixelShape
import com.purride.pixellauncherv2.layout.LauncherLayoutProfileFactory
import com.purride.pixellauncherv2.model.CallLogGroup
import com.purride.pixellauncherv2.model.ContactDetail
import com.purride.pixellauncherv2.model.ContactEntry
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
    /** 通话记录列表（相邻同号已合并）。 */
    val callLogGroups: List<CallLogGroup> = emptyList(),
    /** 通话记录首次加载中。 */
    val isCallLogLoading: Boolean = false,
    /** 是否具备发起通话的权限。 */
    val hasCallPhonePermission: Boolean = false,
    /** 拨号模块当前页（最近通话 / 拨号盘）。 */
    val callPageIndex: Int = CallPageIndex.RECENT,
    /** 拨号盘当前输入的号码。 */
    val dialInput: String = "",
    /** 拨号盘输入的 T9 命中结果（已限量），空列表表示未命中。 */
    val dialMatches: List<ContactEntry> = emptyList(),
    /** 联系人目录（一人一条、provider 排序）。 */
    val contacts: List<ContactDetail> = emptyList(),
    /** 联系人目录首次加载中。 */
    val isContactsLoading: Boolean = false,
    /** 是否具备读取联系人的权限；缺失时联系人页渲染带授权入口的空态。 */
    val hasContactsPermission: Boolean = false,
    /** 详情页当前联系人的 lookupKey；详情不可见时为空。数据从 [contacts] 现场解析，单一真值。 */
    val contactDetailLookupKey: String = "",
    /** 编辑器目标联系人的 lookupKey；新建时为空串。 */
    val contactEditorLookupKey: String = "",
    /** 编辑器姓名草稿。 */
    val contactEditorNameDraft: String = "",
    /** 编辑器"新增号码"草稿；编辑既有联系人时该字段留空表示不加号码。 */
    val contactEditorNumberDraft: String = "",
    val selectedPixelShape: PixelShape = PixelShape.SQUARE,
    val selectedDotSizePx: Int = LauncherLayoutProfileFactory.defaultDotSizePx,
    val isPixelGapEnabled: Boolean = true,
    val selectedTheme: PixelTheme = PixelTheme.DAY,
    /** 设置页当前明确选中的字体家族、宽度模式和默认字号。 */
    val fontSelection: LauncherFontSelection = PixelFontCatalog.defaultUiFontSelection,
    /** 候选字体正在后台准备，当前字体仍保持激活。 */
    val isFontLoading: Boolean = false,
    /** indexed pack 缓存的紧凑诊断摘要。 */
    val fontCacheSummary: String = "0/0K",
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
    DIALER,
    CONTACT_DETAIL,
    CONTACT_EDITOR,
    APP_MANAGEMENT,
    DATA_HEALTH,
    NOTIFICATION_SETTINGS,
    LOADING_PREVIEW,
    DIAGNOSTICS,
    SNAKE,
    IDLE,
}

enum class SmsPermissionState {
    MISSING,
    READ_ONLY,
    READY,
}
