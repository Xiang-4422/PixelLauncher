package com.purride.pixelui.internal

import java.util.concurrent.atomic.AtomicLong

/**
 * 新渲染管线的基础渲染对象。
 *
 * 第一版只稳定 attach/detach、owner 协作、脏标记和子节点遍历协议。
 */
public abstract class RenderObject {
    /** Stable semantic identifiers owned by this retained render object and optional local slots. */
    private val semanticNodeIds: MutableMap<Any, Long> = mutableMapOf()

    /** 记录 `RenderObject` 的 `parent` 配置或运行值，读取与更新均遵守所属类型约束；写入后由所属对象在下一次状态同步时生效。 */
    public var parent: RenderObject? = null
        internal set

    private var owner: PipelineOwner? = null

    /** Whether this render object still participates in one live pipeline owner tree. */
    internal val isAttachedToPipeline: Boolean
        get() = owner != null

    /**
 * 执行 `RenderObject` 的 `semanticNodeId` 公开行为；具体参数、返回和副作用见下文。
 *
     * Returns a process-unique semantic identifier that remains stable for this retained node.
     *
     * [localId] lets one render object own multiple independently addressable virtual nodes.
     */
    public fun semanticNodeId(localId: Any = DefaultSemanticNodeSlot): Long {
        return semanticNodeIds.getOrPut(localId) { NextSemanticNodeId.getAndIncrement() }
    }

    /**
     * 接收一个新的直接子 render object。
     */
    internal fun adoptChild(child: RenderObject) {
        child.parent = this
        owner?.let(child::attach)
        markNeedsLayout()
        markNeedsPaint()
    }

    /**
     * 移除一个既有直接子 render object。
     */
    internal fun dropChild(child: RenderObject) {
        if (child.parent != this) {
            return
        }
        child.detach()
        child.parent = null
        markNeedsLayout()
        markNeedsPaint()
    }

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
    protected open fun onAttach(): Unit = Unit

    /**
     * 子类可选的 detach 扩展点。
     */
    protected open fun onDetach(): Unit = Unit

    /**
     * 遍历当前对象的直接子节点。
     */
    protected open fun visitChildren(visitor: (RenderObject) -> Unit): Unit = Unit

    /**
     * 收集当前 render subtree 的调试快照。
     *
     * 该入口只用于内部测试和诊断，不参与公开 API。
     */
    internal fun collectDiagnostics(): List<RenderDiagnosticsNode> {
        return collectDiagnostics(depth = 0, path = "0:${javaClass.simpleName}")
    }

    /**
     * Counts this complete retained subtree without allocating diagnostic node or child lists.
     *
     * The current pipeline invalidates layout/paint at owner scope, so every node traversed by the
     * next whole-tree pass is considered a dirty render node for frame workload diagnostics.
     */
    internal fun subtreeNodeCount(): Int {
        /** Saturated count beginning with this RenderObject. */
        var count = 1
        visitChildren { child ->
            /** Child subtree count, saturated to keep pathological trees from wrapping negative. */
            val childCount = child.subtreeNodeCount()
            count = if (count > Int.MAX_VALUE - childCount) Int.MAX_VALUE else count + childCount
        }
        return count
    }

    private fun collectDiagnostics(
        depth: Int,
        path: String,
    ): List<RenderDiagnosticsNode> {
        val children = mutableListOf<RenderObject>()
        visitChildren(children::add)
        return buildList {
            add(
                RenderDiagnosticsNode(
                    name = this@RenderObject.javaClass.simpleName,
                    path = path,
                    depth = depth,
                    childCount = children.size,
                    size = (this@RenderObject as? RenderBox)?.size,
                    renderObject = this@RenderObject,
                ),
            )
            children.forEachIndexed { index, child ->
                addAll(
                    child.collectDiagnostics(
                        depth = depth + 1,
                        path = "$path/$index:${child.javaClass.simpleName}",
                    ),
                )
            }
        }
    }

    private companion object {
        /** 单调递增的 id 源；从 1 开始分配，保证导出的每个节点 id 都是正数。 */
        val NextSemanticNodeId: AtomicLong = AtomicLong(1L)

        /** Default local slot for render objects that expose exactly one semantic node. */
        val DefaultSemanticNodeSlot: Any = Any()
    }
}

/**
 * Render tree 调试快照节点。
 */
internal data class RenderDiagnosticsNode(
    val name: String,
    val path: String,
    val depth: Int,
    val childCount: Int,
    val size: RenderSize?,
    val renderObject: RenderObject,
)

/**
 * 新渲染管线里的基础盒模型对象。
 */
public abstract class RenderBox : RenderObject() {
    /** 定义 `RenderObject` 布局中的 `size` 逻辑像素度量；写入后由所属对象在下一次状态同步时生效。 */
    public var size: RenderSize = RenderSize.Zero
        protected set

