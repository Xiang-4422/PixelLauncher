package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer

/**
 * 新渲染管线里的盒模型尺寸。
 */
internal data class RenderSize(
    val width: Int,
    val height: Int,
) {
    companion object {
        val Zero = RenderSize(width = 0, height = 0)
    }
}

/**
 * 新渲染管线里的布局约束。
 *
 * 第一版先稳定最小盒模型协议：上下界宽高约束，以及若干常用收敛方法。
 */
internal data class RenderConstraints(
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
    fun constrainWidth(width: Int): Int {
        return width.coerceIn(minWidth, maxWidth)
    }

    /**
     * 用当前约束收敛高度。
     */
    fun constrainHeight(height: Int): Int {
        return height.coerceIn(minHeight, maxHeight)
    }

    /**
     * 把当前约束减去四向内边距，得到子节点可用约束。
     */
    fun inset(
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
 */
internal data class PaintContext(
    val buffer: PixelBuffer,
)

/**
 * 新渲染管线里的命中测试结果。
 */
internal data class HitTestResult(
    val hits: MutableList<RenderObject> = mutableListOf(),
) {
    /**
     * 追加一个命中节点。
     */
    fun add(target: RenderObject) {
        hits += target
    }
}
