package com.purride.pixelui.internal

/**
 * retained widget 渲染阶段的中间装配结果。
 *
 * 这层对齐“build 出来的 element tree”和“用于渲染的请求参数”，让 retained widget
 * runtime 不再在一个方法里同时手拼两个阶段的中间对象。
 */
internal data class RetainedWidgetRenderAssembly(
    val elementRoot: Element?,
    val renderRequest: ElementTreeRenderRequest,
) {
    /**
     * 使用给定的 element tree renderer 渲染当前 assembly。
     */
    fun renderWith(renderer: ElementTreeRenderer): PixelRenderResult {
        return renderer.render(request = renderRequest)
    }
}
