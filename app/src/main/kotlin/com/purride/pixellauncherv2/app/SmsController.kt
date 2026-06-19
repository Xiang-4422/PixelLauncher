package com.purride.pixellauncherv2.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.util.Log
import com.purride.pixellauncherv2.data.SmsRepository
import com.purride.pixellauncherv2.data.SmsSendRequest
import com.purride.pixellauncherv2.data.UnreadSmsRepository
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.launcher.LauncherStateTransitions
import com.purride.pixellauncherv2.launcher.SmsPermissionState
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
        fun updateDrawerInputFocus()
        fun scheduleIdleCheck()

        /** 刷新未接来电/未读短信计数（短信发送/已读后需要同步）。 */
        fun refreshCommunicationStatus(render: Boolean)

        /** 申请短信相关运行时权限（READ/SEND/RECEIVE_SMS）。 */
        fun requestSmsPermissions(permissions: Array<String>)

        /** 发起默认短信应用角色申请。 */
        fun startSmsRoleRequest(intent: Intent)

        /** 启动一个系统 Intent（宿主负责 catch ActivityNotFoundException）。 */
        fun launchSystemIntent(intent: Intent)
    }

    private val smsRepository = SmsRepository(context)
    private val unreadSmsRepository = UnreadSmsRepository(context)

    private var smsThreadsUnreadOnly = true
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

    fun openThread(threadId: Long, address: String) {
        openSmsThread(threadId = threadId, address = address)
    }

    fun selectIndex(index: Int) {
        host.state = host.state.copy(smsSelectedIndex = index)
    }

    fun draftChanged(text: String) {
        host.state = LauncherStateTransitions.updateSmsDraftText(state = host.state, smsDraftText = text)
    }

    // ── Module open/close ─────────────────────────────────────────────────────

    fun openModule(forceRefresh: Boolean = false, unreadOnly: Boolean = true) {
        smsThreadsUnreadOnly = unreadOnly
        refreshSmsCapability(render = false)
        val canShowThreads =
            host.state.smsPermissionState != SmsPermissionState.MISSING &&
            (host.state.isDefaultSmsApp || smsRolePromptDismissedThisSession)
        if (!canShowThreads) {
            host.state = host.state.copy(isSmsThreadsLoading = false)
        } else if (forceRefresh) {
            host.state = host.state.copy(
                smsThreads = emptyList(),
                isSmsThreadsLoading = true,
            )
        }
        host.state = if (canShowThreads) {
            LauncherStateTransitions.showSmsThreads(
                state = host.state,
                visibleRows = host.smsThreadsVisibleRows(),
            )
        } else {
            LauncherStateTransitions.showSmsRolePrompt(host.state)
        }
        host.render()
        host.updateTextInputFocus()
        if (canShowThreads && forceRefresh) {
            refreshSmsThreads(render = true, unreadOnly = smsThreadsUnreadOnly)
        }
    }

    fun closeModule() {
        smsThreadsUnreadOnly = true
        host.state = LauncherStateTransitions.hideSmsThreads(host.state)
        host.render()
        host.updateTextInputFocus()
        host.scheduleIdleCheck()
    }

    fun openSelectedThread() {
        val thread = host.state.smsThreads.getOrNull(host.state.smsThreadSelectedIndex) ?: return
        openSmsThread(
            threadId = thread.threadId,
            address = thread.address,
        )
    }

    fun closeThreadDetail() {
        host.state = LauncherStateTransitions.hideSmsThreadDetail(host.state)
        host.render()
        host.updateTextInputFocus()
    }

    fun openUnreadInbox() {
        host.state = LauncherStateTransitions.updateUnreadSmsEntries(
            state = host.state,
            entries = unreadSmsRepository.readUnreadMessages(),
            visibleRows = host.smsInboxVisibleRows(),
        )
        host.state = LauncherStateTransitions.showUnreadSmsInbox(
            state = host.state,
            visibleRows = host.smsInboxVisibleRows(),
        )
        host.render()
        host.updateDrawerInputFocus()
    }

    fun closeUnreadInbox() {
        host.state = LauncherStateTransitions.hideUnreadSmsInbox(host.state)
        host.render()
        host.updateDrawerInputFocus()
        host.scheduleIdleCheck()
    }

    // ── Hardware-key navigation ───────────────────────────────────────────────

    fun moveThreadSelection(delta: Int) {
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

    fun launchSelectedUnread() {
        val entry = host.state.unreadSmsEntries.getOrNull(host.state.smsSelectedIndex) ?: return
        host.launchSystemIntent(
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("sms:${Uri.encode(entry.address)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    // ── Draft sending + role / permission ─────────────────────────────────────

    fun sendDraft() {
        val address = host.state.smsCurrentAddress.trim()
        val draft = host.state.smsDraftText.trim()
        if (address.isBlank() || draft.isBlank()) {
            return
        }
        if (host.state.smsPermissionState != SmsPermissionState.READY) {
            ensureReadAccessAndRole()
            return
        }
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
                    host.state = LauncherStateTransitions.updateSmsMessages(
                        state = LauncherStateTransitions.updateSmsDraftText(
                            state = host.state,
                            smsDraftText = "",
                        ),
                        threadId = sentEntry.threadId.takeIf { it > 0L } ?: host.state.smsCurrentThreadId,
                        address = sentEntry.address,
                        messages = nextMessages,
                    )
                    host.render()
                    refreshSmsThreads(render = false)
                    host.refreshCommunicationStatus(render = false)
                }
            }
        }
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
        refreshSmsThreads(render = false)
        requestDefaultRole()
        host.render()
    }

    /** 默认短信角色申请返回后调用。 */
    fun onRoleRequestResult() {
        refreshSmsCapability(render = false)
        smsRolePromptDismissedThisSession = !smsRepository.isDefaultSmsApp()
        openModule(forceRefresh = true, unreadOnly = false)
    }

    // ── Deep-link entry (from launch intent) ──────────────────────────────────

    /** 处理短信深链：刷新能力后打开指定（或按地址解析的）会话。 */
    fun openDeepLinkedThread(threadId: Long?, address: String, draft: String) {
        refreshSmsCapability(render = false)
        openSmsThread(
            threadId = threadId ?: smsRepository.findThreadForAddress(address)?.threadId,
            address = address,
            prefilledDraft = draft,
        )
    }

    // ── Internal orchestration ────────────────────────────────────────────────

    private fun openSmsThread(
        threadId: Long?,
        address: String,
        prefilledDraft: String = "",
    ) {
        Log.d(
            LOG_TAG,
            "openSmsThread threadId=$threadId address=$address draftLength=${prefilledDraft.length} beforeMode=${host.state.mode}",
        )
        host.state = LauncherStateTransitions.showSmsThreadDetail(
            state = LauncherStateTransitions.updateSmsDraftText(
                state = host.state,
                smsDraftText = prefilledDraft,
            ),
            threadId = threadId,
            address = address,
        )
        Log.d(
            LOG_TAG,
            "openSmsThread afterMode=${host.state.mode} currentThread=${host.state.smsCurrentThreadId} address=${host.state.smsCurrentAddress}",
        )
        host.render()
        host.updateTextInputFocus()
        refreshSmsThreadDetail(
            threadId = threadId,
            fallbackAddress = address,
            render = true,
        )
        if (threadId != null) {
            backgroundExecutor.execute {
                smsRepository.markThreadRead(threadId)
                refreshSmsThreads(render = false, unreadOnly = smsThreadsUnreadOnly)
                host.refreshCommunicationStatus(render = false)
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

    private fun refreshSmsThreads(render: Boolean, unreadOnly: Boolean = smsThreadsUnreadOnly) {
        backgroundExecutor.execute {
            val threads = smsRepository.readThreads().let { allThreads ->
                if (unreadOnly) allThreads.filter { it.unreadCount > 0 } else allThreads
            }
            mainHandler.post {
                if (!host.isActive()) {
                    return@post
                }
                host.state = LauncherStateTransitions.updateSmsThreads(
                    state = host.state.copy(isSmsThreadsLoading = false),
                    threads = threads,
                    visibleRows = host.smsThreadsVisibleRows(),
                )
                if (render) {
                    host.render()
                }
            }
        }
    }

    private fun refreshSmsThreadDetail(
        threadId: Long?,
        fallbackAddress: String,
        render: Boolean,
    ) {
        backgroundExecutor.execute {
            val messages = threadId?.let(smsRepository::readThreadMessages).orEmpty()
            val resolvedAddress = messages.lastOrNull()?.address?.takeIf { it.isNotBlank() } ?: fallbackAddress
            mainHandler.post {
                if (!host.isActive()) {
                    return@post
                }
                host.state = LauncherStateTransitions.updateSmsMessages(
                    state = host.state,
                    threadId = threadId,
                    address = resolvedAddress,
                    messages = messages,
                )
                if (render) {
                    host.render()
                }
            }
        }
    }

    private fun onSmsProviderChanged() {
        refreshSmsCapability(render = false)
        refreshSmsThreads(
            render = host.state.mode == LauncherMode.SMS_THREADS || host.state.mode == LauncherMode.SMS_ROLE_PROMPT,
            unreadOnly = smsThreadsUnreadOnly,
        )
        if (host.state.mode == LauncherMode.SMS_THREAD_DETAIL) {
            refreshSmsThreadDetail(
                threadId = host.state.smsCurrentThreadId,
                fallbackAddress = host.state.smsCurrentAddress,
                render = true,
            )
        }
    }

    private companion object {
        const val LOG_TAG = "SmsIntent"
    }
}
