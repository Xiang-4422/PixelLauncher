package com.purride.pixellauncherv2.app

import android.content.Context
import android.content.Intent
import com.purride.pixellauncherv2.data.SmsMuteSettingsRepository
import com.purride.pixellauncherv2.data.SmsNotificationHelper
import com.purride.pixellauncherv2.data.SmsRepository
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.launcher.SmsPermissionState
import com.purride.pixellauncherv2.launcher.SmsSendStatus
import com.purride.pixellauncherv2.model.SmsMessageEntry
import com.purride.pixellauncherv2.model.SmsThreadSummary
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SmsController] 的真实编排行为测试。
 *
 * Controller 与状态转换器使用生产实现；只有 Android Context、Handler 和最终仓库边界
 * 使用严格 mock，后台与主线程队列由测试逐级推进。
 */
class SmsControllerBehaviorTest {

    /** 缺少短信读取能力时必须结束旧 loading，并进入角色引导页。 */
    @Test
    fun openModule_withoutReadPermission_finishesLoadingAndShowsRolePrompt() {
        val harness = SmsHarness(
            initialState = sentinelState().copy(
                isSmsThreadsLoading = true,
                unreadSmsCount = 1,
            ),
        )
        every { harness.repository.isDefaultSmsApp() } returns false
        every { harness.repository.permissionState() } returns SmsPermissionState.MISSING

        harness.controller.openModule()

        assertEquals(LauncherMode.SMS_ROLE_PROMPT, harness.host.state.mode)
        assertFalse(harness.host.state.isSmsThreadsLoading)
        assertEquals(1, harness.host.renderCount)
        assertEquals(1, harness.host.textInputFocusUpdateCount)
        assertEquals(0, harness.executor.pendingTaskCount)
        assertSentinels(harness.host.state)
        verify(exactly = 1) { harness.repository.isDefaultSmsApp() }
        verify(exactly = 1) { harness.repository.permissionState() }
        verify(exactly = 0) { harness.repository.readMessages() }
        verify(exactly = 0) { harness.main.handler.post(any<Runnable>()) }
    }

    /** 有读取能力且强制刷新时，旧缓存要原子清空并保留可观察的 loading 中间态。 */
    @Test
    fun openModule_forceRefresh_resetsCachesAndStartsLoadingBeforeBackgroundRead() {
        val cachedMessage = message(
            id = 1L,
            conversationKey = "person:cached",
            body = "cached",
        )
        val cachedThread = SmsThreadSummary(
            threadId = 1L,
            address = "10001",
            snippet = "cached",
            dateMillis = 1L,
            unreadCount = 1,
            messageCount = 1,
            conversationKey = "person:cached",
        )
        val harness = SmsHarness(
            initialState = sentinelState().copy(
                unreadSmsEntries = listOf(cachedMessage),
                smsThreads = listOf(cachedThread),
                smsAllMessages = listOf(cachedMessage),
                unreadSmsCount = 1,
            ),
        )
        every { harness.repository.isDefaultSmsApp() } returns true
        every { harness.repository.permissionState() } returns SmsPermissionState.READY
        every { harness.repository.readMessages() } returns emptyList()

        harness.controller.openModule(forceRefresh = true)

        assertEquals(LauncherMode.SMS_THREADS, harness.host.state.mode)
        assertTrue(harness.host.state.isSmsThreadsLoading)
        assertTrue(harness.host.state.unreadSmsEntries.isEmpty())
        assertTrue(harness.host.state.smsThreads.isEmpty())
        assertTrue(harness.host.state.smsAllMessages.isEmpty())
        assertEquals(1, harness.executor.pendingTaskCount)
        assertEquals(0, harness.main.pendingImmediateTaskCount)
        assertSentinels(harness.host.state)
        verify(exactly = 0) { harness.repository.readMessages() }
        verify(exactly = 0) { harness.main.handler.post(any<Runnable>()) }
    }

    /** 搜索态的硬件导航必须按过滤结果钳制选择，并保留其它状态。 */
    @Test
    fun moveThreadSelection_whileSearching_clampsAgainstFilteredMessages() {
        val harness = SmsHarness(
            initialState = sentinelState().copy(
                smsThreadSearchQuery = "project",
                smsThreadSelectedIndex = 0,
                smsThreadListStartIndex = 7,
                smsAllMessages = listOf(
                    message(1L, "person:a", "project alpha"),
                    message(2L, "person:b", "unrelated"),
                    message(3L, "person:c", "project gamma"),
                ),
            ),
        )

        harness.controller.moveThreadSelection(delta = 20)

        assertEquals(1, harness.host.state.smsThreadSelectedIndex)
        assertEquals(7, harness.host.state.smsThreadListStartIndex)
        assertEquals(1, harness.host.renderCount)
        assertSentinels(harness.host.state)
        confirmVerified(
            harness.repository,
            harness.notificationHelper,
            harness.muteSettingsRepository,
        )
    }

