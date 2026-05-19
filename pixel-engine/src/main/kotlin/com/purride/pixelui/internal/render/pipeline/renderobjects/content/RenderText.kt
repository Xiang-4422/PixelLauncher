package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelTone
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.TextDirection
import kotlin.math.min

/**
 * 新渲染管线里的文本对象。
 */
internal class RenderText(
    private var text: String,
    private var style: PixelTextStyle,
    private var textAlign: PixelTextAlign,
    private var textDirection: TextDirection,
    private var softWrap: Boolean,
    private var overflow: PixelTextOverflow,
    private var maxLines: Int,
    private var defaultTextRasterizer: PixelTextRasterizer,
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
    private var displayText = text
    private var drawTextX = 0
    private var drawTextY = 0

    /**
     * 用新的文本配置更新当前对象，并触发布局与绘制刷新。
     */
    fun updateText(
        text: String,
        style: PixelTextStyle,
        textAlign: PixelTextAlign,
        textDirection: TextDirection,
        softWrap: Boolean,
        overflow: PixelTextOverflow,
        maxLines: Int,
        defaultTextRasterizer: PixelTextRasterizer = this.defaultTextRasterizer,
    ) {
        if (
            this.text == text &&
            this.style == style &&
            this.textAlign == textAlign &&
            this.textDirection == textDirection &&
            this.softWrap == softWrap &&
            this.overflow == overflow &&
            this.maxLines == maxLines &&
            this.defaultTextRasterizer === defaultTextRasterizer
        ) {
            return
        }
        this.text = text
        this.style = style
        this.textAlign = textAlign
        this.textDirection = textDirection
        this.softWrap = softWrap
        this.overflow = overflow
        this.maxLines = maxLines
        this.defaultTextRasterizer = defaultTextRasterizer
        markNeedsLayout()
        markNeedsPaint()
    }

    /**
     * 按给定约束测量文本对象。
     */
    override fun layout(constraints: RenderConstraints) {
        rasterizer = style.textRasterizer ?: defaultTextRasterizer

        val horizontalPadding = paddingLeft + paddingRight
        val verticalPadding = paddingTop + paddingBottom
        val availableTextWidth = resolveAvailableTextWidth(
            constraints = constraints,
            horizontalPadding = horizontalPadding,
        )
        displayText = ParagraphLayoutSupport.resolvePlainTextLines(
            text = text,
            rasterizer = rasterizer,
            availableWidth = availableTextWidth,
            softWrap = softWrap,
            overflow = overflow,
            maxLines = maxLines,
        ).joinToString(separator = "\n")
        textWidth = rasterizer.measureText(displayText)
        textHeight = rasterizer.measureHeight(displayText.ifEmpty { " " })

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
        drawTextX = paddingLeft + ParagraphLayoutSupport.resolveLineStartX(
            textAlign = textAlign,
            textDirection = textDirection,
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
        if (displayText.isEmpty()) {
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
                text = displayText,
                x = destinationX,
                y = destinationY,
                value = style.tone.value,
            )
            return
        }

        val scratch = context.bufferPool.acquire(
            width = textWidth.coerceAtLeast(1),
            height = textHeight.coerceAtLeast(1),
        )
        try {
            rasterizer.drawText(
                buffer = scratch,
                text = displayText,
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
        } finally {
            context.bufferPool.release(scratch)
        }
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

    private fun resolveAvailableTextWidth(
        constraints: RenderConstraints,
        horizontalPadding: Int,
    ): Int {
        val measuredWidth = when {
            explicitWidth != null -> explicitWidth
            fillMaxWidth || occupyFullWidth -> constraints.maxWidth
            else -> constraints.maxWidth
        }
        return (constraints.constrainWidth(measuredWidth) - horizontalPadding).coerceAtLeast(0)
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
