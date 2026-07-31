package com.purride.pixellauncherv2.ui.theme

import com.purride.pixelcore.PixelColor
import com.purride.pixellauncherv2.launcher.LauncherThemeBrightness
import com.purride.pixellauncherv2.launcher.LauncherThemeFamily

/** 普通信息文字与其背景之间的最低对比度。 */
private const val MINIMUM_TEXT_CONTRAST = 4.5

/** 控件轮廓与相邻背景之间的最低对比度。 */
private const val MINIMUM_NON_TEXT_CONTRAST = 3.0

/** 在基础色与主色之间寻找可读颜色时使用的插值精度。 */
private const val ACCESSIBLE_COLOR_SEARCH_STEPS = 100

/** Launcher 内置的 Showcase 风格主题目录。 */
internal object LauncherThemeCatalog {
    /** 与 Showcase 六色主题语言一致的单个原始色板。 */
    private data class Palette(
        /** 整机背景色。 */
        val background: PixelColor,
        /** 主要标题和交互前景色。 */
        val title: PixelColor,
        /** 次级内容颜色。 */
        val dim: PixelColor,
        /** 弱化内容颜色。 */
        val faint: PixelColor,
        /** 控件边框颜色。 */
        val border: PixelColor,
        /** 警告与危险状态颜色。 */
        val alert: PixelColor,
    )

    /** Midnight 日间变体，反转深蓝层级并保留冷色机器感。 */
    private val midnightLight = Palette(
        background = rgb(0xECF4FF),
        title = rgb(0x0A0E1A),
        dim = rgb(0x465F82),
        faint = rgb(0x8CA5C8),
        border = rgb(0x506482),
        alert = rgb(0xB9342A),
    )

    /** Midnight 夜间变体，直接采用 Showcase 的 Deep Blue 色板。 */
    private val midnightDark = Palette(
        background = rgb(0x0A0E1A),
        title = rgb(0xECF4FF),
        dim = rgb(0x8CA5C8),
        faint = rgb(0x506482),
        border = rgb(0x465F82),
        alert = rgb(0xD84838),
    )

    /** CRT 日间变体，模拟浅色磷光管与墨绿色字形。 */
    private val crtLight = Palette(
        background = rgb(0xE8F8E8),
        title = rgb(0x030A04),
        dim = rgb(0x206424),
        faint = rgb(0x46B946),
        border = rgb(0x246E28),
        alert = rgb(0xA35B00),
    )

    /** CRT 夜间变体，直接采用 Showcase 的 Phosphor 色板。 */
    private val crtDark = Palette(
        background = rgb(0x030A04),
        title = rgb(0x82FF82),
        dim = rgb(0x46B946),
        faint = rgb(0x246E28),
        border = rgb(0x206424),
        alert = rgb(0xFFC83C),
    )

    /** Amber 日间变体，模拟暖色终端纸张。 */
    private val amberLight = Palette(
        background = rgb(0xFFF3D5),
        title = rgb(0x3B2100),
        dim = rgb(0x8A5A14),
        faint = rgb(0xC3A264),
        border = rgb(0xA98546),
        alert = rgb(0xC2410C),
    )

    /** Amber 夜间变体，直接采用 Showcase 的 Old Terminal 色板。 */
    private val amberDark = Palette(
        background = rgb(0x0E0802),
        title = rgb(0xFFBE3C),
        dim = rgb(0xC68C2A),
        faint = rgb(0x76541A),
        border = rgb(0x6A4C18),
        alert = rgb(0xFF603C),
    )

    /** Gameboy 日间变体，采用经典液晶屏四阶绿色。 */
    private val gameboyLight = Palette(
        background = rgb(0xD8E894),
        title = rgb(0x0F380F),
        dim = rgb(0x306230),
        faint = rgb(0x8BAC0F),
        border = rgb(0x306230),
        alert = rgb(0x0F380F),
    )

    /** Gameboy 夜间变体，直接采用 Showcase 的 Four Greens 色板。 */
    private val gameboyDark = Palette(
        background = rgb(0x0F380F),
        title = rgb(0x9BBC0F),
        dim = rgb(0x8BAC0F),
        faint = rgb(0x306230),
        border = rgb(0x306230),
        alert = rgb(0x9BBC0F),
    )

