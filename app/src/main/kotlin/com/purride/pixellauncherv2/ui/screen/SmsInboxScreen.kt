package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelui.Axis
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.PageViewBuilder
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState
import com.purride.pixellauncherv2.data.UnreadSmsEntry
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.widget.LauncherHeader
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * SMS_INBOX 屏幕：未读消息翻页浏览。
 *
 * 使用水平 [PageViewBuilder] 承载每条未读消息，每页可垂直滚动查看正文；
 * 点击消息正文区域进入对应会话详情。
 *
 * @param scrollStates  每页各自的垂直滚动状态，由宿主创建，长度 ≥ unreadSmsEntries.size
 */
fun SmsInboxScreen(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    chargeTick: Int,
    statusBarHeight: Int,
    pagerController: PixelPagerController,
    pagerState: PixelPagerState,
    scrollController: PixelListController,
    scrollStates: List<PixelListState>,
    onSelectSmsIndex: (Int) -> Unit,
    onOpenThread: (threadId: Long, address: String) -> Unit,
): Widget {
    val entries = uiState.unreadSmsEntries
    val total = entries.size.coerceAtLeast(1)
    val current = uiState.smsSelectedIndex.coerceIn(0, entries.lastIndex.coerceAtLeast(0))
    val titleText = if (entries.isEmpty()) "INBOX" else "INBOX ${current + 1}/$total"

    return Column(
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        mainAxisSize = MainAxisSize.MAX,
        spacing = 0,
        children = listOf(
            LauncherHeader(
                timeText = uiState.currentTimeText.ifEmpty { "--:--" },
                screenTitle = titleText,
                batteryLevel = uiState.batteryLevel,
                isCharging = uiState.isCharging,
                chargeTick = chargeTick,
                theme = theme,
                statusBarHeight = statusBarHeight,
            ),
            Expanded(
                child = if (entries.isEmpty()) {
                    Column(
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        mainAxisSize = MainAxisSize.MAX,
                        mainAxisAlignment = com.purride.pixelui.MainAxisAlignment.CENTER,
                        spacing = 0,
                        children = listOf(
                            Text(
                                "NO UNREAD MESSAGES",
                                style = TextStyle(color = theme.sms.timestamp),
                                textAlign = com.purride.pixelui.TextAlign.CENTER,
                            ),
                        ),
                    )
                } else {
                    PageViewBuilder(
                        axis = Axis.HORIZONTAL,
                        controller = pagerController,
                        state = pagerState,
                        itemCount = entries.size,
                        onPageChanged = onSelectSmsIndex,
                        itemBuilder = { index ->
                            val entry = entries[index]
                            val scrollState = scrollStates.getOrElse(index) { scrollStates.last() }
                            buildInboxPage(entry, theme, scrollController, scrollState) {
                                onOpenThread(entry.threadId, entry.address)
                            }
                        },
                    )
                },
            ),
        ),
    )
}

private fun buildInboxPage(
    entry: UnreadSmsEntry,
    theme: LauncherTheme,
    scrollController: PixelListController,
    scrollState: PixelListState,
    onOpenThread: () -> Unit,
): Widget = GestureDetector(
    onTap = onOpenThread,
    child = SingleChildScrollView(
        state = scrollState,
        controller = scrollController,
        child = Column(
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            mainAxisSize = MainAxisSize.MIN,
            spacing = 2,
            children = listOf(
                Text(
                    entry.address.uppercase(),
                    style = TextStyle(color = theme.sms.sender),
                ),
                Text(
                    entry.body,
                    style = TextStyle(color = theme.sms.body),
                    softWrap = true,
                    maxLines = Int.MAX_VALUE,
                ),
            ),
        ),
    ),
)
