package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.model.CallLogGroup
import com.purride.pixellauncherv2.model.SmsMessageEntry
import com.purride.pixellauncherv2.model.SmsThreadSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * reducer 外整态写入对应的具名转换契约。
 *
 * 每个用例都从含非默认哨兵值的状态出发，并用完整 data class 相等性锁定精确写入面。
 */
class LauncherStateWriteTransitionsTest {

    /**
     * 构造跨领域非默认哨兵，确保被测转换不会顺带覆盖无关字段。
     */
    private fun sentinelState(): LauncherState = LauncherState(
        drawerQuery = "sentinel-query",
        isDrawerRailSliding = true,
        isAppActionMenuVisible = true,
        isLoading = false,
        currentTimeText = "23:59",
        currentDateText = "2099-12-31",
        mode = LauncherMode.SETTINGS,
        returnMode = LauncherMode.IDLE,
        settingsSelectedIndex = 3,
        smsThreadSelectedIndex = 2,
        smsThreadListStartIndex = 1,
        smsDraftText = "sentinel-draft",
        smsSendStatus = SmsSendStatus.FAILED,
        dialInput = "10086",
        contactEditorNameDraft = "sentinel-contact",
        lastInteractionUptimeMs = 9876L,
        statusBarMessageText = "sentinel-message",
        dataHealthUpdatedTimeText = "22:22",
    )

    /** 构造强制刷新前的短信 provider 快照。 */
    private fun smsMessage(messageId: Long = 11L): SmsMessageEntry = SmsMessageEntry(
        messageId = messageId,
        threadId = 7L,
        address = "10086",
        body = "sentinel-body",
        dateMillis = 1234L,
        type = 1,
        isRead = false,
    )

    /** 构造强制刷新前的短信会话摘要。 */
    private fun smsThread(): SmsThreadSummary = SmsThreadSummary(
        threadId = 7L,
        address = "10086",
        snippet = "sentinel-snippet",
        dateMillis = 1234L,
        unreadCount = 1,
        messageCount = 2,
    )

    /** 构造通话记录缓存哨兵。 */
    private fun callLogGroup(): CallLogGroup = CallLogGroup(
        callId = 9L,
        number = "10010",
        displayTitle = "SERVICE",
        dateMillis = 4321L,
        durationSeconds = 20L,
        type = 1,
        callCount = 2,
        hasNew = true,
        callIds = listOf(9L, 8L),
    )

    /** 应用目录首载只打开目录 loading。 */
    @Test
    fun beginAppCatalogLoading_updatesOnlyCatalogLoadingFlag() {
        val before = sentinelState()

        val actual = LauncherStateTransitions.beginAppCatalogLoading(before)

        assertEquals(before.copy(isLoading = true), actual)
    }

    /** Drawer 入场只同步搜索焦点并结束 Rail 滑动。 */
    @Test
    fun prepareDrawerEntryFocus_updatesOnlyEntryFocusAndRail() {
        val before = sentinelState().copy(
            mode = LauncherMode.APP_DRAWER,
            isDrawerSearchFocused = false,
            isDrawerRailSliding = true,
        )

        val actual = LauncherStateTransitions.prepareDrawerEntryFocus(
            state = before,
            focusSearch = true,
        )
        val unfocusedEntry = LauncherStateTransitions.prepareDrawerEntryFocus(
            state = before.copy(isDrawerSearchFocused = true),
            focusSearch = false,
        )
        val hiddenPage = LauncherStateTransitions.prepareDrawerEntryFocus(
            state = before.copy(mode = LauncherMode.HOME),
            focusSearch = true,
        )

        assertEquals(
            before.copy(
                isDrawerSearchFocused = true,
                isDrawerRailSliding = false,
            ),
            actual,
        )
        assertEquals(
            before.copy(
                isDrawerSearchFocused = false,
                isDrawerRailSliding = false,
            ),
            unfocusedEntry,
        )
        assertEquals(before.copy(mode = LauncherMode.HOME), hiddenPage)
    }

    /** Drawer 搜索接管焦点时不改变 query、选择或浮层。 */
    @Test
    fun focusDrawerSearchInput_requiresVisibleDrawerAndPreservesOtherFields() {
        val before = sentinelState().copy(
            mode = LauncherMode.APP_DRAWER,
            isDrawerSearchFocused = false,
            isDrawerRailSliding = true,
        )

        val actual = LauncherStateTransitions.focusDrawerSearchInput(before)
        val hiddenPage = LauncherStateTransitions.focusDrawerSearchInput(
            before.copy(mode = LauncherMode.SETTINGS),
        )

        assertEquals(
            before.copy(
                isDrawerSearchFocused = true,
                isDrawerRailSliding = false,
            ),
            actual,
        )
        assertEquals(before.copy(mode = LauncherMode.SETTINGS), hiddenPage)
    }

