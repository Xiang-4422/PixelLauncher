package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.Row
import com.purride.pixelui.Semantics
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextButtonStyle
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixellauncherv2.launcher.HomeInfoAction
import com.purride.pixellauncherv2.launcher.HomeInfoLine
import com.purride.pixellauncherv2.launcher.HomeInfoModel
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

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
 */
class HomeScreen(
    private val uiState: LauncherUiState,
    private val theme: LauncherTheme,
    private val onOpenCall: () -> Unit,
    private val onOpenSms: () -> Unit,
    private val onInfoAction: (HomeInfoAction) -> Unit,
    private val onInfoDetail: (HomeInfoAction) -> Unit,
    override val key: Any? = null,
) : StatefulWidget(key = key) {

    override fun createState(): State<out StatefulWidget> = HomeState()

    private inner class HomeState : State<HomeScreen>() {

        override fun build(context: BuildContext): Widget {
            val s = widget.uiState
            val t = widget.theme
            val actionButtonStyle = TextButtonStyle(
                textStyle = TextStyle(color = t.button.text),
            )

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
                    Expanded(child = SizedBox(width = 0, height = 0)),
                    Padding(
                        padding = EdgeInsets.only(
                            left = LauncherSpacing.EDGE_ACTION,
                            right = LauncherSpacing.EDGE_ACTION,
                            bottom = LauncherSpacing.EDGE_ACTION,
                        ),
                        child = Row(
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
                                ),
                                Expanded(child = SizedBox(width = 0, height = 0)),
                                HomeActionButton(
                                    label = "SMS",
                                    count = s.unreadSmsCount,
                                    countOnStart = true,
                                    theme = t,
                                    onPressed = widget.onOpenSms,
                                    style = actionButtonStyle,
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }

        private fun buildInfoColumn(s: LauncherUiState, t: LauncherTheme): List<Widget> =
            buildList {
                // Date row (primary color)
                add(
                    Text(
                        s.currentDateText.ifBlank { "--- --- --" },
                        style = TextStyle(color = t.text.primary),
                        overflow = TextOverflow.ELLIPSIS,
                        softWrap = false,
                        maxLines = 1,
                    ),
                )
                add(
                    HomeInfoRow(
                        line = HomeInfoModel.weatherLine(s),
                        theme = t,
                        onAction = widget.onInfoAction,
                        onDetail = widget.onInfoDetail,
                    ),
                )
                HomeInfoModel.lines(s).forEach { line ->
                    add(
                        HomeInfoRow(
                            line = line,
                            theme = t,
                            onAction = widget.onInfoAction,
                            onDetail = widget.onInfoDetail,
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
): Widget {
    val text = Text(
        line.text,
        style = TextStyle(color = theme.text.primary),
        overflow = TextOverflow.ELLIPSIS,
        softWrap = false,
        maxLines = 1,
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

private fun HomeActionButton(
    label: String,
    count: Int,
    countOnStart: Boolean,
    theme: LauncherTheme,
    onPressed: () -> Unit,
    style: TextButtonStyle,
): Widget {
    if (count <= 0) {
        return TextButton(
            text = label,
            onPressed = onPressed,
            style = style,
        )
    }

    val labelSegment = homeActionSegment(
        text = label,
        textStyle = TextStyle(color = theme.button.text),
        fillColor = null,
    )
    val countSegment = homeActionSegment(
        text = count.toString(),
        textStyle = TextStyle(color = theme.text.inverse),
        fillColor = theme.button.border,
    )
    val divider = homeActionDivider(theme)
    val children = if (countOnStart) {
        listOf(countSegment, divider, labelSegment)
    } else {
        listOf(labelSegment, divider, countSegment)
    }

    return Semantics(
        label = "$label $count",
        role = PixelSemanticRole.BUTTON,
        enabled = true,
        child = GestureDetector(
            onTap = onPressed,
            child = Container(
                borderColor = theme.button.border,
                padding = EdgeInsets.all(HOME_ACTION_BORDER_PX),
                child = Row(
                    spacing = 0,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                    children = children,
                ),
            ),
        ),
    )
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

private const val HOME_ACTION_BORDER_PX = 1
private const val HOME_ACTION_DIVIDER_PX = 1
private const val HOME_ACTION_SEGMENT_HEIGHT_PX = 11
private const val HOME_ACTION_SEGMENT_HORIZONTAL_PADDING_PX = 2
