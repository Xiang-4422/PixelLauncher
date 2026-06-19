package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Row
import com.purride.pixelui.Text
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixellauncherv2.data.SmsThreadSummary
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.widget.LauncherHeader
import com.purride.pixellauncherv2.util.SmsTimeFormatter
import com.purride.pixellauncherv2.launcher.SmsThreadGeometry
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * SMS_THREADS 屏幕：会话列表。
 *
 * 每行显示：
 * - 顶部行：联系人地址（accent 色，左对齐）+ 时间（dim 色，右对齐）
 * - 底部行：片段预览（dim 色，末尾截断）
 */
fun SmsThreadsScreen(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    chargeTick: Int,
    statusBarHeight: Int,
    listState: PixelListState,
    listController: PixelListController,
    onOpenThread: (threadId: Long, address: String) -> Unit,
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
            statusBarHeight = statusBarHeight,
        ),
        Expanded(
            child = if (uiState.isSmsThreadsLoading) {
                centeredSmsStatus("LOADING", theme)
            } else if (uiState.smsThreads.isEmpty()) {
                centeredSmsStatus("NO MESSAGES", theme)
            } else {
                ListViewBuilder(
                    itemCount = uiState.smsThreads.size,
                    state = listState,
                    controller = listController,
                    itemExtent = SmsThreadGeometry.ROW_EXTENT_PX,
                    spacing = SmsThreadGeometry.ROW_SPACING_PX,
                    itemBuilder = { index ->
                        val thread = uiState.smsThreads[index]
                        buildThreadRow(thread, theme, onOpenThread)
                    },
                )
            },
        ),
    ),
)

private fun centeredSmsStatus(
    text: String,
    theme: LauncherTheme,
): Widget = Column(
    crossAxisAlignment = CrossAxisAlignment.STRETCH,
    mainAxisSize = MainAxisSize.MAX,
    mainAxisAlignment = com.purride.pixelui.MainAxisAlignment.CENTER,
    spacing = 0,
    children = listOf(
        Text(
            text,
            style = TextStyle(color = theme.sms.timestamp),
            textAlign = com.purride.pixelui.TextAlign.CENTER,
        ),
    ),
)

private fun buildThreadRow(
    thread: SmsThreadSummary,
    theme: LauncherTheme,
    onOpenThread: (threadId: Long, address: String) -> Unit,
): Widget = GestureDetector(
    onTap = { onOpenThread(thread.threadId, thread.address) },
    // 内容优先的两行布局：
    //  行1 = [未读圆圈徽标(accent)] 号码(dim 次要) ··· 时间(dim)
    //  行2 = 摘要(body 亮色，主要可读元素)
    // 取代旧的"号码抢眼蓝 + 每行重复 NEW n 文字"。
    child = Column(
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        mainAxisSize = MainAxisSize.MIN,
        spacing = 1,
        children = listOf(
            Row(
                spacing = 2,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
                children = listOfNotNull(
                    if (thread.unreadCount > 0) {
                        Text(
                            unreadBadge(thread.unreadCount),
                            style = TextStyle(color = theme.sms.sender),
                        )
                    } else {
                        null
                    },
                    Expanded(
                        child = Text(
                            thread.address.uppercase(),
                            style = TextStyle(color = theme.text.muted),
                            overflow = TextOverflow.ELLIPSIS,
                        ),
                    ),
                    Text(
                        SmsTimeFormatter.format(thread.dateMillis),
                        style = TextStyle(color = theme.sms.timestamp),
                    ),
                ),
            ),
            Text(
                thread.snippet.trim(),
                style = TextStyle(color = theme.sms.body),
                overflow = TextOverflow.ELLIPSIS,
            ),
        ),
    ),
)

/** 未读徽标：圆圈数字（①..⑳，>20 用 ⑳+），紧凑且在像素字库覆盖范围内（U+2460..）。 */
private fun unreadBadge(count: Int): String = when {
    count <= 0 -> ""
    count <= 20 -> ('①'.code + count - 1).toChar().toString()
    else -> "⑳+"
}
