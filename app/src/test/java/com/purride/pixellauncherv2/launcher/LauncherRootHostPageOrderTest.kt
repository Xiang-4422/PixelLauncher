package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherRootHostPageOrderTest {

    @Test
    fun mainPagesAreSettingsHomeDrawerFromLeftToRight() {
        assertEquals(
            listOf(LauncherMode.SETTINGS, LauncherMode.HOME, LauncherMode.APP_DRAWER),
            LauncherRootHost.MAIN_PAGE_MODES,
        )
        assertEquals(0, LauncherRootHost.modeToMainPage(LauncherMode.SETTINGS))
        assertEquals(1, LauncherRootHost.modeToMainPage(LauncherMode.HOME))
        assertEquals(2, LauncherRootHost.modeToMainPage(LauncherMode.APP_DRAWER))
    }
}
