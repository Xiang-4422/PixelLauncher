package com.purride.pixelui.internal

import com.purride.pixelui.InheritedNotifier
import com.purride.pixelui.InheritedWidget
import com.purride.pixelui.Listenable
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget

internal class BuildOwner(
    private val onVisualUpdate: () -> Unit,
    private val widgetAdapter: WidgetAdapter,
) {
    var rootElement: Element? = null
        private set

    private val dirtyElements = linkedSetOf<Element>()
    private val listenableCallbacks = mutableMapOf<Listenable, ListenerBinding>()

    fun updateRootWidget(widget: Widget) {
        rootElement = updateChild(
            parent = null,
            current = rootElement,
            newWidget = widget,
        )
    }

    fun buildScope() {
        while (true) {
            val pending = dirtyElements.sortedBy { it.depth }
            if (pending.isEmpty()) {
                break
            }
            dirtyElements.clear()
            pending.forEach { element ->
                element.rebuildIfNeeded()
            }
        }
    }

    fun scheduleBuildFor(element: Element) {
        dirtyElements += element
        requestVisualUpdate()
    }

    fun requestVisualUpdate() {
        onVisualUpdate()
    }

    fun registerListenableDependency(
        element: Element,
        listenable: Listenable,
    ) {
        val binding = listenableCallbacks.getOrPut(listenable) {
            val callback = com.purride.pixelui.VoidCallback {
                listenableCallbacks[listenable]
                    ?.elements
                    ?.toList()
                    ?.forEach { dependent ->
                        dependent.markNeedsBuild()
                    }
                requestVisualUpdate()
            }
            listenable.addListener(callback)
            ListenerBinding(
                callback = callback,
                elements = linkedSetOf(),
            )
        }
        if (binding.elements.add(element)) {
            element.listenedObjects += listenable
        }
    }

    fun clearListenableDependencies(element: Element) {
        element.listenedObjects.toList().forEach { listenable ->
            val binding = listenableCallbacks[listenable] ?: return@forEach
            binding.elements -= element
            if (binding.elements.isEmpty()) {
                listenable.removeListener(binding.callback)
                listenableCallbacks -= listenable
            }
        }
        element.listenedObjects.clear()
    }

    fun updateChild(
        parent: Element?,
        current: Element?,
        newWidget: Widget?,
    ): Element? {
        if (newWidget == null) {
            current?.unmount()
            return null
        }
        if (current != null && canUpdate(current.widget, newWidget)) {
            current.update(newWidget)
            return current
        }
        current?.unmount()
        return inflateWidget(newWidget).also { element ->
            element.mount(parent = parent, owner = this)
        }
    }

    fun dispose() {
        rootElement?.unmount()
        rootElement = null
        listenableCallbacks.forEach { (listenable, binding) ->
            listenable.removeListener(binding.callback)
        }
        listenableCallbacks.clear()
        dirtyElements.clear()
    }

    private fun inflateWidget(widget: Widget): Element {
        return when (widget) {
            is InheritedNotifier<*> -> InheritedNotifierElement(widget)
            is InheritedWidget -> InheritedElement(widget)
            is StatefulWidget -> StatefulElement(widget)
            is StatelessWidget -> StatelessElement(widget)
            else -> widgetAdapter.adapt(widget)
                ?: error("当前 Widget 还没有接入 retained build runtime: ${widget::class.qualifiedName}")
        }
    }

    private fun canUpdate(
        oldWidget: Widget,
        newWidget: Widget,
    ): Boolean {
        return oldWidget::class == newWidget::class && oldWidget.key == newWidget.key
    }

    private data class ListenerBinding(
        val callback: com.purride.pixelui.VoidCallback,
        val elements: MutableSet<Element>,
    )
}
