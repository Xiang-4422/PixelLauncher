package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.InheritedNotifier
import com.purride.pixelui.InheritedWidget
import com.purride.pixelui.InternalBuildContext
import com.purride.pixelui.Listenable
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.StatelessWidget
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
     * 执行当前 element 的实际重建。
     */
    protected abstract fun performRebuild()
}

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
        childSlot.update(
            owner = owner,
            parent = this,
            newWidget = buildWidget(),
        )
    }

    /**
     * 遍历当前组件的唯一子节点。
     */
    override fun visitChildren(visitor: (Element) -> Unit) {
        childSlot.visit(visitor)
    }

    /**
     * 构建当前组件的下一级 widget。
     */
    protected abstract fun buildWidget(): Widget?
}

/**
 * StatelessWidget 对应的 element。
 */
internal class StatelessElement(
    widget: StatelessWidget,
) : ComponentElement(widget) {
    /**
     * 调用 stateless widget 的 build。
     */
    override fun buildWidget(): Widget {
        return (widget as StatelessWidget).build(this)
    }
}

/**
 * StatefulWidget 对应的 element。
 */
internal class StatefulElement(
    widget: StatefulWidget,
) : ComponentElement(widget) {
    private val stateBinding = StateBinding(
        widget = widget,
        context = this,
    )

    /**
     * 更新 widget 时同步 state 的 didUpdateWidget 回调。
     */
    override fun update(newWidget: Widget) {
        super.update(newWidget)
        stateBinding.update(newWidget)
    }

    /**
     * 通过 state 构建当前组件树。
     */
    override fun buildWidget(): Widget {
        return stateBinding.build(this)
    }

    /**
     * 卸载时释放 state 绑定。
     */
    override fun onUnmount() {
        stateBinding.dispose()
    }

    /**
     * 让 state 可通过 context 主动触发重建。
     */
    override fun markCurrentElementNeedsBuild() {
        markNeedsBuild()
    }

    /**
     * 标记 inherited 依赖已变更。
     */
    fun markDependenciesChanged() {
        stateBinding.markDependenciesChanged()
        markNeedsBuild()
    }
}

/**
 * InheritedWidget 对应的 element。
 */
internal open class InheritedElement(
    widget: InheritedWidget,
) : ComponentElement(widget) {
    private val dependencyRegistry = InheritedDependencyRegistry()

    /**
     * 更新 inherited widget，并在需要时通知依赖方。
     */
    override fun update(newWidget: Widget) {
        val oldWidget = widget as InheritedWidget
        super.update(newWidget)
        if ((newWidget as InheritedWidget).updateShouldNotify(oldWidget)) {
            notifyDependents()
        }
    }

    /**
     * inherited element 的 child 就是它暴露的子树。
     */
    override fun buildWidget(): Widget {
        return (widget as InheritedWidget).child
    }

    /**
     * 注册一个依赖当前 inherited widget 的 element。
     */
    fun addDependent(element: Element) {
        dependencyRegistry.add(element)
    }

    /**
     * 移除一个依赖当前 inherited widget 的 element。
     */
    fun removeDependent(element: Element) {
        dependencyRegistry.remove(element)
    }

    /**
     * 通知所有依赖方刷新。
     */
    protected fun notifyDependents() {
        dependencyRegistry.notifyDependents()
    }
}

/**
 * InheritedNotifier 对应的 element。
 */
internal class InheritedNotifierElement(
    widget: InheritedNotifier<*>,
) : InheritedElement(widget) {
    private val notifierBinding = InheritedNotifierBinding {
        notifyDependents()
        owner.requestVisualUpdate()
    }

    /**
     * 挂载时绑定 notifier。
     */
    override fun mount(parent: Element?, owner: BuildOwner) {
        super.mount(parent, owner)
        notifierBinding.bind((widget as InheritedNotifier<*>).notifier as? Listenable)
    }

    /**
     * 更新时切换 notifier 绑定。
     */
    override fun update(newWidget: Widget) {
        val nextNotifier = (newWidget as InheritedNotifier<*>).notifier as? Listenable
        super.update(newWidget)
        notifierBinding.bind(nextNotifier)
    }

    /**
     * 卸载时清理 notifier 绑定。
     */
    override fun onUnmount() {
        notifierBinding.clear()
    }
}
