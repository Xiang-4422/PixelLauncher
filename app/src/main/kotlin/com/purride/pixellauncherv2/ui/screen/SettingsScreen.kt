package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.ListView
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixellauncherv2.launcher.DataHealthModel
import com.purride.pixellauncherv2.launcher.HomeInfoModel
import com.purride.pixellauncherv2.launcher.SettingsListGeometry
import com.purride.pixellauncherv2.launcher.SettingsMenuItem
import com.purride.pixellauncherv2.launcher.SettingsMenuModel
import com.purride.pixellauncherv2.launcher.SettingsSection
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.widget.SettingsActionRow
import com.purride.pixellauncherv2.ui.widget.SettingsOptionStepperRow
import com.purride.pixellauncherv2.ui.widget.SettingsSectionHeader
import com.purride.pixellauncherv2.ui.widget.SettingsSwitchRow
import com.purride.pixellauncherv2.ui.widget.SettingsValueSlider
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * 重写后的设置页（pixel-engine 渲染）。
 *
 * 交互规则（Phase 3）：
 * - 点击行左侧（标题区域）→ 上一个选项（direction = -1）
 * - 点击行右侧（数值区域）→ 下一个选项（direction = +1）
 * - D-Pad 路由留待 Phase 3b 补全
 *
 * @param uiState       当前 UI 状态快照（读取设置值 + 已选索引）
 * @param theme         当前颜色主题
 * @param onItemAction  设置项动作回调：(item, direction) → 更新 ViewModel
 */
