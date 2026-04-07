package com.purride.pixelui.internal

/**
 * legacy 节点运行时 support 的默认工厂。
 *
 * 这层集中创建测量、节点渲染和根渲染 support，
 * 避免结构 support 工厂继续承担节点运行时的具体 wiring。
 */
internal object LegacyNodeSupportFactory {
    /**
     * 创建默认的节点运行时 support 装配结果。
     */
    fun createDefault(
        callbacks: LegacyRenderCallbacks,
        textSupportAssembly: LegacyTextSupportAssembly,
        layoutSupportAssembly: LegacyLayoutSupportAssembly,
        viewportSupportAssembly: LegacyViewportSupportAssembly,
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
    ): LegacyNodeSupportAssembly {
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
        return LegacyNodeSupportAssembly(
            measureSupport = measureSupport,
            nodeRenderSupport = nodeRenderSupport,
            rootRenderSupport = rootRenderSupport,
        )
    }
}
