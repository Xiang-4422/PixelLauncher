package com.purride.pixelui.internal

/**
 * bridge 渲染树交给 bridge renderer 的显式请求对象。
 */
internal data class BridgeRenderRequest(
    val root: BridgeRenderNode,
    val logicalWidth: Int,
    val logicalHeight: Int,
)
