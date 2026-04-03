package com.purride.pixelui.internal

import com.purride.pixelui.Listenable
import com.purride.pixelui.Widget

internal class BuildOwner(
    private val onVisualUpdate: () -> Unit,
    private val elementInflater: ElementInflater,
) {
    var rootElement: Element? = null
        private set

    private val dirtyElementScheduler = DirtyElementScheduler()
    private val listenableRegistry = ListenableDependencyRegistry(
        requestVisualUpdate = ::requestVisualUpdate,
    )

    fun updateRootWidget(widget: Widget) {
        rootElement = updateChild(
            parent = null,
            current = rootElement,
            newWidget = widget,
        )
    }

    fun buildScope() {
        dirtyElementScheduler.buildScope()
    }

    fun scheduleBuildFor(element: Element) {
        dirtyElementScheduler.schedule(element)
        requestVisualUpdate()
    }

    fun requestVisualUpdate() {
        onVisualUpdate()
    }

    fun registerListenableDependency(
        element: Element,
        listenable: Listenable,
    ) {
        listenableRegistry.register(
            element = element,
            listenable = listenable,
        )
    }

    fun clearListenableDependencies(element: Element) {
        listenableRegistry.clear(element)
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
        return elementInflater.inflate(newWidget).also { element ->
            element.mount(parent = parent, owner = this)
        }
    }

    fun dispose() {
        rootElement?.unmount()
        rootElement = null
        listenableRegistry.dispose()
        dirtyElementScheduler.clear()
    }
    private fun canUpdate(
        oldWidget: Widget,
        newWidget: Widget,
    ): Boolean {
        return oldWidget::class == newWidget::class && oldWidget.key == newWidget.key
    }
}
