package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextField
import com.purride.pixelui.TextFieldStyle
import com.purride.pixelui.TextInputAction
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixellauncherv2.data.DeepSeekAiConfig
import com.purride.pixellauncherv2.launcher.LauncherHeaderLayout
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.launcher.SettingsMenuModel
import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.widget.LauncherHeader
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

class AiSettingsScreen(
    private val uiState: LauncherUiState,
    private val theme: LauncherTheme,
    private val chargeTick: Int,
    private val screenProfile: ScreenProfile,
    private val apiKeyController: PixelTextFieldController,
    private val apiKeyState: PixelTextFieldState,
    private val onDeepSeekApiKeyChanged: (String) -> Unit,
    override val key: Any? = null,
) : StatefulWidget(key = key) {

    override fun createState(): State<out StatefulWidget> = AiSettingsState()

    private inner class AiSettingsState : State<AiSettingsScreen>() {
        private val scrollController = PixelListController()
        private val scrollState: PixelListState = scrollController.create()

        override fun build(context: BuildContext): Widget {
            return Column(
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                mainAxisSize = MainAxisSize.MAX,
                spacing = 0,
                children = listOf(
                    LauncherHeader(
                        timeText = widget.uiState.currentTimeText.ifEmpty { "--:--" },
                        screenTitle = "AI",
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
                            child = aiSettingsBody(
                                uiState = widget.uiState,
                                theme = widget.theme,
                                apiKeyController = widget.apiKeyController,
                                apiKeyState = widget.apiKeyState,
                                onDeepSeekApiKeyChanged = widget.onDeepSeekApiKeyChanged,
                            ),
                        ),
                    ),
                ),
            )
        }
    }
}

private fun aiSettingsBody(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    apiKeyController: PixelTextFieldController,
    apiKeyState: PixelTextFieldState,
    onDeepSeekApiKeyChanged: (String) -> Unit,
): Widget {
    return Padding(
        horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
        vertical = LauncherSpacing.CONTENT_VERTICAL,
        child = Column(
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            mainAxisSize = MainAxisSize.MIN,
            spacing = LauncherSpacing.ROW_SPACING,
            children = listOf(
                keyValueRow("PROVIDER", "DEEPSEEK", theme),
                keyValueRow("BASE URL", DeepSeekAiConfig.BASE_URL, theme),
                keyValueRow("API KEY", SettingsMenuModel.apiKeyLabel(uiState.deepSeekApiKey), theme),
                fieldLabel("DEEPSEEK API KEY", theme),
                TextField(
                    state = apiKeyState,
                    controller = apiKeyController,
                    placeholder = "API KEY",
                    style = aiTextFieldStyle(theme),
                    autofocus = true,
                    textInputAction = TextInputAction.DONE,
                    onChanged = onDeepSeekApiKeyChanged,
                ),
            ),
        ),
    )
}

private fun keyValueRow(
    key: String,
    value: String,
    theme: LauncherTheme,
): Widget = Row(
    spacing = LauncherSpacing.ROW_SPACING,
    children = listOf(
        Text(
            key,
            style = TextStyle(color = theme.text.muted),
            overflow = TextOverflow.ELLIPSIS,
            softWrap = false,
            maxLines = 1,
        ),
        Expanded(child = SizedBox(width = 0, height = 0)),
        Text(
            value,
            style = TextStyle(color = theme.text.primary),
            overflow = TextOverflow.ELLIPSIS,
            softWrap = false,
            maxLines = 1,
        ),
    ),
)

private fun fieldLabel(
    text: String,
    theme: LauncherTheme,
): Widget = Text(
    text,
    style = TextStyle(color = theme.text.muted),
    overflow = TextOverflow.ELLIPSIS,
    softWrap = false,
    maxLines = 1,
)

private fun aiTextFieldStyle(theme: LauncherTheme): TextFieldStyle = TextFieldStyle(
    fillColor = PixelColor.Transparent,
    borderColor = theme.text.inverse,
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
