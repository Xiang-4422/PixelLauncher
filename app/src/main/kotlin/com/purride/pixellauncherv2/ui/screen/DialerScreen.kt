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
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.PageView
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.Row
import com.purride.pixelui.Semantics
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState
import com.purride.pixellauncherv2.launcher.CallPageIndex
import com.purride.pixellauncherv2.launcher.DialInputModel
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.launcher.LauncherTextRole
import com.purride.pixellauncherv2.launcher.T9Model
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

private val CALL_PAGE_TABS = listOf("RECENT", "DIAL")

/**
 * DIALER 屏幕：拨号模块首页。
 *
 * 固定两页——左页最近通话，右页拨号盘。导航放在**第一行**，底部留给当页的主操作
 * （拨号盘的 CALL）：在 22 行的纵向预算里，把导航和主操作挤在同一端会让主操作
 * 失去分量。全屏只允许一个反色实心块，那就是主操作。
 */
fun DialerScreen(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    vsync: PixelTickerProvider,
    pagerController: PixelPagerController,
    pagerState: PixelPagerState,
    listState: PixelListState,
    listController: PixelListController,
    onCallPageSelected: (Int) -> Unit,
    onCallGroupPressed: (number: String) -> Unit,
    onRequestCallLogPermission: () -> Unit,
    onDialDigit: (Char) -> Unit,
    onDialBackspace: () -> Unit,
    onDialClear: () -> Unit,
    onDialCall: () -> Unit,
    onDialMatchPressed: (number: String) -> Unit,
): Widget = Column(
    crossAxisAlignment = CrossAxisAlignment.STRETCH,
    mainAxisSize = MainAxisSize.MAX,
    spacing = 0,
    children = listOf(
        callTopTabs(
            selectedIndex = CallPageIndex.coerce(uiState.callPageIndex),
            theme = theme,
            onSelected = onCallPageSelected,
        ),
        Expanded(
            child = PageView(
                axis = Axis.HORIZONTAL,
                controller = pagerController,
                state = pagerState,
                pages = listOf(
                    CallLogScreen(
                        uiState = uiState,
                        theme = theme,
                        vsync = vsync,
                        listState = listState,
                        listController = listController,
                        onCallGroupPressed = onCallGroupPressed,
                        onRequestCallLogPermission = onRequestCallLogPermission,
                    ),
                    dialPadPage(
                        uiState = uiState,
                        theme = theme,
                        onDialDigit = onDialDigit,
                        onDialBackspace = onDialBackspace,
                        onDialClear = onDialClear,
                        onDialCall = onDialCall,
                        onDialMatchPressed = onDialMatchPressed,
                    ),
                ),
                onPageChanged = onCallPageSelected,
            ),
        ),
    ),
)

/** 顶部页签：等宽、文字居中、共用外边框，选中页纯色填充（UI 规范 §8）。 */
private fun callTopTabs(
    selectedIndex: Int,
    theme: LauncherTheme,
    onSelected: (Int) -> Unit,
): Widget = Padding(
    horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
    vertical = LauncherSpacing.ROW_SPACING,
    child = Container(
        borderColor = theme.button.border,
        child = Row(
            spacing = 0,
            children = CALL_PAGE_TABS.mapIndexed { index, label ->
                Expanded(
                    child = Semantics(
                        label = if (index == selectedIndex) "$label selected" else label,
                        role = PixelSemanticRole.TAB,
                        focused = index == selectedIndex,
                        child = GestureDetector(
                            onTap = { onSelected(index) },
                            child = Container(
                                alignment = Alignment.CENTER,
                                fillColor = if (index == selectedIndex) {
                                    theme.button.border
                                } else {
                                    PixelColor.Transparent
                                },
                                padding = EdgeInsets.symmetric(
                                    horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
                                    vertical = LauncherSpacing.ROW_SPACING,
                                ),
                                child = dialText(
                                    text = label,
                                    color = if (index == selectedIndex) {
                                        theme.surface.offPixelColor
                                    } else {
                                        theme.button.text
                                    },
                                    theme = theme,
                                    align = TextAlign.CENTER,
                                ),
                            ),
                        ),
                    ),
                )
            },
        ),
    ),
)

