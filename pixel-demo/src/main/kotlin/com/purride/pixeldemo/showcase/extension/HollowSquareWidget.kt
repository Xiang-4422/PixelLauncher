package com.purride.pixeldemo.showcase.extension

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.advanced.PixelLeafRenderObjectWidget
import com.purride.pixelui.advanced.PixelPaintContext
import com.purride.pixelui.advanced.PixelRenderBox
import com.purride.pixelui.advanced.PixelRenderConstraints
import com.purride.pixelui.advanced.PixelRenderObject
import com.purride.pixelui.advanced.PixelRenderSize

/**
 * 用于演示 `pixelui.advanced` 公开扩展点的自定义叶节点 widget。
 *
 * 在指定约束下渲染一个空心方框（只绘制边框像素），并在 widget 属性变化
 * 时通过 [markNeedsLayout] / [markNeedsPaint] 触发增量重绘，验证
 * Widget → Element → RenderObject 更新链路正常工作。
 */
class HollowSquareWidget(
    val side: Int,
    val color: PixelColor,
    override val key: Any? = null,
) : PixelLeafRenderObjectWidget(key = key) {

    override fun createRenderObject(context: BuildContext): PixelRenderObject =
        HollowSquareRender(side = side, color = color)

    override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
        (renderObject as HollowSquareRender).update(side = side, color = color)
    }
}

/**
 * [HollowSquareWidget] 对应的 render object。
 *
 * layout：把自身尺寸收敛到 [side] × [side]（受外部约束夹紧）。
 * paint：只画方框四条边的像素（空心矩形），用 [color] 颜色值。
 * update：equality 短路——属性未变不触发任何脏标记。
 */
internal class HollowSquareRender(
    private var side: Int,
    private var color: PixelColor,
) : PixelRenderBox() {

    override fun layout(constraints: PixelRenderConstraints) {
        size = PixelRenderSize(
            width = constraints.constrainWidth(side),
            height = constraints.constrainHeight(side),
        )
    }

    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return
        for (x in 0 until w) {
            context.buffer.setPixel(offsetX + x, offsetY, color)
            context.buffer.setPixel(offsetX + x, offsetY + h - 1, color)
        }
        for (y in 1 until h - 1) {
            context.buffer.setPixel(offsetX, offsetY + y, color)
            context.buffer.setPixel(offsetX + w - 1, offsetY + y, color)
        }
    }

    fun update(side: Int, color: PixelColor) {
        val sizeChanged = this.side != side
        val colorChanged = this.color != color
        if (!sizeChanged && !colorChanged) return
        this.side = side
        this.color = color
        if (sizeChanged) markNeedsLayout()
        markNeedsPaint()
    }
}
