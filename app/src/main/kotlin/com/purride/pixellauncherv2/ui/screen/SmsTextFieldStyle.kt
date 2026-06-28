package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.ButtonStyle
import com.purride.pixelui.TextFieldStyle
import com.purride.pixelui.TextStyle
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.ui.theme.LauncherTheme

internal fun smsTextFieldStyle(theme: LauncherTheme): TextFieldStyle = TextFieldStyle(
    fillColor = PixelColor.Transparent,
    borderColor = theme.sms.draftBorder,
    focusedBorderColor = theme.sms.draftBorder,
    disabledBorderColor = theme.button.disabledText,
    textStyle = TextStyle(color = theme.sms.body),
    placeholderStyle = TextStyle(color = theme.sms.timestamp),
    disabledTextStyle = TextStyle(color = theme.button.disabledText),
    disabledPlaceholderStyle = TextStyle(color = theme.button.disabledText),
    cursorColor = theme.semantic.info,
    selectionColor = theme.semantic.info,
    compositionColor = theme.semantic.info,
    selectionHandleColor = theme.semantic.info,
    padding = LauncherSpacing.BORDERED_CONTROL_INSET,
)

internal fun smsSendButtonStyle(theme: LauncherTheme): ButtonStyle = ButtonStyle(
    fillColor = PixelColor.Transparent,
    borderColor = theme.sms.draftBorder,
    textStyle = TextStyle(color = theme.sms.body),
)
