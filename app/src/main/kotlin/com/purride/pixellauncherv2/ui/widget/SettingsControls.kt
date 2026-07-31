package com.purride.pixellauncherv2.ui.widget

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsActions
import com.purride.pixelui.Row
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.SegmentedControlStyle
import com.purride.pixelui.SegmentedControlWidthPolicy
import com.purride.pixelui.Semantics
import com.purride.pixelui.Text
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.ValueAdjuster
import com.purride.pixelui.ValueAdjusterStyle
import com.purride.pixelui.Widget
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.text.opticallyAlignEndText
import com.purride.pixellauncherv2.ui.text.opticallyAlignStartText

/** SETTINGS 页面左右两列文字使用的真实字形墨迹边界解析器。 */
data class SettingsTextEdgeResolvers(
    /** 返回左列文字首字形的左侧空白像素数。 */
    val leadingInkInset: (String) -> Int,
    /** 返回右列文字末字形的右侧空白像素数。 */
    val trailingInkInset: (String) -> Int,
) {
    /** 集中提供无需光学补偿的默认解析器。 */
    companion object {
        /** 非 SETTINGS 页面或预览控件使用的零补偿配置。 */
        val None = SettingsTextEdgeResolvers(
            leadingInkInset = { 0 },
            trailingInkInset = { 0 },
        )
    }
}

/** Switch 的 OFF/ON 分段之间保留一条像素缝，不属于页面行间距。 */
private const val SETTINGS_SWITCH_SEGMENT_GAP_PX = 1

/** 设置行标题列与值列的内部水平间距，不用于相邻设置行。 */
private const val SETTINGS_INLINE_COLUMN_GAP_PX = 2

/** 行内滑动选择器允许直接展示的最大候选项数量。 */
private const val SETTINGS_INLINE_SELECTION_MAX_OPTIONS = 3

/**
 * 根据候选项数量选择设置枚举行：三项以内直接选择，更多项暂时回退步进器。
 *
 * 该入口集中约束设置页的组件选择；未来增加多候选项组件时只需替换超过三项的分支。
 */
fun SettingsChoiceRow(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    theme: LauncherTheme,
    textEdgeResolvers: SettingsTextEdgeResolvers = SettingsTextEdgeResolvers.None,
    widthPolicy: SegmentedControlWidthPolicy = SegmentedControlWidthPolicy.Content,
    enabled: Boolean = true,
    onSelected: (Int) -> Unit,
): Widget {
    require(labels.isNotEmpty()) { "SettingsChoiceRow labels must not be empty." }
    require(selectedIndex in labels.indices) { "SettingsChoiceRow selectedIndex must reference labels." }
    if (labels.size <= SETTINGS_INLINE_SELECTION_MAX_OPTIONS) {
        return SettingsSegmentedControlRow(
            title = title,
            labels = labels,
            selectedIndex = selectedIndex,
            theme = theme,
            textEdgeResolvers = textEdgeResolvers,
            widthPolicy = widthPolicy,
            enabled = enabled,
            onSelected = onSelected,
        )
    }
    return SettingsOptionStepperRow(
        title = title,
        valueLabel = labels[selectedIndex],
        theme = theme,
        textEdgeResolvers = textEdgeResolvers,
        onPrevious = { onSelected(wrappedOptionIndex(selectedIndex - 1, labels.size)) },
        onNext = { onSelected(wrappedOptionIndex(selectedIndex + 1, labels.size)) },
        enabled = enabled,
    )
}

/**
 * 构建设置页的通用多态单选行，并把每项宽度策略透传给底层分段选择器。
 *
 * @param widthPolicy 支持各项内容宽、按最长项等宽或调用方指定等宽。
 * @param enabled 是否允许点击、键盘和无障碍操作整组候选项。
 */
