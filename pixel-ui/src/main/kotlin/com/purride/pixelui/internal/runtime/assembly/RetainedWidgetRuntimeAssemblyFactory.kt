package com.purride.pixelui.internal

/**
 * 负责创建 retained widget runtime 的默认装配结果。
 */
internal object RetainedWidgetRuntimeAssemblyFactory {
    /**
     * 创建默认的 retained widget runtime 装配结果。
     */
    fun create(
        onVisualUpdate: () -> Unit,
        renderSupport: RetainedRenderSupport,
    ): RetainedWidgetRuntimeAssembly {
        return RetainedWidgetRuntimeAssembly(
            buildRuntime = ElementTreeBuildRuntimeFactory.createDefault(
                onVisualUpdate = onVisualUpdate,
                widgetAdapter = renderSupport.widgetAdapter,
            ),
            elementTreeRenderer = renderSupport.elementTreeRenderer,
        )
    }
}
