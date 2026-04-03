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
        val textRenderSupport = PixelTextRenderSupport(defaultTextRasterizer = textRasterizer)
        val textFieldRenderSupport = PixelTextFieldRenderSupport(
            defaultTextRasterizer = textRasterizer,
            textRenderSupport = textRenderSupport,
        )
        val layoutRenderSupport = PixelLayoutRenderSupport(
            measureNode = measureNode,
            renderNode = renderNode,
        )
        val viewportRenderSupport = PixelViewportRenderSupport(
            measureNode = measureNode,
            renderNode = renderNode,
            scrollAxisUnboundedMax = SCROLL_AXIS_UNBOUNDED_MAX,
        )
        val measureSupport = PixelMeasureSupport(
            measureNode = measureNode,
            textRenderSupport = textRenderSupport,
            textFieldRenderSupport = textFieldRenderSupport,
            layoutRenderSupport = layoutRenderSupport,
        )
        val nodeRenderSupport = PixelNodeRenderSupport(
            textRenderSupport = textRenderSupport,
            textFieldRenderSupport = textFieldRenderSupport,
            layoutRenderSupport = layoutRenderSupport,
            viewportRenderSupport = viewportRenderSupport,
            renderNode = renderNode,
        )
        val rootRenderSupport = PixelRootRenderSupport(
            measureNode = measureNode,
            renderNode = renderNode,
        )
        return LegacySupportAssembly(
            textRenderSupport = textRenderSupport,
            textFieldRenderSupport = textFieldRenderSupport,
            layoutRenderSupport = layoutRenderSupport,
            viewportRenderSupport = viewportRenderSupport,
            measureSupport = measureSupport,
            nodeRenderSupport = nodeRenderSupport,
            rootRenderSupport = rootRenderSupport,
        )
    }

    private const val SCROLL_AXIS_UNBOUNDED_MAX = 4096
}
