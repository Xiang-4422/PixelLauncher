package com.purride.pixelui.internal

/**
 * legacy 布局 support 的默认工厂。
 *
 * 这层集中创建布局相关的 render/measure support，
 * 避免结构 support 工厂继续手写整段布局 wiring。
 */
internal object LegacyLayoutSupportFactory {
    /**
     * 创建默认的布局 support 装配结果。
     */
    fun createDefault(
        measureNode: (LegacyRenderNode, PixelConstraints) -> PixelSize,
        renderNode: (
            LegacyRenderNode,
            PixelRect,
            PixelConstraints,
            com.purride.pixelcore.PixelBuffer,
            MutableList<PixelClickTarget>,
            MutableList<PixelPagerTarget>,
            MutableList<PixelListTarget>,
            MutableList<PixelTextInputTarget>,
        ) -> Unit,
    ): LegacyLayoutSupportAssembly {
        val flexLayoutSupport = PixelFlexLayoutSupport(measureNode = measureNode)
        val positionedLayoutSupport = PixelPositionedLayoutSupport()
        val alignmentLayoutSupport = PixelAlignmentLayoutSupport()
        val surfaceRenderSupport = PixelSurfaceRenderSupport(
            measureNode = measureNode,
            renderNode = renderNode,
            alignmentLayoutSupport = alignmentLayoutSupport,
        )
        val positionedRenderSupport = PixelPositionedRenderSupport(
            measureNode = measureNode,
            renderNode = renderNode,
            positionedLayoutSupport = positionedLayoutSupport,
        )
        val stackRenderSupport = PixelStackRenderSupport(
            measureNode = measureNode,
            renderNode = renderNode,
            alignmentLayoutSupport = alignmentLayoutSupport,
            positionedRenderSupport = positionedRenderSupport,
        )
        val rowRenderSupport = PixelRowRenderSupport(
            flexLayoutSupport = flexLayoutSupport,
            alignmentLayoutSupport = alignmentLayoutSupport,
            renderNode = renderNode,
        )
        val columnRenderSupport = PixelColumnRenderSupport(
            flexLayoutSupport = flexLayoutSupport,
            alignmentLayoutSupport = alignmentLayoutSupport,
            renderNode = renderNode,
        )
        val layoutRenderSupport = PixelLayoutRenderSupport(
            surfaceRenderSupport = surfaceRenderSupport,
            stackRenderSupport = stackRenderSupport,
            rowRenderSupport = rowRenderSupport,
            columnRenderSupport = columnRenderSupport,
        )
        val layoutMeasureSupport = PixelLayoutMeasureSupport(
            flexLayoutSupport = flexLayoutSupport,
            positionedLayoutSupport = positionedLayoutSupport,
        )
        return LegacyLayoutSupportAssembly(
            layoutRenderSupport = layoutRenderSupport,
            layoutMeasureSupport = layoutMeasureSupport,
        )
    }
}
