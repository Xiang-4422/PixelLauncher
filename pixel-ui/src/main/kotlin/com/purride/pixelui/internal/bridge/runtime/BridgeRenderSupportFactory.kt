package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

/**
 * bridge 渲染支持的默认工厂。
 *
 * 这层负责把 bridge element tree renderer、bridge runtime 和 legacy renderer
 * 串成默认实现，避免 retained runtime 工厂继续知道这条装配链细节。
 */
internal object BridgeRenderSupportFactory {
    /**
     * 创建默认的 bridge element tree renderer。
     */
    fun createDefault(
        textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
    ): ElementTreeRenderer {
        return createDefaultAssembly(
            textRasterizer = textRasterizer,
        ).toElementTreeRenderer()
    }

    /**
     * 创建 bridge 渲染支持的默认装配结果。
     */
    fun createDefaultAssembly(
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
