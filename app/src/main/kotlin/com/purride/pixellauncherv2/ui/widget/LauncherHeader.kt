package com.purride.pixellauncherv2.ui.widget

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelInputType
import com.purride.pixelui.Row
import com.purride.pixelui.Semantics
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextButtonStyle
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextField
import com.purride.pixelui.TextFieldStyle
import com.purride.pixelui.TextInputAction
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixelui.widgets.animated.AnimatedSwitcher
import com.purride.pixellauncherv2.launcher.LauncherChromeGeometry
import com.purride.pixellauncherv2.launcher.LauncherChromeLayout
import com.purride.pixellauncherv2.launcher.LauncherHeaderLayout
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.launcher.LauncherTextRole
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.text.opticallyAlignStartText
import kotlin.time.Duration.Companion.milliseconds

/**
 * 所有 Launcher 屏幕的顶部标题栏。
 *
 * 视觉结构（宽度 = 屏幕宽度，自动 STRETCH）：
 * ```
 * HH:MM           SCREEN TITLE
 * ████████████░░░░░░░░░░░░░░  ← BatteryDivider（1px 高，贴住底边）
 * ```
 *
 * 时间靠左，屏幕标题靠右；电量分隔线位于文字行正下方。
 *
 * @param timeText    当前时间字符串（例如 "14:30"）
 * @param screenTitle 屏幕名称（例如 "HOME"、"APP DRAWER"）
 * @param messageText 临时消息；非空时独占文字行，电量线保持显示
 * @param batteryLevel 电量百分比 0–100
 * @param isCharging  是否正在充电
 * @param chargeTick  充电像素的动画帧计数
 * @param theme       当前颜色主题（提供 statusBar token）
 * @param statusBarWidth 状态栏用于左右边缘锚定的完整逻辑宽度
 * @param statusBarHeight 顶部状态栏占位高度（engine 逻辑像素）
 * @param resolveLeadingInkInset 查询左侧边缘文字首字形的左侧空白像素数
 * @param measureTextWidth 使用当前实际字形包测量单行文字宽度
 * @param chromeGeometry 当前 CHROME face 的共享边框几何
 */
fun LauncherHeader(
    timeText: String,
    screenTitle: String,
    messageText: String = "",
    actionLeadingText: String = "",
    actionLabel: String = "",
    isActionDanger: Boolean = false,
    centerActionLabel: String = "",
    isCenterActionEnabled: Boolean = true,
    centerText: String = "",
    centerTextColor: PixelColor? = null,
    batteryLevel: Int,
    isCharging: Boolean,
    chargeTick: Int,
    theme: LauncherTheme,
    statusBarWidth: Int,
    resolveLeadingInkInset: (String) -> Int,
    measureTextWidth: (String) -> Int,
    chromeGeometry: LauncherChromeGeometry,
    statusBarHeight: Int,
    pageTagVsync: PixelTickerProvider? = null,
    onAction: (() -> Unit)? = null,
    onCenterAction: (() -> Unit)? = null,
    onCenterTap: (() -> Unit)? = null,
    onCenterDoubleTap: (() -> Unit)? = null,
): Widget {
    val message = messageText.trim()
    val action = actionLabel.trim()
    val centerAction = centerActionLabel.trim()
    val isShowingMessage = message.isNotEmpty()
    val isShowingAction = action.isNotEmpty()
    val mediaTitle = centerText.trim()
    val centerContent = when {
        isShowingAction -> StatusBarCenterContent.FilledAction(
            leadingText = actionLeadingText,
            actionLabel = action,
            isDanger = isActionDanger,
        )
        isShowingMessage -> StatusBarCenterContent.Message(message)
        centerAction.isNotEmpty() -> StatusBarCenterContent.TextAction(
            actionLabel = centerAction,
            enabled = isCenterActionEnabled,
        )
        mediaTitle.isNotEmpty() -> StatusBarCenterContent.MediaTitle(
            text = mediaTitle,
            color = centerTextColor,
        )
        else -> StatusBarCenterContent.Empty
    }
    val header = Column(
        children = statusBarChildren(
            statusBarHeight = statusBarHeight,
            contentWidth = statusBarWidth,
            contentHeight = chromeGeometry.rowHeightPx + LauncherHeaderLayout.dividerHeight,
            row = statusBarTitleRow(
                timeText = timeText,
                screenTitle = screenTitle,
                centerContent = centerContent,
                theme = theme,
                resolveLeadingInkInset = resolveLeadingInkInset,
                measureTextWidth = measureTextWidth,
                statusBarWidth = statusBarWidth,
                chromeGeometry = chromeGeometry,
                pageTagVsync = pageTagVsync,
                onAction = onAction,
                onCenterAction = onCenterAction,
                onCenterTap = onCenterTap,
                onCenterDoubleTap = onCenterDoubleTap,
            ),
            divider = BatteryDividerWidget(
                batteryLevel = batteryLevel,
                isCharging = isCharging,
                chargeTick = chargeTick,
                highColor = theme.statusBar.batteryHigh,
                mediumColor = theme.statusBar.batteryMedium,
                lowColor = theme.statusBar.batteryLow,
            ),
        ),
        spacing = 0,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    )
    return header
}

