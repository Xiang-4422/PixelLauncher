package com.purride.pixellauncherv2.app

import android.Manifest
import android.os.Handler
import android.util.Log
import com.purride.pixellauncherv2.data.CallLogRepository
import com.purride.pixellauncherv2.data.DialerRepository
import com.purride.pixellauncherv2.launcher.CallLogModel
import com.purride.pixellauncherv2.launcher.DialInputModel
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.launcher.LauncherStateTransitions
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

/**
 * 拨号模块的运行时编排，与 [SmsController] 同构。
 *
 * 纯状态转移仍由 [LauncherStateTransitions] 提供；本类只承担命令式胶水
 * （仓库调用、线程、权限申请）。与宿主的耦合通过 [Host] 收敛。
 */
internal class CallController(
    private val callLogRepository: CallLogRepository,
    private val dialerRepository: DialerRepository,
    private val backgroundExecutor: ExecutorService,
    private val mainHandler: Handler,
    private val host: Host,
) {

    /** 宿主（[MainActivity]）需要提供的钩子。 */
    interface Host {
        /** 共享的 Launcher 状态；拨号编排读写它，宿主持有真值。 */
        var state: LauncherState

        /** 把当前状态提交到 pixel-engine 渲染。 */
        fun render()

        /** Activity 仍存活（未销毁/未结束）时为 true，用于异步回调的有效性校验。 */
        fun isActive(): Boolean

        /** 刷新未接来电/未读短信计数（清除未接标记后需要同步）。 */
        fun refreshCommunicationStatus(render: Boolean)

        /** 申请拨号相关运行时权限。 */
        fun requestCallPermissions(permissions: Array<String>)

        /** 全局状态栏临时消息（自动消失）。 */
        fun showStatusBarMessage(message: String)

        fun scheduleIdleCheck()
    }

    /** 已请求过一次权限就不再自动弹窗，避免反复打扰。 */
    private var permissionRequestedThisSession = false

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** 前台时监听通话记录变化。 */
    fun start() {
        callLogRepository.start(::onCallLogChanged)
    }

    /** 后台时停止监听。 */
    fun stop() {
        mainHandler.removeCallbacks(changeDebounceRunnable)
        callLogRepository.stop()
    }

    // ── Module open/close ─────────────────────────────────────────────────────

    /** 打开通话记录页；缺少读取权限时先申请。 */
    fun openCallLog() {
        refreshCallCapability(render = false)
        if (!callLogRepository.hasReadCallLogPermission()) {
            requestMissingPermissions()
            return
        }
        val hasData = host.state.callLogGroups.isNotEmpty()
        host.state = LauncherStateTransitions.showCallLog(
            host.state.copy(isCallLogLoading = !hasData),
        )
        host.render()
        refreshCallLog(render = true, acknowledgeNewCalls = true)
    }

    fun closeCallLog() {
        host.state = LauncherStateTransitions.hideCallLog(host.state)
        host.render()
        host.scheduleIdleCheck()
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /** 回电：列表点按与按键回车共用。 */
    fun callNumber(number: String) {
        val trimmed = number.trim()
        if (trimmed.isBlank()) {
            host.showStatusBarMessage(STATUS_UNKNOWN_NUMBER)
            return
        }
        if (!dialerRepository.hasCallPhonePermission()) {
            requestMissingPermissions()
            return
        }
        // placeCall 会拉起系统电话栈，可能阻塞：放到后台线程。
        runInBackground {
            val result = dialerRepository.placeCall(trimmed)
            if (result.isFailure) {
                mainHandler.post {
                    if (host.isActive()) {
                        host.showStatusBarMessage(STATUS_CALL_FAILED)
                    }
                }
            }
        }
    }

    // ── Dial pad ──────────────────────────────────────────────────────────────

    /** 切换最近通话 / 拨号盘。 */
    fun selectPage(index: Int) {
        val nextState = LauncherStateTransitions.selectCallPage(host.state, index)
        if (nextState.callPageIndex == host.state.callPageIndex) {
            return
        }
        host.state = nextState
        host.render()
    }

    /** 拨号盘按键：追加一个字符。 */
    fun appendDialDigit(digit: Char) {
        applyDialInput(DialInputModel.append(host.state.dialInput, digit))
    }

    /** 删除末位。 */
    fun backspaceDialInput() {
        applyDialInput(DialInputModel.backspace(host.state.dialInput))
    }

    /** 清空输入。 */
    fun clearDialInput() {
        applyDialInput("")
    }

    /** 呼叫当前拨号盘输入。 */
    fun callDialInput() {
        val input = host.state.dialInput
        if (!DialInputModel.isCallable(input)) {
            return
        }
        callNumber(input)
    }

    /**
     * 落地新的拨号输入，并异步解析联系人名。
     *
     * 联系人查询是跨进程 IO，不能占用主线程；回填时校验输入未变，
     * 避免快速连按后旧结果盖掉新号码对应的姓名。
     */
    private fun applyDialInput(input: String) {
        val nextState = LauncherStateTransitions.updateDialInput(host.state, input)
        if (nextState === host.state) {
            return
        }
        host.state = nextState
        host.render()
        if (input.isBlank()) {
            return
        }
        runInBackground {
            val name = callLogRepository.contactNameFor(input)
            if (name.isBlank()) {
                return@runInBackground
            }
            mainHandler.post {
                if (!host.isActive()) {
                    return@post
                }
                host.state = LauncherStateTransitions.updateDialContactName(
                    state = host.state,
                    input = input,
                    contactName = name,
                )
                host.render()
            }
        }
    }

    /** 按键导航：移动选中项。 */
    fun moveSelection(delta: Int) {
        host.state = LauncherStateTransitions.moveCallLogSelection(host.state, delta)
        host.render()
    }

    /** 按键回车：回电当前选中项。 */
    fun callSelected() {
        val group = host.state.callLogGroups.getOrNull(host.state.callLogSelectedIndex) ?: return
        callNumber(group.number)
    }

    // ── Permission plumbing ───────────────────────────────────────────────────

    /** 申请缺失的拨号权限（读写通话记录 + 发起通话）。 */
    fun requestMissingPermissions() {
        if (permissionRequestedThisSession) {
            host.showStatusBarMessage(STATUS_NEED_PERMISSION)
            return
        }
        val missing = buildList {
            if (!callLogRepository.hasReadCallLogPermission()) add(Manifest.permission.READ_CALL_LOG)
            if (!callLogRepository.hasWriteCallLogPermission()) add(Manifest.permission.WRITE_CALL_LOG)
            if (!dialerRepository.hasCallPhonePermission()) add(Manifest.permission.CALL_PHONE)
        }
        if (missing.isEmpty()) {
            return
        }
        permissionRequestedThisSession = true
        host.requestCallPermissions(missing.toTypedArray())
    }

    /** 拨号权限申请返回后调用。 */
    fun onPermissionsResult() {
        refreshCallCapability(render = false)
        if (callLogRepository.hasReadCallLogPermission()) {
            openCallLog()
        } else {
            host.render()
        }
    }

    /** 同步拨号相关能力（权限）。 */
    fun refreshCallCapability(render: Boolean) {
        host.state = LauncherStateTransitions.updateCallCapability(
            state = host.state,
            hasCallPhonePermission = dialerRepository.hasCallPhonePermission(),
        )
        if (render) {
            host.render()
        }
    }

    // ── Internal orchestration ────────────────────────────────────────────────

    /**
     * 读取通话记录并同步到状态。
     *
     * [acknowledgeNewCalls] 为 true 时顺带把未接来电标记为已确认——用户已经
     * 看到列表，Home 上的未接角标应当随之清零。
     */
    private fun refreshCallLog(render: Boolean, acknowledgeNewCalls: Boolean) {
        runInBackground {
            val entries = callLogRepository.readRecentCalls()
            val acknowledged = if (acknowledgeNewCalls) {
                callLogRepository.markCallsAcknowledged(CallLogModel.newCallIds(entries))
            } else {
                false
            }
            val groups = CallLogModel.group(entries)
            mainHandler.post {
                if (!host.isActive()) {
                    return@post
                }
                host.state = LauncherStateTransitions.updateCallLogGroups(host.state, groups)
                if (render) {
                    host.render()
                }
                if (acknowledged) {
                    host.refreshCommunicationStatus(render = true)
                }
            }
        }
    }

    /**
     * 内容观察者回调（主线程）。标记已确认会触发连环 onChange，
     * 与短信一致合并 300ms 窗口内的变更。
     */
    private fun onCallLogChanged() {
        mainHandler.removeCallbacks(changeDebounceRunnable)
        mainHandler.postDelayed(changeDebounceRunnable, CHANGE_DEBOUNCE_MS)
    }

    private val changeDebounceRunnable = Runnable {
        if (!host.isActive()) {
            return@Runnable
        }
        // 只在通话记录页可见时重绘；否则静默更新数据，等下次打开即是最新。
        refreshCallLog(
            render = host.state.mode == LauncherMode.DIALER,
            acknowledgeNewCalls = false,
        )
    }

    /**
     * 提交后台任务。宿主销毁时执行器被 shutdownNow：此后到达的异步回调
     * 再提交任务会抛 RejectedExecutionException，此时结果已无处落地。
     */
    private fun runInBackground(task: () -> Unit) {
        try {
            backgroundExecutor.execute {
                // 任务内部的未捕获异常会杀掉整个进程——对 Launcher 而言就是桌面
                // 消失。仓库层再怎么出错也只记日志，不允许带走宿主。
                runCatching(task).onFailure { error ->
                    Log.w(LOG_TAG, "background task failed", error)
                }
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    private companion object {
        const val LOG_TAG = "CallController"
        const val STATUS_CALL_FAILED = "CALL FAILED"
        const val STATUS_UNKNOWN_NUMBER = "NO NUMBER"
        const val STATUS_NEED_PERMISSION = "NEED CALL PERMISSION"

        /** 通话记录变更防抖窗口，与短信保持一致。 */
        const val CHANGE_DEBOUNCE_MS = 300L
    }
}
