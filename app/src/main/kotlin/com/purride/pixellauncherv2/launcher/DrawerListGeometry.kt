package com.purride.pixellauncherv2.launcher

/**
 * 抽屉应用列表的单一行几何来源。
 *
 * 引擎渲染（[com.purride.pixellauncherv2.ui.screen.DrawerScreen] 的 ListViewBuilder）
 * 与状态机的可见行数计算（[AppListLayout] → `visibleRows`）必须共用同一套行高，
 * 否则状态机会高估可见行数，导致选择 / 滚动窗口与实际渲染错位。
 */
object DrawerListGeometry {

    /** 单行内容高度在字号基础上额外增加的像素。 */
    const val ROW_EXTENT_PADDING_PX = 5

    /** 行与行之间的间距像素。 */
    const val ROW_SPACING_PX = 1

    /** 单行内容高度（即 ListViewBuilder 的 itemExtent）。 */
    fun rowExtent(fontPx: Int): Int = fontPx + ROW_EXTENT_PADDING_PX

    /**
     * 行距（item + spacing）。把视口高度换算成可见行数时用这个，
     * 才能与引擎实际渲染的行排布一致。
     */
    fun rowPitch(fontPx: Int): Int = rowExtent(fontPx) + ROW_SPACING_PX
}
