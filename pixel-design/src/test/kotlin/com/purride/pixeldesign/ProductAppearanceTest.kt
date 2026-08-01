package com.purride.pixeldesign

import com.purride.pixelcore.PixelShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁定 Launcher 与锁屏共享外观协议的默认值、规格目录和容错行为。 */
class ProductAppearanceTest {
    /** 默认快照必须与当前产品默认外观一致。 */
    @Test
    fun defaultsRemainStable() {
        /** 当前默认共享外观。 */
        val appearance = ProductAppearance()

        assertEquals(PixelShape.SQUARE, appearance.pixelShape)
        assertEquals(12, appearance.dotSizePx)
        assertEquals(false, appearance.pixelGapEnabled)
        assertEquals(ProductThemeFamily.MIDNIGHT, appearance.themeFamily)
        assertEquals(ProductThemeMode.NIGHT, appearance.themeMode)
    }

    /** 尺寸目录必须保持唯一、有序且覆盖 Launcher 设置页现有选项。 */
    @Test
    fun pixelCatalogKeepsSupportedOptionsStable() {
        assertEquals(listOf(7, 8, 10, 12, 14, 16), ProductPixelCatalog.supportedDotSizePxOptions)
        assertEquals(
            ProductPixelCatalog.supportedDotSizePxOptions.size,
            ProductPixelCatalog.supportedDotSizePxOptions.distinct().size,
        )
        assertTrue(ProductPixelCatalog.supportedDotSizePxOptions.all { value -> value > 0 })
    }

    /** 跨进程脏值必须安全回退，不能让 SystemUI 因非法设置崩溃。 */
    @Test
    fun contractParsersFailClosedToDefaults() {
        assertEquals(12, ProductPixelCatalog.normalizeDotSize(9))
        assertEquals(PixelShape.SQUARE, ProductPixelCatalog.parsePixelShape("TRIANGLE"))
        assertEquals(ProductThemeFamily.MIDNIGHT, ProductAppearanceContract.parseThemeFamily("missing"))
        assertEquals(ProductThemeMode.NIGHT, ProductAppearanceContract.parseThemeMode("missing"))
        assertEquals(
            "content://${ProductAppearanceContract.releaseAuthority}/current",
            ProductAppearanceContract.contentUri(ProductAppearanceContract.releaseAuthority),
        )
    }
}
