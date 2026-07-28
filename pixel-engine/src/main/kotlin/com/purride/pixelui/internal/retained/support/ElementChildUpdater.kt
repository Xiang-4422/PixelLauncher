package com.purride.pixelui.internal

import com.purride.pixelui.HostCapabilities
import com.purride.pixelui.Widget

/**
 * retained element 子树更新协议。
 */
internal interface ElementChildUpdater {
    /** Reconciles one nullable child configuration against its currently retained Element. */
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
    /** Factory used only when no compatible retained Element can be reused. */
    private val elementInflater: ElementInflater,
) : ElementChildUpdater {
    /** Applies removal, identity reuse, compatible update, or replacement in that order. */
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
        // HostCapabilities 的等值静默契约要求：完全相同的子 Widget 实例不得触发一次更新，
        // 否则依赖方会在快照未变时被重建。其他 widget 家族不共享该契约，仍按每帧更新语义处理。
        if (
            current != null &&
            current.widget === newWidget &&
            parent?.widget is HostCapabilities
        ) {
            return current
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

    /** Returns whether a fresh configuration may update an existing retained Element in place. */
    private fun canUpdate(
        oldWidget: Widget,
        newWidget: Widget,
    ): Boolean {
        return oldWidget::class == newWidget::class && oldWidget.key == newWidget.key
    }
}
