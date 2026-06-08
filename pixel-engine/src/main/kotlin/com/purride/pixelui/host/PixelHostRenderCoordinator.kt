package com.purride.pixelui

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.host.PixelHostFrameLoop
import com.purride.pixelui.internal.HostRootWidget
import com.purride.pixelui.internal.PixelPagerTarget
import com.purride.pixelui.internal.PixelRenderResult
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
    private val runtime = PixelUiRuntime(onVisualUpdate = { host.postInvalidateOnAnimation() })
    private val frameLoop = PixelHostFrameLoop()
    private var contentProvider: RootWidgetProvider? = null

    var lastRenderResult: PixelRenderResult? = null

    fun setContent(provider: RootWidgetProvider) {
        contentProvider = provider
        host.postInvalidateOnAnimation()
    }

    fun submitFrame(
        pixelBuffer: PixelBuffer,
        screenProfile: ScreenProfile,
        backgroundColor: PixelColor,
    ) {
        host.screenProfile = screenProfile
        host.backgroundColor = backgroundColor
        lastRenderResult = PixelRenderResult(
            buffer = pixelBuffer,
            clickTargets = emptyList(),
            pagerTargets = emptyList(),
            listTargets = emptyList(),
            scrollbarTargets = emptyList(),
            textInputTargets = emptyList(),
            sliderTargets = emptyList(),
            semanticsNodes = emptyList(),
        )
        host.invalidate()
    }

    fun renderFrame(): PixelRenderResult? {
        val frameDeltaMs = frameLoop.consumeFrameDeltaMs()
        frameLoop.beginPaint()
        stepActivePagers(frameDeltaMs)
        stepActiveLists(frameDeltaMs)
        textInputCoordinator.stepCursorBlink(frameDeltaMs)

        val provider = contentProvider
        val renderResult = if (provider != null) {
            val rootWidget = provider()
            val wrappedRoot = HostRootWidget(
                screenProfile = host.screenProfile,
                textDirection = host.textDirection,
                textRasterizer = host.textRasterizer,
                windowInsets = host.windowInsets,
                child = rootWidget,
                key = "host-root",
            )
            lastRenderResult = null
            runtime.render(
                root = wrappedRoot,
                logicalWidth = host.screenProfile.logicalWidth,
                logicalHeight = host.screenProfile.logicalHeight,
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
                host.postInvalidateOnAnimation()
            }
            textInputCoordinator.scheduleNextCursorBlinkInvalidate()
        }

        frameLoop.endPaint()
        host.frameStatsObserver?.invoke(frameLoop.snapshotStats())
        return renderResult
    }

    fun dumpElementTree(): String = runtime.dumpElementTree()

    fun dispose() {
        runtime.dispose()
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
