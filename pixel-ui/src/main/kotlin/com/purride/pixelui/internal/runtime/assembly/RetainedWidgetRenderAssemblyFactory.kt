package com.purride.pixelui.internal

/**
 * 负责创建 retained widget 渲染阶段的默认装配结果。
 */
internal object RetainedWidgetRenderAssemblyFactory {
    /**
     * 基于 widget render request 和 build runtime 创建渲染装配结果。
     */
    fun create(
        request: WidgetRenderRequest,
        buildRuntime: ElementTreeBuildRuntime,
    ): RetainedWidgetRenderAssembly {
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
}
