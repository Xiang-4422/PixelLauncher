package com.purride.pixelui.internal

/**
 * 负责把内容尺寸和 modifier 约束收敛成最终测量结果。
 */
internal class PixelMeasureResultSupport {
    /**
     * 基于内容尺寸、modifier 和外层约束计算最终尺寸。
     */
    fun resolveMeasuredSize(
        contentSize: PixelSize,
        constraints: PixelConstraints,
        modifierInfo: PixelModifierInfo,
    ): PixelSize {
        val naturalWidth = contentSize.width + modifierInfo.paddingLeft + modifierInfo.paddingRight
        val naturalHeight = contentSize.height + modifierInfo.paddingTop + modifierInfo.paddingBottom
        val measuredWidth = modifierInfo.fixedWidth
            ?: if (modifierInfo.fillMaxWidth) constraints.maxWidth else naturalWidth
        val measuredHeight = modifierInfo.fixedHeight
            ?: if (modifierInfo.fillMaxHeight) constraints.maxHeight else naturalHeight

        return PixelSize(
            width = measuredWidth.coerceIn(0, constraints.maxWidth),
            height = measuredHeight.coerceIn(0, constraints.maxHeight),
        )
    }
}
