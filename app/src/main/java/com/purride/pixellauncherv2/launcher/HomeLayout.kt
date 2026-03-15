package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.ScreenProfile

object HomeLayout {

    private const val bottomPadding = 2
    private const val titleTopInset = 18
    private const val statusTextHeight = 10
    private const val minTitleStatusGap = 8

    fun metrics(screenProfile: ScreenProfile): HomeLayoutMetrics {
        val contentTop = LauncherHeaderLayout.contentTop
        val contentBottom = screenProfile.logicalHeight - bottomPadding
        val titleY = (contentTop + titleTopInset)
            .coerceAtMost((contentBottom - 16).coerceAtLeast(contentTop + 8))
        val preferredStatusY = contentBottom - statusTextHeight
        val statusY = preferredStatusY.coerceAtLeast(titleY + 16 + minTitleStatusGap)

        return HomeLayoutMetrics(
            titleY = titleY,
            statusY = statusY,
        )
    }
}

data class HomeLayoutMetrics(
    val titleY: Int,
    val statusY: Int,
)
