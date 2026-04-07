package com.purride.pixelui.internal

/**
 * 新渲染管线的基础渲染对象。
 *
 * 第一版只稳定 attach/detach、owner 协作、脏标记和子节点遍历协议。
 */
internal abstract class RenderObject {
    var parent: RenderObject? = null
        internal set

    private var owner: PipelineOwner? = null

    /**
     * 把当前对象挂到指定 pipeline owner。
     */
    internal fun attach(nextOwner: PipelineOwner) {
        owner = nextOwner
        onAttach()
        visitChildren { child ->
            child.parent = this
            child.attach(nextOwner)
        }
    }

    /**
     * 从当前 pipeline owner 卸载。
     */
    internal fun detach() {
        visitChildren { child ->
            child.detach()
            child.parent = null
        }
        onDetach()
        owner = null
    }

    /**
     * 标记当前对象需要重新 layout。
     */
    protected fun markNeedsLayout() {
        owner?.markNeedsLayout()
    }

    /**
     * 标记当前对象需要重新 paint。
     */
    protected fun markNeedsPaint() {
        owner?.markNeedsPaint()
    }

    /**
     * 子类可选的 attach 扩展点。
     */
    protected open fun onAttach() = Unit

    /**
     * 子类可选的 detach 扩展点。
     */
    protected open fun onDetach() = Unit

    /**
     * 遍历当前对象的直接子节点。
     */
    protected open fun visitChildren(visitor: (RenderObject) -> Unit) = Unit
}

/**
 * 新渲染管线里的基础盒模型对象。
 */
internal abstract class RenderBox : RenderObject() {
    var size: RenderSize = RenderSize.Zero
        protected set

    /**
     * 在给定约束下执行布局。
     */
    abstract fun layout(constraints: RenderConstraints)

    /**
     * 在指定偏移下把自己画到目标 buffer。
     */
    abstract fun paint(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    )

    /**
     * 执行局部坐标系下的命中测试。
     */
    open fun hitTest(
        localX: Int,
        localY: Int,
        result: HitTestResult,
    ) = Unit

    /**
     * 导出当前子树里的点击目标。
     */
    open fun collectClickTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelClickTarget>,
    ) = Unit
}
