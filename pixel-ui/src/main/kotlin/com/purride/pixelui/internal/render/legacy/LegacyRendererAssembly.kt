package com.purride.pixelui.internal

/**
 * legacy renderer 默认装配结果。
 *
 * 这层把 legacy support 和 renderer 对齐到一个 assembly，避免默认工厂继续把
 * support wiring 分散在多个位置。
 */
internal data class LegacyRendererAssembly(
    val renderSupport: LegacyRenderSupport,
    val renderer: LegacyTreeRenderer,
) {
    /**
     * 返回当前 assembly 对应的 legacy renderer。
     */
    fun toRenderer(): LegacyTreeRenderer = renderer
}
