package com.purride.pixellauncherv2.launcher

/** 跨版本持久化使用的稳定字体家族 ID。 */
@JvmInline
value class LauncherFontFamily(
    /** `fonts/font_catalog.json` 中声明的稳定小写 ID。 */
    val id: String,
) {
    init {
        require(id.isNotBlank()) { "font family id must not be blank" }
    }

    /** 兼容旧设置迁移使用的历史枚举名称。 */
    val name: String
        get() = PixelFontCatalog.familyDescriptor(this)?.constantName ?: id.uppercase()

    /** 设置页和诊断页展示名称。 */
    val displayLabel: String
        get() = PixelFontCatalog.familyDescriptor(this)?.displayLabel ?: id.uppercase()

    /** 单包资源使用的稳定前缀。 */
    val assetFamilyId: String
        get() = PixelFontCatalog.familyDescriptor(this)?.assetFamilyId ?: id

    /** 提供现有调用点可读的稳定家族常量。 */
    companion object {
        val FUSION: LauncherFontFamily = LauncherFontFamily("fusion")
        val ARK: LauncherFontFamily = LauncherFontFamily("ark")
        val CUBIC_11: LauncherFontFamily = LauncherFontFamily("cubic_11")
        val BOUTIQUE_7: LauncherFontFamily = LauncherFontFamily("boutique_7")
        val BOUTIQUE_9: LauncherFontFamily = LauncherFontFamily("boutique_9")
        /** 直接从原始 13 格点阵轮廓恢复的一像素 Dotted 家族。 */
        val DOTTED: LauncherFontFamily = LauncherFontFamily("dotted")
        val GNU_UNIFONT: LauncherFontFamily = LauncherFontFamily("gnu_unifont")
        val PIX32: LauncherFontFamily = LauncherFontFamily("pix32")

        /** 按 catalog 顺序返回全部内置字体家族。 */
        val entries: List<LauncherFontFamily>
            get() = PixelFontCatalog.fontFamilyOptions()
    }
}

/** 对外强调家族值是稳定 ID，而不是可持久化枚举名称。 */
typealias FontFamilyId = LauncherFontFamily

/** 字体字符的水平宽度模式，与字体家族保持正交。 */
enum class LauncherFontWidthMode(
    /** 设置页使用的紧凑显示名称。 */
    val displayLabel: String,
    /** catalog 和资源目录使用的稳定 ID。 */
    val assetStyleName: String,
) {
    /** 字符使用字体自身的比例前进宽度。 */
    PROPORTIONAL(displayLabel = "PROP", assetStyleName = "proportional"),

    /** 字体官方固定网格宽度模式。 */
    MONOSPACED(displayLabel = "MONO", assetStyleName = "monospaced"),
}

/** 可由 catalog 任意扩展的正整数像素字号。 */
@JvmInline
value class PixelFontSize(
    /** 字体发布方标注的原生像素字号。 */
    val px: Int,
) : Comparable<PixelFontSize> {
    init {
        require(px > 0) { "font size must be > 0" }
    }

    /** 兼容旧版枚举名称的迁移文本。 */
    val name: String
        get() = "PX_$px"

    /** 按像素值排序字号。 */
    override fun compareTo(other: PixelFontSize): Int = px.compareTo(other.px)

    /** 保留现有组件可读的常用字号常量。 */
    companion object {
        val PX_7 = PixelFontSize(7)
        val PX_8 = PixelFontSize(8)
        val PX_9 = PixelFontSize(9)
        val PX_10 = PixelFontSize(10)
        val PX_11 = PixelFontSize(11)
        val PX_12 = PixelFontSize(12)
        val PX_13 = PixelFontSize(13)
        val PX_16 = PixelFontSize(16)

        /** 返回 catalog 实际声明的全部字号。 */
        val entries: List<PixelFontSize>
            get() = PixelFontCatalog.fontSizeOptions()
    }
}

/** Launcher 组件可声明的字体资源角色。 */
enum class LauncherTextRole {
    /** 继承用户当前选择的原生字号。 */
    DEFAULT,

    /** 状态栏和紧凑控件使用的同家族原生 face。 */
    CHROME,
}

/** 精确标识一个字体家族、宽度模式和字号。 */
data class FontFaceKey(
    /** 字体家族稳定 ID。 */
    val family: LauncherFontFamily,
    /** 字符宽度模式。 */
    val widthMode: LauncherFontWidthMode,
    /** 标称像素字号。 */
    val size: PixelFontSize,
)

/** 一次完整、无跨家族回退的 Launcher 字体选择。 */
data class LauncherFontSelection(
    /** 被唯一查询的字体家族。 */
    val family: LauncherFontFamily,
    /** 当前字体家族的字符宽度模式。 */
    val widthMode: LauncherFontWidthMode,
    /** 默认文本字号。 */
    val size: PixelFontSize,
) {
    /** 转换为 catalog 精确 face key。 */
    fun toFaceKey(): FontFaceKey = FontFaceKey(family, widthMode, size)
}

