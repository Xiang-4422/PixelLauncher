package com.purride.pixelui.internal

import com.purride.pixelui.Widget

/**
 * 负责创建 widget 渲染请求。
 */
internal object WidgetRenderRequestFactory {
    /**
     * 基于根 widget 和逻辑尺寸创建默认渲染请求。
     */
    fun create(
        root: Widget,
        logicalWidth: Int,
        logicalHeight: Int,
    ): WidgetRenderRequest {
        return WidgetRenderRequest(
            root = root,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
        )
    }
}
