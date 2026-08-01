package com.purride.pixeldesign

import com.purride.pixelcore.PixelColor

/** 普通信息文字与主题背景之间必须满足的最低对比度。 */
private const val MINIMUM_TEXT_CONTRAST = 4.5

/** 在原始色与主色之间寻找可读颜色时使用的固定插值步数。 */
private const val ACCESSIBLE_COLOR_SEARCH_STEPS = 100

/** 产品内置主题家族；每个家族都具有独立的日间和夜间色板。 */
public enum class ProductThemeFamily(
    /** 主题变体 ID 和持久化设置使用的稳定前缀。 */
    public val idPrefix: String,
    /** 设置页和预览页使用的紧凑展示名称。 */
    public val displayLabel: String,
) {
    MIDNIGHT("midnight", "MIDNIGHT"),
    CRT("crt", "CRT"),
    AMBER("amber", "AMBER"),
    GAMEBOY("gameboy", "GAMEBOY"),
    PAPER("paper", "PAPER"),
    BUBBLEGUM("bubblegum", "BUBBLEGUM"),
    CITRUS("citrus", "CITRUS"),
    ARCADE("arcade", "ARCADE"),
    ;
}

/** 用户可选择的主题模式；AUTO 只负责把系统明暗解析为具体亮度。 */
public enum class ProductThemeMode(
    /** 设置页和预览页使用的紧凑展示名称。 */
    public val displayLabel: String,
) {
    DAY("DAY"),
    AUTO("AUTO"),
    NIGHT("NIGHT"),
    ;

    /** 根据系统暗色状态把用户模式解析为实际主题亮度。 */
    public fun resolve(systemInDarkMode: Boolean): ProductThemeBrightness = when (this) {
        AUTO -> if (systemInDarkMode) ProductThemeBrightness.DARK else ProductThemeBrightness.LIGHT
        DAY -> ProductThemeBrightness.LIGHT
        NIGHT -> ProductThemeBrightness.DARK
    }
}

/** 主题实际采用的亮度；该值由用户模式和系统状态解析，不作为独立设置保存。 */
public enum class ProductThemeBrightness(
    /** 主题变体 ID 使用的稳定后缀。 */
    public val idSuffix: String,
) {
    LIGHT("day"),
    DARK("night"),
}

/** Launcher、锁屏和离线预览共同消费的可读产品色板。 */
public data class ProductPalette(
    /** 当前色板所属主题家族。 */
    public val family: ProductThemeFamily,
    /** 当前色板的实际亮度。 */
    public val brightness: ProductThemeBrightness,
    /** 页面或预览基准背景色。 */
    public val background: PixelColor,
    /** 标题、主要文字和高强调图形颜色。 */
    public val primary: PixelColor,
    /** 次级文字和低强调图形颜色。 */
    public val secondary: PixelColor,
    /** 已针对背景补足普通文字对比度的弱化信息颜色。 */
    public val muted: PixelColor,
    /** 已针对背景补足对比度的控件轮廓颜色。 */
    public val outline: PixelColor,
    /** 已针对背景补足对比度的警告和危险颜色。 */
    public val alert: PixelColor,
) {
    /** 返回主题家族和亮度共同组成的稳定变体 ID。 */
    public val id: String
        get() = "${family.idPrefix}-${brightness.idSuffix}"

    /** 返回适合诊断和预览的完整主题名称。 */
    public val label: String
        get() = "${family.displayLabel} ${brightness.idSuffix.uppercase()}"
}

/** 产品主题的唯一色板目录，集中维护全部家族及其可读性派生规则。 */
public object ProductThemeCatalog {
    /** 未做可读性派生的六色产品原始色板。 */
    private data class RawPalette(
        /** 页面基准背景色。 */
        val background: PixelColor,
        /** 主要标题和交互前景色。 */
        val primary: PixelColor,
        /** 次级内容颜色。 */
        val secondary: PixelColor,
        /** 弱化内容的原始颜色。 */
        val muted: PixelColor,
        /** 控件边框的原始颜色。 */
        val outline: PixelColor,
        /** 警告和危险状态的原始颜色。 */
        val alert: PixelColor,
    )

