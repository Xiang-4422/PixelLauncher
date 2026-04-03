package com.purride.pixelui.internal

/**
 * bridge 渲染树的统一渲染入口。
 *
 * element tree renderer 只依赖这层协议，不直接依赖 `BridgeRenderRuntime`
 * 的具体类型。
 */
internal fun interface BridgeTreeRenderer {
    fun render(
        root: BridgeRenderNode,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult
}