/** Launcher 布局和缺字单元使用的字体像素度量。 */
data class PixelFontMetrics(
    /** 度量所属标称字号。 */
    val size: PixelFontSize,
    /** 字形单元高度。 */
    val cellHeight: Int,
    /** 从字形单元顶部计算的基线。 */
    val baseline: Int,
    /** 缺失 ASCII 字形使用的默认前进宽度。 */
    val narrowAdvanceWidth: Int,
    /** 缺失宽字符使用的默认前进宽度。 */
    val wideAdvanceWidth: Int,
)

/** 一个生成字形包的资源与来源描述。 */
data class FontPackDescriptor(
    /** manifest 和目录共同使用的稳定 pack ID。 */
    val id: String,
    /** Android assets 相对目录。 */
    val assetDirectory: String,
    /** ttf、otf 或 bdf 源类型。 */
    val sourceType: String,
    /** 仓库根目录下的字体源路径。 */
    val sourcePath: String,
    /** 字体源 SHA-256。 */
    val sourceSha256: String,
    /** catalog 中引用的 Unicode 范围集合。 */
    val rangeSet: String,
    /** 当前 pack 声明的 Unicode 覆盖范围。 */
    val coverageRanges: List<String>,
    /** manifest 在缺少独立记录时使用的默认 advance。 */
    val defaultAdvance: Int,
)

/** 一个精确可渲染 face 的能力、度量和资源。 */
data class FontFaceDescriptor(
    /** face 唯一键。 */
    val key: FontFaceKey,
    /** 是否允许在设置页作为默认字号选择。 */
    val settingsVisible: Boolean,
    /** 该 face 承担的组件角色。 */
    val roles: Set<LauncherTextRole>,
    /** 字形单元真实度量。 */
    val metrics: PixelFontMetrics,
    /** 严格属于同一家族的有序资源包。 */
    val packs: List<FontPackDescriptor>,
)

/** 一个字体家族的展示、来源、默认值和全部精确 face。 */
data class FontFamilyDescriptor(
    /** 字体家族稳定 ID。 */
    val id: LauncherFontFamily,
    /** 旧枚举兼容和诊断使用的常量名称。 */
    val constantName: String,
    /** 设置页显示名称。 */
    val displayLabel: String,
    /** 单包目录前缀。 */
    val assetFamilyId: String,
    /** 内置源版本。 */
    val sourceVersion: String,
    /** 授权类型或用户声明。 */
    val licenseId: String,
    /** 无历史设置时使用的默认 face。 */
    val defaultKey: FontFaceKey,
    /** 当前家族所有设置和组件 face。 */
    val faces: List<FontFaceDescriptor>,
)

/** 从生成目录提供精确字体能力、资源和排版度量。 */
object PixelFontCatalog {
    /** 生成目录的家族顺序索引。 */
    private val families: List<FontFamilyDescriptor>
        get() = GeneratedPixelFontCatalog.families

    /** 全部 face 的精确键索引。 */
    private val facesByKey: Map<FontFaceKey, FontFaceDescriptor> by lazy {
        families.flatMap(FontFamilyDescriptor::faces).associateBy(FontFaceDescriptor::key)
    }

    /** 设置缺失或旧版本升级时使用的默认字体选择。 */
    val defaultUiFontSelection: LauncherFontSelection
        get() = selectionOf(requireNotNull(familyDescriptor(LauncherFontFamily.FUSION)).defaultKey)

    /** 兼容只需要默认字号的布局入口。 */
    val defaultUiFontSize: PixelFontSize
        get() = defaultUiFontSelection.size

    /** 返回设置页字体家族顺序。 */
    fun fontFamilyOptions(): List<LauncherFontFamily> = families.map(FontFamilyDescriptor::id)

    /** 返回指定家族描述，不存在时返回 null。 */
    fun familyDescriptor(family: LauncherFontFamily): FontFamilyDescriptor? =
        families.firstOrNull { descriptor -> descriptor.id == family }

    /** 返回指定家族真实支持的设置宽度模式。 */
    fun widthModeOptions(family: LauncherFontFamily): List<LauncherFontWidthMode> =
        familyDescriptor(family)?.faces
            .orEmpty()
            .asSequence()
            .filter(FontFaceDescriptor::settingsVisible)
            .map { face -> face.key.widthMode }
            .distinct()
            .toList()

    /** 返回指定家族与宽度模式可在设置页选择的字号。 */
    fun fontSizeOptions(
        family: LauncherFontFamily,
        widthMode: LauncherFontWidthMode,
    ): List<PixelFontSize> = familyDescriptor(family)?.faces
        .orEmpty()
        .asSequence()
        .filter { face -> face.settingsVisible && face.key.widthMode == widthMode }
        .map { face -> face.key.size }
        .sorted()
        .toList()

