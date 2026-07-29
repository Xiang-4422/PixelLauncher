package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.AnimatedPixelLoadingBar
import com.purride.pixelui.Axis
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Dialog
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
import com.purride.pixelui.Stack
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextButtonStyle
import com.purride.pixelui.SlidableAction
import com.purride.pixelui.SlidableActionPane
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextField
import com.purride.pixelui.TextInputAction
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixellauncherv2.model.SmsMessageEntry
import com.purride.pixellauncherv2.model.SmsThreadSummary
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.util.RelativeTimeFormatter
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
    vsync: PixelTickerProvider,
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
    onMarkUnreadMessageRead: (Long) -> Unit,
    onOpenThread: (conversationKey: String) -> Unit,
    onComposeNewThread: (address: String) -> Unit,
    onThreadLongPressed: (conversationKey: String) -> Unit,
    onThreadMenuMarkRead: () -> Unit,
    onThreadMenuToggleMute: () -> Unit,
    onThreadMenuDelete: () -> Unit,
    onThreadMenuDismiss: () -> Unit,
): Widget {
    val showUnreadTabs =
        uiState.unreadSmsEntries.isNotEmpty() ||
            (uiState.isSmsThreadsLoading && uiState.smsPageIndex == SmsPageIndex.UNREAD)
    val content = Column(
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        mainAxisSize = MainAxisSize.MAX,
        spacing = 0,
        children = buildList {
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
                                    vsync = vsync,
                                    listState = unreadListState,
                                    listController = unreadListController,
                                    onOpenThread = onOpenThread,
                                    onMarkUnreadMessageRead = onMarkUnreadMessageRead,
                                ),
                                buildAllThreadsPage(
                                    uiState = uiState,
                                    theme = theme,
                                    vsync = vsync,
                                    listState = listState,
                                    listController = listController,
                                    searchController = searchController,
                                    searchState = searchState,
                                    onSearchChanged = onSearchChanged,
                                    onOpenThread = onOpenThread,
                                    onComposeNewThread = onComposeNewThread,
                                    onThreadLongPressed = onThreadLongPressed,
                                ),
                            ),
                            onPageChanged = onSmsPageSelected,
                        )
                    } else {
                        buildAllThreadsPage(
                            uiState = uiState,
                            theme = theme,
                            vsync = vsync,
                            listState = listState,
                            listController = listController,
                            searchController = searchController,
                            searchState = searchState,
                            onSearchChanged = onSearchChanged,
                            onOpenThread = onOpenThread,
                            onComposeNewThread = onComposeNewThread,
                            onThreadLongPressed = onThreadLongPressed,
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
    // 长按会话行弹出的 Playdate 风格轻量浮层菜单。点外关闭与 Back 关闭都由
    // 引擎的 modal 关闭屏障提供（见 Dialog 的 onDismissRequest）。
    val menuThread = uiState.smsThreads
        .firstOrNull { it.conversationKey == uiState.smsThreadMenuConversationKey }
    if (!uiState.isSmsThreadMenuVisible || menuThread == null) {
        return content
    }
    return Stack(
        children = listOf(
            content,
            smsThreadActionMenu(
                thread = menuThread,
                isMuted = menuThread.conversationKey in uiState.smsMutedConversationKeys,
                theme = theme,
                onMarkRead = onThreadMenuMarkRead,
                onToggleMute = onThreadMenuToggleMute,
                onDelete = onThreadMenuDelete,
                onDismiss = onThreadMenuDismiss,
            ),
        ),
    )
}

/** 会话操作浮层：（有未读时）标记已读 / 静音切换 / 删除会话。 */
private fun smsThreadActionMenu(
    thread: SmsThreadSummary,
    isMuted: Boolean,
    theme: LauncherTheme,
    onMarkRead: () -> Unit,
    onToggleMute: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
): Widget {
    val actionStyle = TextButtonStyle(
        textStyle = TextStyle(color = theme.button.text),
        padding = EdgeInsets.all(LauncherSpacing.BORDERED_CONTROL_INSET),
    )
    return Dialog(
        title = Text(
            smsThreadMenuTitle(thread),
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
                if (thread.unreadCount > 0) {
                    add(TextButton(text = "MARK READ", onPressed = onMarkRead, style = actionStyle))
                }
                add(
                    TextButton(
                        text = if (isMuted) "UNMUTE" else "MUTE",
                        onPressed = onToggleMute,
                        style = actionStyle,
                    ),
                )
                add(TextButton(text = "DELETE", onPressed = onDelete, style = actionStyle))
                add(TextButton(text = "CANCEL", onPressed = onDismiss, style = actionStyle))
            },
        ),
        fillColor = theme.surface.panel,
        borderColor = theme.button.border,
        // 引擎按 onDismissRequest 装内建关闭屏障：Back 与点外都会走这里，
        // 同时保留 modal 对背景指针/输入/焦点的隔离。
        onDismissRequest = onDismiss,
    )
}

