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

        assertEquals(true, tappedPage == 2 || tappedPage == 3)
    }

    @Test
    fun hitTestMapsRailTapToLetterBounds() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )
        val metrics = AppListLayout.metrics(screenProfile)

        val topLetter = AppListLayout.hitTestIndexRailLetter(
            screenProfile = screenProfile,
            logicalX = metrics.indexRailLeft + 1,
            logicalY = metrics.railTop,
        )
        val bottomLetter = AppListLayout.hitTestIndexRailLetter(
            screenProfile = screenProfile,
            logicalX = metrics.indexRailLeft + 1,
            logicalY = metrics.railTop + metrics.railHeight - 1,
        )

        assertEquals(0, topLetter)
        assertEquals(25, bottomLetter)
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

    @Test
    fun centeredVisibleRowsIsAlwaysOdd() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )

        val centeredRows = AppListLayout.centeredVisibleRows(screenProfile)

        assertEquals(1, centeredRows % 2)
    }

    @Test
    fun centeredVisibleRowsGrowsWithAvailableHeight() {
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

        val smallRows = AppListLayout.centeredVisibleRows(small)
        val largeRows = AppListLayout.centeredVisibleRows(large)

        assertEquals(1, smallRows % 2)
        assertEquals(1, largeRows % 2)
        assertEquals(true, largeRows >= smallRows)
    }

    @Test
    fun hitTestUsesCenteredWindowInDrawerListMode() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )
        val metrics = AppListLayout.metrics(screenProfile)
        val centeredWindow = AppListLayout.centeredListWindow(screenProfile)
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
            isLoading = false,
        )

        val tappedCenterIndex = AppListLayout.hitTestAppIndex(
            screenProfile = screenProfile,
            state = state,
            logicalX = metrics.textX,
            logicalY = centeredWindow.rowTop(centeredWindow.centerRow) + 1,
        )

        assertEquals(8, tappedCenterIndex)
    }

    @Test
    fun hitTestReturnsNullForCenteredPaddingRows() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )
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
            isLoading = false,
        )

        val tappedIndex = AppListLayout.hitTestAppIndex(
            screenProfile = screenProfile,
            state = state,
            logicalX = metrics.textX,
            logicalY = metrics.listStartY + 1,
        )

        assertNull(tappedIndex)
    }

    @Test
    fun centeredListTopKeepsTopAndBottomPaddingBalanced() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )
        val metrics = AppListLayout.metrics(screenProfile)
        val centeredWindow = AppListLayout.centeredListWindow(screenProfile)
        val centeredTop = centeredWindow.listTop
        val centeredBottom = centeredWindow.listBottomExclusive
        val topPadding = centeredTop - metrics.listStartY
        val bottomPadding = (metrics.listStartY + metrics.railHeight) - centeredBottom

        assertEquals(true, topPadding >= 0)
        assertEquals(true, bottomPadding >= 0)
        assertEquals(true, kotlin.math.abs(topPadding - bottomPadding) <= 1)
    }

    @Test
    fun centeredWindowHeightNeverExceedsRailHeight() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )
        val metrics = AppListLayout.metrics(screenProfile)
        val centeredWindow = AppListLayout.centeredListWindow(screenProfile)

        val contentHeight = centeredWindow.listBottomExclusive - centeredWindow.listTop
        assertEquals(true, contentHeight <= metrics.railHeight)
    }

    @Test
    fun hitTestReturnsNullAboveCenteredTopInDrawerListMode() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )
        val metrics = AppListLayout.metrics(screenProfile)
        val centeredTop = AppListLayout.centeredListTop(screenProfile)
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            isDrawerSearchFocused = false,
            apps = List(10) { index ->
                AppEntry(
                    label = "App $index",
                    packageName = "pkg.$index",
                    activityName = "Activity$index",
                )
            },
            selectedIndex = 5,
            isLoading = false,
        )

        val logicalY = (centeredTop - 1).coerceAtLeast(metrics.listStartY)
        val tappedIndex = AppListLayout.hitTestAppIndex(
            screenProfile = screenProfile,
            state = state,
            logicalX = metrics.textX,
            logicalY = logicalY,
        )

        if (centeredTop > metrics.listStartY) {
            assertNull(tappedIndex)
        }
    }
}
