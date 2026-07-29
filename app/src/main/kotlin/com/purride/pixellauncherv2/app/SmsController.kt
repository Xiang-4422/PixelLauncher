package com.purride.pixellauncherv2.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import com.purride.pixellauncherv2.data.SmsMuteSettingsRepository
import com.purride.pixellauncherv2.data.SmsNotificationHelper
import com.purride.pixellauncherv2.data.SmsRepository
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.launcher.LauncherStateTransitions
import com.purride.pixellauncherv2.launcher.SmsConversationModel
import com.purride.pixellauncherv2.launcher.SmsDraftStore
import com.purride.pixellauncherv2.launcher.SmsMessageStatusModel
import com.purride.pixellauncherv2.launcher.SmsPageIndex
import com.purride.pixellauncherv2.launcher.SmsSendStatus
import com.purride.pixellauncherv2.launcher.SmsSendRetryPolicy
import com.purride.pixellauncherv2.launcher.SmsPermissionState
import com.purride.pixellauncherv2.launcher.SmsThreadSearchModel
import com.purride.pixellauncherv2.launcher.SmsVerificationCodeModel
import com.purride.pixellauncherv2.model.SmsMessageEntry
import com.purride.pixellauncherv2.model.SmsSendRequest
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

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
    private val smsRepository: SmsRepository,
    private val smsNotificationHelper: SmsNotificationHelper,
    private val smsMuteSettingsRepository: SmsMuteSettingsRepository,
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

        /** 全局状态栏临时消息（自动消失），用于承载页面级一次性提示。 */
        fun showStatusBarMessage(message: String)

    }

    // 短信仓库由外部注入（禁止在此内部 new），构造边界见 AppContainer / AndroidComponentDependencies。
    private val appContext = context.applicationContext

    private var smsRolePromptDismissedThisSession = false

    /** 重发在途的失败消息 id 集合；只在主线程读写，用于防重复点按。 */
    private val resendInFlightMessageIds = mutableSetOf<Long>()

    /** 按会话保存的未发送草稿；只在主线程读写。 */
    private val draftStore = SmsDraftStore()

    /** 自动补发是否已在排程中；只在主线程读写。 */
    private var queuedRetryScheduled = false

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** 前台时开始监听短信内容变化，并加载会话静音规则。 */
    fun start() {
        smsRepository.start(::onSmsProviderChanged)
        runInBackground {
            // 启动对账：回执广播可能丢失（极端系统状态），把滞留过久的
            // OUTBOX 记录判为失败，避免永远停在“发送中”。
            smsRepository.failStaleOutboxMessages(STALE_OUTBOX_TIMEOUT_MS)
            val mutedKeys = smsMuteSettingsRepository.mutedConversationKeys()
            mainHandler.post {
                if (!host.isActive()) {
                    return@post
                }
                host.state = LauncherStateTransitions.updateSmsMutedConversations(host.state, mutedKeys)
            }
        }
    }

    /** 后台时停止监听。 */
    fun stop() {
        mainHandler.removeCallbacks(providerChangeDebounceRunnable)
        mainHandler.removeCallbacks(queuedRetryRunnable)
        queuedRetryScheduled = false
        smsRepository.stop()
    }

    // ── LauncherCallbacks entry points ────────────────────────────────────────

    fun openThread(conversationKey: String) {
        openSmsConversation(conversationKey)
    }

    /** 对搜索到的任意号码发起新会话；联系人解析走后台线程。 */
    fun composeNewThread(address: String) {
        val trimmed = address.trim()
        if (trimmed.isBlank()) {
            return
        }
        runInBackground {
            val conversation = smsRepository.conversationForAddress(trimmed)
            mainHandler.post {
                if (!host.isActive()) {
                    return@post
                }
                // 号码若属于既有会话，openSmsConversation 会按 key 命中并打开原会话。
                openSmsConversation(
                    conversationKey = conversation.key,
                    fallbackAddress = trimmed,
                    fallbackTitle = conversation.title,
                    fallbackIsService = conversation.isService,
                )
            }
        }
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
        draftStore.update(host.state.smsCurrentConversationKey, text)
        // 状态确实从 SENDING/FAILED 变回 NONE 时必须重绘：输入区的状态标签由
        // 渲染快照派生，不重绘会让 FAILED 一直残留在屏上。
        val clearedStatus = host.state.smsSendStatus != SmsSendStatus.NONE
        host.state = LauncherStateTransitions.updateSmsSendStatus(
            state = LauncherStateTransitions.updateSmsDraftText(state = host.state, smsDraftText = text),
            smsSendStatus = SmsSendStatus.NONE,
        )
        if (clearedStatus) {
            host.render()
        }
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
        // 会话浮层菜单打开时，返回操作只关菜单、不退出模块。
        if (host.state.isSmsThreadMenuVisible) {
            threadMenuDismiss()
            return
        }
        host.state = LauncherStateTransitions.hideSmsThreads(host.state)
        host.render()
        host.updateTextInputFocus()
        host.scheduleIdleCheck()
    }

    fun openSelectedThread() {
        val query = host.state.smsThreadSearchQuery
        if (query.isNotBlank()) {
            val results = SmsThreadSearchModel.filter(
                messages = host.state.smsAllMessages,
                query = query,
            )
            val message = results.getOrNull(host.state.smsThreadSelectedIndex)
            if (message != null) {
                openSmsConversation(message.conversationKey)
                return
            }
            // 无匹配但搜索词是可拨号码时，回车等同点按屏上的 NEW MSG TO 入口，
            // 否则纯按键操作看得到入口却无法激活。
            if (results.isEmpty()) {
                SmsThreadSearchModel.composeAddress(query)?.let(::composeNewThread)
            }
            return
        }
        val thread = host.state.smsThreads.getOrNull(host.state.smsThreadSelectedIndex) ?: return
        openSmsConversation(thread.conversationKey)
    }

    fun closeThreadDetail() {
        // 浮层菜单打开时，返回操作只关菜单、不退出会话。
        if (host.state.isSmsMessageMenuVisible) {
            messageMenuDismiss()
            return
        }
        host.state = LauncherStateTransitions.hideSmsThreadDetail(host.state)
        host.render()
        host.updateTextInputFocus()
    }

    // ── Message long-press menu ───────────────────────────────────────────────

    fun messageLongPressed(messageId: Long) {
        host.state = LauncherStateTransitions.showSmsMessageMenu(host.state, messageId)
        host.render()
    }

    fun messageMenuDismiss() {
        host.state = LauncherStateTransitions.hideSmsMessageMenu(host.state)
        host.render()
    }

    fun messageMenuCopyBody() {
        val message = menuMessage()
        messageMenuDismiss()
        val body = message?.body?.trim().orEmpty()
        if (body.isBlank()) return
        if (copyToClipboard(label = "SMS BODY", text = body)) {
            host.showStatusBarMessage(SMS_STATUS_COPIED_BODY)
        }
    }

    fun messageMenuCopyCode() {
        val message = menuMessage()
        messageMenuDismiss()
        val code = message?.body?.let(SmsVerificationCodeModel::extract) ?: return
        if (copyToClipboard(label = "SMS CODE", text = code)) {
            host.showStatusBarMessage(SMS_STATUS_COPIED_CODE)
        }
    }

    fun messageMenuResend() {
        val message = menuMessage()
        messageMenuDismiss()
        if (message == null || !SmsMessageStatusModel.isRetryable(message.type)) return
        resendMessage(message)
    }

    fun messageMenuDelete() {
        val message = menuMessage()
        messageMenuDismiss()
        if (message == null) return
        runInBackground {
            if (!smsRepository.deleteMessage(message.messageId)) {
                return@runInBackground
            }
            mainHandler.post {
                if (host.isActive()) {
                    host.showStatusBarMessage(SMS_STATUS_DELETED)
                }
            }
            refreshSmsData(render = true)
            host.refreshCommunicationStatus(render = true)
        }
    }

    private fun menuMessage(): SmsMessageEntry? =
        host.state.smsMessages.firstOrNull { it.messageId == host.state.smsMessageMenuMessageId }

    // ── Thread long-press menu ────────────────────────────────────────────────

    fun threadLongPressed(conversationKey: String) {
        host.state = LauncherStateTransitions.showSmsThreadMenu(host.state, conversationKey)
        host.render()
    }

    fun threadMenuDismiss() {
        host.state = LauncherStateTransitions.hideSmsThreadMenu(host.state)
        host.render()
    }

    /** 把该会话的全部未读置为已读并撤下通知。 */
    fun threadMenuMarkRead() {
        val conversationKey = menuConversationKey()
        threadMenuDismiss()
        if (conversationKey.isBlank()) return
        val unread = SmsConversationModel.unread(
            SmsConversationModel.messages(host.state.smsAllMessages, conversationKey),
        )
        if (unread.isEmpty()) return
        runInBackground {
            if (!smsRepository.markMessagesRead(unread.map { it.messageId })) {
                return@runInBackground
            }
            unread.map { it.threadId }.distinct().forEach(smsNotificationHelper::cancelForThread)
            refreshSmsData(render = true)
            host.refreshCommunicationStatus(render = true)
        }
    }

    /** 切换该会话的静音状态（静音只挡通知，不影响入库与未读计数）。 */
    fun threadMenuToggleMute() {
        val conversationKey = menuConversationKey()
        val muted = conversationKey in host.state.smsMutedConversationKeys
        threadMenuDismiss()
        if (conversationKey.isBlank()) return
        runInBackground {
            val mutedKeys = smsMuteSettingsRepository.setMuted(conversationKey, !muted)
            mainHandler.post {
                if (!host.isActive()) {
                    return@post
                }
                host.state = LauncherStateTransitions.updateSmsMutedConversations(host.state, mutedKeys)
                host.showStatusBarMessage(if (muted) SMS_STATUS_UNMUTED else SMS_STATUS_MUTED)
            }
        }
    }

    /** 删除整个会话：按 conversationKey 汇总消息 id 批量删除（服务号聚合会话跨多个 thread）。 */
    fun threadMenuDelete() {
        val conversationKey = menuConversationKey()
        threadMenuDismiss()
        if (conversationKey.isBlank()) return
        val messages = SmsConversationModel.messages(host.state.smsAllMessages, conversationKey)
        if (messages.isEmpty()) return
        // 会话整体删除，草稿一并清掉。
        draftStore.clear(conversationKey)
        runInBackground {
            if (!smsRepository.deleteMessages(messages.map { it.messageId })) {
                return@runInBackground
            }
            messages.map { it.threadId }.distinct().forEach(smsNotificationHelper::cancelForThread)
            mainHandler.post {
                if (host.isActive()) {
                    host.showStatusBarMessage(SMS_STATUS_DELETED)
                }
            }
            refreshSmsData(render = true)
            host.refreshCommunicationStatus(render = true)
        }
    }

    private fun menuConversationKey(): String = host.state.smsThreadMenuConversationKey

    // ── Queued auto retry ─────────────────────────────────────────────────────

    /** 数据刷新后发现 QUEUED 消息时安排一次自动补发；主线程调用。 */
    private fun scheduleQueuedRetryIfNeeded(messages: List<SmsMessageEntry>) {
        if (queuedRetryScheduled || messages.none { SmsMessageStatusModel.isQueued(it.type) }) {
            return
        }
        queuedRetryScheduled = true
        mainHandler.postDelayed(queuedRetryRunnable, SmsSendRetryPolicy.RETRY_INTERVAL_MS)
    }

    private val queuedRetryRunnable = Runnable {
        queuedRetryScheduled = false
        if (!host.isActive()) {
            return@Runnable
        }
        // 仍未成功的会再次落成 QUEUED，由下一轮刷新重新调度，形成固定间隔的重试环。
        val queued = host.state.smsAllMessages
            .filter { SmsMessageStatusModel.isQueued(it.type) }
            .filter { resendInFlightMessageIds.add(it.messageId) }
        if (queued.isEmpty()) {
            return@Runnable
        }
        runInBackground {
            queued.forEach { message ->
                // 状态快照可能过期（消息已被手动重发/删除）：删除原记录成功
                // 才算认领本次补发，否则跳过避免重复发送。
                if (!smsRepository.deleteMessage(message.messageId)) {
                    return@forEach
                }
                smsRepository.sendMessage(
                    SmsSendRequest(
                        address = message.address,
                        body = message.body,
                        threadId = message.threadId.takeIf { it > 0L },
                        subscriptionId = message.subscriptionId.takeIf { it >= 0 },
                    ),
                )
            }
            mainHandler.post {
                queued.forEach { resendInFlightMessageIds.remove(it.messageId) }
                if (host.isActive()) {
                    refreshSmsData(render = true)
                }
            }
        }
    }

    fun openUnreadSummaryTarget() {
        openModule(initialPage = SmsPageIndex.UNREAD)
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
        val notifiedThreadIds = host.state.unreadSmsEntries.map { it.threadId }.distinct()
        runInBackground {
            val changed = smsRepository.markAllRead()
            if (!changed) {
                return@runInBackground
            }
            notifiedThreadIds.forEach(smsNotificationHelper::cancelForThread)
            refreshSmsData(render = true)
            host.refreshCommunicationStatus(render = true)
        }
    }

    fun markMessageRead(messageId: Long) {
        val entry = host.state.unreadSmsEntries.firstOrNull { it.messageId == messageId } ?: return
        // 该会话只剩这一条未读时，它挂着的通知也一并撤下。
        val clearsThread = host.state.unreadSmsEntries.count { it.threadId == entry.threadId } <= 1
        runInBackground {
            val changed = smsRepository.markMessagesRead(listOf(messageId))
            if (!changed) {
                return@runInBackground
            }
            if (clearsThread) {
                smsNotificationHelper.cancelForThread(entry.threadId)
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
        // 上一次发送还没回来时忽略重复触发（连点 SEND / 输入法 SEND 键），避免重复发送。
        if (host.state.smsSendStatus == SmsSendStatus.SENDING) {
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
        // 主线程先捕获会话快照：后台 lambda 不得直接读 host.state（跨线程读可变状态），
        // 且发送期间用户可能切换会话，回调时需要校验是否仍停留在原会话。
        val conversationKey = host.state.smsCurrentConversationKey
        val threadId = host.state.smsCurrentThreadId
        // 双卡：沿用该会话最近一条消息的 SIM 回复，避免跨卡回错号码。
        val subscriptionId = host.state.smsMessages.lastOrNull { it.subscriptionId >= 0 }?.subscriptionId
        host.state = LauncherStateTransitions.updateSmsSendStatus(
            state = host.state,
            smsSendStatus = SmsSendStatus.SENDING,
        )
        host.render()
        runInBackground {
            val result = smsRepository.sendMessage(
                SmsSendRequest(
                    address = address,
                    body = draft,
                    threadId = threadId,
                    subscriptionId = subscriptionId,
                ),
            )
            mainHandler.post {
                if (!host.isActive()) {
                    return@post
                }
                val stillInConversation = host.state.smsCurrentConversationKey == conversationKey
                result.onSuccess { sentEntry ->
                    // 只清除与已发送文本一致的草稿：发送在途期间用户可能又输入了新内容。
                    draftStore.clearIfUnchanged(conversationKey, draft)
                    if (stillInConversation) {
                        val draftUnchanged = host.state.smsDraftText.trim() == draft
                        val nextMessages = host.state.smsMessages + sentEntry
                        host.state = LauncherStateTransitions.updateSmsAllMessages(
                            state = LauncherStateTransitions.updateSmsMessages(
                                state = LauncherStateTransitions.updateSmsSendStatus(
                                    state = LauncherStateTransitions.updateSmsDraftText(
                                        state = host.state,
                                        smsDraftText = if (draftUnchanged) "" else host.state.smsDraftText,
                                    ),
                                    smsSendStatus = SmsSendStatus.NONE,
                                ),
                                threadId = sentEntry.threadId.takeIf { it > 0L } ?: host.state.smsCurrentThreadId,
                                address = sentEntry.address,
                                messages = nextMessages,
                            ),
                            messages = listOf(sentEntry) + host.state.smsAllMessages,
                        )
                        host.render()
                        refreshSmsData(render = false)
                    } else {
                        // 已离开原会话：不并入当前消息流、不动草稿，清掉残留的 SENDING
                        // 后把消息汇入全量列表并整体刷新（消息已在提供者里，刷新自然归位）。
                        host.state = LauncherStateTransitions.updateSmsAllMessages(
                            state = LauncherStateTransitions.updateSmsSendStatus(
                                state = host.state,
                                smsSendStatus = SmsSendStatus.NONE,
                            ),
                            messages = listOf(sentEntry) + host.state.smsAllMessages,
                        )
                        refreshSmsData(render = true)
                    }
                    host.refreshCommunicationStatus(render = false)
                }
                result.onFailure {
                    if (stillInConversation) {
                        host.state = LauncherStateTransitions.updateSmsSendStatus(
                            state = host.state,
                            smsSendStatus = SmsSendStatus.FAILED,
                        )
                        host.render()
                    } else {
                        host.state = LauncherStateTransitions.updateSmsSendStatus(
                            state = host.state,
                            smsSendStatus = SmsSendStatus.NONE,
                        )
                        refreshSmsData(render = true)
                    }
                }
            }
        }
    }

    /** 消息点按入口：失败/排队消息触发立即重发，其余复制验证码或正文。 */
    fun messagePressed(messageId: Long) {
        val message = host.state.smsMessages.firstOrNull { it.messageId == messageId } ?: return
        if (SmsMessageStatusModel.isRetryable(message.type)) {
            resendMessage(message)
            return
        }
        copyMessageCodeOrBody(message)
    }

    /** 删除旧的失败记录后按原地址原文重发，走同一套 OUTBOX→回执流转。 */
    private fun resendMessage(message: SmsMessageEntry) {
        // 主线程守卫：同一条失败消息的重发在途时忽略重复点按，避免重复发送。
        if (!resendInFlightMessageIds.add(message.messageId)) {
            return
        }
        host.state = LauncherStateTransitions.updateSmsSendStatus(
            state = host.state,
            smsSendStatus = SmsSendStatus.SENDING,
        )
        host.render()
        runInBackground {
            // 删除原记录即“认领”这次重发：删除失败说明记录已被并发路径
            // （自动重试/另一次点按）处理，放弃本次以避免重复发送。
            if (!smsRepository.deleteMessage(message.messageId)) {
                mainHandler.post {
                    resendInFlightMessageIds.remove(message.messageId)
                    if (!host.isActive()) {
                        return@post
                    }
                    host.state = LauncherStateTransitions.updateSmsSendStatus(
                        state = host.state,
                        smsSendStatus = SmsSendStatus.NONE,
                    )
                    host.render()
                    refreshSmsData(render = true)
                }
                return@runInBackground
            }
            val result = smsRepository.sendMessage(
                SmsSendRequest(
                    address = message.address,
                    body = message.body,
                    threadId = message.threadId.takeIf { it > 0L },
                    subscriptionId = message.subscriptionId.takeIf { it >= 0 },
                ),
            )
            mainHandler.post {
                resendInFlightMessageIds.remove(message.messageId)
                if (!host.isActive()) {
                    return@post
                }
                host.state = LauncherStateTransitions.updateSmsSendStatus(
                    state = host.state,
                    smsSendStatus = if (result.isSuccess) SmsSendStatus.NONE else SmsSendStatus.FAILED,
                )
                host.render()
                refreshSmsData(render = true)
                host.refreshCommunicationStatus(render = false)
            }
        }
    }

    private fun copyMessageCodeOrBody(message: SmsMessageEntry) {
        val code = SmsVerificationCodeModel.extract(message.body)
        val textToCopy = code ?: message.body.trim()
        if (textToCopy.isBlank()) return
        if (!copyToClipboard(label = if (code != null) "SMS CODE" else "SMS BODY", text = textToCopy)) {
            return
        }
        // 复制反馈走全局状态栏临时消息：服务号（验证码）会话不渲染输入区，
        // 写 smsSendStatusText 在最高频的验证码复制场景完全不可见。
        host.showStatusBarMessage(if (code != null) SMS_STATUS_COPIED_CODE else SMS_STATUS_COPIED_BODY)
    }

    private fun copyToClipboard(label: String, text: String): Boolean {
        val clipboard = appContext.getSystemService(ClipboardManager::class.java) ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        return true
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
        // 深链常在点通知冷启动时到达：全量读库与联系人解析不能占主线程，
        // 读完投递回主线程再打开会话。
        runInBackground {
            val messages = smsRepository.readMessages()
            val fallbackConversation = smsRepository.conversationForAddress(address)
            mainHandler.post {
                if (!host.isActive()) {
                    return@post
                }
                applySmsData(messages)
                val normalizedAddress = SmsConversationModel.normalizeAddress(address)
                val target = messages.firstOrNull {
                    (threadId != null && it.threadId == threadId) ||
                        SmsConversationModel.normalizeAddress(it.address) == normalizedAddress
                }
                if (target != null) {
                    openSmsConversation(target.conversationKey, prefilledDraft = draft)
                    return@post
                }
                openSmsConversation(
                    conversationKey = fallbackConversation.key,
                    prefilledDraft = draft,
                    fallbackAddress = address,
                    fallbackTitle = fallbackConversation.title,
                    fallbackIsService = fallbackConversation.isService,
                )
            }
        }
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
            // 进入会话时清掉上一个会话残留的状态文案（SENDING/FAILED/COPIED），
            // 并恢复该会话此前未发送的草稿（深链预填内容优先）。
            state = LauncherStateTransitions.updateSmsDraftText(
                state = LauncherStateTransitions.updateSmsSendStatus(
                    state = host.state,
                    smsSendStatus = SmsSendStatus.NONE,
                ),
                smsDraftText = draftStore.restore(conversationKey, prefilled = prefilledDraft),
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
        threadId?.let(smsNotificationHelper::cancelForThread)
        // 入库失败的降级通知按地址编号，打开会话时一并撤下。
        smsNotificationHelper.cancelForAddress(address)
        val unreadIds = SmsConversationModel.unread(messages).map { it.messageId }
        if (unreadIds.isNotEmpty()) {
            runInBackground {
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
        runInBackground {
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

    private fun applySmsData(messages: List<SmsMessageEntry>) {
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
        scheduleQueuedRetryIfNeeded(messages)
        // 菜单指向的会话可能已被删除/合并：标志残留会让菜单无声消失，
        // 之后第一次返回键被消耗在关闭一个不可见的菜单上。
        if (nextState.isSmsThreadMenuVisible &&
            threads.none { it.conversationKey == nextState.smsThreadMenuConversationKey }
        ) {
            nextState = LauncherStateTransitions.hideSmsThreadMenu(nextState)
        }
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
            // 菜单指向的消息可能已被删除（如 QUEUED 消息被自动补发换了新 id）：
            // 同样要清标志，避免菜单无声消失后返回键被白白消耗一次。
            if (nextState.isSmsMessageMenuVisible &&
                conversationMessages.none { it.messageId == nextState.smsMessageMenuMessageId }
            ) {
                nextState = LauncherStateTransitions.hideSmsMessageMenu(nextState)
            }
        }
        host.state = nextState
    }

    /**
     * 内容观察者回调（主线程）。批量写库（标记全部已读、删除会话）会触发
     * 连环 onChange，每次都全表重读代价高：合并 300ms 窗口内的变更只刷一次。
     */
    /**
     * 提交后台任务。宿主销毁时执行器被 shutdownNow：此后到达的异步回调再提交
     * 任务会抛 RejectedExecutionException 杀死进程——此时结果已无处落地，静默丢弃。
     */
    private fun runInBackground(task: () -> Unit) {
        try {
            backgroundExecutor.execute { task() }
        } catch (_: RejectedExecutionException) {
        }
    }

    private fun onSmsProviderChanged() {
        mainHandler.removeCallbacks(providerChangeDebounceRunnable)
        mainHandler.postDelayed(providerChangeDebounceRunnable, PROVIDER_CHANGE_DEBOUNCE_MS)
    }

    private val providerChangeDebounceRunnable = Runnable {
        if (!host.isActive()) {
            return@Runnable
        }
        refreshSmsCapability(render = false)
        val renderSmsHome = host.state.mode == LauncherMode.SMS_THREADS
        refreshSmsData(
            render = renderSmsHome ||
                host.state.mode == LauncherMode.SMS_ROLE_PROMPT ||
                host.state.mode == LauncherMode.SMS_THREAD_DETAIL,
        )
    }

    private companion object {
        const val LOG_TAG = "SmsIntent"
        const val SMS_STATUS_COPIED_CODE = "COPIED CODE"
        const val SMS_STATUS_COPIED_BODY = "COPIED MSG"
        const val SMS_STATUS_DELETED = "DELETED"
        const val SMS_STATUS_MUTED = "MUTED"
        const val SMS_STATUS_UNMUTED = "UNMUTED"

        /** 内容变更防抖窗口：合并批量写库触发的连环 onChange。 */
        const val PROVIDER_CHANGE_DEBOUNCE_MS = 300L

        /** OUTBOX 滞留超过该时长即判定回执丢失（启动对账用）。 */
        const val STALE_OUTBOX_TIMEOUT_MS = 10 * 60_000L
    }
}
