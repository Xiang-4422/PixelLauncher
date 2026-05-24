package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Container
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixellauncherv2.launcher.SettingsMenuItem
import com.purride.pixellauncherv2.launcher.SettingsMenuModel
import com.purride.pixellauncherv2.launcher.SettingsMenuRow
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
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
    override val key: Any? = null,
) : StatefulWidget(key = key) {

    override fun createState(): State<out StatefulWidget> = SettingsState()

    private inner class SettingsState : State<SettingsScreen>() {

        private val listController = PixelListController()
        private val listState: PixelListState = listController.create()

        override fun build(context: BuildContext): Widget {
            val rows = widget.uiState.toSettingsRows()
            val t = widget.theme

            return Column(
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                mainAxisSize = MainAxisSize.MAX,
                spacing = 0,
                children = listOf(
                    Expanded(
                        child = ListViewBuilder(
                            state = listState,
                            controller = listController,
                            itemCount = rows.size,
                            itemExtent = ROW_HEIGHT,
                            spacing = 1,
                            itemBuilder = { index ->
                                buildRow(rows[index], t)
                            },
                        ),
                    ),
                ),
            )
        }

        private fun buildRow(
            row: SettingsMenuRow,
            t: LauncherTheme,
        ): Widget {
            val rowPadding = EdgeInsets.symmetric(horizontal = 2, vertical = 1)
            return Container(
                borderColor = null,
                padding = rowPadding,
                child = Row(
                    spacing = 2,
                    children = listOf(
                        // Left tap zone: title → previous value
                        GestureDetector(
                            onTap = { widget.onItemAction(row.item, -1) },
                            child = Text(
                                row.title,
                                style = TextStyle(color = t.primaryColor),
                            ),
                        ),
                        Expanded(child = SizedBox(width = 0, height = 0)),
                        // Right tap zone: value label → next value
                        GestureDetector(
                            onTap = { widget.onItemAction(row.item, +1) },
                            child = Text(
                                SettingsMenuModel.displayValue(row),
                                style = TextStyle(color = t.dimColor),
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    companion object {
        /** 每行像素高度（含 1px padding × 2 + 字体行高约 10px）。 */
        const val ROW_HEIGHT = 12
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

/** 从 [LauncherUiState] 生成当前的设置行列表，去除 IDLE/ChargeEffect 相关项。 */
private fun LauncherUiState.toSettingsRows(): List<SettingsMenuRow> = buildList {
    add(SettingsMenuRow(
        item = SettingsMenuItem.FONT_SIZE,
        title = "FONT SIZE",
        value = com.purride.pixellauncherv2.render.PixelFontCatalog.sizeLabel(selectedFontSize),
    ))
    add(SettingsMenuRow(
        item = SettingsMenuItem.FONT_STYLE,
        title = "FONT STYLE",
        value = com.purride.pixellauncherv2.render.PixelFontCatalog.styleLabel(selectedFontStyle),
    ))
    add(SettingsMenuRow(
        item = SettingsMenuItem.RESOLUTION,
        title = "PIXEL SIZE",
        value = "${selectedDotSizePx}PX",
    ))
    add(SettingsMenuRow(
        item = SettingsMenuItem.PIXEL_GAP,
        title = "PIXEL GAP",
        value = if (isPixelGapEnabled) "ON" else "OFF",
    ))
    if (isPixelGapEnabled) {
        add(SettingsMenuRow(
            item = SettingsMenuItem.STYLE,
            title = "STYLE",
            value = SettingsMenuModel.styleLabel(selectedPixelShape),
        ))
    }
    add(SettingsMenuRow(
        item = SettingsMenuItem.THEME,
        title = "THEME",
        value = SettingsMenuModel.themeLabel(selectedTheme),
    ))
    add(SettingsMenuRow(
        item = SettingsMenuItem.APP_LIST_ALIGNMENT,
        title = "APP ALIGN",
        value = SettingsMenuModel.drawerListAlignmentLabel(drawerListAlignment),
    ))
    add(SettingsMenuRow(
        item = SettingsMenuItem.DRAWER_AUTO_SEARCH,
        title = "DRAWER SEARCH",
        value = if (openDrawerInSearchMode) "ON" else "OFF",
    ))
}
