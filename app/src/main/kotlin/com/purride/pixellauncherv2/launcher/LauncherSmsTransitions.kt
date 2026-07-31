package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.model.SmsMessageEntry
import com.purride.pixellauncherv2.model.SmsThreadSummary

/**
 * SMS 域的纯状态转换（ADR-0001 阶段 2 拆分，来源 LauncherStateTransitions）。
 *
 * 承载短信模块的路由流程、会话/未读列表与窗口、搜索、草稿、菜单、静音与能力快照的
 * 写入。对外入口仍是 [LauncherStateTransitions] facade；行为与拆分前逐字节等价。
 */
object LauncherSmsTransitions {

    /** 打开短信角色引导页。 */
    fun showSmsRolePrompt(state: LauncherState): LauncherState {
        return state.copy(
            mode = LauncherMode.SMS_ROLE_PROMPT,
            returnMode = LauncherMode.HOME,
        )
    }

    /** 打开短信首页。 */
    fun showSmsThreads(
        state: LauncherState,
        visibleRows: Int,
        pageIndex: Int = SmsPageIndex.UNREAD,
    ): LauncherState {
        val requestedPageIndex = SmsPageIndex.coerce(pageIndex)
        val nextPageIndex =
            if (requestedPageIndex == SmsPageIndex.UNREAD &&
                state.unreadSmsEntries.isEmpty() &&
                !state.isSmsThreadsLoading
            ) {
                SmsPageIndex.ALL
            } else {
                requestedPageIndex
            }
        return syncSmsThreadWindow(
            state = state.copy(
                mode = LauncherMode.SMS_THREADS,
                returnMode = LauncherMode.HOME,
                smsPageIndex = nextPageIndex,
                smsThreadSelectedIndex = state.smsThreadSelectedIndex.coerceAtLeast(0),
            ),
            visibleRows = visibleRows,
        )
    }

    /** 关闭短信模块并回到 Home。 */
    fun hideSmsThreads(state: LauncherState): LauncherState {
        return state.copy(
            mode = LauncherMode.HOME,
            smsDraftText = "",
            smsThreadSearchQuery = "",
            smsSendStatus = SmsSendStatus.NONE,
            isSmsThreadMenuVisible = false,
            smsThreadMenuConversationKey = "",
        )
    }

    /** 打开指定短信线程详情页。 */
    fun showSmsThreadDetail(
        state: LauncherState,
        conversationKey: String,
        conversationTitle: String,
        isServiceConversation: Boolean,
        threadId: Long?,
        address: String,
    ): LauncherState {
        return state.copy(
            mode = LauncherMode.SMS_THREAD_DETAIL,
            returnMode = LauncherMode.SMS_THREADS,
            smsCurrentConversationKey = conversationKey,
            smsCurrentConversationTitle = conversationTitle,
            smsCurrentIsServiceConversation = isServiceConversation,
            smsCurrentThreadId = threadId,
            smsCurrentAddress = address,
            smsMessages = emptyList(),
            smsSendStatus = SmsSendStatus.NONE,
            isSmsMessageMenuVisible = false,
            smsMessageMenuMessageId = -1L,
            // 会话列表的菜单标志同样要清：否则从详情页返回列表时菜单会凭空复活。
            isSmsThreadMenuVisible = false,
            smsThreadMenuConversationKey = "",
        )
    }

    /** 打开详情页消息长按浮层菜单；消息不在当前会话时保持关闭。 */
    fun showSmsMessageMenu(state: LauncherState, messageId: Long): LauncherState {
        if (state.mode != LauncherMode.SMS_THREAD_DETAIL ||
            state.smsMessages.none { it.messageId == messageId }
        ) {
            return hideSmsMessageMenu(state)
        }
        return state.copy(
            isSmsMessageMenuVisible = true,
            smsMessageMenuMessageId = messageId,
        )
    }

    /** 关闭详情页消息长按浮层菜单。 */
    fun hideSmsMessageMenu(state: LauncherState): LauncherState {
        return state.copy(
            isSmsMessageMenuVisible = false,
            smsMessageMenuMessageId = -1L,
        )
    }

