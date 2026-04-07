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
        val layoutSupportAssembly = LegacyLayoutSupportFactory.createDefault(
            measureNode = measureNode,
            renderNode = renderNode,
        )
        val viewportSupportAssembly = LegacyViewportSupportFactory.createDefault(
            callbacks = callbacks,
            scrollAxisUnboundedMax = scrollAxisUnboundedMax,
        )
        val measureSupport = PixelMeasureSupport(
            measureNode = measureNode,
            textRenderSupport = textSupportAssembly.textRenderSupport,
            textFieldRenderSupport = textSupportAssembly.textFieldRenderSupport,
            layoutMeasureSupport = layoutSupportAssembly.layoutMeasureSupport,
        )
        val nodeRenderSupport = PixelNodeRenderSupport(
            textRenderSupport = textSupportAssembly.textRenderSupport,
            textFieldRenderSupport = textSupportAssembly.textFieldRenderSupport,
            layoutRenderSupport = layoutSupportAssembly.layoutRenderSupport,
            viewportRenderSupport = viewportSupportAssembly.viewportRenderSupport,
            renderNode = renderNode,
        )
        val rootRenderSupport = PixelRootRenderSupport(
            callbacks = callbacks,
        )
        return LegacyStructureSupportAssembly(
            layoutRenderSupport = layoutSupportAssembly.layoutRenderSupport,
            layoutMeasureSupport = layoutSupportAssembly.layoutMeasureSupport,
            viewportRenderSupport = viewportSupportAssembly.viewportRenderSupport,
            measureSupport = measureSupport,
            nodeRenderSupport = nodeRenderSupport,
            rootRenderSupport = rootRenderSupport,
        )
    }
}
