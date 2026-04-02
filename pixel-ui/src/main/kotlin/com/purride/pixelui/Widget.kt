package com.purride.pixelui

/**
 * Flutter 风格的公开组件抽象。
 *
 * 当前开始把公开边界固定到 `Widget`，并让状态、主题、环境和自动重建
 * 真正建立在 retained build tree 上。
 *
 * 这一轮仍然允许内部暂时把最终组件树翻译到 legacy pixel renderer，
 * 但这个翻译层已经不再是公开 API 的一部分。
 */
interface Widget {
    /**
     * 组件稳定标识。
     *
     * retained build tree 会用它作为节点复用的重要线索。
     */
    val key: Any?
}
