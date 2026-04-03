package com.purride.pixelui.internal

/**
 * bridge 渲染树的统一渲染入口。
 *
 * element tree renderer 只依赖这层协议，不直接依赖 `BridgeRenderRuntime`
 * 的具体类型。
 */
internal fun interface BridgeTreeRenderer {
    /**
     * 渲染已经解析好的 bridge 渲染请求。
     */
    fun render(request: BridgeRenderRequest): PixelRenderResult
}
