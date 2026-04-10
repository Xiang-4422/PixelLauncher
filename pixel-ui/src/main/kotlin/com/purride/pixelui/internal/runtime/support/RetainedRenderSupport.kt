package com.purride.pixelui.internal

/**
 * retained widget runtime 需要的最小支持集合。
 *
 * 当前这套支持同时承接：
 * - widget 到 retained element 的严格适配
 * - 新 pipeline renderer
 *
 * retained 主链只依赖这层协议，不直接依赖具体后端。
 */
internal interface RetainedRenderSupport {
    val widgetAdapter: WidgetAdapter
    val elementTreeRenderer: ElementTreeRenderer
}
