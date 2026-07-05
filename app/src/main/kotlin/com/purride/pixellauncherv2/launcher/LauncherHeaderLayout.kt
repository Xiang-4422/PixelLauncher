package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.GlyphStyle
import com.purride.pixellauncherv2.render.ScreenProfile
import kotlin.math.max

object LauncherHeaderLayout {

    const val horizontalPadding = LauncherSpacing.CONTENT_HORIZONTAL
    const val dividerGap = 0
    const val dividerHeight = 1

    val rowY: Int
        get() = 0

    val textOffsetY: Int
        get() = 0

    val headerTextY: Int
        get() = rowY + textOffsetY

    val dividerY: Int
        get() = headerTextY + GlyphStyle.UI_SMALL_10.cellHeight + dividerGap

    val headerContentHeight: Int
        get() = GlyphStyle.UI_SMALL_10.cellHeight + dividerGap + dividerHeight

    val defaultStatusBarHeight: Int
        get() = headerContentHeight

    fun statusBarHeight(screenProfile: ScreenProfile): Int {
        return max(screenProfile.statusBarHeight, defaultStatusBarHeight)
    }

    val contentTop: Int
        get() = defaultStatusBarHeight

    fun contentTop(screenProfile: ScreenProfile): Int = statusBarHeight(screenProfile)

    val firstContentItemTop: Int
        get() = contentTop + LauncherSpacing.CONTENT_VERTICAL

    fun firstContentItemTop(screenProfile: ScreenProfile): Int {
        return contentTop(screenProfile) + LauncherSpacing.CONTENT_VERTICAL
    }

    val titleGap: Int
        get() = max(2, GlyphStyle.UI_SMALL_10.narrowAdvanceWidth / 2)
}
