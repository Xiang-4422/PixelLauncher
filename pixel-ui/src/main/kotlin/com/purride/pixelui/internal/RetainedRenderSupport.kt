package com.purride.pixelui.internal

/**
 * retained widget runtime 需要的最小支持集合。
 *
 * 当前这套支持由 bridge 层提供，但 retained 主链只依赖这层协议，不直接依赖
 * bridge graph 的具体类型。
 */
internal interface RetainedRenderSupport {
    val widgetAdapter: WidgetAdapter
    val elementTreeRenderer: ElementTreeRenderer
}
