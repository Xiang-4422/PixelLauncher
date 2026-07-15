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

    /** 执行 `PixelTextRasterizer` 的 `measureText` 渲染或命中阶段。
 *
 * Measures the horizontal pixel advance of [text].
 */
    public fun measureText(text: String): Int

    /** 执行 `PixelTextRasterizer` 的 `measureHeight` 渲染或命中阶段。
 *
 * Measures the vertical pixel extent of [text], including explicit line breaks.
 */
    public fun measureHeight(text: String): Int

    /** 执行 `PixelTextRasterizer` 的 `fontMetrics` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns baseline and ink metrics for [text], using a stable non-empty fallback sample.
 */
    public fun fontMetrics(text: String = " "): PixelFontMetrics {
        /** Positive fallback cell height derived from the implementation's own measurement. */
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

    /** 执行 `PixelTextRasterizer` 的 `drawText` 渲染或命中阶段。
 *
 * Draws [text] at the supplied pixel origin without changing the backing String.
 */
    public fun drawText(
        buffer: PixelBuffer,
        text: String,
        x: Int,
        y: Int,
        color: PixelColor = PixelColor.fromRgb(255, 255, 255),
    )
}

/** 判断文本是否包含 Kotlin `lines()` 识别的显式 CR/LF 换行符。 */
internal fun String.hasExplicitCrLfLineBreak(): Boolean {
    return indexOf('\n') >= 0 || indexOf('\r') >= 0
}

/**
 * 定义 `PixelClusterTextRasterizer` 在 `PixelTextRasterizer` 中的可替换调用契约。
 *
 * Additive contract for rasterizers that can draw an entire extended grapheme as one glyph unit.
 *
 * Paragraph layout always calls [PixelTextRasterizer.measureText] and
 * [PixelTextRasterizer.drawText]. When this capability returns `true`, those methods receive the
 * exact multi-code-point cluster. When it returns `false`, the paragraph substitutes one
 * deterministic U+FFFD fallback; default-ignorable-only clusters remain zero-width and unpainted.
 * Existing rasterizers need not implement this interface and continue to support single-code-point
 * clusters, including supplementary scalar values.
 */
public interface PixelClusterTextRasterizer : PixelTextRasterizer {
    /** 判断 `PixelTextRasterizer` 是否满足 `canRasterizeCluster` 条件，不修改现有状态。
 *
 * Returns whether [cluster] is rendered atomically without separate code-point fallbacks.
 */
    public fun canRasterizeCluster(cluster: String): Boolean
}

/** 定义 `PixelFontMetrics` 在 `PixelTextRasterizer` 中承担的数据与行为边界。
 *
 * Immutable vertical metrics shared by paragraph measurement, caret, and selection geometry.
 */
public data class PixelFontMetrics(
    /** Full font cell height in logical pixels. */
    val cellHeight: Int,
    /** Baseline row measured from the top of the cell. */
    val baseline: Int,
    /** Logical ascent above the baseline. */
    val ascent: Int,
    /** Logical descent below the baseline. */
    val descent: Int,
    /** First row containing visible ink. */
    val inkTop: Int,
    /** Last row containing visible ink. */
    val inkBottom: Int,
)

/**
 * 带样式的文本栅格化适配器。
 *
 * 当上层已经有 `PixelFontEngine + GlyphStyle` 组合时，可以通过这个适配器暴露成
 * `PixelTextRasterizer`，从而直接接入 pixel-engine UI layer。
 */
public class PixelStyledTextRasterizer(
    /** Code-point-aware engine supplying glyph metrics and pixels. */
    private val engine: PixelFontEngine,
    /** Immutable glyph style used by every measurement and draw. */
    private val style: GlyphStyle,
    /** Extra pixels inserted after every explicit line except the final line. */
    private val lineSpacing: Int = 0,
) : PixelClusterTextRasterizer {

    /** Physical height occupied by one non-final line. */
    private val lineHeight: Int
        get() = style.cellHeight + lineSpacing

    /** Measures the widest explicit line using code-point-aware glyph lookup. */
    override fun measureText(text: String): Int {
        if (!text.hasExplicitCrLfLineBreak()) {
            return engine.measureText(text, style)
        }
        /** 需要分别测量的显式源文本行。 */
        val lines = text.lines()
        return lines.maxOfOrNull { line -> engine.measureText(line, style) } ?: 0
    }

    /** 连续测量两个无硬换行片段，保持 wrapped engine 的字形 pair 语义。 */
    internal fun measureAdjacentText(first: String, second: String): Int {
        return engine.measureAdjacentText(first = first, second = second, style = style)
    }

    /** Measures all explicit lines and inter-line spacing. */
    override fun measureHeight(text: String): Int {
        if (!text.hasExplicitCrLfLineBreak()) {
            return style.cellHeight
        }
        /** 即使样本文本为空也至少保留一行。 */
        val lineCount = text.lines().size.coerceAtLeast(1)
        return (lineCount * style.cellHeight) + ((lineCount - 1) * lineSpacing)
    }

    /** Delegates scalar-aware baseline and ink inspection to the wrapped engine. */
    override fun fontMetrics(text: String): PixelFontMetrics {
        return engine.fontMetrics(text = text, style = style)
    }

    /**
     * Declares only single-scalar clusters as atomic because [PixelFontEngine] currently resolves
     * one glyph record per code point; consumer rasterizers may advertise richer cluster support.
     */
    override fun canRasterizeCluster(cluster: String): Boolean {
        return cluster.isNotEmpty() && Character.codePointCount(cluster, 0, cluster.length) == 1
    }

    /** Draws every explicit line through the code-point-aware engine. */
    override fun drawText(
        buffer: PixelBuffer,
        text: String,
        x: Int,
        y: Int,
        color: PixelColor,
    ) {
        if (!text.hasExplicitCrLfLineBreak()) {
            engine.drawText(
                buffer = buffer,
                text = text,
                startX = x,
                startY = y,
                maxWidth = Int.MAX_VALUE,
                color = color,
                style = style,
            )
            return
        }
        /** 当前显式文本行的纵向绘制原点。 */
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
