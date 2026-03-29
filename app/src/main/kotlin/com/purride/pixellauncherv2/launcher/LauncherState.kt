package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.PixelFontCatalog
import com.purride.pixellauncherv2.render.PixelFontSize
import com.purride.pixellauncherv2.render.PixelFontStyle
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.render.PixelTheme
import com.purride.pixellauncherv2.render.ScreenProfileFactory
import com.purride.pixellauncherv2.render.IdleFluidState
import com.purride.pixellauncherv2.render.ChargeIdleEffect
import com.purride.pixellauncherv2.data.SmsMessageEntry
import com.purride.pixellauncherv2.data.SmsThreadSummary
import com.purride.pixellauncherv2.data.UnreadSmsEntry

data class LauncherState(
    val apps: List<AppEntry> = emptyList(),
    val drawerVisibleApps: List<AppEntry> = emptyList(),
    val drawerQuery: String = "",
    val isDrawerSearchFocused: Boolean = false,
    val isDrawerRailSliding: Boolean = false,
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
    val unreadSmsEntries: List<UnreadSmsEntry> = emptyList(),
    val smsSelectedIndex: Int = 0,
    val smsListStartIndex: Int = 0,
    val smsThreads: List<SmsThreadSummary> = emptyList(),
    val smsThreadSelectedIndex: Int = 0,
    val smsThreadListStartIndex: Int = 0,
    val smsCurrentThreadId: Long? = null,
    val smsCurrentAddress: String = "",
    val smsMessages: List<SmsMessageEntry> = emptyList(),
    val smsDraftText: String = "",
    val isDefaultSmsApp: Boolean = false,
    val smsPermissionState: SmsPermissionState = SmsPermissionState.MISSING,
    val selectedFontSize: PixelFontSize = PixelFontCatalog.defaultFontSize,
    val selectedFontStyle: PixelFontStyle = PixelFontCatalog.defaultFontStyle,
    val selectedPixelShape: PixelShape = PixelShape.SQUARE,
    val selectedDotSizePx: Int = ScreenProfileFactory.defaultDotSizePx,
    val selectedTheme: PixelTheme = PixelTheme.GREEN_PHOSPHOR,
    val drawerListAlignment: DrawerListAlignment = DrawerListAlignment.LEFT,
    val isIdlePageEnabled: Boolean = false,
    val openDrawerInSearchMode: Boolean = false,
    val chargeIdleEffect: ChargeIdleEffect = ChargeIdleEffect.FLUID,
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val recentApps: List<String> = emptyList(),
    val lastInteractionUptimeMs: Long = 0L,
    val launchCount: Int = 0,
    val lastLaunchPackageName: String? = null,
    val terminalStatusText: String = "",
    val nextAlarmText: String = "--:--",
    val missedCallCount: Int = 0,
    val unreadSmsCount: Int = 0,
    val rainHintText: String = "",
    val screenUsageTimeText: String = "--:--",
    val screenOpenCountText: String = "--",
    val quoteText: String = "BREATHE, FOCUS ON ONE THING, AND LET THE REST WAIT.",
    val homeContextCard: HomeContextCard = HomeContextCard.QUOTE,
    val idleFluidState: IdleFluidState = IdleFluidState(),
)

enum class HomeContextCard {
    QUOTE,
    MEDIA,
    NOTIFICATIONS,
    TODO,
}

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
    SMS_INBOX,
    DIAGNOSTICS,
    IDLE,
}

enum class SmsPermissionState {
    MISSING,
    READ_ONLY,
    READY,
}
