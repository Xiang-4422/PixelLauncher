package com.purride.pixellauncherv2.launcher

import kotlin.math.abs

/** Launcher 可由设置页显式选择的字体家族。 */
enum class LauncherFontFamily(
    /** 设置页和诊断页展示的稳定名称。 */
    val displayLabel: String,
) {
    /** 当前覆盖最完整的 Fusion Pixel 字体。 */
    FUSION(displayLabel = "FUSION"),

    /** 独立绘制、字符覆盖仍在增长中的 Ark Pixel 字体。 */
    ARK(displayLabel = "ARK"),
}

/** 字体字符的水平宽度模式，与字体家族保持正交。 */
enum class LauncherFontWidthMode(
    /** 设置页使用的紧凑显示名称。 */
    val displayLabel: String,
    /** 字形包目录使用的稳定名称片段。 */
    val assetStyleName: String,
) {
    /** 字符按照自身墨迹和排版度量使用不同比例宽度。 */
    PROPORTIONAL(displayLabel = "PROP", assetStyleName = "proportional"),

    /** 使用字体官方的固定网格宽度；宽字符可占两个拉丁单元。 */
    MONOSPACED(displayLabel = "MONO", assetStyleName = "monospaced"),
}

/** 渲染层可明确选择的原生字体像素尺寸。 */
enum class PixelFontSize(
    /** 字体发布方标注的原生像素字号。 */
    val px: Int,
) {
    /** 8px 原生点阵字号。 */
    PX_8(8),

    /** 10px 原生点阵字号。 */
    PX_10(10),

    /** 12px 原生点阵字号。 */
    PX_12(12),

    /** 16px 原生点阵字号。 */
    PX_16(16),
}

/** 一次完整、无隐式字体家族回退的 Launcher 字体选择。 */
data class LauncherFontSelection(
    /** 被唯一查询的字体家族。 */
    val family: LauncherFontFamily,
    /** 当前字体家族的字符宽度模式。 */
    val widthMode: LauncherFontWidthMode,
    /** 未被 UI 文本样式覆盖时使用的默认字号。 */
    val size: PixelFontSize,
)

/** Launcher 布局估算使用的字体像素度量。 */
data class PixelFontMetrics(
    /** 度量所属的原生字号。 */
    val size: PixelFontSize,
    /** 字形单元高度。 */
    val cellHeight: Int,
    /** 从字形单元顶部计算的基线。 */
    val baseline: Int,
    /** 窄字符或缺失的 ASCII 字形使用的默认前进宽度。 */
    val narrowAdvanceWidth: Int,
    /** 宽字符使用的默认前进宽度。 */
    val wideAdvanceWidth: Int,
)

/** 集中声明字体能力矩阵、资源路径和排版度量。 */
object PixelFontCatalog {

    /** 设置缺失或旧版本升级时使用的默认字体选择。 */
    val defaultUiFontSelection: LauncherFontSelection = LauncherFontSelection(
        family = LauncherFontFamily.FUSION,
        widthMode = LauncherFontWidthMode.PROPORTIONAL,
        size = PixelFontSize.PX_10,
    )

    /** 兼容仍只需要默认字号的布局常量。 */
    val defaultUiFontSize: PixelFontSize = defaultUiFontSelection.size

    /** 返回设置页允许循环选择的字体家族。 */
    fun fontFamilyOptions(): List<LauncherFontFamily> = LauncherFontFamily.entries

    /** 返回指定字体家族真实支持的宽度模式。 */
    fun widthModeOptions(family: LauncherFontFamily): List<LauncherFontWidthMode> {
        return supportedSelections
            .asSequence()
            .filter { selection -> selection.family == family }
            .map { selection -> selection.widthMode }
            .distinct()
            .toList()
    }

    /** 返回指定字体家族和宽度模式真实支持的字号。 */
    fun fontSizeOptions(
        family: LauncherFontFamily,
        widthMode: LauncherFontWidthMode,
    ): List<PixelFontSize> {
        return supportedSelections
            .asSequence()
            .filter { selection -> selection.family == family && selection.widthMode == widthMode }
            .map { selection -> selection.size }
            .distinct()
            .toList()
    }

    /** 返回诊断页展示的全部基础字号。 */
    fun fontSizeOptions(): List<PixelFontSize> = PixelFontSize.entries

    /** 判断一个三维字体组合是否存在对应的单字体资源。 */
    fun supports(selection: LauncherFontSelection): Boolean = selection in supportedSelections

    /** 把旧设置或不受支持的组合收敛到同一家族的有效选择。 */
    fun normalize(selection: LauncherFontSelection): LauncherFontSelection {
        if (supports(selection)) return selection
        /** 同一家族全部有效组合，声明顺序即稳定回退顺序。 */
        val familySelections = supportedSelections.filter { candidate -> candidate.family == selection.family }
        return familySelections
            .filter { candidate -> candidate.widthMode == selection.widthMode }
            .minByOrNull { candidate -> abs(candidate.size.px - selection.size.px) }
            ?: familySelections.minByOrNull { candidate -> abs(candidate.size.px - selection.size.px) }
            ?: defaultUiFontSelection
    }

