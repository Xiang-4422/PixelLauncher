package com.purride.pixelui.internal

/**
 * 宿主侧默认 widget runtime 装配结果。
 *
 * 当前只有一个 runtime 实例，但单独抽成 assembly 以后，
 * 宿主层和默认工厂不需要关心 retained runtime 的具体装配细节。
 */
internal data class WidgetRuntimeAssembly(
    val runtime: WidgetRenderRuntime,
) {
    /**
     * 返回当前 assembly 对应的宿主运行时。
     */
    fun toRuntime(): WidgetRenderRuntime = runtime

    /**
     * 通过当前 assembly 持有的宿主运行时执行一次渲染。
     */
    fun render(request: WidgetRenderRequest): PixelRenderResult {
        return runtime.render(request)
    }

    /**
     * 释放当前 assembly 持有的宿主运行时。
     */
    fun dispose() {
        runtime.dispose()
    }
}
