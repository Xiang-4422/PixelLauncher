package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.layout.LauncherLayoutProfile

/**
 * 抽屉应用列表的视口行数计算（状态机用）。
 *
 * 渲染由 [com.purride.pixellauncherv2.ui.screen.DrawerScreen] 负责；本对象只把屏幕几何
 * 换算成可见行数，行高与渲染共用 [DrawerListGeometry]，保证两侧一致。
 */
object AppListLayout {

    private const val bottomPadding = 0

    /** 按当前字体字号计算与 DrawerScreen 一致的可见行数。 */
    fun visibleRows(
        screenProfile: LauncherLayoutProfile,
        fontSelection: LauncherFontSelection = PixelFontCatalog.defaultUiFontSelection,
    ): Int {
        /** 与引擎 DrawerScreen 渲染行距一致的当前行高。 */
        val rowHeight = DrawerListGeometry.rowPitch(PixelFontCatalog.metrics(fontSelection).cellHeight)
        val listStartY = LauncherHeaderLayout.firstContentItemTop(screenProfile)
        val railHeight = (screenProfile.logicalHeight - listStartY - bottomPadding).coerceAtLeast(rowHeight)
        return TextListSupport.createLayoutMetrics(
            top = listStartY,
            bottomExclusive = listStartY + railHeight,
            rowHeight = rowHeight,
        ).viewport.visibleRows
    }
}
