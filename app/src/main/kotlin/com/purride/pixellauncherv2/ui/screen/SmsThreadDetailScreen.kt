package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Dialog
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.PositionedFill
import com.purride.pixelui.Stack
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextButtonStyle
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.StatelessWidget
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
import com.purride.pixellauncherv2.model.SmsMessageEntry
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.launcher.SmsMessageStatusModel
import com.purride.pixellauncherv2.launcher.SmsVerificationCodeModel
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.util.SmsTimeFormatter
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * SMS_THREAD_DETAIL：单个会话的完整消息流 + 草稿输入。
 *
 * 重设计要点（取代旧的"每条固定 24px 单行截断"）：
 * - 消息列表用变高 lazy list（[ListViewBuilder] 的 estimatedItemExtent 路径），
 *   每条按正文自适应高度，**完整换行显示**，不再 ELLIPSIS 截断——验证码 / 通知
 *   类长短信的关键内容（取件码、链接等）现在能读全。
 * - 每条消息显示时间和完整正文，通过左右对齐与颜色区分收发。
 * - 服务商聚合会话只读；个人会话底部保留草稿栏并避让输入法。
 */
fun SmsThreadDetailScreen(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    msgListState: PixelListState,
    msgListController: PixelListController,
    draftController: PixelTextFieldController,
    draftState: PixelTextFieldState,
    onDraftChanged: (String) -> Unit,
    onSendDraft: () -> Unit,
    onMessagePressed: (Long) -> Unit,
    onMessageLongPressed: (Long) -> Unit,
    onMenuCopy: () -> Unit,
    onMenuCopyCode: () -> Unit,
    onMenuResend: () -> Unit,
    onMenuDelete: () -> Unit,
    onMenuDismiss: () -> Unit,
): Widget {
    val content = Column(
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        mainAxisSize = MainAxisSize.MAX,
        spacing = 0,
        children = buildList {
            add(
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
                                    children = uiState.smsMessages.mapIndexed { index, msg ->
                                        buildMessage(
                                            msg = msg,
                                            theme = theme,
                                            onMessagePressed = onMessagePressed,
                                            onMessageLongPressed = onMessageLongPressed,
                                            isLatestMessage = index == uiState.smsMessages.lastIndex,
                                        )
                                    },
                                ),
                            ),
                        )
                    },
                ),
            )
            if (!uiState.smsCurrentIsServiceConversation) {
                add(
                    ImeBottomPadding(
                        child = buildComposeArea(
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
        },
    )
    // 长按消息弹出的 Playdate 风格轻量浮层菜单（UI 规范 §10）：
    // 点击浮层外任意位置即关闭。
    val menuMessage = uiState.smsMessages
        .firstOrNull { it.messageId == uiState.smsMessageMenuMessageId }
    if (!uiState.isSmsMessageMenuVisible || menuMessage == null) {
        return content
    }
    return Stack(
        children = listOf(
            content,
            PositionedFill(
                child = GestureDetector(
                    onTap = onMenuDismiss,
                    child = Container(fillColor = PixelColor.Transparent),
                ),
            ),
            smsMessageActionMenu(
                message = menuMessage,
                theme = theme,
                onCopy = onMenuCopy,
                onCopyCode = onMenuCopyCode,
                onResend = onMenuResend,
                onDelete = onMenuDelete,
                onDismiss = onMenuDismiss,
            ),
        ),
    )
}

/** 消息操作浮层：复制 /（有验证码时）复制验证码 /（发送失败时）重发 / 删除。 */
private fun smsMessageActionMenu(
    message: SmsMessageEntry,
    theme: LauncherTheme,
    onCopy: () -> Unit,
    onCopyCode: () -> Unit,
    onResend: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
): Widget {
    val actionStyle = TextButtonStyle(
        textStyle = TextStyle(color = theme.button.text),
        padding = EdgeInsets.all(LauncherSpacing.BORDERED_CONTROL_INSET),
    )
    val code = SmsVerificationCodeModel.extract(message.body)
    return Dialog(
        title = Text(
            smsMessageMenuTitle(message),
            style = TextStyle(color = theme.text.primary),
            overflow = TextOverflow.ELLIPSIS,
            softWrap = false,
            maxLines = 1,
        ),
        content = Column(
            mainAxisSize = MainAxisSize.MIN,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            spacing = LauncherSpacing.ROW_SPACING,
            children = buildList {
                add(TextButton(text = "COPY", onPressed = onCopy, style = actionStyle))
                if (code != null) {
                    add(TextButton(text = "COPY CODE", onPressed = onCopyCode, style = actionStyle))
                }
                if (SmsMessageStatusModel.isFailed(message.type)) {
                    add(TextButton(text = "RESEND", onPressed = onResend, style = actionStyle))
                }
                add(TextButton(text = "DELETE", onPressed = onDelete, style = actionStyle))
                add(TextButton(text = "CANCEL", onPressed = onDismiss, style = actionStyle))
            },
        ),
        fillColor = theme.surface.panel,
        borderColor = theme.button.border,
    )
}

/** 菜单标题：取正文首行压缩空白，超长省略，让用户确认操作对象。 */
private fun smsMessageMenuTitle(message: SmsMessageEntry): String {
    val body = message.body.trim().replace(Regex("\\s+"), " ")
    val maxChars = 18
    return when {
        body.isEmpty() -> "MSG"
        body.length <= maxChars -> body
        else -> "${body.take(maxChars - 2)}.."
    }
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
                                style = smsTextFieldStyle(theme),
                                textInputAction = TextInputAction.SEND,
                                onChanged = onDraftChanged,
                                onSubmitted = { onSendDraft() },
                            ),
                        ),
                        OutlinedButton(
                            text = "SEND",
                            onPressed = onSendDraft,
                            style = smsSendButtonStyle(theme),
                        ),
                    ),
                ),
            )
        },
    ),
)

