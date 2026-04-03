package com.purride.pixelui.internal

/**
 * bridge 渲染树的默认解析器。
 */
internal object DefaultBridgeTreeResolver : BridgeTreeResolving {
    private val childNodeCollector = BridgeChildNodeCollector(::resolveElement)

    override fun resolve(root: Element?): BridgeRenderNode? {
        return root?.let(::resolveElement)
    }

    private fun resolveElement(element: Element): BridgeRenderNode? {
        return when (element) {
            is BridgeResolvableElement -> {
                val childNodes = BridgeNodeChildren(childNodeCollector.collectAll(element))
                element.resolveBridgeNode(childNodes)
            }
            else -> childNodeCollector.collectFirst(element)
        }
    }
}
