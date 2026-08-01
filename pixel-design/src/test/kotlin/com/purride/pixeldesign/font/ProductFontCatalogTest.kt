package com.purride.pixeldesign.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证字体能力矩阵、默认选择和不跨家族归一化规则。 */
class ProductFontCatalogTest {

    /** 三个低分辨率矢量点形应合并为唯一、可逐点验证的 Dotted 家族。 */
    @Test
    fun dotted_exposesOnlyCanonicalPointGridFamily() {
        assertEquals(8, ProductFontCatalog.fontFamilyOptions().size)
        assertEquals(1, ProductFontCatalog.fontFamilyOptions().count { family -> family == ProductFontFamily.DOTTED })
        assertEquals("DOTTED", ProductFontCatalog.familyLabel(ProductFontFamily.DOTTED))
        assertEquals(
            ProductFontMetrics(ProductFontSize.PX_13, 14, 10, 9, 13),
            ProductFontCatalog.metrics(
                ProductFontSelection(
                    ProductFontFamily.DOTTED,
                    ProductFontWidthMode.PROPORTIONAL,
                    ProductFontSize.PX_13,
                ),
            ),
        )
    }

    /** 字号标签应使用稳定的 PX 表示。 */
    @Test
    fun sizeLabel_mapsPixelSizes() {
        assertEquals("7PX", ProductFontCatalog.sizeLabel(ProductFontSize.PX_7))
        assertEquals("8PX", ProductFontCatalog.sizeLabel(ProductFontSize.PX_8))
        assertEquals("9PX", ProductFontCatalog.sizeLabel(ProductFontSize.PX_9))
        assertEquals("10PX", ProductFontCatalog.sizeLabel(ProductFontSize.PX_10))
        assertEquals("11PX", ProductFontCatalog.sizeLabel(ProductFontSize.PX_11))
        assertEquals("12PX", ProductFontCatalog.sizeLabel(ProductFontSize.PX_12))
        assertEquals("13PX", ProductFontCatalog.sizeLabel(ProductFontSize.PX_13))
        assertEquals("16PX", ProductFontCatalog.sizeLabel(ProductFontSize.PX_16))
    }

    /** 诊断页字号选项应覆盖全部原生字号。 */
    @Test
    fun options_exposeAllEnumEntries() {
        assertEquals(ProductFontSize.entries.toList(), ProductFontCatalog.fontSizeOptions())
    }

    /** 缺失设置时应继续使用 Fusion Prop 10px。 */
    @Test
    fun default_isFusionProportional10px() {
        assertEquals(
            ProductFontSelection(
                family = ProductFontFamily.FUSION,
                widthMode = ProductFontWidthMode.PROPORTIONAL,
                size = ProductFontSize.PX_10,
            ),
            ProductFontCatalog.defaultUiFontSelection,
        )
    }

    /** 每个字体族只应暴露自身真正存在的字号。 */
    @Test
    fun fontSizes_followSelectedFamilyCapabilities() {
        ProductFontWidthMode.entries.forEach { widthMode ->
            assertEquals(
                listOf(ProductFontSize.PX_8, ProductFontSize.PX_10, ProductFontSize.PX_12),
                ProductFontCatalog.fontSizeOptions(ProductFontFamily.FUSION, widthMode),
            )
            assertEquals(
                listOf(ProductFontSize.PX_10, ProductFontSize.PX_12, ProductFontSize.PX_16),
                ProductFontCatalog.fontSizeOptions(ProductFontFamily.ARK, widthMode),
            )
        }
        assertEquals(
            listOf(ProductFontSize.PX_12),
            ProductFontCatalog.fontSizeOptions(ProductFontFamily.CUBIC_11, ProductFontWidthMode.PROPORTIONAL),
        )
        assertEquals(
            listOf(ProductFontSize.PX_8),
            ProductFontCatalog.fontSizeOptions(ProductFontFamily.BOUTIQUE_7, ProductFontWidthMode.PROPORTIONAL),
        )
        assertEquals(
            listOf(ProductFontSize.PX_10),
            ProductFontCatalog.fontSizeOptions(ProductFontFamily.BOUTIQUE_9, ProductFontWidthMode.PROPORTIONAL),
        )
        assertEquals(
            listOf(ProductFontSize.PX_13),
            ProductFontCatalog.fontSizeOptions(ProductFontFamily.DOTTED, ProductFontWidthMode.PROPORTIONAL),
        )
        assertEquals(
            listOf(ProductFontSize.PX_16),
            ProductFontCatalog.fontSizeOptions(ProductFontFamily.GNU_UNIFONT, ProductFontWidthMode.MONOSPACED),
        )
        assertEquals(
            listOf(ProductFontSize.PX_12),
            ProductFontCatalog.fontSizeOptions(ProductFontFamily.PIX32, ProductFontWidthMode.MONOSPACED),
        )
        assertEquals(
            listOf(ProductFontWidthMode.PROPORTIONAL),
            ProductFontCatalog.widthModeOptions(ProductFontFamily.CUBIC_11),
        )
        assertEquals(
            listOf(ProductFontWidthMode.MONOSPACED),
            ProductFontCatalog.widthModeOptions(ProductFontFamily.PIX32),
        )
    }

