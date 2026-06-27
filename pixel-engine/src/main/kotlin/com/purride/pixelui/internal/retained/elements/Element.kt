package com.purride.pixelui.internal

import com.purride.pixelui.InheritedWidget
import com.purride.pixelui.InternalBuildContext
import com.purride.pixelui.Listenable
import com.purride.pixelui.PixelErrorBoundary
import com.purride.pixelui.Widget
import kotlin.reflect.KClass

/**
 * retained build tree 中的基础 element。
 *
 * 它负责承接 widget、父子关系和 owner 协作，并把具体的状态绑定、
 * inherited 查找、child slot 等局部职责交给独立 helper。
 */
internal abstract class Element(
    final override var widget: Widget,
) : InternalBuildContext {
    lateinit var owner: BuildOwner
        private set

    var parent: Element? = null
        private set

    /**
     * 当前 element 在 retained 树中的深度。
     */
    val depth: Int
        get() = (parent?.depth ?: -1) + 1

    internal val listenedObjects = linkedSetOf<Listenable>()
    private val inheritedLookupBinding = InheritedLookupBinding(this)
    private var dirty = true

    /**
     * 挂载 element 并加入下一轮 build 调度。
     */
    open fun mount(
        parent: Element?,
        owner: BuildOwner,
    ) {
        this.parent = parent
        this.owner = owner
        owner.scheduleBuildFor(this)
    }

    /**
     * 用新的 widget 更新当前 element。
     */
    open fun update(newWidget: Widget) {
        widget = newWidget
        markNeedsBuild()
    }

    /**
     * 标记当前 element 需要在下一轮 build scope 中重建。
     */
    fun markNeedsBuild() {
        if (!dirty) {
            dirty = true
        }
        owner.scheduleBuildFor(this)
    }

    /**
     * 在 dirty 时执行真正的重建逻辑。
     */
    fun rebuildIfNeeded() {
        if (!dirty) {
            return
        }
        dirty = false
        inheritedLookupBinding.clear()
        performRebuild()
    }

    /**
     * 返回当前 element subtree 的内部诊断快照。
     */
    internal fun collectDiagnostics(): List<ElementDiagnosticsNode> {
        return collectDiagnostics(depth = 0, path = "0:${widget.javaClass.simpleName}")
    }

    private fun collectDiagnostics(
        depth: Int,
        path: String,
    ): List<ElementDiagnosticsNode> {
        val children = mutableListOf<Element>()
        visitChildren(children::add)
        return buildList {
            val renderObject = if (this@Element is RenderObjectElement) findRenderObject() else null
            val resolvedRenderObjectName = findRenderObject()?.javaClass?.simpleName
            add(
                ElementDiagnosticsNode(
                    name = this@Element.javaClass.simpleName,
                    widgetName = widget.javaClass.simpleName,
                    path = path,
                    depth = depth,
                    childCount = children.size,
                    isDirty = dirty,
                    listenedObjectCount = listenedObjects.size,
                    renderObjectName = resolvedRenderObjectName,
                    renderObject = renderObject,
                ),
            )
            children.forEachIndexed { index, child ->
                addAll(
                    child.collectDiagnostics(
                        depth = depth + 1,
                        path = "$path/$index:${child.widget.javaClass.simpleName}",
                    ),
                )
            }
        }
    }

    internal fun collectWidgets(): List<Widget> {
        val children = mutableListOf<Element>()
        visitChildren(children::add)
        return buildList {
            add(widget)
            children.forEach { child ->
                addAll(child.collectWidgets())
            }
        }
    }

    /**
     * 读取并登记对 inherited widget 的依赖。
     */
    override fun <T : InheritedWidget> dependOnInheritedWidgetOfExactType(type: KClass<T>): T? {
        return inheritedLookupBinding.dependOn(type)
    }

    /**
     * 只读取 inherited widget，不登记依赖。
     */
    override fun <T : InheritedWidget> getInheritedWidgetOfExactType(type: KClass<T>): T? {
        return inheritedLookupBinding.get(type)
    }

    /**
     * 注册当前 element 对 listenable 的依赖。
     */
    override fun watch(listenable: Listenable?) {
        listenable ?: return
        owner.registerListenableDependency(
            element = this,
            listenable = listenable,
        )
    }

    /**
     * 让当前 build context 关联的 element 进入 dirty 状态。
     */
    override fun markCurrentElementNeedsBuild() {
        markNeedsBuild()
    }

    /**
     * 卸载当前 element 并释放其关联资源。
     */
    open fun unmount() {
        inheritedLookupBinding.clear()
        owner.clearListenableDependencies(this)
        visitChildren { child -> child.unmount() }
        onUnmount()
    }

    /**
     * 为子类提供卸载扩展点。
     */
    protected open fun onUnmount() = Unit

    /**
     * 遍历当前 element 的直接子节点。
     */
    internal open fun visitChildren(visitor: (Element) -> Unit) = Unit

    /**
     * 返回当前 element 对应的 render object。
     *
     * 只有 `RenderObjectElement` 会直接持有 render object；组合型 element
     * 后续会通过子树继续向下查找。
     */
    open fun findRenderObject(): RenderObject? = null

    /**
     * 从最近的 [PixelErrorBoundary] 构建错误后备界面；没有边界时返回 null。
     */
    internal fun buildErrorFallback(error: Throwable): Widget? {
        return findNearestErrorBoundary()?.fallbackFor(error)
    }

    private fun findNearestErrorBoundary(): PixelErrorBoundary? {
        var cursor: Element? = this
        while (cursor != null) {
            val boundary = (cursor as? InheritedElement)?.widget as? PixelErrorBoundary
            if (boundary != null) {
                return boundary
            }
            cursor = cursor.parent
        }
        return null
    }

    /**
     * 执行当前 element 的实际重建。
     */
    protected abstract fun performRebuild()
}

/**
 * Retained element tree 调试快照节点。
 */
internal data class ElementDiagnosticsNode(
    val name: String,
    val widgetName: String,
    val path: String,
    val depth: Int,
    val childCount: Int,
    val isDirty: Boolean,
    val listenedObjectCount: Int,
    val renderObjectName: String?,
    val renderObject: RenderObject?,
)

/**
 * 单 child 组件 element 的公共基类。
 */
internal abstract class ComponentElement(
    widget: Widget,
) : Element(widget) {
    private val childSlot = SingleChildElementSlot()

    /**
     * 重建当前组件并刷新它的唯一子节点。
     */
    override fun performRebuild() {
        val nextWidget = try {
            buildWidget()
        } catch (error: Throwable) {
            buildErrorFallback(error) ?: throw error
        }
        try {
            childSlot.update(
                owner = owner,
                parent = this,
                newWidget = nextWidget,
            )
        } catch (error: Throwable) {
            childSlot.update(
                owner = owner,
                parent = this,
                newWidget = buildErrorFallback(error) ?: throw error,
            )
        }
    }

    /**
     * 遍历当前组件的唯一子节点。
     */
    override fun visitChildren(visitor: (Element) -> Unit) {
        childSlot.visit(visitor)
    }

    /**
     * 组合型 element 自身没有 render object，向唯一子节点透传查找。
     */
    override fun findRenderObject(): RenderObject? {
        var found: RenderObject? = null
        childSlot.visit { child ->
            if (found == null) {
                found = child.findRenderObject()
            }
        }
        return found
    }

    /**
     * 构建当前组件的下一级 widget。
     */
    protected abstract fun buildWidget(): Widget?
}
