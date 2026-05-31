package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pure-function coverage for [LauncherStateTransitions].
 *
 * These reducers take a [LauncherState] (+ inputs) and return a new state; they
 * have no Android dependencies, so they run on a plain JVM. The suite focuses on
 * the deterministic, behaviour-defining logic: mode/returnMode navigation, the
 * viewport start-index maths, and selection clamping.
 */
class LauncherStateTransitionsTest {

    private fun apps(count: Int): List<AppEntry> =
        (0 until count).map { AppEntry(label = "App$it", packageName = "pkg.$it", activityName = "Act$it") }

    // ── calculateListStartIndex ───────────────────────────────────────────────

    @Test
    fun calculateListStartIndex_emptyListStartsAtZero() {
        assertEquals(0, LauncherStateTransitions.calculateListStartIndex(selectedIndex = 2, visibleRows = 5, totalCount = 0))
    }

    @Test
    fun calculateListStartIndex_selectionInsideFirstWindowKeepsSelection() {
        assertEquals(3, LauncherStateTransitions.calculateListStartIndex(selectedIndex = 3, visibleRows = 5, totalCount = 10))
    }

    @Test
    fun calculateListStartIndex_selectionNearEndClampsToMaxStart() {
        // maxStart = totalCount - rows = 10 - 5 = 5
        assertEquals(5, LauncherStateTransitions.calculateListStartIndex(selectedIndex = 9, visibleRows = 5, totalCount = 10))
    }

    @Test
    fun calculateListStartIndex_everythingFitsStartsAtZero() {
        assertEquals(0, LauncherStateTransitions.calculateListStartIndex(selectedIndex = 2, visibleRows = 5, totalCount = 3))
    }

    @Test
    fun calculateListStartIndex_negativeSelectionCoercedToZero() {
        assertEquals(0, LauncherStateTransitions.calculateListStartIndex(selectedIndex = -4, visibleRows = 5, totalCount = 10))
    }

    @Test
    fun calculateListStartIndex_nonPositiveRowsBehavesAsSingleRow() {
        // safeRows = 1 -> maxStart = 9, min(7, 9) = 7
        assertEquals(7, LauncherStateTransitions.calculateListStartIndex(selectedIndex = 7, visibleRows = 0, totalCount = 10))
    }

    @Test
    fun calculateListStartIndex_selectionBeyondTotalClampsToMaxStart() {
        assertEquals(5, LauncherStateTransitions.calculateListStartIndex(selectedIndex = 100, visibleRows = 5, totalCount = 10))
    }

    // ── Settings navigation + returnMode ──────────────────────────────────────

    @Test
    fun showHome_switchesToHome() {
        val result = LauncherStateTransitions.showHome(LauncherState(mode = LauncherMode.SETTINGS))
        assertEquals(LauncherMode.HOME, result.mode)
    }

    @Test
    fun showSettings_fromHome_remembersHomeAsReturnMode() {
        val result = LauncherStateTransitions.showSettings(LauncherState(mode = LauncherMode.HOME), visibleRows = 5)
        assertEquals(LauncherMode.SETTINGS, result.mode)
        assertEquals(LauncherMode.HOME, result.returnMode)
    }

    @Test
    fun showSettings_fromDrawer_remembersDrawerAsReturnMode() {
        val result = LauncherStateTransitions.showSettings(LauncherState(mode = LauncherMode.APP_DRAWER), visibleRows = 5)
        assertEquals(LauncherMode.SETTINGS, result.mode)
        assertEquals(LauncherMode.APP_DRAWER, result.returnMode)
    }

    @Test
    fun showSettings_whenAlreadyInSettings_preservesExistingReturnMode() {
        val state = LauncherState(mode = LauncherMode.SETTINGS, returnMode = LauncherMode.APP_DRAWER)
        val result = LauncherStateTransitions.showSettings(state, visibleRows = 5)
        assertEquals(LauncherMode.APP_DRAWER, result.returnMode)
    }

    @Test
    fun hideSettings_returnsToRememberedMode() {
        val state = LauncherState(mode = LauncherMode.SETTINGS, returnMode = LauncherMode.APP_DRAWER)
        val result = LauncherStateTransitions.hideSettings(state)
        assertEquals(LauncherMode.APP_DRAWER, result.mode)
        assertEquals(LauncherMode.APP_DRAWER, result.returnMode)
    }

    @Test
    fun hideSettings_fallsBackToHomeWhenReturnModeIsNonReturnable() {
        val state = LauncherState(mode = LauncherMode.SETTINGS, returnMode = LauncherMode.DIAGNOSTICS)
        val result = LauncherStateTransitions.hideSettings(state)
        assertEquals(LauncherMode.HOME, result.mode)
    }

    // ── Idle guard + return ───────────────────────────────────────────────────

    @Test
    fun showIdle_whenDisabled_leavesStateUntouched() {
        val state = LauncherState(mode = LauncherMode.HOME, isIdlePageEnabled = false)
        assertSame(state, LauncherStateTransitions.showIdle(state))
    }