fun SettingsSegmentedControlRow(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    theme: LauncherTheme,
    textEdgeResolvers: SettingsTextEdgeResolvers = SettingsTextEdgeResolvers.None,
    widthPolicy: SegmentedControlWidthPolicy = SegmentedControlWidthPolicy.Content,
    enabled: Boolean = true,
    onSelected: (Int) -> Unit,
): Widget {
    return Container(
        child = settingsInlineRow(
            title = settingsTitleCell(
                title = title,
                theme = theme,
                textEdgeResolvers = textEdgeResolvers,
                enabled = enabled,
            ),
            trailing = SettingsSelection(
                title = title,
                labels = labels,
                selectedIndex = selectedIndex,
                theme = theme,
                widthPolicy = widthPolicy,
                showLabels = true,
                enabled = enabled,
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
    textEdgeResolvers: SettingsTextEdgeResolvers = SettingsTextEdgeResolvers.None,
    onDecrease: (() -> Unit)?,
    onIncrease: (() -> Unit)?,
    key: Any? = null,
): Widget {
    return Container(
        child = settingsInlineRow(
            title = settingsTitleCell(title = title, theme = theme, textEdgeResolvers = textEdgeResolvers),
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
    textEdgeResolvers: SettingsTextEdgeResolvers = SettingsTextEdgeResolvers.None,
    showLabels: Boolean = true,
    offLabel: String = "OFF",
    onLabel: String = "ON",
    onToggle: () -> Unit,
): Widget = Container(
    child = settingsInlineRow(
        title = GestureDetector(
            onTap = onToggle,
            child = settingsTitleCell(title = title, theme = theme, textEdgeResolvers = textEdgeResolvers),
        ),
        trailing = SettingsSwitch(
            title = title,
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
    textEdgeResolvers: SettingsTextEdgeResolvers = SettingsTextEdgeResolvers.None,
    onToggle: () -> Unit,
): Widget = SettingsSwitchRow(
    title = title,
    checked = rightSelected,
    theme = theme,
    textEdgeResolvers = textEdgeResolvers,
    showLabels = true,
    offLabel = leftLabel,
    onLabel = rightLabel,
    onToggle = onToggle,
)

fun SettingsOptionStepperRow(
    title: String,
    valueLabel: String,
    theme: LauncherTheme,
    textEdgeResolvers: SettingsTextEdgeResolvers = SettingsTextEdgeResolvers.None,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    enabled: Boolean = true,
): Widget = Container(
    child = settingsInlineRow(
        title = if (enabled) {
            GestureDetector(
                onTap = onPrevious,
                child = settingsTitleCell(title = title, theme = theme, textEdgeResolvers = textEdgeResolvers),
            )
        } else {
            settingsTitleCell(
                title = title,
                theme = theme,
                textEdgeResolvers = textEdgeResolvers,
                enabled = false,
            )
        },
        trailing = if (enabled) {
            GestureDetector(
                onTap = onNext,
                child = settingsValueCell(
                    valueLabel = valueLabel,
                    theme = theme,
                    textEdgeResolvers = textEdgeResolvers,
                ),
            )
        } else {
            settingsValueCell(
                valueLabel = valueLabel,
                theme = theme,
                textEdgeResolvers = textEdgeResolvers,
                enabled = false,
            )
        },
    ),
)

fun SettingsActionRow(
    title: String,
    valueLabel: String,
    theme: LauncherTheme,
    textEdgeResolvers: SettingsTextEdgeResolvers = SettingsTextEdgeResolvers.None,
    onPressed: () -> Unit,
    key: Any? = null,
): Widget = GestureDetector(
    onTap = onPressed,
    key = key,
    child = Container(
        child = settingsInlineRow(
            title = settingsTitleCell(title = title, theme = theme, textEdgeResolvers = textEdgeResolvers),
            trailing = settingsValueCell(
                valueLabel = valueLabel,
                theme = theme,
                textEdgeResolvers = textEdgeResolvers,
            ),
        ),
    ),
)

fun SettingsSectionHeader(
    title: String,
    theme: LauncherTheme,
    textEdgeResolvers: SettingsTextEdgeResolvers = SettingsTextEdgeResolvers.None,
    topMargin: Int = 0,
): Widget = Container(
    margin = EdgeInsets.only(top = topMargin),
    fillColor = theme.button.filledSurface,
    padding = EdgeInsets.symmetric(
        horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
        vertical = LauncherSpacing.ROW_SPACING,
    ),
    alignment = Alignment.CENTER_START,
    child = opticallyAlignStartText(
        text = title,
        resolveLeadingInkInset = textEdgeResolvers.leadingInkInset,
        child = Text(
            title,
            style = TextStyle(color = theme.button.filledText),
            overflow = TextOverflow.ELLIPSIS,
            softWrap = false,
            maxLines = 1,
        ),
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
    textEdgeResolvers: SettingsTextEdgeResolvers = SettingsTextEdgeResolvers.None,
    enabled: Boolean = true,
): Widget = Container(
    alignment = Alignment.CENTER_START,
    child = opticallyAlignStartText(
        text = title,
        resolveLeadingInkInset = textEdgeResolvers.leadingInkInset,
        child = Text(
            title,
            style = TextStyle(color = if (enabled) theme.settings.itemTitle else theme.button.disabledText),
            overflow = TextOverflow.ELLIPSIS,
            softWrap = false,
            maxLines = 1,
        ),
    ),
)

private fun settingsValueCell(
    valueLabel: String,
    theme: LauncherTheme,
    textEdgeResolvers: SettingsTextEdgeResolvers = SettingsTextEdgeResolvers.None,
    enabled: Boolean = true,
): Widget = Container(
    alignment = Alignment.CENTER_END,
    child = opticallyAlignEndText(
        text = valueLabel,
        resolveTrailingInkInset = textEdgeResolvers.trailingInkInset,
        child = Text(
            valueLabel,
            style = TextStyle(color = if (enabled) theme.settings.itemValue else theme.button.disabledText),
            overflow = TextOverflow.ELLIPSIS,
            softWrap = false,
            maxLines = 1,
        ),
    ),
)

private fun settingsValueAdjusterStyle(theme: LauncherTheme): ValueAdjusterStyle = ValueAdjusterStyle(
    borderColor = theme.button.border,
    buttonFillColor = theme.button.filledSurface,
    buttonSymbolColor = theme.button.filledText,
    valueTextColor = theme.settings.itemValue,
    disabledColor = theme.button.disabledText,
    focusColor = theme.button.border,
)

private fun SettingsSwitch(
    title: String,
    checked: Boolean,
    theme: LauncherTheme,
    showLabels: Boolean,
    offLabel: String,
    onLabel: String,
    onToggle: () -> Unit,
): Widget {
    /** 无标签模式保留旧控件的紧凑分段宽度，文字只用于内部稳定 identity。 */
    val widthPolicy = if (showLabels) {
        SegmentedControlWidthPolicy.EqualToWidest
    } else {
        SegmentedControlWidthPolicy.Fixed(
            width = LauncherSpacing.BORDERED_CONTROL_INSET * 2,
        )
    }
    /** 底层多态选择器；Switch 上层只负责布尔值与下标之间的映射。 */
    val selector = SettingsSelection(
        title = title,
        labels = listOf(offLabel, onLabel),
        selectedIndex = if (checked) 1 else 0,
        theme = theme,
        widthPolicy = widthPolicy,
        showLabels = showLabels,
        enabled = true,
        onSelected = { selectedIndex ->
            /** 重选当前项保持幂等，仅在目标布尔值改变时调用旧的 toggle 协议。 */
            val nextChecked = selectedIndex == 1
            if (nextChecked != checked) onToggle()
        },
    )
    /** 隐藏内部 Tab 语义，把二态封装重新导出为平台可识别的 Switch。 */
    return Semantics(
        label = title,
        role = PixelSemanticRole.SWITCH,
        checked = checked,
        excludeDescendants = true,
        actions = PixelSemanticsActions(
            onClick = {
                onToggle()
                true
            },
        ),
        child = selector,
        key = "$title-settings-switch-semantics",
    )
}

/**
 * Settings 所有单选控件共享的滑动高亮选择器。
 *
 * OFF/ON 由 [SettingsSwitch] 在上层封装成平台 Switch 语义；三态及更多选项直接保留
 * [SegmentedControl] 的 Tab 语义，但二者始终使用相同颜色、间距和移动动画。
 */
private fun SettingsSelection(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    theme: LauncherTheme,
    widthPolicy: SegmentedControlWidthPolicy,
    showLabels: Boolean,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
): Widget = SegmentedControl(
    labels = labels,
    selectedIndex = selectedIndex,
    onSelected = onSelected,
    widthPolicy = widthPolicy,
    style = SegmentedControlStyle(
        containerColor = PixelColor.Transparent,
        borderColor = theme.button.border,
        selectedFillColor = theme.button.border,
        selectedContentColor = if (showLabels) theme.button.selectedText else PixelColor.Transparent,
        unselectedContentColor = if (showLabels) theme.button.unselectedText else PixelColor.Transparent,
        disabledContentColor = theme.button.disabledText,
        padding = EdgeInsets.symmetric(
            horizontal = LauncherSpacing.BORDERED_CONTROL_INSET,
            vertical = LauncherSpacing.BORDERED_CONTROL_INSET,
        ),
        segmentSpacing = SETTINGS_SWITCH_SEGMENT_GAP_PX,
    ),
    key = "$title-settings-selection",
    enabled = enabled,
)

/** 将步进器越过首尾的目标位置循环映射回合法候选项下标。 */
private fun wrappedOptionIndex(index: Int, optionCount: Int): Int = ((index % optionCount) + optionCount) % optionCount
