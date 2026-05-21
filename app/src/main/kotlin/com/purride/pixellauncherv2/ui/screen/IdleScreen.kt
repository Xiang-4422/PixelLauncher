package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * IDLE 屏幕（占位版）：居中显示时间和日期。
 *
 * IDLE 物理流体效果已废弃（旧 IdleFluidEngine），本版本仅展示静态文字内容。
 * 充电动画通过 LauncherHeader 的 BatteryDivider 实现（此屏幕不含 header，简化处理）。
 */
fun IdleScreen(
    uiState: LauncherUiState,
    theme: LauncherTheme,
): Widget = Center(
    child = Column(
        mainAxisSize = MainAxisSize.MIN,
        mainAxisAlignment = MainAxisAlignment.CENTER,
        spacing = 2,
        children = listOf(
            Text(
                uiState.currentTimeText.ifEmpty { "--:--" },
                style = TextStyle(color = theme.primaryColor),
            ),
            Text(
                uiState.currentDateText.ifBlank { "--- --- --" },
                style = TextStyle(color = theme.dimColor),
            ),
        ),
    ),
)
