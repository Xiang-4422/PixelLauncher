package com.purride.pixelui.internal

/**
 * bridge 渲染阶段的中间装配结果。
 *
 * 这层把已经解析好的 bridge root 和用于渲染的请求参数对齐起来，
 * 让 bridge element tree renderer 不再在一个方法里同时手拼两个阶段的中间对象。
 */
internal data class BridgeRenderAssembly(
    val bridgeRoot: BridgeRenderNode,
    val renderRequest: BridgeRenderRequest,
) {
    /**
     * 使用给定的 bridge tree renderer 渲染当前 assembly。
     */
    fun renderWith(renderer: BridgeTreeRenderer): PixelRenderResult {
        return renderer.render(request = renderRequest)
    }
}