    /** 打开会话列表长按浮层菜单；会话不存在时保持关闭。 */
    fun showSmsThreadMenu(state: LauncherState, conversationKey: String): LauncherState {
        if (state.mode != LauncherMode.SMS_THREADS ||
            state.smsThreads.none { it.conversationKey == conversationKey }
        ) {
            return hideSmsThreadMenu(state)
        }
        return state.copy(
            isSmsThreadMenuVisible = true,
            smsThreadMenuConversationKey = conversationKey,
        )
    }

    /** 关闭会话列表长按浮层菜单。 */
    fun hideSmsThreadMenu(state: LauncherState): LauncherState {
        return state.copy(
            isSmsThreadMenuVisible = false,
            smsThreadMenuConversationKey = "",
        )
    }

    /** 同步被静音的会话键集合。 */
    fun updateSmsMutedConversations(state: LauncherState, mutedKeys: Set<String>): LauncherState {
        return state.copy(smsMutedConversationKeys = mutedKeys)
    }

    /** 从详情页返回短信会话列表。 */
    fun hideSmsThreadDetail(state: LauncherState): LauncherState {
        return state.copy(
            mode = LauncherMode.SMS_THREADS,
            returnMode = LauncherMode.HOME,
            smsDraftText = "",
            smsSendStatus = SmsSendStatus.NONE,
            isSmsMessageMenuVisible = false,
            smsMessageMenuMessageId = -1L,
            isSmsThreadMenuVisible = false,
            smsThreadMenuConversationKey = "",
        )
    }

    /**
     * 短信能力不足或数据已经落地时结束会话列表加载提示。
     */
    fun finishSmsThreadsLoading(state: LauncherState): LauncherState {
        return state.copy(isSmsThreadsLoading = false)
    }

    /**
     * 开始一次用户明确触发的短信全量刷新。
     *
     * 强制刷新会丢弃三份 provider 派生快照并展示 loading；草稿、菜单和会话身份保持不变。
     */
    fun beginForcedSmsRefresh(state: LauncherState): LauncherState {
        return state.copy(
            unreadSmsEntries = emptyList(),
            smsThreads = emptyList(),
            smsAllMessages = emptyList(),
            isSmsThreadsLoading = true,
        )
    }

    /** 用最新未读短信列表更新短信页状态，并尽量保持当前选中有效。 */
    fun updateUnreadSmsEntries(state: LauncherState, entries: List<SmsMessageEntry>, visibleRows: Int): LauncherState {
        val safeSelectedIndex = state.smsSelectedIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
        val nextPageIndex = if (entries.isEmpty() && !state.isSmsThreadsLoading) SmsPageIndex.ALL else state.smsPageIndex
        return syncSmsWindow(
            state = state.copy(
                unreadSmsEntries = entries,
                smsSelectedIndex = safeSelectedIndex,
                smsPageIndex = nextPageIndex,
            ),
            visibleRows = visibleRows,
        )
    }

    /** 选中短信页中的某一行。 */
    fun selectSmsIndex(state: LauncherState, index: Int, visibleRows: Int): LauncherState {
        val maxIndex = (state.unreadSmsEntries.size - 1).coerceAtLeast(0)
        return syncSmsWindow(
            state = state.copy(smsSelectedIndex = index.coerceIn(0, maxIndex)),
            visibleRows = visibleRows,
        )
    }

    /** 切换短信首页内部 Tab/Page。 */
    fun selectSmsPage(state: LauncherState, index: Int): LauncherState {
        val requestedPageIndex = SmsPageIndex.coerce(index)
        val nextPageIndex =
            if (requestedPageIndex == SmsPageIndex.UNREAD &&
                state.unreadSmsEntries.isEmpty() &&
                !state.isSmsThreadsLoading
            ) {
                SmsPageIndex.ALL
            } else {
                requestedPageIndex
            }
        // 切页后原会话菜单不再有对应上下文（UNREAD 页也没有长按入口），一并关闭。
        return state.copy(
            smsPageIndex = nextPageIndex,
            isSmsThreadMenuVisible = false,
            smsThreadMenuConversationKey = "",
        )
    }

