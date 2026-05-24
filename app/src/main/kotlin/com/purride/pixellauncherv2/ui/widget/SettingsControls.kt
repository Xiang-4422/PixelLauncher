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

private fun SettingsSwitch(
    checked: Boolean,
    theme: LauncherTheme,
    showLabels: Boolean,
    offLabel: String,
    onLabel: String,
    onToggle: () -> Unit,
): Widget {
    val width = if (showLabels) 34 else 18
    return GestureDetector(
        onTap = onToggle,
        child = SizedBox(
            width = width,
            height = 11,
            child = Container(
                borderColor = theme.button.border,
                padding = EdgeInsets.all(1),
                child = Row(
                    spacing = 1,
                    children = listOf(
                        switchSegment(
                            label = if (showLabels) offLabel else "",
                            active = !checked,
                            theme = theme,
                        ),
                        switchSegment(
                            label = if (showLabels) onLabel else "",
                            active = checked,
                            theme = theme,
                        ),
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
): Widget = Expanded(
    child = Container(
        fillColor = if (active) theme.button.pressedFill else PixelColor.Transparent,
        alignment = Alignment.CENTER,
        child = Text(
            label,
            style = TextStyle(color = if (active) theme.button.text else theme.button.disabledText),
            overflow = TextOverflow.CLIP,
        ),
    ),
)
