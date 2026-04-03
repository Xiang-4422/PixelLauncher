package com.purride.pixelui.internal

import com.purride.pixelui.Widget

/**
 * Widget 根树到像素渲染结果的统一运行时协议。
 *
 * 宿主层和上层包装只依赖这层，不直接依赖具体 retained runtime 实现。
 */
internal interface WidgetRenderRuntime {
    fun render(
        root: Widget,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult

    fun dispose()
}
