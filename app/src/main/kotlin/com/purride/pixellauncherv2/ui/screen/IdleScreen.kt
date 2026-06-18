package com.purride.pixellauncherv2.ui.screen

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
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixellauncherv2.launcher.SettingsMenuModel
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * IDLE 屏幕：只保留低信息密度状态。
 * 旧 IdleFluidEngine 已废弃；本页完全通过 pixel-engine widget 组合表达。
 */
fun IdleScreen(
    uiState: LauncherUiState,
    theme: LauncherTheme,
): Widget {
    val subtitle = listOf(uiState.currentWeekdayText, uiState.currentDateText)
        .filter { it.isNotBlank() }
        .joinToString("  ")
        .ifBlank { "--- --- --" }
    val status = uiState.idleStatusModel()
    val statusColor = when (status.kind) {
        IdleStatusKind.NOTIFICATION -> theme.semantic.warning
        IdleStatusKind.CHARGING -> theme.semantic.success
        IdleStatusKind.WEATHER -> theme.semantic.info
        IdleStatusKind.DEFAULT -> theme.text.secondary
    }

    return Padding(
        horizontal = 2,
        vertical = 2,
        child = Column(
            mainAxisSize = MainAxisSize.MAX,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            spacing = 4,
            children = listOf(
                Expanded(child = SizedBox(width = 0, height = 0)),
                Text(
                    uiState.currentTimeText.ifEmpty { "--:--" },
                    style = TextStyle(color = theme.text.primary, fontScale = 2),
                ),
                Text(
                    subtitle,
                    style = TextStyle(color = theme.text.muted),
                    overflow = TextOverflow.ELLIPSIS,
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
                            Text(
                                status.title,
                                style = TextStyle(color = statusColor),
                                overflow = TextOverflow.ELLIPSIS,
                            ),
                            Text(
                                status.body,
                                style = TextStyle(color = theme.text.primary),
                                overflow = TextOverflow.ELLIPSIS,
                            ),
                        ),
                    ),
                ),
                Expanded(child = SizedBox(width = 0, height = 0)),
                Row(
                    spacing = 2,
                    children = listOf(
                        Text(
                            "BAT ${uiState.batteryLevel.coerceIn(0, 100)}%",
                            style = TextStyle(color = theme.text.secondary),
                        ),
                        Expanded(child = SizedBox(width = 0, height = 0)),
                        Text(
                            "USE ${uiState.screenUsageTimeText.ifBlank { "--:--" }}",
                            style = TextStyle(color = theme.text.muted),
                        ),
                    ),
                ),
            ),
        ),
    )
}

private enum class IdleStatusKind {
    NOTIFICATION,
    CHARGING,
    WEATHER,
    DEFAULT,
}

private data class IdleStatusModel(
    val kind: IdleStatusKind,
    val title: String,
    val body: String,
)

private fun LauncherUiState.idleStatusModel(): IdleStatusModel {
    val notifications = buildList {
        if (missedCallCount > 0) add("CALL $missedCallCount")
        if (unreadSmsCount > 0) add("SMS $unreadSmsCount")
    }
    if (notifications.isNotEmpty()) {
        return IdleStatusModel(
            kind = IdleStatusKind.NOTIFICATION,
            title = "NOTIFY",
            body = notifications.joinToString("  "),
        )
    }

    if (isCharging) {
        return IdleStatusModel(
            kind = IdleStatusKind.CHARGING,
            title = "CHARGING",
            body = SettingsMenuModel.chargeIdleEffectLabel(chargeIdleEffect),
        )
    }

    if (rainHintText.isNotBlank()) {
        return IdleStatusModel(
            kind = IdleStatusKind.WEATHER,
            title = "WEATHER",
            body = rainHintText,
        )
    }

    return IdleStatusModel(
        kind = IdleStatusKind.DEFAULT,
        title = "IDLE",
        body = nextAlarmText.takeIf { it.isNotBlank() && it != "--:--" }?.let { "ALARM $it" }
            ?: "NO PRIORITY STATE",
    )
}
