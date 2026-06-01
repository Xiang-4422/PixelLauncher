package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for [AppDrawerIndexModel] — the drawer's page windowing (which page
 * the selection sits on and the rows shown on it). Pure, JVM-safe.
 */
class AppDrawerIndexModelTest {

    private fun apps(count: Int): List<AppEntry> =
        (0 until count).map { AppEntry(label = "App$it", packageName = "pkg.$it", activityName = "Act$it") }

    @Test
    fun create_emptyAppsYieldsEmptyModel() {
        val model = AppDrawerIndexModel.create(apps = emptyList(), visibleRows = 4, selectedIndex = 0)
        assertEquals(0, model.pageCount)
        assertEquals(emptyList<AppEntry>(), model.currentPageApps)
        assertEquals(0, model.currentPageStartIndex)
    }

    @Test
    fun create_pagesByVisibleRowsAndLocatesSelection() {
        val model = AppDrawerIndexModel.create(apps = apps(10), visibleRows = 4, selectedIndex = 5)
        assertEquals(listOf(0, 4, 8), model.pageStartIndices)
        assertEquals(3, model.pageCount)
        assertEquals(1, model.currentPageIndex)
        assertEquals(4, model.currentPageStartIndex)
        assertEquals(4, model.currentPageApps.size)
        assertEquals(1, model.currentPageSelectedRow) // index 5 on a page starting at 4
    }

    @Test
    fun create_lastPageMayBePartial() {
        val model = AppDrawerIndexModel.create(apps = apps(10), visibleRows = 4, selectedIndex = 9)
        assertEquals(2, model.currentPageIndex)
        assertEquals(8, model.currentPageStartIndex)
        assertEquals(2, model.currentPageApps.size) // apps 8 and 9
        assertEquals(1, model.currentPageSelectedRow)
    }

    @Test
    fun create_nonPositiveRowsFallsBackToOnePerPage() {
        val model = AppDrawerIndexModel.create(apps = apps(3), visibleRows = 0, selectedIndex = 2)
        assertEquals(3, model.pageCount)
        assertEquals(2, model.currentPageIndex)
        assertEquals(1, model.currentPageApps.size)
    }

    @Test
    fun create_clampsOutOfRangeSelection() {
        val model = AppDrawerIndexModel.create(apps = apps(5), visibleRows = 3, selectedIndex = 99)
        assertEquals(1, model.currentPageIndex) // last index 4 -> page 1 (start 3)
        assertEquals(3, model.currentPageStartIndex)
        assertEquals(1, model.currentPageSelectedRow) // index 4 on page starting at 3
    }
}
