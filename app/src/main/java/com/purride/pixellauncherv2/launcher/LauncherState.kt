package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.PixelFontCatalog
import com.purride.pixellauncherv2.render.PixelFontId
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.render.PixelTheme
import com.purride.pixellauncherv2.render.ScreenProfileFactory
import com.purride.pixellauncherv2.render.IdleFluidState

data class LauncherState(
    val apps: List<AppEntry> = emptyList(),
    val selectedIndex: Int = 0,
    val listStartIndex: Int = 0,
    val drawerPageIndex: Int = 0,
    val drawerFocus: DrawerFocus = DrawerFocus.LIST,
    val isLoading: Boolean = true,
    val currentTimeText: String = "",
    val mode: LauncherMode = LauncherMode.HOME,
    val returnMode: LauncherMode = LauncherMode.HOME,
    val settingsSelectedIndex: Int = 0,
    val selectedFontId: PixelFontId = PixelFontCatalog.defaultFontId,
    val selectedPixelShape: PixelShape = PixelShape.SQUARE,
    val selectedDotSizePx: Int = ScreenProfileFactory.defaultDotSizePx,
    val selectedTheme: PixelTheme = PixelTheme.GREEN_PHOSPHOR,
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val recentApps: List<String> = emptyList(),
    val lastInteractionUptimeMs: Long = 0L,
    val launchCount: Int = 0,
    val lastLaunchPackageName: String? = null,
    val terminalStatusText: String = "",
    val idleFluidState: IdleFluidState = IdleFluidState(),
)

enum class DrawerFocus {
    LIST,
}

enum class LauncherMode {
    HOME,
    APP_DRAWER,
    SETTINGS,
    DIAGNOSTICS,
    IDLE,
}
