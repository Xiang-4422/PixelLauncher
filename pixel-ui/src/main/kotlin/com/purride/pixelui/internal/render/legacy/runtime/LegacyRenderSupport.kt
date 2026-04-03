package com.purride.pixelui.internal

/**
 * legacy renderer façade 依赖的最小 support 协议。
 *
 * `PixelRenderRuntime` 只依赖这层协议，不直接依赖具体 bundle 类型。
 */
internal interface LegacyRenderSupport {
    fun renderRoot(
        root: LegacyRenderNode,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult
}
