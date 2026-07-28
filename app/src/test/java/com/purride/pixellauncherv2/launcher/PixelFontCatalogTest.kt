package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证字体能力矩阵、默认选择和不跨家族归一化规则。 */
class PixelFontCatalogTest {

    /** 字号标签应使用稳定的 PX 表示。 */
    @Test
    fun sizeLabel_mapsPixelSizes() {
        assertEquals("7PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_7))
        assertEquals("8PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_8))
        assertEquals("9PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_9))
        assertEquals("10PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_10))
        assertEquals("11PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_11))
        assertEquals("12PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_12))
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

    /** 不支持的字号应在所选字体族和宽度模式内选择最近值。 */
    @Test
    fun normalize_keepsFamilyAndWidthMode() {
        /** Ark Mono 8px 不存在，应收敛为同字体同模式的 10px。 */
        val normalized = PixelFontCatalog.normalize(
            LauncherFontSelection(
                family = LauncherFontFamily.ARK,
                widthMode = LauncherFontWidthMode.MONOSPACED,
                size = PixelFontSize.PX_8,
            ),
        )

        assertEquals(LauncherFontFamily.ARK, normalized.family)
        assertEquals(LauncherFontWidthMode.MONOSPACED, normalized.widthMode)
        assertEquals(PixelFontSize.PX_10, normalized.size)
        assertTrue(PixelFontCatalog.supports(normalized))
    }

    /** 基础布局度量应覆盖新增的 16px 原生字号。 */
    @Test
    fun metricsLabel_formatsCellBaselineAndAdvance() {
        assertEquals("C7 B6 A4/7", PixelFontCatalog.metricsLabel(PixelFontSize.PX_7))
        assertEquals("C8 B7 A4/8", PixelFontCatalog.metricsLabel(PixelFontSize.PX_8))
        assertEquals("C9 B8 A5/9", PixelFontCatalog.metricsLabel(PixelFontSize.PX_9))
        assertEquals("C10 B9 A6/10", PixelFontCatalog.metricsLabel(PixelFontSize.PX_10))
        assertEquals("C11 B10 A6/11", PixelFontCatalog.metricsLabel(PixelFontSize.PX_11))
        assertEquals("C12 B11 A8/12", PixelFontCatalog.metricsLabel(PixelFontSize.PX_12))
        assertEquals("C16 B15 A8/16", PixelFontCatalog.metricsLabel(PixelFontSize.PX_16))
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
        assertEquals(PixelFontMetrics(PixelFontSize.PX_12, 12, 10, 12, 12), fusionMonospaced)
    }

    /** 每个字体都应提供同家族 10px chrome 资源，但不把它混入原生 SIZE 选项。 */
    @Test
    fun renderableSelections_includePrivateChromeSizeForEveryFamily() {
        LauncherFontFamily.entries.forEach { family ->
            assertTrue(
                "missing 10px chrome resource for $family",
                PixelFontCatalog.renderableSelections().any { selection ->
                    selection.family == family && selection.size == PixelFontSize.PX_10
                },
            )
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
