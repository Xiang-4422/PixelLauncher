package com.purride.pixelui.internal

/**
 * legacy render support 默认装配结果。
 *
 * 这层把 legacy render support 对齐成独立 assembly，避免默认工厂继续直接持有
 * 具体 bundle 构造细节。
 */
internal data class LegacyRenderSupportAssembly(
    val renderSupport: LegacyRenderSupport,
) {
    /**
     * 返回当前 assembly 对应的 legacy render support。
     */
    fun toRenderSupport(): LegacyRenderSupport = renderSupport
}
