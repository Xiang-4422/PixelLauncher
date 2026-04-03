package com.purride.pixelui.internal

import com.purride.pixelui.Widget

/**
 * 管理 retained build tree 的根 element。
 *
 * 这层负责：
 * 1. 更新根 widget 对应的 element
 * 2. 暴露当前根 element
 * 3. 在 runtime 销毁时卸载整棵 retained tree
 */
internal class RootElementSlot(
    private val elementChildUpdater: ElementChildUpdater,
) {
    /**
     * 当前保留的根 element。
     */
    var element: Element? = null
        private set

    /**
     * 用新的根 widget 更新 retained tree。
     */
    fun update(
        owner: BuildOwner,
        widget: Widget,
    ) {
        element = elementChildUpdater.updateChild(
            parent = null,
            current = element,
            newWidget = widget,
            owner = owner,
        )
    }

    /**
     * 卸载当前保留的根 element。
     */
    fun clear() {
        element?.unmount()
        element = null
    }
}
