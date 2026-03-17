package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerContentTapResolverTest {

    @Test
    fun searchWithResultsTapAnyItemLaunchesDirectly() {
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            drawerQuery = "cam",
            isDrawerSearchFocused = true,
            selectedIndex = 1,
        )

        val decision = DrawerContentTapResolver.resolve(
            state = state,
            tappedAppIndex = 3,
        )

        assertEquals(DrawerContentTapAction.LAUNCH_SELECTED, decision.action)
        assertEquals(3, decision.targetIndex)
    }

    @Test
    fun searchWithResultsTapSelectedItemLaunchesDirectly() {
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            drawerQuery = "cam",
            isDrawerSearchFocused = true,
            selectedIndex = 2,
        )

        val decision = DrawerContentTapResolver.resolve(
            state = state,
            tappedAppIndex = 2,
        )

        assertEquals(DrawerContentTapAction.LAUNCH_SELECTED, decision.action)
        assertEquals(2, decision.targetIndex)
    }

    @Test
    fun searchFocusedWithBlankQueryTapOutsideExitsSearch() {
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            drawerQuery = "",
            isDrawerSearchFocused = true,
            selectedIndex = 0,
        )

        val decision = DrawerContentTapResolver.resolve(
            state = state,
            tappedAppIndex = null,
        )

        assertEquals(DrawerContentTapAction.EXIT_SEARCH, decision.action)
    }
}
