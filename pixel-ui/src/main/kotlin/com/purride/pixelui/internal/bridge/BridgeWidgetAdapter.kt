package com.purride.pixelui.internal

import com.purride.pixelui.Widget

/**
 * bridge 层提供给 retained build runtime 的 widget 适配入口。
 *
 * 这层直接把公开 Widget 适配成 bridge element，不再额外包一层 factory，
 * 避免 bridge graph 再持有多余的装配名字。
 */
internal object BridgeWidgetAdapter : WidgetAdapter {
    override fun adapt(widget: Widget): Element? {
        return adaptBridgeWidget(widget)?.let(::BridgeAdapterElement)
    }
}