private fun statusBarText(
    text: String,
    theme: LauncherTheme,
    textAlign: TextAlign = TextAlign.START,
    color: PixelColor = theme.statusBar.text,
): Widget = Text(
    text,
    style = theme.typography.textStyle(color = color, role = LauncherTextRole.CHROME),
    textAlign = textAlign,
    overflow = TextOverflow.ELLIPSIS,
    softWrap = false,
    maxLines = 1,
)

private sealed class StatusBarCenterContent {
    data object Empty : StatusBarCenterContent()
    data class Message(val text: String) : StatusBarCenterContent()
    data class FilledAction(
        val leadingText: String,
        val actionLabel: String,
        val isDanger: Boolean,
    ) : StatusBarCenterContent()
    data class TextAction(
        val actionLabel: String,
        val enabled: Boolean,
    ) : StatusBarCenterContent()
    data class MediaTitle(
        val text: String,
        val color: PixelColor?,
    ) : StatusBarCenterContent()
}

private fun statusBarTitleRow(
    timeText: String,
    screenTitle: String,
    centerContent: StatusBarCenterContent,
    theme: LauncherTheme,
    resolveLeadingInkInset: (String) -> Int,
    measureTextWidth: (String) -> Int,
    statusBarWidth: Int,
    chromeGeometry: LauncherChromeGeometry,
    pageTagVsync: PixelTickerProvider?,
    onAction: (() -> Unit)?,
    onCenterAction: (() -> Unit)?,
    onCenterTap: (() -> Unit)?,
    onCenterDoubleTap: (() -> Unit)?,
): Widget {
    /** 页面标题按实际字形测量并限制在状态栏剩余区域内。 */
    val pageTagWidth = statusBarPageTagWidth(
        statusBarWidth = statusBarWidth,
        timeTextWidth = measureTextWidth(timeText),
        pageTitleTextWidth = measureTextWidth(screenTitle),
    )
    if (centerContent is StatusBarCenterContent.FilledAction) {
        return statusBarFullWidthAction(
            leadingText = centerContent.leadingText,
            actionLabel = centerContent.actionLabel,
            isDanger = centerContent.isDanger,
            theme = theme,
            resolveLeadingInkInset = resolveLeadingInkInset,
            onAction = onAction,
            chromeGeometry = chromeGeometry,
        )
    }
    if (centerContent == StatusBarCenterContent.Empty) {
        return Container(
            height = chromeGeometry.rowHeightPx,
            padding = EdgeInsets.all(STATUS_BAR_TITLE_EDGE_PADDING_PX),
            child = Row(
                mainAxisSize = MainAxisSize.MAX,
                children = listOf(
                    statusBarSegment(
                        text = timeText,
                        theme = theme,
                        textColor = theme.statusBar.text,
                        height = chromeGeometry.segmentHeightPx,
                        resolveLeadingInkInset = resolveLeadingInkInset,
                    ),
                    Expanded(child = SizedBox(width = 0, height = 0)),
                    statusBarPageTag(
                        text = screenTitle,
                        theme = theme,
                        textColor = theme.statusBar.text,
                        textAlign = TextAlign.END,
                        height = chromeGeometry.segmentHeightPx,
                        width = pageTagWidth,
                        vsync = pageTagVsync,
                    ),
                ),
                spacing = 0,
            ),
        )
    }
    return Container(
        height = chromeGeometry.rowHeightPx,
        borderColor = theme.button.border,
        padding = EdgeInsets.all(STATUS_BAR_MEDIA_BORDER_PX),
        child = Row(
            mainAxisSize = MainAxisSize.MAX,
            children = listOf(
                statusBarSegment(
                    text = timeText,
                    theme = theme,
                    textColor = theme.statusBar.text,
                    height = chromeGeometry.segmentHeightPx,
                    resolveLeadingInkInset = resolveLeadingInkInset,
                ),
                statusBarDivider(theme, chromeGeometry),
                Expanded(
                    child = statusBarCenterContent(
                        content = centerContent,
                        theme = theme,
                        onAction = onAction,
                        onCenterAction = onCenterAction,
                        onCenterTap = onCenterTap,
                        onCenterDoubleTap = onCenterDoubleTap,
                        chromeGeometry = chromeGeometry,
                    ),
                ),
                statusBarDivider(theme, chromeGeometry),
                statusBarPageTag(
                    text = screenTitle,
                    theme = theme,
                    textColor = theme.statusBar.text,
                    textAlign = TextAlign.END,
                    height = chromeGeometry.segmentHeightPx,
                    width = pageTagWidth,
                    vsync = pageTagVsync,
                ),
            ),
            spacing = 0,
        ),
    )
}

