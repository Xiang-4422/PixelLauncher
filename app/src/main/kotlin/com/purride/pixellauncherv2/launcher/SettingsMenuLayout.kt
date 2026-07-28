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

    /** 设置列表估算行距（单一来源 [SettingsListGeometry]）。 */
    private val rowHeight: Int
        get() = SettingsListGeometry.ROW_PITCH_PX

    fun visibleRows(
        screenProfile: LauncherLayoutProfile,
        fontSelection: LauncherFontSelection = PixelFontCatalog.defaultUiFontSelection,
    ): Int = computeVisibleRows(
        screenProfile = screenProfile,
        listRowHeight = maxOf(rowHeight, PixelFontCatalog.metrics(fontSelection).cellHeight + rowGap),
    )

    fun largeVisibleRows(
        screenProfile: LauncherLayoutProfile,
        fontSelection: LauncherFontSelection = PixelFontCatalog.defaultUiFontSelection,
    ): Int = computeVisibleRows(
        screenProfile = screenProfile,
        listRowHeight = (PixelFontCatalog.metrics(fontSelection).cellHeight * 2) + rowGap,
    )

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
