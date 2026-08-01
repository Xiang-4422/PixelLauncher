package com.purride.pixeldesign.font

import com.purride.pixelcore.PixelBitmapFont
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 共享字体自适应边界的纯 JVM 回归测试。 */
class ProductFontSizingTest {
    /** 已经满足边界的栅格器必须保持实例不变，避免无意义包装。 */
    @Test
    fun fittingRasterizerReturnsOriginalWhenBoundsAlreadyFit() {
        /** 引擎内置的稳定 5×7 测试字体。 */
        val rasterizer = PixelBitmapFont.Default

        assertSame(rasterizer, rasterizer.fitProductTextWithin(maxHeight = 7))
    }

    /** 同时存在宽高约束时，结果必须满足更严格的一项。 */
    @Test
    fun fittingRasterizerHonorsWidthAndHeightBounds() {
        /** 缩小后的测试字体。 */
        val fitted = PixelBitmapFont.Default.fitProductTextWithin(
            sampleText = "00:00",
            maxWidth = 14,
            maxHeight = 4,
        )

        assertTrue(fitted.measureText("00:00") <= 14)
        assertTrue(fitted.measureHeight("Mg") <= 4)
    }
}
