package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixellauncherv2.ui.theme.LauncherTheme

internal fun smsStatusText(
    text: String,
    theme: LauncherTheme,
): Widget = Text(
    text,
    style = TextStyle(color = theme.sms.timestamp),
    textAlign = TextAlign.CENTER,
    overflow = TextOverflow.ELLIPSIS,
    softWrap = false,
    maxLines = 1,
)
