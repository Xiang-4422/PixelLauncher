package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.ListView
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.SegmentedControlWidthPolicy
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixellauncherv2.launcher.ChargeIdleEffect
import com.purride.pixellauncherv2.launcher.DrawerListAlignment
import com.purride.pixellauncherv2.launcher.IdleSettings
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.launcher.PixelFontCatalog
import com.purride.pixellauncherv2.launcher.SettingsListGeometry
import com.purride.pixellauncherv2.launcher.SettingsMenuItem
import com.purride.pixellauncherv2.launcher.SettingsMenuModel
import com.purride.pixellauncherv2.launcher.SettingsSection
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.widget.SettingsActionRow
import com.purride.pixellauncherv2.ui.widget.SettingsChoiceRow
import com.purride.pixellauncherv2.ui.widget.SettingsOptionStepperRow
import com.purride.pixellauncherv2.ui.widget.SettingsPixelSizeControl
import com.purride.pixellauncherv2.ui.widget.SettingsSectionHeader
import com.purride.pixellauncherv2.ui.widget.SettingsSwitchRow
import com.purride.pixellauncherv2.ui.widget.SettingsTextEdgeResolvers
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * 重写后的设置页（pixel-engine 渲染）。
 *
 * 交互规则：
 * - 三项以内的枚举直接点击分段选择，明确布尔值继续使用开关语义
 * - 超过三项的枚举暂时保留标题向前、数值向后的步进交互
 * - THEME、FONT 与 PIXEL 使用各自保留的专用控件
 *
 * @param uiState       当前 UI 状态快照（读取设置值 + 已选索引）
 * @param theme         当前颜色主题
 * @param textEdgeResolvers 设置页左右两列真实字形墨迹边界解析器
 * @param onItemAction  设置项动作回调：(item, direction) → 更新 ViewModel
 */
