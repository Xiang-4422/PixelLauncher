package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证字体能力矩阵、默认选择和不跨家族归一化规则。 */
class PixelFontCatalogTest {

    /** 三个低分辨率矢量点形应合并为唯一、可逐点验证的 Dotted 家族。 */
    @Test
    fun dotted_exposesOnlyCanonicalPointGridFamily() {
        assertEquals(8, PixelFontCatalog.fontFamilyOptions().size)
        assertEquals(1, PixelFontCatalog.fontFamilyOptions().count { family -> family == LauncherFontFamily.DOTTED })
        assertEquals("DOTTED", PixelFontCatalog.familyLabel(LauncherFontFamily.DOTTED))
        assertEquals(
            PixelFontMetrics(PixelFontSize.PX_13, 14, 10, 9, 13),
            PixelFontCatalog.metrics(
                LauncherFontSelection(
                    LauncherFontFamily.DOTTED,
                    LauncherFontWidthMode.PROPORTIONAL,
                    PixelFontSize.PX_13,
                ),
            ),
        )
    }

    /** 字号标签应使用稳定的 PX 表示。 */
    @Test
    fun sizeLabel_mapsPixelSizes() {
        assertEquals("7PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_7))
        assertEquals("8PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_8))
        assertEquals("9PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_9))
        assertEquals("10PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_10))
        assertEquals("11PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_11))
        assertEquals("12PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_12))
        assertEquals("13PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_13))
        assertEquals("16PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_16))
    }

    /** 诊断页字号选项应覆盖全部原生字号。 */
    @Test
    fun options_exposeAllEnumEntries() {
        assertEquals(PixelFontSize.entries.toList(), PixelFontCatalog.fontSizeOptions())
    }

    /** 缺失设置时应继续使用 Fusion Prop 10px。 */
    @Test
    fun default_isFusionProportional10px() {
        assertEquals(
            LauncherFontSelection(
                family = LauncherFontFamily.FUSION,
                widthMode = LauncherFontWidthMode.PROPORTIONAL,
                size = PixelFontSize.PX_10,
            ),
            PixelFontCatalog.defaultUiFontSelection,
        )
    }

    /** 每个字体族只应暴露自身真正存在的字号。 */
    @Test
    fun fontSizes_followSelectedFamilyCapabilities() {
        LauncherFontWidthMode.entries.forEach { widthMode ->
            assertEquals(
                listOf(PixelFontSize.PX_8, PixelFontSize.PX_10, PixelFontSize.PX_12),
                PixelFontCatalog.fontSizeOptions(LauncherFontFamily.FUSION, widthMode),
            )
            assertEquals(
                listOf(PixelFontSize.PX_10, PixelFontSize.PX_12, PixelFontSize.PX_16),
                PixelFontCatalog.fontSizeOptions(LauncherFontFamily.ARK, widthMode),
            )
        }
        assertEquals(
            listOf(PixelFontSize.PX_11),
            PixelFontCatalog.fontSizeOptions(LauncherFontFamily.CUBIC_11, LauncherFontWidthMode.PROPORTIONAL),
        )
        assertEquals(
            listOf(PixelFontSize.PX_7),
            PixelFontCatalog.fontSizeOptions(LauncherFontFamily.BOUTIQUE_7, LauncherFontWidthMode.PROPORTIONAL),
        )
        assertEquals(
            listOf(PixelFontSize.PX_9),
            PixelFontCatalog.fontSizeOptions(LauncherFontFamily.BOUTIQUE_9, LauncherFontWidthMode.PROPORTIONAL),
        )
        assertEquals(
            listOf(PixelFontSize.PX_13),
            PixelFontCatalog.fontSizeOptions(LauncherFontFamily.DOTTED, LauncherFontWidthMode.PROPORTIONAL),
        )
        assertEquals(
            listOf(PixelFontSize.PX_16),
            PixelFontCatalog.fontSizeOptions(LauncherFontFamily.GNU_UNIFONT, LauncherFontWidthMode.MONOSPACED),
        )
        assertEquals(
            listOf(PixelFontSize.PX_12),
            PixelFontCatalog.fontSizeOptions(LauncherFontFamily.PIX32, LauncherFontWidthMode.MONOSPACED),
        )
        assertEquals(
            listOf(LauncherFontWidthMode.PROPORTIONAL),
            PixelFontCatalog.widthModeOptions(LauncherFontFamily.CUBIC_11),
        )
        assertEquals(
            listOf(LauncherFontWidthMode.MONOSPACED),
            PixelFontCatalog.widthModeOptions(LauncherFontFamily.PIX32),
        )
    }

    /** 不支持的设置必须恢复到 catalog 明确默认 face，不能近似字号。 */
    @Test
    fun normalize_keepsFamilyAndWidthMode() {
        /** Ark Mono 8px 不存在，应恢复为家族默认 Prop 10px。 */
        val normalized = PixelFontCatalog.normalize(
            LauncherFontSelection(
                family = LauncherFontFamily.ARK,
                widthMode = LauncherFontWidthMode.MONOSPACED,
                size = PixelFontSize.PX_8,
            ),
        )

        assertEquals(LauncherFontFamily.ARK, normalized.family)
        assertEquals(LauncherFontWidthMode.PROPORTIONAL, normalized.widthMode)
        assertEquals(PixelFontSize.PX_10, normalized.size)
        assertTrue(PixelFontCatalog.supports(normalized))
    }

    /** 度量标签必须来自完整 face，而不是脱离家族的全局字号。 */
    @Test
    fun metricsLabel_formatsCellBaselineAndAdvance() {
        assertEquals("C10 B8 A5/10", PixelFontCatalog.metricsLabel(
            LauncherFontSelection(LauncherFontFamily.ARK, LauncherFontWidthMode.PROPORTIONAL, PixelFontSize.PX_10),
        ))
        assertEquals("C14 B10 A9/13", PixelFontCatalog.metricsLabel(
            LauncherFontSelection(LauncherFontFamily.DOTTED, LauncherFontWidthMode.PROPORTIONAL, PixelFontSize.PX_13),
        ))
    }

    /** 完整选择应使用对应官方变体的基线和窄字符宽度。 */
    @Test
    fun metrics_usesSelectedFamilyAndWidthVariant() {
        /** Ark 10px 比例变体为保护常用下行笔画而使用第 8 行基线。 */
        val arkProportional = PixelFontCatalog.metrics(
            LauncherFontSelection(
                family = LauncherFontFamily.ARK,
                widthMode = LauncherFontWidthMode.PROPORTIONAL,
                size = PixelFontSize.PX_10,
            ),
        )
        /** Fusion 12px 等宽包使用自身清单中的第 10 行基线。 */
        val fusionMonospaced = PixelFontCatalog.metrics(
            LauncherFontSelection(
                family = LauncherFontFamily.FUSION,
                widthMode = LauncherFontWidthMode.MONOSPACED,
                size = PixelFontSize.PX_12,
            ),
        )

        assertEquals(PixelFontMetrics(PixelFontSize.PX_10, 10, 8, 5, 10), arkProportional)
        assertEquals(PixelFontMetrics(PixelFontSize.PX_12, 12, 10, 6, 12), fusionMonospaced)
    }

    /** 每个字体都应提供同家族 10px chrome 资源，但不把它混入原生 SIZE 选项。 */
    @Test
    fun renderableSelections_includePrivateChromeSizeForEveryFamily() {
        LauncherFontFamily.entries.forEach { family ->
            PixelFontCatalog.widthModeOptions(family).forEach { widthMode ->
                assertEquals(
                    PixelFontSize.PX_10,
                    PixelFontCatalog.selectionForRole(family, widthMode, LauncherTextRole.CHROME_10).size,
                )
            }
        }
        assertEquals(
            listOf(PixelFontSize.PX_12),
            PixelFontCatalog.fontSizeOptions(LauncherFontFamily.PIX32, LauncherFontWidthMode.MONOSPACED),
        )
        assertEquals(
            PixelFontMetrics(PixelFontSize.PX_12, 14, 11, 6, 12),
            PixelFontCatalog.metrics(
                LauncherFontSelection(
                    LauncherFontFamily.PIX32,
                    LauncherFontWidthMode.MONOSPACED,
                    PixelFontSize.PX_12,
                ),
            ),
        )
    }
}