    /** Pager 拖动只关闭 Drawer 搜索焦点和应用菜单。 */
    @Test
    fun dismissDrawerOverlaysForPagerDrag_closesOnlyFocusAndMenu() {
        val before = sentinelState().copy(
            mode = LauncherMode.APP_DRAWER,
            isDrawerSearchFocused = true,
            isDrawerRailSliding = true,
            isAppActionMenuVisible = true,
        )

        val actual = LauncherStateTransitions.dismissDrawerOverlaysForPagerDrag(before)

        assertEquals(
            before.copy(
                isDrawerSearchFocused = false,
                isAppActionMenuVisible = false,
            ),
            actual,
        )
    }

    /** 短信能力不足或数据落地后只结束会话列表 loading。 */
    @Test
    fun finishSmsThreadsLoading_updatesOnlySmsLoadingFlag() {
        val before = sentinelState().copy(isSmsThreadsLoading = true)

        val actual = LauncherStateTransitions.finishSmsThreadsLoading(before)

        assertEquals(before.copy(isSmsThreadsLoading = false), actual)
    }

    /** 强制短信刷新只清三份 provider 快照并打开 loading。 */
    @Test
    fun beginForcedSmsRefresh_resetsProviderSnapshotsAndPreservesSessionState() {
        val message = smsMessage()
        val before = sentinelState().copy(
            unreadSmsEntries = listOf(message),
            smsThreads = listOf(smsThread()),
            smsAllMessages = listOf(message),
            isSmsThreadsLoading = false,
            isSmsThreadMenuVisible = true,
            smsThreadMenuConversationKey = "thread:7",
        )

        val actual = LauncherStateTransitions.beginForcedSmsRefresh(before)

        assertEquals(
            before.copy(
                unreadSmsEntries = emptyList(),
                smsThreads = emptyList(),
                smsAllMessages = emptyList(),
                isSmsThreadsLoading = true,
            ),
            actual,
        )
    }

    /** 短信搜索选择按结果数钳制，未搜索时不触碰普通会话选择。 */
    @Test
    fun moveSmsSearchSelection_clampsAgainstResultCountAndRequiresQuery() {
        val before = sentinelState().copy(
            smsThreadSearchQuery = "service",
            smsThreadSelectedIndex = 2,
            smsThreadListStartIndex = 1,
        )

        val clampedToEnd = LauncherStateTransitions.moveSmsSearchSelection(
            state = before,
            delta = 10,
            resultCount = 4,
        )
        val emptyResult = LauncherStateTransitions.moveSmsSearchSelection(
            state = before,
            delta = -1,
            resultCount = 0,
        )
        val inactiveSearch = LauncherStateTransitions.moveSmsSearchSelection(
            state = before.copy(smsThreadSearchQuery = ""),
            delta = 1,
            resultCount = 4,
        )

        assertEquals(before.copy(smsThreadSelectedIndex = 3), clampedToEnd)
        assertEquals(before.copy(smsThreadSelectedIndex = 0), emptyResult)
        assertEquals(before.copy(smsThreadSearchQuery = ""), inactiveSearch)
    }

    /** Call Log 只在可读取且无缓存时展示首次 loading。 */
    @Test
    fun prepareCallLogLoading_requiresPermissionAndEmptyCache() {
        val emptyCache = sentinelState().copy(
            callLogGroups = emptyList(),
            isCallLogLoading = false,
        )
        val cached = emptyCache.copy(
            callLogGroups = listOf(callLogGroup()),
            isCallLogLoading = true,
        )

        val loading = LauncherStateTransitions.prepareCallLogLoading(
            state = emptyCache,
            canReadCallLog = true,
        )
        val denied = LauncherStateTransitions.prepareCallLogLoading(
            state = emptyCache.copy(isCallLogLoading = true),
            canReadCallLog = false,
        )
        val silentRefresh = LauncherStateTransitions.prepareCallLogLoading(
            state = cached,
            canReadCallLog = true,
        )

        assertEquals(emptyCache.copy(isCallLogLoading = true), loading)
        assertEquals(emptyCache.copy(isCallLogLoading = false), denied)
        assertEquals(cached.copy(isCallLogLoading = false), silentRefresh)
    }
}
