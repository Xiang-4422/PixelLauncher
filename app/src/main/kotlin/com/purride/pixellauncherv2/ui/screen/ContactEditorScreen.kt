package com.purride.pixellauncherv2.ui.screen

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
import com.purride.pixelui.Row
import com.purride.pixelui.Semantics
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextField
import com.purride.pixelui.TextFieldStyle
import com.purride.pixelui.TextInputAction
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixellauncherv2.launcher.ContactEditorModel
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * 联系人编辑器：新建（姓名 + 号码）或编辑既有联系人（改名 / 删号 / 加号）。
 *
 * 语义刻意极简：固定两个输入框——姓名与"新增号码"；既有号码只能整条删除，
 * 改号 = 删旧 + 加新。动态数量的输入框在点阵 UI 上成本高且易错。
 * SAVE 是本屏唯一主操作，用全屏唯一的反色实心块。
 */
fun ContactEditorScreen(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    nameController: PixelTextFieldController,
    nameState: PixelTextFieldState,
    numberController: PixelTextFieldController,
    numberState: PixelTextFieldState,
    onNameChanged: (String) -> Unit,
    onNumberChanged: (String) -> Unit,
    onDeleteNumber: (dataId: Long) -> Unit,
    onSave: () -> Unit,
): Widget {
    val existing = uiState.contacts.firstOrNull { contact ->
        contact.lookupKey == uiState.contactEditorLookupKey
    }
    val canSave = ContactEditorModel.canSave(
        name = uiState.contactEditorNameDraft,
        numberDraft = uiState.contactEditorNumberDraft,
        hasExistingContact = existing != null,
    )
    val inputStyle = editorTextFieldStyle(theme)
    return Padding(
        horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
        vertical = LauncherSpacing.CONTENT_VERTICAL,
        child = Column(
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            mainAxisSize = MainAxisSize.MAX,
            spacing = LauncherSpacing.ROW_SPACING,
            children = buildList {
                add(editorFieldLabel("NAME", theme))
                add(
                    TextField(
                        state = nameState,
                        controller = nameController,
                        placeholder = "CONTACT NAME",
                        style = inputStyle,
                        textInputAction = TextInputAction.NEXT,
                        onChanged = onNameChanged,
                    ),
                )
                // 既有号码：整条删除，不逐位编辑。
                existing?.numbers?.forEach { phone ->
                    add(
                        Row(
                            spacing = LauncherSpacing.ROW_SPACING,
                            crossAxisAlignment = CrossAxisAlignment.CENTER,
                            children = listOf(
                                Expanded(
                                    child = editorText(phone.number, theme.text.primary, theme),
                                ),
                                Semantics(
                                    label = "DELETE ${phone.number}",
                                    role = PixelSemanticRole.BUTTON,
                                    child = GestureDetector(
                                        onTap = { onDeleteNumber(phone.dataId) },
                                        child = Container(
                                            borderColor = theme.semantic.danger,
                                            padding = EdgeInsets.symmetric(
                                                horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
                                                vertical = LauncherSpacing.ROW_SPACING,
                                            ),
                                            child = editorText("DEL", theme.semantic.danger, theme),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    )
                }
                add(editorFieldLabel(if (existing == null) "NUMBER" else "ADD NUMBER", theme))
                add(
                    TextField(
                        state = numberState,
                        controller = numberController,
                        placeholder = "PHONE NUMBER",
                        style = inputStyle,
                        textInputAction = TextInputAction.DONE,
                        onChanged = onNumberChanged,
                        onSubmitted = { onSave() },
                    ),
                )
                add(Expanded(child = Column(mainAxisSize = MainAxisSize.MAX, spacing = 0, children = emptyList())))
                add(editorSaveBar(theme, canSave, onSave))
            },
        ),
    )
}

/** 主操作：通栏反色实心 SAVE；不可保存时退为边框 + 弱化文字，点按无反应由控制器校验兜底。 */
private fun editorSaveBar(
    theme: LauncherTheme,
    enabled: Boolean,
    onSave: () -> Unit,
): Widget = Semantics(
    label = "SAVE",
    role = PixelSemanticRole.BUTTON,
    enabled = enabled,
    child = GestureDetector(
        onTap = onSave,
        child = Container(
            alignment = Alignment.CENTER,
            fillColor = if (enabled) theme.button.filledSurface else PixelColor.Transparent,
            borderColor = if (enabled) null else theme.button.border,
            padding = EdgeInsets.symmetric(
                horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
                vertical = LauncherSpacing.ROW_SPACING,
            ),
            child = Text(
                "SAVE",
                style = theme.typography.textStyle(
                    color = if (enabled) theme.button.filledText else theme.button.disabledText,
                ),
                textAlign = TextAlign.CENTER,
                overflow = TextOverflow.ELLIPSIS,
                softWrap = false,
                maxLines = 1,
            ),
        ),
    ),
)

private fun editorFieldLabel(text: String, theme: LauncherTheme): Widget =
    editorText(text, theme.sms.timestamp, theme)

private fun editorText(text: String, color: PixelColor, theme: LauncherTheme): Widget = Text(
    text,
    style = theme.typography.textStyle(color = color),
    overflow = TextOverflow.ELLIPSIS,
    softWrap = false,
    maxLines = 1,
)

private fun editorTextFieldStyle(theme: LauncherTheme): TextFieldStyle = TextFieldStyle(
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
