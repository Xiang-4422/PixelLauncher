package com.purride.pixeldesign

import com.purride.pixelcore.PixelColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁定共享产品主题目录的覆盖范围、稳定标识和基础可读性。 */
class ProductThemeCatalogTest {
    /** 八个家族都必须同时具有日间和夜间色板。 */
    @Test
    fun catalogCoversEveryFamilyAndBrightness() {
        ProductThemeFamily.entries.forEach { family ->
            ProductThemeBrightness.entries.forEach { brightness ->
                /** 当前家族和亮度对应的共享产品色板。 */
                val palette = ProductThemeCatalog.resolve(family, brightness)
                assertEquals(family, palette.family)
                assertEquals(brightness, palette.brightness)
                assertEquals("${family.idPrefix}-${brightness.idSuffix}", palette.id)
            }
        }
        assertEquals(8, ProductThemeFamily.entries.size)
    }

    /** 同一家族的日夜背景和主色必须保持不同。 */
    @Test
    fun everyFamilyHasDistinctDayAndNightIdentity() {
        ProductThemeFamily.entries.forEach { family ->
            /** 当前家族的日间色板。 */
            val day = ProductThemeCatalog.resolve(family, ProductThemeBrightness.LIGHT)
            /** 当前家族的夜间色板。 */
            val night = ProductThemeCatalog.resolve(family, ProductThemeBrightness.DARK)
            assertNotEquals(day.background, night.background)
            assertNotEquals(day.primary, night.primary)
        }
    }

    /** 全部文字、轮廓和警示色必须满足目录承诺的普通文字对比度。 */
    @Test
    fun everyPaletteKeepsSharedRolesReadable() {
        ProductThemeFamily.entries.forEach { family ->
            ProductThemeBrightness.entries.forEach { brightness ->
                /** 当前待验证的共享产品色板。 */
                val palette = ProductThemeCatalog.resolve(family, brightness)
                listOf(palette.primary, palette.secondary, palette.muted, palette.outline, palette.alert)
                    .forEach { color ->
                        /** 当前颜色与主题背景之间的 WCAG 对比度。 */
                        val contrast = contrastRatio(color, palette.background)
                        assertTrue("${palette.id} 对比度不足：$contrast", contrast >= 4.5)
                    }
            }
        }
    }

    /** AUTO 只跟随系统明暗，显式 DAY/NIGHT 不受系统状态影响。 */
    @Test
    fun themeModeResolvesBrightnessDeterministically() {
        assertEquals(ProductThemeBrightness.LIGHT, ProductThemeMode.DAY.resolve(systemInDarkMode = true))
        assertEquals(ProductThemeBrightness.DARK, ProductThemeMode.NIGHT.resolve(systemInDarkMode = false))
        assertEquals(ProductThemeBrightness.LIGHT, ProductThemeMode.AUTO.resolve(systemInDarkMode = false))
        assertEquals(ProductThemeBrightness.DARK, ProductThemeMode.AUTO.resolve(systemInDarkMode = true))
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
            return if (normalized <= 0.03928) normalized / 12.92 else Math.pow((normalized + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(color.red) +
            0.7152 * linear(color.green) +
            0.0722 * linear(color.blue)
    }
}
