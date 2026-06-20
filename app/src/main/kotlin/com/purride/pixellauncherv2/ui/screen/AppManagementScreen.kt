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
import com.purride.pixellauncherv2.launcher.LauncherHeaderLayout
import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.widget.LauncherHeader
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

class AppManagementScreen(
    private val uiState: LauncherUiState,
    private val theme: LauncherTheme,
    private val chargeTick: Int,
    private val screenProfile: ScreenProfile,
    private val nameController: PixelTextFieldController,
    private val nameState: PixelTextFieldState,
    private val aliasController: PixelTextFieldController,
    private val aliasState: PixelTextFieldState,
    private val onPrevious: () -> Unit,
    private val onNext: () -> Unit,
    private val onNameChanged: (String) -> Unit,
    private val onAliasChanged: (String) -> Unit,
    private val onSave: () -> Unit,
    private val onReset: () -> Unit,
    private val onCacheReset: () -> Unit,
    override val key: Any? = null,
) : StatefulWidget(key = key) {

    override fun createState(): State<out StatefulWidget> = AppManagementState()

    private inner class AppManagementState : State<AppManagementScreen>() {

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
                        screenTitle = "APPS",
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
                            child = appManagementBody(
                                uiState = widget.uiState,
                                theme = widget.theme,
                                nameController = widget.nameController,
                                nameState = widget.nameState,
                                aliasController = widget.aliasController,
                                aliasState = widget.aliasState,
                                onPrevious = widget.onPrevious,
                                onNext = widget.onNext,
                                onNameChanged = widget.onNameChanged,
                                onAliasChanged = widget.onAliasChanged,
                                onSave = widget.onSave,
                                onReset = widget.onReset,
                                onCacheReset = widget.onCacheReset,
                            ),
                        ),
                    ),
                ),
            )
        }
    }
}

private fun appManagementBody(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    nameController: PixelTextFieldController,
    nameState: PixelTextFieldState,
    aliasController: PixelTextFieldController,
    aliasState: PixelTextFieldState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onNameChanged: (String) -> Unit,
    onAliasChanged: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onCacheReset: () -> Unit,
): Widget {
    val selectedApp = uiState.apps.getOrNull(uiState.appEditorSelectedIndex)
    val indexText = if (uiState.apps.isEmpty()) {
        "0/0"
    } else {
        "${uiState.appEditorSelectedIndex + 1}/${uiState.apps.size}"
    }
    val inputStyle = appTextFieldStyle(theme)
    return Padding(
        horizontal = 2,
        vertical = 2,
        child = Column(
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            mainAxisSize = MainAxisSize.MIN,
            spacing = 2,
            children = listOf(
                keyValueRow("APP", indexText, theme),
                Text(
                    selectedApp?.label?.uppercase().orEmpty().ifBlank { "NO APPS" },
                    style = TextStyle(color = theme.text.primary),
                    overflow = TextOverflow.ELLIPSIS,
                    softWrap = false,
                    maxLines = 1,
                ),
                Text(
                    selectedApp?.systemLabel?.uppercase().orEmpty(),
                    style = TextStyle(color = theme.text.muted),
                    overflow = TextOverflow.ELLIPSIS,
                    softWrap = false,
                    maxLines = 1,
                ),
                fieldLabel("NAME", theme),
                TextField(
                    state = nameState,
                    controller = nameController,
                    placeholder = "DISPLAY NAME",
                    style = inputStyle,
                    enabled = selectedApp != null,
                    textInputAction = TextInputAction.NEXT,
                    onChanged = onNameChanged,
                ),
                fieldLabel("ALIAS", theme),
                TextField(
                    state = aliasState,
                    controller = aliasController,
                    placeholder = "SEARCH ALIAS",
                    style = inputStyle,
                    enabled = selectedApp != null,
                    textInputAction = TextInputAction.DONE,
                    onChanged = onAliasChanged,
                    onSubmitted = { onSave() },
                ),
                Row(
                    spacing = 2,
                    children = listOf(
                        appCommandButton("PREV", theme, selectedApp != null, onPrevious),
                        Expanded(child = SizedBox(width = 0, height = 0)),
                        appCommandButton("NEXT", theme, selectedApp != null, onNext),
                    ),
                ),
                Row(
                    spacing = 2,
                    children = listOf(
                        appCommandButton("SAVE", theme, selectedApp != null, onSave),
                        Expanded(child = SizedBox(width = 0, height = 0)),
                        appCommandButton("RESET", theme, selectedApp != null, onReset),
                    ),
                ),
                Row(
                    spacing = 2,
                    children = listOf(
                        appCommandButton("REFRESH", theme, true, onCacheReset),
                        Expanded(child = SizedBox(width = 0, height = 0)),
                    ),
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
    spacing = 2,
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

private fun appTextFieldStyle(theme: LauncherTheme): TextFieldStyle = TextFieldStyle(
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
    padding = 2,
)

private fun appCommandButton(
    text: String,
    theme: LauncherTheme,
    enabled: Boolean,
    onTap: () -> Unit,
): Widget {
    val content = Container(
        borderColor = if (enabled) theme.button.border else theme.button.disabledText,
        fillColor = PixelColor.Transparent,
        padding = EdgeInsets.all(2),
        alignment = Alignment.CENTER,
        child = Text(
            text,
            style = TextStyle(color = if (enabled) theme.button.text else theme.button.disabledText),
            overflow = TextOverflow.ELLIPSIS,
            softWrap = false,
            maxLines = 1,
        ),
    )
    return if (enabled) {
        GestureDetector(onTap = onTap, child = content)
    } else {
        content
    }
}
