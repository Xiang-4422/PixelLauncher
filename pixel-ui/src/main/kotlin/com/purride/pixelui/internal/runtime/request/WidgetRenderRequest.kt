package com.purride.pixelui.internal

import com.purride.pixelui.Widget

/**
 * Widget 根树交给 Widget runtime 的显式渲染请求。
 */
internal data class WidgetRenderRequest(
    val root: Widget,
    val logicalWidth: Int,
    val logicalHeight: Int,
)
