package com.purride.pixellauncherv2.ui.text

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixellauncherv2.launcher.LauncherFontFamily
import com.purride.pixellauncherv2.launcher.LauncherFontSelection
import com.purride.pixellauncherv2.launcher.LauncherFontWidthMode
import com.purride.pixellauncherv2.launcher.PixelFontSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** 验证 UI 显式字号覆盖仍被限制在当前字体家族与宽度模式内。 */
class LauncherTypographyTest {

    /** 支持的显式字号应原样交给栅格器解析器。 */
    @Test
    fun rasterizer_usesExplicitSupportedSize() {
        /** 记录解析器实际收到的字体选择。 */
        val resolvedSelections = mutableListOf<LauncherFontSelection>()
        /** Ark 默认选择，组件稍后显式请求 16px。 */
        val typography = LauncherTypography(
            selection = LauncherFontSelection(
                family = LauncherFontFamily.ARK,
                widthMode = LauncherFontWidthMode.MONOSPACED,
                size = PixelFontSize.PX_10,
            ),
            rasterizerResolver = { selection ->
                resolvedSelections += selection
                PixelBitmapFont.Default
            },
        )

        assertSame(PixelBitmapFont.Default, typography.rasterizer(PixelFontSize.PX_16))
        assertEquals(
            LauncherFontSelection(
                family = LauncherFontFamily.ARK,
                widthMode = LauncherFontWidthMode.MONOSPACED,
                size = PixelFontSize.PX_16,
            ),
            resolvedSelections.single(),
        )
    }

    /** 不支持的显式字号必须立即失败，不能近似或切换字体家族。 */
    @Test(expected = IllegalArgumentException::class)
    fun rasterizer_rejectsUnsupportedExactSize() {
        /** 记录归一化后的解析参数。 */
        var resolvedSelection: LauncherFontSelection? = null
        /** Ark 不提供 8px，调用必须在进入解析器前失败。 */
        val typography = LauncherTypography(
            selection = LauncherFontSelection(
                family = LauncherFontFamily.ARK,
                widthMode = LauncherFontWidthMode.MONOSPACED,
                size = PixelFontSize.PX_12,
            ),
            rasterizerResolver = { selection ->
                resolvedSelection = selection
                PixelBitmapFont.Default
            },
        )

        typography.rasterizer(PixelFontSize.PX_8)
        assertEquals(null, resolvedSelection)
    }
}
