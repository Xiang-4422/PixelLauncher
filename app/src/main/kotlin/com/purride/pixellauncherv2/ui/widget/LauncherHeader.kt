package com.purride.pixellauncherv2.ui.widget

import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixellauncherv2.ui.theme.LauncherTheme

/**
 * 所有 Launcher 屏幕的顶部标题栏。
 *
 * 视觉结构（宽度 = 屏幕宽度，自动 STRETCH）：
 * ```
 * HH:MM           SCREEN TITLE
 * ████████████░░░░░░░░░░░░░░  ← BatteryDivider（1px 高）
 * ```
 *
 * 时间靠左，屏幕标题靠右；电量分隔线位于文字行正下方。
 *
 * @param timeText    当前时间字符串（例如 "14:30"）
 * @param screenTitle 屏幕名称（例如 "HOME"、"APP DRAWER"）
 * @param batteryLevel 电量百分比 0–100
 * @param isCharging  是否正在充电
 * @param chargeTick  动画帧计数，传 0 表示静态（无充电动画）
 * @param theme       当前颜色主题（提供 primaryColor / accentColor）
 */
fun LauncherHeader(
    timeText: String,
    screenTitle: String,
    batteryLevel: Int,
    isCharging: Boolean,
    chargeTick: Int,
    theme: LauncherTheme,
): Widget = Column(
    children = listOf(
        Row(
            children = listOf(
                Text(timeText, style = TextStyle(color = theme.primaryColor)),
                Expanded(child = SizedBox(width = 0, height = 0)),
                Text(screenTitle, style = TextStyle(color = theme.primaryColor)),
            ),
            spacing = 0,
        ),
        BatteryDividerWidget(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            chargeTick = chargeTick,
            primaryColor = theme.primaryColor,
            accentColor = theme.accentColor,
        ),
    ),
    spacing = 0,
    mainAxisSize = MainAxisSize.MIN,
    crossAxisAlignment = CrossAxisAlignment.STRETCH,
)
