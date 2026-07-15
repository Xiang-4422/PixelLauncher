package com.purride.pixelui

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.services.PixelClock
import com.purride.pixelui.services.PixelErrorReporter
import com.purride.pixelui.services.PixelLogger
import com.purride.pixelui.host.PixelFrameDiagnosticsRecorder
import com.purride.pixelui.host.PixelHostFrameLoop
import com.purride.pixelui.internal.HostRootWidget
import com.purride.pixelui.internal.InspectorNodeAssociation
import com.purride.pixelui.internal.PixelFocusDispatcher
import com.purride.pixelui.internal.PixelPagerTarget
import com.purride.pixelui.internal.PixelRenderResult
import com.purride.pixelui.internal.RenderObject
import com.purride.pixelui.internal.PixelUiRuntime

/**
 * Coordinates retained runtime rendering and frame-scoped host state.
 *
 * PixelHostView still owns Android drawing and input entry points; this class owns
 * widget runtime render, frame stepping, page-change dispatch, and frame stats emission.
 */
internal class PixelHostRenderCoordinator(
    private val host: PixelHostView,
    private val textInputCoordinator: PixelHostTextInputCoordinator,
) {
    /** 动态读取当前绑定 Engine 的时钟，切换 Engine 后无需重建 retained runtime。 */
    private val engineClock = PixelHostEngineClock(host)

    /** 是否允许推进动态渲染；由 Host lifecycle 的两个状态轴共同决定。 */
    private var lifecycleActive: Boolean = false

    /** pause/detach 期间被合并、等待 resume 的视觉更新。 */
    private var pendingVisualUpdate: Boolean = false

    /** resume 后首帧是否应丢弃后台 wall-clock delta。 */
    private var resetDeltaOnNextFrame: Boolean = true

    /** terminal destroy 是否已释放 runtime。 */
    private var disposed: Boolean = false

    /** Retained runtime 的带外更新统一经过 lifecycle-aware gating。 */
    private val runtime = PixelUiRuntime(
        onVisualUpdate = ::requestVisualUpdate,
        clock = engineClock,
        errorReporter = PixelErrorReporter { event ->
            host.engine.services.errorReporter.report(event)
        },
        logger = PixelLogger { event ->
            host.engine.services.logger.log(event)
        },
    )

    /** Runtime-local focus owner used by every Host input channel. */
    val focusOwner: PixelFocusDispatcher
        get() = runtime.focusOwner

    /** Frame delta and render statistics for this Host only. */
    private val frameLoop = PixelHostFrameLoop(engineClock)

    /** Opt-in full-pipeline recorder retained once per Host and inactive by default. */
    private val frameDiagnosticsRecorder = PixelFrameDiagnosticsRecorder.forAndroidHost()

    /** Whether the current Android onDraw owns an active diagnostics frame. */
    private var frameDiagnosticsActive: Boolean = false

    /** Latest declarative root provider installed on this Host. */
    private var contentProvider: RootWidgetProvider? = null

    /** 最近发布给绘制与输入层的同一份 target snapshot。 */
    var lastRenderResult: PixelRenderResult? = null
        set(value) {
            field = value
            if (value != null) {
                host.reconcileInteractionTargets(value)
            }
        }

    /** 在 pause/resume 或 attach/detach 后切换渲染能力，并合并恢复帧。 */
    fun setLifecycleActive(active: Boolean) {
        if (disposed || lifecycleActive == active) return
        lifecycleActive = active
        if (active) {
            resetDeltaOnNextFrame = true
            pendingVisualUpdate = false
            host.postInvalidateOnAnimation()
        }
    }

    fun setContent(provider: RootWidgetProvider) {
        if (disposed) return
        contentProvider = provider
        requestVisualUpdate()
    }

    fun submitFrame(
        pixelBuffer: PixelBuffer,
        screenProfile: ScreenProfile,
        backgroundColor: PixelColor,
    ) {
        if (disposed) return
        host.screenProfile = screenProfile
        host.bezelColor = backgroundColor
        lastRenderResult = PixelRenderResult(
            buffer = pixelBuffer,
            clickTargets = emptyList(),
            pagerTargets = emptyList(),
            listTargets = emptyList(),
            scrollbarTargets = emptyList(),
            refreshTargets = emptyList(),
            textInputTargets = emptyList(),
            sliderTargets = emptyList(),
            semanticsNodes = emptyList(),
        )
        requestVisualUpdate()
    }

    /** Starts legacy whole-draw timing and optional full-pipeline diagnostics at onDraw entry. */
    fun beginAndroidFrame() {
        frameLoop.beginPaint()
        frameDiagnosticsActive = host.frameDiagnosticsEnabled || host.frameDiagnosticsObserver != null
        if (frameDiagnosticsActive) {
            /** Budget derived from the same immutable capability snapshot rendered by this frame. */
            val frameBudgetNanos = PixelFrameDiagnosticsRecorder.frameBudgetNanos(
                host.effectiveCapabilities.refreshRateHz,
            )
            frameDiagnosticsRecorder.beginFrame(frameBudgetNanos = frameBudgetNanos)
        }
    }

    /** Starts one exclusive Android View drawing segment when diagnostics are active. */
    fun beginAndroidDraw() {
        if (frameDiagnosticsActive) frameDiagnosticsRecorder.beginAndroidDraw()
    }

    /** Ends one exclusive Android View drawing segment when diagnostics are active. */
    fun endAndroidDraw() {
        if (frameDiagnosticsActive) frameDiagnosticsRecorder.endAndroidDraw()
    }

    /** Starts PixelBuffer-to-Canvas submission when diagnostics are active. */
    fun beginBufferSubmit() {
        if (frameDiagnosticsActive) frameDiagnosticsRecorder.beginBufferSubmit()
    }

    /** Ends PixelBuffer-to-Canvas submission when diagnostics are active. */
    fun endBufferSubmit() {
        if (frameDiagnosticsActive) frameDiagnosticsRecorder.endBufferSubmit()
    }

    /** Adds the logical source-pixel count submitted by one Android draw. */
    fun recordBufferSubmit(buffer: PixelBuffer) {
        if (!frameDiagnosticsActive) return
        /** Long multiplication avoids overflow for defensive externally supplied dimensions. */
        val submittedPixelCount = buffer.width.coerceAtLeast(0).toLong() *
            buffer.height.coerceAtLeast(0).toLong()
        frameDiagnosticsRecorder.recordBufferSubmit(submittedPixelCount = submittedPixelCount)
    }

    /**
     * Finishes legacy/full diagnostics and notifies observers at the terminal onDraw boundary.
     *
     * Open phases are closed defensively so an exceptional draw cannot corrupt the next frame.
     */
    fun endAndroidFrame() {
        frameLoop.endPaint()
        /** Legacy snapshot created only when its compatibility observer is installed. */
        val frameStats = if (host.frameStatsObserver != null) frameLoop.snapshotStats() else null
        /** Full snapshot completed before invoking arbitrary consumer observer code. */
        val diagnostics = if (frameDiagnosticsActive) frameDiagnosticsRecorder.finishFrame() else null
        frameDiagnosticsActive = false
        if (frameStats != null) host.frameStatsObserver?.invoke(frameStats)
        if (diagnostics != null) host.frameDiagnosticsObserver?.invoke(diagnostics)
    }

    fun renderFrame(): PixelRenderResult? {
        if (disposed) return null
        if (!lifecycleActive) return lastRenderResult
        val measuredDeltaMs = frameLoop.consumeFrameDeltaMs()
        val frameDeltaMs = if (resetDeltaOnNextFrame) 0L else measuredDeltaMs
        resetDeltaOnNextFrame = false
        stepActivePagers(frameDeltaMs)
        stepActiveLists(frameDeltaMs)
        textInputCoordinator.stepCursorBlink(frameDeltaMs)

        val provider = contentProvider
        val renderResult = if (provider != null) {
            val rootWidget = provider()
            val wrappedRoot = HostRootWidget(
                screenProfile = host.screenProfile,
                physicalWidthPx = host.width,
                physicalHeightPx = host.height,
                textRasterizer = host.textRasterizer,
                windowInsets = host.windowInsets,
                viewInsets = host.viewInsets,
                hostBridge = host.hostBridge,
                hostServices = host.effectiveHostServices,
                themeTokens = host.engine.themeOverride,
                motionVsync = host.tickerProvider,
                capabilities = host.effectiveCapabilities,
                child = rootWidget,
                key = "host-root",
            )
            lastRenderResult = null
            runtime.render(
                root = wrappedRoot,
                logicalWidth = host.screenProfile.logicalWidth,
                logicalHeight = host.screenProfile.logicalHeight,
                framePhaseSink = if (frameDiagnosticsActive) frameDiagnosticsRecorder else null,
            )
        } else {
            lastRenderResult
        }

        if (renderResult != null) {
            lastRenderResult = renderResult
            dispatchPageChanged(renderResult.pagerTargets)
            textInputCoordinator.syncRequestedFocus(renderResult.textInputTargets)
            if (renderResult.pagerTargets.any { it.controller.isActive(it.state) } ||
                renderResult.listTargets.any { it.controller.isActive(it.state) }
            ) {
                requestVisualUpdate()
            }
            textInputCoordinator.scheduleNextCursorBlinkInvalidate()
        }

        return renderResult
    }

    fun dumpElementTree(): String = runtime.dumpElementTree()

    fun dumpRenderTree(): String = runtime.dumpRenderTree()

    fun hasPendingBuild(): Boolean = runtime.hasPendingBuild()

    fun collectInspectorNodeAssociations(): Map<RenderObject, InspectorNodeAssociation> {
        return runtime.collectInspectorNodeAssociations()
    }

    fun snapshotFrameStats(): PixelHostFrameStats = frameLoop.snapshotStats()

    /** Returns the latest completed opt-in full-frame diagnostics snapshot, if any. */
    fun snapshotFrameDiagnostics(): PixelHostFrameDiagnostics? = frameDiagnosticsRecorder.latestDiagnostics

    /** 终态释放 retained runtime、provider closure 与最近一帧 callback targets。 */
    fun dispose() {
        if (disposed) return
        disposed = true
        lifecycleActive = false
        pendingVisualUpdate = false
        contentProvider = null
        runtime.dispose()
        lastRenderResult = null
    }

    /** 活跃时请求下一 Android frame；非活跃时只保留一个待恢复标志。 */
    private fun requestVisualUpdate() {
        if (disposed) return
        if (!lifecycleActive) {
            pendingVisualUpdate = true
            return
        }
        host.postInvalidateOnAnimation()
    }

    private fun stepActivePagers(deltaMs: Long) {
        lastRenderResult?.pagerTargets?.forEach { it.controller.step(it.state, deltaMs) }
    }

    private fun dispatchPageChanged(targets: List<PixelPagerTarget>) {
        targets.forEach { target ->
            val currentPage = target.state.currentPage
            if (currentPage != target.state.lastDispatchedPage) {
                target.state.lastDispatchedPage = currentPage
                target.onPageChanged?.invoke(currentPage)
            }
        }
    }

    private fun stepActiveLists(deltaMs: Long) {
        lastRenderResult?.listTargets?.forEach { target ->
            target.controller.step(target.state, deltaMs, target.viewportHeightPx, target.contentHeightPx)
        }
    }
}

/** 把 Host 当前绑定 Engine 的公开时钟适配给 runtime 与 Android 帧统计。 */
private class PixelHostEngineClock(
    /** 时钟调用时动态读取 Engine 的 Host。 */
    private val host: PixelHostView,
) : PixelClock, com.purride.pixelui.host.MonotonicClock {
    /** 返回当前 Engine 的单调毫秒时间。 */
    override fun uptimeMillis(): Long = host.engine.services.clock.uptimeMillis()

    /** 返回当前 Engine 的单调纳秒时间。 */
    override fun nanoTime(): Long = host.engine.services.clock.nanoTime()

    /** 返回当前 Engine 的墙上时间。 */
    override fun currentTimeMillis(): Long = host.engine.services.clock.currentTimeMillis()
}
