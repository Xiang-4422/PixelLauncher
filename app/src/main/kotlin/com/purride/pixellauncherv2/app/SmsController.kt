package com.purride.pixellauncherv2.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import com.purride.pixellauncherv2.data.SmsRepository
import com.purride.pixellauncherv2.data.SmsSendRequest
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.launcher.LauncherStateTransitions
import com.purride.pixellauncherv2.launcher.SmsConversationModel
import com.purride.pixellauncherv2.launcher.SmsPageIndex
import com.purride.pixellauncherv2.launcher.SmsPermissionState
import com.purride.pixellauncherv2.launcher.SmsThreadSearchModel
import com.purride.pixellauncherv2.launcher.SmsVerificationCodeModel
import java.util.concurrent.ExecutorService

/**
 * 短信模块的运行时编排，从 [MainActivity] 抽出。
 *
 * 持有两个短信仓库、会话级标记，并负责短信各页面的打开/关闭/刷新/发送/角色与权限申请。
 * 纯状态转移仍由 [LauncherStateTransitions] 提供；本类只承担命令式胶水（仓库调用、线程、
 * 角色与权限申请、Intent）。与宿主（Activity）的耦合通过 [Host] 收敛：state 读写、重渲染、
 * 焦点/待机、可视行数、通信状态刷新、权限与角色请求、系统 Intent 启动。
 */
