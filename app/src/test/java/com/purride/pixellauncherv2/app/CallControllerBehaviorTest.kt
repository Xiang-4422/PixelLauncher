package com.purride.pixellauncherv2.app

import android.Manifest
import com.purride.pixellauncherv2.data.CallLogRepository
import com.purride.pixellauncherv2.data.ContactDirectoryRepository
import com.purride.pixellauncherv2.data.ContactSearchRepository
import com.purride.pixellauncherv2.data.DialerRepository
import com.purride.pixellauncherv2.launcher.CallPageIndex
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.model.CallLogEntry
import com.purride.pixellauncherv2.model.CallLogGroup
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CallController] 的真实编排行为测试。
 *
 * Controller 和通话分组模型均使用生产实现，final Android 仓库边界由严格 MockK
 * mock 控制，后台与主线程队列分别推进。
 */
class CallControllerBehaviorTest {

    /** 缺少通话记录权限时仍应进入拨号模块，并一次性请求全部缺失能力。 */
    @Test
    fun openCallLog_withoutPermission_landsOnDialPadAndRequestsMissingCapabilities() {
        val harness = CallHarness(initialState = sentinelState().copy(isCallLogLoading = true))
        every { harness.callLogRepository.hasReadCallLogPermission() } returns false
        every { harness.callLogRepository.hasWriteCallLogPermission() } returns false
        every { harness.dialerRepository.hasCallPhonePermission() } returns false
        every { harness.contactSearchRepository.invalidate() } just Runs
        every { harness.contactSearchRepository.hasReadContactsPermission() } returns false
        every { harness.contactDirectoryRepository.hasWriteContactsPermission() } returns false

        harness.controller.openCallLog()

        assertEquals(LauncherMode.DIALER, harness.host.state.mode)
        assertEquals(CallPageIndex.DIAL, harness.host.state.callPageIndex)
        assertFalse(harness.host.state.isCallLogLoading)
        assertFalse(harness.host.state.hasCallLogPermission)
        assertFalse(harness.host.state.hasCallPhonePermission)
        assertEquals(1, harness.host.renderCount)
        assertEquals(
            listOf(
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS,
            ),
            harness.host.permissionRequests.single(),
        )
        assertEquals(0, harness.executor.pendingTaskCount)
        assertSentinels(harness.host.state)
        verify(exactly = 3) { harness.callLogRepository.hasReadCallLogPermission() }
        verify(exactly = 1) { harness.callLogRepository.hasWriteCallLogPermission() }
        verify(exactly = 2) { harness.dialerRepository.hasCallPhonePermission() }
        verify(exactly = 1) { harness.contactSearchRepository.invalidate() }
        verify(exactly = 1) { harness.contactSearchRepository.hasReadContactsPermission() }
        verify(exactly = 1) {
            harness.contactDirectoryRepository.hasWriteContactsPermission()
        }
        verify(exactly = 0) { harness.main.handler.post(any<Runnable>()) }
        verify(exactly = 0) {
            harness.main.handler.postDelayed(any<Runnable>(), any<Long>())
        }
        verify(exactly = 0) { harness.main.handler.removeCallbacks(any<Runnable>()) }
    }

