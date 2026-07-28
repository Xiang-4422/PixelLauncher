package com.purride.pixellauncherv2.ui.widget

import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.TextAlign
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证 drawer 搜索 hint 与应用列表共享同一视觉内容边界。 */
class LauncherSearchHeaderTest {

    /** 左对齐时扣除大写 S 的真实左侧空白，使可见墨迹从 drawer 内容边界开始。 */
    @Test
    fun searchRowPadding_startAlignment_compensatesPlaceholderInkInset() {
        /** 当前 10px Fusion Pixel 大写 S 的左侧空白像素数。 */
        val placeholderLeadingInkInset = 2
        /** 根据 drawer 内容边界得到的搜索行留白。 */
        val padding = searchRowPadding(
            textAlign = TextAlign.START,
            placeholderLeadingInkInset = placeholderLeadingInkInset,
        )

        assertEquals(LauncherSpacing.CONTENT_HORIZONTAL, padding.left + placeholderLeadingInkInset)
        assertEquals(LauncherSpacing.CONTENT_HORIZONTAL, padding.right)
    }

    /** 居中和右对齐不依赖首字形左边距，应保持与 drawer 相同的对称边界。 */
    @Test
    fun searchRowPadding_centerAndEndAlignment_keepDrawerInsets() {
        /** drawer 内容区域对应的完整对称留白。 */
        val expectedPadding = EdgeInsets(
            left = LauncherSpacing.CONTENT_HORIZONTAL,
            top = 1,
            right = LauncherSpacing.CONTENT_HORIZONTAL,
            bottom = 1,
        )

        assertEquals(expectedPadding, searchRowPadding(TextAlign.CENTER, placeholderLeadingInkInset = 2))
        assertEquals(expectedPadding, searchRowPadding(TextAlign.END, placeholderLeadingInkInset = 2))
    }
}