internal class SmsController(
    context: Context,
    private val backgroundExecutor: ExecutorService,
    private val mainHandler: Handler,
    private val host: Host,
) {

    /** 宿主（[MainActivity]）需要提供的钩子。 */
    interface Host {
        /** 共享的 Launcher 状态；短信编排读写它，宿主持有真值。 */
        var state: LauncherState

        /** 把当前状态提交到 pixel-engine 渲染。 */
        fun render()

        /** Activity 仍存活（未销毁/未结束）时为 true，用于异步回调的有效性校验。 */
        fun isActive(): Boolean

        /** 短信会话列表的可视行数（依赖屏幕档位）。 */
        fun smsThreadsVisibleRows(): Int

        /** 未读短信收件箱的可视行数（依赖屏幕档位）。 */
        fun smsInboxVisibleRows(): Int

        fun updateTextInputFocus()
        fun scheduleIdleCheck()

        /** 刷新未接来电/未读短信计数（短信发送/已读后需要同步）。 */
        fun refreshCommunicationStatus(render: Boolean)

        /** 申请短信相关运行时权限（READ/SEND/RECEIVE_SMS）。 */
        fun requestSmsPermissions(permissions: Array<String>)

        /** 发起默认短信应用角色申请。 */
        fun startSmsRoleRequest(intent: Intent)

    }

    private val smsRepository = SmsRepository(context)
    private val appContext = context.applicationContext

    private var smsRolePromptDismissedThisSession = false

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** 前台时开始监听短信内容变化。 */
    fun start() {
        smsRepository.start(::onSmsProviderChanged)
    }

    /** 后台时停止监听。 */
    fun stop() {
        smsRepository.stop()
    }

    // ── LauncherCallbacks entry points ────────────────────────────────────────

    fun openThread(conversationKey: String) {
        openSmsConversation(conversationKey)
    }

    fun selectIndex(index: Int) {
        host.state = host.state.copy(smsSelectedIndex = index)
    }

    fun selectPage(index: Int) {
        val nextState = LauncherStateTransitions.selectSmsPage(host.state, index)
        if (nextState.smsPageIndex == host.state.smsPageIndex) {
            return
        }
        host.state = nextState
        host.render()
        host.updateTextInputFocus()
    }

    fun draftChanged(text: String) {
        host.state = LauncherStateTransitions.updateSmsSendStatusText(
            state = LauncherStateTransitions.updateSmsDraftText(state = host.state, smsDraftText = text),
            smsSendStatusText = "",
        )
    }

    fun threadSearchChanged(text: String) {
        host.state = LauncherStateTransitions.updateSmsThreadSearchQuery(
            state = host.state,
            query = text,
        )
        host.render()
    }

    // ── Module open/close ─────────────────────────────────────────────────────

    fun openModule(
        forceRefresh: Boolean = false,
        initialPage: Int = SmsPageIndex.UNREAD,
    ) {
        val effectiveInitialPage =
            if (initialPage == SmsPageIndex.UNREAD && host.state.unreadSmsCount <= 0) {
                SmsPageIndex.ALL
            } else {
                initialPage
            }
        refreshSmsCapability(render = false)
        val canShowThreads =
            host.state.smsPermissionState != SmsPermissionState.MISSING &&
            (host.state.isDefaultSmsApp || smsRolePromptDismissedThisSession)
        if (!canShowThreads) {
            host.state = host.state.copy(isSmsThreadsLoading = false)
        } else if (forceRefresh) {
            host.state = host.state.copy(
                unreadSmsEntries = emptyList(),
                smsThreads = emptyList(),
                smsAllMessages = emptyList(),
                isSmsThreadsLoading = true,
            )
        }
        host.state = if (canShowThreads) {
            LauncherStateTransitions.showSmsThreads(
                state = host.state,
                visibleRows = host.smsThreadsVisibleRows(),
                pageIndex = effectiveInitialPage,
            )
        } else {
            LauncherStateTransitions.showSmsRolePrompt(host.state)
        }
        host.render()
        host.updateTextInputFocus()
        if (canShowThreads && forceRefresh) {
            refreshSmsData(render = true)
        }
    }

    fun closeModule() {
        host.state = LauncherStateTransitions.hideSmsThreads(host.state)
        host.render()
        host.updateTextInputFocus()
        host.scheduleIdleCheck()
    }

    fun openSelectedThread() {
        if (host.state.smsThreadSearchQuery.isNotBlank()) {
            val message = SmsThreadSearchModel.filter(
                messages = host.state.smsAllMessages,
                query = host.state.smsThreadSearchQuery,
            ).getOrNull(host.state.smsThreadSelectedIndex) ?: return
            openSmsConversation(message.conversationKey)
            return
        }
        val thread = host.state.smsThreads.getOrNull(host.state.smsThreadSelectedIndex) ?: return
        openSmsConversation(thread.conversationKey)
    }

    fun closeThreadDetail() {
        host.state = LauncherStateTransitions.hideSmsThreadDetail(host.state)
        host.render()
        host.updateTextInputFocus()
    }

    fun openUnreadInbox() {
        openModule(initialPage = SmsPageIndex.UNREAD)
    }

    fun openUnreadSummaryTarget() {
        openModule(initialPage = SmsPageIndex.UNREAD)
    }

    fun closeUnreadInbox() {
        closeModule()
    }

    // ── Hardware-key navigation ───────────────────────────────────────────────

    fun moveThreadSelection(delta: Int) {
        if (host.state.smsThreadSearchQuery.isNotBlank()) {
            val lastIndex = SmsThreadSearchModel.filter(
                messages = host.state.smsAllMessages,
                query = host.state.smsThreadSearchQuery,
            ).lastIndex.coerceAtLeast(0)
            host.state = host.state.copy(
                smsThreadSelectedIndex = (host.state.smsThreadSelectedIndex + delta).coerceIn(0, lastIndex),
            )
            host.render()
            return
        }
        host.state = LauncherStateTransitions.moveSmsThreadSelection(
            state = host.state,
            delta = delta,
            visibleRows = host.smsThreadsVisibleRows(),
        )
        host.render()
    }

    fun moveInboxSelection(delta: Int) {
        host.state = LauncherStateTransitions.moveSmsSelection(
            state = host.state,
            delta = delta,
            visibleRows = host.smsInboxVisibleRows(),
        )
        host.render()
    }

    fun openSelectedUnreadThread() {
        val entry = host.state.unreadSmsEntries.getOrNull(host.state.smsSelectedIndex) ?: return
        openSmsConversation(entry.conversationKey)
    }

    fun markAllRead() {
        if (host.state.unreadSmsEntries.isEmpty()) {
            return
        }
        backgroundExecutor.execute {
            val changed = smsRepository.markAllRead()
            if (!changed) {
                return@execute
            }
            refreshSmsData(render = true)
            host.refreshCommunicationStatus(render = true)
        }
    }

    fun markMessageRead(messageId: Long) {
        if (host.state.unreadSmsEntries.none { it.messageId == messageId }) {
            return
        }
        backgroundExecutor.execute {
            val changed = smsRepository.markMessagesRead(listOf(messageId))
            if (!changed) {
                return@execute
            }
            refreshSmsData(render = true)
            host.refreshCommunicationStatus(render = true)
        }
    }

    // ── Draft sending + role / permission ─────────────────────────────────────

    fun sendDraft() {
        if (host.state.smsCurrentIsServiceConversation) {
            return
        }
        val address = host.state.smsCurrentAddress.trim()
        val draft = host.state.smsDraftText.trim()
        if (address.isBlank() || draft.isBlank()) {
            return
        }
        if (host.state.smsPermissionState != SmsPermissionState.READY) {
            ensureReadAccessAndRole()
            return
        }
        host.state = LauncherStateTransitions.updateSmsSendStatusText(
            state = host.state,
            smsSendStatusText = SMS_STATUS_SENDING,
        )
        host.render()
        backgroundExecutor.execute {
            val result = smsRepository.sendMessage(
                SmsSendRequest(
                    address = address,
                    body = draft,
                    threadId = host.state.smsCurrentThreadId,
                ),
            )
            mainHandler.post {
                if (!host.isActive()) {
                    return@post
                }
                result.onSuccess { sentEntry ->
                    val nextMessages = host.state.smsMessages + sentEntry
                    host.state = LauncherStateTransitions.updateSmsAllMessages(
                        state = LauncherStateTransitions.updateSmsMessages(
                            state = LauncherStateTransitions.updateSmsSendStatusText(
                                state = LauncherStateTransitions.updateSmsDraftText(
                                    state = host.state,
                                    smsDraftText = "",
                                ),
                                smsSendStatusText = "",
                            ),
                            threadId = sentEntry.threadId.takeIf { it > 0L } ?: host.state.smsCurrentThreadId,
                            address = sentEntry.address,
                            messages = nextMessages,
                        ),
                        messages = listOf(sentEntry) + host.state.smsAllMessages,
                    )
                    host.render()
                    refreshSmsData(render = false)
                    host.refreshCommunicationStatus(render = false)
                }
                result.onFailure {
                    host.state = LauncherStateTransitions.updateSmsSendStatusText(
                        state = host.state,
                        smsSendStatusText = SMS_STATUS_FAILED,
                    )
                    host.render()
                }
            }
        }
    }

    fun copyMessageCodeOrBody(messageId: Long) {
        val message = host.state.smsMessages.firstOrNull { it.messageId == messageId } ?: return
        val code = SmsVerificationCodeModel.extract(message.body)
        val textToCopy = code ?: message.body.trim()
        if (textToCopy.isBlank()) return
        val clipboard = appContext.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                if (code != null) "SMS CODE" else "SMS BODY",
                textToCopy,
            ),
        )
        host.state = LauncherStateTransitions.updateSmsSendStatusText(
            state = host.state,
            smsSendStatusText = if (code != null) SMS_STATUS_COPIED_CODE else SMS_STATUS_COPIED_BODY,
        )
        host.render()
    }

    /** 申请默认短信应用角色；若已是默认应用则直接进入会话列表。 */
    fun requestDefaultRole() {
        if (smsRepository.isDefaultSmsApp()) {
            host.state = LauncherStateTransitions.showSmsThreads(
                state = host.state,
                visibleRows = host.smsThreadsVisibleRows(),
            )
            host.render()
            return
        }
        val intent = smsRepository.buildDefaultSmsRoleIntent() ?: return
        host.startSmsRoleRequest(intent)
    }

    /** 先补齐短信读写权限，再申请默认短信角色。 */
    fun ensureReadAccessAndRole() {
        val missingPermissions = buildList {
            if (!smsRepository.hasReadSmsPermission()) add(Manifest.permission.READ_SMS)
            if (!smsRepository.hasSendSmsPermission()) add(Manifest.permission.SEND_SMS)
            if (!smsRepository.hasReceiveSmsPermission()) add(Manifest.permission.RECEIVE_SMS)
            if (!smsRepository.hasReadContactsPermission()) add(Manifest.permission.READ_CONTACTS)
        }
        if (missingPermissions.isNotEmpty()) {
            host.requestSmsPermissions(missingPermissions.toTypedArray())
            return
        }
        requestDefaultRole()
    }

    // ── Result routing (called by the Activity) ───────────────────────────────

    /** 短信运行时权限申请返回后调用。 */
    fun onPermissionsResult() {
        refreshSmsCapability(render = false)
        refreshSmsData(render = false)
        requestDefaultRole()
        host.render()
    }

    /** 默认短信角色申请返回后调用。 */
    fun onRoleRequestResult() {
        refreshSmsCapability(render = false)
        smsRolePromptDismissedThisSession = !smsRepository.isDefaultSmsApp()
        openModule(forceRefresh = true)
    }

    // ── Deep-link entry (from launch intent) ──────────────────────────────────

    /** 处理短信深链：刷新能力后打开指定（或按地址解析的）会话。 */
    fun openDeepLinkedThread(threadId: Long?, address: String, draft: String) {
        refreshSmsCapability(render = false)
        val messages = smsRepository.readMessages()
        applySmsData(messages)
        val normalizedAddress = SmsConversationModel.normalizeAddress(address)
        val target = messages.firstOrNull {
            (threadId != null && it.threadId == threadId) ||
                SmsConversationModel.normalizeAddress(it.address) == normalizedAddress
        }
        if (target != null) {
            openSmsConversation(target.conversationKey, prefilledDraft = draft)
            return
        }
        val conversation = smsRepository.conversationForAddress(address)
        openSmsConversation(
            conversationKey = conversation.key,
            prefilledDraft = draft,
            fallbackAddress = address,
            fallbackTitle = conversation.title,
            fallbackIsService = conversation.isService,
        )
    }

    // ── Internal orchestration ────────────────────────────────────────────────

    private fun openSmsConversation(
        conversationKey: String,
        prefilledDraft: String = "",
        fallbackAddress: String = "",
        fallbackTitle: String = "",
        fallbackIsService: Boolean = false,
    ) {
        val messages = SmsConversationModel.messages(host.state.smsAllMessages, conversationKey)
        val latest = messages.maxByOrNull { it.dateMillis }
        val address = latest?.address ?: fallbackAddress
        val title = latest?.conversationTitle ?: fallbackTitle.ifBlank { address }
        val isService = latest?.isServiceConversation ?: fallbackIsService
        val threadId = latest?.threadId
        Log.d(
            LOG_TAG,
            "openSmsConversation key=$conversationKey threadId=$threadId address=$address beforeMode=${host.state.mode}",
        )
        host.state = LauncherStateTransitions.showSmsThreadDetail(
            state = LauncherStateTransitions.updateSmsDraftText(
                state = host.state,
                smsDraftText = prefilledDraft,
            ),
            conversationKey = conversationKey,
            conversationTitle = title,
            isServiceConversation = isService,
            threadId = threadId,
            address = address,
        )
        host.state = LauncherStateTransitions.updateSmsMessages(
            state = host.state,
            conversationKey = conversationKey,
            conversationTitle = title,
            isServiceConversation = isService,
            threadId = threadId,
            address = address,
            messages = messages,
        )
        Log.d(
            LOG_TAG,
            "openSmsConversation afterMode=${host.state.mode} currentThread=${host.state.smsCurrentThreadId}",
        )
        host.render()
        host.updateTextInputFocus()
        val unreadIds = SmsConversationModel.unread(messages).map { it.messageId }
        if (unreadIds.isNotEmpty()) {
            backgroundExecutor.execute {
                if (smsRepository.markMessagesRead(unreadIds)) {
                    refreshSmsData(render = true)
                    host.refreshCommunicationStatus(render = true)
                }
            }
        }
    }

    /** 同步是否默认短信应用与短信权限状态。前台恢复与权限/角色返回后会调用。 */
    fun refreshSmsCapability(render: Boolean) {
        host.state = LauncherStateTransitions.updateSmsCapability(
            state = host.state,
            isDefaultSmsApp = smsRepository.isDefaultSmsApp(),
            smsPermissionState = smsRepository.permissionState(),
        )
        if (host.state.isDefaultSmsApp) {
            smsRolePromptDismissedThisSession = false
        }
        if (render) {
            host.render()
        }
    }

    private fun refreshSmsData(render: Boolean) {
        backgroundExecutor.execute {
            val messages = smsRepository.readMessages()
            mainHandler.post {
                if (!host.isActive()) {
                    return@post
                }
                applySmsData(messages)
                if (render) {
                    host.render()
                }
            }
        }
    }

    private fun applySmsData(messages: List<com.purride.pixellauncherv2.data.SmsMessageEntry>) {
        val threads = SmsConversationModel.summarize(messages)
        var nextState = LauncherStateTransitions.updateSmsAllMessages(
            state = host.state.copy(isSmsThreadsLoading = false),
            messages = messages,
        )
        nextState = LauncherStateTransitions.updateSmsThreads(
            state = nextState,
            threads = threads,
            visibleRows = host.smsThreadsVisibleRows(),
        )
        nextState = LauncherStateTransitions.updateUnreadSmsEntries(
            state = nextState,
            entries = SmsConversationModel.unread(messages),
            visibleRows = host.smsInboxVisibleRows(),
        )
        if (nextState.mode == LauncherMode.SMS_THREAD_DETAIL && nextState.smsCurrentConversationKey.isNotBlank()) {
            val conversationMessages = SmsConversationModel.messages(
                allMessages = messages,
                conversationKey = nextState.smsCurrentConversationKey,
            )
            val latest = conversationMessages.maxByOrNull { it.dateMillis }
            nextState = LauncherStateTransitions.updateSmsMessages(
                state = nextState,
                conversationKey = nextState.smsCurrentConversationKey,
                conversationTitle = latest?.conversationTitle ?: nextState.smsCurrentConversationTitle,
                isServiceConversation = latest?.isServiceConversation
                    ?: nextState.smsCurrentIsServiceConversation,
                threadId = latest?.threadId ?: nextState.smsCurrentThreadId,
                address = latest?.address ?: nextState.smsCurrentAddress,
                messages = conversationMessages,
            )
        }
        host.state = nextState
    }

    private fun onSmsProviderChanged() {
        refreshSmsCapability(render = false)
        val renderSmsHome = host.state.mode == LauncherMode.SMS_THREADS ||
            host.state.mode == LauncherMode.SMS_INBOX
        refreshSmsData(
            render = renderSmsHome ||
                host.state.mode == LauncherMode.SMS_ROLE_PROMPT ||
                host.state.mode == LauncherMode.SMS_THREAD_DETAIL,
        )
    }

    private companion object {
        const val LOG_TAG = "SmsIntent"
        const val SMS_STATUS_SENDING = "SENDING"
        const val SMS_STATUS_FAILED = "FAILED"
        const val SMS_STATUS_COPIED_CODE = "COPIED CODE"
        const val SMS_STATUS_COPIED_BODY = "COPIED MSG"
    }
}