    /** 返回 catalog 声明的全部不重复字号。 */
    fun fontSizeOptions(): List<PixelFontSize> = families
        .flatMap(FontFamilyDescriptor::faces)
        .map { face -> face.key.size }
        .distinct()
        .sorted()

    /** 返回全部设置和组件可渲染组合。 */
    fun renderableSelections(): List<LauncherFontSelection> = families
        .flatMap(FontFamilyDescriptor::faces)
        .map { face -> selectionOf(face.key) }

    /** 判断一个选择是否允许在设置页持久化。 */
    fun supports(selection: LauncherFontSelection): Boolean =
        findFace(selection)?.settingsVisible == true

    /** 精确查找可渲染 face，不执行近似字号解析。 */
    fun findFace(selection: LauncherFontSelection): FontFaceDescriptor? = facesByKey[selection.toFaceKey()]

    /** 精确返回可渲染 face；缺失代表 catalog 或调用点错误。 */
    fun requireFace(selection: LauncherFontSelection): FontFaceDescriptor = requireNotNull(findFace(selection)) {
        "Missing exact font face: ${selection.family.id}/${selection.widthMode.assetStyleName}/${selection.size.px}px"
    }

    /** 将未知设置恢复到同家族默认 face，未知家族恢复到全局默认。 */
    fun normalize(selection: LauncherFontSelection): LauncherFontSelection {
        if (supports(selection)) return selection
        val family = familyDescriptor(selection.family)
            ?: familyDescriptor(LauncherFontFamily.FUSION)
            ?: error("Generated font catalog must contain Fusion")
        return selectionOf(family.defaultKey)
    }

    /** 兼容旧调用名称，但只允许精确资源，不再寻找最近字号。 */
    fun resolveRenderable(selection: LauncherFontSelection): LauncherFontSelection =
        selection.also(::requireFace)

    /** 返回一个 face 严格属于当前家族的有序 asset 目录。 */
    fun assetDirectories(selection: LauncherFontSelection): List<String> =
        requireFace(selection).packs.map(FontPackDescriptor::assetDirectory)

    /** 返回字体家族设置页名称。 */
    fun familyLabel(family: LauncherFontFamily): String =
        familyDescriptor(family)?.displayLabel ?: family.id.uppercase()

    /** 返回宽度模式设置页名称。 */
    fun widthModeLabel(widthMode: LauncherFontWidthMode): String = widthMode.displayLabel

    /** 返回标称字号设置页名称。 */
    fun sizeLabel(size: PixelFontSize): String = "${size.px}PX"

    /** 返回完整选择的真实字形包度量。 */
    fun metrics(selection: LauncherFontSelection): PixelFontMetrics = requireFace(selection).metrics

    /** 返回完整选择的紧凑真实度量文本。 */
    fun metricsLabel(selection: LauncherFontSelection): String {
        val metrics = metrics(selection)
        return "C${metrics.cellHeight} B${metrics.baseline} A${metrics.narrowAdvanceWidth}/${metrics.wideAdvanceWidth}"
    }

    /** 返回设置页使用的原生字号、行框、基线和生成源类型。 */
    fun fontInfoLabel(selection: LauncherFontSelection): String {
        /** 当前设置精确对应的 catalog face。 */
        val face = requireFace(selection)
        /** 去重后的紧凑生产源类型标签。 */
        val sourceTypes = face.packs
            .map { pack -> pack.sourceType.uppercase().replace('_', '-') }
            .distinct()
            .joinToString("+")
        return "${sizeLabel(selection.size)} C${face.metrics.cellHeight} B${face.metrics.baseline} $sourceTypes"
    }

    /** 按精确 face 的缺字 advance 估算诊断文本宽度。 */
    fun estimatedTextWidth(
        text: String,
        selection: LauncherFontSelection = defaultUiFontSelection,
        size: PixelFontSize = selection.size,
    ): Int {
        val metrics = metrics(selection.copy(size = size))
        return text.sumOf { char ->
            if (char.code <= ASCII_MAX_CODE_POINT) metrics.narrowAdvanceWidth else metrics.wideAdvanceWidth
        }
    }

    /** 返回家族指定宽度模式承担某个组件角色的精确选择。 */
    fun selectionForRole(
        family: LauncherFontFamily,
        widthMode: LauncherFontWidthMode,
        role: LauncherTextRole,
    ): LauncherFontSelection {
        val face = familyDescriptor(family)?.faces?.singleOrNull { candidate ->
            candidate.key.widthMode == widthMode && role in candidate.roles
        }
        return selectionOf(requireNotNull(face) { "Missing $role face for ${family.id}/${widthMode.assetStyleName}" }.key)
    }

    /** 把内部 face key 转成公开选择值。 */
    private fun selectionOf(key: FontFaceKey): LauncherFontSelection = LauncherFontSelection(
        family = key.family,
        widthMode = key.widthMode,
        size = key.size,
    )

    /** ASCII 宽度估算使用的最大码点。 */
    private const val ASCII_MAX_CODE_POINT = 0x7F
}
