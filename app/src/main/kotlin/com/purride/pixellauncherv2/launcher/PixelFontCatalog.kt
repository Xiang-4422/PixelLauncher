package com.purride.pixellauncherv2.launcher

import com.purride.pixeldesign.font.ProductFontCatalog

/** Launcher 业务层保留的字体家族名称，底层类型由共享产品字体模块提供。 */
typealias LauncherFontFamily = com.purride.pixeldesign.font.ProductFontFamily

/** Launcher 业务层保留的字体宽度模式名称。 */
typealias LauncherFontWidthMode = com.purride.pixeldesign.font.ProductFontWidthMode

/** Launcher 业务层保留的字体字号名称。 */
typealias PixelFontSize = com.purride.pixeldesign.font.ProductFontSize

/** Launcher 业务层保留的文本角色名称。 */
typealias LauncherTextRole = com.purride.pixeldesign.font.ProductTextRole

/** Launcher 业务层保留的字体 face 键名称。 */
typealias FontFaceKey = com.purride.pixeldesign.font.ProductFontFaceKey

/** Launcher 业务层保留的完整字体选择名称。 */
typealias LauncherFontSelection = com.purride.pixeldesign.font.ProductFontSelection

/** Launcher 业务层保留的字体度量名称。 */
typealias PixelFontMetrics = com.purride.pixeldesign.font.ProductFontMetrics

/** Launcher 业务层保留的字体包描述名称。 */
typealias FontPackDescriptor = com.purride.pixeldesign.font.ProductFontPackDescriptor

/** Launcher 业务层保留的字体 face 描述名称。 */
typealias FontFaceDescriptor = com.purride.pixeldesign.font.ProductFontFaceDescriptor

/** Launcher 业务层保留的字体家族描述名称。 */
typealias FontFamilyDescriptor = com.purride.pixeldesign.font.ProductFontFamilyDescriptor

/** Launcher 业务代码继续使用的共享产品字体目录别名。 */
internal object PixelFontCatalog {
    /** 设置缺失时使用的共享默认字体。 */
    val defaultUiFontSelection: LauncherFontSelection
        get() = ProductFontCatalog.defaultUiFontSelection

    /** 兼容只需要默认字号的布局入口。 */
    val defaultUiFontSize: PixelFontSize
        get() = ProductFontCatalog.defaultUiFontSize

    /** 返回全部设置字体家族。 */
    fun fontFamilyOptions(): List<LauncherFontFamily> = ProductFontCatalog.fontFamilyOptions()

    /** 返回字体家族描述。 */
    fun familyDescriptor(family: LauncherFontFamily): FontFamilyDescriptor? =
        ProductFontCatalog.familyDescriptor(family)

    /** 返回指定家族支持的宽度模式。 */
    fun widthModeOptions(family: LauncherFontFamily): List<LauncherFontWidthMode> =
        ProductFontCatalog.widthModeOptions(family)

    /** 返回指定家族和宽度模式支持的字号。 */
    fun fontSizeOptions(
        family: LauncherFontFamily,
        widthMode: LauncherFontWidthMode,
    ): List<PixelFontSize> = ProductFontCatalog.fontSizeOptions(family, widthMode)

    /** 返回目录中的全部字号。 */
    fun fontSizeOptions(): List<PixelFontSize> = ProductFontCatalog.fontSizeOptions()

    /** 返回全部可渲染字体选择。 */
    fun renderableSelections(): List<LauncherFontSelection> = ProductFontCatalog.renderableSelections()

    /** 判断当前选择是否允许持久化。 */
    fun supports(selection: LauncherFontSelection): Boolean = ProductFontCatalog.supports(selection)

    /** 查找精确字体 face。 */
    fun findFace(selection: LauncherFontSelection): FontFaceDescriptor? =
        ProductFontCatalog.findFace(selection)

    /** 返回精确字体 face。 */
    fun requireFace(selection: LauncherFontSelection): FontFaceDescriptor =
        ProductFontCatalog.requireFace(selection)

    /** 把外部选择归一化到共享目录。 */
    fun normalize(selection: LauncherFontSelection): LauncherFontSelection =
        ProductFontCatalog.normalize(selection)

    /** 验证并返回精确可渲染选择。 */
    fun resolveRenderable(selection: LauncherFontSelection): LauncherFontSelection =
        ProductFontCatalog.resolveRenderable(selection)

    /** 返回当前选择的全部共享 asset 目录。 */
    fun assetDirectories(selection: LauncherFontSelection): List<String> =
        ProductFontCatalog.assetDirectories(selection)

    /** 返回字体家族展示名称。 */
    fun familyLabel(family: LauncherFontFamily): String = ProductFontCatalog.familyLabel(family)

    /** 返回宽度模式展示名称。 */
    fun widthModeLabel(widthMode: LauncherFontWidthMode): String =
        ProductFontCatalog.widthModeLabel(widthMode)

    /** 返回字号展示名称。 */
    fun sizeLabel(size: PixelFontSize): String = ProductFontCatalog.sizeLabel(size)

    /** 返回字体选择真实度量。 */
    fun metrics(selection: LauncherFontSelection): PixelFontMetrics = ProductFontCatalog.metrics(selection)

    /** 返回字体选择紧凑度量文本。 */
    fun metricsLabel(selection: LauncherFontSelection): String = ProductFontCatalog.metricsLabel(selection)

    /** 返回设置页字体说明。 */
    fun fontInfoLabel(selection: LauncherFontSelection): String = ProductFontCatalog.fontInfoLabel(selection)

    /** 估算文本逻辑宽度。 */
    fun estimatedTextWidth(
        text: String,
        selection: LauncherFontSelection = defaultUiFontSelection,
        size: PixelFontSize = selection.size,
    ): Int = ProductFontCatalog.estimatedTextWidth(text, selection, size)

    /** 返回同家族同宽度承担指定角色的精确字体选择。 */
    fun selectionForRole(
        family: LauncherFontFamily,
        widthMode: LauncherFontWidthMode,
        role: LauncherTextRole,
    ): LauncherFontSelection = ProductFontCatalog.selectionForRole(family, widthMode, role)
}

/** 对外强调家族值是稳定 ID，而不是可持久化枚举名称。 */
typealias FontFamilyId = LauncherFontFamily
