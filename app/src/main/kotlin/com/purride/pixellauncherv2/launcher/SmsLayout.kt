package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.layout.LauncherLayoutProfile

/**
 * 短信会话列表的视口行数计算（状态机用）。行高与引擎 SmsThreadsScreen 共用
 * [SmsThreadGeometry] 的字号派生规则，保证两侧一致。
 */
object SmsLayout {

    private const val panelBottomPadding = 2

    /** 按当前字体字号计算可见的短信会话行数。 */
    fun threadVisibleRows(
        screenProfile: LauncherLayoutProfile,
        fontSelection: LauncherFontSelection = PixelFontCatalog.defaultUiFontSelection,
    ): Int {
        /** 与渲染列表完全一致的动态行距。 */
        val threadRowHeight = SmsThreadGeometry.rowPitch(fontSelection)
        val top = LauncherHeaderLayout.firstContentItemTop(screenProfile)
        val bottomExclusive = (screenProfile.logicalHeight - panelBottomPadding).coerceAtLeast(top + threadRowHeight)
        return TextListSupport.createLayoutMetrics(
            top = top,
            bottomExclusive = bottomExclusive,
            rowHeight = threadRowHeight,
        ).viewport.visibleRows
    }
}
