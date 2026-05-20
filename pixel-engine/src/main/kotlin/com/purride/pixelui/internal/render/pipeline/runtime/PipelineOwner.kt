package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBufferPool
import com.purride.pixelcore.PixelColorMode

/**
 * 新渲染管线的最小 owner。
 *
 * 第一版负责 root 挂载、layout、paint 和命中测试调度，并把宿主提供的
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
    private var cachedColorMode: PixelColorMode = PixelColorMode.Mono

    init {
        attachRoot(root)
    }

    /**
     * 挂载当前 pipeline 的根对象。
     */
    fun attachRoot(root: RenderBox?) {
        if (this.root === root) {
            return
        }
        this.root?.detach()
        this.root = root
        root?.attach(this)
        markNeedsLayout()
    }

    /**
     * 标记当前 pipeline 需要重新 layout。
     */
    fun markNeedsLayout() {
        needsLayout = true
        needsPaint = true
    }

    /**
     * 标记当前 pipeline 需要重新 paint。
     */
    fun markNeedsPaint() {
        needsPaint = true
    }

    /**
     * 把上一帧缓存的 result.buffer 归还到池，然后清空缓存。
     */
    private fun discardCachedResult() {
        cachedResult?.let { bufferPool.release(it.buffer) }
        cachedResult = null
    }

    /**
     * 渲染当前根对象并导出宿主需要的像素结果。
     *
     * 当 needsLayout / needsPaint 都为 false 且逻辑尺寸与上一帧一致时，
     * 直接返回上一帧 cached PixelRenderResult，避免重复 layout/paint/target 收集。
     * 这对静态场景（用户没操作、动画未激活）能完全省掉一帧的渲染工作。
     */
    fun render(
        logicalWidth: Int,
        logicalHeight: Int,
        colorMode: PixelColorMode = PixelColorMode.Mono,
    ): PixelRenderResult {
        val canReuseCache = !needsLayout && !needsPaint &&
            cachedResult != null &&
            cachedLogicalWidth == logicalWidth &&
            cachedLogicalHeight == logicalHeight &&
            cachedColorMode == colorMode
        if (canReuseCache) {
            cacheHits += 1
            return cachedResult!!
        }
        // 任何"需要重画"的情况都先把旧 cached buffer 还回池，避免错失复用。
        discardCachedResult()
        cachedLogicalWidth = logicalWidth
        cachedLogicalHeight = logicalHeight
        cachedColorMode = colorMode

        val session = PixelRenderSessionFactory.create(
            width = logicalWidth,
            height = logicalHeight,
            bufferPool = bufferPool,
            colorMode = colorMode,
        )
        val root = root
        if (root == null) {
            val emptyResult = session.toRenderResult()
            cachedResult = emptyResult
            return emptyResult
        }
        val constraints = RenderConstraints(
            maxWidth = logicalWidth,
            maxHeight = logicalHeight,
        )
        if (needsLayout) {
            root.layout(constraints)
            layoutPassCount += 1
            needsLayout = false
        }
        root.paint(
            context = PaintContext(buffer = session.buffer, bufferPool = bufferPool, colorMode = colorMode),
            offsetX = 0,
            offsetY = 0,
        )
        renderCount += 1
        paintPassCount += 1
        needsPaint = false
        root.collectClickTargets(
            offsetX = 0,
            offsetY = 0,
            targets = session.clickTargets,
        )
        root.collectPagerTargets(
            offsetX = 0,
            offsetY = 0,
            targets = session.pagerTargets,
        )
        root.collectListTargets(
            offsetX = 0,
            offsetY = 0,
            targets = session.listTargets,
        )
        root.collectTextInputTargets(
            offsetX = 0,
            offsetY = 0,
            targets = session.textInputTargets,
        )
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

    /**
     * 主动丢弃 cached result。runtime/host 在销毁阶段调用，把 buffer 还回池后再清空池。
     */
    fun dispose() {
        discardCachedResult()
    }

    /**
     * 对当前根对象执行一次命中测试。
     */
    fun hitTest(
        x: Int,
        y: Int,
    ): HitTestResult {
        val result = HitTestResult()
        root?.hitTest(
            localX = x,
            localY = y,
            result = result,
        )
        return result
    }

    /**
     * 返回当前 render tree 的内部诊断快照。
     */
    fun collectDiagnostics(): List<RenderDiagnosticsNode> {
        return root?.collectDiagnostics().orEmpty()
    }

    /**
     * 返回当前 pipeline owner 的内部执行状态。
     */
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

/**
 * Pipeline owner 调试快照。
 */
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

/**
 * 最近一次 render 导出的 target 数量。
 */
internal data class TargetDiagnostics(
    val clickTargets: Int = 0,
    val pagerTargets: Int = 0,
    val listTargets: Int = 0,
    val textInputTargets: Int = 0,
)