    /** Paper 日间变体，直接采用 Showcase 的 E-Ink Look 色板。 */
    private val paperLight = Palette(
        background = rgb(0xE9E4D6),
        title = rgb(0x181614),
        dim = rgb(0x54504A),
        faint = rgb(0x989288),
        border = rgb(0x7A756C),
        alert = rgb(0xB22A20),
    )

    /** Paper 夜间变体，以相同暖灰阶构造反相电子纸。 */
    private val paperDark = Palette(
        background = rgb(0x181614),
        title = rgb(0xE9E4D6),
        dim = rgb(0xAAA49A),
        faint = rgb(0x69645D),
        border = rgb(0x817B72),
        alert = rgb(0xE05A4F),
    )

    /** 主题家族与亮度到完整运行时主题的映射。 */
    val byVariant: Map<LauncherThemeVariant, LauncherTheme> = buildMap {
        putVariant(LauncherThemeFamily.MIDNIGHT, LauncherThemeBrightness.LIGHT, midnightLight)
        putVariant(LauncherThemeFamily.MIDNIGHT, LauncherThemeBrightness.DARK, midnightDark)
        putVariant(LauncherThemeFamily.CRT, LauncherThemeBrightness.LIGHT, crtLight)
        putVariant(LauncherThemeFamily.CRT, LauncherThemeBrightness.DARK, crtDark)
        putVariant(LauncherThemeFamily.AMBER, LauncherThemeBrightness.LIGHT, amberLight)
        putVariant(LauncherThemeFamily.AMBER, LauncherThemeBrightness.DARK, amberDark)
        putVariant(LauncherThemeFamily.GAMEBOY, LauncherThemeBrightness.LIGHT, gameboyLight)
        putVariant(LauncherThemeFamily.GAMEBOY, LauncherThemeBrightness.DARK, gameboyDark)
        putVariant(LauncherThemeFamily.PAPER, LauncherThemeBrightness.LIGHT, paperLight)
        putVariant(LauncherThemeFamily.PAPER, LauncherThemeBrightness.DARK, paperDark)
    }

    /** 把一个 Showcase 风格色板转换并加入完整主题变体映射。 */
    private fun MutableMap<LauncherThemeVariant, LauncherTheme>.putVariant(
        family: LauncherThemeFamily,
        brightness: LauncherThemeBrightness,
        palette: Palette,
    ) {
        /** 当前色板对应的主题变体键。 */
        val variant = LauncherThemeVariant(family = family, brightness = brightness)
        put(variant, buildTheme(family = family, brightness = brightness, palette = palette))
    }

