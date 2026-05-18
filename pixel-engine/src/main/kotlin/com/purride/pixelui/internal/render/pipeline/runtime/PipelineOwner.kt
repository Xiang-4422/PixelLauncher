package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBufferPool

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
    private var lastTargetDiagnostics = TargetDiagnostics()

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
     * 渲染当前根对象并导出宿主需要的像素结果。
     */
    fun render(
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        val session = PixelRenderSessionFactory.create(
            width = logicalWidth,
            height = logicalHeight,
            bufferPool = bufferPool,
        )
        val root = root ?: return session.toRenderResult()
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
            context = PaintContext(buffer = session.buffer, bufferPool = bufferPool),
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
        return session.toRenderResult()
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
