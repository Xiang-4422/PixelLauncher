package com.purride.pixellauncherv2.launcher

/**
 * 设置页与短信会话列表的行几何（单一来源）。
 *
 * 与抽屉不同（[DrawerListGeometry] 是字号派生），这里的基础行距不随字体设置变化。
 * `SettingsScreen` 使用该值作为滚动估算，短信会话列表仍按固定 itemExtent 渲染；状态机
 * 的 `visibleRows`（`SettingsMenuLayout` / `SmsLayout`）继续用同一套行距做选择 / 滚动窗口
 * 估算，避免列表状态明显错位。
 */
object SettingsListGeometry {
    /** 单行内容高度估算；短信列表使用为固定 itemExtent。 */
    const val ROW_EXTENT_PX = 25

    /** 行与行之间的间距像素。 */
    const val ROW_SPACING_PX = LauncherSpacing.ROW_SPACING

    /** 行距（item + spacing）；状态机据此把视口高度换算成可见行数。 */
    const val ROW_PITCH_PX = ROW_EXTENT_PX + ROW_SPACING_PX
}

object SmsThreadGeometry {
    /** 单行内容高度（顶部地址行 + 底部摘要行 + 稳定内边距）。 */
    const val ROW_EXTENT_PX = 25

    /** 行与行之间的间距像素。 */
    const val ROW_SPACING_PX = 1

    /** 行距（item + spacing）；状态机据此把视口高度换算成可见行数。 */
    const val ROW_PITCH_PX = ROW_EXTENT_PX + ROW_SPACING_PX
}
