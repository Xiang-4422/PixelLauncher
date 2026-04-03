package com.purride.pixelui.internal

/**
 * legacy 渲染树交给 legacy renderer 的显式请求对象。
 */
internal data class LegacyRenderRequest(
    val root: LegacyRenderNode,
    val logicalWidth: Int,
    val logicalHeight: Int,
)
