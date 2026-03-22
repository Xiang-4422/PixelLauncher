package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.GlyphStyle
import com.purride.pixellauncherv2.render.ScreenProfile

object HomeLayout {

    private const val bottomPadding = 2
    private const val sectionGap = 2
    private const val minFixedHeight = 40
    private const val minStackHeight = 22
    private const val splitRatioPercent = 52

    fun metrics(screenProfile: ScreenProfile): HomeLayoutMetrics {
        val contentTop = LauncherHeaderLayout.firstContentItemTop
        val minimumContentHeight = minFixedHeight + sectionGap + minStackHeight
        val contentBottom = (screenProfile.logicalHeight - bottomPadding)
            .coerceAtLeast(contentTop + minimumContentHeight - 1)
        val contentHeight = (contentBottom - contentTop + 1).coerceAtLeast(minimumContentHeight)
        val desiredFixedBottom = contentTop + ((contentHeight * splitRatioPercent) / 100)
        val fixedBottom = desiredFixedBottom
            .coerceIn(
                minimumValue = contentTop + minFixedHeight - 1,
                maximumValue = contentBottom - minStackHeight - sectionGap,
            )
        val stackTop = (fixedBottom + sectionGap).coerceAtMost(contentBottom - minStackHeight + 1)
        val left = LauncherHeaderLayout.horizontalPadding
        val right = (screenProfile.logicalWidth - LauncherHeaderLayout.horizontalPadding - 1)
            .coerceAtLeast(left + 8)
        val innerLeft = left
        val innerRight = right
        val dateY = fixedTopY(contentTop)
        val weekdayY = dateY + GlyphStyle.UI_SMALL_10.cellHeight + 1
        val fixedInfoStartY = weekdayY + GlyphStyle.UI_SMALL_10.cellHeight + 2
        val stackCardBodyY = (screenProfile.logicalHeight - GlyphStyle.UI_SMALL_10.cellHeight).coerceAtLeast(stackTop)

        return HomeLayoutMetrics(
            fixedTop = contentTop,
            fixedBottom = fixedBottom,
            stackTop = stackTop,
            stackBottom = contentBottom,
            outerLeft = left,
            outerRight = right,
            innerLeft = innerLeft,
            innerRight = innerRight,
            dateY = dateY,
            weekdayY = weekdayY,
            fixedInfoStartY = fixedInfoStartY,
            fixedInfoRowHeight = GlyphStyle.UI_SMALL_10.cellHeight,
            stackCardBodyY = stackCardBodyY,
        )
    }

    private fun fixedTopY(contentTop: Int): Int {
        return contentTop
    }
}

data class HomeLayoutMetrics(
    val fixedTop: Int,
    val fixedBottom: Int,
    val stackTop: Int,
    val stackBottom: Int,
    val outerLeft: Int,
    val outerRight: Int,
    val innerLeft: Int,
    val innerRight: Int,
    val dateY: Int,
    val weekdayY: Int,
    val fixedInfoStartY: Int,
    val fixedInfoRowHeight: Int,
    val stackCardBodyY: Int,
) {
    val fixedHeight: Int
        get() = (fixedBottom - fixedTop + 1).coerceAtLeast(0)

    val stackHeight: Int
        get() = (stackBottom - stackTop + 1).coerceAtLeast(0)

    val innerWidth: Int
        get() = (innerRight - innerLeft + 1).coerceAtLeast(0)
}
