package com.purride.pixelui.internal

/**
 * 负责 legacy 节点测量前的 modifier 处理和最终尺寸收敛。
 */
internal class PixelMeasureSupport(
    private val measureNode: (LegacyRenderNode, PixelConstraints) -> PixelSize,
    private val textRenderSupport: PixelTextRenderSupport,
    private val textFieldRenderSupport: PixelTextFieldRenderSupport,
    private val layoutRenderSupport: PixelLayoutRenderSupport,
) {
    private val measureDispatch = PixelMeasureDispatch(
        measureNode = measureNode,
        textRenderSupport = textRenderSupport,
        textFieldRenderSupport = textFieldRenderSupport,
        layoutRenderSupport = layoutRenderSupport,
    )

    /**
     * 测量节点最终尺寸。
     */
    fun measure(
        node: LegacyRenderNode,
        constraints: PixelConstraints,
        modifierInfo: PixelModifierInfo,
    ): PixelSize {
        val innerConstraints = constraints.shrink(
            paddingLeft = modifierInfo.paddingLeft,
            paddingTop = modifierInfo.paddingTop,
            paddingRight = modifierInfo.paddingRight,
            paddingBottom = modifierInfo.paddingBottom,
        )
        val contentSize = measureDispatch.measureContent(
            node = node,
            innerConstraints = innerConstraints,
        )

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