class SettingsScreen(
    private val uiState: LauncherUiState,
    private val theme: LauncherTheme,
    private val onItemAction: (SettingsMenuItem, Int) -> Unit,
    private val onItemRatioChanged: (SettingsMenuItem, Float) -> Unit,
    private val onPreviewChanged: () -> Unit,
    override val key: Any? = null,
) : StatefulWidget(key = key) {

    override fun createState(): State<out StatefulWidget> = SettingsState()

    private inner class SettingsState : State<SettingsScreen>() {

        private val listController = PixelListController()
        private val listState: PixelListState = listController.create()
        private var previewPixelSizeRatio: Float? = null
        private var previewGapRatio: Float? = null

        override fun build(context: BuildContext): Widget {
            val items = widget.uiState.toSettingsWidgets(widget.theme)

            return Column(
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                mainAxisSize = MainAxisSize.MAX,
                spacing = 0,
                children = listOf(
                    Expanded(
                        child = ListView(
                            items = items,
                            state = listState,
                            controller = listController,
                            spacing = SettingsListGeometry.ROW_SPACING_PX,
                        ),
                    ),
                ),
            )
        }

        private fun LauncherUiState.toSettingsWidgets(t: LauncherTheme): List<Widget> = buildList {
            val pixelPreview = previewPixelSizeRatio ?: SettingsMenuModel.resolutionRatio(selectedDotSizePx)
            val pixelPreviewSize = SettingsMenuModel.resolutionAtRatio(pixelPreview)
            val gapPreview = previewGapRatio ?: SettingsMenuModel.pixelGapRatioSnap(pixelGapRatio)
            addSection(SettingsSection.DISPLAY, t)
            add(
                SettingsValueSlider(
                    title = "PIXEL",
                    valueLabel = "${pixelPreviewSize}PX",
                    value = pixelPreview,
                    theme = t,
                    onStepDown = { widget.onItemAction(SettingsMenuItem.RESOLUTION, -1) },
                    onStepUp = { widget.onItemAction(SettingsMenuItem.RESOLUTION, +1) },
                    onValuePreview = { ratio -> updatePixelSizePreview(ratio) },
                    onValueChanged = { ratio ->
                        val snapped = SettingsMenuModel.resolutionRatio(SettingsMenuModel.resolutionAtRatio(ratio))
                        previewPixelSizeRatio = null
                        widget.onItemRatioChanged(SettingsMenuItem.RESOLUTION, snapped)
                    },
                ),
            )
            add(
                SettingsValueSlider(
                    title = "GAP",
                    valueLabel = SettingsMenuModel.pixelGapSizeLabel(gapPreview),
                    value = gapPreview,
                    theme = t,
                    live = true,
                    onStepDown = { widget.onItemAction(SettingsMenuItem.PIXEL_GAP_SIZE, -1) },
                    onStepUp = { widget.onItemAction(SettingsMenuItem.PIXEL_GAP_SIZE, +1) },
                    onValueChanged = { ratio -> updateGapPreviewAndCommit(ratio) },
                ),
            )
            add(
                SettingsOptionStepperRow(
                    title = "STYLE",
                    valueLabel = SettingsMenuModel.styleLabel(selectedPixelShape),
                    theme = t,
                    onPrevious = { widget.onItemAction(SettingsMenuItem.STYLE, -1) },
                    onNext = { widget.onItemAction(SettingsMenuItem.STYLE, +1) },
                ),
            )
            add(
                SettingsOptionStepperRow(
                    title = "THEME",
                    valueLabel = SettingsMenuModel.themeLabel(selectedTheme),
                    theme = t,
                    onPrevious = { widget.onItemAction(SettingsMenuItem.THEME, -1) },
                    onNext = { widget.onItemAction(SettingsMenuItem.THEME, +1) },
                ),
            )
            addSection(SettingsSection.HOME, t)
            add(
                SettingsActionRow(
                    title = "STATUS",
                    valueLabel = HomeInfoModel.summary(this@toSettingsWidgets),
                    theme = t,
                    onPressed = { widget.onItemAction(SettingsMenuItem.HOME_STATUS, +1) },
                ),
            )
            addSection(SettingsSection.DRAWER, t)
            add(
                SettingsOptionStepperRow(
                    title = "ALIGN",
                    valueLabel = SettingsMenuModel.drawerListAlignmentLabel(drawerListAlignment),
                    theme = t,
                    onPrevious = { widget.onItemAction(SettingsMenuItem.APP_LIST_ALIGNMENT, -1) },
                    onNext = { widget.onItemAction(SettingsMenuItem.APP_LIST_ALIGNMENT, +1) },
                ),
            )
            add(
                SettingsSwitchRow(
                    title = "SEARCH",
                    checked = openDrawerInSearchMode,
                    theme = t,
                    showLabels = true,
                    onToggle = { widget.onItemAction(SettingsMenuItem.DRAWER_AUTO_SEARCH, +1) },
                ),
            )
            add(
                SettingsActionRow(
                    title = "APPS",
                    valueLabel = if (apps.isEmpty()) "EMPTY" else "OPEN",
                    theme = t,
                    onPressed = { widget.onItemAction(SettingsMenuItem.APP_MANAGEMENT, +1) },
                ),
            )
            addSection(SettingsSection.IDLE, t)
            add(
                SettingsSwitchRow(
                    title = "IDLE",
                    checked = isIdlePageEnabled,
                    theme = t,
                    showLabels = true,
                    onToggle = { widget.onItemAction(SettingsMenuItem.IDLE_PAGE, +1) },
                ),
            )
            add(
                SettingsSwitchRow(
                    title = "CHARGE",
                    checked = chargeAutoIdleEnabled,
                    theme = t,
                    showLabels = true,
                    onToggle = { widget.onItemAction(SettingsMenuItem.CHARGE_AUTO_IDLE, +1) },
                ),
            )
            add(
                SettingsSwitchRow(
                    title = "AUTO",
                    checked = inactivityAutoIdleEnabled,
                    theme = t,
                    showLabels = true,
                    onToggle = { widget.onItemAction(SettingsMenuItem.INACTIVITY_AUTO_IDLE, +1) },
                ),
            )
            add(
                SettingsOptionStepperRow(
                    title = "TIMEOUT",
                    valueLabel = SettingsMenuModel.idleTimeoutLabel(idleTimeoutSeconds),
                    theme = t,
                    onPrevious = { widget.onItemAction(SettingsMenuItem.IDLE_TIMEOUT, -1) },
                    onNext = { widget.onItemAction(SettingsMenuItem.IDLE_TIMEOUT, +1) },
                ),
            )
            add(
                SettingsOptionStepperRow(
                    title = "EFFECT",
                    valueLabel = SettingsMenuModel.chargeIdleEffectLabel(chargeIdleEffect),
                    theme = t,
                    onPrevious = { widget.onItemAction(SettingsMenuItem.CHARGE_IDLE_EFFECT, -1) },
                    onNext = { widget.onItemAction(SettingsMenuItem.CHARGE_IDLE_EFFECT, +1) },
                ),
            )
            addSection(SettingsSection.DATA, t)
            add(
                SettingsActionRow(
                    title = "DATA",
                    valueLabel = DataHealthModel.summary(this@toSettingsWidgets),
                    theme = t,
                    onPressed = { widget.onItemAction(SettingsMenuItem.DATA_HEALTH, +1) },
                ),
            )
            addSection(SettingsSection.ADVANCED, t)
            add(
                SettingsActionRow(
                    title = "ADVANCED",
                    valueLabel = "OPEN",
                    theme = t,
                    onPressed = { widget.onItemAction(SettingsMenuItem.ADVANCED, +1) },
                ),
            )
        }

        private fun MutableList<Widget>.addSection(section: SettingsSection, theme: LauncherTheme) {
            add(
                SettingsSectionHeader(
                    title = SettingsMenuModel.sectionLabel(section),
                    theme = theme,
                ),
            )
        }

        private fun updatePixelSizePreview(ratio: Float) {
            val snapped = SettingsMenuModel.resolutionRatio(SettingsMenuModel.resolutionAtRatio(ratio))
            updatePreview(
                current = previewPixelSizeRatio,
                next = snapped,
                assign = { previewPixelSizeRatio = it },
            )
        }

        private fun updateGapPreviewAndCommit(ratio: Float) {
            val snapped = SettingsMenuModel.pixelGapRatioSnap(ratio)
            updatePreview(
                current = previewGapRatio,
                next = snapped,
                assign = { previewGapRatio = it },
            )
            widget.onItemRatioChanged(SettingsMenuItem.PIXEL_GAP_SIZE, snapped)
        }

        private fun updatePreview(
            current: Float?,
            next: Float,
            assign: (Float) -> Unit,
        ) {
            if (current != next) {
                assign(next)
                widget.onPreviewChanged()
                setState { }
            }
        }
    }
}
