package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.GlyphStyle
import com.purride.pixellauncherv2.render.ScreenProfile

object SmsLayout {

    private const val panelBottomPadding = 2
    private const val panelTextInsetX = 2
    private val composeBarHeight = GlyphStyle.UI_SMALL_10.cellHeight + 4
    private const val composeBarBottomGap = 1
    private const val threadMetaGap = 1
    private val threadRowHeight = GlyphStyle.APP_LABEL_16.cellHeight + GlyphStyle.UI_SMALL_10.cellHeight + threadMetaGap + 2

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

    fun detailMetrics(screenProfile: ScreenProfile): SmsDetailLayoutMetrics {
        val bodyTop = LauncherHeaderLayout.firstContentItemTop
        val composeTop = (screenProfile.logicalHeight - composeBarHeight - composeBarBottomGap).coerceAtLeast(bodyTop)
        val bodyBottomExclusive = composeTop
        return SmsDetailLayoutMetrics(
            textLeft = LauncherHeaderLayout.horizontalPadding + panelTextInsetX,
            textWidth = (screenProfile.logicalWidth - LauncherHeaderLayout.horizontalPadding - panelTextInsetX - 1).coerceAtLeast(8),
            bodyTop = bodyTop,
            bodyBottomExclusive = bodyBottomExclusive,
            composeTop = composeTop,
            composeBottomExclusive = screenProfile.logicalHeight - composeBarBottomGap,
            composeTextLeft = LauncherHeaderLayout.horizontalPadding + panelTextInsetX,
            composeSendRight = screenProfile.logicalWidth - LauncherHeaderLayout.horizontalPadding - panelTextInsetX,
        )
    }

    fun hitTestThreadRow(
        screenProfile: ScreenProfile,
        logicalX: Int,
        logicalY: Int,
        rowCount: Int,
        listStartIndex: Int,
        scrollOffsetPx: Int,
    ): Int? {
        if (rowCount <= 0) {
            return null
        }
        val metrics = threadListMetrics(screenProfile)
        if (logicalX !in 0 until screenProfile.logicalWidth) {
            return null
        }
        if (logicalY !in metrics.textList.viewport.top until metrics.panelBottom) {
            return null
        }
        return TextListSupport.hitTestRow(
            viewport = metrics.textList.viewport,
            logicalY = logicalY,
            rowCount = rowCount,
            listStartIndex = listStartIndex,
            scrollOffsetPx = scrollOffsetPx,
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

data class SmsDetailLayoutMetrics(
    val textLeft: Int,
    val textWidth: Int,
    val bodyTop: Int,
    val bodyBottomExclusive: Int,
    val composeTop: Int,
    val composeBottomExclusive: Int,
    val composeTextLeft: Int,
    val composeSendRight: Int,
)
