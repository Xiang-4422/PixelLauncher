package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.GlyphStyle
import com.purride.pixellauncherv2.render.ScreenProfile

/**
 * 设置页 / 短信收件箱的视口行数计算（状态机用）。
 *
 * 设置列表行高与引擎 SettingsScreen 共用固定 [SettingsListGeometry]；短信收件箱是横向
 * pager（一条一页），[largeVisibleRows] 仅作非可视的选择 clamp。
 */
object SettingsMenuLayout {

    private const val panelBottomPadding = 4
    private const val rowGap = 2

    /** 设置列表固定行距（单一来源 [SettingsListGeometry]）。 */
    private val rowHeight: Int
        get() = SettingsListGeometry.ROW_PITCH_PX

    private val largeRowHeight: Int
        get() = (GlyphStyle.APP_LABEL_16.cellHeight * 2) + rowGap

    fun visibleRows(screenProfile: ScreenProfile): Int = computeVisibleRows(screenProfile, rowHeight)

    fun largeVisibleRows(screenProfile: ScreenProfile): Int = computeVisibleRows(screenProfile, largeRowHeight)

    private fun computeVisibleRows(screenProfile: ScreenProfile, listRowHeight: Int): Int {
        val panelTop = LauncherHeaderLayout.firstContentItemTop
        val panelBottom = (screenProfile.logicalHeight - panelBottomPadding).coerceAtLeast(panelTop + 24)
        return TextListSupport.createLayoutMetrics(
            top = panelTop,
            bottomExclusive = panelBottom,
            rowHeight = listRowHeight,
        ).viewport.visibleRows
    }
}
