package com.purride.pixelui.internal

/**
 * legacy 渲染树的统一渲染入口。
 *
 * bridge 层只依赖这层协议，不直接依赖 `PixelRenderRuntime` 的具体类型。
 */
internal fun interface LegacyTreeRenderer {
    /**
     * 渲染已经解析好的 legacy 渲染请求。
     */
    fun render(request: LegacyRenderRequest): PixelRenderResult
}
