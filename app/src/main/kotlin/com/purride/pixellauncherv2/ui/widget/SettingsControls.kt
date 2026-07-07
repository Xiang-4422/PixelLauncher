package com.purride.pixellauncherv2.ui.widget

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Row
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.Text
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.ValueAdjuster
import com.purride.pixelui.ValueAdjusterStyle
import com.purride.pixelui.Widget
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.ui.theme.LauncherTheme

/** Switch 的 OFF/ON 分段之间保留一条像素缝，不属于页面行间距。 */
private const val SETTINGS_SWITCH_SEGMENT_GAP_PX = 1

/** 设置行标题列与值列的内部水平间距，不用于相邻设置行。 */
private const val SETTINGS_INLINE_COLUMN_GAP_PX = 2

fun SettingsSegmentedControlRow(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    theme: LauncherTheme,
    onSelected: (Int) -> Unit,
): Widget {
    return Container(
        child = settingsInlineRow(
            title = settingsTitleCell(title = title, theme = theme),
            trailing = SegmentedControl(
                labels = labels,
                selectedIndex = selectedIndex,
                onSelected = onSelected,
            ),
            titleFlex = 1,
            trailingFlex = 3,
        ),
    )
}

fun SettingsPixelSizeControl(
    title: String,
    valueLabel: String,
    theme: LauncherTheme,
    onDecrease: (() -> Unit)?,
    onIncrease: (() -> Unit)?,
    key: Any? = null,
): Widget {
    return Container(
        child = settingsInlineRow(
            title = settingsTitleCell(title = title, theme = theme),
            trailing = ValueAdjuster(
                valueText = valueLabel,
                onDecrease = onDecrease,
                onIncrease = onIncrease,
                valueWidth = 34,
                style = settingsValueAdjusterStyle(theme),
                key = key,
            ),
        ),
    )
}

fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    theme: LauncherTheme,
    showLabels: Boolean = true,
    offLabel: String = "OFF",
    onLabel: String = "ON",
    onToggle: () -> Unit,
): Widget = Container(
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
    key: Any? = null,
): Widget = GestureDetector(
    onTap = onPressed,
    key = key,
    child = Container(
        child = settingsInlineRow(
            title = settingsTitleCell(title = title, theme = theme),
            trailing = settingsValueCell(valueLabel = valueLabel, theme = theme),
        ),
    ),
)

fun SettingsSectionHeader(
    title: String,
    theme: LauncherTheme,
    topMargin: Int = 0,
): Widget = Container(
    margin = EdgeInsets.only(top = topMargin),
    fillColor = theme.button.border,
    padding = EdgeInsets.symmetric(
        horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
        vertical = LauncherSpacing.ROW_SPACING,
    ),
    alignment = Alignment.CENTER_START,
    child = Text(
        title,
        style = TextStyle(color = theme.surface.offPixelColor),
        overflow = TextOverflow.ELLIPSIS,
        softWrap = false,
        maxLines = 1,
    ),
)

private fun settingsInlineRow(
    title: Widget,
    trailing: Widget,
    titleFlex: Int = 1,
    trailingFlex: Int = 1,
): Widget = Row(
    mainAxisSize = MainAxisSize.MAX,
    crossAxisAlignment = CrossAxisAlignment.CENTER,
    spacing = SETTINGS_INLINE_COLUMN_GAP_PX,
    children = listOf(
        Expanded(flex = titleFlex, child = settingsRowCell(title, Alignment.CENTER_START)),
        Expanded(flex = trailingFlex, child = settingsRowCell(trailing, Alignment.CENTER_END)),
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
    alignment = Alignment.CENTER_END,
    child = Text(
        valueLabel,
        style = TextStyle(color = theme.settings.itemValue),
        overflow = TextOverflow.ELLIPSIS,
        softWrap = false,
        maxLines = 1,
    ),
)

private fun settingsValueAdjusterStyle(theme: LauncherTheme): ValueAdjusterStyle = ValueAdjusterStyle(
    borderColor = theme.button.border,
    buttonFillColor = theme.button.border,
    buttonSymbolColor = theme.surface.offPixelColor,
    valueTextColor = theme.settings.itemValue,
    disabledColor = theme.button.disabledText,
    focusColor = theme.button.border,
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
        horizontal = LauncherSpacing.BORDERED_CONTROL_INSET,
        vertical = LauncherSpacing.BORDERED_CONTROL_INSET,
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
