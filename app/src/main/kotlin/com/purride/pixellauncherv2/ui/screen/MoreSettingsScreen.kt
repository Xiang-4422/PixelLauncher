package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.ListView
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixellauncherv2.BuildConfig
import com.purride.pixellauncherv2.launcher.DataHealthModel
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.launcher.NotificationSettingsModel
import com.purride.pixellauncherv2.launcher.SettingsListGeometry
import com.purride.pixellauncherv2.launcher.SettingsMenuItem
import com.purride.pixellauncherv2.launcher.SettingsMenuModel
import com.purride.pixellauncherv2.launcher.SettingsSection
import com.purride.pixellauncherv2.launcher.pixelMatterEffectModeLabel
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.widget.SettingsActionRow
import com.purride.pixellauncherv2.ui.widget.SettingsSectionHeader
import com.purride.pixellauncherv2.ui.widget.SettingsSwitchRow
import com.purride.pixellauncherv2.ui.widget.SettingsTextEdgeResolvers
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * 承载低频、实验性与开发用途设置的二级页面。
 *
 * 顶层设置只保留高频外观与行为选项；本页面继续复用统一的设置行组件与动作分发。
 */
class MoreSettingsScreen(
    private val uiState: LauncherUiState,
    private val theme: LauncherTheme,
    private val textEdgeResolvers: SettingsTextEdgeResolvers,
    private val onItemAction: (SettingsMenuItem, Int) -> Unit,
    override val key: Any? = null,
) : StatefulWidget(key = key) {

    override fun createState(): State<out StatefulWidget> = MoreSettingsState()

    /** MORE 页面独立维护滚动位置，子页面返回后仍保留原浏览位置。 */
    private inner class MoreSettingsState : State<MoreSettingsScreen>() {

        /** MORE 设置列表的滚动控制器。 */
        private val listController = PixelListController()
        /** 与滚动控制器绑定的稳定列表状态。 */
        private val listState: PixelListState = listController.create()

        override fun build(context: BuildContext): Widget {
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
                                items = widget.uiState.toMoreSettingsWidgets(widget.theme),
                                state = listState,
                                controller = listController,
                                spacing = SettingsListGeometry.ROW_SPACING_PX,
                            ),
                        ),
                    ),
                ),
            )
        }

        /** 将 MORE 页面状态映射成实际可交互的设置组件。 */
        private fun LauncherUiState.toMoreSettingsWidgets(currentTheme: LauncherTheme): List<Widget> = buildList {
            addSection(SettingsSection.NOTIFICATIONS, currentTheme)
            add(
                SettingsActionRow(
                    title = "WHITELIST",
                    valueLabel = NotificationSettingsModel.summary(allowedNotificationSourceIds),
                    theme = currentTheme,
                    textEdgeResolvers = widget.textEdgeResolvers,
                    onPressed = { widget.onItemAction(SettingsMenuItem.NOTIFICATIONS, +1) },
                ),
            )
            addSection(SettingsSection.ACCESS, currentTheme)
            add(
                SettingsActionRow(
                    title = "PERMISSIONS",
                    valueLabel = DataHealthModel.summary(this@toMoreSettingsWidgets),
                    theme = currentTheme,
                    textEdgeResolvers = widget.textEdgeResolvers,
                    onPressed = { widget.onItemAction(SettingsMenuItem.DATA_HEALTH, +1) },
                ),
            )
            addSection(SettingsSection.EXPERIMENTAL, currentTheme)
            add(
                SettingsSwitchRow(
                    title = "SHAKE",
                    checked = isPixelMatterEffectEnabled,
                    theme = currentTheme,
                    textEdgeResolvers = widget.textEdgeResolvers,
                    showLabels = true,
                    onToggle = { widget.onItemAction(SettingsMenuItem.PIXEL_MATTER_EFFECT, +1) },
                ),
            )
            if (isPixelMatterEffectEnabled) {
                add(
                    SettingsActionRow(
                        title = "SHAKE MODE",
                        valueLabel = pixelMatterEffectModeLabel(pixelMatterEffectMode),
                        theme = currentTheme,
                        textEdgeResolvers = widget.textEdgeResolvers,
                        onPressed = { widget.onItemAction(SettingsMenuItem.PIXEL_MATTER_EFFECT_MODE, +1) },
                    ),
                )
            }
            add(
                SettingsSwitchRow(
                    title = "HAND",
                    checked = isPixelMatterHandControlEnabled,
                    theme = currentTheme,
                    textEdgeResolvers = widget.textEdgeResolvers,
                    showLabels = true,
                    onToggle = { widget.onItemAction(SettingsMenuItem.PIXEL_MATTER_HAND_CONTROL, +1) },
                ),
            )
            if (BuildConfig.DEBUG) {
                addSection(SettingsSection.DEVELOPER, currentTheme)
                add(
                    SettingsActionRow(
                        title = "LOADING PREVIEW",
                        valueLabel = "OPEN",
                        theme = currentTheme,
                        textEdgeResolvers = widget.textEdgeResolvers,
                        onPressed = { widget.onItemAction(SettingsMenuItem.LOADING_PREVIEW, +1) },
                    ),
                )
                add(
                    SettingsActionRow(
                        title = "DIAGNOSTICS",
                        valueLabel = "OPEN",
                        theme = currentTheme,
                        textEdgeResolvers = widget.textEdgeResolvers,
                        onPressed = { widget.onItemAction(SettingsMenuItem.ADVANCED, +1) },
                    ),
                )
                if (isPixelMatterHandControlEnabled) {
                    add(
                        SettingsSwitchRow(
                            title = "HAND DEBUG",
                            checked = isPixelMatterHandDebugEnabled,
                            theme = currentTheme,
                            textEdgeResolvers = widget.textEdgeResolvers,
                            showLabels = true,
                            onToggle = { widget.onItemAction(SettingsMenuItem.PIXEL_MATTER_HAND_DEBUG, +1) },
                        ),
                    )
                }
            }
        }

        /** 添加一个与前方内容保持统一区段间距的分类标题。 */
        private fun MutableList<Widget>.addSection(section: SettingsSection, currentTheme: LauncherTheme) {
            val topMargin = if (isEmpty()) 0 else LauncherSpacing.SETTINGS_SECTION_GAP
            add(
                SettingsSectionHeader(
                    title = SettingsMenuModel.sectionLabel(section),
                    theme = currentTheme,
                    textEdgeResolvers = widget.textEdgeResolvers,
                    topMargin = topMargin,
                ),
            )
        }
    }
}
