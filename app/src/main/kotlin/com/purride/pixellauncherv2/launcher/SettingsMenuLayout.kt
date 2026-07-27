package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.layout.LauncherLayoutProfile

/**
 * 设置页 / 短信收件箱的视口行数估算（状态机用）。
 *
 * SettingsScreen 使用普通 ListView 让行高按内容自适应；这里仍用 [SettingsListGeometry]
 * 的估算行距做非可视的 selection/window clamp。短信收件箱是横向 pager（一条一页），
 * [largeVisibleRows] 也仅作非可视的选择 clamp。
 */
object SettingsMenuLayout {

    private const val panelBottomPadding = LauncherSpacing.CONTENT_VERTICAL
    private const val rowGap = LauncherSpacing.ROW_SPACING

    /** 默认 UI 字号的不可变指标，避免每次访问都重新计算。 */
    private val uiFontMetrics = PixelFontCatalog.metrics(PixelFontCatalog.defaultUiFontSize)

    /** 设置列表估算行距（单一来源 [SettingsListGeometry]）。 */
    private val rowHeight: Int
        get() = SettingsListGeometry.ROW_PITCH_PX

    private val largeRowHeight: Int
        get() = (uiFontMetrics.cellHeight * 2) + rowGap

    fun visibleRows(screenProfile: LauncherLayoutProfile): Int = computeVisibleRows(screenProfile, rowHeight)

    fun largeVisibleRows(screenProfile: LauncherLayoutProfile): Int = computeVisibleRows(screenProfile, largeRowHeight)

    private fun computeVisibleRows(screenProfile: LauncherLayoutProfile, listRowHeight: Int): Int {
        val panelTop = LauncherHeaderLayout.firstContentItemTop(screenProfile)
        val panelBottom = (screenProfile.logicalHeight - panelBottomPadding).coerceAtLeast(panelTop + 24)
        return TextListSupport.createLayoutMetrics(
            top = panelTop,
            bottomExclusive = panelBottom,
            rowHeight = listRowHeight,
        ).viewport.visibleRows
    }
}
