package com.purride.pixelbenchmark.target

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Looper
import com.purride.pixelui.PixelHostLifecycleState
import com.purride.pixelui.PixelHostSetup

/**
 * 接收基准测试进程发出的显式终态诊断请求。
 *
 * 该 Receiver 只存在于 benchmark target，正式 SDK 与消费者应用不会打包它。请求必须使用
 * 精确 action；成功结果通过 `am broadcast` 的 result data 返回稳定键值协议。
 */
class PixelBenchmarkDiagnosticsReceiver : BroadcastReceiver() {
    /** 在目标应用主线程释放当前 Host，并返回不含对象引用的资源计数。 */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CollectAction) {
            resultCode = ResultRejected
            resultData = "error=unexpected_action"
            return
        }
        runCatching { PixelBenchmarkHostRegistry.disposeCurrentAndCollect() }
            .onSuccess { diagnostics ->
                resultCode = ResultOk
                resultData = diagnostics.toWireValue()
            }
            .onFailure { throwable ->
                resultCode = ResultFailed
                /** Shell 协议只保留安全 ASCII，避免异常文本破坏键值解析。 */
                val safeMessage = throwable.message
                    .orEmpty()
                    .replace(UnsafeWireCharacter, "_")
                    .take(MaximumErrorLength)
                resultData = "error=${safeMessage.ifEmpty { "unknown" }}"
            }
    }

    /** 与 instrumentation 共享的显式广播协议常量。 */
    companion object {
        /** 触发当前 Host 终态释放和资源快照的唯一 action。 */
        const val CollectAction: String =
            "com.purride.pixelbenchmark.target.action.COLLECT_TERMINAL_DIAGNOSTICS"

        /** 成功收集诊断时返回给 shell 的结果码。 */
        private const val ResultOk: Int = 0

        /** action 不符合契约时返回给 shell 的结果码。 */
        private const val ResultRejected: Int = 2

        /** 释放或快照失败时返回给 shell 的结果码。 */
        private const val ResultFailed: Int = 3

        /** 错误消息允许保留的最大字符数。 */
        private const val MaximumErrorLength: Int = 160

        /** shell 键值协议不能直接容纳的字符集合。 */
        private val UnsafeWireCharacter: Regex = Regex("[^A-Za-z0-9_.-]")
    }
}

/** 保存目标进程当前真实 Host，使显式广播能在同一主线程完成终态验收。 */
internal object PixelBenchmarkHostRegistry {
    /** 当前 Activity 创建且尚未解除注册的 Host 装配。 */
    private var currentSetup: PixelHostSetup? = null

    /** 当前 Host 已完成终态释放后缓存的基础类型快照。 */
    private var terminalDiagnostics: PixelBenchmarkTerminalDiagnostics? = null

    /** 注册新 Activity 的 Host；若异常残留旧 Host，会先确定性释放旧所有权。 */
    fun register(setup: PixelHostSetup) {
        requireMainThread()
        currentSetup?.takeIf { existing -> existing !== setup }?.dispose()
        currentSetup = setup
        terminalDiagnostics = null
    }

    /** 幂等释放当前 Host，并返回释放后的 callback、listener、ticker 与 retained 树计数。 */
    fun disposeCurrentAndCollect(): PixelBenchmarkTerminalDiagnostics {
        requireMainThread()
        terminalDiagnostics?.let { diagnostics -> return diagnostics }
        /** 当前前台基准 Activity 注册的真实 Host 装配。 */
        val setup = checkNotNull(currentSetup) { "no_active_benchmark_host" }
        setup.dispose()
        /** 被销毁 Host 在公开 inspector 中留下的终态只读快照。 */
        val inspector = setup.hostView.inspect(includeFrameStats = false)
        /** 被销毁 Host 的 frame scope 精确资源计数。 */
        val frameScope = setup.hostView.frameScopeDiagnostics
        /** 被销毁 Host 的生命周期终态计数。 */
        val lifecycle = setup.hostView.lifecycleDiagnostics
        /** inspector 中所有仍被保留的输入、手势和语义目标总数。 */
        val retainedTargetCount = inspector.targetCounts.run {
            click + pager + list + scrollbar + refresh + textInput + slider + semantics
        }
        return PixelBenchmarkTerminalDiagnostics(
            lifecycleDestroyed = lifecycle.lifecycleState == PixelHostLifecycleState.Destroyed,
            destroyCount = lifecycle.destroyCount,
            frameScopeDisposed = frameScope.isDisposed,
            pendingCallbackCount = frameScope.pendingCallbackCount,
            frameListenerCount = frameScope.frameListenerCount,
            activeTickerCount = frameScope.activeTickerCount,
            liveTickerCount = frameScope.liveTickerCount,
            sourceFramePending = frameScope.sourceFramePending,
            retainedElementRoot = inspector.elementTree != EmptyElementTree,
            retainedRenderRoot = inspector.renderTree != EmptyRenderTree,
            retainedTargetCount = retainedTargetCount,
            pendingBuild = inspector.hasPendingBuild,
            focusedTextInput = inspector.focusedTextInput,
            activePagerCount = inspector.activePagerCount,
            activeListCount = inspector.activeListCount,
        ).also { diagnostics -> terminalDiagnostics = diagnostics }
    }

