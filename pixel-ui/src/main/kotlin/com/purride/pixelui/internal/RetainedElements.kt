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

internal abstract class Element(
    final override var widget: Widget,
) : InternalBuildContext {
    lateinit var owner: BuildOwner
        private set

    var parent: Element? = null
        private set

    val depth: Int
        get() = (parent?.depth ?: -1) + 1

    internal val listenedObjects = linkedSetOf<Listenable>()
    private val inheritedDependencies = linkedSetOf<InheritedElement>()
    private var dirty = true

    open fun mount(
        parent: Element?,
        owner: BuildOwner,
    ) {
        this.parent = parent
        this.owner = owner
        owner.scheduleBuildFor(this)
    }

    open fun update(newWidget: Widget) {
        widget = newWidget
        markNeedsBuild()
    }

    fun markNeedsBuild() {
        if (!dirty) {
            dirty = true
        }
        owner.scheduleBuildFor(this)
    }

    fun rebuildIfNeeded() {
        if (!dirty) {
            return
        }
        dirty = false
        clearInheritedDependencies()
        performRebuild()
    }

    override fun <T : InheritedWidget> dependOnInheritedWidgetOfExactType(type: KClass<T>): T? {
        val ancestor = findInheritedElement(type) ?: return null
        inheritedDependencies += ancestor
        ancestor.addDependent(this)
        @Suppress("UNCHECKED_CAST")
        return ancestor.widget as T
    }

    override fun <T : InheritedWidget> getInheritedWidgetOfExactType(type: KClass<T>): T? {
        val ancestor = findInheritedElement(type) ?: return null
        @Suppress("UNCHECKED_CAST")
        return ancestor.widget as T
    }

    override fun watch(listenable: Listenable?) {
        listenable ?: return
        owner.registerListenableDependency(
            element = this,
            listenable = listenable,
        )
    }

    override fun markCurrentElementNeedsBuild() {
        markNeedsBuild()
    }

    open fun unmount() {
        clearInheritedDependencies()
        owner.clearListenableDependencies(this)
        visitChildren { child -> child.unmount() }
        onUnmount()
    }

    protected open fun onUnmount() = Unit

    internal open fun visitChildren(visitor: (Element) -> Unit) = Unit

    protected abstract fun performRebuild()

    private fun findInheritedElement(type: KClass<out InheritedWidget>): InheritedElement? {
        var cursor = parent
        while (cursor != null) {
            if (cursor is InheritedElement && type.isInstance(cursor.widget)) {
                return cursor
            }
            cursor = cursor.parent
        }
        return null
    }

    private fun clearInheritedDependencies() {
        inheritedDependencies.toList().forEach { ancestor ->
            ancestor.removeDependent(this)
        }
        inheritedDependencies.clear()
    }
}

internal abstract class ComponentElement(
    widget: Widget,
) : Element(widget) {
    protected var child: Element? = null

    override fun performRebuild() {
        val built = buildWidget()
        child = owner.updateChild(
            parent = this,
            current = child,
            newWidget = built,
        )
    }

    override fun visitChildren(visitor: (Element) -> Unit) {
        child?.let(visitor)
    }

    protected abstract fun buildWidget(): Widget?
}

internal class StatelessElement(
    widget: StatelessWidget,
) : ComponentElement(widget) {
    override fun buildWidget(): Widget {
        return (widget as StatelessWidget).build(this)
    }
}

internal class StatefulElement(
    widget: StatefulWidget,
) : ComponentElement(widget) {
    private val state: State<StatefulWidget> = createAttachedState(widget)
    private var dependenciesChanged = true

    override fun update(newWidget: Widget) {
        val oldWidget = widget as StatefulWidget
        super.update(newWidget)
        @Suppress("UNCHECKED_CAST")
        state.widget = newWidget as StatefulWidget
        state.didUpdateWidget(oldWidget)
    }

    override fun buildWidget(): Widget {
        state.context = this
        if (dependenciesChanged) {
            dependenciesChanged = false
            state.didChangeDependencies()
        }
        return state.build(this)
    }

    override fun onUnmount() {
        state.dispose()
        state.detach()
    }

    override fun markCurrentElementNeedsBuild() {
        markNeedsBuild()
    }

    fun markDependenciesChanged() {
        dependenciesChanged = true
        markNeedsBuild()
    }

    private fun createAttachedState(widget: StatefulWidget): State<StatefulWidget> {
        @Suppress("UNCHECKED_CAST")
        val createdState = widget.createState() as State<StatefulWidget>
        createdState.widget = widget
        createdState.context = this
        createdState.attach()
        createdState.initState()
        return createdState
    }
}

internal open class InheritedElement(
    widget: InheritedWidget,
) : ComponentElement(widget) {
    private val dependents = linkedSetOf<Element>()

    override fun update(newWidget: Widget) {
        val oldWidget = widget as InheritedWidget
        super.update(newWidget)
        if ((newWidget as InheritedWidget).updateShouldNotify(oldWidget)) {
            notifyDependents()
        }
    }

    override fun buildWidget(): Widget {
        return (widget as InheritedWidget).child
    }

    fun addDependent(element: Element) {
        dependents += element
    }

    fun removeDependent(element: Element) {
        dependents -= element
    }

    protected fun notifyDependents() {
        dependents.toList().forEach { dependent ->
            if (dependent is StatefulElement) {
                dependent.markDependenciesChanged()
            } else {
                dependent.markNeedsBuild()
            }
        }
    }
}

internal class InheritedNotifierElement(
    widget: InheritedNotifier<*>,
) : InheritedElement(widget) {
    private var currentNotifier: Listenable? = null
    private var callback: com.purride.pixelui.VoidCallback? = null

    override fun mount(parent: Element?, owner: BuildOwner) {
        super.mount(parent, owner)
        bindNotifier((widget as InheritedNotifier<*>).notifier as? Listenable)
    }

    override fun update(newWidget: Widget) {
        val nextNotifier = (newWidget as InheritedNotifier<*>).notifier as? Listenable
        super.update(newWidget)
        bindNotifier(nextNotifier)
    }

    override fun onUnmount() {
        callback?.let { currentNotifier?.removeListener(it) }
        currentNotifier = null
        callback = null
    }

    private fun bindNotifier(notifier: Listenable?) {
        if (currentNotifier === notifier) {
            return
        }
        callback?.let { currentNotifier?.removeListener(it) }
        currentNotifier = notifier
        callback = notifier?.let {
            com.purride.pixelui.VoidCallback {
                notifyDependents()
                owner.requestVisualUpdate()
            }.also { listener ->
                notifier.addListener(listener)
            }
        }
    }
}
