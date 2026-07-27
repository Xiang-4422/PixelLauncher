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

    /** 与引擎 DrawerScreen 渲染行距一致（单一来源 [DrawerListGeometry]）。 */
    private val rowHeight: Int
        get() = DrawerListGeometry.rowPitch(PixelFontCatalog.defaultUiFontSize.px)

    fun visibleRows(screenProfile: LauncherLayoutProfile): Int {
        val listStartY = LauncherHeaderLayout.firstContentItemTop(screenProfile)
        val railHeight = (screenProfile.logicalHeight - listStartY - bottomPadding).coerceAtLeast(rowHeight)
        return TextListSupport.createLayoutMetrics(
            top = listStartY,
            bottomExclusive = listStartY + railHeight,
            rowHeight = rowHeight,
        ).viewport.visibleRows
    }
}