    /** 不支持的设置必须恢复到 catalog 明确默认 face，不能近似字号。 */
    @Test
    fun normalize_keepsFamilyAndWidthMode() {
        /** Ark Mono 8px 不存在，应恢复为家族默认 Prop 10px。 */
        val normalized = ProductFontCatalog.normalize(
            ProductFontSelection(
                family = ProductFontFamily.ARK,
                widthMode = ProductFontWidthMode.MONOSPACED,
                size = ProductFontSize.PX_8,
            ),
        )

        assertEquals(ProductFontFamily.ARK, normalized.family)
        assertEquals(ProductFontWidthMode.PROPORTIONAL, normalized.widthMode)
        assertEquals(ProductFontSize.PX_10, normalized.size)
        assertTrue(ProductFontCatalog.supports(normalized))
    }

    /** 度量标签必须来自完整 face，而不是脱离家族的全局字号。 */
    @Test
    fun metricsLabel_formatsCellBaselineAndAdvance() {
        assertEquals("C14 B11 A5/10", ProductFontCatalog.metricsLabel(
            ProductFontSelection(ProductFontFamily.ARK, ProductFontWidthMode.PROPORTIONAL, ProductFontSize.PX_10),
        ))
        assertEquals("C14 B10 A9/13", ProductFontCatalog.metricsLabel(
            ProductFontSelection(ProductFontFamily.DOTTED, ProductFontWidthMode.PROPORTIONAL, ProductFontSize.PX_13),
        ))
    }

    /** 设置页字体信息必须同时显示原生字号、行框、基线和生产源类型。 */
    @Test
    fun fontInfoLabel_exposesNativeSourceType() {
        assertEquals(
            "10PX C14 B11 BDF",
            ProductFontCatalog.fontInfoLabel(
                ProductFontSelection(
                    ProductFontFamily.FUSION,
                    ProductFontWidthMode.PROPORTIONAL,
                    ProductFontSize.PX_10,
                ),
            ),
        )
        assertEquals(
            "12PX C14 B10 OUTLINE",
            ProductFontCatalog.fontInfoLabel(
                ProductFontSelection(
                    ProductFontFamily.CUBIC_11,
                    ProductFontWidthMode.PROPORTIONAL,
                    ProductFontSize.PX_12,
                ),
            ),
        )
    }

    /** 完整选择应使用对应官方变体的基线和窄字符宽度。 */
    @Test
    fun metrics_usesSelectedFamilyAndWidthVariant() {
        /** Ark 10px 比例变体使用官方 BDF 的 11/3 ascent/descent。 */
        val arkProportional = ProductFontCatalog.metrics(
            ProductFontSelection(
                family = ProductFontFamily.ARK,
                widthMode = ProductFontWidthMode.PROPORTIONAL,
                size = ProductFontSize.PX_10,
            ),
        )
        /** Fusion 12px 等宽包使用自身清单中的第 10 行基线。 */
        val fusionMonospaced = ProductFontCatalog.metrics(
            ProductFontSelection(
                family = ProductFontFamily.FUSION,
                widthMode = ProductFontWidthMode.MONOSPACED,
                size = ProductFontSize.PX_12,
            ),
        )

        assertEquals(ProductFontMetrics(ProductFontSize.PX_10, 14, 11, 5, 10), arkProportional)
        assertEquals(ProductFontMetrics(ProductFontSize.PX_12, 12, 10, 6, 12), fusionMonospaced)
    }

    /** 每个字体都应把 chrome 映射到同家族声明的原生 face。 */
    @Test
    fun renderableSelections_includePrivateChromeSizeForEveryFamily() {
        val expectedSizes = mapOf(
            ProductFontFamily.FUSION to ProductFontSize.PX_10,
            ProductFontFamily.ARK to ProductFontSize.PX_10,
            ProductFontFamily.CUBIC_11 to ProductFontSize.PX_12,
            ProductFontFamily.BOUTIQUE_7 to ProductFontSize.PX_8,
            ProductFontFamily.BOUTIQUE_9 to ProductFontSize.PX_10,
            ProductFontFamily.DOTTED to ProductFontSize.PX_13,
            ProductFontFamily.GNU_UNIFONT to ProductFontSize.PX_16,
            ProductFontFamily.PIX32 to ProductFontSize.PX_12,
        )
        ProductFontFamily.entries.forEach { family ->
            ProductFontCatalog.widthModeOptions(family).forEach { widthMode ->
                assertEquals(
                    expectedSizes.getValue(family),
                    ProductFontCatalog.selectionForRole(family, widthMode, ProductTextRole.CHROME).size,
                )
            }
        }
        assertEquals(
            listOf(ProductFontSize.PX_12),
            ProductFontCatalog.fontSizeOptions(ProductFontFamily.PIX32, ProductFontWidthMode.MONOSPACED),
        )
        assertEquals(
            ProductFontMetrics(ProductFontSize.PX_12, 12, 11, 6, 12),
            ProductFontCatalog.metrics(
                ProductFontSelection(
                    ProductFontFamily.PIX32,
                    ProductFontWidthMode.MONOSPACED,
                    ProductFontSize.PX_12,
                ),
            ),
        )
    }
}