    /** 返回一个选择严格属于当前字体家族的字形包目录，不追加其他家族。 */
    fun assetDirectories(selection: LauncherFontSelection): List<String> {
        val normalized = normalize(selection)
        return when (normalized.family) {
            LauncherFontFamily.FUSION -> listOf(
                "glyphpacks/fusion_pixel_${normalized.size.px}px_${normalized.widthMode.assetStyleName}_latin",
                "glyphpacks/fusion_pixel_${normalized.size.px}px_${normalized.widthMode.assetStyleName}_zh_hans",
            )
            LauncherFontFamily.ARK -> listOf(
                "glyphpacks/ark_pixel_${normalized.size.px}px_${normalized.widthMode.assetStyleName}_zh_cn",
            )
        }
    }

    /** 返回字体家族的设置页显示名称。 */
    fun familyLabel(family: LauncherFontFamily): String = family.displayLabel

    /** 返回宽度模式的设置页显示名称。 */
    fun widthModeLabel(widthMode: LauncherFontWidthMode): String = widthMode.displayLabel

    /** 返回字号的设置页显示名称。 */
    fun sizeLabel(size: PixelFontSize): String = "${size.px}PX"

    /** 返回指定原生字号的基础布局度量。 */
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
            PixelFontSize.PX_16 -> PixelFontMetrics(
                size = size,
                cellHeight = 16,
                baseline = 15,
                narrowAdvanceWidth = 8,
                wideAdvanceWidth = 16,
            )
        }
    }

    /** 返回完整字体选择对应的真实字形包度量。 */
    fun metrics(selection: LauncherFontSelection): PixelFontMetrics {
        /** 防止无效持久化组合进入度量分支。 */
        val normalized = normalize(selection)
        /** 先取得同字号 Fusion 比例模式使用的基础度量。 */
        val base = metrics(normalized.size)
        return when (normalized.family) {
            LauncherFontFamily.FUSION -> when (normalized.widthMode) {
                LauncherFontWidthMode.PROPORTIONAL -> base
                LauncherFontWidthMode.MONOSPACED -> base.copy(
                    baseline = if (normalized.size == PixelFontSize.PX_12) 10 else base.baseline,
                    narrowAdvanceWidth = base.wideAdvanceWidth,
                )
            }
            LauncherFontFamily.ARK -> arkMetrics(normalized)
        }
    }

    /** 返回 Ark 官方 BDF 变体的原生基线与前进宽度。 */
    private fun arkMetrics(selection: LauncherFontSelection): PixelFontMetrics {
        return when (selection.size) {
            PixelFontSize.PX_8 -> error("Ark Pixel does not provide an 8px pack")
            PixelFontSize.PX_10 -> PixelFontMetrics(
                size = selection.size,
                cellHeight = 10,
                baseline = if (selection.widthMode == LauncherFontWidthMode.PROPORTIONAL) 8 else 9,
                narrowAdvanceWidth = 5,
                wideAdvanceWidth = 10,
            )
            PixelFontSize.PX_12 -> PixelFontMetrics(
                size = selection.size,
                cellHeight = 12,
                baseline = 10,
                narrowAdvanceWidth = 6,
                wideAdvanceWidth = 12,
            )
            PixelFontSize.PX_16 -> PixelFontMetrics(
                size = selection.size,
                cellHeight = 16,
                baseline = 13,
                narrowAdvanceWidth = if (selection.widthMode == LauncherFontWidthMode.PROPORTIONAL) 7 else 8,
                wideAdvanceWidth = 16,
            )
        }
    }

    /** 返回诊断页使用的紧凑字体度量文本。 */
    fun metricsLabel(size: PixelFontSize): String {
        val metrics = metrics(size)
        return "C${metrics.cellHeight} B${metrics.baseline} A${metrics.narrowAdvanceWidth}/${metrics.wideAdvanceWidth}"
    }

    /** 返回完整字体选择对应的紧凑真实度量文本。 */
    fun metricsLabel(selection: LauncherFontSelection): String {
        val metrics = metrics(selection)
        return "C${metrics.cellHeight} B${metrics.baseline} A${metrics.narrowAdvanceWidth}/${metrics.wideAdvanceWidth}"
    }

    /** 按完整字体选择的宽度模式估算文本宽度。 */
    fun estimatedTextWidth(
        text: String,
        selection: LauncherFontSelection = defaultUiFontSelection,
        size: PixelFontSize = selection.size,
    ): Int {
        /** 组件覆盖字号后，同字体与模式对应的真实度量。 */
        val metrics = metrics(normalize(selection.copy(size = size)))
        return text.sumOf { char ->
            if (char.code <= ASCII_MAX_CODE_POINT) metrics.narrowAdvanceWidth else metrics.wideAdvanceWidth
        }
    }

    /** 所有能被设置页选中且具备实际资源的组合。 */
    private val supportedSelections: List<LauncherFontSelection> = buildList {
        LauncherFontWidthMode.entries.forEach { widthMode ->
            PixelFontSize.entries.filter { size -> size != PixelFontSize.PX_16 }.forEach { size ->
                add(LauncherFontSelection(LauncherFontFamily.FUSION, widthMode, size))
            }
        }
        LauncherFontWidthMode.entries.forEach { widthMode ->
            PixelFontSize.entries.filter { size -> size != PixelFontSize.PX_8 }.forEach { size ->
                add(LauncherFontSelection(LauncherFontFamily.ARK, widthMode, size))
            }
        }
    }

    /** ASCII 可打印宽度估算使用的最大码点。 */
    private const val ASCII_MAX_CODE_POINT = 0x7F
}
