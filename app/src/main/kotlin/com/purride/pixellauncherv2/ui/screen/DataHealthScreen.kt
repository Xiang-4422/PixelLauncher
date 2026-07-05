package com.purride.pixellauncherv2.ui.screen

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
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixellauncherv2.launcher.DataHealthItem
import com.purride.pixellauncherv2.launcher.DataHealthLine
import com.purride.pixellauncherv2.launcher.DataHealthModel
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

class DataHealthScreen(
    private val uiState: LauncherUiState,
    private val theme: LauncherTheme,
    private val onItemPressed: (DataHealthItem) -> Unit,
    override val key: Any? = null,
) : StatefulWidget(key = key) {

    override fun createState(): State<out StatefulWidget> = DataHealthState()

    private inner class DataHealthState : State<DataHealthScreen>() {

        private val scrollController = PixelListController()
        private val scrollState: PixelListState = scrollController.create()

        override fun build(context: BuildContext): Widget {
            return Column(
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                mainAxisSize = MainAxisSize.MAX,
                spacing = 0,
                children = listOf(
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
                                    children = DataHealthModel.lines(widget.uiState).map { line ->
                                        dataHealthRow(
                                            line = line,
                                            theme = widget.theme,
                                            onPressed = widget.onItemPressed,
                                        )
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

private fun dataHealthRow(
    line: DataHealthLine,
    theme: LauncherTheme,
    onPressed: (DataHealthItem) -> Unit,
): Widget = GestureDetector(
    onTap = { onPressed(line.item) },
    child = Padding(
        horizontal = 0,
        vertical = LauncherSpacing.CONTENT_VERTICAL,
        child = Row(
            spacing = LauncherSpacing.ROW_SPACING,
            mainAxisAlignment = MainAxisAlignment.START,
            crossAxisAlignment = CrossAxisAlignment.CENTER,
            children = listOf(
                Expanded(
                    child = dataHealthTitleCell(line = line, theme = theme),
                ),
                Text(
                    line.value,
                    style = TextStyle(color = theme.text.primary),
                    overflow = TextOverflow.ELLIPSIS,
                    softWrap = false,
                    maxLines = 1,
                ),
            ),
        ),
    ),
)

private fun dataHealthTitleCell(
    line: DataHealthLine,
    theme: LauncherTheme,
): Widget {
    val reason = line.reason.trim()
    if (reason.isEmpty()) {
        return Text(
            line.title,
            style = TextStyle(color = theme.text.muted),
            overflow = TextOverflow.ELLIPSIS,
            softWrap = false,
            maxLines = 1,
        )
    }
    return Column(
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        mainAxisSize = MainAxisSize.MIN,
        spacing = 1,
        children = listOf(
            Text(
                line.title,
                style = TextStyle(color = theme.text.muted),
                overflow = TextOverflow.ELLIPSIS,
                softWrap = false,
                maxLines = 1,
            ),
            Text(
                reason,
                style = TextStyle(color = theme.text.secondary),
                overflow = TextOverflow.ELLIPSIS,
                softWrap = false,
                maxLines = 1,
            ),
        ),
    )
}
