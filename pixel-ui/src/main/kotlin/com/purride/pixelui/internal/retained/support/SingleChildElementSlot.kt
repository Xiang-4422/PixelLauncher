package com.purride.pixelui.internal

import com.purride.pixelui.Widget

internal class SingleChildElementSlot {
    private var child: Element? = null

    fun update(
        owner: BuildOwner,
        parent: Element,
        newWidget: Widget?,
    ) {
        child = owner.updateChild(
            parent = parent,
            current = child,
            newWidget = newWidget,
        )
    }

    fun visit(visitor: (Element) -> Unit) {
        child?.let(visitor)
    }
}
