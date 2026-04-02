package com.purride.pixelui

/**
 * 旧版公开节点抽象。
 *
 * 当前保留它是为了兼容已经落下的 demo 和组件实现，
 * 但后续主公开语言会切到 Flutter 风格的 `Widget`。
 */
interface PixelNode : Widget {
    val modifier: PixelModifier
}
