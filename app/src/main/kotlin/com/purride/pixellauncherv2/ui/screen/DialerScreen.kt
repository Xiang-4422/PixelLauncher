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
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PageView
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.Row
import com.purride.pixelui.Semantics
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState
import com.purride.pixellauncherv2.launcher.CallPageIndex
import com.purride.pixellauncherv2.launcher.DialInputModel
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

private val CALL_PAGE_TABS = listOf("RECENT", "DIAL")

/**
 * DIALER 屏幕：拨号模块首页。
 *
 * 固定两页——左页最近通话，右页拨号盘；页签固定在底部并与横向 PageView 同步，
 * 与短信首页保持同一套结构（见 UI 规范 §8）。
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
    onDialDigit: (Char) -> Unit,
    onDialBackspace: () -> Unit,
    onDialClear: () -> Unit,
    onDialCall: () -> Unit,
): Widget = Column(
    crossAxisAlignment = CrossAxisAlignment.STRETCH,
    mainAxisSize = MainAxisSize.MAX,
    spacing = 0,
    children = listOf(
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
                    ),
                    dialPadPage(
                        uiState = uiState,
                        theme = theme,
                        onDialDigit = onDialDigit,
                        onDialBackspace = onDialBackspace,
                        onDialClear = onDialClear,
                        onDialCall = onDialCall,
                    ),
                ),
                onPageChanged = onCallPageSelected,
            ),
        ),
        Padding(
            horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
            vertical = LauncherSpacing.ROW_SPACING,
            child = callBottomTabs(
                selectedIndex = CallPageIndex.coerce(uiState.callPageIndex),
                theme = theme,
                onSelected = onCallPageSelected,
            ),
        ),
    ),
)

/** 拨号盘页：号码框 + 3x4 键盘 + 操作行。键位用 Expanded 均分，不写死宽高。 */
private fun dialPadPage(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    onDialDigit: (Char) -> Unit,
    onDialBackspace: () -> Unit,
    onDialClear: () -> Unit,
    onDialCall: () -> Unit,
): Widget = Padding(
    horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
    vertical = LauncherSpacing.CONTENT_VERTICAL,
    child = Column(
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        mainAxisSize = MainAxisSize.MAX,
        spacing = LauncherSpacing.ROW_SPACING,
        children = buildList {
            add(dialInputRow(uiState, theme))
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
            add(
                dialActionRow(
                    uiState = uiState,
                    theme = theme,
                    onDialBackspace = onDialBackspace,
                    onDialClear = onDialClear,
                    onDialCall = onDialCall,
                ),
            )
        },
    ),
)

/** 号码框：展示当前输入，命中联系人时在下方补一行姓名。 */
private fun dialInputRow(
    uiState: LauncherUiState,
    theme: LauncherTheme,
): Widget = Column(
    crossAxisAlignment = CrossAxisAlignment.STRETCH,
    mainAxisSize = MainAxisSize.MIN,
    spacing = 1,
    children = listOfNotNull(
        Text(
            DialInputModel.displayText(uiState.dialInput),
            style = TextStyle(
                color = if (uiState.dialInput.isEmpty()) {
                    theme.sms.timestamp
                } else {
                    theme.text.primary
                },
            ),
            textAlign = TextAlign.CENTER,
            overflow = TextOverflow.ELLIPSIS,
            softWrap = false,
            maxLines = 1,
        ),
        uiState.dialContactName.takeIf(String::isNotBlank)?.let { name ->
            Text(
                name.uppercase(),
                style = TextStyle(color = theme.sms.sender),
                textAlign = TextAlign.CENTER,
                overflow = TextOverflow.ELLIPSIS,
                softWrap = false,
                maxLines = 1,
            )
        },
    ),
)

/** 单个键位：占满所在格，长按 0 输入 +（拨号盘惯例）。 */
private fun dialKey(
    key: Char,
    theme: LauncherTheme,
    onDialDigit: (Char) -> Unit,
): Widget = Expanded(
    child = GestureDetector(
        onTap = { onDialDigit(key) },
        onLongPress = if (key == '0') {
            { onDialDigit('+') }
        } else {
            null
        },
        child = Container(
            alignment = Alignment.CENTER,
            borderColor = theme.button.border,
            padding = EdgeInsets.all(LauncherSpacing.BORDERED_CONTROL_INSET),
            child = Text(
                key.toString(),
                style = TextStyle(color = theme.button.text),
                textAlign = TextAlign.CENTER,
                overflow = TextOverflow.ELLIPSIS,
                softWrap = false,
                maxLines = 1,
            ),
        ),
    ),
)

/** 操作行：删除（长按清空）与呼叫；无输入时呼叫按钮禁用。 */
private fun dialActionRow(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    onDialBackspace: () -> Unit,
    onDialClear: () -> Unit,
    onDialCall: () -> Unit,
): Widget = Row(
    spacing = LauncherSpacing.ROW_SPACING,
    crossAxisAlignment = CrossAxisAlignment.CENTER,
    children = listOf(
        Expanded(
            child = GestureDetector(
                onTap = onDialBackspace,
                onLongPress = onDialClear,
                child = Container(
                    alignment = Alignment.CENTER,
                    borderColor = theme.button.border,
                    padding = EdgeInsets.all(LauncherSpacing.BORDERED_CONTROL_INSET),
                    child = Text(
                        "DEL",
                        style = TextStyle(
                            color = if (uiState.dialInput.isEmpty()) {
                                theme.button.disabledText
                            } else {
                                theme.button.text
                            },
                        ),
                        textAlign = TextAlign.CENTER,
                        overflow = TextOverflow.ELLIPSIS,
                        softWrap = false,
                        maxLines = 1,
                    ),
                ),
            ),
        ),
        Expanded(
            child = OutlinedButton(
                text = "CALL",
                onPressed = onDialCall,
                enabled = DialInputModel.isCallable(uiState.dialInput),
                borderColor = theme.button.border,
            ),
        ),
    ),
)

/** 底部页签：等宽、文字居中、共用外边框，选中页纯色填充（UI 规范 §8）。 */
private fun callBottomTabs(
    selectedIndex: Int,
    theme: LauncherTheme,
    onSelected: (Int) -> Unit,
): Widget = Container(
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
                            child = Text(
                                label,
                                style = TextStyle(
                                    color = if (index == selectedIndex) {
                                        theme.surface.offPixelColor
                                    } else {
                                        theme.button.text
                                    },
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
