package com.purride.pixelui.internal

/**
 * 负责创建宿主侧默认 widget runtime 装配结果。
 */
internal object WidgetRuntimeAssemblyFactory {
    /**
     * 基于 retained widget runtime 创建宿主 runtime 装配结果。
     */
    fun create(
        runtime: WidgetRenderRuntime,
    ): WidgetRuntimeAssembly {
        return WidgetRuntimeAssembly(runtime)
    }
}
