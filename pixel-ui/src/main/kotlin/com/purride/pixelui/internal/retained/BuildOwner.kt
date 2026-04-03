package com.purride.pixelui.internal

import com.purride.pixelui.Listenable
import com.purride.pixelui.Widget

/**
 * retained build tree 的 owner。
 *
 * 这层负责 build 生命周期调度和跨 element 的共享协作，并把根节点管理、
 * dirty 调度、listenable 注册等局部职责交给独立 helper。
 */
internal class BuildOwner(
    private val onVisualUpdate: () -> Unit,
    private val elementChildUpdater: ElementChildUpdater,
) {
    private val rootElementSlot = RootElementSlot(elementChildUpdater)
    private val dirtyElementScheduler = DirtyElementScheduler()
    private val listenableRegistry = ListenableDependencyRegistry(
        requestVisualUpdate = ::requestVisualUpdate,
    )

    /**
     * 当前保留的根 element。
     */
    val rootElement: Element?
        get() = rootElementSlot.element

    /**
     * 用新的根 widget 刷新 retained 树根节点。
     */
    fun updateRootWidget(widget: Widget) {
        rootElementSlot.update(owner = this, widget = widget)
    }

    /**
     * 执行当前 build scope 内所有 dirty element 的重建。
     */
    fun buildScope() {
        dirtyElementScheduler.buildScope()
    }

    /**
     * 把一个 element 加入下一轮 build 调度。
     */
    fun scheduleBuildFor(element: Element) {
        dirtyElementScheduler.schedule(element)
        requestVisualUpdate()
    }

    /**
     * 请求宿主执行一次视觉更新。
     */
    fun requestVisualUpdate() {
        onVisualUpdate()
    }

    /**
     * 注册一个 listenable 依赖。
     */
    fun registerListenableDependency(
        element: Element,
        listenable: Listenable,
    ) {
        listenableRegistry.register(
            element = element,
            listenable = listenable,
        )
    }

    /**
     * 清理一个 element 持有的 listenable 依赖。
     */
    fun clearListenableDependencies(element: Element) {
        listenableRegistry.clear(element)
    }

    /**
     * 更新一个父节点下的 child element。
     */
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

    /**
     * 释放 retained tree 和 owner 持有的所有调度状态。
     */
    fun dispose() {
        rootElementSlot.clear()
        listenableRegistry.dispose()
        dirtyElementScheduler.clear()
    }
}
