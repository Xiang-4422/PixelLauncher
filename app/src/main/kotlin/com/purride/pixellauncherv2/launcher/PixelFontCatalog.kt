package com.purride.pixellauncherv2.launcher

/** Launcher 可由设置页显式选择的内置字体家族。 */
enum class LauncherFontFamily(
    /** 设置页和诊断页展示的稳定名称。 */
    val displayLabel: String,
    /** 对应 assets 字形包目录中的样式片段。 */
    val assetStyleName: String,
) {
    /** Fusion Pixel 比例宽度版本，保持现有界面的默认排版。 */
    FUSION_PROPORTIONAL(displayLabel = "FUSION", assetStyleName = "proportional"),

    /** Fusion Pixel 等宽版本，适合强调终端和点阵网格感。 */
    FUSION_MONOSPACED(displayLabel = "MONO", assetStyleName = "monospaced"),
}

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

    /** 设置缺失或旧版本升级时使用的默认字体家族。 */
    val defaultUiFontFamily: LauncherFontFamily = LauncherFontFamily.FUSION_PROPORTIONAL

    /** Launcher 正文统一使用的默认像素字号。 */
    val defaultUiFontSize: PixelFontSize = PixelFontSize.PX_10

    /** 返回设置页允许循环选择的字体家族。 */
    fun fontFamilyOptions(): List<LauncherFontFamily> = LauncherFontFamily.entries

    /** 返回字体家族的设置页显示名称。 */
    fun familyLabel(family: LauncherFontFamily): String = family.displayLabel

    /** 返回诊断页展示的固定字号选项。 */
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

    /** 按字体家族的实际宽度模式估算文本宽度。 */
    fun estimatedTextWidth(
        text: String,
        family: LauncherFontFamily = defaultUiFontFamily,
        size: PixelFontSize = defaultUiFontSize,
    ): Int {
        val metrics = metrics(size)
        /** 等宽字体中的拉丁字符与宽字符使用相同前进宽度。 */
        val narrowAdvanceWidth = when (family) {
            LauncherFontFamily.FUSION_PROPORTIONAL -> metrics.narrowAdvanceWidth
            LauncherFontFamily.FUSION_MONOSPACED -> metrics.wideAdvanceWidth
        }
        return text.sumOf { char ->
            if (char.code <= ASCII_MAX_CODE_POINT) {
                narrowAdvanceWidth
            } else {
                metrics.wideAdvanceWidth
            }
        }
    }

    private const val ASCII_MAX_CODE_POINT = 0x7F
}
