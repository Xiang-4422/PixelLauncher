package com.purride.pixelui.internal

/**
 * retained runtime 默认支持集合。
 *
 * retained 主链只通过这层拿两种能力：
 * 1. retained widget adapter
 * 2. element tree -> 像素渲染结果
 */
internal class DefaultRetainedRenderSupport(
    override val widgetAdapter: WidgetAdapter,
    override val elementTreeRenderer: ElementTreeRenderer,
) : RetainedRenderSupport {
}