private fun statusBarCenterContent(
    content: StatusBarCenterContent,
    theme: LauncherTheme,
    onAction: (() -> Unit)?,
    onCenterAction: (() -> Unit)?,
    onCenterTap: (() -> Unit)?,
    onCenterDoubleTap: (() -> Unit)?,
    chromeGeometry: LauncherChromeGeometry,
): Widget = when (content) {
    StatusBarCenterContent.Empty -> SizedBox(width = 0, height = 0)
    is StatusBarCenterContent.Message -> statusBarSegment(
        text = content.text,
        theme = theme,
        textColor = theme.statusBar.text,
        textAlign = TextAlign.CENTER,
        height = chromeGeometry.segmentHeightPx,
    )
    is StatusBarCenterContent.FilledAction -> statusBarCenterAction(
        leadingText = content.leadingText,
        actionLabel = content.actionLabel,
        enabled = true,
        filled = true,
        isDanger = content.isDanger,
        theme = theme,
        onAction = onAction,
        chromeGeometry = chromeGeometry,
    )
    is StatusBarCenterContent.TextAction -> statusBarCenterAction(
        leadingText = "",
        actionLabel = content.actionLabel,
        enabled = content.enabled,
        filled = false,
        isDanger = false,
        theme = theme,
        onAction = onCenterAction,
        chromeGeometry = chromeGeometry,
    )
    is StatusBarCenterContent.MediaTitle -> statusBarCenter(
        text = content.text,
        fillColor = content.color ?: theme.button.filledSurface,
        textColor = if (content.color == null) {
            theme.button.filledText
        } else {
            theme.surface.bezelColor
        },
        theme = theme,
        onTap = onCenterTap,
        onDoubleTap = onCenterDoubleTap,
        height = chromeGeometry.segmentHeightPx,
    )
}

