package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

/**
 * 负责创建 bridge 渲染支持的默认装配结果。
 */
internal object BridgeRenderSupportAssemblyFactory {
    /**
     * 创建默认的 bridge 渲染支持装配结果。
     */
    fun createDefault(
        textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
    ): BridgeRenderSupportAssembly {
        val runtimeAssembly = BridgeRuntimeAssemblyFactory.createDefault(
            textRasterizer = textRasterizer,
        )
        return BridgeRenderSupportAssembly(
            bridgeTreeResolver = runtimeAssembly.bridgeTreeResolver,
            bridgeTreeRenderer = runtimeAssembly.bridgeTreeRenderer,
            elementTreeRenderer = BridgeElementTreeRenderer(
                bridgeTreeResolver = runtimeAssembly.bridgeTreeResolver,
                bridgeTreeRenderer = runtimeAssembly.bridgeTreeRenderer,
            ),
        )
    }
}
