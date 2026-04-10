package com.purride.pixelui.internal

/**
 * bridge 渲染树的默认解析器。
 */
internal object DefaultBridgeTreeResolver : BridgeTreeResolving {
    private val childNodeCollector = BridgeChildNodeCollector(::resolveElement)

    /**
     * 解析当前 retained element tree 的 bridge 根节点。
     */
    override fun resolve(request: BridgeTreeResolveRequest): BridgeRenderNode? {
        return request.root?.let(::resolveElement)
    }

    /**
     * 解析单个 retained element。
     */
    private fun resolveElement(element: Element): BridgeRenderNode? {
        return when (element) {
            is BridgeResolvableElement -> {
                val childNodes = BridgeNodeChildren(childNodeCollector.collectAll(element))
                element.resolveBridgeNode(childNodes)
            }

            else -> {
                val bridgeWidget = element.widget as? BridgeWidget
                if (bridgeWidget != null) {
                    bridgeWidget.createBridgeNode(
                        context = element,
                        childNodes = BridgeNodeChildren(childNodeCollector.collectAll(element)),
                    )
                } else {
                    childNodeCollector.collectFirst(element)
                }
            }
        }
    }
}
