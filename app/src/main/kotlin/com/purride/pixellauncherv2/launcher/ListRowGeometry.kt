package com.purride.pixellauncherv2.launcher

/**
 * 设置页与短信会话列表的**固定**行几何（单一来源）。
 *
 * 与抽屉不同（[DrawerListGeometry] 是字号派生），这两个列表的行高是字号无关的固定值：
 * 引擎 screen（`SettingsScreen` / `SmsThreadsScreen`）按固定 `itemExtent` 渲染，状态机
 * 的 `visibleRows`（`SettingsMenuLayout` / `SmsLayout`）必须用同一套行距，否则会高估
 * 可见行数、让选择 / 滚动窗口与实际渲染错位。
 */
object SettingsListGeometry {
    /** 单行内容高度（即 ListViewBuilder 的 itemExtent）。 */
    const val ROW_EXTENT_PX = 25

    /** 行与行之间的间距像素。 */
    const val ROW_SPACING_PX = 1

    /** 行距（item + spacing）；状态机据此把视口高度换算成可见行数。 */
    const val ROW_PITCH_PX = ROW_EXTENT_PX + ROW_SPACING_PX
}

object SmsThreadGeometry {
    /** 单行内容高度（顶部地址行 + 底部摘要行）。 */
    const val ROW_EXTENT_PX = 22

    /** 行与行之间的间距像素。 */
    const val ROW_SPACING_PX = 1

    /** 行距（item + spacing）；状态机据此把视口高度换算成可见行数。 */
    const val ROW_PITCH_PX = ROW_EXTENT_PX + ROW_SPACING_PX
}
