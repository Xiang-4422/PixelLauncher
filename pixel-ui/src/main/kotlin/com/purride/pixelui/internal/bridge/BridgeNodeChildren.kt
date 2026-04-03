package com.purride.pixelui.internal

internal class BridgeNodeChildren(
    private val nodes: List<BridgeRenderNode>,
) {
    fun asList(): List<BridgeRenderNode> = nodes

    fun single(): BridgeRenderNode = nodes.single()
}
