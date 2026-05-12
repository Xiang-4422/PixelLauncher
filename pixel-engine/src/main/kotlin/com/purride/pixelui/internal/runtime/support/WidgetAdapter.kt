package com.purride.pixelui.internal

import com.purride.pixelui.Widget

/**
 * retained build runtime 侧消费的统一 widget 适配入口。
 *
 * 当前默认路径已经直接识别 retained widget 与 render object widget，这层只保留
 * 给非默认测试或过渡代码显式接入额外 widget 类型。
 */
internal fun interface WidgetAdapter {
    /**
     * 适配一个 widget，返回可挂到 retained tree 的 element。
     */
    fun adapt(request: WidgetAdaptRequest): Element?

    /**
     * 直接适配单个 widget 的便捷入口。
     */
    fun adapt(widget: Widget): Element? = adapt(
        request = WidgetAdaptRequest(widget = widget),
    )
}

/**
 * 默认的严格 widget adapter。
 *
 * 它不做任何额外适配；未被 retained runtime 原生识别的 widget
 * 会在 `DefaultElementInflater` 中直接报错。
 */
internal object UnsupportedWidgetAdapter : WidgetAdapter {
    /**
     * 始终拒绝额外适配，让调用方看到明确的“不支持 widget”错误。
     */
    override fun adapt(request: WidgetAdaptRequest): Element? = null
}
