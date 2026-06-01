package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextInputAction
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.TextField
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.widget.LauncherHeader
import com.purride.pixellauncherv2.util.SmsTimeFormatter
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * SMS_THREAD_DETAIL 屏幕：消息历史 + 草稿输入框。
 *
 * 结构：
 * - LauncherHeader（时间 / "SMS" / 电量）
 * - Expanded ListViewBuilder（消息列表，每条双行：状态行 + 正文行）
 * - 底部草稿栏（TextField + SEND 按钮）
 */
fun SmsThreadDetailScreen(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    chargeTick: Int,
    msgListState: PixelListState,
    msgListController: PixelListController,
    draftController: PixelTextFieldController,
    draftState: PixelTextFieldState,
    onDraftChanged: (String) -> Unit,
    onSendDraft: () -> Unit,
): Widget = Column(
    crossAxisAlignment = CrossAxisAlignment.STRETCH,
    mainAxisSize = MainAxisSize.MAX,
    spacing = 0,
    children = listOf(
        LauncherHeader(
            timeText = uiState.currentTimeText.ifEmpty { "--:--" },
            screenTitle = "SMS",
            batteryLevel = uiState.batteryLevel,
            isCharging = uiState.isCharging,
            chargeTick = chargeTick,
            theme = theme,
        ),
        Expanded(
            child = if (uiState.smsMessages.isEmpty()) {
                Column(
                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    mainAxisSize = MainAxisSize.MAX,
                    mainAxisAlignment = com.purride.pixelui.MainAxisAlignment.CENTER,
                    spacing = 0,
                    children = listOf(
                        Text(
                            "NO MESSAGES",
                            style = TextStyle(color = theme.sms.timestamp),
                            textAlign = com.purride.pixelui.TextAlign.CENTER,
                        ),
                    ),
                )
            } else {
                ListViewBuilder(
                    itemCount = uiState.smsMessages.size,
                    state = msgListState,
                    controller = msgListController,
                    itemExtent = MSG_ROW_HEIGHT,
                    spacing = 1,
                    itemBuilder = { index ->
                        val msg = uiState.smsMessages[index]
                        val isOut = msg.type == TYPE_SENT
                        Column(
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                            mainAxisSize = MainAxisSize.MIN,
                            spacing = 1,
                            children = listOf(
                                Row(
                                    spacing = 2,
                                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                                    children = listOf(
                                        Text(
                                            if (isOut) "OUT" else "IN",
                                            style = TextStyle(color = theme.sms.sender),
                                        ),
                                        Expanded(child = SizedBox(width = 0, height = 0)),
                                        Text(
                                            SmsTimeFormatter.format(msg.dateMillis),
                                            style = TextStyle(color = theme.sms.timestamp),
                                        ),
                                    ),
                                ),
                                Text(
                                    msg.body,
                                    style = TextStyle(color = theme.sms.body),
                                    overflow = TextOverflow.ELLIPSIS,
                                ),
                            ),
                        )
                    },
                )
            },
        ),
        // Draft compose bar — intrinsic height (no STRETCH); a STRETCH Row here grabs
        // the column's remaining height and collapses the message list above it.
        Row(
            spacing = 2,
            children = listOf(
                Expanded(
                    child = SizedBox(
                        height = COMPOSE_HEIGHT,
                        child = TextField(
                            state = draftState,
                            controller = draftController,
                            placeholder = "TYPE MSG",
                            textInputAction = TextInputAction.SEND,
                            onChanged = onDraftChanged,
                            onSubmitted = { onSendDraft() },
                        ),
                    ),
                ),
                OutlinedButton(
                    text = "SEND",
                    onPressed = onSendDraft,
                    borderColor = theme.sms.draftBorder,
                ),
            ),
        ),
    ),
)

private const val MSG_ROW_HEIGHT = 24
private const val COMPOSE_HEIGHT = 16
/** android.provider.Telephony.Sms.MESSAGE_TYPE_SENT = 2 */
private const val TYPE_SENT = 2
