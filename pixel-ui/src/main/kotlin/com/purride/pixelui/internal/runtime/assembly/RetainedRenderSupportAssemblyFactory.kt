package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

/**
 * 负责创建 retained render support 的默认装配结果。
 */
internal object RetainedRenderSupportAssemblyFactory {
    /**
     * 创建默认的 retained render support 装配结果。
     */
    fun createDefault(
        textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
    ): RetainedRenderSupportAssembly {
        val pipelineRenderer = PipelineElementTreeRenderer(
            bridgeTreeResolver = PipelineOnlyBridgeTreeResolver,
            defaultTextRasterizer = textRasterizer,
        )
        return RetainedRenderSupportAssembly(
            widgetAdapter = UnsupportedWidgetAdapter,
            elementTreeRenderer = pipelineRenderer,
        )
    }
}

/**
 * 默认运行时的严格 bridge resolver。
 *
 * 默认主线不再自动构造 bridge/legacy fallback；如果 retained tree 不是 direct
 * render object tree，这里返回 null 让 pipeline renderer 明确失败。
 */
private object PipelineOnlyBridgeTreeResolver : BridgeTreeResolving {
    /**
     * 默认路径不再解析 bridge tree。
     */
    override fun resolve(request: BridgeTreeResolveRequest): BridgeRenderNode? = null
}