private fun statusBarFullWidthAction(
    leadingText: String,
    actionLabel: String,
    isDanger: Boolean,
    theme: LauncherTheme,
    resolveLeadingInkInset: (String) -> Int,
    onAction: (() -> Unit)?,
    chromeGeometry: LauncherChromeGeometry,
): Widget {
    val fillColor = statusBarActionBackgroundColor(isDanger, theme)
    val textColor = statusBarActionTextColor(isDanger, theme)
    val labelText = Text(
        actionLabel,
        style = theme.typography.textStyle(color = textColor, role = LauncherTextRole.CHROME),
        overflow = TextOverflow.ELLIPSIS,
        softWrap = false,
        maxLines = 1,
    )
    val children = if (leadingText.isBlank()) {
        listOf(
            Expanded(
                child = Container(
                    alignment = Alignment.CENTER,
                    child = labelText,
                ),
            ),
        )
    } else {
        listOf(
            opticallyAlignStartText(
                text = leadingText,
                resolveLeadingInkInset = resolveLeadingInkInset,
                child = Text(
                    leadingText,
                    style = theme.typography.textStyle(color = textColor, role = LauncherTextRole.CHROME),
                    overflow = TextOverflow.ELLIPSIS,
                    softWrap = false,
                    maxLines = 1,
                ),
            ),
            Expanded(
                child = Container(
                    alignment = Alignment.CENTER_END,
                    child = labelText,
                ),
            ),
        )
    }
    val content = Container(
        height = chromeGeometry.rowHeightPx,
        fillColor = fillColor,
        padding = EdgeInsets.symmetric(horizontal = LauncherSpacing.CONTENT_HORIZONTAL),
        child = Row(
            mainAxisSize = MainAxisSize.MAX,
            children = children,
            spacing = 0,
        ),
    )
    return Semantics(
        label = actionLabel,
        role = PixelSemanticRole.BUTTON,
        enabled = onAction != null,
        child = if (onAction == null) {
            content
        } else {
            GestureDetector(
                onTap = onAction,
                child = content,
            )
        },
    )
}

private fun statusBarCenterAction(
    leadingText: String,
    actionLabel: String,
    enabled: Boolean,
    filled: Boolean,
    isDanger: Boolean,
    theme: LauncherTheme,
    onAction: (() -> Unit)?,
    chromeGeometry: LauncherChromeGeometry,
): Widget {
    val textColor = when {
        !enabled -> theme.statusBar.mutedText
        filled -> statusBarActionTextColor(isDanger, theme)
        else -> theme.statusBar.text
    }
    val actionButton = TextButton(
        text = actionLabel,
        onPressed = onAction,
        enabled = enabled,
        style = TextButtonStyle(
            textStyle = theme.typography.textStyle(color = textColor, role = LauncherTextRole.CHROME),
        ),
    )
    val fillColor = if (filled) statusBarActionBackgroundColor(isDanger, theme) else null
    if (leadingText.isBlank()) {
        return Container(
            height = chromeGeometry.segmentHeightPx,
            fillColor = fillColor,
            alignment = Alignment.CENTER,
            child = actionButton,
        )
    }
    return Container(
        height = chromeGeometry.segmentHeightPx,
        fillColor = fillColor,
        padding = EdgeInsets.symmetric(horizontal = STATUS_BAR_SEGMENT_HORIZONTAL_PADDING_PX),
        child = Row(
            children = listOf(
                Text(
                    leadingText,
                    style = theme.typography.textStyle(color = textColor, role = LauncherTextRole.CHROME),
                    overflow = TextOverflow.ELLIPSIS,
                    softWrap = false,
                    maxLines = 1,
                ),
                Expanded(
                    child = Container(
                        alignment = Alignment.CENTER_END,
                        child = actionButton,
                    ),
                ),
            ),
            spacing = 0,
        ),
    )
}

