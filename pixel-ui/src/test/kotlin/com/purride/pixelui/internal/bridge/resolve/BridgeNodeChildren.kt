package com.purride.pixelui.internal

/**
 * 包装已经解析完成的 bridge 子节点集合。
 */
internal class BridgeNodeChildren(
    private val nodes: List<BridgeRenderNode>,
) {
    /**
     * 以列表形式暴露所有子节点。
     */
    fun asList(): List<BridgeRenderNode> = nodes

    /**
     * 读取唯一子节点。
     */
    fun single(): BridgeRenderNode = nodes.single()
}
