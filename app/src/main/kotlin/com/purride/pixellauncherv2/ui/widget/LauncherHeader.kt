package com.purride.pixellauncherv2.ui.widget

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelInputType
import com.purride.pixelui.Row
import com.purride.pixelui.Semantics
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextButtonStyle
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextOverflow
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
 * @param messageText 临时消息；非空时独占文字行，电量线保持显示
 * @param batteryLevel 电量百分比 0–100
 * @param isCharging  是否正在充电
 * @param chargeTick  充电像素的动画帧计数
 * @param theme       当前颜色主题（提供 statusBar token）
 * @param statusBarHeight 顶部状态栏占位高度（engine 逻辑像素）
 */
fun LauncherHeader(
    timeText: String,
    screenTitle: String,
    messageText: String = "",
    actionLeadingText: String = "",
    actionLabel: String = "",
    isActionDanger: Boolean = false,
    centerText: String = "",
    centerTextColor: PixelColor? = null,
    batteryLevel: Int,
    isCharging: Boolean,
    chargeTick: Int,
    theme: LauncherTheme,
    statusBarHeight: Int = LauncherHeaderLayout.defaultStatusBarHeight,
    onAction: (() -> Unit)? = null,
    onCenterTap: (() -> Unit)? = null,
    onCenterDoubleTap: (() -> Unit)? = null,
): Widget {
    val message = messageText.trim()
    val action = actionLabel.trim()
    val isShowingMessage = message.isNotEmpty()
    val isShowingAction = action.isNotEmpty()
    val header = Column(
        children = statusBarChildren(
            statusBarHeight = statusBarHeight,
            row = when {
                isShowingAction -> statusBarAction(
                    leadingText = actionLeadingText,
                    actionLabel = action,
                    onAction = onAction,
                )
                isShowingMessage -> statusBarMessage(message, theme)
                else -> statusBarTitleRow(
                    timeText = timeText,
                    screenTitle = screenTitle,
                    centerText = centerText.trim(),
                    centerTextColor = centerTextColor,
                    theme = theme,
                    onCenterTap = onCenterTap,
                    onCenterDoubleTap = onCenterDoubleTap,
                )
            },
            divider = BatteryDividerWidget(
                batteryLevel = batteryLevel,
                isCharging = isCharging,
                chargeTick = chargeTick,
                highColor = theme.statusBar.batteryHigh,
                mediumColor = theme.statusBar.batteryMedium,
                lowColor = theme.statusBar.batteryLow,
            ),
        ),
        spacing = 0,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    )
    return if (isShowingAction) {
        Container(
            fillColor = statusBarActionBackgroundColor(isActionDanger, theme),
            child = header,
        )
    } else {
        header
    }
}

private fun statusBarText(
    text: String,
    theme: LauncherTheme,
    textAlign: TextAlign = TextAlign.START,
    color: PixelColor = theme.statusBar.text,
): Widget = Text(
    text,
    style = TextStyle(color = color),
    textAlign = textAlign,
    overflow = TextOverflow.ELLIPSIS,
    softWrap = false,
    maxLines = 1,
)

private fun statusBarTitleRow(
    timeText: String,
    screenTitle: String,
    centerText: String,
    centerTextColor: PixelColor?,
    theme: LauncherTheme,
    onCenterTap: (() -> Unit)?,
    onCenterDoubleTap: (() -> Unit)?,
): Widget = Padding(
    horizontal = LauncherHeaderLayout.horizontalPadding,
    child = Row(
        children = listOf(
            statusBarText(timeText, theme),
            Expanded(
                child = statusBarCenter(
                    text = centerText,
                    color = centerTextColor ?: theme.statusBar.text,
                    theme = theme,
                    onTap = onCenterTap,
                    onDoubleTap = onCenterDoubleTap,
                ),
            ),
            statusBarText(
                text = screenTitle,
                theme = theme,
                textAlign = TextAlign.END,
            ),
        ),
        spacing = 0,
    ),
)

