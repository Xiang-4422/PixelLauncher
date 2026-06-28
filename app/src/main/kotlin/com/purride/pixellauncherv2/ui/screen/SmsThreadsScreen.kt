package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.Axis
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.PageView
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.Row
import com.purride.pixelui.Semantics
import com.purride.pixelui.Slidable
import com.purride.pixelui.SlidableAction
import com.purride.pixelui.SlidableActionPane
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextButtonStyle
import com.purride.pixelui.TextField
import com.purride.pixelui.TextInputAction
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixellauncherv2.data.SmsMessageEntry
import com.purride.pixellauncherv2.data.SmsThreadSummary
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.widget.BatteryDividerWidget
import com.purride.pixellauncherv2.ui.widget.LauncherHeader
import com.purride.pixellauncherv2.util.SmsTimeFormatter
import com.purride.pixellauncherv2.launcher.LauncherHeaderLayout
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.launcher.SmsPageIndex
import com.purride.pixellauncherv2.launcher.SmsThreadSearchModel
import com.purride.pixellauncherv2.launcher.SmsThreadGeometry
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

private const val SMS_THREAD_ROW_PADDING_PX = LauncherSpacing.CONTENT_HORIZONTAL
private val SMS_PAGE_TABS = listOf("UNREAD", "ALL")

/**
 * SMS_THREADS 屏幕：短信应用首页。
 *
 * 左页显示未读短信列表，右页显示全部会话列表。每行显示：
 * - 顶部行：联系人地址（accent 色，左对齐）+ 时间（dim 色，右对齐）
 * - 底部行：片段预览（dim 色，末尾截断）
 */
fun SmsThreadsScreen(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    chargeTick: Int,
    statusBarHeight: Int,
    pagerController: PixelPagerController,
    pagerState: PixelPagerState,
    unreadListState: PixelListState,
    unreadListController: PixelListController,
    listState: PixelListState,
    listController: PixelListController,
    searchController: PixelTextFieldController,
    searchState: PixelTextFieldState,
    onSmsPageSelected: (Int) -> Unit,
    onSearchChanged: (String) -> Unit,
    onMarkSmsRead: () -> Unit,
    onMarkUnreadMessageRead: (Long) -> Unit,
    onOpenThread: (conversationKey: String) -> Unit,
): Widget {
    val showUnreadTabs =
        uiState.unreadSmsEntries.isNotEmpty() ||
            (uiState.isSmsThreadsLoading && uiState.smsPageIndex == SmsPageIndex.UNREAD)
    return Column(
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        mainAxisSize = MainAxisSize.MAX,
        spacing = 0,
        children = buildList {
            add(
                smsHeader(
                    uiState = uiState,
                    theme = theme,
                    chargeTick = chargeTick,
                    statusBarHeight = statusBarHeight,
                    onMarkSmsRead = onMarkSmsRead,
                ),
            )
            add(
                Expanded(
                    child = if (showUnreadTabs) {
                        PageView(
                            axis = Axis.HORIZONTAL,
                            controller = pagerController,
                            state = pagerState,
                            pages = listOf(
                                buildUnreadMessagesPage(
                                    uiState = uiState,
                                    theme = theme,
                                    listState = unreadListState,
                                    listController = unreadListController,
                                    onOpenThread = onOpenThread,
                                    onMarkUnreadMessageRead = onMarkUnreadMessageRead,
                                ),
                                buildAllThreadsPage(
                                    uiState = uiState,
                                    theme = theme,
                                    listState = listState,
                                    listController = listController,
                                    searchController = searchController,
                                    searchState = searchState,
                                    onSearchChanged = onSearchChanged,
                                    onOpenThread = onOpenThread,
                                ),
                            ),
                            onPageChanged = onSmsPageSelected,
                        )
                    } else {
                        buildAllThreadsPage(
                            uiState = uiState,
                            theme = theme,
                            listState = listState,
                            listController = listController,
                            searchController = searchController,
                            searchState = searchState,
                            onSearchChanged = onSearchChanged,
                            onOpenThread = onOpenThread,
                        )
                    },
                ),
            )
            if (showUnreadTabs) {
                add(
                    Padding(
                        horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
                        vertical = LauncherSpacing.ROW_SPACING,
                        child = smsBottomTabs(
                            selectedIndex = SmsPageIndex.coerce(uiState.smsPageIndex),
                            theme = theme,
                            onSelected = onSmsPageSelected,
                        ),
                    ),
                )
            }
        },
    )
}

