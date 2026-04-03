package com.purride.pixelui.internal

/**
 * retained element tree 构建运行时的默认工厂。
 */
internal object ElementTreeBuildRuntimeFactory {
    /**
     * 创建默认的 retained build runtime。
     */
    fun createDefault(
        onVisualUpdate: () -> Unit,
        widgetAdapter: WidgetAdapter,
    ): ElementTreeBuildRuntime {
        return RetainedBuildRuntime(
            onVisualUpdate = onVisualUpdate,
            elementChildUpdater = DefaultElementChildUpdater(
                elementInflater = DefaultElementInflater(
                    widgetAdapter = widgetAdapter,
                ),
            ),
        )
    }
}
