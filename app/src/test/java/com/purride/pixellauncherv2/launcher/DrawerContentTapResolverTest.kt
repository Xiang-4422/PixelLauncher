package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Coverage for [DrawerContentTapResolver.resolve] — tapping an app launches it
 * regardless of search state; tapping empty space exits an active search; and an
 * idle tap is a no-op. JVM-safe; no Android dependencies.
 */
class DrawerContentTapResolverTest {

    @Test
    fun resolve_launchesTappedAppRegardlessOfSearchState() {
        val decision = DrawerContentTapResolver.resolve(
            LauncherState(isDrawerSearchFocused = true, drawerQuery = "ab"),
            tappedAppIndex = 3,
        )
        assertEquals(DrawerContentTapAction.LAUNCH_SELECTED, decision.action)
        assertEquals(3, decision.targetIndex)
    }

    @Test
    fun resolve_exitsSearchWhenFocusedAndNoAppTapped() {
        val decision = DrawerContentTapResolver.resolve(
            LauncherState(isDrawerSearchFocused = true),
            tappedAppIndex = null,
        )
        assertEquals(DrawerContentTapAction.EXIT_SEARCH, decision.action)
        assertNull(decision.targetIndex)
    }

    @Test
    fun resolve_exitsSearchWhenQueryPresentAndNoAppTapped() {
        val decision = DrawerContentTapResolver.resolve(
            LauncherState(drawerQuery = "chr"),
            tappedAppIndex = null,
        )
        assertEquals(DrawerContentTapAction.EXIT_SEARCH, decision.action)
    }

    @Test
    fun resolve_noOpWhenIdleAndNoAppTapped() {
        val decision = DrawerContentTapResolver.resolve(
            LauncherState(),
            tappedAppIndex = null,
        )
        assertEquals(DrawerContentTapAction.NONE, decision.action)
        assertNull(decision.targetIndex)
    }
}