    /** 全部主题家族和亮度组合对应的可读产品色板。 */
    private val palettes: Map<Pair<ProductThemeFamily, ProductThemeBrightness>, ProductPalette> = buildMap {
        putPalette(ProductThemeFamily.MIDNIGHT, ProductThemeBrightness.LIGHT, 0xECF4FF, 0x0A0E1A, 0x465F82, 0x8CA5C8, 0x506482, 0xB9342A)
        putPalette(ProductThemeFamily.MIDNIGHT, ProductThemeBrightness.DARK, 0x0A0E1A, 0xECF4FF, 0x8CA5C8, 0x506482, 0x465F82, 0xD84838)
        putPalette(ProductThemeFamily.CRT, ProductThemeBrightness.LIGHT, 0xE8F8E8, 0x030A04, 0x206424, 0x46B946, 0x246E28, 0xA35B00)
        putPalette(ProductThemeFamily.CRT, ProductThemeBrightness.DARK, 0x030A04, 0x82FF82, 0x46B946, 0x246E28, 0x206424, 0xFFC83C)
        putPalette(ProductThemeFamily.AMBER, ProductThemeBrightness.LIGHT, 0xFFF3D5, 0x3B2100, 0x8A5A14, 0xC3A264, 0xA98546, 0xC2410C)
        putPalette(ProductThemeFamily.AMBER, ProductThemeBrightness.DARK, 0x0E0802, 0xFFBE3C, 0xC68C2A, 0x76541A, 0x6A4C18, 0xFF603C)
        putPalette(ProductThemeFamily.GAMEBOY, ProductThemeBrightness.LIGHT, 0xD8E894, 0x0F380F, 0x306230, 0x8BAC0F, 0x306230, 0x0F380F)
        putPalette(ProductThemeFamily.GAMEBOY, ProductThemeBrightness.DARK, 0x0F380F, 0x9BBC0F, 0x8BAC0F, 0x306230, 0x306230, 0x9BBC0F)
        putPalette(ProductThemeFamily.PAPER, ProductThemeBrightness.LIGHT, 0xE9E4D6, 0x181614, 0x54504A, 0x989288, 0x7A756C, 0xB22A20)
        putPalette(ProductThemeFamily.PAPER, ProductThemeBrightness.DARK, 0x181614, 0xE9E4D6, 0xAAA49A, 0x69645D, 0x817B72, 0xE05A4F)
        putPalette(ProductThemeFamily.BUBBLEGUM, ProductThemeBrightness.LIGHT, 0xFFF0F7, 0x4B0B7A, 0xB00068, 0x9A7087, 0x005FE5, 0xC23B00)
        putPalette(ProductThemeFamily.BUBBLEGUM, ProductThemeBrightness.DARK, 0x170022, 0xFF65C3, 0x57E8FF, 0x8A4F9A, 0xFFD23F, 0xFF7A3D)
        putPalette(ProductThemeFamily.CITRUS, ProductThemeBrightness.LIGHT, 0xFFF7C2, 0x2A165F, 0x7A3E00, 0xA18A3B, 0x6A2FD1, 0xC43A00)
        putPalette(ProductThemeFamily.CITRUS, ProductThemeBrightness.DARK, 0x160D2B, 0xD7FF45, 0xFF9F1C, 0x73604E, 0x34D1BF, 0xFF5A5F)
        putPalette(ProductThemeFamily.ARCADE, ProductThemeBrightness.LIGHT, 0xE9FBFF, 0x061B6B, 0xA0006D, 0x6E8490, 0x0057D9, 0xD12D00)
        putPalette(ProductThemeFamily.ARCADE, ProductThemeBrightness.DARK, 0x07051F, 0x00F0FF, 0xFF4FD8, 0x604D80, 0x7CFF4F, 0xFFB000)
    }