    /** 按相对行数移动短信页内部焦点。 */
    fun moveSmsSelection(state: LauncherState, delta: Int, visibleRows: Int): LauncherState {
        return selectSmsIndex(
            state = state,
            index = state.smsSelectedIndex + delta,
            visibleRows = visibleRows,
        )
    }

    /** 在 viewport 或内容变化后，重新校正短信页的焦点和窗口。 */
    fun reflowSmsWindow(state: LauncherState, visibleRows: Int): LauncherState {
        val maxIndex = (state.unreadSmsEntries.size - 1).coerceAtLeast(0)
        return syncSmsWindow(
            state = state.copy(smsSelectedIndex = state.smsSelectedIndex.coerceIn(0, maxIndex)),
            visibleRows = visibleRows,
        )
    }

    /** 同步默认短信应用角色与短信权限三态。 */
    fun updateSmsCapability(
        state: LauncherState,
        isDefaultSmsApp: Boolean,
        smsPermissionState: SmsPermissionState,
    ): LauncherState {
        return state.copy(
            isDefaultSmsApp = isDefaultSmsApp,
            smsPermissionState = smsPermissionState,
        )
    }

    /** 同步会话列表数据，并保持选中与窗口一致。 */
    fun updateSmsThreads(
        state: LauncherState,
        threads: List<SmsThreadSummary>,
        visibleRows: Int,
    ): LauncherState {
        val safeSelectedIndex = state.smsThreadSelectedIndex.coerceIn(0, (threads.size - 1).coerceAtLeast(0))
        return syncSmsThreadWindow(
            state = state.copy(
                smsThreads = threads,
                smsThreadSelectedIndex = safeSelectedIndex,
            ),
            visibleRows = visibleRows,
        )
    }

    /** 选中会话列表中的某一行。 */
    fun selectSmsThreadIndex(state: LauncherState, index: Int, visibleRows: Int): LauncherState {
        val maxIndex = (state.smsThreads.size - 1).coerceAtLeast(0)
        return syncSmsThreadWindow(
            state = state.copy(smsThreadSelectedIndex = index.coerceIn(0, maxIndex)),
            visibleRows = visibleRows,
        )
    }

    /** 按相对行数移动会话列表焦点。 */
    fun moveSmsThreadSelection(state: LauncherState, delta: Int, visibleRows: Int): LauncherState {
        return selectSmsThreadIndex(
            state = state,
            index = state.smsThreadSelectedIndex + delta,
            visibleRows = visibleRows,
        )
    }

    /**
     * 在短信搜索结果中按相对行数移动焦点。
     *
     * 搜索未开启时保持原状态；空结果也把下标规范为零，且不改动普通会话列表窗口。
     */
    fun moveSmsSearchSelection(
        state: LauncherState,
        delta: Int,
        resultCount: Int,
    ): LauncherState {
        if (state.smsThreadSearchQuery.isBlank()) {
            return state
        }
        val maxIndex = (resultCount - 1).coerceAtLeast(0)
        return state.copy(
            smsThreadSelectedIndex = (state.smsThreadSelectedIndex + delta).coerceIn(0, maxIndex),
        )
    }

    /** 在 viewport 或内容变化后，重新校正会话列表的焦点和窗口。 */
    fun reflowSmsThreadWindow(state: LauncherState, visibleRows: Int): LauncherState {
        val maxIndex = (state.smsThreads.size - 1).coerceAtLeast(0)
        return syncSmsThreadWindow(
            state = state.copy(
                smsThreadSelectedIndex = state.smsThreadSelectedIndex.coerceIn(0, maxIndex),
            ),
            visibleRows = visibleRows,
        )
    }

