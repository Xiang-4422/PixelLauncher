package com.purride.pixelui.internal

import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import kotlin.math.max

/**
 * 像素渲染阶段使用的二维尺寸。
 */
internal data class PixelSize(
    val width: Int,
    val height: Int,
)

/**
 * 像素渲染阶段使用的矩形区域。
 */
internal data class PixelRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    /**
     * 矩形右边界的开区间坐标。
     */
    val right: Int
        get() = left + width

    /**
     * 矩形下边界的开区间坐标。
     */
    val bottom: Int
        get() = top + height

    /**
     * 判断指定点是否位于当前矩形内部。
     */
    fun contains(x: Int, y: Int): Boolean {
        return x in left until right && y in top until bottom
    }

    /**
     * 按四边内缩矩形，并保证结果不会产生负尺寸。
     */
    fun inset(
        paddingLeft: Int,
        paddingTop: Int,
        paddingRight: Int,
        paddingBottom: Int,
    ): PixelRect {
        val nextLeft = (left + paddingLeft).coerceAtMost(right)
        val nextTop = (top + paddingTop).coerceAtMost(bottom)
        val nextRight = (right - paddingRight).coerceAtLeast(nextLeft)
        val nextBottom = (bottom - paddingBottom).coerceAtLeast(nextTop)
        return PixelRect(
            left = nextLeft,
            top = nextTop,
            width = nextRight - nextLeft,
            height = nextBottom - nextTop,
        )
    }

    /**
     * 平移当前矩形。
     */
    fun translate(deltaX: Int, deltaY: Int): PixelRect {
        return PixelRect(
            left = left + deltaX,
            top = top + deltaY,
            width = width,
            height = height,
        )
    }

    /**
     * 计算当前矩形与另一个矩形的交集。
     */
    fun intersect(other: PixelRect): PixelRect? {
        val nextLeft = max(left, other.left)
        val nextTop = max(top, other.top)
        val nextRight = minOf(right, other.right)
        val nextBottom = minOf(bottom, other.bottom)
        if (nextRight <= nextLeft || nextBottom <= nextTop) {
            return null
        }
        return PixelRect(
            left = nextLeft,
            top = nextTop,
            width = nextRight - nextLeft,
            height = nextBottom - nextTop,
        )
    }
}

/**
 * legacy 迁出后仍保留给 pipeline 内部使用的最大尺寸约束。
 */
internal data class PixelConstraints(
    val maxWidth: Int,
    val maxHeight: Int,
) {
    /**
     * 按四边 padding 收缩约束。
     */
    fun shrink(
        paddingLeft: Int,
        paddingTop: Int,
        paddingRight: Int,
        paddingBottom: Int,
    ): PixelConstraints {
        return PixelConstraints(
            maxWidth = (maxWidth - paddingLeft - paddingRight).coerceAtLeast(0),
            maxHeight = (maxHeight - paddingTop - paddingBottom).coerceAtLeast(0),
        )
    }
}

/**
 * 点击命中目标。
 */
internal data class PixelClickTarget(
    val bounds: PixelRect,
    val onClick: () -> Unit,
)

/**
 * 分页视口命中目标。
 */
internal data class PixelPagerTarget(
    val bounds: PixelRect,
    val axis: PixelAxis,
    val state: PixelPagerState,
    val controller: PixelPagerController,
    val onPageChanged: ((Int) -> Unit)?,
)

/**
 * 列表视口命中目标。
 */
internal data class PixelListTarget(
    val bounds: PixelRect,
    val viewportHeightPx: Int,
    val contentHeightPx: Int,
    val state: PixelListState,
    val controller: PixelListController,
)

/**
 * 文本输入命中目标。
 */
internal data class PixelTextInputTarget(
    val bounds: PixelRect,
    val state: PixelTextFieldState,
    val controller: PixelTextFieldController,
    val readOnly: Boolean,
    val autofocus: Boolean,
    val action: PixelTextInputAction,
    val onChanged: ((String) -> Unit)?,
    val onSubmitted: ((String) -> Unit)?,
)

/**
 * pipeline 渲染结果。
 */
internal data class PixelRenderResult(
    val buffer: PixelBuffer,
    val clickTargets: List<PixelClickTarget>,
    val pagerTargets: List<PixelPagerTarget>,
    val listTargets: List<PixelListTarget>,
    val textInputTargets: List<PixelTextInputTarget>,
)

/**
 * 单次 pipeline 渲染期间收集的可变会话。
 */
internal data class PixelRenderSession(
    val buffer: PixelBuffer,
    val clickTargets: MutableList<PixelClickTarget> = mutableListOf(),
    val pagerTargets: MutableList<PixelPagerTarget> = mutableListOf(),
    val listTargets: MutableList<PixelListTarget> = mutableListOf(),
    val textInputTargets: MutableList<PixelTextInputTarget> = mutableListOf(),
) {
    /**
     * 固化当前会话为对外渲染结果。
     */
    fun toRenderResult(): PixelRenderResult {
        return PixelRenderResult(
            buffer = buffer,
            clickTargets = clickTargets,
            pagerTargets = pagerTargets,
            listTargets = listTargets,
            textInputTargets = textInputTargets,
        )
    }
}