private fun statusBarCenter(
    text: String,
    color: PixelColor,
    theme: LauncherTheme,
    onTap: (() -> Unit)?,
    onDoubleTap: (() -> Unit)?,
): Widget {
    val content = Container(
        alignment = Alignment.CENTER,
        child = if (text.isBlank()) {
            SizedBox(width = 0, height = 0)
        } else {
            statusBarText(
                text = text,
                theme = theme,
                textAlign = TextAlign.CENTER,
                color = color,
            )
        },
    )
    if (text.isBlank() || (onTap == null && onDoubleTap == null)) {
        return content
    }
    return Semantics(
        label = text,
        role = PixelSemanticRole.BUTTON,
        enabled = true,
        child = GestureDetector(
            onTap = onTap ?: {},
            onDoubleTap = onDoubleTap,
            child = content,
        ),
    )
}

private fun statusBarMessage(
    text: String,
    theme: LauncherTheme,
): Widget = Padding(
    horizontal = LauncherHeaderLayout.horizontalPadding,
    child = Row(
        children = listOf(
            Expanded(
                child = statusBarText(
                    text = text,
                    theme = theme,
                    textAlign = TextAlign.CENTER,
                ),
            ),
        ),
        spacing = 0,
    ),
)

private fun statusBarAction(
    leadingText: String,
    actionLabel: String,
    onAction: (() -> Unit)?,
): Widget {
    val actionTextStyle = TextStyle(color = PixelColor.White)
    return Container(
        child = Padding(
            horizontal = LauncherHeaderLayout.horizontalPadding,
            child = Row(
                children = listOf(
                    Text(
                        leadingText,
                        style = actionTextStyle,
                        overflow = TextOverflow.ELLIPSIS,
                        softWrap = false,
                        maxLines = 1,
                    ),
                    Expanded(
                        child = Container(
                            alignment = Alignment.CENTER_END,
                            child = TextButton(
                                text = actionLabel,
                                onPressed = onAction,
                                style = TextButtonStyle(textStyle = actionTextStyle),
                            ),
                        ),
                    ),
                ),
                spacing = 0,
            ),
        ),
    )
}

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
    messageText: String = "",
    actionLeadingText: String = "",
    actionLabel: String = "",
    isActionDanger: Boolean = false,
    autofocus: Boolean,
    textAlign: TextAlign = TextAlign.START,
    batteryLevel: Int,
    isCharging: Boolean,
    chargeTick: Int,
    theme: LauncherTheme,
    statusBarHeight: Int = LauncherHeaderLayout.defaultStatusBarHeight,
    onChanged: (String) -> Unit,
    onSubmitted: () -> Unit,
    onAction: (() -> Unit)? = null,
): Widget {
    val message = messageText.trim()
    val action = actionLabel.trim()
    val isShowingMessage = message.isNotEmpty()
    val isShowingAction = action.isNotEmpty()
    val header = Column(
        children = statusBarChildren(
            statusBarHeight = statusBarHeight,
            row = when {
                isShowingAction -> statusBarAction(
                    leadingText = actionLeadingText,
                    actionLabel = action,
                    onAction = onAction,
                )
                isShowingMessage -> statusBarMessage(message, theme)
                else -> {
                    Padding(
                        horizontal = LauncherHeaderLayout.horizontalPadding,
                        child = Row(
                            children = listOf(
                                Expanded(
                                    child = TextField(
                                        state = state,
                                        controller = controller,
                                        placeholder = placeholder,
                                        autofocus = autofocus,
                                        inputType = PixelInputType.ASCII,
                                        textAlign = textAlign,
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
                    )
                }
            },
            divider = BatteryDividerWidget(
                batteryLevel = batteryLevel,
                isCharging = isCharging,
                chargeTick = chargeTick,
                highColor = theme.statusBar.batteryHigh,
                mediumColor = theme.statusBar.batteryMedium,
                lowColor = theme.statusBar.batteryLow,
            ),
        ),
        spacing = 0,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    )
    return if (isShowingAction) {
        Container(
            fillColor = statusBarActionBackgroundColor(isActionDanger, theme),
            child = header,
        )
    } else {
        header
    }
}

private fun statusBarActionBackgroundColor(
    isDanger: Boolean,
    theme: LauncherTheme,
): PixelColor = if (isDanger) {
    PixelColor.fromRgb(255, 0, 0)
} else {
    theme.surface.panel
}

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
