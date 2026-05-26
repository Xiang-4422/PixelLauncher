package com.purride.pixelcore

/**
 * 像素文本栅格化接口。
 *
 * 这个接口把 pixel-engine UI layer 需要的最小文本能力收敛成统一协议：
 * 1. 测量文本宽度
 * 2. 测量文本高度
 * 3. 把文本绘制到像素缓冲
 *
 * 这样上层不需要关心底层到底是内置位图字体、真实字形包，还是其他字体实现。
 */
public interface PixelTextRasterizer {

    public fun measureText(text: String): Int

    public fun measureHeight(text: String): Int

    public fun fontMetrics(text: String = " "): PixelFontMetrics {
        val height = measureHeight(text.ifEmpty { " " }).coerceAtLeast(1)
        return PixelFontMetrics(
            cellHeight = height,
            baseline = height - 1,
            ascent = height - 1,
            descent = 1,
            inkTop = 0,
            inkBottom = height - 1,
        )
    }

    public fun drawText(
        buffer: PixelBuffer,
        text: String,
        x: Int,
        y: Int,
        color: PixelColor = PixelColor.fromRgb(255, 255, 255),
    )
}

public data class PixelFontMetrics(
    val cellHeight: Int,
    val baseline: Int,
    val ascent: Int,
    val descent: Int,
    val inkTop: Int,
    val inkBottom: Int,
)

/**
 * 带样式的文本栅格化适配器。
 *
 * 当上层已经有 `PixelFontEngine + GlyphStyle` 组合时，可以通过这个适配器暴露成
 * `PixelTextRasterizer`，从而直接接入 pixel-engine UI layer。
 */
public class PixelStyledTextRasterizer(
    private val engine: PixelFontEngine,
    private val style: GlyphStyle,
    private val lineSpacing: Int = 0,
) : PixelTextRasterizer {

    private val lineHeight: Int
        get() = style.cellHeight + lineSpacing

    override fun measureText(text: String): Int {
        val lines = text.lines()
        return lines.maxOfOrNull { line -> engine.measureText(line, style) } ?: 0
    }

    override fun measureHeight(text: String): Int {
        val lineCount = text.lines().size.coerceAtLeast(1)
        return (lineCount * style.cellHeight) + ((lineCount - 1) * lineSpacing)
    }

    override fun fontMetrics(text: String): PixelFontMetrics {
        return engine.fontMetrics(text = text, style = style)
    }

    override fun drawText(
        buffer: PixelBuffer,
        text: String,
        x: Int,
        y: Int,
        color: PixelColor,
    ) {
        var cursorY = y
        text.lines().forEach { line ->
            engine.drawText(
                buffer = buffer,
                text = line,
                startX = x,
                startY = cursorY,
                maxWidth = Int.MAX_VALUE,
                color = color,
                style = style,
            )
            cursorY += lineHeight
        }
    }
}