/**
 * 拨号盘页：号码行 + 固定匹配槽 + 无边框键盘 + 通栏主操作。
 *
 * 键位不画边框——12 个方框在点阵屏上会形成密集网格线，噪声压过数字本身；
 * 靠 Expanded 均分的留白分隔即可，按下态由手势反馈承担。
 */
private fun dialPadPage(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    onDialDigit: (Char) -> Unit,
    onDialBackspace: () -> Unit,
    onDialClear: () -> Unit,
    onDialCall: () -> Unit,
    onDialMatchPressed: (number: String) -> Unit,
): Widget = Padding(
    horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
    vertical = LauncherSpacing.CONTENT_VERTICAL,
    child = Column(
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        mainAxisSize = MainAxisSize.MAX,
        spacing = LauncherSpacing.ROW_SPACING,
        children = buildList {
            add(dialNumberRow(uiState, theme, onDialBackspace, onDialClear))
            add(dialMatchSlot(uiState, theme, onDialMatchPressed))
            DialInputModel.keypadRows.forEach { row ->
                add(
                    Expanded(
                        child = Row(
                            spacing = LauncherSpacing.ROW_SPACING,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                            children = row.map { key -> dialKey(key, theme, onDialDigit) },
                        ),
                    ),
                )
            }
            add(dialCallBar(uiState, theme, onDialCall))
        },
    ),
)

/** 号码行：当前输入靠左，DEL 靠右（就近编辑）；长按 DEL 清空。 */
private fun dialNumberRow(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    onDialBackspace: () -> Unit,
    onDialClear: () -> Unit,
): Widget = Row(
    spacing = LauncherSpacing.ROW_SPACING,
    crossAxisAlignment = CrossAxisAlignment.CENTER,
    children = listOf(
        Expanded(
            child = dialText(
                text = DialInputModel.displayText(uiState.dialInput),
                color = if (uiState.dialInput.isEmpty()) {
                    theme.sms.timestamp
                } else {
                    theme.text.primary
                },
                theme = theme,
                // 号码保尾截头：尾号是用户核对刚按下数字的唯一依据，从尾部截断等于
                // 把刚输入的内容藏起来。截断点由引擎按真实可用宽度决定，UI 层不猜字符数。
                overflow = TextOverflow.ELLIPSIS_START,
            ),
        ),
        GestureDetector(
            onTap = onDialBackspace,
            onLongPress = onDialClear,
            child = dialText(
                text = "DEL",
                color = if (uiState.dialInput.isEmpty()) {
                    theme.button.disabledText
                } else {
                    theme.button.text
                },
                theme = theme,
            ),
        ),
    ),
)

/**
 * T9 匹配槽：**固定占一行，永不塌陷**。
 *
 * 塌陷会让整个键盘上下跳动——输入时手指已经落在键位上，位置变化直接导致误触。
 * 唯一命中显示姓名与号码（点按即拨），多命中显示条数，未命中留空占位。
 */
private fun dialMatchSlot(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    onDialMatchPressed: (number: String) -> Unit,
): Widget {
    val matches = uiState.dialMatches
    val first = matches.firstOrNull()
    val slot = dialText(
        // 文本由 model 负责且保证非空——空串会让本槽塌成 0 高度，键盘随每次按键弹跳。
        text = DialInputModel.matchSlotText(
            displayName = first?.displayName,
            number = first?.number,
            extraCount = (matches.size - 1).coerceAtLeast(0),
        ),
        color = theme.sms.sender,
        theme = theme,
    )
    return if (first == null) {
        slot
    } else {
        GestureDetector(onTap = { onDialMatchPressed(first.number) }, child = slot)
    }
}

