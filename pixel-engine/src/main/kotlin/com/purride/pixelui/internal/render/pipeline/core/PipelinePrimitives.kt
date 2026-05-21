package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelBufferPool
import com.purride.pixelcore.PixelColor

/**
 * 新渲染管线里的盒模型尺寸。
 */
public data class RenderSize(
    val width: Int,
    val height: Int,
) {
    public companion object {
        public val Zero: RenderSize = RenderSize(width = 0, height = 0)
    }
}

/**
 * 新渲染管线里的布局约束。
 *
 * 第一版先稳定最小盒模型协议：上下界宽高约束，以及若干常用收敛方法。
 */
public data class RenderConstraints(
    val minWidth: Int = 0,
    val maxWidth: Int,
    val minHeight: Int = 0,
    val maxHeight: Int,
) {
    init {
        require(minWidth <= maxWidth) { "minWidth 不能大于 maxWidth" }
        require(minHeight <= maxHeight) { "minHeight 不能大于 maxHeight" }
    }

    /**
     * 用当前约束收敛宽度。
     */
    public fun constrainWidth(width: Int): Int {
        return width.coerceIn(minWidth, maxWidth)
    }

    /**
     * 用当前约束收敛高度。
     */
    public fun constrainHeight(height: Int): Int {
        return height.coerceIn(minHeight, maxHeight)
    }

    /**
     * 把当前约束减去四向内边距，得到子节点可用约束。
     */
    public fun inset(
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0,
    ): RenderConstraints {
        val horizontal = (left + right).coerceAtLeast(0)
        val vertical = (top + bottom).coerceAtLeast(0)
        return RenderConstraints(
            minWidth = (minWidth - horizontal).coerceAtLeast(0),
            maxWidth = (maxWidth - horizontal).coerceAtLeast(0),
            minHeight = (minHeight - vertical).coerceAtLeast(0),
            maxHeight = (maxHeight - vertical).coerceAtLeast(0),
        )
    }
}

/**
 * 新渲染管线里的绘制上下文。
 *
 * 持有目标 buffer 与共享 buffer pool。需要 scratch buffer 的 RenderObject
 * 子类应该从 [bufferPool] 借（acquire）、在 paint 结束前还（release），
 * 而不是直接 new PixelBuffer。
 *
 * 单元测试构造 PaintContext 时可省略 [bufferPool]，会创建一个独立的
 * 新池子，保证测试隔离。
 */
public class PaintContext(
    public val buffer: PixelBuffer,
    public val bufferPool: PixelBufferPool = PixelBufferPool(),
) {
    public fun setColor(x: Int, y: Int, color: PixelColor) {
        buffer.setPixel(x, y, color)
    }

    public fun fillRect(x: Int, y: Int, w: Int, h: Int, color: PixelColor) {
        buffer.fillRect(x, y, w, h, color)
    }

    public fun drawRect(x: Int, y: Int, w: Int, h: Int, color: PixelColor) {
        buffer.drawRect(x, y, w, h, color)
    }
}

/**
 * 新渲染管线里的命中测试结果。
 */
public data class HitTestResult(
    val hits: MutableList<RenderObject> = mutableListOf(),
) {
    /**
     * 追加一个命中节点。
     */
    public fun add(target: RenderObject) {
        hits += target
    }
}
