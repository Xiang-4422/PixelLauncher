package com.purride.pixellauncherv2.launcher

import com.purride.pixelui.PixelRouteTransition
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

    @Test
    fun navigatorGroupsPagerAndSmsHomeModes() {
        assertEquals(LauncherRouteDestination.MAIN, LauncherRootHost.destinationFor(LauncherMode.HOME))
        assertEquals(LauncherRouteDestination.MAIN, LauncherRootHost.destinationFor(LauncherMode.APP_DRAWER))
        assertEquals(LauncherRouteDestination.MAIN, LauncherRootHost.destinationFor(LauncherMode.SETTINGS))
        assertEquals(
            LauncherRouteDestination.MORE_SETTINGS,
            LauncherRootHost.destinationFor(LauncherMode.MORE_SETTINGS),
        )
        assertEquals(LauncherRouteDestination.LOADING_PREVIEW, LauncherRootHost.destinationFor(LauncherMode.LOADING_PREVIEW))
        assertEquals(LauncherRouteDestination.SMS_THREADS, LauncherRootHost.destinationFor(LauncherMode.SMS_THREADS))
        assertEquals(
            LauncherRouteDestination.SMS_THREAD_DETAIL,
            LauncherRootHost.destinationFor(LauncherMode.SMS_THREAD_DETAIL),
        )
    }

    @Test
    fun routeTransitionsDefaultToHorizontalButSmsOpensFromBottom() {
        assertEquals(null, LauncherRootHost.transitionFor(LauncherRouteDestination.MAIN))
        assertEquals(null, LauncherRootHost.transitionFor(LauncherRouteDestination.MORE_SETTINGS))
        assertEquals(null, LauncherRootHost.transitionFor(LauncherRouteDestination.SMS_THREAD_DETAIL))
        assertEquals(null, LauncherRootHost.transitionFor(LauncherRouteDestination.DATA_HEALTH))
        assertEquals(null, LauncherRootHost.transitionFor(LauncherRouteDestination.LOADING_PREVIEW))
        assertEquals(
            PixelRouteTransition.SlideVertical,
            LauncherRootHost.transitionFor(LauncherRouteDestination.SMS_THREADS),
        )
        assertEquals(
            PixelRouteTransition.SlideVertical,
            LauncherRootHost.transitionFor(LauncherRouteDestination.SMS_ROLE_PROMPT),
        )
    }

    @Test
    fun navigatorRouteChangesUseStackSemanticsForDirectionalTransitions() {
        assertEquals(
            LauncherRouteNavigationAction.PUSH,
            LauncherRootHost.navigationAction(
                listOf(LauncherRouteDestination.MAIN.routeName),
                LauncherRouteDestination.MORE_SETTINGS,
            ),
        )
        assertEquals(
            LauncherRouteNavigationAction.POP,
            LauncherRootHost.navigationAction(
                listOf(
                    LauncherRouteDestination.MAIN.routeName,
                    LauncherRouteDestination.MORE_SETTINGS.routeName,
                    LauncherRouteDestination.DATA_HEALTH.routeName,
                ),
                LauncherRouteDestination.MORE_SETTINGS,
            ),
        )
        assertEquals(
            LauncherRouteNavigationAction.PUSH,
            LauncherRootHost.navigationAction(
                listOf(LauncherRouteDestination.MAIN.routeName),
                LauncherRouteDestination.DATA_HEALTH,
            ),
        )
        assertEquals(
            LauncherRouteNavigationAction.POP,
            LauncherRootHost.navigationAction(
                listOf(
                    LauncherRouteDestination.MAIN.routeName,
                    LauncherRouteDestination.SMS_THREADS.routeName,
                    LauncherRouteDestination.SMS_THREAD_DETAIL.routeName,
                ),
                LauncherRouteDestination.SMS_THREADS,
            ),
        )
        assertEquals(
            LauncherRouteNavigationAction.POP_TO_ROOT,
            LauncherRootHost.navigationAction(
                listOf(
                    LauncherRouteDestination.MAIN.routeName,
                    LauncherRouteDestination.DIAGNOSTICS.routeName,
                    LauncherRouteDestination.DATA_HEALTH.routeName,
                ),
                LauncherRouteDestination.MAIN,
            ),
        )
        assertEquals(
            LauncherRouteNavigationAction.NONE,
            LauncherRootHost.navigationAction(
                listOf(LauncherRouteDestination.MAIN.routeName),
                LauncherRouteDestination.MAIN,
            ),
        )
    }
}
