package com.purride.pixeldesign.font

import com.purride.pixelcore.PixelBitmapFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** 验证 UI 显式字号覆盖仍被限制在当前字体家族与宽度模式内。 */
class ProductTypographyTest {

    /** 语义 chrome 必须解析到当前家族声明的原生 face。 */
    @Test
    fun rasterizer_usesNativeChromeRole() {
        val resolvedSelections = mutableListOf<ProductFontSelection>()
        val typography = ProductTypography(
            selection = ProductFontSelection(
                family = ProductFontFamily.PIX32,
                widthMode = ProductFontWidthMode.MONOSPACED,
                size = ProductFontSize.PX_12,
            ),
            rasterizerResolver = { selection ->
                resolvedSelections += selection
                PixelBitmapFont.Default
            },
        )

        typography.rasterizer(ProductTextRole.CHROME)

        assertEquals(ProductFontSize.PX_12, resolvedSelections.single().size)
    }

    /** 支持的显式字号应原样交给栅格器解析器。 */
    @Test
    fun rasterizer_usesExplicitSupportedSize() {
        /** 记录解析器实际收到的字体选择。 */
        val resolvedSelections = mutableListOf<ProductFontSelection>()
        /** Ark 默认选择，组件稍后显式请求 16px。 */
        val typography = ProductTypography(
            selection = ProductFontSelection(
                family = ProductFontFamily.ARK,
                widthMode = ProductFontWidthMode.MONOSPACED,
                size = ProductFontSize.PX_10,
            ),
            rasterizerResolver = { selection ->
                resolvedSelections += selection
                PixelBitmapFont.Default
            },
        )

        assertSame(PixelBitmapFont.Default, typography.rasterizer(ProductFontSize.PX_16))
        assertEquals(
            ProductFontSelection(
                family = ProductFontFamily.ARK,
                widthMode = ProductFontWidthMode.MONOSPACED,
                size = ProductFontSize.PX_16,
            ),
            resolvedSelections.single(),
        )
    }

    /** 不支持的显式字号必须立即失败，不能近似或切换字体家族。 */
    @Test(expected = IllegalArgumentException::class)
    fun rasterizer_rejectsUnsupportedExactSize() {
        /** 记录归一化后的解析参数。 */
        var resolvedSelection: ProductFontSelection? = null
        /** Ark 不提供 8px，调用必须在进入解析器前失败。 */
        val typography = ProductTypography(
            selection = ProductFontSelection(
                family = ProductFontFamily.ARK,
                widthMode = ProductFontWidthMode.MONOSPACED,
                size = ProductFontSize.PX_12,
            ),
            rasterizerResolver = { selection ->
                resolvedSelection = selection
                PixelBitmapFont.Default
            },
        )

        typography.rasterizer(ProductFontSize.PX_8)
        assertEquals(null, resolvedSelection)
    }
}
