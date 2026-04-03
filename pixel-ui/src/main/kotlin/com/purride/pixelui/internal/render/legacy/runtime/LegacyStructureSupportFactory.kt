package com.purride.pixelui.internal

/**
 * legacy 文本之外其余 support 的默认工厂。
 *
 * 这层负责布局、视口、测量、节点渲染和根渲染 support 的装配，
 * 让默认 assembly factory 可以专注在高层组合，而不是继续堆全部构造步骤。
 */
internal object LegacyStructureSupportFactory {
    /**
     * 创建默认的结构 support 装配结果。
     */
    fun createDefault(
        callbacks: LegacyRenderCallbacks,
        measureNode: (LegacyRenderNode, PixelConstraints) -> PixelSize,
        textSupportAssembly: LegacyTextSupportAssembly,
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
        scrollAxisUnboundedMax: Int,
    ): LegacyStructureSupportAssembly {
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
        val viewportRenderSupport = PixelViewportRenderSupport(
            callbacks = callbacks,
            scrollAxisUnboundedMax = scrollAxisUnboundedMax,
        )
        val measureSupport = PixelMeasureSupport(
            measureNode = measureNode,
            textRenderSupport = textSupportAssembly.textRenderSupport,
            textFieldRenderSupport = textSupportAssembly.textFieldRenderSupport,
            layoutMeasureSupport = layoutMeasureSupport,
        )
        val nodeRenderSupport = PixelNodeRenderSupport(
            textRenderSupport = textSupportAssembly.textRenderSupport,
            textFieldRenderSupport = textSupportAssembly.textFieldRenderSupport,
            layoutRenderSupport = layoutRenderSupport,
            viewportRenderSupport = viewportRenderSupport,
            renderNode = renderNode,
        )
        val rootRenderSupport = PixelRootRenderSupport(
            callbacks = callbacks,
        )
        return LegacyStructureSupportAssembly(
            layoutRenderSupport = layoutRenderSupport,
            layoutMeasureSupport = layoutMeasureSupport,
            viewportRenderSupport = viewportRenderSupport,
            measureSupport = measureSupport,
            nodeRenderSupport = nodeRenderSupport,
            rootRenderSupport = rootRenderSupport,
        )
    }
}
