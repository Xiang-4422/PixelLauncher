package com.purride.pixeldesign

import com.purride.pixelcore.PixelShape

/** Launcher 与锁屏跨进程共享的非敏感产品外观快照。 */
public data class ProductAppearance(
    /** 逻辑像素在物理画布上的绘制形状。 */
    public val pixelShape: PixelShape = ProductPixelCatalog.defaultPixelShape,
    /** 一个逻辑像素占用的物理像素边长。 */
    public val dotSizePx: Int = ProductPixelCatalog.defaultDotSizePx,
    /** 逻辑像素之间是否保留可见间隙。 */
    public val pixelGapEnabled: Boolean = ProductPixelCatalog.defaultPixelGapEnabled,
    /** 当前产品主题家族。 */
    public val themeFamily: ProductThemeFamily = ProductThemeFamily.MIDNIGHT,
    /** 当前主题日夜选择；AUTO 由各宿主按本进程系统配置解析。 */
    public val themeMode: ProductThemeMode = ProductThemeMode.NIGHT,
) {
    init {
        require(dotSizePx in ProductPixelCatalog.supportedDotSizePxOptions) {
            "dotSizePx 必须属于共享产品像素尺寸目录"
        }
    }
}

/** 产品支持的像素规格目录，Launcher 与锁屏不得分别维护另一份列表。 */
public object ProductPixelCatalog {
    /** 未选择尺寸时使用的默认物理像素点大小。 */
    public const val defaultDotSizePx: Int = 12

    /** 默认像素形状。 */
    public val defaultPixelShape: PixelShape = PixelShape.SQUARE

    /** 默认关闭像素间隙。 */
    public const val defaultPixelGapEnabled: Boolean = false

    /** 设置页和锁屏共同支持的全部物理像素点大小。 */
    public val supportedDotSizePxOptions: List<Int> = listOf(7, 8, 10, 12, 14, 16)

    /** 把外部整数归一化为受支持尺寸，非法值回退到默认值。 */
    public fun normalizeDotSize(value: Int?): Int =
        value?.takeIf(supportedDotSizePxOptions::contains) ?: defaultDotSizePx

    /** 把跨进程字符串解析为像素形状，非法值回退到默认形状。 */
    public fun parsePixelShape(value: String?): PixelShape =
        PixelShape.entries.firstOrNull { shape -> shape.name == value } ?: defaultPixelShape
}

/** Launcher 外观 Provider 与 SystemUI 读取端共享的稳定跨进程协议。 */
public object ProductAppearanceContract {
    /** 当前协议版本；不兼容字段变化时必须递增。 */
    public const val schemaVersion: Int = 1

    /** 正式 Launcher 的外观 Provider authority。 */
    public const val releaseAuthority: String = "com.purride.pixellauncherv2.appearance"

    /** 默认 Debug Launcher 的外观 Provider authority。 */
    public const val debugAuthority: String = "com.purride.pixellauncherv2.debug.appearance"

    /** Provider 唯一只读路径。 */
    public const val appearancePath: String = "current"

    /** 协议版本列。 */
    public const val columnSchemaVersion: String = "schema_version"

    /** 像素形状列。 */
    public const val columnPixelShape: String = "pixel_shape"

    /** 物理像素尺寸列。 */
    public const val columnDotSizePx: String = "dot_size_px"

    /** 像素间隙开关列。 */
    public const val columnPixelGapEnabled: String = "pixel_gap_enabled"

    /** 主题家族稳定 ID 列。 */
    public const val columnThemeFamily: String = "theme_family"

    /** 主题模式列。 */
    public const val columnThemeMode: String = "theme_mode"

    /** 返回给定 authority 的完整只读 URI 字符串。 */
    public fun contentUri(authority: String): String = "content://$authority/$appearancePath"

    /** 按稳定家族 ID 解析主题，非法值回退为 MIDNIGHT。 */
    public fun parseThemeFamily(value: String?): ProductThemeFamily =
        ProductThemeFamily.entries.firstOrNull { family -> family.idPrefix == value }
            ?: ProductThemeFamily.MIDNIGHT

    /** 按协议名称解析主题模式，非法值回退为 NIGHT。 */
    public fun parseThemeMode(value: String?): ProductThemeMode =
        ProductThemeMode.entries.firstOrNull { mode -> mode.name == value }
            ?: ProductThemeMode.NIGHT
}
