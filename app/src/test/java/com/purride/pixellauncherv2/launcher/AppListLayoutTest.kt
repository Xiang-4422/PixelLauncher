package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.ScreenProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppListLayoutTest {

    @Test
    fun hitTestMapsLogicalPointToScrolledAppIndex() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )
        val metrics = AppListLayout.metrics(screenProfile)
        val state = LauncherState(
            apps = List(20) { index ->
                AppEntry(
                    label = "App $index",
                    packageName = "pkg.$index",
                    activityName = "Activity$index",
                )
            },
            selectedIndex = 5,
            listStartIndex = 4,
            isLoading = false,
        )

        val tappedIndex = AppListLayout.hitTestAppIndex(
            screenProfile = screenProfile,
            state = state,
            logicalX = 10,
            logicalY = metrics.listStartY + metrics.rowHeight + 1,
        )

        assertEquals(5, tappedIndex)
    }

    @Test
    fun hitTestMapsRailTapToDrawerPage() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )
        val metrics = AppListLayout.metrics(screenProfile)

        val tappedPage = AppListLayout.hitTestIndexRailPage(
            screenProfile = screenProfile,
            logicalX = metrics.indexRailLeft + 1,
            logicalY = metrics.railTop + (metrics.railHeight / 2),
            pageCount = 6,
        )

        assertEquals(3, tappedPage)
    }

    @Test
    fun hitTestReturnsNullOutsideListArea() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )
        val state = LauncherState(
            apps = List(3) { index ->
                AppEntry(
                    label = "App $index",
                    packageName = "pkg.$index",
                    activityName = "Activity$index",
                )
            },
            isLoading = false,
        )

        val tappedIndex = AppListLayout.hitTestAppIndex(
            screenProfile = screenProfile,
            state = state,
            logicalX = 10,
            logicalY = 1,
        )

        assertNull(tappedIndex)
    }

    @Test
    fun hitTestAppReturnsNullInsideIndexRail() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )
        val metrics = AppListLayout.metrics(screenProfile)
        val state = LauncherState(
            apps = List(8) { index ->
                AppEntry(
                    label = "App $index",
                    packageName = "pkg.$index",
                    activityName = "Activity$index",
                )
            },
            isLoading = false,
        )

        val tappedIndex = AppListLayout.hitTestAppIndex(
            screenProfile = screenProfile,
            state = state,
            logicalX = metrics.indexRailLeft + 1,
            logicalY = metrics.listStartY + 1,
        )

        assertNull(tappedIndex)
    }

    @Test
    fun listStartsBelowSharedHeaderDivider() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )

        val metrics = AppListLayout.metrics(screenProfile)

        assertEquals(true, metrics.listStartY > LauncherHeaderLayout.dividerY)
    }
}
