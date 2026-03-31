package com.purride.pixellauncherv2.render

import com.purride.pixellauncherv2.launcher.AppEntry
import com.purride.pixellauncherv2.launcher.AppListLayout
import com.purride.pixellauncherv2.launcher.LauncherHeaderLayout
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.launcher.LauncherStateTransitions
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelRendererPageViewTest {

    private val screenProfile = ScreenProfile(
        logicalWidth = 72,
        logicalHeight = 160,
        dotSizePx = 15,
    )
    private val renderer = PixelRenderer(PixelFontEngine(BlockGlyphProvider()))
    private val apps = List(12) { index ->
        AppEntry(
            label = "APP$index",
            packageName = "pkg.$index",
            activityName = "Activity$index",
        )
    }

    @Test
    fun zeroOffsetPagerSnapshotMatchesSinglePageRendering() {
        val state = baseState(mode = LauncherMode.HOME)
        val singlePage = renderer.render(
            state = state,
            screenProfile = screenProfile,
            animationState = LauncherAnimationState(),
        )
        val pagerComposed = renderer.render(
            state = state,
            screenProfile = screenProfile,
            animationState = LauncherAnimationState(),
            pagerSnapshot = HorizontalPageSnapshot(
                anchorPageIndex = 1,
                dragOffsetPx = 0f,
                pageCount = 3,
            ),
        )

        assertBufferEquals(singlePage, pagerComposed)
    }

    @Test
    fun positiveOffsetFromHomeComposesWithSettingsPage() {
        val homeState = baseState(mode = LauncherMode.HOME)
        val homePage = renderMode(LauncherMode.HOME)
        val settingsPage = renderMode(LauncherMode.SETTINGS)
        val expected = HorizontalPageRenderer.compose(
            currentPage = homePage,
            adjacentPage = settingsPage,
            dragOffsetPx = 24f,
            contentStartY = LauncherHeaderLayout.contentTop,
        )
        val pagerComposed = renderer.render(
            state = homeState,
            screenProfile = screenProfile,
            animationState = LauncherAnimationState(),
            pagerSnapshot = HorizontalPageSnapshot(
                anchorPageIndex = 1,
                dragOffsetPx = 24f,
                pageCount = 3,
            ),
        )

        assertBufferEquals(expected, pagerComposed)
    }

    @Test
    fun negativeOffsetFromHomeComposesWithAppsPage() {
        val homeState = baseState(
            mode = LauncherMode.HOME,
            selectedIndex = 8,
            listStartIndex = 8,
        )
        val homePage = renderMode(LauncherMode.HOME)
        val appsPage = renderer.render(
            state = expectedDrawerEntryState(homeState),
            screenProfile = screenProfile,
            animationState = LauncherAnimationState(),
        )
        val expected = HorizontalPageRenderer.compose(
            currentPage = homePage,
            adjacentPage = appsPage,
            dragOffsetPx = -24f,
            contentStartY = LauncherHeaderLayout.contentTop,
        )
        val pagerComposed = renderer.render(
            state = homeState,
            screenProfile = screenProfile,
            animationState = LauncherAnimationState(),
            pagerSnapshot = HorizontalPageSnapshot(
                anchorPageIndex = 1,
                dragOffsetPx = -24f,
                pageCount = 3,
            ),
        )

        assertBufferEquals(expected, pagerComposed)
    }

    @Test
    fun pagerDraggingKeepsHeaderRegionStatic() {
        val homeState = baseState(mode = LauncherMode.HOME)
        val homePage = renderMode(LauncherMode.HOME)
        val pagerComposed = renderer.render(
            state = homeState,
            screenProfile = screenProfile,
            animationState = LauncherAnimationState(),
            pagerSnapshot = HorizontalPageSnapshot(
                anchorPageIndex = 1,
                dragOffsetPx = -24f,
                pageCount = 3,
            ),
        )

        for (y in 0 until LauncherHeaderLayout.contentTop.coerceIn(0, pagerComposed.height)) {
            for (x in 0 until pagerComposed.width) {
                assertTrue(homePage.getPixel(x, y) == pagerComposed.getPixel(x, y))
            }
        }
    }

    private fun renderMode(mode: LauncherMode): PixelBuffer {
        return renderer.render(
            state = baseState(mode = mode),
            screenProfile = screenProfile,
            animationState = LauncherAnimationState(),
        )
    }

    private fun expectedDrawerEntryState(homeState: LauncherState): LauncherState {
        val visibleRows = AppListLayout.metrics(screenProfile).textList.viewport.visibleRows
        return LauncherStateTransitions.showAppDrawer(
            state = LauncherStateTransitions.clearDrawerQuery(
                state = homeState,
                visibleRows = visibleRows,
            ),
            visibleRows = visibleRows,
        ).copy(
            isDrawerSearchFocused = homeState.openDrawerInSearchMode,
            isDrawerRailSliding = false,
        )
    }

    private fun baseState(
        mode: LauncherMode,
        selectedIndex: Int = 3,
        listStartIndex: Int = 0,
    ): LauncherState {
        return LauncherState(
            mode = mode,
            apps = apps,
            drawerVisibleApps = apps,
            drawerQuery = "",
            isDrawerSearchFocused = false,
            selectedIndex = selectedIndex,
            listStartIndex = listStartIndex,
            isLoading = false,
            currentDateText = "MAR 16",
            currentWeekdayText = "MONDAY",
        )
    }

    private fun assertBufferEquals(expected: PixelBuffer, actual: PixelBuffer) {
        assertTrue(expected.width == actual.width)
        assertTrue(expected.height == actual.height)
        assertTrue(expected.pixels.contentEquals(actual.pixels))
    }

    private class BlockGlyphProvider : GlyphProvider {
        override fun rasterizeGlyph(character: Char, style: GlyphStyle): GlyphBitmap {
            val isWideGlyph = character.code !in 32..126
            val width = if (isWideGlyph) style.wideAdvanceWidth else style.narrowAdvanceWidth
            return GlyphBitmap(
                width = width,
                height = style.cellHeight,
                pixels = ByteArray(width * style.cellHeight) { 1 },
                metrics = GlyphMetrics(
                    advanceWidth = width,
                    baselineOffset = style.cellHeight - 2,
                    isWideGlyph = isWideGlyph,
                ),
            )
        }
    }
}
