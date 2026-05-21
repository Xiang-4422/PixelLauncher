package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixellauncherv2.launcher.DiagnosticsModel
import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.widget.LauncherHeader
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * DIAGNOSTICS 屏幕：显示 6 行键值诊断数据。
 *
 * 数据来源：[DiagnosticsModel.lines]（LauncherUiState 重载）。
 */
fun DiagnosticsScreen(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    chargeTick: Int,
    screenProfile: ScreenProfile,
): Widget = Column(
    crossAxisAlignment = CrossAxisAlignment.STRETCH,
    mainAxisSize = MainAxisSize.MAX,
    spacing = 0,
    children = listOf(
        LauncherHeader(
            timeText = uiState.currentTimeText.ifEmpty { "--:--" },
            screenTitle = "ADVANCED",
            batteryLevel = uiState.batteryLevel,
            isCharging = uiState.isCharging,
            chargeTick = chargeTick,
            theme = theme,
        ),
        Expanded(
            child = Padding(
                horizontal = 2,
                vertical = 2,
                child = Column(
                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    mainAxisSize = MainAxisSize.MIN,
                    spacing = 2,
                    children = DiagnosticsModel.lines(uiState, screenProfile).map { line ->
                        Row(
                            spacing = 2,
                            mainAxisAlignment = MainAxisAlignment.START,
                            crossAxisAlignment = CrossAxisAlignment.CENTER,
                            children = listOf(
                                Text(
                                    line.title,
                                    style = TextStyle(color = theme.dimColor),
                                    overflow = TextOverflow.ELLIPSIS,
                                ),
                                Expanded(child = SizedBox(width = 0, height = 0)),
                                Text(
                                    line.value,
                                    style = TextStyle(color = theme.primaryColor),
                                    overflow = TextOverflow.ELLIPSIS,
                                ),
                            ),
                        )
                    },
                ),
            ),
        ),
    ),
)
