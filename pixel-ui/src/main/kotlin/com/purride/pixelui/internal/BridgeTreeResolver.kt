package com.purride.pixelui.internal

/**
 * retained element tree 到 bridge 渲染树的解析器。
 */
internal object BridgeTreeResolver {
    fun resolve(root: Element?): BridgeRenderNode? {
        return root?.let(::resolveElement)
    }

    private fun resolveElement(element: Element): BridgeRenderNode? {
        return when (element) {
            is BridgeResolvableElement -> {
                val childNodes = resolveChildren(element)
                element.resolveBridgeNode(childNodes)
            }
            else -> resolveFirstChild(element)
        }
    }

    private fun resolveChildren(element: Element): List<BridgeRenderNode> {
        val result = mutableListOf<BridgeRenderNode>()
        element.visitChildren { child ->
            resolveElement(child)?.let(result::add)
        }
        return result
    }

    private fun resolveFirstChild(element: Element): BridgeRenderNode? {
        var resolved: BridgeRenderNode? = null
        element.visitChildren { child ->
            if (resolved == null) {
                resolved = resolveElement(child)
            }
        }
        return resolved
    }
}