private fun statusBarCenter(
    text: String,
    fillColor: PixelColor,
    textColor: PixelColor,
    theme: LauncherTheme,
    onTap: (() -> Unit)?,
    onDoubleTap: (() -> Unit)?,
    height: Int? = null,
): Widget {
    val content = statusBarSegment(
        text = text,
        theme = theme,
        fillColor = fillColor,
        textColor = textColor,
        textAlign = TextAlign.CENTER,
        height = height,
    )
    if (text.isBlank() || (onTap == null && onDoubleTap == null)) {
        return content
    }
    return Semantics(
        label = text,
        role = PixelSemanticRole.BUTTON,
        enabled = true,
        child = GestureDetector(
            onTap = onTap ?: {},
            onDoubleTap = onDoubleTap,
            child = content,
        ),
    )
}

private fun statusBarSegment(
    text: String,
    theme: LauncherTheme,
    fillColor: PixelColor? = null,
    textColor: PixelColor,
    textAlign: TextAlign = TextAlign.START,
    height: Int? = null,
    key: Any? = null,
    resolveLeadingInkInset: ((String) -> Int)? = null,
    width: Int? = null,
): Widget {
    /** 状态栏左侧边缘文字；中间及右侧文字保持自身排版规则。 */
    val content = if (textAlign == TextAlign.START && resolveLeadingInkInset != null) {
        opticallyAlignStartText(
            text = text,
            resolveLeadingInkInset = resolveLeadingInkInset,
            child = statusBarText(
                text = text,
                theme = theme,
                textAlign = textAlign,
                color = textColor,
            ),
        )
    } else {
        statusBarText(
            text = text,
            theme = theme,
            textAlign = textAlign,
            color = textColor,
        )
    }
    return Container(
        width = width,
        height = height,
        fillColor = fillColor,
        alignment = when (textAlign) {
            TextAlign.START -> Alignment.CENTER_START
            TextAlign.CENTER -> Alignment.CENTER
            TextAlign.END -> Alignment.CENTER_END
        },
        padding = EdgeInsets.symmetric(horizontal = STATUS_BAR_SEGMENT_HORIZONTAL_PADDING_PX),
        key = key,
        child = content,
    )
}

private fun statusBarPageTag(
    text: String,
    theme: LauncherTheme,
    textColor: PixelColor,
    textAlign: TextAlign,
    height: Int,
    width: Int,
    vsync: PixelTickerProvider?,
): Widget {
    val tag = statusBarSegment(
        text = text,
        theme = theme,
        textColor = textColor,
        textAlign = textAlign,
        width = width,
        height = height,
        key = "status-bar-page-tag-$text",
    )
    return if (vsync == null) {
        tag
    } else {
        SizedBox(
            width = width,
            height = height,
            child = AnimatedSwitcher(
                duration = STATUS_BAR_PAGE_TAG_TRANSITION_MS.milliseconds,
                vsync = vsync,
                key = "status-bar-page-tag-switcher",
                child = tag,
            ),
        )
    }
}

private fun statusBarDivider(
    theme: LauncherTheme,
    chromeGeometry: LauncherChromeGeometry,
): Widget = Container(
    width = STATUS_BAR_SEGMENT_DIVIDER_PX,
    height = chromeGeometry.segmentHeightPx,
    fillColor = theme.button.border,
)

/**
 * 同一套 Launcher 状态栏的搜索态。
 *
 * 外层结构必须和 [LauncherHeader] 保持一致：一行内容 + 贴底 BatteryDivider。
 * 这样 HOME / DRAWER / SETTINGS 之间切换时，状态栏尺寸不会跳变。
 *
 * @param placeholderLeadingInkInset placeholder 首字形在实际字形包中的左侧空白像素数
 * @param statusBarWidth 状态栏搜索行使用的完整逻辑宽度
 * @param chromeGeometry 当前 CHROME face 的共享边框几何
 */
