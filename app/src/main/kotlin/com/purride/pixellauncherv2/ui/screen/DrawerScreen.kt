package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.Text
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixellauncherv2.launcher.AppEntry
import com.purride.pixellauncherv2.launcher.DrawerListAlignment
import com.purride.pixellauncherv2.launcher.DrawerListGeometry
import com.purride.pixellauncherv2.launcher.PixelFontCatalog
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

private const val DRAWER_ROW_PADDING_PX = 2

/**
 * APP_DRAWER 应用列表页（pixel-engine 渲染）。
 *
 * 从 [com.purride.pixellauncherv2.launcher.LauncherRootHost] 抽出，与 HomeScreen /
 * SettingsScreen 保持一致的 screen 形态。搜索输入框仍由 host 的共享状态栏承载；本函数
 * 只负责应用列表本身。列表滚动状态由 host 持有并通过 [listState] / [listController] 注入。
 *
 * @param uiState        当前 UI 状态快照（可见应用、查询、对齐、字号、加载态）
 * @param theme          当前颜色主题
 * @param listState      host 持有的列表滚动状态
 * @param listController host 持有的列表滚动控制器
 * @param onAppPressed   点击某行应用（按可见列表下标）
 * @param onAppLongPressed 长按某行应用（按可见列表下标）
 */
fun DrawerScreen(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    listState: PixelListState,
    listController: ScrollController,
    onAppPressed: (Int) -> Unit,
    onAppLongPressed: (Int) -> Unit,
): Widget {
    val apps = drawerApps(uiState)
    val rowHeight = DrawerListGeometry.rowExtent(PixelFontCatalog.defaultUiFontSize.px)
    return Column(
        spacing = 0,
        mainAxisSize = MainAxisSize.MAX,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        children = listOf(
            Expanded(
                child = Padding(
                    horizontal = 2,
                    vertical = 2,
                    child = ListViewBuilder(
                        itemCount = apps.size.coerceAtLeast(1),
                        state = listState,
                        controller = listController,
                        itemExtent = rowHeight,
                        cacheExtent = 4,
                        spacing = DrawerListGeometry.ROW_SPACING_PX,
                        itemBuilder = { index ->
                            val app = apps.getOrNull(index)
                            drawerListItem(
                                label = app?.label?.uppercase() ?: if (uiState.isLoading) "LOADING" else "NO RESULTS",
                                enabled = app != null,
                                rowHeight = rowHeight,
                                alignment = uiState.drawerListAlignment,
                                theme = theme,
                                onTap = app?.let { { onAppPressed(index) } },
                                onLongPress = app?.let { { onAppLongPressed(index) } },
                            )
                        },
                    ),
                ),
            ),
        ),
    )
}

/** 当前抽屉应用列表：优先可见列表，查询非空但无结果时为空，否则全部应用。 */
private fun drawerApps(uiState: LauncherUiState): List<AppEntry> {
    if (uiState.drawerVisibleApps.isNotEmpty()) return uiState.drawerVisibleApps
    if (uiState.drawerQuery.isNotBlank()) return emptyList()
    return uiState.apps
}

private fun drawerListItem(
    label: String,
    enabled: Boolean,
    rowHeight: Int,
    alignment: DrawerListAlignment,
    theme: LauncherTheme,
    onTap: (() -> Unit)?,
    onLongPress: (() -> Unit)?,
): Widget {
    val item = Row(
        mainAxisSize = MainAxisSize.MAX,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        children = listOf(
            Expanded(
                child = Container(
                    height = rowHeight,
                    fillColor = PixelColor.Transparent,
                    borderColor = null,
                    padding = EdgeInsets.symmetric(
                        horizontal = DRAWER_ROW_PADDING_PX,
                        vertical = DRAWER_ROW_PADDING_PX,
                    ),
                    alignment = when (alignment) {
                        DrawerListAlignment.LEFT -> Alignment.CENTER_START
                        DrawerListAlignment.CENTER -> Alignment.CENTER
                        DrawerListAlignment.RIGHT -> Alignment.CENTER_END
                    },
                    child = Text(
                        label,
                        style = TextStyle(color = if (enabled) theme.drawer.itemText else theme.drawer.itemTextMuted),
                        overflow = TextOverflow.ELLIPSIS,
                        softWrap = false,
                        maxLines = 1,
                    ),
                ),
            ),
        ),
    )
    return if (onTap != null) {
        GestureDetector(onTap = onTap, onLongPress = onLongPress, child = item)
    } else {
        item
    }
}