/** 一条消息：时间 + 完整正文，通过左右对齐区分方向；发出方向附加发送状态行。 */
private fun buildMessage(
    msg: SmsMessageEntry,
    theme: LauncherTheme,
    onMessagePressed: (Long) -> Unit,
    onMessageLongPressed: (Long) -> Unit,
    isLatestMessage: Boolean,
): Widget {
    val isOutgoing = SmsMessageStatusModel.isOutgoing(msg.type)
    // CODE 标签用严格提取（须命中关键词），避免把订单号/年份标成验证码；
    // 点按复制与菜单 COPY CODE 仍用宽松的 extract。
    val code = SmsVerificationCodeModel.displayCode(msg.body)
    return GestureDetector(
        onTap = { onMessagePressed(msg.messageId) },
        onLongPress = { onMessageLongPressed(msg.messageId) },
        child = Column(
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            mainAxisSize = MainAxisSize.MIN,
            spacing = 1,
            children = buildList {
                add(messageMetaRow(code, SmsTimeFormatter.format(msg.dateMillis), theme))
                add(
                    Text(
                        msg.body,
                        style = TextStyle(color = theme.sms.body),
                        softWrap = true,
                        maxLines = Int.MAX_VALUE,
                        // 发出的消息整体靠尾端（右）对齐，做出收/发的聊天感（收到的靠首端）。
                        textAlign = if (isOutgoing) TextAlign.END else TextAlign.START,
                    ),
                )
                // 回执未到 → SENDING；临时性失败排队 → QUEUED（自动重试，点按立即重试）；
                // 发送失败 → 失败提示，点按整条消息即重发；
                // 送达回执只在会话最后一条消息下显示，避免整屏 DELIVERED 噪声。
                if (SmsMessageStatusModel.isQueued(msg.type)) {
                    add(messageStatusLine("QUEUED - AUTO RETRY", theme.sms.timestamp))
                } else if (SmsMessageStatusModel.isPending(msg.type)) {
                    add(messageStatusLine("SENDING", theme.sms.timestamp))
                }
                if (SmsMessageStatusModel.isFailed(msg.type)) {
                    add(messageStatusLine("FAILED - TAP TO RESEND", theme.semantic.danger))
                }
                if (isLatestMessage &&
                    SmsMessageStatusModel.isSent(msg.type) &&
                    SmsMessageStatusModel.isDelivered(msg.deliveryStatus)
                ) {
                    add(messageStatusLine("DELIVERED", theme.sms.timestamp))
                }
            },
        ),
    )
}

/** 发送状态行：靠尾端对齐的单行小字，与发出消息同侧。 */
private fun messageStatusLine(
    text: String,
    color: PixelColor,
): Widget = Text(
    text,
    style = TextStyle(color = color),
    overflow = TextOverflow.ELLIPSIS,
    softWrap = false,
    maxLines = 1,
    textAlign = TextAlign.END,
)

private class ImeBottomPadding(
    private val child: Widget,
) : StatelessWidget() {
    override fun build(context: BuildContext): Widget {
        return Padding(
            padding = EdgeInsets.only(bottom = MediaQuery.of(context).viewInsets.bottom),
            child = child,
        )
    }
}

private fun messageMetaRow(
    code: String?,
    timeText: String,
    theme: LauncherTheme,
): Widget = Row(
    spacing = LauncherSpacing.ROW_SPACING,
    children = listOf(
        Expanded(
            child = Text(
                code?.takeIf(String::isNotBlank)?.let { "CODE $it" }.orEmpty(),
                style = TextStyle(color = theme.semantic.info),
                overflow = TextOverflow.ELLIPSIS,
                softWrap = false,
                maxLines = 1,
            ),
        ),
        Text(
            timeText,
            style = TextStyle(color = theme.sms.timestamp),
            overflow = TextOverflow.ELLIPSIS,
            softWrap = false,
            maxLines = 1,
        ),
    ),
)