fun LauncherSearchHeader(
    state: PixelTextFieldState,
    controller: TextEditingController,
    placeholder: String,
    placeholderLeadingInkInset: Int,
    autofocus: Boolean,
    /**
     * 浮层菜单打开时传 false：禁用后不再产出文本输入目标，点击才能落到
     * 菜单的全屏遮罩上（抬手时输入目标优先于点击目标并直接消费手势）。
     */
    enabled: Boolean = true,
    textAlign: TextAlign = TextAlign.START,
    batteryLevel: Int,
    isCharging: Boolean,
    chargeTick: Int,
    theme: LauncherTheme,
    statusBarWidth: Int,
    chromeGeometry: LauncherChromeGeometry,
    statusBarHeight: Int,
    onChanged: (String) -> Unit,
    onSubmitted: () -> Unit,
): Widget {
    return Column(
        children = statusBarChildren(
            statusBarHeight = statusBarHeight,
            contentWidth = statusBarWidth,
            contentHeight = chromeGeometry.rowHeightPx + LauncherHeaderLayout.dividerHeight,
            row = statusBarSearchRow(
                state = state,
                controller = controller,
                placeholder = placeholder,
                placeholderLeadingInkInset = placeholderLeadingInkInset,
                autofocus = autofocus,
                enabled = enabled,
                textAlign = textAlign,
                theme = theme,
                chromeGeometry = chromeGeometry,
                onChanged = onChanged,
                onSubmitted = onSubmitted,
            ),
            divider = BatteryDividerWidget(
                batteryLevel = batteryLevel,
                isCharging = isCharging,
                chargeTick = chargeTick,
                highColor = theme.statusBar.batteryHigh,
                mediumColor = theme.statusBar.batteryMedium,
                lowColor = theme.statusBar.batteryLow,
            ),
        ),
        spacing = 0,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    )
}

private fun statusBarSearchRow(
    state: PixelTextFieldState,
    controller: TextEditingController,
    placeholder: String,
    placeholderLeadingInkInset: Int,
    autofocus: Boolean,
    enabled: Boolean,
    textAlign: TextAlign,
    theme: LauncherTheme,
    chromeGeometry: LauncherChromeGeometry,
    onChanged: (String) -> Unit,
    onSubmitted: () -> Unit,
): Widget = Container(
    height = chromeGeometry.rowHeightPx,
    padding = searchRowPadding(
        textAlign = textAlign,
        placeholderLeadingInkInset = placeholderLeadingInkInset,
    ),
    child = Row(
        children = listOf(
            Expanded(
                child = TextField(
                    state = state,
                    controller = controller,
                    placeholder = placeholder,
                    enabled = enabled,
                    autofocus = autofocus,
                    inputType = PixelInputType.ASCII,
                    textAlign = textAlign,
                    textInputAction = TextInputAction.SEARCH,
                    style = TextFieldStyle(
                        // null 会回落到组件 token；透明色才表示普通态明确不绘制边框。
                        borderColor = PixelColor.Transparent,
                        // 聚焦指示器复用该颜色，透明色同时保留光标并移除黄色外框。
                        focusedBorderColor = PixelColor.Transparent,
                        textStyle = theme.typography.textStyle(
                            color = theme.statusBar.searchText,
                            role = LauncherTextRole.CHROME,
                        ),
                        placeholderStyle = theme.typography.textStyle(
                            color = theme.statusBar.searchPlaceholder,
                            role = LauncherTextRole.CHROME,
                        ),
                        padding = 0,
                        cursorGap = SEARCH_CURSOR_GAP_PX,
                    ),
                    onChanged = onChanged,
                    onSubmitted = { onSubmitted() },
                ),
            ),
        ),
        spacing = 0,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    ),
)

/**
 * 计算搜索行的内容留白，使 hint 与采用相同对齐方式的 drawer 应用名共用视觉边界。
 */
