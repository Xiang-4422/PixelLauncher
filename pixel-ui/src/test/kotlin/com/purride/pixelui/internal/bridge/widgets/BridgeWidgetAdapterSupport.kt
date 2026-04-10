package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.PixelNode

/**
 * bridge widget 适配阶段用到的辅助逻辑。
 */
internal fun adaptBridgeWidget(widget: Widget): BridgeWidget? {
    return when (widget) {
        is BridgeWidget -> widget
        is PixelNode -> StaticBridgeNodeWidget(widget)
        else -> null
    }
}
