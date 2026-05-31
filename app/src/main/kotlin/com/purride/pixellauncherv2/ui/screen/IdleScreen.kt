package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * IDLE 屏幕（占位版）：居中显示放大的时间，下方一行「星期 · 日期」。
 *
 * IDLE 流体物理效果已废弃（旧 IdleFluidEngine），本版本仅展示静态文字。
 * 充电动画由 LauncherHeader 的 BatteryDivider 承担；此屏幕不含 header。
 */
fun IdleScreen(
    uiState: LauncherUiState,
    theme: LauncherTheme,
): Widget {
    val subtitle = listOf(uiState.currentWeekdayText, uiState.currentDateText)
        .filter { it.isNotBlank() }
        .joinToString("  ")
        .ifBlank { "--- --- --" }
    return Center(
        child = Column(
            mainAxisSize = MainAxisSize.MIN,
            mainAxisAlignment = MainAxisAlignment.CENTER,
            crossAxisAlignment = CrossAxisAlignment.CENTER,
            spacing = 4,
            children = listOf(
                Text(
                    uiState.currentTimeText.ifEmpty { "--:--" },
                    style = TextStyle(color = theme.text.primary, fontScale = 2),
                ),
                Text(
                    subtitle,
                    style = TextStyle(color = theme.text.muted),
                ),
            ),
        ),
    )
}
