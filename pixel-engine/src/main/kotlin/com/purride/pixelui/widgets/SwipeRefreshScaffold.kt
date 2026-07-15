package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.state.PixelRefreshIndicatorController
import com.purride.pixelui.state.PixelRefreshIndicatorState

/**
 * 带可选上下栏的下拉刷新页面骨架。
 *
 * 组件复用 [RefreshIndicator] 的手势与状态；[state] 和 [controller] 仍由调用方持有，
 * 不会在内部创建或保存刷新生命周期。[semanticLabel] 同时命名键盘和 Switch Access
 * 可触发的刷新动作。
 *
 * @param body 位于可选上下栏之间、参与下拉刷新的页面主体。
 * @param state 调用方持有的刷新阶段与拉动进度。
 * @param controller 驱动 [state] 刷新生命周期的控制器。
 * @param onRefresh 成功进入刷新阶段时调用一次的业务回调。
 * @param topBar 可选固定顶部栏。
 * @param bottomBar 可选固定底部栏。
 * @param thresholdPx 从拉动进入 armed 状态所需的像素距离。
 * @param enabled 是否接受下拉、键盘与无障碍刷新动作。
 * @param indicatorColor 普通拉动阶段的指示器颜色。
 * @param armedColor 达到触发阈值后的指示器颜色。
 * @param refreshingColor 正在刷新阶段的指示器颜色。
 * @param key scaffold 与刷新子边界的稳定 identity。
 * @param semanticLabel 与主体内容分离的刷新动作无障碍名称。
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
    semanticLabel: String = "Refresh",
): Widget {
    return SwipeRefreshScaffold(
        body = body,
        state = state,
        controller = controller,
        states = PixelControlStateSet.Normal,
        onRefresh = onRefresh,
        topBar = topBar,
        bottomBar = bottomBar,
        // Historical defaults are omission sentinels for live theme resolution.
        thresholdPx = thresholdPx.takeUnless { value -> value == 12 },
        enabled = enabled,
        indicatorColor = indicatorColor.takeUnless { color -> color == PixelColor.White },
        armedColor = armedColor.takeUnless { color -> color == SwipeRefreshLegacyArmedColor },
        refreshingColor = refreshingColor.takeUnless { color -> color == SwipeRefreshLegacyLoadingColor },
        key = key,
        semanticLabel = semanticLabel.takeUnless { label -> label == "Refresh" },
    )
}

/**
 * 执行 `SwipeRefreshScaffold` 的 `SwipeRefreshScaffold` 公开行为；具体参数、返回和副作用见下文。
 *
 * Builds a themed state-aware refresh scaffold while preserving the legacy binary facade.
 *
 * [states] is required and the explicit JVM name remains stable independently from Kotlin default
 * arguments. Bars remain outside the refresh target, while the body delegates every state, token,
 * focus, keyboard, semantics, and pointer contract to [RefreshIndicator].
 *
 * @param body Page body participating in pull-to-refresh.
 * @param state Caller-owned refresh lifecycle state.
 * @param controller Controller paired with [state].
 * @param states Caller-owned component states merged by [RefreshIndicator].
 * @param onRefresh Business callback; null normalizes the component to Disabled.
 * @param topBar Optional fixed bar above the refresh body.
 * @param bottomBar Optional fixed bar below the refresh body.
 * @param thresholdPx Optional pull threshold; null resolves foundation size tokens.
 * @param enabled Whether refresh mutation is available before state normalization.
 * @param indicatorColor Optional ordinary-pull foreground override.
 * @param armedColor Optional armed/Selected foreground override.
 * @param refreshingColor Optional refreshing/Loading foreground override.
 * @param key Stable identity for the scaffold and refresh child boundary.
 * @param semanticLabel Optional spoken label; null or blank resolves theme labels.
 */
@JvmName("SwipeRefreshScaffoldWithControlStates")
public fun SwipeRefreshScaffold(
    body: Widget,
    state: PixelRefreshIndicatorState,
    controller: PixelRefreshIndicatorController,
    states: PixelControlStateSet,
    onRefresh: (() -> Unit)?,
    topBar: Widget? = null,
    bottomBar: Widget? = null,
    thresholdPx: Int? = null,
    enabled: Boolean = true,
    indicatorColor: PixelColor? = null,
    armedColor: PixelColor? = null,
    refreshingColor: PixelColor? = null,
    key: Any? = null,
    semanticLabel: String? = null,
): Widget {
    /** 是否需要把刷新主体包进带固定栏位的纵向骨架。 */
    val hasBars = topBar != null || bottomBar != null
    /** 复用全部手势、键盘和语义刷新契约的主体节点。 */
    val refresh = RefreshIndicator(
        child = body,
        state = state,
        controller = controller,
        states = states,
        onRefresh = onRefresh,
        thresholdPx = thresholdPx,
        enabled = enabled,
        indicatorColor = indicatorColor,
        armedColor = armedColor,
        refreshingColor = refreshingColor,
        key = if (hasBars) key?.let { "$it-refresh" } else key,
        semanticLabel = semanticLabel,
    )
    if (!hasBars) return refresh
    return Builder(key = key?.let(::SwipeRefreshBarsThemeKey)) { context ->
        /** Live foundation spacing preserving the historical one-pixel Default gap. */
        val barGap = PixelTheme.tokensOf(context).spacing.extraSmall
        /** 按顶部栏、可扩展主体和底部栏顺序构建的 scaffold 子节点。 */
        val children = buildList {
            if (topBar != null) {
                add(topBar)
                add(Gap(height = barGap))
            }
            add(Expanded(child = refresh))
            if (bottomBar != null) {
                add(Gap(height = barGap))
                add(bottomBar)
            }
        }
        Column(
            children = children,
            mainAxisSize = MainAxisSize.MAX,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            key = key,
        )
    }
}

/** Stable identity for the mounted bar-spacing resolver above a refresh scaffold. */
private data class SwipeRefreshBarsThemeKey(
    /** Original caller-owned scaffold identity. */
    val scaffoldKey: Any,
)

/** Historical armed-color sentinel retained by the compatibility facade only. */
private val SwipeRefreshLegacyArmedColor: PixelColor = PixelColor.fromRgb(200, 100, 0)

/** Historical refreshing-color sentinel retained by the compatibility facade only. */
private val SwipeRefreshLegacyLoadingColor: PixelColor = PixelColor.fromRgb(255, 255, 0)