/** 菜单标题：会话名（联系人名/服务号来源/号码），超长省略。 */
private fun smsThreadMenuTitle(thread: SmsThreadSummary): String {
    val title = thread.displayName.ifBlank { thread.address }.ifBlank { "SMS" }.uppercase()
    val maxChars = 18
    return if (title.length <= maxChars) title else "${title.take(maxChars - 2)}.."
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
                                    color = if (index == selectedIndex) theme.surface.offPixelColor else theme.button.text,
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
    vsync: PixelTickerProvider,
    listState: PixelListState,
    listController: PixelListController,
    onOpenThread: (conversationKey: String) -> Unit,
    onMarkUnreadMessageRead: (Long) -> Unit,
): Widget {
    val entries = uiState.unreadSmsEntries
    if (uiState.isSmsThreadsLoading && entries.isEmpty()) {
        return centeredSmsLoading(theme, vsync)
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
    vsync: PixelTickerProvider,
    listState: PixelListState,
    listController: PixelListController,
    searchController: PixelTextFieldController,
    searchState: PixelTextFieldState,
    onSearchChanged: (String) -> Unit,
    onOpenThread: (conversationKey: String) -> Unit,
    onComposeNewThread: (address: String) -> Unit,
    onThreadLongPressed: (conversationKey: String) -> Unit,
): Widget {
    val query = uiState.smsThreadSearchQuery
    val searchResults = SmsThreadSearchModel.filter(uiState.smsAllMessages, query)
    val composeAddress = SmsThreadSearchModel.composeAddress(query)
    val content = when {
        uiState.isSmsThreadsLoading -> centeredSmsLoading(theme, vsync)
        // 搜索词是可拨号号码且无既有会话匹配：给一行"发给该号码"的新建会话入口。
        query.isNotBlank() && searchResults.isEmpty() && composeAddress != null ->
            buildComposeNewThreadRow(composeAddress, theme, onComposeNewThread)
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
                buildThreadRow(uiState.smsThreads[index], theme, onOpenThread, onThreadLongPressed)
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
                    style = smsTextFieldStyle(theme),
                    textInputAction = TextInputAction.SEARCH,
                    onChanged = onSearchChanged,
                ),
            ),
            Expanded(child = content),
        ),
    )
}

/** 新建会话入口行：搜索的号码没有既有会话时，点按直接对该号码发起会话。 */
private fun buildComposeNewThreadRow(
    address: String,
    theme: LauncherTheme,
    onComposeNewThread: (address: String) -> Unit,
): Widget = Column(
    crossAxisAlignment = CrossAxisAlignment.STRETCH,
    mainAxisSize = MainAxisSize.MAX,
    spacing = 0,
    children = listOf(
        GestureDetector(
            onTap = { onComposeNewThread(address) },
            child = Padding(
                horizontal = SMS_THREAD_ROW_PADDING_PX,
                vertical = SMS_THREAD_ROW_PADDING_PX,
                child = Text(
                    "NEW MSG TO $address",
                    style = TextStyle(color = theme.sms.sender),
                    overflow = TextOverflow.ELLIPSIS,
                    softWrap = false,
                    maxLines = 1,
                ),
            ),
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
        smsStatusText(text, theme),
    ),
)

private fun centeredSmsLoading(
    theme: LauncherTheme,
    vsync: PixelTickerProvider,
): Widget = Column(
    crossAxisAlignment = CrossAxisAlignment.STRETCH,
    mainAxisSize = MainAxisSize.MAX,
    mainAxisAlignment = com.purride.pixelui.MainAxisAlignment.CENTER,
    spacing = 0,
    children = listOf(
        Padding(
            horizontal = LauncherSpacing.CONTENT_HORIZONTAL * 2,
            child = AnimatedPixelLoadingBar(
                vsync = vsync,
                color = theme.text.primary,
                // 点阵背景显式沿用扫描色，避免回落到组件 track 角色。
                trackColor = theme.text.primary,
                width = 96,
                height = 9,
                blockWidth = 9,
                trailWidth = 5,
                key = "sms-loading-bar",
            ),
        ),
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
                            RelativeTimeFormatter.format(entry.dateMillis),
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
            foregroundColor = theme.surface.offPixelColor,
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
    onThreadLongPressed: (conversationKey: String) -> Unit,
): Widget = GestureDetector(
    onTap = { onOpenThread(thread.conversationKey) },
    onLongPress = { onThreadLongPressed(thread.conversationKey) },
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
                            RelativeTimeFormatter.format(thread.dateMillis),
                            style = TextStyle(color = theme.sms.timestamp),
                            overflow = TextOverflow.ELLIPSIS,
                            softWrap = false,
                            maxLines = 1,
                        ),
                    ),
                ),
                Text(
                    thread.snippet.trim(),
                    style = TextStyle(color = theme.sms.body),
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
                            RelativeTimeFormatter.format(message.dateMillis),
                            style = TextStyle(color = theme.sms.timestamp),
                            overflow = TextOverflow.ELLIPSIS,
                            softWrap = false,
                            maxLines = 1,
                        ),
                    ),
                ),
                Text(
                    message.body.trim(),
                    style = TextStyle(color = theme.sms.body),
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
