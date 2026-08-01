package com.purride.pixellauncherv2.ui.theme

import com.purride.pixelcore.PixelColor
import com.purride.pixellauncherv2.launcher.LauncherThemeBrightness
import com.purride.pixellauncherv2.launcher.LauncherThemeFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁定全部内置主题家族的日间与夜间变体及可读性。 */
class LauncherThemeCatalogTest {
    /** 家族与实际亮度的笛卡尔积必须全部可用且元数据匹配。 */
    @Test
    fun catalogCoversEveryThemeFamilyAndBrightness() {
        /** 全部主题家族与亮度组合。 */
        val expectedVariants = LauncherThemeFamily.entries.flatMap { family ->
            LauncherThemeBrightness.entries.map { brightness ->
                LauncherThemeVariant(family = family, brightness = brightness)
            }
        }.toSet()

        assertEquals(expectedVariants, LauncherThemeCatalog.byVariant.keys)
        expectedVariants.forEach { variant ->
            /** 当前变体对应的完整运行时主题。 */
            val theme = LauncherThemeCatalog.byVariant.getValue(variant)
            assertEquals(
                "${variant.family.idPrefix}-${variant.brightness.idSuffix}",
                theme.id,
            )
            assertEquals(variant.brightness, theme.mode)
        }
    }

    /** Showcase 原生身份色保持不变，需要增强的弱色则只做最低限度的可读性派生。 */
    @Test
    fun nativeVariantsPreserveShowcaseIdentityAndAccessibleDerivations() {
        assertCoreColors(
            family = LauncherThemeFamily.MIDNIGHT,
            brightness = LauncherThemeBrightness.DARK,
            background = 0x0A0E1A,
            title = 0xECF4FF,
            dim = 0x8CA5C8,
            faint = 0x506482,
            border = 0x465F82,
            alert = 0xD84838,
        )
        assertCoreColors(
            family = LauncherThemeFamily.CRT,
            brightness = LauncherThemeBrightness.DARK,
            background = 0x030A04,
            title = 0x82FF82,
            dim = 0x46B946,
            faint = 0x246E28,
            border = 0x206424,
            alert = 0xFFC83C,
        )
        assertCoreColors(
            family = LauncherThemeFamily.AMBER,
            brightness = LauncherThemeBrightness.DARK,
            background = 0x0E0802,
            title = 0xFFBE3C,
            dim = 0xC68C2A,
            faint = 0x76541A,
            border = 0x6A4C18,
            alert = 0xFF603C,
        )
        assertCoreColors(
            family = LauncherThemeFamily.GAMEBOY,
            brightness = LauncherThemeBrightness.DARK,
            background = 0x0F380F,
            title = 0x9BBC0F,
            dim = 0x8BAC0F,
            faint = 0x306230,
            border = 0x306230,
            alert = 0x9BBC0F,
        )
        assertCoreColors(
            family = LauncherThemeFamily.PAPER,
            brightness = LauncherThemeBrightness.LIGHT,
            background = 0xE9E4D6,
            title = 0x181614,
            dim = 0x54504A,
            faint = 0x989288,
            border = 0x7A756C,
            alert = 0xB22A20,
        )
    }

    /** 高活力主题必须保留各自经过评审的冷暖核心色，不被可读性派生洗成同一色板。 */
    @Test
    fun dopamineVariantsPreserveReviewedCoreColors() {
        assertCoreColors(
            family = LauncherThemeFamily.BUBBLEGUM,
            brightness = LauncherThemeBrightness.LIGHT,
            background = 0xFFF0F7,
            title = 0x4B0B7A,
            dim = 0xB00068,
            faint = 0x9A7087,
            border = 0x005FE5,
            alert = 0xC23B00,
        )
        assertCoreColors(
            family = LauncherThemeFamily.BUBBLEGUM,
            brightness = LauncherThemeBrightness.DARK,
            background = 0x170022,
            title = 0xFF65C3,
            dim = 0x57E8FF,
            faint = 0x8A4F9A,
            border = 0xFFD23F,
            alert = 0xFF7A3D,
        )
        assertCoreColors(
            family = LauncherThemeFamily.CITRUS,
            brightness = LauncherThemeBrightness.LIGHT,
            background = 0xFFF7C2,
            title = 0x2A165F,
            dim = 0x7A3E00,
            faint = 0xA18A3B,
            border = 0x6A2FD1,
            alert = 0xC43A00,
        )
        assertCoreColors(
            family = LauncherThemeFamily.CITRUS,
            brightness = LauncherThemeBrightness.DARK,
            background = 0x160D2B,
            title = 0xD7FF45,
            dim = 0xFF9F1C,
            faint = 0x73604E,
            border = 0x34D1BF,
            alert = 0xFF5A5F,
        )
        assertCoreColors(
            family = LauncherThemeFamily.ARCADE,
            brightness = LauncherThemeBrightness.LIGHT,
            background = 0xE9FBFF,
            title = 0x061B6B,
            dim = 0xA0006D,
            faint = 0x6E8490,
            border = 0x0057D9,
            alert = 0xD12D00,
        )
        assertCoreColors(
            family = LauncherThemeFamily.ARCADE,
            brightness = LauncherThemeBrightness.DARK,
            background = 0x07051F,
            title = 0x00F0FF,
            dim = 0xFF4FD8,
            faint = 0x604D80,
            border = 0x7CFF4F,
            alert = 0xFFB000,
        )
    }

