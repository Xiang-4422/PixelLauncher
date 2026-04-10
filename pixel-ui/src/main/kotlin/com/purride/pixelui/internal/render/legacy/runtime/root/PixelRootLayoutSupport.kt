package com.purride.pixelui.internal

/**
 * 负责根渲染阶段的根约束和根 bounds 计算。
 */
internal class PixelRootLayoutSupport(
    private val measureNode: (LegacyRenderNode, PixelConstraints) -> PixelSize,
) {
    /**
     * 解析根节点在当前逻辑尺寸下的约束。
     */
    fun rootConstraints(
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelConstraints {
        return PixelConstraints(
            maxWidth = logicalWidth,
            maxHeight = logicalHeight,
        )
    }

    /**
     * 解析根节点最终绘制 bounds。
     */
    fun rootBounds(
        root: LegacyRenderNode,
        constraints: PixelConstraints,
    ): PixelRect {
        val measuredRoot = measureNode(root, constraints)
        return PixelRect(
            left = 0,
            top = 0,
            width = measuredRoot.width.coerceAtMost(constraints.maxWidth),
            height = measuredRoot.height.coerceAtMost(constraints.maxHeight),
        )
    }
}
