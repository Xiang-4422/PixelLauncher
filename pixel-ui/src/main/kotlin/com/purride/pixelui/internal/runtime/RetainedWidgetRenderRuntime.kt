package com.purride.pixelui.internal

import com.purride.pixelui.Widget

/**
 * retained Widget 树到 legacy renderer 的过渡运行时。
 *
 * 当前阶段它显式负责两步：
 * 1. 用 retained build runtime 把 Widget 树解析成 retained element tree
 * 2. 再由 bridge 把结果交给纯 legacy renderer 输出像素结果
 *
 * 这样 `PixelRenderRuntime` 可以继续收敛成纯渲染器，不再同时承担
 * Widget 解析职责。
 */
internal class RetainedWidgetRenderRuntime(
    private val buildRuntime: ElementTreeBuildRuntime,
    private val elementTreeRenderer: ElementTreeRenderer,
) : WidgetRenderRuntime {
    /**
     * 先解析 Widget 树，再渲染解析后的 element tree。
     */
    override fun render(request: WidgetRenderRequest): PixelRenderResult {
        val assembly = createRenderAssembly(request)
        return assembly.renderWith(renderer = elementTreeRenderer)
    }

    /**
     * 为一次 widget 渲染构建 retained 中间装配结果。
     */
    private fun createRenderAssembly(request: WidgetRenderRequest): RetainedWidgetRenderAssembly {
        val elementRoot = buildRuntime.resolveElementTree(
            request = ElementTreeBuildRequest(
                root = request.root,
            ),
        )
        return RetainedWidgetRenderAssembly(
            elementRoot = elementRoot,
            renderRequest = ElementTreeRenderRequest(
                root = elementRoot,
                logicalWidth = request.logicalWidth,
                logicalHeight = request.logicalHeight,
            ),
        )
    }

    /**
     * 释放 retained build runtime 持有的 element tree 资源。
     */
    override fun dispose() {
        buildRuntime.dispose()
    }
}
