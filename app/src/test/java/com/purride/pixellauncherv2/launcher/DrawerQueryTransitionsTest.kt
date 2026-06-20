package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for the drawer search/query path of [LauncherStateTransitions]
 * (updateDrawerQuery / appendDrawerQuery / backspaceDrawerQuery), exercising the
 * filter + window reset through to the visible app list.
 */
class DrawerQueryTransitionsTest {

    private val apps = listOf(
        AppEntry(label = "Apple", packageName = "com.apple", activityName = "A"),
        AppEntry(label = "Banana", packageName = "com.banana", activityName = "B"),
        AppEntry(label = "Grape", packageName = "com.grape", activityName = "G"),
    )
    private val state = LauncherState(apps = apps, drawerVisibleApps = apps)

    @Test
    fun updateDrawerQuery_filtersToMatchingApps() {
        val result = LauncherStateTransitions.updateDrawerQuery(state, query = "apple", visibleRows = 5)
        assertEquals("apple", result.drawerQuery)
        val labels = result.drawerVisibleApps.map { it.label }
        assertTrue("Apple should match 'apple'", labels.contains("Apple"))
        assertFalse("Banana should not match 'apple'", labels.contains("Banana"))
        assertEquals(0, result.selectedIndex)
    }

    @Test
    fun updateDrawerQuery_matchesUserAliases() {
        val customizedApps = listOf(
            AppEntry(
                label = "Bank",
                packageName = "com.bank",
                activityName = "BankActivity",
                aliases = listOf("pay bill"),
            ),
            AppEntry(label = "Browser", packageName = "com.browser", activityName = "BrowserActivity"),
        )
        val customizedState = LauncherState(apps = customizedApps, drawerVisibleApps = customizedApps)

        val result = LauncherStateTransitions.updateDrawerQuery(customizedState, query = "pay", visibleRows = 5)

        assertEquals(listOf("Bank"), result.drawerVisibleApps.map { it.label })
        assertEquals(0, result.selectedIndex)
    }

    @Test
    fun updateDrawerQuery_searchesRenamedDisplayLabel() {
        val customizedApps = listOf(
            AppEntry(
                label = "Pay",
                packageName = "com.bank",
                activityName = "BankActivity",
                systemLabel = "Bank",
            ),
            AppEntry(label = "Browser", packageName = "com.browser", activityName = "BrowserActivity"),
        )
        val customizedState = LauncherState(apps = customizedApps, drawerVisibleApps = customizedApps)

        val result = LauncherStateTransitions.updateDrawerQuery(customizedState, query = "pay", visibleRows = 5)

        assertEquals(listOf("Pay"), result.drawerVisibleApps.map { it.label })
    }

    @Test
    fun updateDrawerQuery_blankQueryShowsAllApps() {
        val result = LauncherStateTransitions.updateDrawerQuery(state, query = "", visibleRows = 5)
        assertEquals("", result.drawerQuery)
        assertEquals(3, result.drawerVisibleApps.size)
    }

    @Test
    fun appendThenBackspaceDrawerQuery_roundTrips() {
        val a = LauncherStateTransitions.updateDrawerQuery(state, query = "a", visibleRows = 5)
        val ap = LauncherStateTransitions.appendDrawerQuery(a, text = "p", visibleRows = 5)
        assertEquals("ap", ap.drawerQuery)
        val back = LauncherStateTransitions.backspaceDrawerQuery(ap, visibleRows = 5)
        assertEquals("a", back.drawerQuery)
    }
}
