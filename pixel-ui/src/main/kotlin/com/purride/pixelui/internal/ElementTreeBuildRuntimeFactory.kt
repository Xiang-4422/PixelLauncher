package com.purride.pixelui.internal

/**
 * retained element tree 构建运行时的默认工厂。
 */
internal object ElementTreeBuildRuntimeFactory {
    fun createDefault(
        onVisualUpdate: () -> Unit,
        widgetAdapter: WidgetAdapter,
    ): ElementTreeBuildRuntime {
        return RetainedBuildRuntime(
            onVisualUpdate = onVisualUpdate,
            widgetAdapter = widgetAdapter,
        )
    }
}
