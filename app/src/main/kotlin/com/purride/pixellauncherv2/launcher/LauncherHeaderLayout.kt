package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.layout.LauncherLayoutProfile
import kotlin.math.max

/** 状态栏占位与页面内容起点的字体自适应布局规则。 */
object LauncherHeaderLayout {

    /** 状态栏文字与页面内容共用的水平边距。 */
    const val horizontalPadding = LauncherSpacing.CONTENT_HORIZONTAL
    /** 边框行与电量线之间不额外留缝。 */
    const val dividerGap = 0
    /** 电量分隔线固定为一逻辑像素。 */
    const val dividerHeight = 1

    /** 状态栏边框行加电量分隔线的完整内容高度。 */
    fun headerContentHeight(fontSelection: LauncherFontSelection): Int {
        return LauncherChromeLayout.geometry(fontSelection).rowHeightPx + dividerGap + dividerHeight
    }

    /** 系统栏占位与当前 CHROME 内容高度取较大值，避免字体或边框被裁切。 */
    fun statusBarHeight(
        screenProfile: LauncherLayoutProfile,
        fontSelection: LauncherFontSelection,
    ): Int = max(screenProfile.statusBarHeight, headerContentHeight(fontSelection))

    /** 返回状态栏之后的页面内容起点。 */
    fun contentTop(
        screenProfile: LauncherLayoutProfile,
        fontSelection: LauncherFontSelection,
    ): Int = statusBarHeight(screenProfile, fontSelection)

    /** 返回带共享页面上边距的首项位置。 */
    fun firstContentItemTop(
        screenProfile: LauncherLayoutProfile,
        fontSelection: LauncherFontSelection,
    ): Int {
        return contentTop(screenProfile, fontSelection) + LauncherSpacing.CONTENT_VERTICAL
    }

    /** 按当前 CHROME face 的窄字符宽度计算标题间距。 */
    fun titleGap(fontSelection: LauncherFontSelection): Int {
        val chromeSelection = PixelFontCatalog.selectionForRole(
            family = fontSelection.family,
            widthMode = fontSelection.widthMode,
            role = LauncherTextRole.CHROME,
        )
        return max(2, PixelFontCatalog.metrics(chromeSelection).narrowAdvanceWidth / 2)
    }
}
