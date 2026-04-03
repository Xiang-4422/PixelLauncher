package com.purride.pixelui.internal

/**
 * retained runtime 默认支持集合。
 *
 * 当前默认实现仍然由 bridge 层提供，但 retained 主链只通过这层拿两种能力：
 * 1. retained widget adapter
 * 2. element tree -> 像素渲染结果
 *
 * 这样 retained 入口不再同时知道 bridge adapter、bridge renderer 和 bridge
 * element tree renderer 的具体拼装细节。
 */
internal class DefaultRetainedRenderSupport(
    override val widgetAdapter: WidgetAdapter,
    override val elementTreeRenderer: ElementTreeRenderer,
) : RetainedRenderSupport {
}
