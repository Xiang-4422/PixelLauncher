package com.purride.pixellauncherv2.launcher

import com.purride.pixelcore.PixelShape
import com.purride.pixellauncherv2.model.CallLogGroup
import com.purride.pixellauncherv2.model.DeviceStatus
import com.purride.pixellauncherv2.model.LauncherStatsSnapshot
import com.purride.pixellauncherv2.model.SmsMessageEntry
import com.purride.pixellauncherv2.model.SmsThreadSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 补齐状态拆分前缺失的高风险 reducer 行为基线。
 *
 * 每个用例都执行真实的 [LauncherStateTransitions] 转换并检查前后状态；
 * 与既有细粒度用例合并后，100 个公开转换入口均有 JVM 行为测试直接调用。
 */
class LauncherTransitionBaselineTest {

    /**
     * 保护设置子页、短信角色页和贪吃蛇的顶层路由及返回上下文。
     */
    @Test
    fun auxiliaryFlows_keepDocumentedShellRoutes() {
        val settingsState = LauncherState(
            mode = LauncherMode.SETTINGS,
            returnMode = LauncherMode.APP_DRAWER,
        )

        val snakeState = LauncherStateTransitions.showSnake(settingsState)
        assertEquals(LauncherMode.SNAKE, snakeState.mode)
        assertEquals(LauncherMode.APP_DRAWER, snakeState.returnMode)
        assertEquals(LauncherMode.SETTINGS, LauncherStateTransitions.hideSnake(snakeState).mode)

        val moreState = LauncherStateTransitions.showMoreSettings(settingsState)
        assertEquals(LauncherMode.MORE_SETTINGS, moreState.mode)
        assertEquals(LauncherMode.APP_DRAWER, moreState.returnMode)

        val notificationState = LauncherStateTransitions.showNotificationSettings(moreState)
        assertEquals(LauncherMode.NOTIFICATION_SETTINGS, notificationState.mode)
        assertEquals(
            LauncherMode.MORE_SETTINGS,
            LauncherStateTransitions.hideNotificationSettings(notificationState).mode,
        )

        val smsRoleState = LauncherStateTransitions.showSmsRolePrompt(
            LauncherState(mode = LauncherMode.APP_DRAWER),
        )
        assertEquals(LauncherMode.SMS_ROLE_PROMPT, smsRoleState.mode)
        assertEquals(LauncherMode.HOME, smsRoleState.returnMode)
    }

    /**
     * 保护抽屉滚动、翻页与绝对选择时选中项、窗口和页码保持一致。
     */
    @Test
    fun drawerViewportNavigation_keepsSelectionInsideWindow() {
        val initialState = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            drawerVisibleApps = apps(9),
            selectedIndex = 1,
            listStartIndex = 0,
        )

        val scrolledState = LauncherStateTransitions.scrollDrawerWindow(
            state = initialState,
            delta = 3,
            visibleRows = 3,
        )
        assertEquals(3, scrolledState.listStartIndex)
        assertEquals(4, scrolledState.selectedIndex)
        assertEquals(1, scrolledState.drawerPageIndex)

        val pagedState = LauncherStateTransitions.pageSelection(
            state = scrolledState,
            direction = 1,
            visibleRows = 3,
        )
        assertEquals(6, pagedState.selectedIndex)
        assertEquals(6, pagedState.listStartIndex)
        assertEquals(2, pagedState.drawerPageIndex)

        val clampedState = LauncherStateTransitions.selectIndex(
            state = pagedState,
            index = 99,
            visibleRows = 3,
        )
        assertEquals(8, clampedState.selectedIndex)
        assertEquals(6, clampedState.listStartIndex)

