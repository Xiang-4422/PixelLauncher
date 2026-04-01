package com.purride.pixelui

/**
 * Flutter 风格的公开组件抽象。
 *
 * 当前阶段先把公开语言统一到 `Widget`，
 * 底层 runtime 仍然复用现有 `Pixel*` 节点与渲染实现。
 *
 * 这样后续可以逐步把内部结构演进到 `Widget / Element / RenderObject`，
 * 同时不需要一次性打碎已经可运行的 demo 和测试。
 */
interface Widget {
    /**
     * 组件稳定标识。
     *
     * 后续进入真正的 `Element` 树后，
     * 这里会继续作为节点复用与状态保持的重要线索。
     */
    val key: Any?

    /**
     * 当前组件的通用修饰信息。
     *
     * 现阶段仍然通过 `PixelModifier` 承接尺寸、点击等能力；
     * 后续公开层会逐步改为更 Flutter 风格的包装组件，但内部机制先保持兼容。
     */
    val modifier: PixelModifier
}