private fun smsHeader(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    chargeTick: Int,
    statusBarHeight: Int,
    onMarkSmsRead: () -> Unit,
): Widget {
    if (uiState.statusBarMessageText.isNotBlank()) {
        return LauncherHeader(
            timeText = uiState.currentTimeText.ifEmpty { "--:--" },
            screenTitle = "SMS",
            messageText = uiState.statusBarMessageText,
            batteryLevel = uiState.batteryLevel,
            isCharging = uiState.isCharging,
            chargeTick = chargeTick,
            theme = theme,
            statusBarHeight = statusBarHeight,
        )
    }
    val topSpacer = (statusBarHeight - LauncherHeaderLayout.headerContentHeight).coerceAtLeast(0)
    val textStyle = TextStyle(color = theme.statusBar.text)
    val readEnabled = uiState.unreadSmsEntries.isNotEmpty()
    return Column(
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        mainAxisSize = MainAxisSize.MIN,
        spacing = 0,
        children = buildList {
            if (topSpacer > 0) {
                add(SizedBox(height = topSpacer))
            }
            add(
                Padding(
                    horizontal = LauncherHeaderLayout.horizontalPadding,
                    child = Row(
                        spacing = 0,
                        children = listOf(
                            Text(
                                uiState.currentTimeText.ifEmpty { "--:--" },
                                style = textStyle,
                                overflow = TextOverflow.ELLIPSIS,
                                softWrap = false,
                                maxLines = 1,
                            ),
                            Expanded(
                                child = Text(
                                    "SMS",
                                    style = textStyle,
                                    textAlign = TextAlign.CENTER,
                                    overflow = TextOverflow.ELLIPSIS,
                                    softWrap = false,
                                    maxLines = 1,
                                ),
                            ),
                            TextButton(
                                text = "READ",
                                onPressed = onMarkSmsRead,
                                enabled = readEnabled,
                                style = TextButtonStyle(
                                    textStyle = TextStyle(
                                        color = if (readEnabled) theme.statusBar.text else theme.statusBar.mutedText,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
            if (LauncherHeaderLayout.dividerGap > 0) {
                add(SizedBox(height = LauncherHeaderLayout.dividerGap))
            }
            add(
                BatteryDividerWidget(
                    batteryLevel = uiState.batteryLevel,
                    isCharging = uiState.isCharging,
                    chargeTick = chargeTick,
                    highColor = theme.statusBar.batteryHigh,
                    mediumColor = theme.statusBar.batteryMedium,
                    lowColor = theme.statusBar.batteryLow,
                ),
            )
        },
    )
}

private fun smsBottomTabs(
    selectedIndex: Int,
    theme: LauncherTheme,
    onSelected: (Int) -> Unit,
): Widget = Container(
    borderColor = theme.button.border,
    child = Row(
        spacing = 0,
        children = SMS_PAGE_TABS.mapIndexed { index, label ->
            Expanded(
                child = Semantics(
                    label = if (index == selectedIndex) "$label selected" else label,
                    role = PixelSemanticRole.TAB,
                    focused = index == selectedIndex,
                    child = GestureDetector(
                        onTap = { onSelected(index) },
                        child = Container(
                            alignment = Alignment.CENTER,
                            fillColor = if (index == selectedIndex) theme.button.border else PixelColor.Transparent,
                            padding = EdgeInsets.symmetric(
                                horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
                                vertical = LauncherSpacing.ROW_SPACING,
                            ),
                            child = Text(
                                label,
                                style = TextStyle(
                                    color = if (index == selectedIndex) theme.text.inverse else theme.button.text,
                                ),
                                textAlign = TextAlign.CENTER,
                                overflow = TextOverflow.ELLIPSIS,
                                softWrap = false,
                                maxLines = 1,
                            ),
                        ),
                    ),
                ),
            )
        },
    ),
)

private fun buildUnreadMessagesPage(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    listState: PixelListState,
    listController: PixelListController,
    onOpenThread: (conversationKey: String) -> Unit,
    onMarkUnreadMessageRead: (Long) -> Unit,
): Widget {
    val entries = uiState.unreadSmsEntries
    if (uiState.isSmsThreadsLoading && entries.isEmpty()) {
        return centeredSmsStatus("LOADING", theme)
    }
    if (entries.isEmpty()) {
        return centeredSmsStatus("NO UNREAD MESSAGES", theme)
    }
    return ListViewBuilder(
        itemCount = entries.size,
        state = listState,
        controller = listController,
        spacing = SmsThreadGeometry.ROW_SPACING_PX,
        itemBuilder = { index ->
            buildUnreadRow(entries[index], theme, onOpenThread, onMarkUnreadMessageRead)
        },
    )
}

private fun buildAllThreadsPage(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    listState: PixelListState,
    listController: PixelListController,
    searchController: PixelTextFieldController,
    searchState: PixelTextFieldState,
    onSearchChanged: (String) -> Unit,
    onOpenThread: (conversationKey: String) -> Unit,
): Widget {
    val query = uiState.smsThreadSearchQuery
    val searchResults = SmsThreadSearchModel.filter(uiState.smsAllMessages, query)
    val content = when {
        uiState.isSmsThreadsLoading -> centeredSmsStatus("LOADING", theme)
        query.isNotBlank() && searchResults.isEmpty() -> centeredSmsStatus("NO MATCH", theme)
        query.isNotBlank() -> ListViewBuilder(
            itemCount = searchResults.size,
            state = listState,
            controller = listController,
            spacing = SmsThreadGeometry.ROW_SPACING_PX,
            itemBuilder = { index ->
                buildSearchResultRow(searchResults[index], theme, onOpenThread)
            },
        )
        uiState.smsThreads.isEmpty() -> centeredSmsStatus("NO MESSAGES", theme)
        else -> ListViewBuilder(
            itemCount = uiState.smsThreads.size,
            state = listState,
            controller = listController,
            itemExtent = SmsThreadGeometry.ROW_EXTENT_PX,
            spacing = SmsThreadGeometry.ROW_SPACING_PX,
            itemBuilder = { index ->
                buildThreadRow(uiState.smsThreads[index], theme, onOpenThread)
            },
        )
    }
    return Column(
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        mainAxisSize = MainAxisSize.MAX,
        spacing = 0,
        children = listOf(
            Padding(
                horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
                vertical = LauncherSpacing.CONTENT_VERTICAL,
                child = TextField(
                    state = searchState,
                    controller = searchController,
                    placeholder = "SEARCH ALL SMS",
                    textInputAction = TextInputAction.SEARCH,
                    onChanged = onSearchChanged,
                ),
            ),
            Expanded(child = content),
        ),
    )
}

private fun centeredSmsStatus(
    text: String,
    theme: LauncherTheme,
): Widget = Column(
    crossAxisAlignment = CrossAxisAlignment.STRETCH,
    mainAxisSize = MainAxisSize.MAX,
    mainAxisAlignment = com.purride.pixelui.MainAxisAlignment.CENTER,
    spacing = 0,
    children = listOf(
        smsStatusText(text, theme),
    ),
)

private fun buildUnreadRow(
    entry: SmsMessageEntry,
    theme: LauncherTheme,
    onOpenThread: (conversationKey: String) -> Unit,
    onMarkUnreadMessageRead: (Long) -> Unit,
): Widget = Slidable(
    onTap = { onOpenThread(entry.conversationKey) },
    startActionPane = unreadReadActionPane(theme) { onMarkUnreadMessageRead(entry.messageId) },
    endActionPane = unreadReadActionPane(theme) { onMarkUnreadMessageRead(entry.messageId) },
    onDismissed = { onMarkUnreadMessageRead(entry.messageId) },
    child = Padding(
        horizontal = SMS_THREAD_ROW_PADDING_PX,
        vertical = SMS_THREAD_ROW_PADDING_PX,
        child = Column(
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            mainAxisSize = MainAxisSize.MIN,
            spacing = 1,
            children = listOf(
                Row(
                    spacing = LauncherSpacing.ROW_SPACING,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                    children = listOf(
                        Expanded(
                            child = Text(
                                entry.conversationTitle.ifBlank { entry.address }.uppercase(),
                                style = TextStyle(color = theme.sms.sender),
                                overflow = TextOverflow.ELLIPSIS,
                                softWrap = false,
                                maxLines = 1,
                            ),
                        ),
                        Text(
                            SmsTimeFormatter.format(entry.dateMillis),
                            style = TextStyle(color = theme.sms.timestamp),
                            overflow = TextOverflow.ELLIPSIS,
                            softWrap = false,
                            maxLines = 1,
                        ),
                    ),
                ),
                Text(
                    entry.body.trim(),
                    style = TextStyle(color = theme.sms.body),
                    softWrap = true,
                    maxLines = Int.MAX_VALUE,
                ),
            ),
        ),
    ),
)

private fun unreadReadActionPane(
    theme: LauncherTheme,
    onRead: () -> Unit,
): SlidableActionPane = SlidableActionPane(
    children = listOf(
        SlidableAction(
            label = "READ",
            backgroundColor = theme.semantic.success,
            foregroundColor = theme.text.inverse,
            onPressed = onRead,
        ),
    ),
    extentRatio = 0.35f,
    dismissible = true,
    dismissThreshold = 0.45f,
)

private fun buildThreadRow(
    thread: SmsThreadSummary,
    theme: LauncherTheme,
    onOpenThread: (conversationKey: String) -> Unit,
): Widget = GestureDetector(
    onTap = { onOpenThread(thread.conversationKey) },
    // 行1 用更强的 sender 色承载会话标签；行2 预览降为 muted，避免主次反抢。
    child = Padding(
        horizontal = SMS_THREAD_ROW_PADDING_PX,
        vertical = SMS_THREAD_ROW_PADDING_PX,
        child = Column(
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            mainAxisSize = MainAxisSize.MIN,
            spacing = 1,
            children = listOf(
                Row(
                    spacing = LauncherSpacing.ROW_SPACING,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                    children = listOfNotNull(
                        if (thread.unreadCount > 0) {
                            Text(
                                unreadBadge(thread.unreadCount),
                                style = TextStyle(color = theme.sms.sender),
                                overflow = TextOverflow.ELLIPSIS,
                                softWrap = false,
                                maxLines = 1,
                            )
                        } else {
                            null
                        },
                        Expanded(
                            child = Text(
                                thread.displayName.ifBlank { thread.address }.uppercase(),
                                style = TextStyle(color = theme.sms.sender),
                                overflow = TextOverflow.ELLIPSIS,
                                softWrap = false,
                                maxLines = 1,
                            ),
                        ),
                        Text(
                            SmsTimeFormatter.format(thread.dateMillis),
                            style = TextStyle(color = theme.sms.timestamp),
                            overflow = TextOverflow.ELLIPSIS,
                            softWrap = false,
                            maxLines = 1,
                        ),
                    ),
                ),
                Text(
                    thread.snippet.trim(),
                    style = TextStyle(color = theme.text.muted),
                    overflow = TextOverflow.ELLIPSIS,
                    softWrap = false,
                    maxLines = 1,
                ),
            ),
        ),
    ),
)

private fun buildSearchResultRow(
    message: SmsMessageEntry,
    theme: LauncherTheme,
    onOpenThread: (conversationKey: String) -> Unit,
): Widget = GestureDetector(
    onTap = { onOpenThread(message.conversationKey) },
    child = Padding(
        horizontal = SMS_THREAD_ROW_PADDING_PX,
        vertical = SMS_THREAD_ROW_PADDING_PX,
        child = Column(
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            mainAxisSize = MainAxisSize.MIN,
            spacing = 1,
            children = listOf(
                Row(
                    spacing = LauncherSpacing.ROW_SPACING,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                    children = listOf(
                        Expanded(
                            child = Text(
                                message.conversationTitle.ifBlank { message.address }.uppercase(),
                                style = TextStyle(color = theme.sms.sender),
                                overflow = TextOverflow.ELLIPSIS,
                                softWrap = false,
                                maxLines = 1,
                            ),
                        ),
                        Text(
                            SmsTimeFormatter.format(message.dateMillis),
                            style = TextStyle(color = theme.sms.timestamp),
                            overflow = TextOverflow.ELLIPSIS,
                            softWrap = false,
                            maxLines = 1,
                        ),
                    ),
                ),
                Text(
                    message.body.trim(),
                    style = TextStyle(color = theme.text.muted),
                    softWrap = true,
                    maxLines = Int.MAX_VALUE,
                ),
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
