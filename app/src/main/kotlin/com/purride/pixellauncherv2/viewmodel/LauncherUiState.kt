package com.purride.pixellauncherv2.viewmodel

import com.purride.pixellauncherv2.data.SmsMessageEntry
import com.purride.pixellauncherv2.data.SmsThreadSummary
import com.purride.pixellauncherv2.data.DeepSeekAiConfig
import com.purride.pixellauncherv2.launcher.AppEntry
import com.purride.pixellauncherv2.launcher.ChargeIdleEffect
import com.purride.pixellauncherv2.launcher.DrawerFocus
import com.purride.pixellauncherv2.launcher.DrawerListAlignment
import com.purride.pixellauncherv2.launcher.IdleSettings
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.NotificationSourceInfo
import com.purride.pixellauncherv2.launcher.SmsPageIndex
import com.purride.pixellauncherv2.launcher.SmsPermissionState
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.launcher.PixelTheme
import com.purride.pixellauncherv2.render.ScreenProfileFactory

/**
 * 重写后的 Launcher UI 状态快照。
 * 外观类型目前仍引用旧 render 层类型，将在后续逐步迁移至 pixel-engine 类型。
 */
data class LauncherUiState(
    // ── App Drawer ────────────────────────────────────────────────────────────
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

    // ── Time ──────────────────────────────────────────────────────────────────
    val currentTimeText: String = "",
    val currentDateText: String = "",
    val currentWeekdayText: String = "",

    // ── Navigation ────────────────────────────────────────────────────────────
    val mode: LauncherMode = LauncherMode.HOME,
    val returnMode: LauncherMode = LauncherMode.HOME,

    // ── Settings screen ───────────────────────────────────────────────────────
    val settingsSelectedIndex: Int = 0,
    val settingsListStartIndex: Int = 0,
    val appEditorSelectedIndex: Int = 0,
    val appEditorNameDraft: String = "",
    val appEditorAliasDraft: String = "",

    // ── SMS ───────────────────────────────────────────────────────────────────
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
    val smsSendStatusText: String = "",
    val isDefaultSmsApp: Boolean = false,
    val smsPermissionState: SmsPermissionState = SmsPermissionState.MISSING,

    // ── Appearance (old render types; migrated to pixel-engine in Phase 1+) ───
    val selectedPixelShape: PixelShape = PixelShape.SQUARE,
    val selectedDotSizePx: Int = ScreenProfileFactory.defaultDotSizePx,
    val selectedPixelSizePresetIndex: Int = -1,
    val isPixelGapEnabled: Boolean = true,
    val selectedTheme: PixelTheme = PixelTheme.DAY,

    // ── UI behaviour ──────────────────────────────────────────────────────────
    val drawerListAlignment: DrawerListAlignment = DrawerListAlignment.LEFT,
    val isIdlePageEnabled: Boolean = false,
    val chargeAutoIdleEnabled: Boolean = false,
    val inactivityAutoIdleEnabled: Boolean = true,
    val idleTimeoutSeconds: Int = IdleSettings.DEFAULT_TIMEOUT_SECONDS,
    val openDrawerInSearchMode: Boolean = false,
    val chargeIdleEffect: ChargeIdleEffect = ChargeIdleEffect.FLUID,

    // ── Device status ─────────────────────────────────────────────────────────
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,

    // ── Usage / stats ─────────────────────────────────────────────────────────
    val recentApps: List<String> = emptyList(),
    val lastInteractionUptimeMs: Long = 0L,
    val launchCount: Int = 0,
    val lastLaunchPackageName: String? = null,

    // ── Home info rows ────────────────────────────────────────────────────────
    val nextAlarmText: String = "--:--",
    val missedCallCount: Int = 0,
    val unreadSmsCount: Int = 0,
    val notificationSummaryText: String = "",
    val notificationCount: Int = 0,
    val notificationSources: List<NotificationSourceInfo> = emptyList(),
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
    val deepSeekApiKey: String = DeepSeekAiConfig.DEFAULT_API_KEY,
)