        val firstPageState = LauncherStateTransitions.selectDrawerPage(
            state = clampedState,
            pageIndex = -1,
            visibleRows = 3,
        )
        assertEquals(0, firstPageState.selectedIndex)
        assertEquals(0, firstPageState.listStartIndex)
        assertEquals(0, firstPageState.drawerPageIndex)
        assertEquals(DrawerFocus.LIST, firstPageState.drawerFocus)
    }

    /**
     * 保护按包名、字母索引定位以及退出搜索时保留当前应用的规则。
     */
    @Test
    fun drawerIdentityAndSearchExit_preserveFocusedApplication() {
        val catalog = listOf(
            AppEntry(label = "Alpha", packageName = "pkg.alpha", activityName = "AlphaActivity"),
            AppEntry(label = "Beta", packageName = "pkg.beta", activityName = "BetaActivity"),
            AppEntry(label = "Gamma", packageName = "pkg.gamma", activityName = "GammaActivity"),
        )
        val drawerState = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            apps = catalog,
            drawerVisibleApps = catalog,
        )

        val packageSelectedState = LauncherStateTransitions.selectByPackageName(
            state = drawerState,
            packageName = "pkg.beta",
            visibleRows = 2,
        )
        assertEquals(1, packageSelectedState.selectedIndex)
        assertSame(
            packageSelectedState,
            LauncherStateTransitions.selectByPackageName(
                state = packageSelectedState,
                packageName = "pkg.missing",
                visibleRows = 2,
            ),
        )

        val letterSelectedState = LauncherStateTransitions.selectByLetterIndex(
            state = drawerState,
            letterIndex = 1,
            visibleRows = 2,
        )
        assertEquals("pkg.beta", letterSelectedState.drawerVisibleApps[letterSelectedState.selectedIndex].packageName)

        val searchedState = LauncherStateTransitions.updateDrawerQuery(
            state = drawerState,
            query = "beta",
            visibleRows = 2,
        ).copy(
            isDrawerSearchFocused = true,
            isDrawerRailSliding = true,
        )
        val clearedState = LauncherStateTransitions.clearDrawerQuery(searchedState, visibleRows = 2)
        assertEquals("", clearedState.drawerQuery)
        assertEquals(catalog, clearedState.drawerVisibleApps)
        assertEquals(0, clearedState.selectedIndex)

        val exitedState = LauncherStateTransitions.exitDrawerSearch(searchedState, visibleRows = 2)
        assertEquals("", exitedState.drawerQuery)
        assertEquals("pkg.beta", exitedState.drawerVisibleApps[exitedState.selectedIndex].packageName)
        assertFalse(exitedState.isDrawerSearchFocused)
        assertFalse(exitedState.isDrawerRailSliding)
    }

    /**
     * 保护设置动态行数变化后，焦点和窗口始终落在合法范围。
     */
    @Test
    fun settingsViewportNavigation_clampsAgainstDynamicRows() {
        val baseState = LauncherState(mode = LauncherMode.SETTINGS)
        val lastIndex = SettingsMenuModel.rows(baseState).lastIndex

        val selectedState = LauncherStateTransitions.selectSettingsIndex(
            state = baseState,
            index = Int.MAX_VALUE,
            visibleRows = 3,
        )
        assertEquals(lastIndex, selectedState.settingsSelectedIndex)
        assertEquals((lastIndex - 2).coerceAtLeast(0), selectedState.settingsListStartIndex)

        val movedState = LauncherStateTransitions.moveSettingsSelection(
            state = selectedState,
            delta = -1,
            visibleRows = 3,
        )
        assertEquals((lastIndex - 1).coerceAtLeast(0), movedState.settingsSelectedIndex)

        val scrolledState = LauncherStateTransitions.scrollSettingsWindow(
            state = baseState.copy(settingsSelectedIndex = 1),
            delta = 3,
            visibleRows = 3,
        )
        assertEquals(3, scrolledState.settingsListStartIndex)
        assertEquals(4, scrolledState.settingsSelectedIndex)

        val reflowedState = LauncherStateTransitions.reflowSettingsWindow(
            state = baseState.copy(
                settingsSelectedIndex = Int.MAX_VALUE,
                settingsListStartIndex = Int.MAX_VALUE,
            ),
            visibleRows = 3,
        )
        assertEquals(lastIndex, reflowedState.settingsSelectedIndex)
        assertEquals((lastIndex - 2).coerceAtLeast(0), reflowedState.settingsListStartIndex)
    }

    /**
     * 保护未读短信列表选择与窗口在数据和 viewport 变化后仍保持一致。
     */
    @Test
    fun smsInboxViewportNavigation_clampsSelectionAndWindow() {
        val messages = smsMessages(6)
        val baseState = LauncherState(
            unreadSmsEntries = messages,
            smsSelectedIndex = 0,
            smsListStartIndex = 0,
        )

        val selectedState = LauncherStateTransitions.selectSmsIndex(
            state = baseState,
            index = Int.MAX_VALUE,
            visibleRows = 3,
        )
        assertEquals(5, selectedState.smsSelectedIndex)
        assertEquals(3, selectedState.smsListStartIndex)

        val movedState = LauncherStateTransitions.moveSmsSelection(
            state = selectedState,
            delta = -2,
            visibleRows = 3,
        )
        assertEquals(3, movedState.smsSelectedIndex)
        assertEquals(3, movedState.smsListStartIndex)

        val reflowedState = LauncherStateTransitions.reflowSmsWindow(
            state = baseState.copy(
                smsSelectedIndex = Int.MAX_VALUE,
                smsListStartIndex = Int.MAX_VALUE,
            ),
            visibleRows = 3,
        )
        assertEquals(5, reflowedState.smsSelectedIndex)
        assertEquals(3, reflowedState.smsListStartIndex)
    }

    /**
     * 保护短信会话数据回填、选择移动和窗口重排是一致的。
     */
    @Test
    fun smsThreadViewportNavigation_clampsSelectionAndWindow() {
        val threads = smsThreads(6)
        val loadedState = LauncherStateTransitions.updateSmsThreads(
            state = LauncherState(
                smsThreadSelectedIndex = Int.MAX_VALUE,
                smsThreadListStartIndex = Int.MAX_VALUE,
            ),
            threads = threads,
            visibleRows = 3,
        )
        assertEquals(threads, loadedState.smsThreads)
        assertEquals(5, loadedState.smsThreadSelectedIndex)
        assertEquals(3, loadedState.smsThreadListStartIndex)

        val selectedState = LauncherStateTransitions.selectSmsThreadIndex(
            state = loadedState,
            index = 0,
            visibleRows = 3,
        )
        assertEquals(0, selectedState.smsThreadSelectedIndex)
        assertEquals(0, selectedState.smsThreadListStartIndex)

        val movedState = LauncherStateTransitions.moveSmsThreadSelection(
            state = selectedState,
            delta = 2,
            visibleRows = 3,
        )
        assertEquals(2, movedState.smsThreadSelectedIndex)
        assertEquals(0, movedState.smsThreadListStartIndex)

        val reflowedState = LauncherStateTransitions.reflowSmsThreadWindow(
            state = loadedState.copy(
                smsThreadSelectedIndex = Int.MAX_VALUE,
                smsThreadListStartIndex = Int.MAX_VALUE,
            ),
            visibleRows = 3,
        )
        assertEquals(5, reflowedState.smsThreadSelectedIndex)
        assertEquals(3, reflowedState.smsThreadListStartIndex)
    }

    /**
     * 保护异步短信数据落地时会话身份原子更新，且不会覆盖独立的草稿和发送状态。
     */
    @Test
    fun smsConversationRefresh_updatesIdentityWithoutClobberingDraft() {
        val refreshedMessages = smsMessages(2, conversationKey = "person:new")
        val initialState = LauncherState(
            mode = LauncherMode.SMS_THREAD_DETAIL,
            smsCurrentConversationKey = "person:old",
            smsCurrentConversationTitle = "OLD",
            smsCurrentThreadId = 1L,
            smsCurrentAddress = "10000",
            smsDraftText = "new unsent draft",
            smsSendStatus = SmsSendStatus.SENDING,
            isSmsThreadMenuVisible = true,
            smsThreadMenuConversationKey = "person:old",
        )

        val conversationState = LauncherStateTransitions.updateSmsMessages(
            state = initialState,
            conversationKey = "person:new",
            conversationTitle = "NEW",
            isServiceConversation = true,
            threadId = 2L,
            address = "10086",
            messages = refreshedMessages,
        )
        assertEquals("person:new", conversationState.smsCurrentConversationKey)
        assertEquals("NEW", conversationState.smsCurrentConversationTitle)
        assertTrue(conversationState.smsCurrentIsServiceConversation)
        assertEquals(2L, conversationState.smsCurrentThreadId)
        assertEquals("10086", conversationState.smsCurrentAddress)
        assertEquals(refreshedMessages, conversationState.smsMessages)
        assertEquals("new unsent draft", conversationState.smsDraftText)
        assertEquals(SmsSendStatus.SENDING, conversationState.smsSendStatus)

        val aggregateState = LauncherStateTransitions.updateSmsAllMessages(
            state = conversationState,
            messages = refreshedMessages,
        )
        val draftState = LauncherStateTransitions.updateSmsDraftText(
            state = aggregateState,
            smsDraftText = "latest draft",
        )
        val capabilityState = LauncherStateTransitions.updateSmsCapability(
            state = draftState,
            isDefaultSmsApp = true,
            smsPermissionState = SmsPermissionState.READY,
        )
        val menuClosedState = LauncherStateTransitions.hideSmsThreadMenu(capabilityState)

        assertEquals(refreshedMessages, menuClosedState.smsAllMessages)
        assertEquals("latest draft", menuClosedState.smsDraftText)
        assertTrue(menuClosedState.isDefaultSmsApp)
        assertEquals(SmsPermissionState.READY, menuClosedState.smsPermissionState)
        assertFalse(menuClosedState.isSmsThreadMenuVisible)
        assertEquals("", menuClosedState.smsThreadMenuConversationKey)
    }

    /**
     * 保护通话记录加载完成语义，以及联系人编辑草稿的独立更新。
     */
    @Test
    fun phoneAndContactsRefresh_landDataWithoutMixingDrafts() {
        val group = CallLogGroup(
            callId = 8L,
            number = "10086",
            displayTitle = "SERVICE",
            dateMillis = 100L,
            durationSeconds = 20L,
            type = 1,
            callCount = 2,
            hasNew = true,
            callIds = listOf(8L, 7L),
        )
        val callState = LauncherStateTransitions.updateCallLogGroups(
            state = LauncherState(isCallLogLoading = true),
            groups = listOf(group),
        )
        assertEquals(listOf(group), callState.callLogGroups)
        assertFalse(callState.isCallLogLoading)

        val editorState = LauncherState(contactEditorLookupKey = "lookup-1")
        val nameState = LauncherStateTransitions.updateContactEditorName(editorState, "ALICE")
        val numberState = LauncherStateTransitions.updateContactEditorNumber(nameState, "12345")
        assertEquals("lookup-1", numberState.contactEditorLookupKey)
        assertEquals("ALICE", numberState.contactEditorNameDraft)
        assertEquals("12345", numberState.contactEditorNumberDraft)
    }

    /**
     * 保护外观更新的字体归一化，以及字体准备和缓存诊断的独立状态。
     */
    @Test
    fun appearanceAndFontRefresh_normalizeSelectionAndKeepActivationSeparate() {
        val unsupportedSelection = LauncherFontSelection(
            family = LauncherFontFamily("missing-family"),
            widthMode = LauncherFontWidthMode.MONOSPACED,
            size = PixelFontSize(99),
        )
        val appearanceState = LauncherStateTransitions.updateAppearance(
            state = LauncherState(),
            selectedPixelShape = PixelShape.DIAMOND,
            selectedDotSizePx = 7,
            isPixelGapEnabled = false,
            selectedThemeFamily = LauncherThemeFamily.AMBER,
            selectedThemeMode = LauncherThemeMode.NIGHT,
            fontSelection = unsupportedSelection,
        )
        assertEquals(PixelShape.DIAMOND, appearanceState.selectedPixelShape)
        assertEquals(7, appearanceState.selectedDotSizePx)
        assertFalse(appearanceState.isPixelGapEnabled)
        assertEquals(LauncherThemeFamily.AMBER, appearanceState.selectedThemeFamily)
        assertEquals(LauncherThemeMode.NIGHT, appearanceState.selectedThemeMode)
        assertEquals(PixelFontCatalog.defaultUiFontSelection, appearanceState.fontSelection)

        val loadingState = LauncherStateTransitions.updateFontLoading(appearanceState, isLoading = true)
        assertTrue(loadingState.isFontLoading)
        assertEquals(appearanceState.fontSelection, loadingState.fontSelection)

        val emptyCacheState = LauncherStateTransitions.updateFontCacheSummary(loadingState, "   ")
        assertEquals("0/0K", emptyCacheState.fontCacheSummary)
        val cacheState = LauncherStateTransitions.updateFontCacheSummary(emptyCacheState, "  4/32K  ")
        assertEquals("4/32K", cacheState.fontCacheSummary)
    }

    /**
     * 保护 Home 和 System 的独立快照更新不会相互抹除。
     */
    @Test
    fun homeAndSystemRefreshes_preserveIndependentSnapshots() {
        val timeState = LauncherStateTransitions.updateTime(
            state = LauncherState(),
            currentTimeText = "09:41",
            currentDateText = "07-30",
            currentWeekdayText = "THU",
        )
        val deviceState = LauncherStateTransitions.updateDeviceStatus(
            state = timeState,
            deviceStatus = DeviceStatus(batteryLevel = 42, isCharging = true),
        )
        val stats = LauncherStatsSnapshot(
            launchCount = 7,
            recentApps = listOf("pkg.alpha", "pkg.beta"),
            lastLaunchPackageName = "pkg.alpha",
        )
        val statsState = LauncherStateTransitions.updateStats(deviceState, stats)
        val alarmState = LauncherStateTransitions.updateNextAlarmText(statsState, "06:30")
        val communicationState = LauncherStateTransitions.updateCommunicationStatus(
            state = alarmState,
            missedCallCount = -2,
            unreadSmsCount = 5,
        )
        val playback = MediaPlaybackSnapshot(
            isActive = true,
            packageName = "pkg.music",
            title = "TRACK",
            isPlaying = true,
        )
        val mediaState = LauncherStateTransitions.updateMediaPlayback(communicationState, playback)
        val usageState = LauncherStateTransitions.updateScreenUsageSummary(
            state = mediaState,
            screenUsageTimeText = "01:20",
            screenOpenCountText = "12",
        )
        val interactionState = LauncherStateTransitions.recordInteraction(usageState, uptimeMs = 9_999L)

        assertEquals("09:41", interactionState.currentTimeText)
        assertEquals("07-30", interactionState.currentDateText)
        assertEquals("THU", interactionState.currentWeekdayText)
        assertEquals(42, interactionState.batteryLevel)
        assertTrue(interactionState.isCharging)
        assertEquals(stats.recentApps, interactionState.recentApps)
        assertEquals(7, interactionState.launchCount)
        assertEquals("pkg.alpha", interactionState.lastLaunchPackageName)
        assertEquals("06:30", interactionState.nextAlarmText)
        assertEquals(0, interactionState.missedCallCount)
        assertEquals(5, interactionState.unreadSmsCount)
        assertEquals(playback, interactionState.mediaPlayback)
        assertEquals("01:20", interactionState.screenUsageTimeText)
        assertEquals("12", interactionState.screenOpenCountText)
        assertEquals(9_999L, interactionState.lastInteractionUptimeMs)
    }

    /**
     * 保护状态栏 action 与普通消息互斥，且展示文本会在 reducer 边界裁剪。
     */
    @Test
    fun statusBarAction_clearsMessageAndUpdatesAtomicActionFields() {
        val result = LauncherStateTransitions.updateStatusBarAction(
            state = LauncherState(statusBarMessageText = "OLD"),
            leadingText = "  SMS ACCESS  ",
            actionLabel = "  FIX  ",
            isDanger = true,
        )

        assertEquals("", result.statusBarMessageText)
        assertEquals("SMS ACCESS", result.statusBarActionLeadingText)
        assertEquals("FIX", result.statusBarActionLabel)
        assertTrue(result.isStatusBarActionDanger)
    }

    /**
     * 生成稳定的抽屉应用目录。
     */
    private fun apps(count: Int): List<AppEntry> =
        (0 until count).map { index ->
            AppEntry(
                label = "App$index",
                packageName = "pkg.$index",
                activityName = "Activity$index",
            )
        }

    /**
     * 生成指定会话中的短信记录。
     */
    private fun smsMessages(
        count: Int,
        conversationKey: String = "person:test",
    ): List<SmsMessageEntry> =
        (0 until count).map { index ->
            SmsMessageEntry(
                messageId = index.toLong(),
                threadId = 1L,
                address = "10086",
                body = "message-$index",
                dateMillis = index.toLong(),
                type = 1,
                isRead = false,
                conversationKey = conversationKey,
            )
        }

    /**
     * 生成稳定的短信会话摘要。
     */
    private fun smsThreads(count: Int): List<SmsThreadSummary> =
        (0 until count).map { index ->
            SmsThreadSummary(
                threadId = index.toLong(),
                address = "1008$index",
                snippet = "thread-$index",
                dateMillis = index.toLong(),
                unreadCount = index,
                messageCount = index + 1,
                conversationKey = "person:$index",
            )
        }
}
