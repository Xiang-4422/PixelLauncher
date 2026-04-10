package com.purride.pixelui

/**
 * Flutter 风格的公开组件抽象。
 *
 * 当前开始把公开边界固定到 `Widget`，并让状态、主题、环境和自动重建
 * 真正建立在 retained build tree 上。
 *
 * 当前默认渲染链路会把 retained element tree 直接交给 render object pipeline。
 */
interface Widget {
    /**
     * 组件稳定标识。
     *
     * retained build tree 会用它作为节点复用的重要线索。
     */
    val key: Any?
}

/**
 * 宿主级根组件提供器。
 *
 * 它和 `WidgetBuilder` 不同：
 * - `WidgetBuilder` 用在 retained tree 内部，带 `BuildContext`
 * - `RootWidgetProvider` 用在宿主入口，不依赖上下文
 */
typealias RootWidgetProvider = () -> Widget
