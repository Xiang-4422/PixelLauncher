package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.layout.LauncherLayoutProfile
import kotlin.math.max

object LauncherHeaderLayout {

    const val horizontalPadding = LauncherSpacing.CONTENT_HORIZONTAL
    const val dividerGap = 0
    const val dividerHeight = 1

    /** 默认 UI 字号的不可变指标，避免每次访问都重新计算。 */
    private val uiFontMetrics = PixelFontCatalog.metrics(PixelFontCatalog.defaultUiFontSize)

    val rowY: Int
        get() = 0

    val textOffsetY: Int
        get() = 0

    val headerTextY: Int
        get() = rowY + textOffsetY

    val dividerY: Int
        get() = headerTextY + uiFontMetrics.cellHeight + dividerGap

    val headerContentHeight: Int
        get() = uiFontMetrics.cellHeight + dividerGap + dividerHeight

    val defaultStatusBarHeight: Int
        get() = headerContentHeight

    fun statusBarHeight(screenProfile: LauncherLayoutProfile): Int {
        return max(screenProfile.statusBarHeight, defaultStatusBarHeight)
    }

    val contentTop: Int
        get() = defaultStatusBarHeight

    fun contentTop(screenProfile: LauncherLayoutProfile): Int = statusBarHeight(screenProfile)

    val firstContentItemTop: Int
        get() = contentTop + LauncherSpacing.CONTENT_VERTICAL

    fun firstContentItemTop(screenProfile: LauncherLayoutProfile): Int {
        return contentTop(screenProfile) + LauncherSpacing.CONTENT_VERTICAL
    }

    val titleGap: Int
        get() = max(2, uiFontMetrics.narrowAdvanceWidth / 2)
}
