package com.purride.pixelui.internal

import com.purride.pixelui.InheritedNotifier
import com.purride.pixelui.InheritedWidget
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget

/**
 * retained element 的构建协议。
 */
internal interface ElementInflater {
    /**
     * 为一个 widget 创建对应的 retained element。
     */
    fun inflate(widget: Widget): Element
}

/**
 * retained element 的默认构建实现。
 *
 * 这层负责把公开 Widget 解析成 retained element，不再让 `BuildOwner`
 * 自己识别所有 Widget 类型和 bridge adapter 细节。
 */
internal class DefaultElementInflater(
    private val widgetAdapter: WidgetAdapter,
) : ElementInflater {
    /**
     * 优先解析 retained 内建 element，再把剩余 widget 交给外部 adapter。
     */
    override fun inflate(widget: Widget): Element {
        return when (widget) {
            is InheritedNotifier<*> -> InheritedNotifierElement(widget)
            is InheritedWidget -> InheritedElement(widget)
            is RenderObjectWidget -> widget.createElement()
            is StatefulWidget -> StatefulElement(widget)
            is StatelessWidget -> StatelessElement(widget)
            else -> widgetAdapter.adapt(
                request = WidgetAdaptRequest(widget = widget),
            )
                ?: error("当前 Widget 还没有接入 retained build runtime: ${widget::class.qualifiedName}")
        }
    }
}
