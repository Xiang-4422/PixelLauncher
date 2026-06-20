package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixellauncherv2.launcher.IdleStatusKind
import com.purride.pixellauncherv2.launcher.IdleStatusModel
import com.purride.pixellauncherv2.launcher.LauncherHeaderLayout
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * IDLE 屏幕：只保留低信息密度状态。
 * 旧 IdleFluidEngine 已废弃；本页完全通过 pixel-engine widget 组合表达。
 */
fun IdleScreen(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    statusBarHeight: Int = LauncherHeaderLayout.defaultStatusBarHeight,
): Widget {
    val subtitle = listOf(uiState.currentWeekdayText, uiState.currentDateText)
        .filter { it.isNotBlank() }
        .joinToString("  ")
        .ifBlank { "--- --- --" }
    val status = IdleStatusModel.line(uiState)
    val statusColor = when (status.kind) {
        IdleStatusKind.NOTIFICATION -> theme.semantic.warning
        IdleStatusKind.CHARGING -> theme.semantic.success
        IdleStatusKind.LOW_BATTERY -> theme.semantic.warning
        IdleStatusKind.WEATHER -> theme.semantic.info
        IdleStatusKind.DEFAULT -> theme.text.secondary
    }

    return Padding(
        padding = EdgeInsets.only(
            left = 2,
            top = statusBarHeight + 2,
            right = 2,
            bottom = 2,
        ),
        child = Column(
            mainAxisSize = MainAxisSize.MAX,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            spacing = 4,
            children = listOf(
                Expanded(child = SizedBox(width = 0, height = 0)),
                idleText(
                    text = uiState.currentTimeText.ifEmpty { "--:--" },
                    color = theme.text.primary,
                ),
                idleText(
                    text = subtitle,
                    color = theme.text.muted,
                ),
                Container(
                    padding = EdgeInsets.symmetric(horizontal = 2, vertical = 2),
                    borderColor = statusColor,
                    fillColor = theme.surface.panelSubtle,
                    child = Column(
                        mainAxisSize = MainAxisSize.MIN,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        spacing = 1,
                        children = listOf(
                            idleText(
                                text = status.title,
                                color = statusColor,
                            ),
                            idleText(
                                text = status.body,
                                color = theme.text.primary,
                            ),
                        ),
                    ),
                ),
                Expanded(child = SizedBox(width = 0, height = 0)),
                Row(
                    spacing = 2,
                    children = listOf(
                        Expanded(
                            child = idleText(
                                text = "BAT ${uiState.batteryLevel.coerceIn(0, 100)}%",
                                color = theme.text.secondary,
                            ),
                        ),
                        Expanded(
                            child = idleText(
                                text = "USE ${uiState.screenUsageTimeText.ifBlank { "--:--" }}",
                                color = theme.text.muted,
                                textAlign = TextAlign.END,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}

private fun idleText(
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
