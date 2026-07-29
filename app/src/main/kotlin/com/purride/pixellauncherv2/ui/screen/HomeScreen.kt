package com.purride.pixellauncherv2.ui.screen

import android.os.SystemClock
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.CustomPaint
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelPoint
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.Positioned
import com.purride.pixelui.Row
import com.purride.pixelui.Semantics
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Stack
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextButtonStyle
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.widgets.animated.AnimatedContainer
import com.purride.pixellauncherv2.launcher.HomeInfoAction
import com.purride.pixellauncherv2.launcher.HomeInfoLine
import com.purride.pixellauncherv2.launcher.HomeInfoModel
import com.purride.pixellauncherv2.launcher.LauncherChromeLayout
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.launcher.MediaPlaybackSnapshot
import com.purride.pixellauncherv2.launcher.NotificationActionInfo
import com.purride.pixellauncherv2.launcher.NotificationSignal
import com.purride.pixellauncherv2.launcher.PixelFontCatalog
import com.purride.pixellauncherv2.launcher.LauncherTextRole
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.text.opticallyAlignStartText
import com.purride.pixellauncherv2.viewmodel.LauncherUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * HOME 屏幕（pixel-engine 渲染）。
 *
 * 结构：
 *  - 固定信息区（日期、天气、闹钟、设备状态、屏幕使用统计）
 *  - Expanded 弹性空白
 *  - 底栏：CALL 按钮（左）/ SMS 按钮（右）
 *
 * @param uiState        当前 UI 状态快照
 * @param theme          当前颜色主题
 * @param onOpenCall     点击 CALL → 打开通话记录
 * @param onOpenSms      点击 SMS → 进入短信模块
 * @param onInfoAction   点击 HOME 信息行
 * @param resolveLeadingInkInset 查询页面边缘文字首字形的左侧空白像素数
 */
