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
import com.purride.pixelui.Slider
import com.purride.pixelui.Text
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixellauncherv2.ui.theme.LauncherTheme

private const val SETTINGS_SWITCH_PADDING_PX = 2
private const val SETTINGS_SWITCH_SEGMENT_GAP_PX = 1
private const val SETTINGS_SWITCH_LABEL_HORIZONTAL_PADDING_PX = 2
private const val SETTINGS_SWITCH_LABEL_VERTICAL_PADDING_PX = 2
private const val SETTINGS_ROW_HORIZONTAL_PADDING_PX = 2
private const val SETTINGS_ROW_VERTICAL_PADDING_PX = 2
private const val SETTINGS_LABEL_VERTICAL_PADDING_PX = 2

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
    padding = settingsRowPadding(),
    child = Column(
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        spacing = 1,
        children = listOf(
            settingsInlineRow(
                title = GestureDetector(
                    onTap = onStepDown,
                    child = settingsTitleCell(title = title, theme = theme),
                ),
                trailing = GestureDetector(
                    onTap = onStepUp,
                    child = settingsValueCell(valueLabel = valueLabel, theme = theme),
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
    padding = settingsRowPadding(),
    child = settingsInlineRow(
        title = GestureDetector(
            onTap = onToggle,
            child = settingsTitleCell(title = title, theme = theme),
        ),
        trailing = SettingsSwitch(
            checked = checked,
            theme = theme,
            showLabels = showLabels,
            offLabel = offLabel,
            onLabel = onLabel,
            onToggle = onToggle,
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
    padding = settingsRowPadding(),
    child = settingsInlineRow(
        title = GestureDetector(
            onTap = onPrevious,
            child = settingsTitleCell(title = title, theme = theme),
        ),
        trailing = GestureDetector(
            onTap = onNext,
            child = settingsValueCell(valueLabel = valueLabel, theme = theme),
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
        padding = settingsRowPadding(),
        child = settingsInlineRow(
            title = settingsTitleCell(title = title, theme = theme),
            trailing = settingsValueCell(valueLabel = valueLabel, theme = theme),
        ),
    ),
)

fun SettingsSectionHeader(
    title: String,
    theme: LauncherTheme,
): Widget = Container(
    padding = settingsRowPadding(),
    child = Text(
        title,
        style = TextStyle(color = theme.text.muted),
        overflow = TextOverflow.ELLIPSIS,
        softWrap = false,
        maxLines = 1,
    ),
)

private fun settingsInlineRow(
    title: Widget,
    trailing: Widget,
): Widget = Row(
    mainAxisSize = MainAxisSize.MAX,
    crossAxisAlignment = CrossAxisAlignment.CENTER,
    spacing = 2,
    children = listOf(
        Expanded(child = settingsRowCell(title, Alignment.CENTER_START)),
        Expanded(child = settingsRowCell(trailing, Alignment.CENTER_END)),
    ),
)

private fun settingsRowCell(
    child: Widget,
    alignment: Alignment,
): Widget = Container(
    alignment = alignment,
    child = child,
)

private fun settingsTitleCell(
    title: String,
    theme: LauncherTheme,
): Widget = Container(
    padding = EdgeInsets.symmetric(vertical = SETTINGS_LABEL_VERTICAL_PADDING_PX),
    alignment = Alignment.CENTER_START,
    child = Text(
        title,
        style = TextStyle(color = theme.settings.itemTitle),
        overflow = TextOverflow.ELLIPSIS,
        softWrap = false,
        maxLines = 1,
    ),
)

private fun settingsValueCell(
    valueLabel: String,
    theme: LauncherTheme,
): Widget = Container(
    padding = EdgeInsets.symmetric(vertical = SETTINGS_LABEL_VERTICAL_PADDING_PX),
    alignment = Alignment.CENTER_END,
    child = Text(
        valueLabel,
        style = TextStyle(color = theme.settings.itemValue),
        overflow = TextOverflow.ELLIPSIS,
        softWrap = false,
        maxLines = 1,
    ),
)

private fun settingsRowPadding(): EdgeInsets = EdgeInsets.symmetric(
    horizontal = SETTINGS_ROW_HORIZONTAL_PADDING_PX,
    vertical = SETTINGS_ROW_VERTICAL_PADDING_PX,
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
        style = TextStyle(
            color = if (active) theme.button.text else theme.button.disabledText,
        ),
        overflow = TextOverflow.ELLIPSIS,
        softWrap = false,
        maxLines = 1,
    ),
)
