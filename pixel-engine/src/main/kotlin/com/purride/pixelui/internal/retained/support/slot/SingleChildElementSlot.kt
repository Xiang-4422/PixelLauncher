package com.purride.pixelui.internal

import com.purride.pixelui.Widget

internal class SingleChildElementSlot {
    /** Current retained child owned by this slot. */
    private var child: Element? = null

    /** Reconciles the nullable child and commits the returned retained element. */
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

    /** Visits the current child without exposing ownership mutation. */
    fun visit(visitor: (Element) -> Unit) {
        child?.let(visitor)
    }

    /** Drops the terminal child reference after [Element.unmount] has processed its snapshot. */
    fun clearReference() {
        child = null
    }
}
