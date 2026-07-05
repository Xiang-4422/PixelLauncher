package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelui.BuildContext
import com.purride.pixelcore.PixelColor
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
import com.purride.pixellauncherv2.launcher.DiagnosticsLine
import com.purride.pixellauncherv2.launcher.DiagnosticsModel
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * DIAGNOSTICS 屏幕：显示短键值诊断数据。
 *
 * 数据来源：[DiagnosticsModel.lines]（LauncherUiState 重载）。
 */
class DiagnosticsScreen(
    private val uiState: LauncherUiState,
    private val theme: LauncherTheme,
    private val screenProfile: ScreenProfile,
    private val onOpenDataHealth: () -> Unit,
    override val key: Any? = null,
) : StatefulWidget(key = key) {

    override fun createState(): State<out StatefulWidget> = DiagnosticsState()

    private inner class DiagnosticsState : State<DiagnosticsScreen>() {

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
                                    children = DiagnosticsModel
                                        .lines(widget.uiState, widget.screenProfile)
                                        .map { line ->
                                            diagnosticsRow(
                                                line = line,
                                                theme = widget.theme,
                                                onOpenDataHealth = widget.onOpenDataHealth,
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

private fun diagnosticsRow(
    line: DiagnosticsLine,
    theme: LauncherTheme,
    onOpenDataHealth: () -> Unit,
): Widget {
    val row = diagnosticsLineRow(line = line, theme = theme)
    return if (line.title == "DEBUG" && line.value == "DATA HEALTH") {
        GestureDetector(onTap = onOpenDataHealth, child = row)
    } else {
        row
    }
}

private fun diagnosticsLineRow(
    line: DiagnosticsLine,
    theme: LauncherTheme,
): Widget = Row(
    spacing = LauncherSpacing.ROW_SPACING,
    mainAxisAlignment = MainAxisAlignment.START,
    crossAxisAlignment = CrossAxisAlignment.CENTER,
    children = listOf(
        Expanded(
            child = diagnosticsText(
                text = line.title,
                color = theme.text.muted,
            ),
        ),
        Expanded(
            child = diagnosticsText(
                text = line.value,
                color = theme.text.primary,
                textAlign = TextAlign.END,
            ),
        ),
    ),
)

private fun diagnosticsText(
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
