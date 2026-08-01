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
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionTheme
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.Positioned
import com.purride.pixelui.Row
import com.purride.pixelui.Semantics
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Stack
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextButtonStyle
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Transform
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.IntTween
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.animation.IntOffset
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.widgets.animated.AnimatedContainer
import com.purride.pixelui.widgets.animated.TweenAnimationBuilder
import com.purride.pixellauncherv2.launcher.HomeInfoAction
import com.purride.pixellauncherv2.launcher.HomeInfoLine
import com.purride.pixellauncherv2.launcher.HomeInfoModel
import com.purride.pixellauncherv2.launcher.LauncherChromeGeometry
import com.purride.pixellauncherv2.launcher.LauncherChromeLayout
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.launcher.MediaPlaybackSnapshot
import com.purride.pixellauncherv2.launcher.NotificationActionInfo
import com.purride.pixellauncherv2.launcher.NotificationSignal
import com.purride.pixellauncherv2.launcher.LauncherTextRole
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.text.opticallyAlignStartText
import com.purride.pixellauncherv2.viewmodel.LauncherUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration
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
 * @param measureChromeTextWidth 使用当前 CHROME face 的实际栅格器测量文字宽度
 * @param chromeGeometry 当前 CHROME face 驱动的共享边框几何
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
    private val measureChromeTextWidth: (String) -> Int,
    private val chromeGeometry: LauncherChromeGeometry,
    override val key: Any? = null,
) : StatefulWidget(key = key) {

    override fun createState(): State<out StatefulWidget> = HomeState()

    private inner class HomeState : State<HomeScreen>() {
        private var isScrubbingMedia = false
        private var scrubStartProgress = 0f
        private var scrubProgress = 0f
        /** 每次点击上一曲时递增，用于触发不依赖媒体状态回传的方向反馈。 */
        private var previousSkipFeedbackTrigger = 0
        /** 每次点击下一曲时递增，用于触发不依赖媒体状态回传的方向反馈。 */
        private var nextSkipFeedbackTrigger = 0
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
                            chromeGeometry = widget.chromeGeometry,
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
                height = widget.chromeGeometry.rowHeightPx,
                child = Stack(
                    alignment = Alignment.CENTER,
                    children = buildList {
                        add(bottomActionRow(s, t))
                        add(
                            Positioned(
                                left = 0,
                                top = 0,
                                height = widget.chromeGeometry.rowHeightPx,
                                child = AnimatedContainer(
                                    duration = HOME_MEDIA_PROGRESS_ANIMATION_MS.milliseconds,
                                    vsync = widget.vsync,
                                    width = if (showProgress) {
                                        barWidth
                                    } else {
                                        HOME_MEDIA_PROGRESS_COLLAPSED_WIDTH_PX
                                    },
                                    height = widget.chromeGeometry.rowHeightPx,
                                    key = "home-media-progress",
                                    child = HomeMediaProgressBar(
                                        progress = scrubProgress,
                                        width = barWidth,
                                        theme = t,
                                        chromeGeometry = widget.chromeGeometry,
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
                    previousSkipFeedbackTrigger = previousSkipFeedbackTrigger,
                    nextSkipFeedbackTrigger = nextSkipFeedbackTrigger,
                    onSkipPrevious = {
                        setState { previousSkipFeedbackTrigger += 1 }
                        widget.onMediaSkipPrevious()
                    },
                    onSkipNext = {
                        setState { nextSkipFeedbackTrigger += 1 }
                        widget.onMediaSkipNext()
                    },
                    chromeGeometry = widget.chromeGeometry,
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
                    homeActionButtonWidth(
                        label = "CALL",
                        count = s.missedCallCount,
                        measureTextWidth = widget.measureChromeTextWidth,
                    ),
                    homeActionButtonWidth(
                        label = "SMS",
                        count = s.unreadSmsCount,
                        measureTextWidth = widget.measureChromeTextWidth,
                    ),
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
                        chromeGeometry = widget.chromeGeometry,
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
                        chromeGeometry = widget.chromeGeometry,
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
    chromeGeometry: LauncherChromeGeometry,
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
                    chromeGeometry = chromeGeometry,
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
    chromeGeometry: LauncherChromeGeometry,
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
                homeNotificationActions(item, theme, onNotificationAction, chromeGeometry)?.let(::add)
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
    chromeGeometry: LauncherChromeGeometry,
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
                    chromeGeometry = chromeGeometry,
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
    chromeGeometry: LauncherChromeGeometry,
): Widget {
    val enabled = !action.requiresInput
    val content = Container(
        height = chromeGeometry.segmentHeightPx,
        fillColor = if (enabled) theme.button.filledSurface else theme.surface.panelSubtle,
        alignment = Alignment.CENTER,
        padding = EdgeInsets.symmetric(horizontal = HOME_ACTION_SEGMENT_HORIZONTAL_PADDING_PX),
        child = Text(
            action.title.uppercase(Locale.getDefault()),
            style = theme.typography.textStyle(
                color = if (enabled) theme.button.filledText else theme.button.disabledText,
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
    chromeGeometry: LauncherChromeGeometry,
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
        chromeGeometry = chromeGeometry,
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
                color = theme.button.filledText,
                role = LauncherTextRole.CHROME,
            ),
            fillColor = theme.button.filledSurface,
            chromeGeometry = chromeGeometry,
        )
        val divider = homeActionDivider(theme, chromeGeometry)
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
                height = chromeGeometry.rowHeightPx,
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
    previousSkipFeedbackTrigger: Int,
    nextSkipFeedbackTrigger: Int,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    chromeGeometry: LauncherChromeGeometry,
): Widget {
    return Container(
        height = chromeGeometry.rowHeightPx,
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
                        chromeGeometry = chromeGeometry,
                    ),
                ),
                mediaControlDivider(theme, chromeGeometry),
                Expanded(
                    child = mediaControlSegment(
                        iconBuilder = { color ->
                            AnimatedMediaSkipIcon(
                                icon = MediaControlIcon.PREVIOUS,
                                color = color,
                                activationTrigger = previousSkipFeedbackTrigger,
                                key = HOME_MEDIA_PREVIOUS_FEEDBACK_KEY,
                            )
                        },
                        semanticLabel = "PREVIOUS",
                        enabled = media.canSkipPrevious,
                        filled = true,
                        theme = theme,
                        onPressed = onSkipPrevious,
                        chromeGeometry = chromeGeometry,
                    ),
                ),
                mediaControlDivider(theme, chromeGeometry),
                Expanded(
                    child = mediaControlSegment(
                        iconBuilder = { color ->
                            AnimatedMediaPlayPauseIcon(
                                isPlaying = media.isPlaying,
                                color = color,
                                key = HOME_MEDIA_PLAY_PAUSE_MORPH_KEY,
                            )
                        },
                        semanticLabel = if (media.isPlaying) "PAUSE" else "PLAY",
                        enabled = media.canPlayPause,
                        filled = false,
                        theme = theme,
                        onPressed = onTogglePlayPause,
                        chromeGeometry = chromeGeometry,
                    ),
                ),
                mediaControlDivider(theme, chromeGeometry),
                Expanded(
                    child = mediaControlSegment(
                        iconBuilder = { color ->
                            AnimatedMediaSkipIcon(
                                icon = MediaControlIcon.NEXT,
                                color = color,
                                activationTrigger = nextSkipFeedbackTrigger,
                                key = HOME_MEDIA_NEXT_FEEDBACK_KEY,
                            )
                        },
                        semanticLabel = "NEXT",
                        enabled = media.canSkipNext,
                        filled = true,
                        theme = theme,
                        onPressed = onSkipNext,
                        chromeGeometry = chromeGeometry,
                    ),
                ),
                mediaControlDivider(theme, chromeGeometry),
                Expanded(
                    child = HomeMediaSideAction(
                        label = "SMS",
                        count = unreadSmsCount,
                        countOnStart = true,
                        theme = theme,
                        onPressed = onOpenSms,
                        chromeGeometry = chromeGeometry,
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
    chromeGeometry: LauncherChromeGeometry,
): Widget {
    val labelText = homeMediaSideText(label, theme)
    val countText = if (count > 0) {
        homeMediaSideText(count.toString(), theme)
    } else {
        null
    }
    val spacer = Expanded(child = SizedBox(width = 0, height = chromeGeometry.segmentHeightPx))
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
                height = chromeGeometry.segmentHeightPx,
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
    iconBuilder: (PixelColor) -> Widget,
    semanticLabel: String,
    enabled: Boolean,
    filled: Boolean,
    theme: LauncherTheme,
    onPressed: () -> Unit,
    chromeGeometry: LauncherChromeGeometry,
): Widget {
    val fillColor = when {
        filled -> theme.button.filledSurface
        else -> null
    }
    /** 当前启用、填充状态解析出的图标颜色。 */
    val iconColor = when {
        filled -> theme.button.filledText
        !enabled -> theme.button.disabledText
        else -> theme.button.text
    }
    val content = Container(
        height = chromeGeometry.segmentHeightPx,
        fillColor = if (enabled) fillColor else fillColor?.withAlpha(HOME_MEDIA_DISABLED_FILL_ALPHA),
        alignment = Alignment.CENTER,
        child = iconBuilder(iconColor),
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

private fun mediaControlDivider(
    theme: LauncherTheme,
    chromeGeometry: LauncherChromeGeometry,
): Widget = Container(
    width = HOME_MEDIA_CONTROL_DIVIDER_PX,
    height = chromeGeometry.segmentHeightPx,
    fillColor = theme.button.border,
)

private fun HomeMediaProgressBar(
    progress: Float,
    width: Int,
    theme: LauncherTheme,
    chromeGeometry: LauncherChromeGeometry,
): Widget = CustomPaint(
    width = width.coerceAtLeast(1),
    height = chromeGeometry.rowHeightPx,
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

/** 上一曲和下一曲点击后沿操作方向位移再回弹，媒体回调延迟不会影响本地反馈。 */
private data class AnimatedMediaSkipIcon(
    /** 决定图标形状与水平位移方向。 */
    val icon: MediaControlIcon,
    /** 当前按钮状态解析出的图标颜色。 */
    val color: PixelColor,
    /** 父级每次成功接收点击时变化的触发值。 */
    val activationTrigger: Int,
    /** 保持各方向动画 State 稳定复用的键。 */
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** 为单个跳曲图标创建独立且可释放的反馈动画状态。 */
    override fun createState(): State<out StatefulWidget> = AnimatedMediaSkipIconState()
}

/** 管理跳曲图标的一次性像素位移反馈，并在连续点击时从当前位移重新发力。 */
private class AnimatedMediaSkipIconState : State<AnimatedMediaSkipIcon>() {
    /** 当前一次性反馈使用的归一化动画控制器。 */
    private var controller: PixelAnimationController? = null
    /** 控制器对应的共享 ticker，用于识别 Motion 环境变化。 */
    private var controllerVsync: PixelTickerProvider? = null
    /** 控制器对应的已解析反馈时长。 */
    private var controllerDuration: Duration = Duration.ZERO
    /** 标记父级触发值变化，延迟到 build 获得最新 Motion 环境后启动。 */
    private var pulsePending = false

    /** 捕获新的点击触发，不依赖播放服务是否及时返回新快照。 */
    override fun didUpdateWidget(oldWidget: AnimatedMediaSkipIcon) {
        if (widget.activationTrigger != oldWidget.activationTrigger) {
            pulsePending = true
        }
    }

    /** 解析统一反馈动效并绘制当前像素位移帧。 */
    override fun build(context: BuildContext): Widget {
        /** Host 提供的统一 ticker 与系统动画偏好。 */
        val motionScope = PixelMotionScope.maybeOf(context)
        /** 点击反馈角色会在减少动态效果时自动变为即时完成。 */
        val motion = motionScope?.let { scope ->
            PixelMotionTheme.of(context).feedback.resolve(scope.settings)
        }
        /** 当前环境是否允许执行这次非必要方向位移。 */
        val canAnimate = motionScope != null && motion != null && !motion.isImmediate
        if (!canAnimate) {
            disposeController()
            pulsePending = false
        } else {
            ensureController(vsync = motionScope.vsync, duration = motion.duration)
            if (pulsePending) {
                pulsePending = false
                restartPulseFromCurrentOffset()
            }
        }

        /** 当前归一化进度，完成态映射回零位移。 */
        val progress = controller?.value ?: 0f
        context.watch(controller)
        return Transform.translate(
            offset = IntOffset(
                x = mediaSkipFeedbackOffset(
                    progress = progress,
                    direction = widget.icon.horizontalDirection,
                ),
                y = 0,
            ),
            child = mediaControlIcon(icon = widget.icon, color = widget.color),
        )
    }

    /** Motion 环境变化时替换控制器，避免沿用错误时长或 ticker。 */
    private fun ensureController(vsync: PixelTickerProvider, duration: Duration) {
        if (controller != null && controllerVsync === vsync && controllerDuration == duration) return
        disposeController()
        controllerVsync = vsync
        controllerDuration = duration
        controller = PixelAnimationController(duration = duration, vsync = vsync)
    }

    /** 连续点击时把回程进度镜像到出程，保持当前位移且重新向峰值运动。 */
    private fun restartPulseFromCurrentOffset() {
        val activeController = controller ?: return
        /** 三角脉冲在前后半程的同值镜像点。 */
        val restartProgress = if (activeController.isAnimating) {
            minOf(activeController.value, 1f - activeController.value)
        } else {
            0f
        }
        activeController.forward(from = restartProgress)
    }

    /** 释放当前控制器和共享 ticker 引用。 */
    private fun disposeController() {
        controller?.dispose()
        controller = null
        controllerVsync = null
        controllerDuration = Duration.ZERO
    }

    /** 页面移除图标时释放动画资源。 */
    override fun dispose() {
        disposeController()
    }
}

/** 将一次反馈进度映射为“移出—回弹”的整数像素位移。 */
internal fun mediaSkipFeedbackOffset(progress: Float, direction: Int): Int {
    /** 非法进度回退到静止端点，有限进度限制到动画区间。 */
    val safeProgress = progress.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    /** 以半程为峰值的对称三角脉冲，保证首尾都严格回到原位。 */
    val pulse = 1f - abs(safeProgress * 2f - 1f)
    /** 方向只允许取符号，避免调用方意外放大位移。 */
    val safeDirection = direction.compareTo(0)
    return (pulse * HOME_MEDIA_SKIP_FEEDBACK_DISTANCE_PX).roundToInt() * safeDirection
}

/** 播放与暂停之间的离散像素形变，状态变化时由隐式动画从当前帧连续重定向。 */
private data class AnimatedMediaPlayPauseIcon(
    /** true 表示目标为暂停双竖线，false 表示目标为播放三角形。 */
    val isPlaying: Boolean,
    /** 当前主题解析出的图标颜色。 */
    val color: PixelColor,
    /** 保持隐式动画 State 跨播放状态更新稳定复用的键。 */
    override val key: Any?,
) : StatelessWidget(key = key) {
    /** 根据宿主动画偏好选择同步终态或离散形变动画。 */
    override fun build(context: BuildContext): Widget {
        /** 播放中显示暂停图标，对应形变终点。 */
        val targetStep = if (isPlaying) HOME_MEDIA_PLAY_PAUSE_MORPH_STEPS else 0
        /** Host 提供的统一 ticker 与系统动画设置。 */
        val motionScope = PixelMotionScope.maybeOf(context)
        /** selection 角色在减少动态效果时会自动变为即时切换。 */
        val motion = motionScope?.let { scope ->
            PixelMotionTheme.of(context).selection.resolve(scope.settings)
        }
        if (motionScope == null || motion == null || motion.isImmediate) {
            return mediaPlayPauseMorphIcon(step = targetStep, color = color)
        }
        return TweenAnimationBuilder(
            tween = IntTween(begin = targetStep, end = targetStep),
            duration = motion.duration,
            curve = motion.curve,
            vsync = motionScope.vsync,
            key = "$key-tween",
            builder = { _, step -> mediaPlayPauseMorphIcon(step = step, color = color) },
        )
    }
}

/** 播放三角形向左暂停条收拢，同时让右暂停条从三角尖端展开。 */
private fun mediaPlayPauseMorphIcon(
    step: Int,
    color: PixelColor,
): Widget {
    /** 当前离散帧对应的像素几何。 */
    val geometry = mediaPlayPauseMorphGeometry(step)
    return CustomPaint(
        width = HOME_MEDIA_CONTROL_ICON_WIDTH_PX,
        height = HOME_MEDIA_CONTROL_ICON_HEIGHT_PX,
    ) {
        drawPolygon(points = geometry.leftShape, color = color, filled = true)
        fillRect(
            geometry.rightLeft,
            geometry.rightTop,
            geometry.rightWidth,
            geometry.rightHeight,
            color,
        )
    }
}

/** 播放/暂停形变单帧的确定性几何，公开给 JVM 测试验证端点和中间帧。 */
internal data class MediaPlayPauseMorphGeometry(
    /** 三角形逐步收拢形成的左侧主体多边形。 */
    val leftShape: List<PixelPoint>,
    /** 右暂停条当前左边缘。 */
    val rightLeft: Int,
    /** 右暂停条当前上边缘。 */
    val rightTop: Int,
    /** 右暂停条当前宽度。 */
    val rightWidth: Int,
    /** 右暂停条当前高度。 */
    val rightHeight: Int,
)

/** 返回指定离散帧的播放/暂停形变几何，越界输入安全限制到合法端点。 */
internal fun mediaPlayPauseMorphGeometry(step: Int): MediaPlayPauseMorphGeometry {
    /** 限制后的离散动画帧。 */
    val safeStep = step.coerceIn(0, HOME_MEDIA_PLAY_PAUSE_MORPH_STEPS)
    /** 将整数坐标按离散帧插值并重新对齐像素网格。 */
    fun coordinate(play: Int, pause: Int): Int {
        return (
            play + (pause - play) * safeStep.toFloat() / HOME_MEDIA_PLAY_PAUSE_MORPH_STEPS
        ).roundToInt()
    }
    /** 左侧主体从三角尖端分裂出的下侧顶点。 */
    val lowerRight = PixelPoint(coordinate(10, 4), coordinate(5, 9))
    /** 左侧主体从三角尖端分裂出的上侧顶点。 */
    val upperRight = PixelPoint(coordinate(10, 4), coordinate(5, 1))
    /** 右暂停条当前边界。 */
    val rightLeft = coordinate(10, 8)
    val rightRight = coordinate(10, 9)
    val rightTop = coordinate(5, 1)
    val rightBottom = coordinate(5, 9)
    return MediaPlayPauseMorphGeometry(
        leftShape = listOf(
            PixelPoint(3, 1),
            PixelPoint(3, 9),
            lowerRight,
            upperRight,
        ),
        rightLeft = rightLeft,
        rightTop = rightTop,
        rightWidth = rightRight - rightLeft + 1,
        rightHeight = rightBottom - rightTop + 1,
    )
}

private fun homeActionSegment(
    text: String,
    textStyle: TextStyle,
    fillColor: PixelColor?,
    chromeGeometry: LauncherChromeGeometry,
): Widget = Container(
    height = chromeGeometry.segmentHeightPx,
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

private fun homeActionDivider(
    theme: LauncherTheme,
    chromeGeometry: LauncherChromeGeometry,
): Widget = Container(
    width = HOME_ACTION_DIVIDER_PX,
    height = chromeGeometry.segmentHeightPx,
    fillColor = theme.button.border,
)

private fun homeActionButtonWidth(
    label: String,
    count: Int,
    measureTextWidth: (String) -> Int,
): Int {
    val labelWidth = homeActionSegmentWidth(label, measureTextWidth)
    val contentWidth = if (count > 0) {
        labelWidth + HOME_ACTION_DIVIDER_PX + homeActionSegmentWidth(count.toString(), measureTextWidth)
    } else {
        labelWidth
    }
    return contentWidth + (HOME_ACTION_BORDER_PX * 2)
}

/** 使用实际 CHROME 栅格器计算边框段宽度，避免宽字体被默认 face 估算裁切。 */
internal fun homeActionSegmentWidth(
    text: String,
    measureTextWidth: (String) -> Int,
): Int = measureTextWidth(text).coerceAtLeast(0) + HOME_ACTION_SEGMENT_HORIZONTAL_PADDING_PX * 2

private enum class MediaControlIcon {
    PREVIOUS,
    NEXT,

    ;

    /** 上一曲向左、下一曲向右移动。 */
    val horizontalDirection: Int
        get() = when (this) {
            PREVIOUS -> -1
            NEXT -> 1
        }
}

/** 播放/暂停形变拆分的离散步数；五帧兼顾像素感与 150ms 内的可读过渡。 */
private const val HOME_MEDIA_PLAY_PAUSE_MORPH_STEPS = 4

/** 播放/暂停隐式动画跨受控状态切换复用的稳定键。 */
private const val HOME_MEDIA_PLAY_PAUSE_MORPH_KEY = "home-media-play-pause-morph"

/** 跳曲点击反馈的最大水平位移，保持在按钮内部且清晰可见。 */
private const val HOME_MEDIA_SKIP_FEEDBACK_DISTANCE_PX = 2

/** 上一曲反馈动画跨父级重建复用的稳定键。 */
private const val HOME_MEDIA_PREVIOUS_FEEDBACK_KEY = "home-media-previous-feedback"

/** 下一曲反馈动画跨父级重建复用的稳定键。 */
private const val HOME_MEDIA_NEXT_FEEDBACK_KEY = "home-media-next-feedback"

private fun PixelColor.withAlpha(alpha: Int): PixelColor = PixelColor.fromArgb(
    a = alpha.coerceIn(0, 255),
    r = red,
    g = green,
    b = blue,
)

private const val HOME_ACTION_BORDER_PX = LauncherChromeLayout.sharedBorderPx
private const val HOME_ACTION_DIVIDER_PX = 1
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
