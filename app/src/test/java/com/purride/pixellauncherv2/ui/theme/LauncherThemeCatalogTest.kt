package com.purride.pixellauncherv2.ui.theme

import com.purride.pixelcore.PixelColor
import com.purride.pixellauncherv2.launcher.LauncherThemeBrightness
import com.purride.pixellauncherv2.launcher.LauncherThemeFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁定全部 Showcase 风格主题家族的日间与夜间变体。 */
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

    /** Showcase 原生色板必须在对应基准亮度下保持完全一致。 */
    @Test
    fun nativeVariantsPreserveShowcaseCoreColors() {
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
        assertEquals(rgb(background), theme.surface.bezelColor)
        assertEquals(rgb(title), theme.text.primary)
        assertEquals(rgb(dim), theme.text.secondary)
        assertEquals(rgb(faint), theme.text.muted)
        assertEquals(rgb(border), theme.button.border)
        assertEquals(rgb(alert), theme.semantic.danger)
    }

    /** 把整数 RGB 常量转换为 PixelColor。 */
    private fun rgb(value: Int): PixelColor = PixelColor.fromRgb(
        (value shr 16) and 0xFF,
        (value shr 8) and 0xFF,
        value and 0xFF,
    )

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
