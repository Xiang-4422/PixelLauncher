package com.purride.pixelui.internal

/**
 * 可被 bridge 渲染树解析器消费的 element 能力。
 *
 * `BridgeTreeResolver` 只依赖这层能力，不再直接识别具体的 bridge adapter
 * 实现类，方便后续继续替换桥接实现。
 */
internal interface BridgeResolvableElement {
    fun resolveBridgeNode(childNodes: List<BridgeRenderNode>): BridgeRenderNode
}
