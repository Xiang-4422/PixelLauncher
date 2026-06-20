package com.purride.pixellauncherv2.launcher

/** 渲染层使用的固定字体像素尺寸。UI 组件按需直接引用，不再通过全局设置项控制。 */
enum class PixelFontSize(val px: Int) {
    PX_8(8),
    PX_10(10),
    PX_12(12),
}

data class PixelFontMetrics(
    val size: PixelFontSize,
    val cellHeight: Int,
    val baseline: Int,
    val narrowAdvanceWidth: Int,
    val wideAdvanceWidth: Int,
)

object PixelFontCatalog {

    val defaultUiFontSize: PixelFontSize = PixelFontSize.PX_10

    fun fontSizeOptions(): List<PixelFontSize> = PixelFontSize.entries

    fun sizeLabel(size: PixelFontSize): String = "${size.px}PX"

    fun metrics(size: PixelFontSize): PixelFontMetrics {
        return when (size) {
            PixelFontSize.PX_8 -> PixelFontMetrics(
                size = size,
                cellHeight = 8,
                baseline = 7,
                narrowAdvanceWidth = 4,
                wideAdvanceWidth = 8,
            )
            PixelFontSize.PX_10 -> PixelFontMetrics(
                size = size,
                cellHeight = 10,
                baseline = 9,
                narrowAdvanceWidth = 6,
                wideAdvanceWidth = 10,
            )
            PixelFontSize.PX_12 -> PixelFontMetrics(
                size = size,
                cellHeight = 12,
                baseline = 11,
                narrowAdvanceWidth = 8,
                wideAdvanceWidth = 12,
            )
        }
    }

    fun metricsLabel(size: PixelFontSize): String {
        val metrics = metrics(size)
        return "C${metrics.cellHeight} B${metrics.baseline} A${metrics.narrowAdvanceWidth}/${metrics.wideAdvanceWidth}"
    }

    fun estimatedTextWidth(text: String, size: PixelFontSize = defaultUiFontSize): Int {
        val metrics = metrics(size)
        return text.sumOf { char ->
            if (char.code <= ASCII_MAX_CODE_POINT) {
                metrics.narrowAdvanceWidth
            } else {
                metrics.wideAdvanceWidth
            }
        }
    }

    private const val ASCII_MAX_CODE_POINT = 0x7F
}
