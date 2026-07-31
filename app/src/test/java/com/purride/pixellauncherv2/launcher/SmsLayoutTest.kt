package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.layout.LauncherLayoutProfile
import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证短信列表的可见行数使用当前字体对应的动态状态栏起点。 */
class SmsLayoutTest {

    @Test
    fun threadVisibleRows_usesCurrentChromeHeaderHeight() {
        val profile = LauncherLayoutProfile(120, 240, 6)
        val unifont = PixelFontCatalog.selectionForRole(
            family = LauncherFontFamily.GNU_UNIFONT,
            widthMode = LauncherFontWidthMode.MONOSPACED,
            role = LauncherTextRole.CHROME,
        )
        val rowHeight = SmsThreadGeometry.rowPitch(unifont)
        val top = LauncherHeaderLayout.firstContentItemTop(profile, unifont)
        val bottomExclusive = (profile.logicalHeight - 2).coerceAtLeast(top + rowHeight)
        val expected = ((bottomExclusive - top) / rowHeight).coerceAtLeast(1)

        assertEquals(expected, SmsLayout.threadVisibleRows(profile, unifont))
    }
}
