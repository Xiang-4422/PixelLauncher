package com.purride.pixelui.internal

/**
 * 新渲染管线的最小 owner。
 *
 * 第一版负责 root 挂载、layout、paint 和命中测试调度。
 */
internal class PipelineOwner(
    root: RenderBox? = null,
) {
    private var root: RenderBox? = null
    private var needsLayout = true
    private var needsPaint = true

    init {
        attachRoot(root)
    }

    /**
     * 挂载当前 pipeline 的根对象。
     */
    fun attachRoot(root: RenderBox?) {
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
     * 渲染当前根对象并导出与现有宿主兼容的像素结果。
     */
    fun render(
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        val session = PixelRenderSessionFactory.create(
            width = logicalWidth,
            height = logicalHeight,
        )
        val root = root ?: return session.toRenderResult()
        val constraints = RenderConstraints(
            maxWidth = logicalWidth,
            maxHeight = logicalHeight,
        )
        if (needsLayout) {
            root.layout(constraints)
            needsLayout = false
        }
        root.paint(
            context = PaintContext(buffer = session.buffer),
            offsetX = 0,
            offsetY = 0,
        )
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
}
