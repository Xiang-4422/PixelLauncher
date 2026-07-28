package com.purride.pixellauncherv2.launcher

/**
 * 设置页与短信会话列表的行几何（单一来源）。
 *
 * 设置列表按内容自适应；短信会话列表则通过 [SmsThreadGeometry] 按当前字号派生高度。
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
    /** 10px 默认字体对应的兼容行高。 */
    const val ROW_EXTENT_PX = 25

    /** 行与行之间的间距像素。 */
    const val ROW_SPACING_PX = 1

    /** 行距（item + spacing）；状态机据此把视口高度换算成可见行数。 */
    const val ROW_PITCH_PX = ROW_EXTENT_PX + ROW_SPACING_PX

    /** 两行文本之外保留的固定上下内边距。 */
    private const val VERTICAL_PADDING_PX = 5

    /** 根据当前原生字号返回短信会话的两行内容高度。 */
    fun rowExtent(fontSelection: LauncherFontSelection): Int =
        maxOf(ROW_EXTENT_PX, PixelFontCatalog.metrics(fontSelection).cellHeight * 2 + VERTICAL_PADDING_PX)

    /** 根据当前原生字号返回包含列表间距的行距。 */
    fun rowPitch(fontSelection: LauncherFontSelection): Int = rowExtent(fontSelection) + ROW_SPACING_PX
}
