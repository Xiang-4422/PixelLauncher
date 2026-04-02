package com.purride.pixelui.internal

import com.purride.pixelui.Widget

/**
 * retained runtime 与 bridge widget 适配层之间的单一入口。
 *
 * BuildOwner 只依赖这个工厂，不再直接知道 `adaptBridgeWidget(...)`
 * 或 `BridgeAdapterElement` 的构造细节。
 */
internal object BridgeWidgetAdapterFactory {
    fun inflate(widget: Widget): Element? {
        return adaptBridgeWidget(widget)?.let(::BridgeAdapterElement)
    }
}
