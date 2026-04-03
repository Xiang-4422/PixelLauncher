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
        val callbacks = createCallbacks(
            measureNode = measureNode,
            renderNode = renderNode,
        )
        val textSupportAssembly = createTextSupportAssembly(
            textRasterizer = textRasterizer,
        )
        val structureSupportAssembly = createStructureSupportAssembly(
            callbacks = callbacks,
            measureNode = measureNode,
            textSupportAssembly = textSupportAssembly,
            renderNode = renderNode,
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

    /**
     * 创建 legacy render callbacks。
     */
    private fun createCallbacks(
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
    ): LegacyRenderCallbacks {
        return LegacyRenderCallbacks(
            measureNode = measureNode,
            renderNode = renderNode,
        )
    }

    /**
     * 创建默认文本渲染 support。
     */
    private fun createTextRenderSupport(
        textRasterizer: PixelTextRasterizer,
    ): PixelTextRenderSupport {
        return PixelTextRenderSupport(defaultTextRasterizer = textRasterizer)
    }

    /**
     * 创建文本相关 support 的默认装配结果。
     */
    private fun createTextSupportAssembly(
        textRasterizer: PixelTextRasterizer,
    ): LegacyTextSupportAssembly {
        val textRenderSupport = createTextRenderSupport(textRasterizer)
        val textFieldRenderSupport = createTextFieldRenderSupport(
            defaultTextRasterizer = textRasterizer,
            textRenderSupport = textRenderSupport,
        )
        return LegacyTextSupportAssembly(
            textRenderSupport = textRenderSupport,
            textFieldRenderSupport = textFieldRenderSupport,
        )
    }

    /**
     * 创建默认文本输入渲染 support。
     */
    private fun createTextFieldRenderSupport(
        defaultTextRasterizer: PixelTextRasterizer,
        textRenderSupport: PixelTextRenderSupport,
    ): PixelTextFieldRenderSupport {
        return PixelTextFieldRenderSupport(
            defaultTextRasterizer = defaultTextRasterizer,
            textRenderSupport = textRenderSupport,
        )
    }

    /**
     * 创建默认布局渲染 support。
     */
    private fun createLayoutRenderSupport(
        callbacks: LegacyRenderCallbacks,
    ): PixelLayoutRenderSupport {
        return PixelLayoutRenderSupport(
            callbacks = callbacks,
        )
    }

    /**
     * 创建默认 viewport 渲染 support。
     */
    private fun createViewportRenderSupport(
        callbacks: LegacyRenderCallbacks,
        scrollAxisUnboundedMax: Int,
    ): PixelViewportRenderSupport {
        return PixelViewportRenderSupport(
            callbacks = callbacks,
            scrollAxisUnboundedMax = scrollAxisUnboundedMax,
        )
    }

    /**
     * 创建默认测量 support。
     */
    private fun createMeasureSupport(
        measureNode: (LegacyRenderNode, PixelConstraints) -> PixelSize,
        textRenderSupport: PixelTextRenderSupport,
        textFieldRenderSupport: PixelTextFieldRenderSupport,
        layoutRenderSupport: PixelLayoutRenderSupport,
    ): PixelMeasureSupport {
        return PixelMeasureSupport(
            measureNode = measureNode,
            textRenderSupport = textRenderSupport,
            textFieldRenderSupport = textFieldRenderSupport,
            layoutRenderSupport = layoutRenderSupport,
        )
    }

    /**
     * 创建默认节点渲染 support。
     */
    private fun createNodeRenderSupport(
        textRenderSupport: PixelTextRenderSupport,
        textFieldRenderSupport: PixelTextFieldRenderSupport,
        layoutRenderSupport: PixelLayoutRenderSupport,
        viewportRenderSupport: PixelViewportRenderSupport,
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
    ): PixelNodeRenderSupport {
        return PixelNodeRenderSupport(
            textRenderSupport = textRenderSupport,
            textFieldRenderSupport = textFieldRenderSupport,
            layoutRenderSupport = layoutRenderSupport,
            viewportRenderSupport = viewportRenderSupport,
            renderNode = renderNode,
        )
    }

    /**
     * 创建默认根渲染 support。
     */
    private fun createRootRenderSupport(
        callbacks: LegacyRenderCallbacks,
    ): PixelRootRenderSupport {
        return PixelRootRenderSupport(
            callbacks = callbacks,
        )
    }

    /**
     * 创建文本之外其余 support 的默认装配结果。
     */
    private fun createStructureSupportAssembly(
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
    ): LegacyStructureSupportAssembly {
        val layoutRenderSupport = createLayoutRenderSupport(
            callbacks = callbacks,
        )
        val viewportRenderSupport = createViewportRenderSupport(
            callbacks = callbacks,
            scrollAxisUnboundedMax = SCROLL_AXIS_UNBOUNDED_MAX,
        )
        val measureSupport = createMeasureSupport(
            measureNode = measureNode,
            textRenderSupport = textSupportAssembly.textRenderSupport,
            textFieldRenderSupport = textSupportAssembly.textFieldRenderSupport,
            layoutRenderSupport = layoutRenderSupport,
        )
        val nodeRenderSupport = createNodeRenderSupport(
            textRenderSupport = textSupportAssembly.textRenderSupport,
            textFieldRenderSupport = textSupportAssembly.textFieldRenderSupport,
            layoutRenderSupport = layoutRenderSupport,
            viewportRenderSupport = viewportRenderSupport,
            renderNode = renderNode,
        )
        val rootRenderSupport = createRootRenderSupport(
            callbacks = callbacks,
        )
        return LegacyStructureSupportAssembly(
            layoutRenderSupport = layoutRenderSupport,
            viewportRenderSupport = viewportRenderSupport,
            measureSupport = measureSupport,
            nodeRenderSupport = nodeRenderSupport,
            rootRenderSupport = rootRenderSupport,
        )
    }

    private const val SCROLL_AXIS_UNBOUNDED_MAX = 4096
}
