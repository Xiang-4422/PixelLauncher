package com.purride.pixelui

/**
 * 旧兼容节点抽象。
 *
 * 它现在只服务 `pixel-ui` 模块内部的 legacy renderer，
 * 不再作为对外公开的页面组织语言。
 */
internal interface PixelNode : Widget {
    val modifier: PixelModifier
}