    @Test
    fun showIdle_whenEnabledFromHome_entersIdleAndRemembersHome() {
        val state = LauncherState(mode = LauncherMode.HOME, isIdlePageEnabled = true)
        val result = LauncherStateTransitions.showIdle(state)
        assertEquals(LauncherMode.IDLE, result.mode)
        assertEquals(LauncherMode.HOME, result.returnMode)
    }

    @Test
    fun showIdle_whenEnabledButNotOnHomeOrDrawer_isBlocked() {
        val state = LauncherState(mode = LauncherMode.SETTINGS, isIdlePageEnabled = true)
        assertSame(state, LauncherStateTransitions.showIdle(state))
    }

    @Test
    fun hideIdle_returnsToModeBeforeIdle() {
        val state = LauncherState(mode = LauncherMode.IDLE, returnMode = LauncherMode.APP_DRAWER)
        assertEquals(LauncherMode.APP_DRAWER, LauncherStateTransitions.hideIdle(state).mode)
    }

    // ── Diagnostics + SMS navigation ──────────────────────────────────────────

    @Test
    fun diagnostics_openAndClose() {
        val opened = LauncherStateTransitions.showDiagnostics(LauncherState(mode = LauncherMode.SETTINGS))
        assertEquals(LauncherMode.DIAGNOSTICS, opened.mode)
        assertEquals(LauncherMode.SETTINGS, LauncherStateTransitions.hideDiagnostics(opened).mode)
    }

    @Test
    fun showSmsThreadDetail_setsThreadIdentityAndReturnMode() {
        val result = LauncherStateTransitions.showSmsThreadDetail(
            state = LauncherState(mode = LauncherMode.SMS_THREADS),
            threadId = 42L,
            address = "10086",
        )
        assertEquals(LauncherMode.SMS_THREAD_DETAIL, result.mode)
        assertEquals(42L, result.smsCurrentThreadId)
        assertEquals("10086", result.smsCurrentAddress)
        assertEquals(LauncherMode.SMS_THREADS, result.returnMode)
    }

    @Test
    fun hideSmsThreadDetail_returnsToThreadsAndClearsDraft() {
        val state = LauncherState(mode = LauncherMode.SMS_THREAD_DETAIL, smsDraftText = "unsent")
        val result = LauncherStateTransitions.hideSmsThreadDetail(state)
        assertEquals(LauncherMode.SMS_THREADS, result.mode)
        assertEquals("", result.smsDraftText)
    }

    @Test
    fun hideSmsThreads_returnsHomeAndClearsDraft() {
        val state = LauncherState(mode = LauncherMode.SMS_THREADS, smsDraftText = "unsent")
        val result = LauncherStateTransitions.hideSmsThreads(state)
        assertEquals(LauncherMode.HOME, result.mode)
        assertEquals("", result.smsDraftText)
    }

    // ── Selection clamping ────────────────────────────────────────────────────

    @Test
    fun moveSelection_onEmptyDrawer_resetsToZero() {
        val state = LauncherState(selectedIndex = 4, listStartIndex = 2)
        val result = LauncherStateTransitions.moveSelection(state, delta = 3, visibleRows = 5)
        assertEquals(0, result.selectedIndex)
        assertEquals(0, result.listStartIndex)
    }

    @Test
    fun moveSelection_movesWithinBounds() {
        val state = LauncherState(drawerVisibleApps = apps(10), selectedIndex = 0)
        val result = LauncherStateTransitions.moveSelection(state, delta = 3, visibleRows = 5)
        assertEquals(3, result.selectedIndex)
    }

    @Test
    fun moveSelection_clampsAtLastIndex() {
        val state = LauncherState(drawerVisibleApps = apps(10), selectedIndex = 8)
        val result = LauncherStateTransitions.moveSelection(state, delta = 5, visibleRows = 5)
        assertEquals(9, result.selectedIndex)
    }

    @Test
    fun moveSelection_clampsAtFirstIndex() {
        val state = LauncherState(drawerVisibleApps = apps(10), selectedIndex = 1)
        val result = LauncherStateTransitions.moveSelection(state, delta = -5, visibleRows = 5)
        assertEquals(0, result.selectedIndex)
    }

    @Test
    fun reflowWindow_onEmptyDrawer_resetsIndices() {
        val state = LauncherState(selectedIndex = 7, listStartIndex = 3)
        val result = LauncherStateTransitions.reflowWindow(state, visibleRows = 5)
        assertEquals(0, result.selectedIndex)
        assertEquals(0, result.listStartIndex)
    }

    @Test
    fun reflowWindow_clampsOutOfRangeSelectionToLastIndex() {
        val state = LauncherState(drawerVisibleApps = apps(5), selectedIndex = 99)
        val result = LauncherStateTransitions.reflowWindow(state, visibleRows = 5)
        assertEquals(4, result.selectedIndex)
    }
}
