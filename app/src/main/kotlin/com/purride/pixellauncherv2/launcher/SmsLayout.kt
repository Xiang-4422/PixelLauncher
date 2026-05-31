package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.GlyphStyle
import com.purride.pixellauncherv2.render.ScreenProfile

object SmsLayout {

    private const val panelBottomPadding = 2
    private const val panelTextInsetX = 2
    private val composeBarHeight: Int
        get() = GlyphStyle.UI_SMALL_10.cellHeight + 4
    private const val composeBarBottomGap = 1
    private const val threadMetaGap = 1
    private val threadRowHeight: Int
        get() = GlyphStyle.APP_LABEL_16.cellHeight + GlyphStyle.UI_SMALL_10.cellHeight + threadMetaGap + 2

    fun threadListMetrics(screenProfile: ScreenProfile): SmsThreadListLayoutMetrics {
        val top = LauncherHeaderLayout.firstContentItemTop
        val bottomExclusive = (screenProfile.logicalHeight - panelBottomPadding).coerceAtLeast(top + threadRowHeight)
        val textList = TextListSupport.createLayoutMetrics(
            top = top,
            bottomExclusive = bottomExclusive,
            rowHeight = threadRowHeight,
        )
        return SmsThreadListLayoutMetrics(
            textList = textList,
            rowTextX = LauncherHeaderLayout.horizontalPadding + panelTextInsetX,
            rowMaxWidth = (screenProfile.logicalWidth - LauncherHeaderLayout.horizontalPadding - panelTextInsetX - 1).coerceAtLeast(8),
            panelBottom = bottomExclusive,
            rowHeight = threadRowHeight,
        )
    }
}

data class SmsThreadListLayoutMetrics(
    val textList: TextListLayoutMetrics,
    val rowTextX: Int,
    val rowMaxWidth: Int,
    val panelBottom: Int,
    val rowHeight: Int,
)
