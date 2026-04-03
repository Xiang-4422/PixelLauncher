package com.purride.pixelui.internal

/**
 * 兼容入口，后续应优先通过 [BridgeTreeResolving] 使用默认解析器。
 */
internal object BridgeTreeResolver {
    fun resolve(root: Element?): BridgeRenderNode? {
        return DefaultBridgeTreeResolver.resolve(root)
    }
}