    /** 后台短信快照只有投递到主线程后才能结束 loading 并替换三个派生列表。 */
    @Test
    fun forceRefresh_resultAppliesDataAndFinishesLoadingOnMainQueue() {
        val loadedMessage = message(
            id = 9L,
            conversationKey = "person:loaded",
            body = "new payload",
        )
        val harness = SmsHarness(initialState = sentinelState().copy(unreadSmsCount = 1))
        every { harness.repository.isDefaultSmsApp() } returns true
        every { harness.repository.permissionState() } returns SmsPermissionState.READY
        every { harness.repository.readMessages() } returns listOf(loadedMessage)

        harness.controller.openModule(forceRefresh = true)
        harness.executor.runNext()

        assertTrue(harness.host.state.isSmsThreadsLoading)
        assertEquals(1, harness.main.pendingImmediateTaskCount)
        assertTrue(harness.host.state.smsAllMessages.isEmpty())

        harness.main.runNextImmediate()

        assertFalse(harness.host.state.isSmsThreadsLoading)
        assertEquals(listOf(loadedMessage), harness.host.state.smsAllMessages)
        assertEquals(listOf(loadedMessage), harness.host.state.unreadSmsEntries)
        assertEquals(1, harness.host.state.smsThreads.size)
        assertEquals("person:loaded", harness.host.state.smsThreads.single().conversationKey)
        assertEquals(2, harness.host.renderCount)
        assertEquals(0, harness.main.pendingDelayedTaskCount)
        assertSentinels(harness.host.state)
        verify(exactly = 1) { harness.repository.readMessages() }
        verify(exactly = 1) { harness.main.handler.post(any<Runnable>()) }
        verify(exactly = 0) {
            harness.main.handler.postDelayed(any<Runnable>(), any<Long>())
        }
        verify(exactly = 0) { harness.main.handler.removeCallbacks(any<Runnable>()) }
    }

    /** 会话 A 的发送结果晚到时，不得覆盖用户已切换到的会话 B 消息与草稿。 */
    @Test
    fun sendDraft_resultForStaleConversation_preservesCurrentConversation() {
        val originalMessage = message(1L, "person:a", "old A", isRead = true)
        val currentMessage = message(2L, "person:b", "old B", isRead = true)
        val sentMessage = message(
            id = 3L,
            conversationKey = "person:a",
            body = "draft A",
            isRead = true,
            type = OUTGOING_MESSAGE_TYPE,
        )
        val harness = SmsHarness(
            initialState = sentinelState().copy(
                mode = LauncherMode.SMS_THREAD_DETAIL,
                smsPermissionState = SmsPermissionState.READY,
                isDefaultSmsApp = true,
                smsCurrentConversationKey = "person:a",
                smsCurrentConversationTitle = "A",
                smsCurrentThreadId = 1L,
                smsCurrentAddress = "10001",
                smsMessages = listOf(originalMessage),
                smsAllMessages = listOf(originalMessage, currentMessage),
                smsDraftText = "draft A",
            ),
        )
        every { harness.repository.sendMessage(any()) } returns Result.success(sentMessage)

        harness.controller.sendDraft()
        assertEquals(SmsSendStatus.SENDING, harness.host.state.smsSendStatus)
        harness.executor.runNext()
        assertEquals(1, harness.main.pendingImmediateTaskCount)

        // 模拟发送在途期间用户切换到另一个会话，并在那里继续编辑草稿。
        harness.host.state = harness.host.state.copy(
            smsCurrentConversationKey = "person:b",
            smsCurrentConversationTitle = "B",
            smsCurrentThreadId = 2L,
            smsCurrentAddress = "10002",
            smsMessages = listOf(currentMessage),
            smsDraftText = "draft B",
        )
        harness.main.runNextImmediate()

        assertEquals("person:b", harness.host.state.smsCurrentConversationKey)
        assertEquals(listOf(currentMessage), harness.host.state.smsMessages)
        assertEquals("draft B", harness.host.state.smsDraftText)
        assertEquals(SmsSendStatus.NONE, harness.host.state.smsSendStatus)
        assertTrue(harness.host.state.smsAllMessages.contains(sentMessage))
        assertEquals(listOf(false), harness.host.communicationRefreshCalls)
        assertEquals(1, harness.executor.pendingTaskCount)
        assertSentinels(harness.host.state)
        verify(exactly = 1) {
            harness.repository.sendMessage(
                match { request ->
                    request.address == "10001" &&
                        request.body == "draft A" &&
                        request.threadId == 1L
                },
            )
        }
        verify(exactly = 1) { harness.main.handler.post(any<Runnable>()) }
    }

    /** 创建带跨域哨兵的最小 Launcher 状态。 */
    private fun sentinelState(): LauncherState = LauncherState(
        currentTimeText = SENTINEL_TIME,
        notificationCount = SENTINEL_NOTIFICATION_COUNT,
        screenUsageTimeText = SENTINEL_SCREEN_USAGE,
    )

