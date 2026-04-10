package com.purride.pixelui.internal

import com.purride.pixelui.Widget

/**
 * retained Widget 树到 pipeline renderer 的运行时。
 *
 * 当前阶段它显式负责两步：
 * 1. 用 retained build runtime 把 Widget 树解析成 retained element tree
 * 2. 再把 element tree 交给 pipeline renderer 输出像素结果
 */
internal class RetainedWidgetRenderRuntime(
    private val buildRuntime: ElementTreeBuildRuntime,
    private val elementTreeRenderer: ElementTreeRenderer,
) : WidgetRenderRuntime {
    /**
     * 先解析 Widget 树，再渲染解析后的 element tree。
     */
    override fun render(request: WidgetRenderRequest): PixelRenderResult {
        val assembly = RetainedWidgetRenderAssemblyFactory.create(
            request = request,
            buildRuntime = buildRuntime,
        )
        return assembly.renderWith(renderer = elementTreeRenderer)
    }

    /**
     * 释放 retained build runtime 持有的 element tree 资源。
     */
    override fun dispose() {
        buildRuntime.dispose()
    }
}
