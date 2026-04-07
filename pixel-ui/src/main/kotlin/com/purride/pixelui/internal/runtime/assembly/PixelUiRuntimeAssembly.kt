package com.purride.pixelui.internal

/**
 * 宿主顶层 `PixelUiRuntime` 的默认装配结果。
 *
 * 这层把宿主真正持有的 widget runtime 对齐成一个更贴近 `PixelUiRuntime`
 * 语义的 assembly，避免最外层 runtime 直接面对更底层的 widget runtime assembly。
 */
internal data class PixelUiRuntimeAssembly(
    val widgetRuntimeAssembly: WidgetRuntimeAssembly,
) {
    /**
     * 使用当前 assembly 持有的 widget runtime 渲染请求。
     */
    fun render(request: WidgetRenderRequest): PixelRenderResult {
        return widgetRuntimeAssembly.render(request)
    }

    /**
     * 释放当前 assembly 持有的内部 runtime。
     */
    fun dispose() {
        widgetRuntimeAssembly.dispose()
    }
}