    /** 断言 Controller 没有改写与短信、电话无关的状态切片。 */
    private fun assertSentinels(state: LauncherState) {
        assertEquals(SENTINEL_TIME, state.currentTimeText)
        assertEquals(SENTINEL_NOTIFICATION_COUNT, state.notificationCount)
        assertEquals(SENTINEL_SCREEN_USAGE, state.screenUsageTimeText)
    }

    /** 构造纯 Kotlin 的短信消息夹具。 */
    private fun message(
        id: Long,
        conversationKey: String,
        body: String,
        isRead: Boolean = false,
        type: Int = INCOMING_MESSAGE_TYPE,
    ): SmsMessageEntry = SmsMessageEntry(
        messageId = id,
        threadId = id,
        address = "1000$id",
        body = body,
        dateMillis = id,
        type = type,
        isRead = isRead,
        conversationKey = conversationKey,
        conversationTitle = conversationKey,
    )

    private companion object {
        /** 用于检查跨域字段保留的时间哨兵。 */
        const val SENTINEL_TIME = "07:30"

        /** 用于检查跨域字段保留的通知计数哨兵。 */
        const val SENTINEL_NOTIFICATION_COUNT = 37

        /** 用于检查跨域字段保留的屏幕使用时长哨兵。 */
        const val SENTINEL_SCREEN_USAGE = "09:09"

        /** Android SMS 收件消息类型的稳定数据库值。 */
        const val INCOMING_MESSAGE_TYPE = 1

        /** Android SMS 已发送消息类型的稳定数据库值。 */
        const val OUTGOING_MESSAGE_TYPE = 2
    }
}

/** 汇总严格 mock、可控队列和短信 Host，减少各行为用例的搭建噪音。 */
private class SmsHarness(initialState: LauncherState) {

    /** 只允许读取 applicationContext 的严格 Android Context mock。 */
    val context: Context = mockk()

    /** Controller 使用的严格短信仓库 mock。 */
    val repository: SmsRepository = mockk()

    /** Controller 使用的严格通知边界 mock。 */
    val notificationHelper: SmsNotificationHelper = mockk()

    /** Controller 使用的严格静音设置边界 mock。 */
    val muteSettingsRepository: SmsMuteSettingsRepository = mockk()

    /** 显式推进的后台队列。 */
    val executor = QueuedControllerExecutor()

    /** 显式推进的主线程 Handler 队列。 */
    val main = ControlledMainHandler()

    /** 记录 Controller 可观察副作用的短信宿主。 */
    val host = FakeSmsHost(initialState)

    /** 使用生产实现的被测 Controller。 */
    val controller: SmsController

    init {
        every { context.applicationContext } returns context
        controller = SmsController(
            context = context,
            smsRepository = repository,
            smsNotificationHelper = notificationHelper,
            smsMuteSettingsRepository = muteSettingsRepository,
            backgroundExecutor = executor,
            mainHandler = main.handler,
            host = host,
        )
        verify(exactly = 1) { context.applicationContext }
    }
}

/** 记录短信 Controller 状态写入与宿主回调的可观察 fake。 */
private class FakeSmsHost(initialState: LauncherState) : SmsController.Host {

    /** Controller 读写的唯一状态快照。 */
    override var state: LauncherState = initialState

    /** 已请求的重绘次数。 */
    var renderCount: Int = 0

    /** 已请求的输入焦点同步次数。 */
    var textInputFocusUpdateCount: Int = 0

    /** 通信状态刷新参数的调用记录。 */
    val communicationRefreshCalls = mutableListOf<Boolean>()

    /** 权限申请参数的调用记录。 */
    val permissionRequests = mutableListOf<List<String>>()

    /** 默认短信角色申请的 Intent 记录。 */
    val roleRequests = mutableListOf<Intent>()

    /** 状态栏临时消息记录。 */
    val statusMessages = mutableListOf<String>()

    /** 测试期间宿主是否保持活动。 */
    var active: Boolean = true

    /** 记录一次渲染请求。 */
    override fun render() {
        renderCount += 1
    }

    /** 返回测试控制的宿主活动状态。 */
    override fun isActive(): Boolean = active

    /** 返回稳定的短信会话可视行数。 */
    override fun smsThreadsVisibleRows(): Int = 3

    /** 返回稳定的未读短信可视行数。 */
    override fun smsInboxVisibleRows(): Int = 3

    /** 记录一次输入焦点同步。 */
    override fun updateTextInputFocus() {
        textInputFocusUpdateCount += 1
    }

    /** 本组用例不需要真实待机排程。 */
    override fun scheduleIdleCheck() = Unit

    /** 记录通信摘要刷新及其重绘参数。 */
    override fun refreshCommunicationStatus(render: Boolean) {
        communicationRefreshCalls += render
    }

    /** 记录短信权限请求。 */
    override fun requestSmsPermissions(permissions: Array<String>) {
        permissionRequests += permissions.toList()
    }

    /** 记录默认短信角色请求。 */
    override fun startSmsRoleRequest(intent: Intent) {
        roleRequests += intent
    }

    /** 记录一次状态栏临时消息。 */
    override fun showStatusBarMessage(message: String) {
        statusMessages += message
    }
}
