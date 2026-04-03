package com.purride.pixelui.internal

/**
 * 负责把 modifier 解析结果转换成渲染前的上下文。
 */
internal data class PixelNodeModifierContext(
    val modifierInfo: PixelModifierInfo,
    val paddedBounds: PixelRect,
    val innerConstraints: PixelConstraints,
)

/**
 * 创建 legacy 节点渲染所需的 modifier 上下文。
 */
internal object PixelNodeModifierContextFactory {
    /**
     * 基于当前节点、bounds 和 constraints 创建 modifier 上下文。
     */
    fun create(
        node: LegacyRenderNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
    ): PixelNodeModifierContext {
        val modifierInfo = PixelModifierSupport.resolve(node.modifier)
        return PixelNodeModifierContext(
            modifierInfo = modifierInfo,
            paddedBounds = bounds.inset(
                paddingLeft = modifierInfo.paddingLeft,
                paddingTop = modifierInfo.paddingTop,
                paddingRight = modifierInfo.paddingRight,
                paddingBottom = modifierInfo.paddingBottom,
            ),
            innerConstraints = constraints.shrink(
                paddingLeft = modifierInfo.paddingLeft,
                paddingTop = modifierInfo.paddingTop,
                paddingRight = modifierInfo.paddingRight,
                paddingBottom = modifierInfo.paddingBottom,
            ),
        )
    }
}
