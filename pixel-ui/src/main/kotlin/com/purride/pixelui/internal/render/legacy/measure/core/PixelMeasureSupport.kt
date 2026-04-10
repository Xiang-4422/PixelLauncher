package com.purride.pixelui.internal

/**
 * 负责 legacy 节点测量前的 modifier 处理和最终尺寸收敛。
 */
internal class PixelMeasureSupport(
    private val measureNode: (LegacyRenderNode, PixelConstraints) -> PixelSize,
    private val textRenderSupport: PixelTextRenderSupport,
    private val textFieldRenderSupport: PixelTextFieldRenderSupport,
    private val layoutMeasureSupport: PixelLayoutMeasureSupport,
) {
    private val measureDispatch = PixelMeasureDispatch(
        measureNode = measureNode,
        textRenderSupport = textRenderSupport,
        textFieldRenderSupport = textFieldRenderSupport,
        layoutMeasureSupport = layoutMeasureSupport,
    )
    private val measureResultSupport = PixelMeasureResultSupport()

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
        return measureResultSupport.resolveMeasuredSize(
            contentSize = contentSize,
            constraints = constraints,
            modifierInfo = modifierInfo,
        )
    }
}
