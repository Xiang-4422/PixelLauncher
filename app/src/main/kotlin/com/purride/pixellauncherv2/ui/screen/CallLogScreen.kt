package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelui.AnimatedPixelLoadingBar
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.Text
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixellauncherv2.launcher.CallLogModel
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.launcher.SmsThreadGeometry
import com.purride.pixellauncherv2.model.CallLogGroup
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.util.RelativeTimeFormatter
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

private const val CALL_ROW_PADDING_PX = LauncherSpacing.CONTENT_HORIZONTAL

/**
 * CALL_LOG 屏幕：最近通话列表。
 *
 * 每行两段：
 * - 顶部行：方向标记（未接/拒接用 danger 色）+ 名称或号码 + 合并次数 + 时间
 * - 底部行：号码（有联系人名时）或通话时长
 *
 * 点按一行直接回电。选中态不渲染高亮——按键导航是次要通路（见 UI 规范 §8）。
 */
fun CallLogScreen(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    vsync: PixelTickerProvider,
    listState: PixelListState,
    listController: PixelListController,
    onCallGroupPressed: (number: String) -> Unit,
): Widget {
    val groups = uiState.callLogGroups
    if (uiState.isCallLogLoading && groups.isEmpty()) {
        return centeredCallLoading(theme, vsync)
    }
    if (groups.isEmpty()) {
        return centeredCallStatus("NO CALLS", theme)
    }
    return ListViewBuilder(
        itemCount = groups.size,
        state = listState,
        controller = listController,
        spacing = SmsThreadGeometry.ROW_SPACING_PX,
        itemBuilder = { index -> buildCallRow(groups[index], theme, onCallGroupPressed) },
    )
}

private fun buildCallRow(
    group: CallLogGroup,
    theme: LauncherTheme,
    onCallGroupPressed: (number: String) -> Unit,
): Widget = GestureDetector(
    onTap = { onCallGroupPressed(group.number) },
    child = Padding(
        horizontal = CALL_ROW_PADDING_PX,
        vertical = CALL_ROW_PADDING_PX,
        child = Column(
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            mainAxisSize = MainAxisSize.MIN,
            spacing = 1,
            children = listOf(
                Row(
                    spacing = LauncherSpacing.ROW_SPACING,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                    children = listOfNotNull(
                        Text(
                            CallLogModel.directionLabel(group.type),
                            style = TextStyle(
                                color = if (CallLogModel.isUnanswered(group.type)) {
                                    theme.semantic.danger
                                } else {
                                    theme.sms.timestamp
                                },
                            ),
                            overflow = TextOverflow.ELLIPSIS,
                            softWrap = false,
                            maxLines = 1,
                        ),
                        Expanded(
                            child = Text(
                                group.displayTitle.uppercase(),
                                style = TextStyle(color = theme.sms.sender),
                                overflow = TextOverflow.ELLIPSIS,
                                softWrap = false,
                                maxLines = 1,
                            ),
                        ),
                        CallLogModel.countBadge(group.callCount)
                            .takeIf(String::isNotEmpty)
                            ?.let { badge ->
                                Text(
                                    badge,
                                    style = TextStyle(color = theme.sms.timestamp),
                                    overflow = TextOverflow.ELLIPSIS,
                                    softWrap = false,
                                    maxLines = 1,
                                )
                            },
                        Text(
                            RelativeTimeFormatter.format(group.dateMillis),
                            style = TextStyle(color = theme.sms.timestamp),
                            overflow = TextOverflow.ELLIPSIS,
                            softWrap = false,
                            maxLines = 1,
                        ),
                    ),
                ),
                Text(
                    callRowSubtitle(group),
                    style = TextStyle(color = theme.sms.body),
                    overflow = TextOverflow.ELLIPSIS,
                    softWrap = false,
                    maxLines = 1,
                ),
            ),
        ),
    ),
)

/**
 * 副标题：有联系人名时展示号码（否则号码已在标题里，重复无意义），
 * 已接通的通话补上时长。
 */
private fun callRowSubtitle(group: CallLogGroup): String {
    val showsNumber = group.displayTitle != group.number && group.number.isNotBlank()
    val duration = CallLogModel.formatDuration(group.durationSeconds)
    return when {
        showsNumber && duration.isNotEmpty() -> "${group.number}  $duration"
        showsNumber -> group.number
        else -> duration
    }
}

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
