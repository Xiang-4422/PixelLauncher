package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.layout.LauncherLayoutProfile

/**
 * 短信会话列表的视口行数计算（状态机用）。行高与引擎 SmsThreadsScreen 共用固定
 * [SmsThreadGeometry]，保证两侧一致。
 */
object SmsLayout {

    private const val panelBottomPadding = 2

    /** 与引擎 SmsThreadsScreen 渲染行距一致（单一来源 [SmsThreadGeometry]）。 */
    private val threadRowHeight: Int
        get() = SmsThreadGeometry.ROW_PITCH_PX

    fun threadVisibleRows(screenProfile: LauncherLayoutProfile): Int {
        val top = LauncherHeaderLayout.firstContentItemTop(screenProfile)
        val bottomExclusive = (screenProfile.logicalHeight - panelBottomPadding).coerceAtLeast(top + threadRowHeight)
        return TextListSupport.createLayoutMetrics(
            top = top,
            bottomExclusive = bottomExclusive,
            rowHeight = threadRowHeight,
        ).viewport.visibleRows
    }
}
