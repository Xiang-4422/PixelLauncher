package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelInputType
import com.purride.pixelui.Row
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextField
import com.purride.pixelui.TextFieldStyle
import com.purride.pixelui.TextInputAction
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.launcher.NotificationSettingsModel
import com.purride.pixellauncherv2.launcher.NotificationSettingsRow
import com.purride.pixellauncherv2.launcher.PixelFontCatalog
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/** 允许用户从全部 Drawer 应用中主动搜索并配置通知白名单。 */
class NotificationSettingsScreen(
    private val uiState: LauncherUiState,
    private val theme: LauncherTheme,
    private val onSourcePressed: (String) -> Unit,
    override val key: Any? = null,
) : StatefulWidget(key = key) {

    override fun createState(): State<out StatefulWidget> = NotificationSettingsState()

    private inner class NotificationSettingsState : State<NotificationSettingsScreen>() {

        /** 白名单候选列表的滚动控制器和状态。 */
        private val scrollController = PixelListController()
        private val scrollState: PixelListState = scrollController.create()
        /** 页面内搜索框的文本控制器和状态。 */
        private val searchController = TextEditingController()
        private val searchState = searchController.create()
        /** 当前搜索文本；仅属于白名单页面，不污染全局 Launcher 状态。 */
        private var searchQuery: String = ""

        override fun build(context: BuildContext): Widget {
            val rows = NotificationSettingsModel.rows(
                apps = widget.uiState.apps,
                sources = widget.uiState.notificationSources,
                allowedSourceIds = widget.uiState.allowedNotificationSourceIds,
                query = searchQuery,
            )
            val rowHeight = PixelFontCatalog.metrics(widget.uiState.fontSelection).cellHeight +
                LauncherSpacing.CONTENT_VERTICAL * 2
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
                            placeholder = "SEARCH APPS",
                            inputType = PixelInputType.ASCII,
                            textInputAction = TextInputAction.SEARCH,
                            style = notificationSearchFieldStyle(widget.theme),
                            onChanged = { value ->
                                setState { searchQuery = value }
                            },
                            semanticLabel = "Search notification whitelist apps",
                        ),
                    ),
                    Expanded(
                        child = Padding(
                            horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
                            vertical = LauncherSpacing.CONTENT_VERTICAL,
                            child = ListViewBuilder(
                                itemCount = rows.size.coerceAtLeast(1),
                                state = scrollState,
                                controller = scrollController,
                                itemExtent = rowHeight,
                                cacheExtent = 6,
                                spacing = LauncherSpacing.ROW_SPACING,
                                itemBuilder = { index ->
                                    val row = rows.getOrNull(index)
                                    if (row == null) {
                                        notificationEmptyRow(
                                            theme = widget.theme,
                                            hasQuery = searchQuery.isNotBlank(),
                                        )
                                    } else {
                                        notificationSourceRow(
                                            row = row,
                                            theme = widget.theme,
                                            onSourcePressed = widget.onSourcePressed,
                                        )
                                    }
                                },
                            ),
                        ),
                    ),
                ),
            )
        }
    }
}

private fun notificationSourceRow(
    row: NotificationSettingsRow,
    theme: LauncherTheme,
    onSourcePressed: (String) -> Unit,
): Widget = GestureDetector(
    onTap = { onSourcePressed(row.sourceId) },
    child = Padding(
        horizontal = 0,
        vertical = LauncherSpacing.CONTENT_VERTICAL,
        child = Row(
            spacing = LauncherSpacing.ROW_SPACING,
            mainAxisAlignment = MainAxisAlignment.START,
            crossAxisAlignment = CrossAxisAlignment.CENTER,
            children = listOf(
                Expanded(
                    child = notificationText(
                        text = row.label.uppercase(),
                        color = theme.text.primary,
                    ),
                ),
                Expanded(
                    child = notificationText(
                        text = if (row.isAllowed) "ON" else "OFF",
                        color = theme.text.secondary,
                        textAlign = TextAlign.END,
                    ),
                ),
            ),
        ),
    ),
)

/** 空候选与搜索无结果使用不同文案，便于区分数据状态。 */
private fun notificationEmptyRow(theme: LauncherTheme, hasQuery: Boolean): Widget = notificationText(
    text = if (hasQuery) "NO RESULTS" else "NO APPS",
    color = theme.text.muted,
)

/** 白名单搜索框沿用设置页的边框与主题语义色。 */
private fun notificationSearchFieldStyle(theme: LauncherTheme): TextFieldStyle = TextFieldStyle(
    fillColor = PixelColor.Transparent,
    borderColor = theme.button.border,
    focusedBorderColor = theme.semantic.info,
    disabledBorderColor = theme.button.disabledText,
    textStyle = TextStyle(color = theme.text.primary),
    placeholderStyle = TextStyle(color = theme.text.muted),
    disabledTextStyle = TextStyle(color = theme.button.disabledText),
    disabledPlaceholderStyle = TextStyle(color = theme.button.disabledText),
    cursorColor = theme.semantic.info,
    selectionColor = theme.semantic.info,
    compositionColor = theme.semantic.info,
    selectionHandleColor = theme.semantic.info,
    padding = LauncherSpacing.BORDERED_CONTROL_INSET,
)

private fun notificationText(
    text: String,
    color: PixelColor,
    textAlign: TextAlign = TextAlign.START,
): Widget = Text(
    text,
    style = TextStyle(color = color),
    textAlign = textAlign,
    overflow = TextOverflow.ELLIPSIS,
    softWrap = false,
    maxLines = 1,
)
