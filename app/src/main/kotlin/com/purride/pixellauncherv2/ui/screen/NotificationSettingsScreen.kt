package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixellauncherv2.launcher.LauncherHeaderLayout
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.launcher.NotificationSettingsModel
import com.purride.pixellauncherv2.launcher.NotificationSettingsRow
import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.widget.LauncherHeader
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

class NotificationSettingsScreen(
    private val uiState: LauncherUiState,
    private val theme: LauncherTheme,
    private val chargeTick: Int,
    private val screenProfile: ScreenProfile,
    private val onSourcePressed: (String) -> Unit,
    override val key: Any? = null,
) : StatefulWidget(key = key) {

    override fun createState(): State<out StatefulWidget> = NotificationSettingsState()

    private inner class NotificationSettingsState : State<NotificationSettingsScreen>() {

        private val scrollController = PixelListController()
        private val scrollState: PixelListState = scrollController.create()

        override fun build(context: BuildContext): Widget {
            val rows = NotificationSettingsModel.rows(
                sources = widget.uiState.notificationSources,
                mutedSourceIds = widget.uiState.mutedNotificationSourceIds,
                prioritySourceIds = widget.uiState.priorityNotificationSourceIds,
            )
            return Column(
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                mainAxisSize = MainAxisSize.MAX,
                spacing = 0,
                children = listOf(
                    LauncherHeader(
                        timeText = widget.uiState.currentTimeText.ifEmpty { "--:--" },
                        screenTitle = "NOTIFY",
                        messageText = widget.uiState.statusBarMessageText,
                        batteryLevel = widget.uiState.batteryLevel,
                        isCharging = widget.uiState.isCharging,
                        chargeTick = widget.chargeTick,
                        theme = widget.theme,
                        statusBarHeight = LauncherHeaderLayout.statusBarHeight(widget.screenProfile),
                    ),
                    Expanded(
                        child = SingleChildScrollView(
                            state = scrollState,
                            controller = scrollController,
                            child = Padding(
                                horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
                                vertical = LauncherSpacing.CONTENT_VERTICAL,
                                child = Column(
                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                    mainAxisSize = MainAxisSize.MIN,
                                    spacing = LauncherSpacing.ROW_SPACING,
                                    children = if (rows.isEmpty()) {
                                        listOf(notificationEmptyRow(widget.theme))
                                    } else {
                                        rows.map { row ->
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
                        text = row.label,
                        color = theme.text.primary,
                    ),
                ),
                Expanded(
                    child = notificationText(
                        text = NotificationSettingsModel.modeLabel(row.mode),
                        color = theme.text.secondary,
                        textAlign = TextAlign.END,
                    ),
                ),
            ),
        ),
    ),
)

private fun notificationEmptyRow(theme: LauncherTheme): Widget = notificationText(
    text = "NO SOURCES",
    color = theme.text.muted,
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
