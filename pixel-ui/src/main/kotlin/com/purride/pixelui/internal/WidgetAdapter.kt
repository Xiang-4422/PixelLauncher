package com.purride.pixelui.internal

import com.purride.pixelui.Widget

/**
 * retained build runtime 侧消费的统一 widget 适配入口。
 *
 * 当前主要由 bridge 层实现，把公开 Widget 适配成可挂到 retained element tree
 * 上的桥接 element。后续如果替换 bridge 实现，build runtime 只需要依赖这层接口。
 */
internal fun interface WidgetAdapter {
    fun adapt(widget: Widget): Element?
}
