package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

/**
 * bridge runtime 的默认工厂。
 *
 * 这层只负责 bridge resolver 和 tree renderer 的默认装配，
 * 让更高一层的 bridge support factory 只处理 element tree renderer 的组合。
 */
internal object BridgeRuntimeAssemblyFactory {
    /**
     * 创建默认的 bridge runtime 装配结果。
     */
    fun createDefault(
        textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
    ): BridgeRuntimeAssembly {
        val bridgeTreeResolver = DefaultBridgeTreeResolver
        val bridgeTreeRenderer = BridgeRenderRuntime(
            legacyTreeRenderer = LegacyTreeRendererFactory.createDefault(
                textRasterizer = textRasterizer,
            ),
        )
        return BridgeRuntimeAssembly(
            bridgeTreeResolver = bridgeTreeResolver,
            bridgeTreeRenderer = bridgeTreeRenderer,
        )
    }
}