    /** 同一家族的日间与夜间必须拥有不同背景并报告正确亮度。 */
    @Test
    fun everyFamilyHasDistinctLightAndDarkVariants() {
        LauncherThemeFamily.entries.forEach { family ->
            /** 当前家族的日间主题。 */
            val light = LauncherThemes.resolve(family, LauncherThemeBrightness.LIGHT)
            /** 当前家族的夜间主题。 */
            val dark = LauncherThemes.resolve(family, LauncherThemeBrightness.DARK)
            assertEquals(LauncherThemeBrightness.LIGHT, light.mode)
            assertEquals(LauncherThemeBrightness.DARK, dark.mode)
            assertNotEquals(light.surface.bezelColor, dark.surface.bezelColor)
            assertNotEquals(light.text.primary, dark.text.primary)
        }
    }

    /** 所有变体的主要文字与背景都必须满足普通文字对比度下限。 */
    @Test
    fun everyVariantKeepsPrimaryTextReadable() {
        LauncherThemeCatalog.byVariant.forEach { (variant, theme) ->
            /** 当前变体主要文字与背景的 WCAG 对比度。 */
            val contrast = contrastRatio(theme.text.primary, theme.surface.bezelColor)
            assertTrue("$variant 主要文字对比度不足：$contrast", contrast >= 4.5)
        }
    }

    /** 承载时间、状态、占位符等信息的弱化文字也必须保持普通文字可读性。 */
    @Test
    fun everyVariantKeepsInformativeTextReadable() {
        LauncherThemeCatalog.byVariant.forEach { (variant, theme) ->
            /** 当前主题的信息文字与背景组合。 */
            val informativeColors = mapOf(
                "secondary" to theme.text.secondary,
                "muted" to theme.text.muted,
                "statusMuted" to theme.statusBar.mutedText,
                "statusPlaceholder" to theme.statusBar.searchPlaceholder,
                "drawerMuted" to theme.drawer.itemTextMuted,
                "drawerPlaceholder" to theme.drawer.searchPlaceholder,
                "settingsValue" to theme.settings.itemValue,
                "buttonDisabled" to theme.button.disabledText,
                "buttonUnselected" to theme.button.unselectedText,
                "smsSender" to theme.sms.sender,
                "smsThreadPreview" to theme.sms.threadPreview,
                "smsIncomingMessage" to theme.sms.incomingMessage,
                "smsOutgoingMessage" to theme.sms.outgoingMessage,
                "smsComposerText" to theme.sms.composerText,
                "smsTimestamp" to theme.sms.timestamp,
                "semanticSuccess" to theme.semantic.success,
                "semanticWarning" to theme.semantic.warning,
                "semanticDanger" to theme.semantic.danger,
                "semanticInfo" to theme.semantic.info,
            )
            informativeColors.forEach { (role, color) ->
                /** 当前信息角色与整机背景之间的对比度。 */
                val contrast = contrastRatio(color, theme.surface.bezelColor)
                assertTrue("$variant $role 对比度不足：$contrast", contrast >= 4.5)
            }
        }
    }

    /** 短信专用颜色必须兼顾输入可读性、加载辨识度与收发信息层级。 */
    @Test
    fun everyVariantKeepsSmsRolesReadableAndDistinct() {
        LauncherThemeCatalog.byVariant.forEach { (variant, theme) ->
            /** 输入正文与选区背景之间的对比度。 */
            val selectionTextContrast = contrastRatio(
                theme.sms.composerText,
                theme.sms.selectionFill,
            )
            /** 加载扫描色与未激活轨道之间的对比度。 */
            val loadingContrast = contrastRatio(theme.text.primary, theme.sms.loadingTrack)
            /** 短信输入轮廓与页面背景之间的对比度。 */
            val draftBorderContrast = contrastRatio(
                theme.sms.draftBorder,
                theme.surface.bezelColor,
            )

            assertTrue("$variant 短信选区文字对比度不足：$selectionTextContrast", selectionTextContrast >= 4.5)
            assertTrue("$variant 短信加载动画对比度不足：$loadingContrast", loadingContrast >= 4.5)
            assertTrue("$variant 短信输入轮廓对比度不足：$draftBorderContrast", draftBorderContrast >= 3.0)
            assertNotEquals("$variant 会话标题与摘要必须区分层级", theme.sms.sender, theme.sms.threadPreview)
            assertNotEquals(
                "$variant 收发消息必须使用不同颜色角色",
                theme.sms.incomingMessage,
                theme.sms.outgoingMessage,
            )
            assertNotEquals("$variant 加载扫描色与轨道不能相同", theme.text.primary, theme.sms.loadingTrack)
        }
    }

