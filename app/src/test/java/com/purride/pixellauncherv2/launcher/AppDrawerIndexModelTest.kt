package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDrawerIndexModelTest {

    private val apps = List(11) { index ->
        AppEntry(
            label = "App $index",
            packageName = "pkg.$index",
            activityName = "Activity$index",
        )
    }

    @Test
    fun createBuildsPageAnchorsFromVisibleRows() {
        val model = AppDrawerIndexModel.create(
            apps = apps,
            visibleRows = 4,
            selectedIndex = 5,
        )

        assertEquals(listOf(0, 4, 8), model.pageStartIndices)
        assertEquals(1, model.currentPageIndex)
        assertEquals(4, model.currentPageApps.size)
        assertEquals(1, model.currentPageSelectedRow)
    }

    @Test
    fun createHandlesEmptyApps() {
        val model = AppDrawerIndexModel.create(
            apps = emptyList(),
            visibleRows = 4,
            selectedIndex = 0,
        )

        assertEquals(emptyList<Int>(), model.pageStartIndices)
        assertEquals(0, model.currentPageIndex)
        assertEquals(emptyList<AppEntry>(), model.currentPageApps)
        assertEquals(0, model.currentPageSelectedRow)
    }
}
