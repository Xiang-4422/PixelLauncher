package com.purride.pixellauncherv2.ui.widget

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Slider
import com.purride.pixelui.Text
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixellauncherv2.ui.theme.LauncherTheme

private const val SETTINGS_SWITCH_PADDING_PX = 1
private const val SETTINGS_SWITCH_SEGMENT_GAP_PX = 1
private const val SETTINGS_SWITCH_LABEL_HORIZONTAL_PADDING_PX = 1
private const val SETTINGS_SWITCH_LABEL_VERTICAL_PADDING_PX = 1

fun SettingsValueSlider(
    title: String,
    valueLabel: String,
    value: Float,
    theme: LauncherTheme,
    live: Boolean = false,
    onStepDown: () -> Unit,
    onStepUp: () -> Unit,
    onValuePreview: (Float) -> Unit = {},
    onValueChanged: (Float) -> Unit,
): Widget = Container(
    padding = EdgeInsets.symmetric(horizontal = 2, vertical = 1),
    child = Column(
        mainAxisSize = MainAxisSize.MAX,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        spacing = 2,
        children = listOf(
            Row(
                spacing = 2,
                children = listOf(
                    GestureDetector(
                        onTap = onStepDown,
                        child = Text(
                            title,
                            style = TextStyle(color = theme.settings.itemTitle),
                            overflow = TextOverflow.ELLIPSIS,
                        ),
                    ),
                    Expanded(child = SizedBox(width = 0, height = 0)),
                    GestureDetector(
                        onTap = onStepUp,
                        child = Text(
                            valueLabel,
                            style = TextStyle(color = theme.settings.itemValue),
                            overflow = TextOverflow.ELLIPSIS,
                        ),
                    ),
                ),
            ),
            Slider(
                value = value.coerceIn(0f, 1f),
                onDrag = if (live) onValueChanged else onValuePreview,
                onRelease = onValueChanged,
                activeColor = theme.semantic.info,
                trackColor = theme.text.muted,
            ),
        ),
    ),
)

fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    theme: LauncherTheme,
    showLabels: Boolean = true,
    offLabel: String = "OFF",
    onLabel: String = "ON",
    onToggle: () -> Unit,
): Widget = Container(
    padding = EdgeInsets.symmetric(horizontal = 2, vertical = 1),
    child = Row(
        spacing = 2,
        children = listOf(
            Text(
                title,
                style = TextStyle(color = theme.settings.itemTitle),
                overflow = TextOverflow.ELLIPSIS,
            ),
            Expanded(child = SizedBox(width = 0, height = 0)),
            SettingsSwitch(
                checked = checked,
                theme = theme,
                showLabels = showLabels,
                offLabel = offLabel,
                onLabel = onLabel,
                onToggle = onToggle,
            ),
        ),
    ),
)

fun SettingsSegmentedSwitchRow(
    title: String,
    rightSelected: Boolean,
    leftLabel: String,
    rightLabel: String,
    theme: LauncherTheme,
    onToggle: () -> Unit,
): Widget = SettingsSwitchRow(
    title = title,
    checked = rightSelected,
    theme = theme,
    showLabels = true,
    offLabel = leftLabel,
    onLabel = rightLabel,
    onToggle = onToggle,
)

fun SettingsOptionStepperRow(
    title: String,
    valueLabel: String,
    theme: LauncherTheme,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
): Widget = Container(
    padding = EdgeInsets.symmetric(horizontal = 2, vertical = 1),
    child = Row(
        spacing = 2,
        children = listOf(
            GestureDetector(
                onTap = onPrevious,
                child = Text(
                    title,
                    style = TextStyle(color = theme.settings.itemTitle),
                    overflow = TextOverflow.ELLIPSIS,
                ),
            ),
            Expanded(child = SizedBox(width = 0, height = 0)),
            GestureDetector(
                onTap = onNext,
                child = Text(
                    valueLabel,
                    style = TextStyle(color = theme.settings.itemValue),
                    overflow = TextOverflow.ELLIPSIS,
                ),
            ),
        ),
    ),
)

fun SettingsActionRow(
    title: String,
    valueLabel: String,
    theme: LauncherTheme,
    onPressed: () -> Unit,
): Widget = GestureDetector(
    onTap = onPressed,
    child = Container(
        padding = EdgeInsets.symmetric(horizontal = 2, vertical = 1),
        child = Row(
            spacing = 2,
            children = listOf(
                Text(
                    title,
                    style = TextStyle(color = theme.settings.itemTitle),
                    overflow = TextOverflow.ELLIPSIS,
                ),
                Expanded(child = SizedBox(width = 0, height = 0)),
                Text(
                    valueLabel,
                    style = TextStyle(color = theme.settings.itemValue),
                    overflow = TextOverflow.ELLIPSIS,
                ),
            ),
        ),
    ),
)

private fun SettingsSwitch(
    checked: Boolean,
    theme: LauncherTheme,
    showLabels: Boolean,
    offLabel: String,
    onLabel: String,
    onToggle: () -> Unit,
): Widget {
    val effectiveOffLabel = if (showLabels) offLabel else ""
    val effectiveOnLabel = if (showLabels) onLabel else ""
    return GestureDetector(
        onTap = onToggle,
        child = Container(
            borderColor = theme.button.border,
            padding = EdgeInsets.all(SETTINGS_SWITCH_PADDING_PX),
            child = Row(
                spacing = SETTINGS_SWITCH_SEGMENT_GAP_PX,
                children = listOf(
                    switchSegment(
                        label = effectiveOffLabel,
                        active = !checked,
                        theme = theme,
                    ),
                    switchSegment(
                        label = effectiveOnLabel,
                        active = checked,
                        theme = theme,
                    ),
                ),
            ),
        ),
    )
}

private fun switchSegment(
    label: String,
    active: Boolean,
    theme: LauncherTheme,
): Widget = Container(
    fillColor = if (active) theme.button.pressedFill else PixelColor.Transparent,
    padding = EdgeInsets.symmetric(
        horizontal = SETTINGS_SWITCH_LABEL_HORIZONTAL_PADDING_PX,
        vertical = SETTINGS_SWITCH_LABEL_VERTICAL_PADDING_PX,
    ),
    alignment = Alignment.CENTER,
    child = Text(
        label,
        style = TextStyle(color = if (active) theme.button.text else theme.button.disabledText),
        overflow = TextOverflow.CLIP,
    ),
)
