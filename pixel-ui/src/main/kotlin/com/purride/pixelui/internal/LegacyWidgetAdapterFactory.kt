package com.purride.pixelui.internal

import com.purride.pixelui.Widget

/**
 * retained runtime 与 legacy widget 适配层之间的单一入口。
 *
 * BuildOwner 只依赖这个工厂，不再直接知道 `adaptLegacyWidget(...)`
 * 或 `LegacyAdapterElement` 的构造细节。
 */
internal object LegacyWidgetAdapterFactory {
    fun inflate(widget: Widget): Element? {
        return adaptLegacyWidget(widget)?.let(::LegacyAdapterElement)
    }
}
