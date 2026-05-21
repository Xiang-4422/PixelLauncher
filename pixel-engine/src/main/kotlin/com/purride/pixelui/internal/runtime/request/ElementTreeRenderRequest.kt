package com.purride.pixelui.internal

/**
 * retained element tree 交给 element tree renderer 的显式请求对象。
 */
internal data class ElementTreeRenderRequest(
    val root: Element?,
    val logicalWidth: Int,
    val logicalHeight: Int,
)
