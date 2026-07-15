package com.purride.pixelui.internal

import com.purride.pixelui.Listenable
import com.purride.pixelui.Widget

/**
 * retained build tree 的 owner。
 *
 * 这层负责 build 生命周期调度和跨 element 的共享协作，并把根节点管理、
 * dirty 调度、listenable 注册等局部职责交给独立 helper。
 */
internal class BuildOwner(
    private val onVisualUpdate: () -> Unit,
    private val elementChildUpdater: ElementChildUpdater,
    /** 已由 ErrorBoundary 生成 fallback 的 build 错误通知。 */
    private val onRecoveredBuildError: (Throwable, String) -> Unit,
) {
    private val rootElementSlot = RootElementSlot(elementChildUpdater)
    private val dirtyElementScheduler = DirtyElementScheduler()
    private val listenableRegistry = ListenableDependencyRegistry(
        requestVisualUpdate = ::requestVisualUpdate,
    )
    /** Failures deferred until the current retained-tree mutation has committed every slot. */
    private val teardownFailures = TeardownFailureCollector()

    /**
     * 当前保留的根 element。
     */
    val rootElement: Element?
        get() = rootElementSlot.element

    /**
     * 用新的根 widget 刷新 retained 树根节点。
     */
    fun updateRootWidget(widget: Widget) {
        rootElementSlot.update(owner = this, widget = widget)
    }

    /**
     * 执行当前 build scope 内所有 dirty element 的重建。
     */
    fun buildScope() {
        dirtyElementScheduler.buildScope()
    }

    /**
     * 在一次完整 render pass 内重建 retained 树：先用新根 widget reconcile，再排空
     * dirty 队列。
     *
     * 关键不变量：reconcile（[updateRootWidget] → `Element.update` → 各 element 的
     * markNeedsBuild）与 build 期间标脏的 element，都会被本次 [buildScope] 在同一帧
     * 内处理完，因此 pass 期间 [requestVisualUpdate] 被抑制、不再向宿主多排一帧。
     * 否则「每帧 reconcile 都 markNeedsBuild → requestVisualUpdate → 下一帧」会让
     * 宿主 postInvalidateOnAnimation 永不收敛——任何含 widget 树的页面在静止时也会
     * 满帧空转重绘。
     */
    fun renderPass(widget: Widget) {
        inRenderPass = true
        /** Pass-local failures combine normal build errors with deferred teardown callbacks. */
        val passFailures = TeardownFailureCollector()
        try {
            updateRootWidget(widget)
            buildScope()
        } catch (failure: Throwable) {
            passFailures.record(failure)
        } finally {
            inRenderPass = false
        }
        teardownFailures.takeFailure()?.let(passFailures::record)
        passFailures.throwIfAny()
    }

    /**
     * render 阶段失败后，尝试把异常交给最近的 PixelErrorBoundary。
     */
    fun recoverFromRenderError(error: Throwable): Element? {
        /** Recovery-local collector also reports teardown triggered by fallback replacement. */
        val failures = TeardownFailureCollector()
        /** Recovered root captured only when the nearest boundary accepted the render failure. */
        var recoveredRoot: Element? = null
        failures.capture {
            val recovered = rootElement?.recoverFromRenderError(error) == true
            recoveredRoot = if (recovered) rootElement else null
        }
        teardownFailures.takeFailure()?.let(failures::record)
        failures.throwIfAny()
        return recoveredRoot
    }

    /**
     * 把一个 element 加入下一轮 build 调度。
     */
    fun scheduleBuildFor(element: Element) {
        dirtyElementScheduler.schedule(element)
        requestVisualUpdate()
    }

    /** Removes a terminal Element from any still-pending build queue entry. */
    internal fun unscheduleBuildFor(element: Element) {
        dirtyElementScheduler.unschedule(element)
    }

    /** Defers one terminal callback failure until the current retained mutation is committed. */
    internal fun recordTeardownFailure(failure: Throwable) {
        teardownFailures.record(failure)
    }

    /** 发布已经成功构造 fallback 的 build 错误，不暴露 Widget 实例。 */
    internal fun reportRecoveredBuildError(failure: Throwable, widgetType: String) {
        onRecoveredBuildError(failure, widgetType)
    }

    /**
     * 请求宿主执行一次视觉更新。
     *
     * render pass 内标脏的 element 由本次 [buildScope] 处理完，无需另排一帧，故在
     * pass 内直接返回；只有 pass 外的标脏（触摸、定时器、帧间 listenable 变化等
     * 真实的带外事件）才请求新帧。
     */
    fun requestVisualUpdate() {
        if (inRenderPass) {
            return
        }
        onVisualUpdate()
    }

    private var inRenderPass = false

    /**
     * 注册一个 listenable 依赖。
     */
    fun registerListenableDependency(
        element: Element,
        listenable: Listenable,
    ) {
        listenableRegistry.register(
            element = element,
            listenable = listenable,
        )
    }

    /**
     * 清理一个 element 持有的 listenable 依赖。
     */
    fun clearListenableDependencies(element: Element) {
        listenableRegistry.clear(element)
    }

    /**
     * 更新一个父节点下的 child element。
     */
    fun updateChild(
        parent: Element?,
        current: Element?,
        newWidget: Widget?,
    ): Element? {
        return elementChildUpdater.updateChild(
            parent = parent,
            current = current,
            newWidget = newWidget,
            owner = this,
        )
    }

    /**
     * 释放 retained tree 和 owner 持有的所有调度状态。
     */
    fun dispose() {
        /** Owner-terminal collector ensures every registry is empty before reporting failure. */
        val failures = TeardownFailureCollector()
        failures.capture { rootElementSlot.clear() }
        failures.capture { listenableRegistry.dispose() }
        failures.capture { dirtyElementScheduler.clear() }
        inRenderPass = false
        teardownFailures.takeFailure()?.let(failures::record)
        failures.throwIfAny()
    }

    fun collectDiagnostics(): BuildOwnerDiagnostics {
        return BuildOwnerDiagnostics(
            hasRoot = rootElement != null,
            elementDiagnostics = rootElement?.collectDiagnostics().orEmpty(),
            dirtyQueueDiagnostics = dirtyElementScheduler.collectDiagnostics(),
        )
    }

    /** Exposes a primitive rebuild counter for allocation-bounded frame diagnostics. */
    fun cumulativeRebuiltElementCount(): Long = dirtyElementScheduler.cumulativeRebuiltElementCount()

    /** Returns every retained Widget in root-first order for internal testing and inspection. */
    fun collectWidgets(): List<Widget> {
        return rootElement?.collectWidgets().orEmpty()
    }
}

/**
 * Retained build owner 调试快照。
 */
internal data class BuildOwnerDiagnostics(
    val hasRoot: Boolean,
    val elementDiagnostics: List<ElementDiagnosticsNode>,
    val dirtyQueueDiagnostics: DirtyElementSchedulerDiagnostics,
)
