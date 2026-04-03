package com.purride.pixelui.internal

import com.purride.pixelui.Widget

internal class MultiChildElementSlot {
    private var children = emptyList<Element>()

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

    fun visit(visitor: (Element) -> Unit) {
        children.forEach(visitor)
    }
}
