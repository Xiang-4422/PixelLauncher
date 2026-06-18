package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixellauncherv2.launcher.SettingsListGeometry
import com.purride.pixellauncherv2.launcher.SettingsMenuItem
import com.purride.pixellauncherv2.launcher.SettingsMenuModel
import com.purride.pixellauncherv2.launcher.PixelFontStyle
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.widget.SettingsOptionStepperRow
import com.purride.pixellauncherv2.ui.widget.SettingsSegmentedSwitchRow
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
                        child = ListViewBuilder(
                            state = listState,
                            controller = listController,
                            itemCount = items.size,
                            itemExtent = SettingsListGeometry.ROW_EXTENT_PX,
                            spacing = SettingsListGeometry.ROW_SPACING_PX,
                            itemBuilder = { index ->
                                items[index]
                            },
                        ),
                    ),
                ),
            )
        }

        private fun LauncherUiState.toSettingsWidgets(t: LauncherTheme): List<Widget> = buildList {
            val pixelPreview = previewPixelSizeRatio ?: SettingsMenuModel.resolutionRatio(selectedDotSizePx)
            val pixelPreviewSize = SettingsMenuModel.resolutionAtRatio(pixelPreview)
            val gapPreview = previewGapRatio ?: SettingsMenuModel.pixelGapRatioSnap(pixelGapRatio)
            add(
                SettingsSegmentedSwitchRow(
                    title = "FONT STYLE",
                    rightSelected = selectedFontStyle == PixelFontStyle.PROP,
                    leftLabel = "MONO",
                    rightLabel = "PROP",
                    theme = t,
                    onToggle = { widget.onItemAction(SettingsMenuItem.FONT_STYLE, +1) },
                ),
            )
            add(
                SettingsValueSlider(
                    title = "PIXEL SIZE",
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
                    title = "GAP SIZE",
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
                SettingsSegmentedSwitchRow(
                    title = "THEME",
                    rightSelected = selectedTheme.ordinal == 1,
                    leftLabel = "DAY",
                    rightLabel = "NIGHT",
                    theme = t,
                    onToggle = { widget.onItemAction(SettingsMenuItem.THEME, +1) },
                ),
            )
            add(
                SettingsOptionStepperRow(
                    title = "APP ALIGN",
                    valueLabel = SettingsMenuModel.drawerListAlignmentLabel(drawerListAlignment),
                    theme = t,
                    onPrevious = { widget.onItemAction(SettingsMenuItem.APP_LIST_ALIGNMENT, -1) },
                    onNext = { widget.onItemAction(SettingsMenuItem.APP_LIST_ALIGNMENT, +1) },
                ),
            )
            add(
                SettingsSwitchRow(
                    title = "DRAWER SEARCH",
                    checked = openDrawerInSearchMode,
                    theme = t,
                    showLabels = true,
                    onToggle = { widget.onItemAction(SettingsMenuItem.DRAWER_AUTO_SEARCH, +1) },
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