internal fun searchRowPadding(
    /** 搜索文字与 drawer 列表共同使用的对齐方式。 */
    textAlign: TextAlign,
    /** hint 首字形真实墨迹之前的空白像素数。 */
    placeholderLeadingInkInset: Int,
): EdgeInsets {
    /** drawer 应用列表左右两侧的统一内容边界。 */
    val drawerHorizontalInset = LauncherSpacing.CONTENT_HORIZONTAL
    /** 左对齐时预先扣除 hint 字形自带的空白，其他模式保留对称内容边界。 */
    val leftInset = if (textAlign == TextAlign.START) {
        (drawerHorizontalInset - placeholderLeadingInkInset.coerceAtLeast(0)).coerceAtLeast(0)
    } else {
        drawerHorizontalInset
    }
    /** 右对齐字段内部会提供两层 1px 尾部空间，因此搜索行无需再保留外侧右留白。 */
    val rightInset = if (textAlign == TextAlign.END) 0 else drawerHorizontalInset
    return EdgeInsets(
        left = leftInset,
        top = STATUS_BAR_TITLE_EDGE_PADDING_PX,
        right = rightInset,
        bottom = STATUS_BAR_TITLE_EDGE_PADDING_PX,
    )
}

/**
 * 计算右侧页面标题段宽度：正常标题保持真实测量宽度，长标题不侵占左侧时间段。
 */
internal fun statusBarPageTagWidth(
    /** 状态栏完整逻辑宽度。 */
    statusBarWidth: Int,
    /** 左侧时间文字的真实测量宽度。 */
    timeTextWidth: Int,
    /** 右侧页面标题文字的真实测量宽度。 */
    pageTitleTextWidth: Int,
): Int {
    /** 左侧时间段包含文字和两侧内部留白后的宽度。 */
    val timeSegmentWidth = timeTextWidth.coerceAtLeast(0) + STATUS_BAR_SEGMENT_HORIZONTAL_PADDING_PX * 2
    /** 扣除状态栏外层边界和时间段后，标题最多可占用的宽度。 */
    val maxPageTagWidth = (
        statusBarWidth.coerceAtLeast(1) - STATUS_BAR_TITLE_EDGE_PADDING_PX * 2 - timeSegmentWidth
    ).coerceAtLeast(1)
    /** 标题自身需要的文字与两侧内部留白宽度。 */
    val desiredPageTagWidth =
        pageTitleTextWidth.coerceAtLeast(0) + STATUS_BAR_SEGMENT_HORIZONTAL_PADDING_PX * 2
    return desiredPageTagWidth.coerceIn(1, maxPageTagWidth)
}

private fun statusBarActionBackgroundColor(
    isDanger: Boolean,
    theme: LauncherTheme,
): PixelColor = if (isDanger) {
    theme.semantic.danger
} else {
    theme.button.filledSurface
}

/** 返回与状态栏实心操作背景成对、满足可读性的文字颜色。 */
private fun statusBarActionTextColor(
    isDanger: Boolean,
    theme: LauncherTheme,
): PixelColor = if (isDanger) {
    theme.surface.bezelColor
} else {
    theme.button.filledText
}

private fun statusBarChildren(
    statusBarHeight: Int,
    contentWidth: Int,
    contentHeight: Int,
    row: Widget,
    divider: Widget,
): List<Widget> = buildList {
    val topSpacer = (statusBarHeight - contentHeight).coerceAtLeast(0)
    if (topSpacer > 0) {
        add(SizedBox(height = topSpacer))
    }
    add(
        StatusBarBatteryFrame(
            width = contentWidth,
            contentHeight = contentHeight,
            row = row,
            divider = divider,
        ),
    )
}

/** 状态栏外层 1px 边界之外再保留 1px，使文字布局边界与主页面的 2px 一致。 */
private const val STATUS_BAR_SEGMENT_HORIZONTAL_PADDING_PX =
    LauncherSpacing.CONTENT_HORIZONTAL - LauncherChromeLayout.sharedBorderPx
private const val STATUS_BAR_SEGMENT_DIVIDER_PX = 1
private const val STATUS_BAR_MEDIA_BORDER_PX = LauncherChromeLayout.sharedBorderPx
private const val STATUS_BAR_TITLE_EDGE_PADDING_PX = STATUS_BAR_MEDIA_BORDER_PX
/** Drawer 搜索输入末尾字形与光标之间保留的像素间隙。 */
private const val SEARCH_CURSOR_GAP_PX = 1
private const val STATUS_BAR_PAGE_TAG_TRANSITION_MS = 120
