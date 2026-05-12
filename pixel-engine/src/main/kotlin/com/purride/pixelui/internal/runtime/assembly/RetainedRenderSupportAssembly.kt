package com.purride.pixelui.internal

/**
 * retained render support 的默认装配结果。
 *
 * 这层把 retained 主链真正依赖的两项能力对齐到同一个 assembly，避免 support
 * 工厂继续手工拼接 widget adapter 和 element tree renderer。
 */
internal data class RetainedRenderSupportAssembly(
    val widgetAdapter: WidgetAdapter,
    val elementTreeRenderer: ElementTreeRenderer,
) {
    /**
     * 基于当前 assembly 生成默认 retained render support。
     */
    fun toRenderSupport(): RetainedRenderSupport {
        return DefaultRetainedRenderSupport(
            widgetAdapter = widgetAdapter,
            elementTreeRenderer = elementTreeRenderer,
        )
    }
}
