package com.purride.pixellauncherv2.launcher

/** 当前 CHROME face 驱动的共享边框几何。 */
data class LauncherChromeGeometry(
    /** CHROME 字形单元的完整高度。 */
    val textHeightPx: Int,
    /** 边框内部可供文字绘制的段高度。 */
    val segmentHeightPx: Int,
    /** 包含上下边框的整行高度。 */
    val rowHeightPx: Int,
) {
    /** 返回整行与页面底部留白共同占用的触控区域高度。 */
    fun bottomRegionHeight(bottomInsetPx: Int): Int = rowHeightPx + bottomInsetPx.coerceAtLeast(0)
}

/** 状态栏与 HOME 底栏共享的字体自适应几何入口。 */
object LauncherChromeLayout {
    /** 所有 Chrome 边框统一使用的一像素线宽。 */
    const val sharedBorderPx = 1
    /** 媒体图标与既有紧凑视觉所需的最小内部高度。 */
    private const val minimumSegmentHeightPx = 11

    /** 按当前家族和宽度模式的精确 CHROME face 计算共享高度。 */
    fun geometry(fontSelection: LauncherFontSelection): LauncherChromeGeometry {
        /** 当前设置对应的同家族、同宽度 CHROME face。 */
        val chromeSelection = PixelFontCatalog.selectionForRole(
            family = fontSelection.family,
            widthMode = fontSelection.widthMode,
            role = LauncherTextRole.CHROME,
        )
        /** catalog 记录的真实字形单元高度。 */
        val textHeight = PixelFontCatalog.metrics(chromeSelection).cellHeight
        /** 文字与固定媒体图标共同需要的边框内部高度。 */
        val segmentHeight = maxOf(minimumSegmentHeightPx, textHeight)
        return LauncherChromeGeometry(
            textHeightPx = textHeight,
            segmentHeightPx = segmentHeight,
            rowHeightPx = segmentHeight + sharedBorderPx * 2,
        )
    }
}