    /** 从六个 Showcase 核心颜色推导 Launcher 所需的全部语义颜色。 */
    private fun buildTheme(
        family: LauncherThemeFamily,
        brightness: LauncherThemeBrightness,
        palette: Palette,
    ): LauncherTheme {
        /** 未点亮像素使用背景向标题轻微插值得到的颜色。 */
        val offPixel = mix(palette.background, palette.title, 0.06f)
        /** 次级面板使用更明显但仍属于本主题的插值颜色。 */
        val panelSubtle = mix(palette.background, palette.title, 0.12f)
        /** 可读信息文字从 Showcase 弱化色出发，仅向主色补足必要对比度。 */
        val metadataText = ensureContrast(
            color = palette.faint,
            background = palette.background,
            toward = palette.title,
            minimumContrast = MINIMUM_TEXT_CONTRAST,
        )
        /** 控件轮廓保留原色倾向，并满足非文字控件的最低识别要求。 */
        val outline = ensureContrast(
            color = palette.border,
            background = palette.background,
            toward = palette.title,
            minimumContrast = MINIMUM_NON_TEXT_CONTRAST,
        )
        /** 实心操作面从原始边框色出发，保证背景色作为反色文字时仍可阅读。 */
        val filledSurface = ensureContrast(
            color = palette.border,
            background = palette.background,
            toward = palette.title,
            minimumContrast = MINIMUM_TEXT_CONTRAST,
        )
        /** 选中指示块保留原有轻量填充起点，再增强到可明确辨认的状态。 */
        val selectedFill = ensureContrast(
            color = mix(palette.background, palette.title, 0.18f),
            background = palette.background,
            toward = palette.title,
            minimumContrast = MINIMUM_TEXT_CONTRAST,
        )
        /** 警告色在保持原始色相的前提下补足信息文字对比度。 */
        val alert = ensureContrast(
            color = palette.alert,
            background = palette.background,
            toward = palette.title,
            minimumContrast = MINIMUM_TEXT_CONTRAST,
        )
        return LauncherTheme(
            id = "${family.idPrefix}-${brightness.idSuffix}",
            label = "${family.displayLabel} ${brightness.idSuffix.uppercase()}",
            mode = brightness,
            surface = SurfaceColors(
                bezelColor = palette.background,
                offPixelColor = offPixel,
                panel = palette.background,
                panelSubtle = panelSubtle,
            ),
            text = TextColors(
                primary = palette.title,
                secondary = palette.dim,
                muted = metadataText,
                inverse = palette.background,
            ),
            statusBar = StatusBarColors(
                text = palette.title,
                mutedText = metadataText,
                batteryHigh = palette.title,
                batteryMedium = alert,
                batteryLow = alert,
                searchText = palette.title,
                searchPlaceholder = metadataText,
            ),
            drawer = DrawerColors(
                itemText = palette.title,
                itemTextMuted = metadataText,
                searchText = palette.title,
                searchPlaceholder = metadataText,
            ),
            settings = SettingsColors(
                itemTitle = palette.title,
                itemValue = palette.dim,
            ),
            button = ButtonColors(
                text = palette.title,
                border = outline,
                pressedFill = selectedFill,
                selectedText = palette.background,
                unselectedText = palette.dim,
                filledSurface = filledSurface,
                filledText = palette.background,
                disabledText = metadataText,
            ),
            sms = SmsColors(
                sender = palette.title,
                threadPreview = palette.dim,
                incomingMessage = palette.title,
                outgoingMessage = palette.dim,
                composerText = palette.title,
                timestamp = metadataText,
                draftBorder = outline,
                selectionFill = panelSubtle,
                loadingTrack = offPixel,
            ),
            semantic = SemanticColors(
                success = palette.title,
                warning = alert,
                danger = alert,
                info = palette.dim,
            ),
        )
    }

    /** 把整数 RGB 常量转换为 PixelColor。 */
    private fun rgb(value: Int): PixelColor = PixelColor.fromRgb(
        (value shr 16) and 0xFF,
        (value shr 8) and 0xFF,
        value and 0xFF,
    )

    /** 按 Showcase 相同的逐通道线性插值生成主题内部层级色。 */
    private fun mix(from: PixelColor, to: PixelColor, fraction: Float): PixelColor {
        return PixelColor.fromRgb(
            (from.red + (to.red - from.red) * fraction).toInt(),
            (from.green + (to.green - from.green) * fraction).toInt(),
            (from.blue + (to.blue - from.blue) * fraction).toInt(),
        )
    }

    /**
     * 从基础色向同主题主色逐步插值，返回首个满足最低对比度的颜色。
     *
     * 这种方式只调整必要的亮度距离，不把 Showcase 家族统一洗成同一套灰阶。
     */
    private fun ensureContrast(
        color: PixelColor,
        background: PixelColor,
        toward: PixelColor,
        minimumContrast: Double,
    ): PixelColor {
        require(minimumContrast >= 1.0) { "minimumContrast 必须不小于 1.0" }
        if (contrastRatio(color, background) >= minimumContrast) return color
        for (step in 1..ACCESSIBLE_COLOR_SEARCH_STEPS) {
            /** 当前搜索步对应的主题内部插值颜色。 */
            val candidate = mix(
                from = color,
                to = toward,
                fraction = step.toFloat() / ACCESSIBLE_COLOR_SEARCH_STEPS,
            )
            if (contrastRatio(candidate, background) >= minimumContrast) return candidate
        }
        return toward
    }

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
