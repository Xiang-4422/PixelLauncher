package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextInputAction
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.TextField
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixellauncherv2.data.SmsMessageEntry
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.launcher.SmsMessageStatusModel
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.widget.LauncherHeader
import com.purride.pixellauncherv2.util.SmsTimeFormatter
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * SMS_THREAD_DETAIL：单个会话的完整消息流 + 草稿输入。
 *
 * 重设计要点（取代旧的"每条固定 24px 单行截断"）：
 * - 消息列表用变高 lazy list（[ListViewBuilder] 的 estimatedItemExtent 路径），
 *   每条按正文自适应高度，**完整换行显示**，不再 ELLIPSIS 截断——验证码 / 通知
 *   类长短信的关键内容（取件码、链接等）现在能读全。
 * - 每条消息：状态 + 时间一行（dim），下面整段正文；IN 用 body 前景色、SENT 用
 *   muted 前景色区分收发。
 * - 表头显示对方地址（截断防溢出），底部固定草稿栏（TextField + SEND）。
 */
fun SmsThreadDetailScreen(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    chargeTick: Int,
    statusBarHeight: Int,
    msgListState: PixelListState,
    msgListController: PixelListController,
    draftController: PixelTextFieldController,
    draftState: PixelTextFieldState,
    onDraftChanged: (String) -> Unit,
    onSendDraft: () -> Unit,
): Widget {
    val contact = uiState.smsCurrentAddress.trim().ifBlank { "SMS" }
    return Column(
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        mainAxisSize = MainAxisSize.MAX,
        spacing = 0,
        children = listOf(
            LauncherHeader(
                timeText = uiState.currentTimeText.ifEmpty { "--:--" },
                screenTitle = headerTitle(contact),
                messageText = uiState.statusBarMessageText,
                batteryLevel = uiState.batteryLevel,
                isCharging = uiState.isCharging,
                chargeTick = chargeTick,
                theme = theme,
                statusBarHeight = statusBarHeight,
            ),
            Expanded(
                child = if (uiState.smsMessages.isEmpty()) {
                    Column(
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        mainAxisSize = MainAxisSize.MAX,
                        mainAxisAlignment = MainAxisAlignment.CENTER,
                        spacing = 0,
                        children = listOf(
                            smsStatusText("NO MESSAGES", theme),
                        ),
                    )
                } else {
                    // 用 SingleChildScrollView + Column 承载整段消息流：Column 会把每条
                    // 消息按其完整正文高度排布，不会像变高 lazy list 那样把高消息裁顶，
                    // 保证长短信（取件码 + 地址 + 链接 + 退订语）完整可读。
                    SingleChildScrollView(
                        state = msgListState,
                        controller = msgListController,
                        child = Padding(
                            horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
                            vertical = LauncherSpacing.CONTENT_VERTICAL,
                            child = Column(
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                mainAxisSize = MainAxisSize.MIN,
                                spacing = LauncherSpacing.ROW_SPACING * 2,
                                children = uiState.smsMessages.map { msg ->
                                    buildMessage(msg, theme)
                                },
                            ),
                        ),
                    )
                },
            ),
            buildComposeArea(
                uiState = uiState,
                theme = theme,
                draftState = draftState,
                draftController = draftController,
                onDraftChanged = onDraftChanged,
                onSendDraft = onSendDraft,
            ),
        ),
    )
}

private fun buildComposeArea(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    draftState: PixelTextFieldState,
    draftController: PixelTextFieldController,
    onDraftChanged: (String) -> Unit,
    onSendDraft: () -> Unit,
): Widget = Padding(
    horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
    vertical = LauncherSpacing.CONTENT_VERTICAL,
    child = Column(
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        mainAxisSize = MainAxisSize.MIN,
        spacing = LauncherSpacing.ROW_SPACING,
        children = buildList {
            if (uiState.smsSendStatusText.isNotBlank()) {
                add(
                    Text(
                        uiState.smsSendStatusText,
                        style = TextStyle(color = theme.sms.timestamp),
                        overflow = TextOverflow.ELLIPSIS,
                        softWrap = false,
                        maxLines = 1,
                    ),
                )
            }
            add(
                Row(
                    spacing = LauncherSpacing.ROW_SPACING,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                    children = listOf(
                        Expanded(
                            child = TextField(
                                state = draftState,
                                controller = draftController,
                                placeholder = "TYPE MSG",
                                textInputAction = TextInputAction.SEND,
                                onChanged = onDraftChanged,
                                onSubmitted = { onSendDraft() },
                            ),
                        ),
                        OutlinedButton(
                            text = "SEND",
                            onPressed = onSendDraft,
                            borderColor = theme.sms.draftBorder,
                        ),
                    ),
                ),
            )
        },
    ),
)

/** 一条消息：方向 + 时间一行，下面整段正文（完整换行，不截断）。 */
private fun buildMessage(
    msg: SmsMessageEntry,
    theme: LauncherTheme,
): Widget {
    val isSent = SmsMessageStatusModel.isSent(msg.type)
    return Column(
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        mainAxisSize = MainAxisSize.MIN,
        spacing = 1,
        children = listOf(
            Row(
                spacing = LauncherSpacing.ROW_SPACING,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
                children = listOf(
                    Text(
                        SmsMessageStatusModel.label(msg.type),
                        style = TextStyle(color = theme.sms.sender),
                        overflow = TextOverflow.ELLIPSIS,
                        softWrap = false,
                        maxLines = 1,
                    ),
                    Expanded(child = SizedBox(width = 0, height = 0)),
                    Text(
                        SmsTimeFormatter.format(msg.dateMillis),
                        style = TextStyle(color = theme.sms.timestamp),
                        overflow = TextOverflow.ELLIPSIS,
                        softWrap = false,
                        maxLines = 1,
                    ),
                ),
            ),
            Text(
                msg.body,
                style = TextStyle(color = if (isSent) theme.text.muted else theme.sms.body),
                softWrap = true,
                maxLines = Int.MAX_VALUE,
                // 发出的消息整体靠尾端（右）对齐，做出收/发的聊天感（收到的靠首端）。
                textAlign = if (isSent) TextAlign.END else TextAlign.START,
            ),
        ),
    )
}

/** 把对方地址收敛成不会撑爆右上角标题区的短串。 */
private fun headerTitle(contact: String): String {
    if (contact == "SMS") return "SMS"
    val trimmed = contact.take(HEADER_TITLE_MAX)
    return if (trimmed.length < contact.length) "$trimmed…" else trimmed
}

private const val HEADER_TITLE_MAX = 12
