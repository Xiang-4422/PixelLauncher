package com.purride.pixelui.internal

import com.purride.pixelui.Widget

/**
 * retained element 子树更新协议。
 */
internal interface ElementChildUpdater {
    fun updateChild(
        parent: Element?,
        current: Element?,
        newWidget: Widget?,
        owner: BuildOwner,
    ): Element?
}

/**
 * retained element 子树更新的默认实现。
 *
 * 这层负责：
 * 1. 判断 element 是否可复用
 * 2. 卸载旧 element
 * 3. 通过 inflater 构建并挂载新 element
 */
internal class DefaultElementChildUpdater(
    private val elementInflater: ElementInflater,
) : ElementChildUpdater {
    override fun updateChild(
        parent: Element?,
        current: Element?,
        newWidget: Widget?,
        owner: BuildOwner,
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
            element.mount(parent = parent, owner = owner)
            // Direct render object parents need newly mounted component children to expose
            // their render object before parent-child render object synchronization runs.
            element.rebuildIfNeeded()
        }
    }

    private fun canUpdate(
        oldWidget: Widget,
        newWidget: Widget,
    ): Boolean {
        return oldWidget::class == newWidget::class && oldWidget.key == newWidget.key
    }
}
