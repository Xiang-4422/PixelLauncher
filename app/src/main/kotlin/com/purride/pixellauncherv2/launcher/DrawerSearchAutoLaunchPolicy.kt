package com.purride.pixellauncherv2.launcher

/** Drawer 搜索结果收敛为唯一 App 时的自动启动策略。 */
internal object DrawerSearchAutoLaunchPolicy {

    fun resolve(state: LauncherState): AppEntry? {
        if (state.mode != LauncherMode.APP_DRAWER) return null
        if (!state.isDrawerSearchFocused || state.drawerQuery.isBlank()) return null
        return state.drawerVisibleApps.singleOrNull()
    }
}
