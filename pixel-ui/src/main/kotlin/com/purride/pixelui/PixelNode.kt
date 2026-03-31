package com.purride.pixelui

/**
 * 通用像素组件节点的最小抽象。
 *
 * 第一阶段先稳定节点边界，让后续的 retained tree、布局树和绘制树
 * 都围绕同一套基础数据结构演进。
 */
interface PixelNode {
    /**
     * 节点稳定标识。
     *
     * 后续 diff 过程会优先依赖 `type + key + position` 做节点复用。
     * 这里先把 key 作为显式字段预留出来，避免后续大面积改签名。
     */
    val key: Any?

    /**
     * 节点修饰信息。
     *
     * 所有尺寸、点击、滚动、焦点等通用行为，都统一挂在 modifier 上。
     */
    val modifier: PixelModifier
}
