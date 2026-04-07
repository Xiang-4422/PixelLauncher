package com.purride.pixelui.internal

import com.purride.pixelui.Widget

/**
 * Widget 根树到像素渲染结果的统一运行时协议。
 *
 * 宿主层和上层包装只依赖这层，不直接依赖具体 retained runtime 实现。
 */
internal interface WidgetRenderRuntime {
    /**
     * 渲染显式的 Widget 渲染请求。
     */
    fun render(request: WidgetRenderRequest): PixelRenderResult

    /**
     * 渲染一棵 Widget 根树。
     */
    fun render(
        root: Widget,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        return render(
            request = WidgetRenderRequestFactory.create(
                root = root,
                logicalWidth = logicalWidth,
                logicalHeight = logicalHeight,
            ),
        )
    }

    /**
     * 释放运行时持有的资源。
     */
    fun dispose()
}