    /** 已有通话缓存时进入模块应静默刷新，不能重新显示首次 loading。 */
    @Test
    fun openCallLog_withCachedGroups_keepsLoadingOffWhileRefreshIsQueued() {
        val cachedGroup = callGroup(id = 7L, number = "10007")
        val harness = CallHarness(
            initialState = sentinelState().copy(
                callLogGroups = listOf(cachedGroup),
                isCallLogLoading = true,
            ),
        )
        every { harness.callLogRepository.hasReadCallLogPermission() } returns true
        every { harness.dialerRepository.hasCallPhonePermission() } returns true
        every { harness.contactSearchRepository.invalidate() } just Runs

        harness.controller.openCallLog()

        assertEquals(LauncherMode.DIALER, harness.host.state.mode)
        assertEquals(CallPageIndex.RECENT, harness.host.state.callPageIndex)
        assertFalse(harness.host.state.isCallLogLoading)
        assertEquals(listOf(cachedGroup), harness.host.state.callLogGroups)
        assertTrue(harness.host.state.hasCallLogPermission)
        assertTrue(harness.host.state.hasCallPhonePermission)
        assertEquals(1, harness.host.renderCount)
        assertEquals(1, harness.executor.pendingTaskCount)
        assertEquals(0, harness.main.pendingImmediateTaskCount)
        assertTrue(harness.host.permissionRequests.isEmpty())
        assertSentinels(harness.host.state)
        verify(exactly = 2) { harness.callLogRepository.hasReadCallLogPermission() }
        verify(exactly = 1) { harness.dialerRepository.hasCallPhonePermission() }
        verify(exactly = 1) { harness.contactSearchRepository.invalidate() }
        verify(exactly = 0) { harness.callLogRepository.readRecentCalls(any()) }
        verify(exactly = 0) { harness.main.handler.post(any<Runnable>()) }
    }

    /** 无缓存时必须先显示 loading，再在主线程投递后落地分组并结束 loading。 */
    @Test
    fun openCallLog_withoutCache_appliesBackgroundResultAndFinishesLoading() {
        val entry = CallLogEntry(
            callId = 41L,
            number = "10041",
            dateMillis = 4_100L,
            durationSeconds = 0L,
            type = MISSED_CALL_TYPE,
            isNew = true,
            displayName = "Caller",
        )
        val harness = CallHarness(initialState = sentinelState())
        every { harness.callLogRepository.hasReadCallLogPermission() } returns true
        every { harness.dialerRepository.hasCallPhonePermission() } returns true
        every { harness.contactSearchRepository.invalidate() } just Runs
        every { harness.callLogRepository.readRecentCalls(any()) } returns listOf(entry)
        every {
            harness.callLogRepository.markCallsAcknowledged(listOf(41L))
        } returns false

        harness.controller.openCallLog()

        assertTrue(harness.host.state.isCallLogLoading)
        assertTrue(harness.host.state.callLogGroups.isEmpty())
        assertEquals(1, harness.host.renderCount)
        assertEquals(1, harness.executor.pendingTaskCount)
        harness.executor.runNext()
        assertTrue(harness.host.state.isCallLogLoading)
        assertEquals(1, harness.host.renderCount)
        assertEquals(1, harness.main.pendingImmediateTaskCount)

        harness.main.runNextImmediate()

        assertFalse(harness.host.state.isCallLogLoading)
        assertEquals(1, harness.host.state.callLogGroups.size)
        assertEquals(41L, harness.host.state.callLogGroups.single().callId)
        assertEquals("Caller", harness.host.state.callLogGroups.single().displayTitle)
        assertEquals(2, harness.host.renderCount)
        assertTrue(harness.host.communicationRefreshCalls.isEmpty())
        assertSentinels(harness.host.state)
        verify(exactly = 1) { harness.callLogRepository.readRecentCalls() }
        verify(exactly = 1) {
            harness.callLogRepository.markCallsAcknowledged(listOf(41L))
        }
        verify(exactly = 1) { harness.main.handler.post(any<Runnable>()) }
        verify(exactly = 0) {
            harness.main.handler.postDelayed(any<Runnable>(), any<Long>())
        }
        verify(exactly = 0) { harness.main.handler.removeCallbacks(any<Runnable>()) }
    }

    /** 创建带跨域哨兵的最小 Launcher 状态。 */
    private fun sentinelState(): LauncherState = LauncherState(
        currentTimeText = SENTINEL_TIME,
        notificationCount = SENTINEL_NOTIFICATION_COUNT,
        screenUsageTimeText = SENTINEL_SCREEN_USAGE,
    )

    /** 断言电话编排没有改写无关状态切片。 */
    private fun assertSentinels(state: LauncherState) {
        assertEquals(SENTINEL_TIME, state.currentTimeText)
        assertEquals(SENTINEL_NOTIFICATION_COUNT, state.notificationCount)
        assertEquals(SENTINEL_SCREEN_USAGE, state.screenUsageTimeText)
    }

