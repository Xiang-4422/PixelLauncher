package com.purride.pixelui.internal

import com.purride.pixelui.Listenable
import com.purride.pixelui.Widget

internal class BuildOwner(
    private val onVisualUpdate: () -> Unit,
    private val elementChildUpdater: ElementChildUpdater,
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
        return elementChildUpdater.updateChild(
            parent = parent,
            current = current,
            newWidget = newWidget,
            owner = this,
        )
    }

    fun dispose() {
        rootElement?.unmount()
        rootElement = null
        listenableRegistry.dispose()
        dirtyElementScheduler.clear()
    }
}