    /**
     * 在给定约束下执行布局。
     */
    public abstract fun layout(constraints: RenderConstraints)

    /**
     * 在指定偏移下把自己画到目标 buffer。
     */
    public abstract fun paint(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    )

    /**
     * 执行局部坐标系下的命中测试。
     */
    public open fun hitTest(
        localX: Int,
        localY: Int,
        result: HitTestResult,
    ): Unit = Unit

    /**
     * 导出当前子树里的点击目标。
     */
    public open fun collectClickTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelClickTarget>,
    ): Unit = Unit

    /**
     * 导出当前子树里的分页目标。框架内部使用，不暴露给外部扩展点。
     */
    public open fun collectPagerTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelPagerTarget>,
    ): Unit = Unit

    /**
     * 导出当前子树里的列表滚动目标。框架内部使用，不暴露给外部扩展点。
     */
    public open fun collectListTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelListTarget>,
    ): Unit = Unit

    /**
     * 导出当前子树里的滚动条拖动目标。框架内部使用，不暴露给外部扩展点。
     */
    public open fun collectScrollbarTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelScrollbarTarget>,
    ): Unit = Unit

    /**
     * 导出当前子树里的下拉刷新目标。框架内部使用，不暴露给外部扩展点。
     */
    public open fun collectRefreshTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelRefreshTarget>,
    ): Unit = Unit

    /**
     * 导出当前子树里的文本输入目标。框架内部使用，不暴露给外部扩展点。
     */
    public open fun collectTextInputTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelTextInputTarget>,
    ): Unit = Unit

    /**
     * 导出当前子树里的滑块目标。框架内部使用，不暴露给外部扩展点。
     */
    public open fun collectSliderTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelSliderTarget>,
    ): Unit = Unit

    /**
     * 导出当前子树的基础可访问性语义。
     */
    public open fun collectSemantics(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelSemanticsTarget>,
    ): Unit = Unit
}

/**
 * 可承接单个 render object 子节点的协议。
 */
internal interface RenderObjectWithChild {
    /**
     * 替换当前 render object 的唯一子节点。
     */
    public fun setRenderObjectChild(child: RenderObject?)
}

/**
 * 可承接多个 render object 子节点的协议。
 */
internal interface RenderObjectWithChildren {
    /**
     * 替换当前 render object 的所有直接子节点。
     */
    public fun setRenderObjectChildren(children: List<RenderObject>)
}

/**
 * 单 child render object 的基础实现。
 */
public abstract class SingleChildRenderObject : RenderObjectWithChild, RenderBox() {
    /** 提供 `RenderObject` 当前管理的 `child` 内容；写入后由所属对象在下一次状态同步时生效。 */
    protected var child: RenderObject? = null
        private set

    /**
     * 替换唯一子节点，并维护父子生命周期。
     */
    override fun setRenderObjectChild(child: RenderObject?) {
        val previous = this.child
        if (previous == child) {
            return
        }
        previous?.let(::dropChild)
        this.child = child
        child?.let(::adoptChild)
    }

    /**
     * 遍历唯一子节点。
     */
    override fun visitChildren(visitor: (RenderObject) -> Unit) {
        child?.let(visitor)
    }
}

/**
 * 多 child render object 的基础实现。
 */
public abstract class MultiChildRenderObject : RenderObjectWithChildren, RenderBox() {
    /** 保存 `RenderObject` 当前的 `children` 集合；元素顺序和所有权遵守所属类型契约；写入后由所属对象在下一次状态同步时生效。 */
    protected var children: List<RenderObject> = emptyList()
        private set

    /**
     * 替换所有直接子节点，并维护父子生命周期。
     *
     * 当新列表与旧列表按引用逐项相等时跳过 dropChild/adoptChild 和脏标记，
     * 避免重复 build 同样的 widget 树时无差别 markNeedsLayout/Paint。
     */
    override fun setRenderObjectChildren(children: List<RenderObject>) {
        val previous = this.children
        if (isSameChildList(previous, children)) {
            return
        }
        previous.filter { child -> children.none { it === child } }.forEach(::dropChild)
        children.filter { child -> previous.none { it === child } }.forEach(::adoptChild)
        this.children = children
        markNeedsLayout()
        markNeedsPaint()
    }

    /**
     * 遍历所有直接子节点。
     */
    override fun visitChildren(visitor: (RenderObject) -> Unit) {
        children.forEach(visitor)
    }

    private fun isSameChildList(
        previous: List<RenderObject>,
        next: List<RenderObject>,
    ): Boolean {
        if (previous.size != next.size) {
            return false
        }
        for (index in previous.indices) {
            if (previous[index] !== next[index]) {
                return false
            }
        }
        return true
    }
}