    /** 原子写入当前会话身份与详情消息列表；草稿与发送态保持不变。 */
    fun updateSmsMessages(
        state: LauncherState,
        conversationKey: String = state.smsCurrentConversationKey,
        conversationTitle: String = state.smsCurrentConversationTitle,
        isServiceConversation: Boolean = state.smsCurrentIsServiceConversation,
        threadId: Long?,
        address: String,
        messages: List<SmsMessageEntry>,
    ): LauncherState {
        return state.copy(
            smsCurrentConversationKey = conversationKey,
            smsCurrentConversationTitle = conversationTitle,
            smsCurrentIsServiceConversation = isServiceConversation,
            smsCurrentThreadId = threadId,
            smsCurrentAddress = address,
            smsMessages = messages,
        )
    }

    /** 同步全量消息快照（搜索数据源）。 */
    fun updateSmsAllMessages(
        state: LauncherState,
        messages: List<SmsMessageEntry>,
    ): LauncherState {
        return state.copy(smsAllMessages = messages)
    }

    /** 更新当前会话草稿文本。 */
    fun updateSmsDraftText(
        state: LauncherState,
        smsDraftText: String,
    ): LauncherState {
        return state.copy(smsDraftText = smsDraftText)
    }

    /** 更新会话搜索词，并重置会话选中与窗口。 */
    fun updateSmsThreadSearchQuery(
        state: LauncherState,
        query: String,
    ): LauncherState {
        return state.copy(
            smsThreadSearchQuery = query.take(MAX_SMS_SEARCH_QUERY_LENGTH),
            smsThreadSelectedIndex = 0,
            smsThreadListStartIndex = 0,
        )
    }

    /** 更新发送状态，不触碰草稿。 */
    fun updateSmsSendStatus(
        state: LauncherState,
        smsSendStatus: SmsSendStatus,
    ): LauncherState {
        return state.copy(smsSendStatus = smsSendStatus)
    }

    private fun syncSmsWindow(state: LauncherState, visibleRows: Int): LauncherState {
        val rows = state.unreadSmsEntries
        if (rows.isEmpty()) {
            return state.copy(
                smsSelectedIndex = 0,
                smsListStartIndex = 0,
            )
        }

        val safeVisibleRows = visibleRows.coerceAtLeast(1)
        val safeSelectedIndex = state.smsSelectedIndex.coerceIn(0, rows.lastIndex)
        val maxStartIndex = (rows.size - safeVisibleRows).coerceAtLeast(0)
        val safeListStartIndex = state.smsListStartIndex.coerceIn(0, maxStartIndex)
        val nextListStartIndex = when {
            safeSelectedIndex < safeListStartIndex -> safeSelectedIndex
            safeSelectedIndex >= safeListStartIndex + safeVisibleRows -> {
                (safeSelectedIndex - safeVisibleRows + 1).coerceIn(0, maxStartIndex)
            }
            else -> safeListStartIndex
        }

        return state.copy(
            smsSelectedIndex = safeSelectedIndex,
            smsListStartIndex = nextListStartIndex,
        )
    }

    private fun syncSmsThreadWindow(state: LauncherState, visibleRows: Int): LauncherState {
        val rows = state.smsThreads
        if (rows.isEmpty()) {
            return state.copy(
                smsThreadSelectedIndex = 0,
                smsThreadListStartIndex = 0,
            )
        }

        val safeVisibleRows = visibleRows.coerceAtLeast(1)
        val safeSelectedIndex = state.smsThreadSelectedIndex.coerceIn(0, rows.lastIndex)
        val maxStartIndex = (rows.size - safeVisibleRows).coerceAtLeast(0)
        val safeListStartIndex = state.smsThreadListStartIndex.coerceIn(0, maxStartIndex)
        val nextListStartIndex = when {
            safeSelectedIndex < safeListStartIndex -> safeSelectedIndex
            safeSelectedIndex >= safeListStartIndex + safeVisibleRows -> {
                (safeSelectedIndex - safeVisibleRows + 1).coerceIn(0, maxStartIndex)
            }
            else -> safeListStartIndex
        }

        return state.copy(
            smsThreadSelectedIndex = safeSelectedIndex,
            smsThreadListStartIndex = nextListStartIndex,
        )
    }

    private const val MAX_SMS_SEARCH_QUERY_LENGTH: Int = 40
}
