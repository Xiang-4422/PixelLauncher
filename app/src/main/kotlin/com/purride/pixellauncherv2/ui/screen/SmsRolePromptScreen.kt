package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixellauncherv2.ui.theme.LauncherTheme

/**
 * SMS_ROLE_PROMPT 屏幕：提示用户将 PixelLauncher 设为默认短信应用。
 *
 * 显示说明文字 + CONTINUE 按钮。
 */
fun SmsRolePromptScreen(
    theme: LauncherTheme,
    onRequestRole: () -> Unit,
): Widget = Center(
    child = Column(
        mainAxisSize = MainAxisSize.MIN,
        mainAxisAlignment = MainAxisAlignment.CENTER,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
        spacing = 2,
        children = listOf(
            Text("SET PIXEL LAUNCHER", style = TextStyle(color = theme.primaryColor)),
            Text("AS DEFAULT SMS APP", style = TextStyle(color = theme.primaryColor)),
            Text("TO RECEIVE AND REPLY", style = TextStyle(color = theme.primaryColor)),
            SizedBox(height = 4),
            OutlinedButton(
                text = "CONTINUE",
                onPressed = onRequestRole,
                borderColor = theme.accentColor,
            ),
        ),
    ),
)