class SettingsScreen(
    private val uiState: LauncherUiState,
    private val theme: LauncherTheme,
    private val textEdgeResolvers: SettingsTextEdgeResolvers,
    private val onItemAction: (SettingsMenuItem, Int) -> Unit,
    override val key: Any? = null,
) : StatefulWidget(key = key) {

    override fun createState(): State<out StatefulWidget> = SettingsState()

    private inner class SettingsState : State<SettingsScreen>() {

        private val listController = PixelListController()
        private val listState: PixelListState = listController.create()

        override fun build(context: BuildContext): Widget {
            val items = widget.uiState.toSettingsWidgets(widget.theme)

            return Column(
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                mainAxisSize = MainAxisSize.MAX,
                spacing = 0,
                children = listOf(
                    Expanded(
                        child = Padding(
                            horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
                            vertical = LauncherSpacing.CONTENT_VERTICAL,
                            child = ListView(
                                items = items,
                                state = listState,
                                controller = listController,
                                spacing = SettingsListGeometry.ROW_SPACING_PX,
                            ),
                        ),
                    ),
                ),
            )
        }

        private fun LauncherUiState.toSettingsWidgets(t: LauncherTheme): List<Widget> = buildList {
            addSection(SettingsSection.DISPLAY, t)
            add(
                SettingsPixelSizeControl(
                    title = "PIXEL",
                    valueLabel = SettingsMenuModel.resolutionLabel(selectedDotSizePx),
                    theme = t,
                    textEdgeResolvers = widget.textEdgeResolvers,
                    onDecrease = { widget.onItemAction(SettingsMenuItem.RESOLUTION, -1) },
                    onIncrease = { widget.onItemAction(SettingsMenuItem.RESOLUTION, +1) },
                ),
            )
            add(
                SettingsSwitchRow(
                    title = "GAP",
                    checked = isPixelGapEnabled,
                    theme = t,
                    textEdgeResolvers = widget.textEdgeResolvers,
                    showLabels = true,
                    onToggle = { widget.onItemAction(SettingsMenuItem.PIXEL_GAP, +1) },
                ),
            )
            if (isPixelGapEnabled) {
                add(
                    SettingsChoiceRow(
                        title = "STYLE",
                        labels = SettingsMenuModel.styleOptions.map(SettingsMenuModel::styleLabel),
                        selectedIndex = SettingsMenuModel.styleOptions.indexOf(selectedPixelShape).coerceAtLeast(0),
                        theme = t,
                        textEdgeResolvers = widget.textEdgeResolvers,
                        widthPolicy = SegmentedControlWidthPolicy.Content,
                        onSelected = { selectedIndex ->
                            /** 将直接选择的目标下标转换为现有设置动作协议使用的相对方向。 */
                            dispatchSelection(
                                item = SettingsMenuItem.STYLE,
                                options = SettingsMenuModel.styleOptions,
                                current = selectedPixelShape,
                                selectedIndex = selectedIndex,
                            )
                        },
                    ),
                )
            }
            addSection(SettingsSection.THEME, t)
            add(
                SettingsOptionStepperRow(
                    title = "THEME",
                    valueLabel = SettingsMenuModel.themeFamilyLabel(selectedThemeFamily),
                    theme = t,
                    textEdgeResolvers = widget.textEdgeResolvers,
                    onPrevious = { widget.onItemAction(SettingsMenuItem.THEME, -1) },
                    onNext = { widget.onItemAction(SettingsMenuItem.THEME, +1) },
                ),
            )
            add(
                SettingsChoiceRow(
                    title = "MODE",
                    labels = SettingsMenuModel.themeModeOptions.map(SettingsMenuModel::themeModeLabel),
                    selectedIndex = SettingsMenuModel.themeModeOptions.indexOf(selectedThemeMode).coerceAtLeast(0),
                    theme = t,
                    textEdgeResolvers = widget.textEdgeResolvers,
                    widthPolicy = SegmentedControlWidthPolicy.EqualToWidest,
                    onSelected = { selectedIndex ->
                        val currentIndex = SettingsMenuModel.themeModeOptions.indexOf(selectedThemeMode)
                        if (currentIndex >= 0 && selectedIndex != currentIndex) {
                            widget.onItemAction(SettingsMenuItem.THEME_MODE, selectedIndex - currentIndex)
                        }
                    },
                ),
            )
            add(
                SettingsOptionStepperRow(
                    title = "FONT",
                    valueLabel = SettingsMenuModel.fontLabel(fontSelection.family),
                    theme = t,
                    onPrevious = { widget.onItemAction(SettingsMenuItem.FONT, -1) },
                    onNext = { widget.onItemAction(SettingsMenuItem.FONT, +1) },
                    enabled = !isFontLoading && PixelFontCatalog.fontFamilyOptions().size > 1,
                ),
            )
            /** 当前字体家族允许用户直接选择的宽度模式，最多展示三个分段。 */
            val widthOptions = PixelFontCatalog.widthModeOptions(fontSelection.family)
            add(
                SettingsChoiceRow(
                    title = "WIDTH",
                    labels = widthOptions.map(SettingsMenuModel::fontWidthLabel),
                    selectedIndex = widthOptions.indexOf(fontSelection.widthMode).coerceAtLeast(0),
                    theme = t,
                    widthPolicy = SegmentedControlWidthPolicy.EqualToWidest,
                    enabled = !isFontLoading && widthOptions.size > 1,
                    onSelected = { selectedIndex ->
                        /** 字体加载期间控件禁用，正常状态下按目标下标直接切换宽度。 */
                        dispatchSelection(
                            item = SettingsMenuItem.FONT_WIDTH,
                            options = widthOptions,
                            current = fontSelection.widthMode,
                            selectedIndex = selectedIndex,
                        )
                    },
                ),
            )
            /** 当前字体与宽度组合允许用户直接选择的字号，目录约束为最多三个。 */
            val sizeOptions = PixelFontCatalog.fontSizeOptions(fontSelection.family, fontSelection.widthMode)
            add(
                SettingsChoiceRow(
                    title = "SIZE",
                    labels = sizeOptions.map(SettingsMenuModel::fontSizeLabel),
                    selectedIndex = sizeOptions.indexOf(fontSelection.size).coerceAtLeast(0),
                    theme = t,
                    widthPolicy = SegmentedControlWidthPolicy.EqualToWidest,
                    enabled = !isFontLoading && sizeOptions.size > 1,
                    onSelected = { selectedIndex ->
                        /** 通过目标字号下标复用既有方向动作，避免新增状态写入通道。 */
                        dispatchSelection(
                            item = SettingsMenuItem.FONT_SIZE,
                            options = sizeOptions,
                            current = fontSelection.size,
                            selectedIndex = selectedIndex,
                        )
                    },
                ),
            )
            addSection(SettingsSection.DRAWER, t)
            add(
                SettingsChoiceRow(
                    title = "ALIGN",
                    labels = DrawerListAlignment.entries.map(
                        SettingsMenuModel::drawerListAlignmentLabel,
                    ),
                    selectedIndex = DrawerListAlignment.entries
                        .indexOf(drawerListAlignment)
                        .coerceAtLeast(0),
                    theme = t,
                    textEdgeResolvers = widget.textEdgeResolvers,
                    widthPolicy = SegmentedControlWidthPolicy.Content,
                    onSelected = { selectedIndex ->
                        /** 抽屉对齐方式固定为三项，允许直接点击目标位置。 */
                        dispatchSelection(
                            item = SettingsMenuItem.APP_LIST_ALIGNMENT,
                            options = DrawerListAlignment.entries,
                            current = drawerListAlignment,
                            selectedIndex = selectedIndex,
                        )
                    },
                ),
            )
            add(
                SettingsSwitchRow(
                    title = "SEARCH",
                    checked = openDrawerInSearchMode,
                    theme = t,
                    textEdgeResolvers = widget.textEdgeResolvers,
                    showLabels = true,
                    onToggle = { widget.onItemAction(SettingsMenuItem.DRAWER_AUTO_SEARCH, +1) },
                ),
            )
            addSection(SettingsSection.IDLE, t)
            add(
                SettingsSwitchRow(
                    title = "ENABLE",
                    checked = isIdlePageEnabled,
                    theme = t,
                    textEdgeResolvers = widget.textEdgeResolvers,
                    showLabels = true,
                    onToggle = { widget.onItemAction(SettingsMenuItem.IDLE_PAGE, +1) },
                ),
            )
            if (isIdlePageEnabled) {
                add(
                    SettingsSwitchRow(
                        title = "CHARGE",
                        checked = chargeAutoIdleEnabled,
                        theme = t,
                        textEdgeResolvers = widget.textEdgeResolvers,
                        showLabels = true,
                        onToggle = { widget.onItemAction(SettingsMenuItem.CHARGE_AUTO_IDLE, +1) },
                    ),
                )
                add(
                    SettingsSwitchRow(
                        title = "AUTO",
                        checked = inactivityAutoIdleEnabled,
                        theme = t,
                        textEdgeResolvers = widget.textEdgeResolvers,
                        showLabels = true,
                        onToggle = { widget.onItemAction(SettingsMenuItem.INACTIVITY_AUTO_IDLE, +1) },
                    ),
                )
                if (inactivityAutoIdleEnabled) {
                    add(
                        SettingsChoiceRow(
                            title = "TIMEOUT",
                            labels = IdleSettings.timeoutOptionsSeconds.map(SettingsMenuModel::idleTimeoutLabel),
                            selectedIndex = IdleSettings.timeoutOptionsSeconds.indexOf(idleTimeoutSeconds).coerceAtLeast(0),
                            theme = t,
                            textEdgeResolvers = widget.textEdgeResolvers,
                            onSelected = { selectedIndex ->
                                /** 四档超时继续显示步进器，但共享统一的受控候选项协议。 */
                                dispatchSelection(
                                    item = SettingsMenuItem.IDLE_TIMEOUT,
                                    options = IdleSettings.timeoutOptionsSeconds,
                                    current = idleTimeoutSeconds,
                                    selectedIndex = selectedIndex,
                                )
                            },
                        ),
                    )
                }
                add(
                    SettingsChoiceRow(
                        title = "EFFECT",
                        labels = ChargeIdleEffect.entries.map(SettingsMenuModel::chargeIdleEffectLabel),
                        selectedIndex = ChargeIdleEffect.entries.indexOf(chargeIdleEffect).coerceAtLeast(0),
                        theme = t,
                        textEdgeResolvers = widget.textEdgeResolvers,
                        onSelected = { selectedIndex ->
                            /** 六种效果等待专用组件，当前由统一入口回退为步进器。 */
                            dispatchSelection(
                                item = SettingsMenuItem.CHARGE_IDLE_EFFECT,
                                options = ChargeIdleEffect.entries,
                                current = chargeIdleEffect,
                                selectedIndex = selectedIndex,
                            )
                        },
                    ),
                )
            }
            addSection(SettingsSection.MORE, t)
            add(
                SettingsActionRow(
                    title = "MORE",
                    valueLabel = "OPEN",
                    theme = t,
                    textEdgeResolvers = widget.textEdgeResolvers,
                    onPressed = { widget.onItemAction(SettingsMenuItem.MORE, +1) },
                ),
            )
        }

        private fun MutableList<Widget>.addSection(section: SettingsSection, theme: LauncherTheme) {
            val topMargin = if (isEmpty()) 0 else LauncherSpacing.SETTINGS_SECTION_GAP
            add(
                SettingsSectionHeader(
                    title = SettingsMenuModel.sectionLabel(section),
                    theme = theme,
                    textEdgeResolvers = widget.textEdgeResolvers,
                    topMargin = topMargin,
                ),
            )
        }

        /**
         * 将分段控件的绝对目标下标转换为现有设置分发器接受的相对方向。
         *
         * 相同选项保持幂等；无效状态或越界目标不会触发设置写入。
         */
        private fun <T> dispatchSelection(
            item: SettingsMenuItem,
            options: List<T>,
            current: T,
            selectedIndex: Int,
        ) {
            /** 当前值在受控选项列表中的位置。 */
            val currentIndex = options.indexOf(current)
            if (currentIndex >= 0 && selectedIndex in options.indices && selectedIndex != currentIndex) {
                widget.onItemAction(item, selectedIndex - currentIndex)
            }
        }

    }

}
