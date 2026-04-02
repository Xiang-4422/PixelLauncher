package com.purride.pixelui.internal

/**
 * legacy 渲染树的统一渲染入口。
 *
 * bridge 层只依赖这层协议，不直接依赖 `PixelRenderRuntime` 的具体类型。
 */
internal fun interface LegacyTreeRenderer {
    fun render(
        root: LegacyRenderNode,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult
}
