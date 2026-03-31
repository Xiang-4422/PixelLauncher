package com.purride.pixelui

/**
 * 通用像素组件修饰器。
 *
 * 设计方向参考 Compose 的 Modifier，但第一版先只做一个轻量容器，
 * 用来稳定 API 入口，不在这一阶段引入完整链式语义实现。
 */
data class PixelModifier(
    val elements: List<PixelModifierElement> = emptyList(),
) {
    fun then(element: PixelModifierElement): PixelModifier = copy(elements = elements + element)

    companion object {
        val Empty = PixelModifier()
    }
}

/**
 * 修饰器元素标记接口。
 *
 * 后续尺寸、间距、点击、焦点、滚动等能力都会拆成独立 element，
 * 运行时再基于 element 类型做布局和事件能力注入。
 */
interface PixelModifierElement
