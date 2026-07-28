package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.model.SmsMessageEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
    fun showHome_clearsDrawerSearchFocus() {
        val state = LauncherState(mode = LauncherMode.APP_DRAWER, isDrawerSearchFocused = true)
        val result = LauncherStateTransitions.showHome(state)

        assertEquals(LauncherMode.HOME, result.mode)
        assertEquals(false, result.isDrawerSearchFocused)
    }

    @Test
    fun showSettings_fromHome_remembersHomeAsReturnMode() {
        val result = LauncherStateTransitions.showSettings(LauncherState(mode = LauncherMode.HOME), visibleRows = 5)
        assertEquals(LauncherMode.SETTINGS, result.mode)
        assertEquals(LauncherMode.HOME, result.returnMode)
    }

    @Test
    fun showSettings_fromDrawer_remembersDrawerAsReturnMode() {
        val result = LauncherStateTransitions.showSettings(
            LauncherState(mode = LauncherMode.APP_DRAWER, isDrawerSearchFocused = true),
            visibleRows = 5,
        )
        assertEquals(LauncherMode.SETTINGS, result.mode)
        assertEquals(LauncherMode.APP_DRAWER, result.returnMode)
        assertEquals(false, result.isDrawerSearchFocused)
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
    fun showIdle_whenEnabledFromDrawer_entersIdleAndRemembersDrawer() {
        val state = LauncherState(mode = LauncherMode.APP_DRAWER, isIdlePageEnabled = true)
        val result = LauncherStateTransitions.showIdle(state)
        assertEquals(LauncherMode.IDLE, result.mode)
        assertEquals(LauncherMode.APP_DRAWER, result.returnMode)
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

    @Test
    fun updateUiBehaviorNormalizesIdleTimeoutSeconds() {
        val result = LauncherStateTransitions.updateUiBehavior(
            state = LauncherState(),
            isIdlePageEnabled = true,
            chargeAutoIdleEnabled = true,
            inactivityAutoIdleEnabled = true,
            idleTimeoutSeconds = 45,
            isPixelMatterEffectEnabled = false,
            pixelMatterEffectMode = PixelMatterEffectMode.SMOKE,
            isPixelMatterHandControlEnabled = true,
            isPixelMatterHandDebugEnabled = false,
        )

        assertEquals(true, result.isIdlePageEnabled)
        assertEquals(true, result.chargeAutoIdleEnabled)
        assertEquals(true, result.inactivityAutoIdleEnabled)
        assertEquals(30, result.idleTimeoutSeconds)
        assertEquals(false, result.isPixelMatterEffectEnabled)
        assertEquals(PixelMatterEffectMode.SMOKE, result.pixelMatterEffectMode)
        assertEquals(true, result.isPixelMatterHandControlEnabled)
        assertEquals(false, result.isPixelMatterHandDebugEnabled)
    }

    // ── Diagnostics + SMS navigation ──────────────────────────────────────────

    @Test
    fun diagnostics_openAndClose() {
        val opened = LauncherStateTransitions.showDiagnostics(LauncherState(mode = LauncherMode.SETTINGS))
        assertEquals(LauncherMode.DIAGNOSTICS, opened.mode)
        assertEquals(LauncherMode.SETTINGS, LauncherStateTransitions.hideDiagnostics(opened).mode)
    }

    @Test
    fun dataHealth_openAndClose() {
        val opened = LauncherStateTransitions.showDataHealth(LauncherState(mode = LauncherMode.SETTINGS))
        assertEquals(LauncherMode.DATA_HEALTH, opened.mode)
        assertEquals(LauncherMode.SETTINGS, LauncherStateTransitions.hideDataHealth(opened).mode)
    }

    @Test
    fun loadingPreview_openAndClose() {
        val opened = LauncherStateTransitions.showLoadingPreview(LauncherState(mode = LauncherMode.SETTINGS))
        assertEquals(LauncherMode.LOADING_PREVIEW, opened.mode)
        assertEquals(LauncherMode.SETTINGS, LauncherStateTransitions.hideLoadingPreview(opened).mode)
    }

    @Test
    fun updateDataHealthPreservesOrWritesRefreshTime() {
        val state = LauncherState(dataHealthUpdatedTimeText = "09:40")

        val preserved = LauncherStateTransitions.updateDataHealth(
            state = state,
            hasUsageAccess = true,
            hasLocationPermission = true,
            hasCallLogPermission = true,
            hasSmsReadPermission = true,
            hasPostNotificationPermission = true,
            hasNotificationListenerAccess = true,
        )
        val updated = LauncherStateTransitions.updateDataHealth(
            state = state,
            hasUsageAccess = true,
            hasLocationPermission = true,
            hasCallLogPermission = true,
            hasSmsReadPermission = true,
            hasPostNotificationPermission = true,
            hasNotificationListenerAccess = true,
            dataHealthUpdatedTimeText = "09:41",
        )

        assertEquals("09:40", preserved.dataHealthUpdatedTimeText)
        assertEquals("09:41", updated.dataHealthUpdatedTimeText)
    }

    @Test
    fun appManagement_openAndClose() {
        val state = LauncherState(
            mode = LauncherMode.SETTINGS,
            apps = listOf(
                AppEntry(label = "Bank", packageName = "pkg.bank", activityName = "BankActivity", aliases = listOf("pay")),
            ),
        )

        val opened = LauncherStateTransitions.showAppManagement(state)

        assertEquals(LauncherMode.APP_MANAGEMENT, opened.mode)
        assertEquals(LauncherMode.SETTINGS, opened.returnMode)
        assertEquals(0, opened.appEditorSelectedIndex)
        assertEquals("Bank", opened.appEditorNameDraft)
        assertEquals("pay", opened.appEditorAliasDraft)
        assertEquals(LauncherMode.SETTINGS, LauncherStateTransitions.hideAppManagement(opened).mode)
    }

    @Test
    fun appManagement_openedFromDrawerReturnsToDrawer() {
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            apps = listOf(
                AppEntry(label = "Bank", packageName = "pkg.bank", activityName = "BankActivity"),
                AppEntry(label = "Maps", packageName = "pkg.maps", activityName = "MapsActivity"),
            ),
        )

        val opened = LauncherStateTransitions.showAppManagement(state, selectedIndex = 1)
        val closed = LauncherStateTransitions.hideAppManagement(opened)

        assertEquals(LauncherMode.APP_MANAGEMENT, opened.mode)
        assertEquals(LauncherMode.APP_DRAWER, opened.returnMode)
        assertEquals(1, opened.appEditorSelectedIndex)
        assertEquals("Maps", opened.appEditorNameDraft)
        assertEquals(LauncherMode.APP_DRAWER, closed.mode)
    }

    @Test
    fun appActionMenu_openedFromDrawerKeepsDrawerModeAndPrefillsSelectedApp() {
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            isDrawerSearchFocused = true,
            isDrawerRailSliding = true,
            apps = listOf(
                AppEntry(label = "Bank", packageName = "pkg.bank", activityName = "BankActivity", aliases = listOf("pay")),
                AppEntry(label = "Maps", packageName = "pkg.maps", activityName = "MapsActivity", aliases = listOf("nav", "road")),
            ),
        )

        val opened = LauncherStateTransitions.showAppActionMenu(state, selectedIndex = 1)
        val closed = LauncherStateTransitions.hideAppActionMenu(opened)

        assertEquals(LauncherMode.APP_DRAWER, opened.mode)
        assertEquals(true, opened.isAppActionMenuVisible)
        assertEquals(false, opened.isDrawerSearchFocused)
        assertEquals(false, opened.isDrawerRailSliding)
        assertEquals(1, opened.appEditorSelectedIndex)
        assertEquals("Maps", opened.appEditorNameDraft)
        assertEquals("nav road", opened.appEditorAliasDraft)
        assertEquals(false, closed.isAppActionMenuVisible)
        assertEquals(LauncherMode.APP_DRAWER, closed.mode)
    }

    @Test
    fun appActionMenu_isClearedWhenOpeningAppManagementOrLeavingDrawer() {
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            isAppActionMenuVisible = true,
            apps = listOf(AppEntry(label = "Bank", packageName = "pkg.bank", activityName = "BankActivity")),
        )

        assertEquals(false, LauncherStateTransitions.showAppManagement(state).isAppActionMenuVisible)
        assertEquals(false, LauncherStateTransitions.showHome(state).isAppActionMenuVisible)
        assertEquals(
            false,
            LauncherStateTransitions.showSettings(state, visibleRows = 5).isAppActionMenuVisible,
        )
    }

    @Test
    fun appManagement_selectionWrapsAndSyncsDrafts() {
        val state = LauncherState(
            mode = LauncherMode.APP_MANAGEMENT,
            apps = listOf(
                AppEntry(label = "Bank", packageName = "pkg.bank", activityName = "BankActivity", aliases = listOf("pay")),
                AppEntry(label = "Maps", packageName = "pkg.maps", activityName = "MapsActivity", aliases = listOf("nav", "road")),
            ),
        )

        val next = LauncherStateTransitions.moveAppEditorSelection(state, direction = 1)
        val previous = LauncherStateTransitions.moveAppEditorSelection(next, direction = -1)

        assertEquals(1, next.appEditorSelectedIndex)
        assertEquals("Maps", next.appEditorNameDraft)
        assertEquals("nav road", next.appEditorAliasDraft)
        assertEquals(0, previous.appEditorSelectedIndex)
        assertEquals("Bank", previous.appEditorNameDraft)
        assertEquals("pay", previous.appEditorAliasDraft)
    }

    @Test
    fun appManagement_emptyListClearsDrafts() {
        val state = LauncherState(
            mode = LauncherMode.APP_MANAGEMENT,
            appEditorSelectedIndex = 3,
            appEditorNameDraft = "old",
            appEditorAliasDraft = "alias",
        )

        val result = LauncherStateTransitions.moveAppEditorSelection(state, direction = 1)

        assertEquals(0, result.appEditorSelectedIndex)
        assertEquals("", result.appEditorNameDraft)
        assertEquals("", result.appEditorAliasDraft)
    }

    @Test
    fun appManagement_updatesDraftsIndependently() {
        val renamed = LauncherStateTransitions.updateAppEditorNameDraft(LauncherState(), "Pay")
        val aliased = LauncherStateTransitions.updateAppEditorAliasDraft(renamed, "bank bill")

        assertEquals("Pay", aliased.appEditorNameDraft)
        assertEquals("bank bill", aliased.appEditorAliasDraft)
    }

    @Test
    fun showSmsThreadDetail_setsThreadIdentityAndReturnMode() {
        val result = LauncherStateTransitions.showSmsThreadDetail(
            state = LauncherState(
                mode = LauncherMode.SMS_THREADS,
                smsMessages = listOf(
                    SmsMessageEntry(
                        messageId = 1L,
                        threadId = 1L,
                        address = "old",
                        body = "OLD",
                        dateMillis = 1L,
                        type = 1,
                        isRead = true,
                    ),
                ),
                smsThreadSearchQuery = "old",
                smsSendStatusText = "FAILED",
            ),
            conversationKey = "service:china-mobile",
            conversationTitle = "China Mobile",
            isServiceConversation = true,
            threadId = 42L,
            address = "10086",
        )
        assertEquals(LauncherMode.SMS_THREAD_DETAIL, result.mode)
        assertEquals("service:china-mobile", result.smsCurrentConversationKey)
        assertEquals("China Mobile", result.smsCurrentConversationTitle)
        assertTrue(result.smsCurrentIsServiceConversation)
        assertEquals(42L, result.smsCurrentThreadId)
        assertEquals("10086", result.smsCurrentAddress)
        assertEquals(LauncherMode.SMS_THREADS, result.returnMode)
        assertEquals("old", result.smsThreadSearchQuery)
        assertEquals("", result.smsSendStatusText)
        assertTrue(result.smsMessages.isEmpty())
    }

    @Test
    fun showSmsMessageMenu_opensOnlyForMessageInCurrentConversation() {
        val state = LauncherState(
            mode = LauncherMode.SMS_THREAD_DETAIL,
            smsMessages = listOf(
                SmsMessageEntry(
                    messageId = 7L,
                    threadId = 1L,
                    address = "10086",
                    body = "BODY",
                    dateMillis = 1L,
                    type = 1,
                    isRead = true,
                ),
            ),
        )

        val shown = LauncherStateTransitions.showSmsMessageMenu(state, messageId = 7L)
        assertTrue(shown.isSmsMessageMenuVisible)
        assertEquals(7L, shown.smsMessageMenuMessageId)

        val missing = LauncherStateTransitions.showSmsMessageMenu(state, messageId = 99L)
        assertFalse(missing.isSmsMessageMenuVisible)
        assertEquals(-1L, missing.smsMessageMenuMessageId)

        val wrongMode = LauncherStateTransitions.showSmsMessageMenu(
            state.copy(mode = LauncherMode.SMS_THREADS),
            messageId = 7L,
        )
        assertFalse(wrongMode.isSmsMessageMenuVisible)
    }

    @Test
    fun hideSmsMessageMenu_resetsMenuState() {
        val state = LauncherState(
            mode = LauncherMode.SMS_THREAD_DETAIL,
            isSmsMessageMenuVisible = true,
            smsMessageMenuMessageId = 7L,
        )

        val result = LauncherStateTransitions.hideSmsMessageMenu(state)
        assertFalse(result.isSmsMessageMenuVisible)
        assertEquals(-1L, result.smsMessageMenuMessageId)
    }

    @Test
    fun hideSmsThreadDetail_dismissesMessageMenu() {
        val state = LauncherState(
            mode = LauncherMode.SMS_THREAD_DETAIL,
            isSmsMessageMenuVisible = true,
            smsMessageMenuMessageId = 7L,
        )

        val result = LauncherStateTransitions.hideSmsThreadDetail(state)
        assertFalse(result.isSmsMessageMenuVisible)
        assertEquals(-1L, result.smsMessageMenuMessageId)
    }

    @Test
    fun hideSmsThreadDetail_returnsToThreadsAndKeepsModuleSearch() {
        val state = LauncherState(
            mode = LauncherMode.SMS_THREAD_DETAIL,
            smsThreadSearchQuery = "code",
            smsDraftText = "unsent",
            smsSendStatusText = "FAILED",
        )
        val result = LauncherStateTransitions.hideSmsThreadDetail(state)
        assertEquals(LauncherMode.SMS_THREADS, result.mode)
        assertEquals("code", result.smsThreadSearchQuery)
        assertEquals("", result.smsDraftText)
        assertEquals("", result.smsSendStatusText)
    }

    @Test
    fun hideSmsThreads_returnsHomeAndClearsDraftStatus() {
        val state = LauncherState(
            mode = LauncherMode.SMS_THREADS,
            smsDraftText = "unsent",
            smsSendStatusText = "FAILED",
        )
        val result = LauncherStateTransitions.hideSmsThreads(state)
        assertEquals(LauncherMode.HOME, result.mode)
        assertEquals("", result.smsDraftText)
        assertEquals("", result.smsSendStatusText)
    }

    @Test
    fun showSmsThreads_usesAllPageWhenNoUnreadMessages() {
        val result = LauncherStateTransitions.showSmsThreads(
            state = LauncherState(smsPageIndex = SmsPageIndex.ALL),
            visibleRows = 4,
        )

        assertEquals(LauncherMode.SMS_THREADS, result.mode)
        assertEquals(SmsPageIndex.ALL, result.smsPageIndex)
    }

    @Test
    fun showSmsThreads_keepsUnreadPageWhileLoadingMessages() {
        val result = LauncherStateTransitions.showSmsThreads(
            state = LauncherState(
                smsPageIndex = SmsPageIndex.ALL,
                isSmsThreadsLoading = true,
            ),
            visibleRows = 4,
        )

        assertEquals(LauncherMode.SMS_THREADS, result.mode)
        assertEquals(SmsPageIndex.UNREAD, result.smsPageIndex)
    }

    @Test
    fun selectSmsPage_coercesToKnownPages() {
        assertEquals(
            SmsPageIndex.UNREAD,
            LauncherStateTransitions.selectSmsPage(
                LauncherState(isSmsThreadsLoading = true),
                -1,
            ).smsPageIndex,
        )
        assertEquals(
            SmsPageIndex.ALL,
            LauncherStateTransitions.selectSmsPage(LauncherState(), 99).smsPageIndex,
        )
    }

    @Test
    fun selectSmsPage_staysAllWhenNoUnreadMessages() {
        assertEquals(
            SmsPageIndex.ALL,
            LauncherStateTransitions.selectSmsPage(LauncherState(smsPageIndex = SmsPageIndex.ALL), SmsPageIndex.UNREAD).smsPageIndex,
        )
    }

    @Test
    fun updateUnreadSmsEntries_switchesToAllWhenNoUnreadMessagesRemain() {
        val result = LauncherStateTransitions.updateUnreadSmsEntries(
            state = LauncherState(
                mode = LauncherMode.SMS_THREADS,
                smsPageIndex = SmsPageIndex.UNREAD,
                isSmsThreadsLoading = false,
            ),
            entries = emptyList(),
            visibleRows = 4,
        )

        assertEquals(SmsPageIndex.ALL, result.smsPageIndex)
    }

    @Test
    fun updateSmsSendStatusText_updatesOnlyDraftStatus() {
        val result = LauncherStateTransitions.updateSmsSendStatusText(
            state = LauncherState(smsDraftText = "hello"),
            smsSendStatusText = "SENDING",
        )

        assertEquals("hello", result.smsDraftText)
        assertEquals("SENDING", result.smsSendStatusText)
    }

    @Test
    fun updateSmsThreadSearchQuery_clampsLongInput() {
        val result = LauncherStateTransitions.updateSmsThreadSearchQuery(
            state = LauncherState(),
            query = "x".repeat(80),
        )

        assertEquals(40, result.smsThreadSearchQuery.length)
    }

    @Test
    fun updateRainHintText_updatesSummaryAndRefreshTime() {
        val result = LauncherStateTransitions.updateRainHintText(
            state = LauncherState(),
            rainHintText = "RAIN 12C",
            rainUpdatedTimeText = "09:41",
        )

        assertEquals("RAIN 12C", result.rainHintText)
        assertEquals("09:41", result.rainUpdatedTimeText)
    }

    @Test
    fun updateNotificationSummary_trimsSummaryAndClampsCount() {
        val result = LauncherStateTransitions.updateNotificationSummary(
            state = LauncherState(),
            notificationSummaryText = "  BANK OTP  ",
            notificationCount = -1,
            notificationSources = listOf(NotificationSourceInfo("bank", "BANK")),
            notificationItems = listOf(NotificationSignal("bank", "BANK", title = "OTP")),
        )

        assertEquals("BANK OTP", result.notificationSummaryText)
        assertEquals(0, result.notificationCount)
        assertEquals(listOf(NotificationSourceInfo("bank", "BANK")), result.notificationSources)
        assertEquals(listOf(NotificationSignal("bank", "BANK", title = "OTP")), result.notificationItems)
    }

    @Test
    fun updateNotificationRulesTrimsSourcesAndLetsMuteWin() {
        val result = LauncherStateTransitions.updateNotificationRules(
            state = LauncherState(),
            mutedSourceIds = setOf(" noisy ", ""),
            prioritySourceIds = setOf("noisy", "bank"),
        )

        assertEquals(setOf("noisy"), result.mutedNotificationSourceIds)
        assertEquals(setOf("bank"), result.priorityNotificationSourceIds)
    }

    @Test
    fun updateStatusBarMessage_trimsGlobalTransientMessage() {
        val result = LauncherStateTransitions.updateStatusBarMessage(
            state = LauncherState(),
            message = "  USE TODAY  ",
        )

        assertEquals("USE TODAY", result.statusBarMessageText)
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