/**
 * 单个键位：数字为主，字母副标只用低对比色弱化，绝不与数字等重。
 *
 * 副标不能靠"更小字号"分层——运行时只准备正文与 CHROME 两个 face，正文已是家族
 * 最小字号时 CHROME 与之同号；层级差全部由颜色承担。
 */
private fun dialKey(
    key: Char,
    theme: LauncherTheme,
    onDialDigit: (Char) -> Unit,
): Widget {
    val hint = T9Model.letterHint(key)
    return Expanded(
        child = Semantics(
            label = if (hint.isEmpty()) key.toString() else "$key $hint",
            role = PixelSemanticRole.BUTTON,
            child = GestureDetector(
                onTap = { onDialDigit(key) },
                // 长按 0 输入 +，拨号盘通行惯例。
                onLongPress = if (key == '0') {
                    { onDialDigit('+') }
                } else {
                    null
                },
                child = Container(
                    alignment = Alignment.CENTER,
                    child = Column(
                        mainAxisSize = MainAxisSize.MIN,
                        mainAxisAlignment = MainAxisAlignment.CENTER,
                        crossAxisAlignment = CrossAxisAlignment.CENTER,
                        spacing = 0,
                        children = listOfNotNull(
                            dialText(
                                text = key.toString(),
                                color = theme.text.primary,
                                theme = theme,
                                align = TextAlign.CENTER,
                            ),
                            hint.takeIf(String::isNotEmpty)?.let { letters ->
                                dialText(
                                    text = letters,
                                    color = theme.button.disabledText,
                                    theme = theme,
                                    align = TextAlign.CENTER,
                                    role = LauncherTextRole.CHROME,
                                )
                            },
                        ),
                    ),
                ),
            ),
        ),
    )
}

/** 主操作：通栏反色实心块——全屏唯一的实心块，其余控件只用边框或纯文字。 */
private fun dialCallBar(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    onDialCall: () -> Unit,
): Widget {
    val enabled = DialInputModel.isCallable(uiState.dialInput)
    return Semantics(
        label = "CALL",
        role = PixelSemanticRole.BUTTON,
        enabled = enabled,
        child = GestureDetector(
            // onTap 不可空：始终接上，能否拨出由控制器判断（callDialInput 会校验），
            // 这样禁用态只是视觉弱化，不会变成一块无反馈的死区。
            onTap = onDialCall,
            child = Container(
                alignment = Alignment.CENTER,
                fillColor = if (enabled) theme.button.border else PixelColor.Transparent,
                borderColor = if (enabled) null else theme.button.border,
                padding = EdgeInsets.symmetric(
                    horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
                    vertical = LauncherSpacing.ROW_SPACING,
                ),
                child = dialText(
                    text = "CALL",
                    color = if (enabled) theme.surface.offPixelColor else theme.button.disabledText,
                    theme = theme,
                    align = TextAlign.CENTER,
                ),
            ),
        ),
    )
}

/**
 * 拨号界面的统一文本。
 *
 * [role] 只接受语义角色，**不接受裸字号**：运行时只准备用户选择的正文 face 与
 * [LauncherTextRole.CHROME] face 两种，显式指定其它字号会在首帧抛
 * `Font face was not prepared` 并杀掉进程。
 */
private fun dialText(
    text: String,
    color: PixelColor,
    theme: LauncherTheme,
    align: TextAlign = TextAlign.START,
    role: LauncherTextRole? = null,
    overflow: TextOverflow = TextOverflow.ELLIPSIS,
): Widget = Text(
    text,
    style = if (role == null) {
        theme.typography.textStyle(color = color)
    } else {
        theme.typography.textStyle(color = color, role = role)
    },
    textAlign = align,
    overflow = overflow,
    softWrap = false,
    maxLines = 1,
)
