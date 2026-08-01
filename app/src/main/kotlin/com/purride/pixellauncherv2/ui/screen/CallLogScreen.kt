package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.AnimatedPixelLoadingBar
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSystemIcon
import com.purride.pixelui.PixelSystemIconSize
import com.purride.pixelui.Row
import com.purride.pixelui.Semantics
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixellauncherv2.launcher.CallLogModel
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.model.CallLogGroup
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.widget.launcherInlineIconSize
import com.purride.pixellauncherv2.ui.widget.launcherSystemIcon
import com.purride.pixellauncherv2.util.RelativeTimeFormatter
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * 拨号模块的「最近通话」页。
 *
 * 单条两行，让身份与细节分层，而不是把四段信息平铺在一行里：
 * ```
 * ! 张三 (3)              12:34
 *   13800138000           1:23
 * ```
 * 方向只用 1 个字符表达（未接通 `!`、呼出 `>`、已接 `<`）——在 29 字符一行里
 * 方向不值得占 4 格。未接通额外把姓名染成 danger 色，只强调"没接到"这一件事。
 */
fun CallLogScreen(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    vsync: PixelTickerProvider,
    listState: PixelListState,
    listController: PixelListController,
    onCallGroupPressed: (number: String) -> Unit,
    onRequestCallLogPermission: () -> Unit,
): Widget {
    val groups = uiState.callLogGroups
    // 缺权限与"确实没有通话"是两件事：前者可操作，必须给出恢复路径，
    // 否则用户被拒一次后就只能去系统设置里翻。
    if (!uiState.hasCallLogPermission) {
        return callLogPermissionEmptyState(theme, onRequestCallLogPermission)
    }
    if (uiState.isCallLogLoading && groups.isEmpty()) {
        return centeredCallLoading(theme, vsync)
    }
    if (groups.isEmpty()) {
        return centeredCallStatus("NO CALLS", theme)
    }
    /** 通话状态图标随当前正文字号选择规格。 */
    val iconSize = launcherInlineIconSize(uiState.fontSelection.size.px)
    return ListViewBuilder(
        itemCount = groups.size,
        state = listState,
        controller = listController,
        spacing = LauncherSpacing.ROW_SPACING,
        itemBuilder = { index -> buildCallRow(groups[index], theme, iconSize, onCallGroupPressed) },
    )
}