    /** 返回指定主题家族和实际亮度对应的共享产品色板。 */
    public fun resolve(
        family: ProductThemeFamily,
        brightness: ProductThemeBrightness,
    ): ProductPalette = palettes.getValue(family to brightness)

    /** 把一组六位 RGB 常量转换为可读产品色板并加入目录。 */
    private fun MutableMap<Pair<ProductThemeFamily, ProductThemeBrightness>, ProductPalette>.putPalette(
        family: ProductThemeFamily,
        brightness: ProductThemeBrightness,
        background: Int,
        primary: Int,
        secondary: Int,
        muted: Int,
        outline: Int,
        alert: Int,
    ) {
        /** 当前原始常量对应的未派生色板。 */
        val raw = RawPalette(
            background = rgb(background),
            primary = rgb(primary),
            secondary = rgb(secondary),
            muted = rgb(muted),
            outline = rgb(outline),
            alert = rgb(alert),
        )
        put(
            family to brightness,
            ProductPalette(
                family = family,
                brightness = brightness,
                background = raw.background,
                primary = raw.primary,
                secondary = raw.secondary,
                muted = ensureContrast(raw.muted, raw.background, raw.primary),
                outline = ensureContrast(raw.outline, raw.background, raw.primary),
                alert = ensureContrast(raw.alert, raw.background, raw.primary),
            ),
        )
    }

    /** 把整数 RGB 常量转换为不透明 PixelColor。 */
    private fun rgb(value: Int): PixelColor = PixelColor.fromRgb(
        (value shr 16) and 0xFF,
        (value shr 8) and 0xFF,
        value and 0xFF,
    )

    /** 从原始颜色向主题主色插值，返回首个满足普通文字对比度的颜色。 */
    private fun ensureContrast(
        color: PixelColor,
        background: PixelColor,
        toward: PixelColor,
    ): PixelColor {
        if (contrastRatio(color, background) >= MINIMUM_TEXT_CONTRAST) return color
        for (step in 1..ACCESSIBLE_COLOR_SEARCH_STEPS) {
            /** 当前搜索步对应的主题内部插值颜色。 */
            val candidate = mix(color, toward, step.toFloat() / ACCESSIBLE_COLOR_SEARCH_STEPS)
            if (contrastRatio(candidate, background) >= MINIMUM_TEXT_CONTRAST) return candidate
        }
        return toward
    }

    /** 按逐通道线性插值生成主题内部颜色。 */
    private fun mix(from: PixelColor, to: PixelColor, fraction: Float): PixelColor = PixelColor.fromRgb(
        (from.red + (to.red - from.red) * fraction).toInt(),
        (from.green + (to.green - from.green) * fraction).toInt(),
        (from.blue + (to.blue - from.blue) * fraction).toInt(),
    )

    /** 计算两个不透明 sRGB 颜色之间的 WCAG 对比度。 */
    private fun contrastRatio(first: PixelColor, second: PixelColor): Double {
        /** 第一种颜色的相对亮度。 */
        val firstLuminance = relativeLuminance(first)
        /** 第二种颜色的相对亮度。 */
        val secondLuminance = relativeLuminance(second)
        return (maxOf(firstLuminance, secondLuminance) + 0.05) /
            (minOf(firstLuminance, secondLuminance) + 0.05)
    }

    /** 计算一个不透明 sRGB 颜色的相对亮度。 */
    private fun relativeLuminance(color: PixelColor): Double {
        /** 把八位 sRGB 通道转换为线性通道。 */
        fun linear(channel: Int): Double {
            /** 归一化后的 sRGB 通道。 */
            val normalized = channel / 255.0
            return if (normalized <= 0.03928) {
                normalized / 12.92
            } else {
                Math.pow((normalized + 0.055) / 1.055, 2.4)
            }
        }
        return 0.2126 * linear(color.red) +
            0.7152 * linear(color.green) +
            0.0722 * linear(color.blue)
    }
}
