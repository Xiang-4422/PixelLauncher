package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

/**
 * legacy support wiring 的默认工厂。
 */
internal object LegacySupportAssemblyFactory {
    /**
     * 创建默认的 legacy support assembly。
     */
    fun createDefault(
        textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
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
    ): LegacySupportAssembly {
        val callbacks = LegacyRenderCallbacksFactory.create(
            measureNode = measureNode,
            renderNode = renderNode,
        )
        val textSupportAssembly = LegacyTextSupportFactory.createDefault(
            textRasterizer = textRasterizer,
        )
        val structureSupportAssembly = LegacyStructureSupportFactory.createDefault(
            callbacks = callbacks,
            measureNode = measureNode,
            textSupportAssembly = textSupportAssembly,
            renderNode = renderNode,
            scrollAxisUnboundedMax = SCROLL_AXIS_UNBOUNDED_MAX,
        )
        return LegacySupportAssembly(
            textRenderSupport = textSupportAssembly.textRenderSupport,
            textFieldRenderSupport = textSupportAssembly.textFieldRenderSupport,
            layoutRenderSupport = structureSupportAssembly.layoutRenderSupport,
            viewportRenderSupport = structureSupportAssembly.viewportRenderSupport,
            measureSupport = structureSupportAssembly.measureSupport,
            nodeRenderSupport = structureSupportAssembly.nodeRenderSupport,
            rootRenderSupport = structureSupportAssembly.rootRenderSupport,
        )
    }

    private const val SCROLL_AXIS_UNBOUNDED_MAX = 4096
}
