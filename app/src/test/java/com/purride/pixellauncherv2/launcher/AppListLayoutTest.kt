package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.ScreenProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppListLayoutTest {

    private val screenProfile = ScreenProfile(
        logicalWidth = 72,
        logicalHeight = 160,
        dotSizePx = 15,
    )

    @Test
    fun hitTestMapsLogicalPointToScrolledAppIndex() {
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
    fun hitTestMapsHiddenRailTapToDrawerPage() {
        val metrics = AppListLayout.metrics(screenProfile)

        val tappedPage = AppListLayout.hitTestIndexRailPage(
            screenProfile = screenProfile,
            logicalX = metrics.hiddenRailLeft + 1,
            logicalY = metrics.railTop + (metrics.railHeight / 2),
            pageCount = 6,
        )

        assertEquals(true, tappedPage == 2 || tappedPage == 3)
    }

    @Test
    fun hitTestMapsHiddenRailTapToLetterBounds() {
        val metrics = AppListLayout.metrics(screenProfile)

        val topLetter = AppListLayout.hitTestIndexRailLetter(
            screenProfile = screenProfile,
            logicalX = metrics.hiddenRailLeft + 1,
            logicalY = metrics.railTop,
        )
        val bottomLetter = AppListLayout.hitTestIndexRailLetter(
            screenProfile = screenProfile,
            logicalX = metrics.hiddenRailLeft + 1,
            logicalY = metrics.railTop + metrics.railHeight - 1,
        )

        assertEquals(0, topLetter)
        assertEquals(25, bottomLetter)
    }

    @Test
    fun hitTestReturnsNullOutsideListArea() {
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
    fun hitTestAppReturnsNullInsideHiddenRailForDrawerList() {
        val metrics = AppListLayout.metrics(screenProfile)
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            isDrawerSearchFocused = false,
            apps = List(8) { index ->
                AppEntry(
                    label = "App $index",
                    packageName = "pkg.$index",
                    activityName = "Activity$index",
                )
            },
            selectedIndex = 0,
            listStartIndex = 0,
            isLoading = false,
        )

        val tappedIndex = AppListLayout.hitTestAppIndex(
            screenProfile = screenProfile,
            state = state,
            logicalX = metrics.hiddenRailLeft + 1,
            logicalY = metrics.listStartY + 1,
        )

        assertNull(tappedIndex)
    }

    @Test
    fun hitTestHeaderAreaCoversWholeStatusBarRow() {
        val metrics = AppListLayout.metrics(screenProfile)

        val headerHit = AppListLayout.hitTestDrawerHeaderSearchArea(
            screenProfile = screenProfile,
            logicalX = screenProfile.logicalWidth / 2,
            logicalY = (metrics.headerTop + metrics.headerBottomExclusive) / 2,
        )

        assertEquals(true, headerHit)
    }

    @Test
    fun hitTestAppAllowsSearchResultsInsideHiddenRailWidth() {
        val metrics = AppListLayout.metrics(screenProfile)
        val apps = List(20) { index ->
            AppEntry(
                label = "App $index",
                packageName = "pkg.$index",
                activityName = "Activity$index",
            )
        }
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            isDrawerSearchFocused = true,
            drawerQuery = "a",
            apps = apps,
            drawerVisibleApps = apps,
            selectedIndex = 8,
            listStartIndex = 8,
            isLoading = false,
        )

        val tappedIndex = AppListLayout.hitTestAppIndex(
            screenProfile = screenProfile,
            state = state,
            logicalX = metrics.hiddenRailLeft + 1,
            logicalY = metrics.listStartY + 1,
        )

        assertEquals(8, tappedIndex)
    }

    @Test
    fun listStartsBelowSharedHeaderDivider() {
        val metrics = AppListLayout.metrics(screenProfile)

        assertEquals(true, metrics.listStartY > LauncherHeaderLayout.dividerY)
    }

    @Test
    fun visibleRowsUsesFullAvailableHeight() {
        val metrics = AppListLayout.metrics(screenProfile)
        val expectedRows = ((screenProfile.logicalHeight - metrics.listStartY) / metrics.rowHeight).coerceAtLeast(1)

        assertEquals(expectedRows, metrics.visibleRows)
    }

    @Test
    fun visibleRowsGrowsWithAvailableHeight() {
        val small = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 120,
            dotSizePx = 15,
        )
        val large = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 220,
            dotSizePx = 15,
        )

        val smallRows = AppListLayout.metrics(small).visibleRows
        val largeRows = AppListLayout.metrics(large).visibleRows

        assertEquals(true, largeRows >= smallRows)
    }

    @Test
    fun drawerHitTestUsesTopAlignedRows() {
        val metrics = AppListLayout.metrics(screenProfile)
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            isDrawerSearchFocused = false,
            apps = List(20) { index ->
                AppEntry(
                    label = "App $index",
                    packageName = "pkg.$index",
                    activityName = "Activity$index",
                )
            },
            selectedIndex = 8,
            listStartIndex = 8,
            isLoading = false,
        )

        val firstTap = AppListLayout.hitTestAppIndex(
            screenProfile = screenProfile,
            state = state,
            logicalX = metrics.textX,
            logicalY = metrics.listStartY + 1,
        )
        val secondTap = AppListLayout.hitTestAppIndex(
            screenProfile = screenProfile,
            state = state,
            logicalX = metrics.textX,
            logicalY = metrics.listStartY + metrics.rowHeight + 1,
        )

        assertEquals(8, firstTap)
        assertEquals(9, secondTap)
    }

    @Test
    fun hitTestTracksTopAlignedRowsWithScrollOffset() {
        val metrics = AppListLayout.metrics(screenProfile)
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            isDrawerSearchFocused = false,
            apps = List(20) { index ->
                AppEntry(
                    label = "App $index",
                    packageName = "pkg.$index",
                    activityName = "Activity$index",
                )
            },
            selectedIndex = 8,
            listStartIndex = 8,
            isLoading = false,
        )

        val offset = 6
        val tappedIndex = AppListLayout.hitTestAppIndex(
            screenProfile = screenProfile,
            state = state,
            logicalX = metrics.textX,
            logicalY = metrics.listStartY + offset + 1,
            drawerListScrollOffsetPx = offset,
        )

        assertEquals(8, tappedIndex)
    }

    @Test
    fun hitTestMapsBottomClippedOverflowRow() {
        val metrics = AppListLayout.metrics(screenProfile)
        val overflowY = metrics.listStartY + (metrics.visibleRows * metrics.rowHeight) + 1
        if (overflowY >= metrics.listStartY + metrics.railHeight) {
            return
        }
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            isDrawerSearchFocused = false,
            apps = List(20) { index ->
                AppEntry(
                    label = "App $index",
                    packageName = "pkg.$index",
                    activityName = "Activity$index",
                )
            },
            selectedIndex = 0,
            listStartIndex = 0,
            isLoading = false,
        )

        val tappedIndex = AppListLayout.hitTestAppIndex(
            screenProfile = screenProfile,
            state = state,
            logicalX = metrics.textX,
            logicalY = overflowY,
        )

        assertEquals(metrics.visibleRows, tappedIndex)
    }

    @Test
    fun hitTestMapsTopClippedPreviousRowDuringPositiveOffset() {
        val metrics = AppListLayout.metrics(screenProfile)
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            isDrawerSearchFocused = true,
            drawerQuery = "A",
            apps = List(20) { index ->
                AppEntry(
                    label = "App $index",
                    packageName = "pkg.$index",
                    activityName = "Activity$index",
                )
            },
            drawerVisibleApps = List(20) { index ->
                AppEntry(
                    label = "App $index",
                    packageName = "pkg.$index",
                    activityName = "Activity$index",
                )
            },
            selectedIndex = 10,
            listStartIndex = 10,
            isLoading = false,
        )

        val tappedIndex = AppListLayout.hitTestAppIndex(
            screenProfile = screenProfile,
            state = state,
            logicalX = metrics.textX,
            logicalY = metrics.listStartY + 1,
            drawerListScrollOffsetPx = 5,
        )

        assertEquals(9, tappedIndex)
    }

    @Test
    fun searchHitTestUsesTopAlignedRowsAndScrollOffset() {
        val metrics = AppListLayout.metrics(screenProfile)
        val apps = List(20) { index ->
            AppEntry(
                label = "App $index",
                packageName = "pkg.$index",
                activityName = "Activity$index",
            )
        }
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            isDrawerSearchFocused = true,
            drawerQuery = "A",
            apps = apps,
            drawerVisibleApps = apps,
            selectedIndex = 10,
            listStartIndex = 10,
            isLoading = false,
        )

        val offset = -5
        val selectedTap = AppListLayout.hitTestAppIndex(
            screenProfile = screenProfile,
            state = state,
            logicalX = metrics.textX,
            logicalY = metrics.listStartY + 1,
            drawerListScrollOffsetPx = offset,
        )
        val nextTap = AppListLayout.hitTestAppIndex(
            screenProfile = screenProfile,
            state = state,
            logicalX = metrics.textX,
            logicalY = metrics.listStartY + metrics.rowHeight + 1,
            drawerListScrollOffsetPx = offset,
        )

        assertEquals(10, selectedTap)
        assertEquals(11, nextTap)
    }
}
