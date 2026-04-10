package com.purride.pixelui.internal

internal class BridgeChildNodeCollector(
    private val resolveElement: (Element) -> BridgeRenderNode?,
) {
    fun collectAll(element: Element): List<BridgeRenderNode> {
        val result = mutableListOf<BridgeRenderNode>()
        element.visitChildren { child ->
            resolveElement(child)?.let(result::add)
        }
        return result
    }

    fun collectFirst(element: Element): BridgeRenderNode? {
        var resolved: BridgeRenderNode? = null
        element.visitChildren { child ->
            if (resolved == null) {
                resolved = resolveElement(child)
            }
        }
        return resolved
    }
}
