package com.purride.pixelui.internal

import com.purride.pixelui.InheritedNotifier
import com.purride.pixelui.InheritedWidget
import com.purride.pixelui.Listenable
import com.purride.pixelui.Widget

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
