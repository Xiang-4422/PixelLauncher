package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.layout.LauncherLayoutProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证共享 Chrome 边框会随各家族真实 CHROME face 高度变化。 */
class LauncherChromeLayoutTest {

    /** 测试矩阵中的当前设置选择；该家族的 CHROME face 由生产目录精确解析。 */
    private data class Case(
        val family: LauncherFontFamily,
        val widthMode: LauncherFontWidthMode,
        val expectedCellHeight: Int,
    )

    @Test
    fun geometry_coversAllShippedChromeCellHeights() {
        val cases = listOf(
            Case(LauncherFontFamily.BOUTIQUE_7, LauncherFontWidthMode.PROPORTIONAL, 8),
            Case(LauncherFontFamily.FUSION, LauncherFontWidthMode.MONOSPACED, 10),
            Case(LauncherFontFamily.BOUTIQUE_9, LauncherFontWidthMode.PROPORTIONAL, 11),
            Case(LauncherFontFamily.PIX32, LauncherFontWidthMode.MONOSPACED, 12),
            Case(LauncherFontFamily.FUSION, LauncherFontWidthMode.PROPORTIONAL, 14),
            Case(LauncherFontFamily.GNU_UNIFONT, LauncherFontWidthMode.MONOSPACED, 16),
        )

        cases.forEach { case ->
            val selection = PixelFontCatalog.selectionForRole(
                family = case.family,
                widthMode = case.widthMode,
                role = LauncherTextRole.CHROME,
            )
            val geometry = LauncherChromeLayout.geometry(selection)
            val expectedSegmentHeight = maxOf(11, case.expectedCellHeight)

            assertEquals(case.expectedCellHeight, geometry.textHeightPx)
            assertEquals(expectedSegmentHeight, geometry.segmentHeightPx)
            assertEquals(
                expectedSegmentHeight + LauncherChromeLayout.sharedBorderPx * 2,
                geometry.rowHeightPx,
            )
            assertTrue("文字单元不得高于边框内部", geometry.segmentHeightPx >= geometry.textHeightPx)
        }
    }

    @Test
    fun headerAndContentTop_followCurrentChromeFace() {
        val profile = LauncherLayoutProfile(
            logicalWidth = 120,
            logicalHeight = 240,
            dotSizePx = 6,
            statusBarHeight = 0,
        )
        val small = PixelFontCatalog.selectionForRole(
            family = LauncherFontFamily.BOUTIQUE_7,
            widthMode = LauncherFontWidthMode.PROPORTIONAL,
            role = LauncherTextRole.CHROME,
        )
        val unifont = PixelFontCatalog.selectionForRole(
            family = LauncherFontFamily.GNU_UNIFONT,
            widthMode = LauncherFontWidthMode.MONOSPACED,
            role = LauncherTextRole.CHROME,
        )

        assertEquals(14, LauncherHeaderLayout.statusBarHeight(profile, small))
        assertEquals(19, LauncherHeaderLayout.statusBarHeight(profile, unifont))
        assertEquals(20, LauncherChromeLayout.geometry(unifont).bottomRegionHeight(2))
        assertEquals(
            LauncherHeaderLayout.statusBarHeight(profile, unifont) + LauncherSpacing.CONTENT_VERTICAL,
            LauncherHeaderLayout.firstContentItemTop(profile, unifont),
        )
    }
}
