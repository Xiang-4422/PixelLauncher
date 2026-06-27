package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.state.PixelRefreshIndicatorController
import com.purride.pixelui.state.PixelRefreshIndicatorState

/**
 * 带可选上下栏的下拉刷新页面骨架。
 *
 * 组件复用 [RefreshIndicator] 的手势与状态；[state] 和 [controller] 仍由调用方持有，
 * 不会在内部创建或保存刷新生命周期。
 */
public fun SwipeRefreshScaffold(
    body: Widget,
    state: PixelRefreshIndicatorState,
    controller: PixelRefreshIndicatorController,
    onRefresh: () -> Unit,
    topBar: Widget? = null,
    bottomBar: Widget? = null,
    thresholdPx: Int = 12,
    enabled: Boolean = true,
    indicatorColor: PixelColor = PixelColor.White,
    armedColor: PixelColor = PixelColor.fromRgb(200, 100, 0),
    refreshingColor: PixelColor = PixelColor.fromRgb(255, 255, 0),
    key: Any? = null,
): Widget {
    val hasBars = topBar != null || bottomBar != null
    val refresh = RefreshIndicator(
        child = body,
        state = state,
        controller = controller,
        onRefresh = onRefresh,
        thresholdPx = thresholdPx,
        enabled = enabled,
        indicatorColor = indicatorColor,
        armedColor = armedColor,
        refreshingColor = refreshingColor,
        key = if (hasBars) key?.let { "$it-refresh" } else key,
    )
    if (!hasBars) return refresh

    val children = buildList {
        if (topBar != null) {
            add(topBar)
            add(Gap(height = 1))
        }
        add(Expanded(child = refresh))
        if (bottomBar != null) {
            add(Gap(height = 1))
            add(bottomBar)
        }
    }
    return Column(
        children = children,
        mainAxisSize = MainAxisSize.MAX,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        key = key,
    )
}