    /** 控件轮廓、选中块以及实心操作必须分别拥有清晰边界和成对前景色。 */
    @Test
    fun everyVariantKeepsControlsReadable() {
        LauncherThemeCatalog.byVariant.forEach { (variant, theme) ->
            /** 控件轮廓与页面背景之间的对比度。 */
            val outlineContrast = contrastRatio(theme.button.border, theme.surface.bezelColor)
            /** 选中块与未选中背景之间的对比度。 */
            val selectionContrast = contrastRatio(theme.button.pressedFill, theme.surface.bezelColor)
            /** 选中文字与选中块之间的对比度。 */
            val selectedTextContrast = contrastRatio(theme.button.selectedText, theme.button.pressedFill)
            /** 实心操作文字与实心背景之间的对比度。 */
            val filledTextContrast = contrastRatio(theme.button.filledText, theme.button.filledSurface)

            assertTrue("$variant 控件轮廓对比度不足：$outlineContrast", outlineContrast >= 4.5)
            assertEquals("$variant 控件选中填充必须与边框同色", theme.button.border, theme.button.pressedFill)
            assertTrue("$variant 选中块对比度不足：$selectionContrast", selectionContrast >= 3.0)
            assertTrue("$variant 选中文字对比度不足：$selectedTextContrast", selectedTextContrast >= 4.5)
            assertTrue("$variant 实心操作文字对比度不足：$filledTextContrast", filledTextContrast >= 4.5)
        }
    }

    /** 断言一个 Showcase 基准色板的六个核心角色。 */
    private fun assertCoreColors(
        family: LauncherThemeFamily,
        brightness: LauncherThemeBrightness,
        background: Int,
        title: Int,
        dim: Int,
        faint: Int,
        border: Int,
        alert: Int,
    ) {
        /** 待验证的完整 Launcher 主题。 */
        val theme = LauncherThemes.resolve(family, brightness)
        /** Showcase 原始背景色。 */
        val backgroundColor = rgb(background)
        /** Showcase 原始主色。 */
        val titleColor = rgb(title)
        assertEquals(backgroundColor, theme.surface.bezelColor)
        assertEquals(titleColor, theme.text.primary)
        assertEquals(rgb(dim), theme.text.secondary)
        assertEquals(
            ensureContrast(rgb(faint), backgroundColor, titleColor, minimumContrast = 4.5),
            theme.text.muted,
        )
        assertEquals(
            ensureContrast(rgb(border), backgroundColor, titleColor, minimumContrast = 4.5),
            theme.button.border,
        )
        assertEquals(
            ensureContrast(rgb(alert), backgroundColor, titleColor, minimumContrast = 4.5),
            theme.semantic.danger,
        )
    }

    /** 把整数 RGB 常量转换为 PixelColor。 */
    private fun rgb(value: Int): PixelColor = PixelColor.fromRgb(
        (value shr 16) and 0xFF,
        (value shr 8) and 0xFF,
        value and 0xFF,
    )

    /** 复现主题目录的最小必要对比度派生规则。 */
    private fun ensureContrast(
        color: PixelColor,
        background: PixelColor,
        toward: PixelColor,
        minimumContrast: Double,
    ): PixelColor {
        if (contrastRatio(color, background) >= minimumContrast) return color
        for (step in 1..100) {
            /** 当前搜索步对应的主题内部插值颜色。 */
            val candidate = mix(
                from = color,
                to = toward,
                fraction = step / 100f,
            )
            if (contrastRatio(candidate, background) >= minimumContrast) return candidate
        }
        return toward
    }

    /** 按逐通道线性插值复现主题内部颜色派生。 */
    private fun mix(from: PixelColor, to: PixelColor, fraction: Float): PixelColor {
        return PixelColor.fromRgb(
            (from.red + (to.red - from.red) * fraction).toInt(),
            (from.green + (to.green - from.green) * fraction).toInt(),
            (from.blue + (to.blue - from.blue) * fraction).toInt(),
        )
    }

    /** 计算两种颜色的 WCAG 对比度。 */
    private fun contrastRatio(first: PixelColor, second: PixelColor): Double {
        /** 第一种颜色的相对亮度。 */
        val firstLuminance = relativeLuminance(first)
        /** 第二种颜色的相对亮度。 */
        val secondLuminance = relativeLuminance(second)
        return (maxOf(firstLuminance, secondLuminance) + 0.05) /
            (minOf(firstLuminance, secondLuminance) + 0.05)
    }

    /** 计算 sRGB 颜色的相对亮度。 */
    private fun relativeLuminance(color: PixelColor): Double {
        /** 把一个八位 sRGB 通道转换为线性通道。 */
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