    /** 构造已有缓存场景使用的通话分组。 */
    private fun callGroup(id: Long, number: String): CallLogGroup = CallLogGroup(
        callId = id,
        number = number,
        displayTitle = number,
        dateMillis = id,
        durationSeconds = 0L,
        type = MISSED_CALL_TYPE,
        callCount = 1,
        hasNew = false,
        callIds = listOf(id),
    )

    private companion object {
        /** 用于检查跨域字段保留的时间哨兵。 */
        const val SENTINEL_TIME = "07:30"

        /** 用于检查跨域字段保留的通知计数哨兵。 */
        const val SENTINEL_NOTIFICATION_COUNT = 37

        /** 用于检查跨域字段保留的屏幕使用时长哨兵。 */
        const val SENTINEL_SCREEN_USAGE = "09:09"

        /** Android CallLog 未接来电类型的稳定数据库值。 */
        const val MISSED_CALL_TYPE = 3
    }
}

/** 汇总严格仓库 mock、可控队列和电话 Host。 */
private class CallHarness(initialState: LauncherState) {

    /** Controller 使用的严格通话记录仓库 mock。 */
    val callLogRepository: CallLogRepository = mockk()

    /** Controller 使用的严格拨号仓库 mock。 */
    val dialerRepository: DialerRepository = mockk()

    /** Controller 使用的严格联系人搜索仓库 mock。 */
    val contactSearchRepository: ContactSearchRepository = mockk()

    /** Controller 使用的严格联系人权限仓库 mock。 */
    val contactDirectoryRepository: ContactDirectoryRepository = mockk()

    /** 显式推进的后台队列。 */
    val executor = QueuedControllerExecutor()

    /** 显式推进的主线程 Handler 队列。 */
    val main = ControlledMainHandler()

    /** 记录 Controller 可观察副作用的电话宿主。 */
    val host = FakeCallHost(initialState)

    /** 使用生产实现的被测 Controller。 */
    val controller = CallController(
        callLogRepository = callLogRepository,
        dialerRepository = dialerRepository,
        contactSearchRepository = contactSearchRepository,
        contactDirectoryRepository = contactDirectoryRepository,
        backgroundExecutor = executor,
        mainHandler = main.handler,
        host = host,
    )
}

/** 记录电话 Controller 状态写入与宿主回调的可观察 fake。 */
private class FakeCallHost(initialState: LauncherState) : CallController.Host {

    /** Controller 读写的唯一状态快照。 */
    override var state: LauncherState = initialState

    /** 已请求的重绘次数。 */
    var renderCount: Int = 0

    /** 通信状态刷新参数的调用记录。 */
    val communicationRefreshCalls = mutableListOf<Boolean>()

    /** 权限申请参数的调用记录。 */
    val permissionRequests = mutableListOf<List<String>>()

    /** 状态栏临时消息记录。 */
    val statusMessages = mutableListOf<String>()

    /** 测试期间宿主是否保持活动。 */
    var active: Boolean = true

    /** 已请求的待机检查次数。 */
    var idleCheckCount: Int = 0

    /** 记录一次渲染请求。 */
    override fun render() {
        renderCount += 1
    }

    /** 返回测试控制的宿主活动状态。 */
    override fun isActive(): Boolean = active

    /** 记录通信摘要刷新及其重绘参数。 */
    override fun refreshCommunicationStatus(render: Boolean) {
        communicationRefreshCalls += render
    }

    /** 记录电话权限请求。 */
    override fun requestCallPermissions(permissions: Array<String>) {
        permissionRequests += permissions.toList()
    }

    /** 记录一次状态栏临时消息。 */
    override fun showStatusBarMessage(message: String) {
        statusMessages += message
    }

    /** 记录一次待机检查排程。 */
    override fun scheduleIdleCheck() {
        idleCheckCount += 1
    }
}
