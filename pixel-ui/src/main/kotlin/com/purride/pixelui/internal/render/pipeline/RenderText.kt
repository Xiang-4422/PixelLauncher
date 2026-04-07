package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelTone
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.TextDirection
import com.purride.pixelui.internal.legacy.PixelTextAlign
import kotlin.math.min

/**
 * 新渲染管线里的最小文本对象。
 *
 * 第一版只覆盖单行文本、三种水平对齐，以及最基本的尺寸/填充/clickable 修饰。
 */
internal class RenderText(
    private val text: String,
    private val style: PixelTextStyle,
    private val textAlign: PixelTextAlign,
    private val textDirection: TextDirection,
    private val defaultTextRasterizer: PixelTextRasterizer,
    private val explicitWidth: Int? = null,
    private val explicitHeight: Int? = null,
    private val occupyFullWidth: Boolean = false,
    private val fillMaxWidth: Boolean = false,
    private val fillMaxHeight: Boolean = false,
    private val paddingLeft: Int = 0,
    private val paddingTop: Int = 0,
    private val paddingRight: Int = 0,
    private val paddingBottom: Int = 0,
    private val onClick: (() -> Unit)? = null,
) : RenderBox() {
    private var rasterizer: PixelTextRasterizer = style.textRasterizer ?: defaultTextRasterizer
    private var textWidth = 0
    private var textHeight = 0
    private var drawTextX = 0
    private var drawTextY = 0

    /**
     * 按给定约束测量文本对象。
     */
    override fun layout(constraints: RenderConstraints) {
        rasterizer = style.textRasterizer ?: defaultTextRasterizer
        textWidth = rasterizer.measureText(text)
        textHeight = rasterizer.measureHeight(text.ifEmpty { " " })

        val horizontalPadding = paddingLeft + paddingRight
        val verticalPadding = paddingTop + paddingBottom

        val measuredWidth = when {
            explicitWidth != null -> explicitWidth
            fillMaxWidth || occupyFullWidth -> constraints.maxWidth
            else -> textWidth + horizontalPadding
        }
        val measuredHeight = when {
            explicitHeight != null -> explicitHeight
            fillMaxHeight -> constraints.maxHeight
            else -> textHeight + verticalPadding
        }

        size = RenderSize(
            width = constraints.constrainWidth(measuredWidth),
            height = constraints.constrainHeight(measuredHeight),
        )

        val contentWidth = (size.width - horizontalPadding).coerceAtLeast(0)
        drawTextX = paddingLeft + resolveLineStartX(
            availableWidth = contentWidth,
            lineWidth = textWidth,
        )
        drawTextY = paddingTop
    }

    /**
     * 把文本绘制到目标 buffer。
     */
    override fun paint(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    ) {
        if (text.isEmpty()) {
            return
        }
        val contentWidth = (size.width - paddingLeft - paddingRight).coerceAtLeast(0)
        val contentHeight = (size.height - paddingTop - paddingBottom).coerceAtLeast(0)
        if (contentWidth == 0 || contentHeight == 0) {
            return
        }
        val destinationX = offsetX + drawTextX
        val destinationY = offsetY + drawTextY
        if (textWidth <= contentWidth && textHeight <= contentHeight) {
            rasterizer.drawText(
                buffer = context.buffer,
                text = text,
                x = destinationX,
                y = destinationY,
                value = style.tone.value,
            )
            return
        }

        val scratch = PixelBuffer(
            width = textWidth.coerceAtLeast(1),
            height = textHeight.coerceAtLeast(1),
        )
        rasterizer.drawText(
            buffer = scratch,
            text = text,
            x = 0,
            y = 0,
            value = style.tone.value,
        )
        blitOpaqueText(
            source = scratch,
            destination = context.buffer,
            destX = destinationX,
            destY = destinationY,
            copyWidth = min(contentWidth, scratch.width),
            copyHeight = min(contentHeight, scratch.height),
        )
    }

    /**
     * 执行文本对象的命中测试。
     */
    override fun hitTest(
        localX: Int,
        localY: Int,
        result: HitTestResult,
    ) {
        if (localX !in 0 until size.width || localY !in 0 until size.height) {
            return
        }
        if (onClick != null) {
            result.add(this)
        }
    }

    /**
     * 导出文本对象的点击目标。
     */
    override fun collectClickTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelClickTarget>,
    ) {
        onClick ?: return
        targets += PixelClickTarget(
            bounds = PixelRect(
                left = offsetX,
                top = offsetY,
                width = size.width,
                height = size.height,
            ),
            onClick = onClick,
        )
    }

    /**
     * 解析单行文本的水平起点。
     */
    private fun resolveLineStartX(
        availableWidth: Int,
        lineWidth: Int,
    ): Int {
        val freeWidth = (availableWidth - lineWidth).coerceAtLeast(0)
        return when (textAlign) {
            PixelTextAlign.CENTER -> freeWidth / 2
            PixelTextAlign.END -> if (textDirection == TextDirection.RTL) 0 else freeWidth
            PixelTextAlign.START -> if (textDirection == TextDirection.RTL) freeWidth else 0
        }
    }

    /**
     * 只把文本 scratch buffer 里非背景像素拷到目标 buffer，避免裁剪路径抹掉底色。
     */
    private fun blitOpaqueText(
        source: PixelBuffer,
        destination: PixelBuffer,
        destX: Int,
        destY: Int,
        copyWidth: Int,
        copyHeight: Int,
    ) {
        for (row in 0 until copyHeight) {
            for (column in 0 until copyWidth) {
                val value = source.getPixel(column, row)
                if (value == PixelTone.OFF.value) {
                    continue
                }
                destination.setPixel(
                    x = destX + column,
                    y = destY + row,
                    value = value,
                )
            }
        }
    }
}
