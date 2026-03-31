package com.purride.pixelui.node

import com.purride.pixelui.PixelModifier
import com.purride.pixelui.PixelNode

/**
 * 自定义像素绘制节点。
 *
 * 它是第一版明确保留的“受控逃生口”：
 * 当某些图形效果不适合用标准组件表达时，业务可以通过它接入自定义绘制，
 * 但不应该把整个页面重新退化成手写 renderer。
 */
data class CustomDraw(
    override val key: Any? = null,
    override val modifier: PixelModifier = PixelModifier.Empty,
    val onDraw: PixelCanvasScope.() -> Unit,
) : PixelNode

/**
 * 像素绘制作用域占位接口。
 *
 * 这里暂时不暴露具体绘制原语，后续等 :pixel-core 的底层能力迁移完成后，
 * 再把真正的缓冲区操作封装成受控 API。
 */
interface PixelCanvasScope
