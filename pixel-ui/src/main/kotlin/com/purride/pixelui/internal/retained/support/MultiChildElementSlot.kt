package com.purride.pixelui.internal

import com.purride.pixelui.Widget

internal class MultiChildElementSlot {
    private var children = emptyList<Element>()

    /**
     * 当前 slot 内保留的 child elements。
     */
    val elements: List<Element>
        get() = children

    /**
     * 按索引更新多 child element 列表。
     */
    fun update(
        owner: BuildOwner,
        parent: Element,
        newWidgets: List<Widget>,
    ) {
        val nextChildren = ArrayList<Element>(newWidgets.size)
        val maxCount = maxOf(children.size, newWidgets.size)
        for (index in 0 until maxCount) {
            val current = children.getOrNull(index)
            val nextWidget = newWidgets.getOrNull(index)
            owner.updateChild(
                parent = parent,
                current = current,
                newWidget = nextWidget,
            )?.let(nextChildren::add)
        }
        children = nextChildren
    }

    /**
     * 遍历当前 child elements。
     */
    fun visit(visitor: (Element) -> Unit) {
        children.forEach(visitor)
    }
}