private fun buildCallRow(
    group: CallLogGroup,
    theme: LauncherTheme,
    iconSize: PixelSystemIconSize,
    onCallGroupPressed: (number: String) -> Unit,
): Widget {
    val unanswered = CallLogModel.isUnanswered(group.type)
    return GestureDetector(
        onTap = { onCallGroupPressed(group.number) },
        child = Padding(
            horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
            vertical = LauncherSpacing.ROW_SPACING,
            child = Column(
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                mainAxisSize = MainAxisSize.MIN,
                spacing = 1,
                children = listOf(
                    Row(
                        spacing = LauncherSpacing.ROW_SPACING,
                        crossAxisAlignment = CrossAxisAlignment.CENTER,
                        children = listOf(
                            callStatusIcon(
                                group = group,
                                theme = theme,
                                iconSize = iconSize,
                            ),
                            Expanded(
                                child = callText(
                                    text = callRowTitle(group),
                                    color = if (unanswered) theme.semantic.danger else theme.sms.sender,
                                    theme = theme,
                                ),
                            ),
                            callText(
                                text = RelativeTimeFormatter.format(group.dateMillis),
                                color = theme.sms.timestamp,
                                theme = theme,
                            ),
                        ),
                    ),
                    // 第二行缩进到姓名起点，让一条记录读起来是一个块而不是两条独立行。
                    Row(
                        spacing = LauncherSpacing.ROW_SPACING,
                        crossAxisAlignment = CrossAxisAlignment.CENTER,
                        children = listOf(
                            SizedBox(width = iconSize.pixels, height = 0),
                            Expanded(
                                child = callText(
                                    // 无联系人姓名时第一行已经是号码，这里不再重复。
                                    text = CallLogModel.secondaryLine(group.displayTitle, group.number),
                                    color = theme.sms.timestamp,
                                    theme = theme,
                                ),
                            ),
                            callText(
                                text = CallLogModel.formatDuration(group.durationSeconds),
                                color = theme.sms.timestamp,
                                theme = theme,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}

/** 将通话方向与结果映射为稳定像素图标，颜色继续承担未接警示层级。 */
private fun callStatusIcon(
    group: CallLogGroup,
    theme: LauncherTheme,
    iconSize: PixelSystemIconSize,
): Widget {
    /** 未接和拒接使用叉号；其余记录按呼入、呼出与语音信箱区分。 */
    val icon = when {
        CallLogModel.isUnanswered(group.type) -> PixelSystemIcon.CLOSE
        CallLogModel.isOutgoing(group.type) -> PixelSystemIcon.FORWARD
        CallLogModel.isVoicemail(group.type) -> PixelSystemIcon.VOICEMAIL
        else -> PixelSystemIcon.BACK
    }
    /** 未接类图标使用危险色，普通方向图标保持时间信息的次级颜色。 */
    val color = if (CallLogModel.isUnanswered(group.type)) {
        theme.semantic.danger
    } else {
        theme.sms.timestamp
    }
    return launcherSystemIcon(icon = icon, size = iconSize, color = color)
}

/** 标题：姓名（或号码），合并多次时紧跟 (N)。 */
private fun callRowTitle(group: CallLogGroup): String {
    val badge = if (group.callCount > 1) " (${group.callCount})" else ""
    return group.displayTitle.uppercase() + badge
}

private fun callText(
    text: String,
    color: PixelColor,
    theme: LauncherTheme,
): Widget = Text(
    text,
    style = theme.typography.textStyle(color = color),
    overflow = TextOverflow.ELLIPSIS,
    softWrap = false,
    maxLines = 1,
)

/**
 * 缺少通话记录权限时的空态：说明原因 + 一个可点的授权入口。
 *
 * 授权入口是被拒后唯一的恢复路径——控制器的一次性节流会让第二次按 CALL 只闪一条
 * 状态栏文字，没有这个按钮用户就只能去系统设置里找。按钮用边框而非实心：顶部页签的
 * 选中态已经占用了本屏的实心块。
 */
private fun callLogPermissionEmptyState(
    theme: LauncherTheme,
    onRequestCallLogPermission: () -> Unit,
): Widget = Column(
    crossAxisAlignment = CrossAxisAlignment.CENTER,
    mainAxisSize = MainAxisSize.MAX,
    mainAxisAlignment = MainAxisAlignment.CENTER,
    spacing = LauncherSpacing.ROW_SPACING * 2,
    children = listOf(
        smsStatusText("NO CALL LOG ACCESS", theme),
        Semantics(
            label = "GRANT CALL LOG ACCESS",
            role = PixelSemanticRole.BUTTON,
            child = GestureDetector(
                onTap = onRequestCallLogPermission,
                child = Container(
                    borderColor = theme.button.border,
                    padding = EdgeInsets.symmetric(
                        horizontal = LauncherSpacing.CONTENT_HORIZONTAL * 2,
                        vertical = LauncherSpacing.ROW_SPACING,
                    ),
                    child = callText(
                        text = "GRANT",
                        color = theme.button.text,
                        theme = theme,
                    ),
                ),
            ),
        ),
    ),
)

private fun centeredCallStatus(
    text: String,
    theme: LauncherTheme,
): Widget = Column(
    crossAxisAlignment = CrossAxisAlignment.STRETCH,
    mainAxisSize = MainAxisSize.MAX,
    mainAxisAlignment = MainAxisAlignment.CENTER,
    spacing = 0,
    children = listOf(smsStatusText(text, theme)),
)

private fun centeredCallLoading(
    theme: LauncherTheme,
    vsync: PixelTickerProvider,
): Widget = Column(
    crossAxisAlignment = CrossAxisAlignment.STRETCH,
    mainAxisSize = MainAxisSize.MAX,
    mainAxisAlignment = MainAxisAlignment.CENTER,
    spacing = 0,
    children = listOf(
        Padding(
            horizontal = LauncherSpacing.CONTENT_HORIZONTAL * 2,
            child = AnimatedPixelLoadingBar(
                vsync = vsync,
                color = theme.text.primary,
                trackColor = theme.text.primary,
                width = 96,
                height = 9,
                blockWidth = 9,
                trailWidth = 5,
                key = "call-log-loading-bar",
            ),
        ),
    ),
)
