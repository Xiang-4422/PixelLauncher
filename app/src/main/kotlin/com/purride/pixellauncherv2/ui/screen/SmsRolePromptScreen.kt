package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixellauncherv2.ui.theme.LauncherTheme

private const val SMS_ROLE_PROMPT_PADDING_PX = 2

/**
 * SMS_ROLE_PROMPT 屏幕：提示用户将 PixelLauncher 设为默认短信应用。
 *
 * 显示说明文字 + CONTINUE 按钮。
 */
fun SmsRolePromptScreen(
    theme: LauncherTheme,
    onRequestRole: () -> Unit,
): Widget = Center(
    child = Padding(
        horizontal = SMS_ROLE_PROMPT_PADDING_PX,
        vertical = SMS_ROLE_PROMPT_PADDING_PX,
        child = Column(
            mainAxisSize = MainAxisSize.MIN,
            mainAxisAlignment = MainAxisAlignment.CENTER,
            crossAxisAlignment = CrossAxisAlignment.CENTER,
            spacing = 2,
            children = listOf(
                promptLine("SET PIXEL LAUNCHER", theme),
                promptLine("AS DEFAULT SMS APP", theme),
                promptLine("TO RECEIVE AND REPLY", theme),
                SizedBox(height = 4),
                OutlinedButton(
                    text = "CONTINUE",
                    onPressed = onRequestRole,
                    borderColor = theme.button.border,
                ),
            ),
        ),
    ),
)

private fun promptLine(
    text: String,
    theme: LauncherTheme,
): Widget = Text(
    text,
    style = TextStyle(color = theme.text.primary),
    textAlign = TextAlign.CENTER,
    overflow = TextOverflow.ELLIPSIS,
    softWrap = false,
    maxLines = 1,
)
