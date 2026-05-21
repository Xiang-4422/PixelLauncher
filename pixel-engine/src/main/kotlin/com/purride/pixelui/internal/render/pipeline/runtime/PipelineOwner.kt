package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBufferPool

/**
 * 新渲染管线的最小 owner。
 *
 * 负责 root 挂载、layout、paint 和命中测试调度，并把宿主提供的
 * [PixelBufferPool] 注入到每帧 PaintContext，供 scratch buffer 借/还使用。
 */
internal class PipelineOwner(
    root: RenderBox? = null,
    private val bufferPool: PixelBufferPool = PixelBufferPool(),
) {
    private var root: RenderBox? = null
    private var needsLayout = true
    private var needsPaint = true
    private var renderCount = 0
    private var layoutPassCount = 0
    private var paintPassCount = 0
    private var cacheHits = 0
    private var lastTargetDiagnostics = TargetDiagnostics()
    private var cachedResult: PixelRenderResult? = null
    private var cachedLogicalWidth: Int = -1
    private var cachedLogicalHeight: Int = -1

    init {
        attachRoot(root)
    }

    fun attachRoot(root: RenderBox?) {
        if (this.root === root) return
        this.root?.detach()
        this.root = root
        root?.attach(this)
        markNeedsLayout()
    }

    fun markNeedsLayout() {
        needsLayout = true
        needsPaint = true
    }

    fun markNeedsPaint() {
        needsPaint = true
    }

    private fun discardCachedResult() {
        cachedResult?.let { bufferPool.release(it.buffer) }
        cachedResult = null
    }

    fun render(logicalWidth: Int, logicalHeight: Int): PixelRenderResult {
        val canReuseCache = !needsLayout && !needsPaint &&
            cachedResult != null &&
            cachedLogicalWidth == logicalWidth &&
            cachedLogicalHeight == logicalHeight
        if (canReuseCache) {
            cacheHits += 1
            return cachedResult!!
        }
        discardCachedResult()
        cachedLogicalWidth = logicalWidth
        cachedLogicalHeight = logicalHeight

        val session = PixelRenderSessionFactory.create(
            width = logicalWidth,
            height = logicalHeight,
            bufferPool = bufferPool,
        )
        val root = root
        if (root == null) {
            val emptyResult = session.toRenderResult()
            cachedResult = emptyResult
            return emptyResult
        }
        val constraints = RenderConstraints(maxWidth = logicalWidth, maxHeight = logicalHeight)
        if (needsLayout) {
            root.layout(constraints)
            layoutPassCount += 1
            needsLayout = false
        }
        root.paint(
            context = PaintContext(buffer = session.buffer, bufferPool = bufferPool),
            offsetX = 0,
            offsetY = 0,
        )
        renderCount += 1
        paintPassCount += 1
        needsPaint = false
        root.collectClickTargets(offsetX = 0, offsetY = 0, targets = session.clickTargets)
        root.collectPagerTargets(offsetX = 0, offsetY = 0, targets = session.pagerTargets)
        root.collectListTargets(offsetX = 0, offsetY = 0, targets = session.listTargets)
        root.collectTextInputTargets(offsetX = 0, offsetY = 0, targets = session.textInputTargets)
        lastTargetDiagnostics = TargetDiagnostics(
            clickTargets = session.clickTargets.size,
            pagerTargets = session.pagerTargets.size,
            listTargets = session.listTargets.size,
            textInputTargets = session.textInputTargets.size,
        )
        val result = session.toRenderResult()
        cachedResult = result
        return result
    }

    fun dispose() {
        discardCachedResult()
    }

    fun hitTest(x: Int, y: Int): HitTestResult {
        val result = HitTestResult()
        root?.hitTest(localX = x, localY = y, result = result)
        return result
    }

    fun collectDiagnostics(): List<RenderDiagnosticsNode> = root?.collectDiagnostics().orEmpty()

    fun collectPipelineDiagnostics(): PipelineDiagnostics {
        return PipelineDiagnostics(
            hasRoot = root != null,
            needsLayout = needsLayout,
            needsPaint = needsPaint,
            renderCount = renderCount,
            layoutPassCount = layoutPassCount,
            paintPassCount = paintPassCount,
            cacheHits = cacheHits,
            targetDiagnostics = lastTargetDiagnostics,
        )
    }
}

internal data class PipelineDiagnostics(
    val hasRoot: Boolean,
    val needsLayout: Boolean,
    val needsPaint: Boolean,
    val renderCount: Int,
    val layoutPassCount: Int,
    val paintPassCount: Int,
    val cacheHits: Int,
    val targetDiagnostics: TargetDiagnostics,
)

internal data class TargetDiagnostics(
    val clickTargets: Int = 0,
    val pagerTargets: Int = 0,
    val listTargets: Int = 0,
    val textInputTargets: Int = 0,
)
