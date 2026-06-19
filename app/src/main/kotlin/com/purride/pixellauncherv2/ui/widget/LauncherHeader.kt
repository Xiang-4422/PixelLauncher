package com.purride.pixellauncherv2.ui.widget

import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextField
import com.purride.pixelui.TextFieldStyle
import com.purride.pixelui.TextInputAction
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixellauncherv2.launcher.LauncherHeaderLayout
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
 * @param theme       当前颜色主题（提供 statusBar token）
 * @param statusBarHeight 顶部状态栏占位高度（engine 逻辑像素）
 */
fun LauncherHeader(
    timeText: String,
    screenTitle: String,
    batteryLevel: Int,
    isCharging: Boolean,
    chargeTick: Int,
    theme: LauncherTheme,
    statusBarHeight: Int = LauncherHeaderLayout.defaultStatusBarHeight,
): Widget = Column(
    children = statusBarChildren(
        statusBarHeight = statusBarHeight,
        row = Row(
            children = listOf(
                Text(timeText, style = TextStyle(color = theme.statusBar.text)),
                Expanded(child = SizedBox(width = 0, height = 0)),
                Text(screenTitle, style = TextStyle(color = theme.statusBar.text)),
            ),
            spacing = 0,
        ),
        divider = BatteryDividerWidget(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            chargeTick = chargeTick,
            primaryColor = theme.statusBar.divider,
            accentColor = theme.statusBar.charging,
        ),
    ),
    spacing = 0,
    mainAxisSize = MainAxisSize.MIN,
    crossAxisAlignment = CrossAxisAlignment.STRETCH,
)

/**
 * 同一套 Launcher 状态栏的搜索态。
 *
 * 外层结构必须和 [LauncherHeader] 保持一致：一行内容 + 一条 BatteryDivider。
 * 这样 HOME / DRAWER / SETTINGS 之间切换时，状态栏尺寸不会跳变。
 */
fun LauncherSearchHeader(
    state: PixelTextFieldState,
    controller: TextEditingController,
    placeholder: String,
    autofocus: Boolean,
    batteryLevel: Int,
    isCharging: Boolean,
    chargeTick: Int,
    theme: LauncherTheme,
    statusBarHeight: Int = LauncherHeaderLayout.defaultStatusBarHeight,
    onChanged: (String) -> Unit,
    onSubmitted: () -> Unit,
): Widget = Column(
    children = statusBarChildren(
        statusBarHeight = statusBarHeight,
        row = Row(
            children = listOf(
                Expanded(
                    child = TextField(
                        state = state,
                        controller = controller,
                        placeholder = placeholder,
                        autofocus = autofocus,
                        textInputAction = TextInputAction.SEARCH,
                        style = TextFieldStyle(
                            borderColor = null,
                            focusedBorderColor = null,
                            textStyle = TextStyle(color = theme.statusBar.searchText),
                            placeholderStyle = TextStyle(color = theme.statusBar.searchPlaceholder),
                            padding = 0,
                        ),
                        onChanged = onChanged,
                        onSubmitted = { onSubmitted() },
                    ),
                ),
            ),
            spacing = 0,
        ),
        divider = BatteryDividerWidget(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            chargeTick = chargeTick,
            primaryColor = theme.statusBar.divider,
            accentColor = theme.statusBar.charging,
        ),
    ),
    spacing = 0,
    mainAxisSize = MainAxisSize.MIN,
    crossAxisAlignment = CrossAxisAlignment.STRETCH,
)

private fun statusBarChildren(
    statusBarHeight: Int,
    row: Widget,
    divider: Widget,
): List<Widget> = buildList {
    val topSpacer = (statusBarHeight - LauncherHeaderLayout.headerContentHeight).coerceAtLeast(0)
    if (topSpacer > 0) {
        add(SizedBox(height = topSpacer))
    }
    add(row)
    if (LauncherHeaderLayout.dividerGap > 0) {
        add(SizedBox(height = LauncherHeaderLayout.dividerGap))
    }
    add(divider)
}
