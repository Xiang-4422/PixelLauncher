package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.internal.legacy.PixelAlignment

/**
 * 新渲染管线里的最小表面对象。
 *
 * 第一版同时承担：
 * - 背景/边框绘制
 * - 单 child 承接
 * - 尺寸、填满、padding、alignment
 * - 点击目标导出
 *
 * 这样 `Text + Surface` 这条最小链就能先独立跑起来。
 */
internal class RenderSurface(
    private val child: RenderBox? = null,
    private val fillTone: PixelTone? = null,
    private val borderTone: PixelTone? = null,
    private val alignment: PixelAlignment = PixelAlignment.TOP_START,
    private val explicitWidth: Int? = null,
    private val explicitHeight: Int? = null,
    private val fillMaxWidth: Boolean = false,
    private val fillMaxHeight: Boolean = false,
    private val outerPaddingLeft: Int = 0,
    private val outerPaddingTop: Int = 0,
    private val outerPaddingRight: Int = 0,
    private val outerPaddingBottom: Int = 0,
    private val contentPaddingLeft: Int = 0,
    private val contentPaddingTop: Int = 0,
    private val contentPaddingRight: Int = 0,
    private val contentPaddingBottom: Int = 0,
    private val onClick: (() -> Unit)? = null,
) : RenderBox() {
    private var childOffsetX = 0
    private var childOffsetY = 0

    init {
        child?.parent = this
    }

    /**
     * 暴露唯一子节点给通用遍历。
     */
    override fun visitChildren(visitor: (RenderObject) -> Unit) {
        child?.let(visitor)
    }

    /**
     * 按给定约束测量表面尺寸和子节点布局。
     */
    override fun layout(constraints: RenderConstraints) {
        val horizontalInsets = outerPaddingLeft + outerPaddingRight + contentPaddingLeft + contentPaddingRight
        val verticalInsets = outerPaddingTop + outerPaddingBottom + contentPaddingTop + contentPaddingBottom
        child?.layout(
            constraints = constraints.inset(
                left = horizontalInsets,
                top = verticalInsets,
                right = 0,
                bottom = 0,
            ),
        )

        val childWidth = child?.size?.width ?: 0
        val childHeight = child?.size?.height ?: 0
        val measuredWidth = when {
            explicitWidth != null -> explicitWidth
            fillMaxWidth -> constraints.maxWidth
            else -> childWidth + horizontalInsets
        }
        val measuredHeight = when {
            explicitHeight != null -> explicitHeight
            fillMaxHeight -> constraints.maxHeight
            else -> childHeight + verticalInsets
        }

        size = RenderSize(
            width = constraints.constrainWidth(measuredWidth),
            height = constraints.constrainHeight(measuredHeight),
        )

        val contentWidth = (
            size.width - outerPaddingLeft - outerPaddingRight - contentPaddingLeft - contentPaddingRight
        ).coerceAtLeast(0)
        val contentHeight = (
            size.height - outerPaddingTop - outerPaddingBottom - contentPaddingTop - contentPaddingBottom
        ).coerceAtLeast(0)

        childOffsetX = outerPaddingLeft + contentPaddingLeft + resolveChildOffsetX(
            availableWidth = contentWidth,
            childWidth = childWidth,
        )
        childOffsetY = outerPaddingTop + contentPaddingTop + resolveChildOffsetY(
            availableHeight = contentHeight,
            childHeight = childHeight,
        )
    }

    /**
     * 把表面和子节点画到目标 buffer。
     */
    override fun paint(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    ) {
        val surfaceLeft = offsetX + outerPaddingLeft
        val surfaceTop = offsetY + outerPaddingTop
        val surfaceWidth = (size.width - outerPaddingLeft - outerPaddingRight).coerceAtLeast(0)
        val surfaceHeight = (size.height - outerPaddingTop - outerPaddingBottom).coerceAtLeast(0)

        if (fillTone != null && surfaceWidth > 0 && surfaceHeight > 0) {
            context.buffer.fillRect(
                left = surfaceLeft,
                top = surfaceTop,
                rectWidth = surfaceWidth,
                rectHeight = surfaceHeight,
                value = fillTone.value,
            )
        }
        if (borderTone != null && surfaceWidth > 0 && surfaceHeight > 0) {
            context.buffer.drawRect(
                left = surfaceLeft,
                top = surfaceTop,
                rectWidth = surfaceWidth,
                rectHeight = surfaceHeight,
                value = borderTone.value,
            )
        }
        child?.paint(
            context = context,
            offsetX = offsetX + childOffsetX,
            offsetY = offsetY + childOffsetY,
        )
    }

    /**
     * 执行表面对象的命中测试。
     */
    override fun hitTest(
        localX: Int,
        localY: Int,
        result: HitTestResult,
    ) {
        if (localX !in 0 until size.width || localY !in 0 until size.height) {
            return
        }
        child?.hitTest(
            localX = localX - childOffsetX,
            localY = localY - childOffsetY,
            result = result,
        )
        if (onClick != null) {
            result.add(this)
        }
    }

    /**
     * 导出当前表面及其子树里的点击目标。
     */
    override fun collectClickTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelClickTarget>,
    ) {
        onClick?.let { clickHandler ->
            targets += PixelClickTarget(
                bounds = PixelRect(
                    left = offsetX,
                    top = offsetY,
                    width = size.width,
                    height = size.height,
                ),
                onClick = clickHandler,
            )
        }
        child?.collectClickTargets(
            offsetX = offsetX + childOffsetX,
            offsetY = offsetY + childOffsetY,
            targets = targets,
        )
    }

    /**
     * 解析子节点在当前内容区里的水平偏移。
     */
    private fun resolveChildOffsetX(
        availableWidth: Int,
        childWidth: Int,
    ): Int {
        val freeWidth = (availableWidth - childWidth).coerceAtLeast(0)
        return when (alignment) {
            PixelAlignment.TOP_CENTER,
            PixelAlignment.CENTER,
            PixelAlignment.BOTTOM_CENTER,
            -> freeWidth / 2

            PixelAlignment.TOP_END,
            PixelAlignment.CENTER_END,
            PixelAlignment.BOTTOM_END,
            -> freeWidth

            else -> 0
        }
    }

    /**
     * 解析子节点在当前内容区里的垂直偏移。
     */
    private fun resolveChildOffsetY(
        availableHeight: Int,
        childHeight: Int,
    ): Int {
        val freeHeight = (availableHeight - childHeight).coerceAtLeast(0)
        return when (alignment) {
            PixelAlignment.CENTER_START,
            PixelAlignment.CENTER,
            PixelAlignment.CENTER_END,
            -> freeHeight / 2

            PixelAlignment.BOTTOM_START,
            PixelAlignment.BOTTOM_CENTER,
            PixelAlignment.BOTTOM_END,
            -> freeHeight

            else -> 0
        }
    }
}