    /** Activity 终结时确保 Host 已释放，并只解除与同一装配实例匹配的注册。 */
    fun disposeAndUnregister(setup: PixelHostSetup) {
        requireMainThread()
        if (currentSetup === setup) {
            disposeCurrentAndCollect()
            currentSetup = null
            terminalDiagnostics = null
        } else {
            setup.dispose()
        }
    }

    /** 拒绝非主线程访问，保持 Android View 和 retained runtime 的线程契约。 */
    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "benchmark_host_registry_requires_main" }
    }

    /** 释放后 Element runtime 的公开稳定空树文本。 */
    private const val EmptyElementTree: String = "<no root>"

    /** 释放后 Render pipeline 的公开稳定空树文本。 */
    private const val EmptyRenderTree: String = "<no render root>"
}

/**
 * 一次 Host 终态释放后的基础类型资源快照。
 *
 * 所有字段都进入稳定 shell 协议，长时测试会逐轮要求布尔值为真、资源计数为零。
 */
internal data class PixelBenchmarkTerminalDiagnostics(
    /** Host lifecycle 是否到达不可逆 Destroyed。 */
    val lifecycleDestroyed: Boolean,
    /** Host 有效 destroy 次数，正确终态必须恰好为一。 */
    val destroyCount: Long,
    /** Host 私有 frame scope 是否完成终态释放。 */
    val frameScopeDisposed: Boolean,
    /** 仍等待下一帧的一次性 callback 数量。 */
    val pendingCallbackCount: Int,
    /** 仍注册在 Host scope 中的重复帧 listener 数量。 */
    val frameListenerCount: Int,
    /** 仍请求帧的活跃 ticker 数量。 */
    val activeTickerCount: Int,
    /** 尚未 dispose 的全部 ticker 数量。 */
    val liveTickerCount: Int,
    /** 是否仍持有一个上游帧请求。 */
    val sourceFramePending: Boolean,
    /** retained Element 根是否仍存在。 */
    val retainedElementRoot: Boolean,
    /** attached RenderObject 根是否仍存在。 */
    val retainedRenderRoot: Boolean,
    /** 仍被 Host 输入/语义层持有的目标总数。 */
    val retainedTargetCount: Int,
    /** retained scheduler 是否仍有待构建节点。 */
    val pendingBuild: Boolean,
    /** Android 文本输入桥是否仍持有焦点目标。 */
    val focusedTextInput: Boolean,
    /** 仍处于运动状态的 Pager 数量。 */
    val activePagerCount: Int,
    /** 仍处于运动状态的 List 数量。 */
    val activeListCount: Int,
) {
    /** 编码为无空格、无引用的稳定键值协议，供 `am broadcast` 输出解析。 */
    fun toWireValue(): String {
        return listOf(
            "lifecycleDestroyed=${lifecycleDestroyed.toWireInt()}",
            "destroyCount=$destroyCount",
            "frameScopeDisposed=${frameScopeDisposed.toWireInt()}",
            "pendingCallbacks=$pendingCallbackCount",
            "frameListeners=$frameListenerCount",
            "activeTickers=$activeTickerCount",
            "liveTickers=$liveTickerCount",
            "sourceFramePending=${sourceFramePending.toWireInt()}",
            "retainedElementRoot=${retainedElementRoot.toWireInt()}",
            "retainedRenderRoot=${retainedRenderRoot.toWireInt()}",
            "retainedTargets=$retainedTargetCount",
            "pendingBuild=${pendingBuild.toWireInt()}",
            "focusedTextInput=${focusedTextInput.toWireInt()}",
            "activePagers=$activePagerCount",
            "activeLists=$activeListCount",
        ).joinToString(separator = ",")
    }

    /** 把布尔终态转换为 shell 协议使用的 0/1。 */
    private fun Boolean.toWireInt(): Int = if (this) 1 else 0
}