class HomeScreen(
    private val uiState: LauncherUiState,
    private val theme: LauncherTheme,
    private val screenWidthPx: Int,
    private val vsync: PixelTickerProvider,
    private val onOpenCall: () -> Unit,
    private val onOpenSms: () -> Unit,
    private val onInfoAction: (HomeInfoAction) -> Unit,
    private val onInfoDetail: (HomeInfoAction) -> Unit,
    private val onMediaTogglePlayPause: () -> Unit,
    private val onMediaSkipPrevious: () -> Unit,
    private val onMediaSkipNext: () -> Unit,
    private val onMediaSeek: (Float) -> Unit,
    private val onNotificationPressed: (String) -> Unit,
    private val onNotificationAction: (String, Int) -> Unit,
    private val resolveLeadingInkInset: (String) -> Int,
    override val key: Any? = null,
) : StatefulWidget(key = key) {

    override fun createState(): State<out StatefulWidget> = HomeState()

    private inner class HomeState : State<HomeScreen>() {
        private var isScrubbingMedia = false
        private var scrubStartProgress = 0f
        private var scrubProgress = 0f
        private val notificationListController = PixelListController()
        private val notificationListState: PixelListState = notificationListController.create()

        override fun didUpdateWidget(oldWidget: HomeScreen) {
            val media = widget.uiState.mediaPlayback
            if (!media.hasTrack) {
                isScrubbingMedia = false
                scrubStartProgress = 0f
                scrubProgress = 0f
            } else if (!isScrubbingMedia) {
                scrubProgress = media.progressAt(SystemClock.elapsedRealtime())
            }
        }

        override fun build(context: BuildContext): Widget {
            val s = widget.uiState
            val t = widget.theme

            return Column(
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                mainAxisSize = MainAxisSize.MAX,
                spacing = 0,
                children = listOf(
                    Padding(
                        horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
                        vertical = LauncherSpacing.CONTENT_VERTICAL,
                        child = Column(
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                            mainAxisSize = MainAxisSize.MIN,
                            spacing = LauncherSpacing.ROW_SPACING,
                            children = buildInfoColumn(s, t),
                        ),
                    ),
                    Expanded(
                        child = HomeNotificationPanel(
                            notifications = s.notificationItems,
                            theme = t,
                            state = notificationListState,
                            controller = notificationListController,
                            widthPx = (widget.screenWidthPx - LauncherSpacing.CONTENT_HORIZONTAL * 2)
                                .coerceAtLeast(1),
                            onNotificationPressed = widget.onNotificationPressed,
                            onNotificationAction = widget.onNotificationAction,
                        ),
                    ),
                    Padding(
                        padding = EdgeInsets.only(
                            left = LauncherSpacing.CONTENT_HORIZONTAL,
                            right = LauncherSpacing.CONTENT_HORIZONTAL,
                            bottom = LauncherSpacing.CONTENT_VERTICAL,
                        ),
                        child = buildBottomBar(s, t),
                    ),
                ),
            )
        }

        private fun buildBottomBar(s: LauncherUiState, t: LauncherTheme): Widget {
            val media = s.mediaPlayback
            val canScrub = media.hasTrack
            /** HOME 底栏与三个主页面的普通内容共用水平页面边距。 */
            val horizontalInset = LauncherSpacing.CONTENT_HORIZONTAL
            val barWidth = (widget.screenWidthPx - horizontalInset * 2).coerceAtLeast(1)
            val showProgress = isScrubbingMedia && canScrub
            fun startScrub() {
                setState {
                    isScrubbingMedia = true
                    scrubStartProgress = media.progressAt(SystemClock.elapsedRealtime())
                    scrubProgress = scrubStartProgress
                }
            }
            fun updateScrub(delta: Int) {
                setState {
                    scrubProgress = (scrubStartProgress + delta.toFloat() / barWidth.toFloat())
                        .coerceIn(0f, 1f)
                }
            }
            fun endScrub(delta: Int) {
                val target = (scrubStartProgress + delta.toFloat() / barWidth.toFloat())
                    .coerceIn(0f, 1f)
                setState {
                    scrubProgress = target
                    isScrubbingMedia = false
                }
                if (media.canSeek) {
                    widget.onMediaSeek(target)
                }
            }
            val edgeSwipeTarget = if (canScrub) {
                Positioned(
                    left = 0,
                    right = 0,
                    bottom = 0,
                    height = HOME_MEDIA_EDGE_SWIPE_TARGET_HEIGHT_PX,
                    child = GestureDetector(
                        onTap = {},
                        onSwipeStart = ::startScrub,
                        onSwipeUpdate = ::updateScrub,
                        onSwipeEnd = ::endScrub,
                        child = SizedBox(
                            width = barWidth,
                            height = HOME_MEDIA_EDGE_SWIPE_TARGET_HEIGHT_PX,
                        ),
                    ),
                )
            } else {
                null
            }
            val content = Container(
                height = HOME_ACTION_TOTAL_HEIGHT_PX,
                child = Stack(
                    alignment = Alignment.CENTER,
                    children = buildList {
                        add(bottomActionRow(s, t))
                        add(
                            Positioned(
                                left = 0,
                                top = 0,
                                height = HOME_ACTION_TOTAL_HEIGHT_PX,
                                child = AnimatedContainer(
                                    duration = HOME_MEDIA_PROGRESS_ANIMATION_MS.milliseconds,
                                    vsync = widget.vsync,
                                    width = if (showProgress) {
                                        barWidth
                                    } else {
                                        HOME_MEDIA_PROGRESS_COLLAPSED_WIDTH_PX
                                    },
                                    height = HOME_ACTION_TOTAL_HEIGHT_PX,
                                    key = "home-media-progress",
                                    child = HomeMediaProgressBar(
                                        progress = scrubProgress,
                                        width = barWidth,
                                        theme = t,
                                    ),
                                ),
                            ),
                        )
                        edgeSwipeTarget?.let(::add)
                    },
                ),
            )
            return GestureDetector(
                onTap = {},
                onSwipeStart = if (canScrub) {
                    ::startScrub
                } else {
                    null
                },
                onSwipeUpdate = if (canScrub) {
                    ::updateScrub
                } else {
                    null
                },
                onSwipeEnd = if (canScrub) {
                    ::endScrub
                } else {
                    null
                },
                child = content,
            )
        }

        private fun bottomActionRow(s: LauncherUiState, t: LauncherTheme): Widget {
            if (s.mediaPlayback.hasTrack) {
                return HomeMediaBottomBar(
                    media = s.mediaPlayback,
                    missedCallCount = s.missedCallCount,
                    unreadSmsCount = s.unreadSmsCount,
                    theme = t,
                    onOpenCall = widget.onOpenCall,
                    onOpenSms = widget.onOpenSms,
                    onTogglePlayPause = widget.onMediaTogglePlayPause,
                    onSkipPrevious = widget.onMediaSkipPrevious,
                    onSkipNext = widget.onMediaSkipNext,
                )
            }

            val actionButtonStyle = TextButtonStyle(
                textStyle = t.typography.textStyle(
                    color = t.button.text,
                    role = LauncherTextRole.CHROME,
                ),
            )
            val balanceSideActions = s.missedCallCount > 0 || s.unreadSmsCount > 0
            val sideActionWidth = if (balanceSideActions) {
                max(
                    homeActionButtonWidth(label = "CALL", count = s.missedCallCount),
                    homeActionButtonWidth(label = "SMS", count = s.unreadSmsCount),
                )
            } else {
                null
            }
            return Row(
                spacing = 0,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
                children = listOf(
                    HomeActionButton(
                        label = "CALL",
                        count = s.missedCallCount,
                        countOnStart = false,
                        theme = t,
                        onPressed = widget.onOpenCall,
                        style = actionButtonStyle,
                        width = sideActionWidth,
                    ),
                    Expanded(child = SizedBox(width = 0, height = 0)),
                    HomeActionButton(
                        label = "SMS",
                        count = s.unreadSmsCount,
                        countOnStart = true,
                        theme = t,
                        onPressed = widget.onOpenSms,
                        style = actionButtonStyle,
                        width = sideActionWidth,
                    ),
                ),
            )
        }

        private fun buildInfoColumn(s: LauncherUiState, t: LauncherTheme): List<Widget> =
            buildList {
                // Date row (primary color)
                add(
                    homeEdgeText(
                        text = s.currentDateText.ifBlank { "--- --- --" },
                        theme = t,
                        resolveLeadingInkInset = widget.resolveLeadingInkInset,
                    ),
                )
                add(
                    HomeInfoRow(
                        line = HomeInfoModel.weatherLine(s),
                        theme = t,
                        onAction = widget.onInfoAction,
                        onDetail = widget.onInfoDetail,
                        resolveLeadingInkInset = widget.resolveLeadingInkInset,
                    ),
                )
                HomeInfoModel.lines(s).forEach { line ->
                    add(
                        HomeInfoRow(
                            line = line,
                            theme = t,
                            onAction = widget.onInfoAction,
                            onDetail = widget.onInfoDetail,
                            resolveLeadingInkInset = widget.resolveLeadingInkInset,
                        ),
                    )
                }
            }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun HomeInfoRow(
    line: HomeInfoLine,
    theme: LauncherTheme,
    onAction: (HomeInfoAction) -> Unit,
    onDetail: (HomeInfoAction) -> Unit,
    resolveLeadingInkInset: (String) -> Int,
): Widget {
    /** 与 Drawer 左对齐应用名共用真实字形墨迹边界的 HOME 信息文字。 */
    val text = homeEdgeText(
        text = line.text,
        theme = theme,
        resolveLeadingInkInset = resolveLeadingInkInset,
    )
    val action = line.action ?: return text
    return Semantics(
        label = line.text,
        role = PixelSemanticRole.BUTTON,
        enabled = true,
        child = GestureDetector(
            onTap = { onAction(action) },
            onLongPress = { onDetail(action) },
            child = Row(
                spacing = 0,
                children = listOf(
                    Expanded(child = text),
                ),
            ),
        ),
    )
}

/** 构建锚定 HOME 左侧页面边界的单行文字。 */
private fun homeEdgeText(
    /** 实际显示并用于解析首字形的文字。 */
    text: String,
    /** 提供 HOME 主文字颜色的主题。 */
    theme: LauncherTheme,
    /** 返回首字形左侧空白像素数的解析函数。 */
    resolveLeadingInkInset: (String) -> Int,
): Widget = opticallyAlignStartText(
    text = text,
    resolveLeadingInkInset = resolveLeadingInkInset,
    child = Text(
        text,
        style = TextStyle(color = theme.text.primary),
        overflow = TextOverflow.ELLIPSIS,
        softWrap = false,
        maxLines = 1,
    ),
)

private fun HomeNotificationPanel(
    notifications: List<NotificationSignal>,
    theme: LauncherTheme,
    state: PixelListState,
    controller: PixelListController,
    widthPx: Int,
    onNotificationPressed: (String) -> Unit,
    onNotificationAction: (String, Int) -> Unit,
): Widget {
    if (notifications.isEmpty()) {
        return SizedBox(width = 0, height = 0)
    }
    return Padding(
        horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
        child = ListViewBuilder(
            itemCount = notifications.size,
            state = state,
            controller = controller,
            spacing = LauncherSpacing.ROW_SPACING,
            estimatedItemExtent = HOME_NOTIFICATION_ESTIMATED_ITEM_HEIGHT_PX,
            key = "home-notifications",
            itemBuilder = { index ->
                HomeNotificationItem(
                    item = notifications[index],
                    theme = theme,
                    widthPx = widthPx,
                    onNotificationPressed = onNotificationPressed,
                    onNotificationAction = onNotificationAction,
                )
            },
        ),
    )
}

private fun HomeNotificationItem(
    item: NotificationSignal,
    theme: LauncherTheme,
    widthPx: Int,
    onNotificationPressed: (String) -> Unit,
    onNotificationAction: (String, Int) -> Unit,
): Widget {
    val title = homeNotificationTitle(item)
    val bodyLines = homeNotificationBodyLines(item, title)
    val hiddenLineCount = homeNotificationHiddenLineCount(item, title, bodyLines.size)
    val content = Container(
        padding = EdgeInsets.all(HOME_NOTIFICATION_PADDING_PX),
        child = Column(
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            mainAxisSize = MainAxisSize.MIN,
            spacing = HOME_NOTIFICATION_INNER_SPACING_PX,
            children = buildList {
                add(homeNotificationHeader(item, theme))
                if (title.isNotBlank()) {
                    add(
                        Text(
                            title,
                            style = TextStyle(color = theme.text.primary),
                            overflow = TextOverflow.ELLIPSIS,
                            softWrap = false,
                            maxLines = 1,
                        ),
                    )
                }
                bodyLines.forEach { line ->
                    add(
                        Text(
                            line,
                            style = TextStyle(color = theme.text.muted),
                            overflow = TextOverflow.ELLIPSIS,
                            softWrap = false,
                            maxLines = 1,
                        ),
                    )
                }
                if (hiddenLineCount > 0) {
                    add(
                        Text(
                            "+$hiddenLineCount",
                            style = TextStyle(color = theme.text.muted),
                            overflow = TextOverflow.ELLIPSIS,
                            softWrap = false,
                            maxLines = 1,
                        ),
                    )
                }
                homeNotificationProgress(item, widthPx, theme)?.let(::add)
                homeNotificationActions(item, theme, onNotificationAction)?.let(::add)
            },
        ),
    )
    val pressableContent = if (item.key.isBlank()) {
        content
    } else {
        GestureDetector(
            onTap = { onNotificationPressed(item.key) },
            child = content,
        )
    }
    return Semantics(
        label = homeNotificationSemanticLabel(item),
        role = PixelSemanticRole.BUTTON,
        enabled = item.key.isNotBlank(),
        child = pressableContent,
    )
}

private fun homeNotificationHeader(item: NotificationSignal, theme: LauncherTheme): Widget =
    Row(
        spacing = HOME_NOTIFICATION_HEADER_GAP_PX,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
        children = listOf(
            Expanded(
                child = Text(
                    item.sourceLabel.trim().ifEmpty { item.sourceId },
                    style = TextStyle(color = theme.button.text),
                    overflow = TextOverflow.ELLIPSIS,
                    softWrap = false,
                    maxLines = 1,
                ),
            ),
            Text(
                homeNotificationTime(item.postedAtMillis),
                style = TextStyle(color = theme.text.muted),
                overflow = TextOverflow.ELLIPSIS,
                softWrap = false,
                maxLines = 1,
            ),
        ),
    )

private fun homeNotificationActions(
    item: NotificationSignal,
    theme: LauncherTheme,
    onNotificationAction: (String, Int) -> Unit,
): Widget? {
    if (item.key.isBlank() || item.actions.isEmpty()) return null
    val visibleActions = item.actions.take(HOME_NOTIFICATION_MAX_ACTIONS)
    return Row(
        spacing = HOME_ACTION_DIVIDER_PX,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
        children = visibleActions.map { action ->
            Expanded(
                child = homeNotificationActionButton(
                    itemKey = item.key,
                    action = action,
                    theme = theme,
                    onNotificationAction = onNotificationAction,
                ),
            )
        },
    )
}

private fun homeNotificationActionButton(
    itemKey: String,
    action: NotificationActionInfo,
    theme: LauncherTheme,
    onNotificationAction: (String, Int) -> Unit,
): Widget {
    val enabled = !action.requiresInput
    val content = Container(
        height = HOME_ACTION_SEGMENT_HEIGHT_PX,
        fillColor = if (enabled) theme.button.border else theme.surface.panelSubtle,
        alignment = Alignment.CENTER,
        padding = EdgeInsets.symmetric(horizontal = HOME_ACTION_SEGMENT_HORIZONTAL_PADDING_PX),
        child = Text(
            action.title.uppercase(Locale.getDefault()),
            style = theme.typography.textStyle(
                color = if (enabled) theme.surface.offPixelColor else theme.button.disabledText,
                role = LauncherTextRole.CHROME,
            ),
            overflow = TextOverflow.ELLIPSIS,
            softWrap = false,
            maxLines = 1,
        ),
    )
    return Semantics(
        label = action.title,
        role = PixelSemanticRole.BUTTON,
        enabled = enabled,
        child = if (enabled) {
            GestureDetector(
                onTap = { onNotificationAction(itemKey, action.index) },
                child = content,
            )
        } else {
            content
        },
    )
}

private fun homeNotificationProgress(
    item: NotificationSignal,
    widthPx: Int,
    theme: LauncherTheme,
): Widget? {
    val progress = item.progress
    if (!progress.indeterminate && progress.max <= 0) return null
    val progressText = when {
        progress.indeterminate -> "..."
        progress.max > 0 -> "${((progress.value.coerceIn(0, progress.max).toFloat() / progress.max) * 100f).roundToInt()}%"
        else -> ""
    }
    return Row(
        spacing = HOME_NOTIFICATION_HEADER_GAP_PX,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
        children = listOf(
            HomeNotificationProgressBar(
                progress = if (progress.indeterminate || progress.max <= 0) {
                    HOME_NOTIFICATION_INDETERMINATE_PROGRESS
                } else {
                    progress.value.toFloat() / progress.max.toFloat()
                },
                width = (widthPx - HOME_NOTIFICATION_PROGRESS_TEXT_WIDTH_PX).coerceAtLeast(1),
                theme = theme,
            ),
            Text(
                progressText,
                style = TextStyle(color = theme.text.muted),
                overflow = TextOverflow.ELLIPSIS,
                softWrap = false,
                maxLines = 1,
            ),
        ),
    )
}

private fun HomeNotificationProgressBar(
    progress: Float,
    width: Int,
    theme: LauncherTheme,
): Widget = CustomPaint(
    width = width.coerceAtLeast(1),
    height = HOME_NOTIFICATION_PROGRESS_HEIGHT_PX,
) {
    val safeWidth = this.width
    val safeHeight = this.height
    if (safeWidth <= 0 || safeHeight <= 0) return@CustomPaint
    val innerWidth = (safeWidth - 2).coerceAtLeast(0)
    val innerHeight = (safeHeight - 2).coerceAtLeast(0)
    val fillWidth = (innerWidth * progress.coerceIn(0f, 1f)).roundToInt()
        .coerceIn(0, innerWidth)
    if (fillWidth > 0 && innerHeight > 0) {
        fillRect(1, 1, fillWidth, innerHeight, theme.button.border)
    }
    drawRect(0, 0, safeWidth, safeHeight, theme.button.border)
}

private fun homeNotificationTitle(item: NotificationSignal): String {
    return item.title.trim()
        .ifEmpty { item.summaryText.trim() }
}

private fun homeNotificationBodyLines(item: NotificationSignal, title: String): List<String> {
    return homeNotificationAllBodyLines(item, title)
        .take(HOME_NOTIFICATION_MAX_BODY_LINES)
}

private fun homeNotificationHiddenLineCount(
    item: NotificationSignal,
    title: String,
    visibleLineCount: Int,
): Int = (homeNotificationAllBodyLines(item, title).size - visibleLineCount).coerceAtLeast(0)

private fun homeNotificationAllBodyLines(item: NotificationSignal, title: String): List<String> {
    val normalizedTitle = title.trim()
    return buildList {
        addAll(item.textLines)
        add(item.bigText)
        add(item.text)
        add(item.subText)
        add(item.category)
        if (item.isOngoing) add("ONGOING")
        if (item.isSilent) add("SILENT")
        if (!item.isClearable) add("LOCKED")
    }
        .map { line -> line.trim() }
        .filter { line -> line.isNotEmpty() && line != normalizedTitle }
        .distinct()
}

private fun homeNotificationSemanticLabel(item: NotificationSignal): String {
    return listOf(item.sourceLabel, item.title, item.text)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString(" ")
        .ifEmpty { item.sourceId }
}

private fun homeNotificationTime(postedAtMillis: Long): String {
    if (postedAtMillis <= 0L) return "--:--"
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(postedAtMillis))
}

private fun HomeActionButton(
    label: String,
    count: Int,
    countOnStart: Boolean,
    theme: LauncherTheme,
    onPressed: () -> Unit,
    style: TextButtonStyle,
    width: Int? = null,
): Widget {
    if (count <= 0 && width == null) {
        return TextButton(
            text = label,
            onPressed = onPressed,
            style = style,
        )
    }

    val labelSegment = homeActionSegment(
        text = label,
        textStyle = theme.typography.textStyle(
            color = theme.button.text,
            role = LauncherTextRole.CHROME,
        ),
        fillColor = null,
    )
    val labelChild = if (width != null) {
        Expanded(child = labelSegment)
    } else {
        labelSegment
    }
    val children = if (count > 0) {
        val countSegment = homeActionSegment(
            text = count.toString(),
            textStyle = theme.typography.textStyle(
                color = theme.surface.offPixelColor,
                role = LauncherTextRole.CHROME,
            ),
            fillColor = theme.button.border,
        )
        val divider = homeActionDivider(theme)
        if (countOnStart) {
            listOf(countSegment, divider, labelChild)
        } else {
            listOf(labelChild, divider, countSegment)
        }
    } else {
        listOf(labelChild)
    }

    return Semantics(
        label = if (count > 0) "$label $count" else label,
        role = PixelSemanticRole.BUTTON,
        enabled = true,
        child = GestureDetector(
            onTap = onPressed,
            child = Container(
                width = width,
                height = HOME_ACTION_TOTAL_HEIGHT_PX,
                borderColor = theme.button.border,
                padding = EdgeInsets.all(HOME_ACTION_BORDER_PX),
                child = Row(
                    spacing = 0,
                    mainAxisSize = if (width == null) MainAxisSize.MIN else MainAxisSize.MAX,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                    children = children,
                ),
            ),
        ),
    )
}

private fun HomeMediaBottomBar(
    media: MediaPlaybackSnapshot,
    missedCallCount: Int,
    unreadSmsCount: Int,
    theme: LauncherTheme,
    onOpenCall: () -> Unit,
    onOpenSms: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
): Widget {
    return Container(
        height = HOME_ACTION_TOTAL_HEIGHT_PX,
        borderColor = theme.button.border,
        padding = EdgeInsets.all(HOME_ACTION_BORDER_PX),
        child = Row(
            spacing = 0,
            mainAxisSize = MainAxisSize.MAX,
            crossAxisAlignment = CrossAxisAlignment.CENTER,
            children = listOf(
                Expanded(
                    child = HomeMediaSideAction(
                        label = "CALL",
                        count = missedCallCount,
                        countOnStart = false,
                        theme = theme,
                        onPressed = onOpenCall,
                    ),
                ),
                mediaControlDivider(theme),
                Expanded(
                    child = mediaControlSegment(
                        icon = MediaControlIcon.PREVIOUS,
                        semanticLabel = "PREVIOUS",
                        enabled = media.canSkipPrevious,
                        filled = true,
                        theme = theme,
                        onPressed = onSkipPrevious,
                    ),
                ),
                mediaControlDivider(theme),
                Expanded(
                    child = mediaControlSegment(
                        icon = if (media.isPlaying) MediaControlIcon.PAUSE else MediaControlIcon.PLAY,
                        semanticLabel = if (media.isPlaying) "PAUSE" else "PLAY",
                        enabled = media.canPlayPause,
                        filled = false,
                        theme = theme,
                        onPressed = onTogglePlayPause,
                    ),
                ),
                mediaControlDivider(theme),
                Expanded(
                    child = mediaControlSegment(
                        icon = MediaControlIcon.NEXT,
                        semanticLabel = "NEXT",
                        enabled = media.canSkipNext,
                        filled = true,
                        theme = theme,
                        onPressed = onSkipNext,
                    ),
                ),
                mediaControlDivider(theme),
                Expanded(
                    child = HomeMediaSideAction(
                        label = "SMS",
                        count = unreadSmsCount,
                        countOnStart = true,
                        theme = theme,
                        onPressed = onOpenSms,
                    ),
                ),
            ),
        ),
    )
}

private fun HomeMediaSideAction(
    label: String,
    count: Int,
    countOnStart: Boolean,
    theme: LauncherTheme,
    onPressed: () -> Unit,
): Widget {
    val labelText = homeMediaSideText(label, theme)
    val countText = if (count > 0) {
        homeMediaSideText(count.toString(), theme)
    } else {
        null
    }
    val spacer = Expanded(child = SizedBox(width = 0, height = HOME_ACTION_SEGMENT_HEIGHT_PX))
    val children = if (countOnStart) {
        buildList {
            countText?.let(::add)
            add(spacer)
            add(labelText)
        }
    } else {
        buildList {
            add(labelText)
            add(spacer)
            countText?.let(::add)
        }
    }
    return Semantics(
        label = if (count > 0) "$label $count" else label,
        role = PixelSemanticRole.BUTTON,
        enabled = true,
        child = GestureDetector(
            onTap = onPressed,
            child = Container(
                height = HOME_ACTION_SEGMENT_HEIGHT_PX,
                padding = EdgeInsets.symmetric(horizontal = HOME_ACTION_SEGMENT_HORIZONTAL_PADDING_PX),
                child = Row(
                    spacing = 0,
                    mainAxisSize = MainAxisSize.MAX,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                    children = children,
                ),
            ),
        ),
    )
}

private fun homeMediaSideText(text: String, theme: LauncherTheme): Widget =
    Text(
        text,
        style = theme.typography.textStyle(
            color = theme.button.text,
            role = LauncherTextRole.CHROME,
        ),
        overflow = TextOverflow.ELLIPSIS,
        softWrap = false,
        maxLines = 1,
    )

private fun mediaControlSegment(
    icon: MediaControlIcon,
    semanticLabel: String,
    enabled: Boolean,
    filled: Boolean,
    theme: LauncherTheme,
    onPressed: () -> Unit,
): Widget {
    val fillColor = when {
        filled -> theme.button.border
        else -> null
    }
    val content = Container(
        height = HOME_ACTION_SEGMENT_HEIGHT_PX,
        fillColor = if (enabled) fillColor else fillColor?.withAlpha(HOME_MEDIA_DISABLED_FILL_ALPHA),
        alignment = Alignment.CENTER,
        child = mediaControlIcon(
            icon = icon,
            color = when {
                filled -> theme.surface.offPixelColor
                !enabled -> theme.button.disabledText
                else -> theme.button.text
            },
        ),
    )
    val button = if (enabled) {
        GestureDetector(
            onTap = onPressed,
            child = content,
        )
    } else {
        content
    }
    return Semantics(
        label = semanticLabel,
        role = PixelSemanticRole.BUTTON,
        enabled = enabled,
        child = button,
    )
}

private fun mediaControlDivider(theme: LauncherTheme): Widget = Container(
    width = HOME_MEDIA_CONTROL_DIVIDER_PX,
    height = HOME_ACTION_SEGMENT_HEIGHT_PX,
    fillColor = theme.button.border,
)

private fun HomeMediaProgressBar(
    progress: Float,
    width: Int,
    theme: LauncherTheme,
): Widget = CustomPaint(
    width = width.coerceAtLeast(1),
    height = HOME_ACTION_TOTAL_HEIGHT_PX,
) {
    val safeWidth = this.width
    val safeHeight = this.height
    if (safeWidth <= 0 || safeHeight <= 0) {
        return@CustomPaint
    }
    fillRect(0, 0, safeWidth, safeHeight, theme.button.pressedFill)
    val innerWidth = (safeWidth - 2).coerceAtLeast(0)
    val innerHeight = (safeHeight - 2).coerceAtLeast(0)
    val fillWidth = (innerWidth * progress.coerceIn(0f, 1f)).roundToInt()
        .coerceIn(0, innerWidth)
    if (fillWidth > 0 && innerHeight > 0) {
        fillRect(1, 1, fillWidth, innerHeight, theme.button.border)
    }
    drawRect(0, 0, safeWidth, safeHeight, theme.button.border)
}

private fun mediaControlIcon(
    icon: MediaControlIcon,
    color: PixelColor,
): Widget = CustomPaint(
    width = HOME_MEDIA_CONTROL_ICON_WIDTH_PX,
    height = HOME_MEDIA_CONTROL_ICON_HEIGHT_PX,
) {
    when (icon) {
        MediaControlIcon.PREVIOUS -> {
            fillRect(1, 1, 1, 9, color)
            drawPolygon(
                points = listOf(
                    PixelPoint(3, 5),
                    PixelPoint(10, 1),
                    PixelPoint(10, 9),
                ),
                color = color,
                filled = true,
            )
        }

        MediaControlIcon.PLAY -> {
            drawPolygon(
                points = listOf(
                    PixelPoint(3, 1),
                    PixelPoint(3, 9),
                    PixelPoint(10, 5),
                ),
                color = color,
                filled = true,
            )
        }

        MediaControlIcon.PAUSE -> {
            fillRect(3, 1, 2, 9, color)
            fillRect(8, 1, 2, 9, color)
        }

        MediaControlIcon.NEXT -> {
            drawPolygon(
                points = listOf(
                    PixelPoint(2, 1),
                    PixelPoint(2, 9),
                    PixelPoint(9, 5),
                ),
                color = color,
                filled = true,
            )
            fillRect(11, 1, 1, 9, color)
        }
    }
}

private fun homeActionSegment(
    text: String,
    textStyle: TextStyle,
    fillColor: PixelColor?,
): Widget = Container(
    height = HOME_ACTION_SEGMENT_HEIGHT_PX,
    fillColor = fillColor,
    padding = EdgeInsets.symmetric(horizontal = HOME_ACTION_SEGMENT_HORIZONTAL_PADDING_PX),
    alignment = Alignment.CENTER,
    child = Text(
        text,
        style = textStyle,
        overflow = TextOverflow.ELLIPSIS,
        softWrap = false,
        maxLines = 1,
    ),
)

private fun homeActionDivider(theme: LauncherTheme): Widget = Container(
    width = HOME_ACTION_DIVIDER_PX,
    height = HOME_ACTION_SEGMENT_HEIGHT_PX,
    fillColor = theme.button.border,
)

private fun homeActionButtonWidth(label: String, count: Int): Int {
    val labelWidth = homeActionSegmentWidth(label)
    val contentWidth = if (count > 0) {
        labelWidth + HOME_ACTION_DIVIDER_PX + homeActionSegmentWidth(count.toString())
    } else {
        labelWidth
    }
    return contentWidth + (HOME_ACTION_BORDER_PX * 2)
}

private fun homeActionSegmentWidth(text: String): Int {
    val metrics = PixelFontCatalog.metrics(PixelFontCatalog.defaultUiFontSelection)
    val conservativeTextWidth = text.length * metrics.wideAdvanceWidth
    return max(PixelFontCatalog.estimatedTextWidth(text), conservativeTextWidth) +
        (HOME_ACTION_SEGMENT_HORIZONTAL_PADDING_PX * 2)
}

private enum class MediaControlIcon {
    PREVIOUS,
    PLAY,
    PAUSE,
    NEXT,
}

private fun PixelColor.withAlpha(alpha: Int): PixelColor = PixelColor.fromArgb(
    a = alpha.coerceIn(0, 255),
    r = red,
    g = green,
    b = blue,
)

private const val HOME_ACTION_BORDER_PX = LauncherChromeLayout.sharedBorderPx
private const val HOME_ACTION_DIVIDER_PX = 1
private const val HOME_ACTION_SEGMENT_HEIGHT_PX = LauncherChromeLayout.sharedSegmentHeightPx
private const val HOME_ACTION_TOTAL_HEIGHT_PX = LauncherChromeLayout.sharedRowHeightPx
private const val HOME_ACTION_SEGMENT_HORIZONTAL_PADDING_PX = 2
private const val HOME_MEDIA_CONTROL_DIVIDER_PX = 1
private const val HOME_MEDIA_EDGE_SWIPE_TARGET_HEIGHT_PX = 2
private const val HOME_MEDIA_PROGRESS_COLLAPSED_WIDTH_PX = 0
private const val HOME_MEDIA_PROGRESS_ANIMATION_MS = 120
private const val HOME_MEDIA_DISABLED_FILL_ALPHA = 128
private const val HOME_NOTIFICATION_PADDING_PX = 1
private const val HOME_NOTIFICATION_INNER_SPACING_PX = 1
private const val HOME_NOTIFICATION_HEADER_GAP_PX = 2
private const val HOME_NOTIFICATION_MAX_BODY_LINES = 4
private const val HOME_NOTIFICATION_MAX_ACTIONS = 3
private const val HOME_NOTIFICATION_ESTIMATED_ITEM_HEIGHT_PX = 42
private const val HOME_NOTIFICATION_PROGRESS_HEIGHT_PX = 5
private const val HOME_NOTIFICATION_PROGRESS_TEXT_WIDTH_PX = 18
private const val HOME_NOTIFICATION_INDETERMINATE_PROGRESS = 0.5f
private const val HOME_MEDIA_CONTROL_ICON_WIDTH_PX = 13
private const val HOME_MEDIA_CONTROL_ICON_HEIGHT_PX = 11
