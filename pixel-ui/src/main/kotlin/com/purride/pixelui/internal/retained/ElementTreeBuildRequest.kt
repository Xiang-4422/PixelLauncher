package com.purride.pixelui.internal

import com.purride.pixelui.Widget

/**
 * Widget 根树交给 retained build runtime 的显式构建请求。
 */
internal data class ElementTreeBuildRequest(
    val root: Widget,
)
