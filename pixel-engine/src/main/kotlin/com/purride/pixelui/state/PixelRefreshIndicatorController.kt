package com.purride.pixelui.state

import com.purride.pixelui.ChangeNotifier

/**
 * Pull-to-refresh 的状态控制器。
 */
public class PixelRefreshIndicatorController : ChangeNotifier() {

    /** 创建初始 refresh indicator 状态。 */
    public fun create(): PixelRefreshIndicatorState = PixelRefreshIndicatorState()

    /** 开始下拉手势，正在刷新时忽略。 */
    public fun startPull(state: PixelRefreshIndicatorState) {
        if (state.isRefreshing) return
        state.pullDistancePx = 0f
        state.isArmed = false
        notifyListeners()
    }

    /** 更新下拉距离，并根据阈值设置 armed 状态。 */
    public fun updatePull(
        state: PixelRefreshIndicatorState,
        distancePx: Float,
        thresholdPx: Int,
    ) {
        if (state.isRefreshing) return
        val safeDistance = distancePx.coerceAtLeast(0f)
        val safeThreshold = thresholdPx.coerceAtLeast(1)
        val armed = safeDistance >= safeThreshold
        if (state.pullDistancePx == safeDistance && state.isArmed == armed) return
        state.pullDistancePx = safeDistance
        state.isArmed = armed
        notifyListeners()
    }

    /** 结束下拉；达到阈值时进入 refreshing 并返回 true。 */
    public fun endPull(
        state: PixelRefreshIndicatorState,
        thresholdPx: Int,
    ): Boolean {
        if (state.isRefreshing) return false
        val shouldRefresh = state.pullDistancePx >= thresholdPx.coerceAtLeast(1)
        state.isArmed = false
        if (shouldRefresh) {
            state.isRefreshing = true
            state.pullDistancePx = thresholdPx.coerceAtLeast(1).toFloat()
        } else {
            state.pullDistancePx = 0f
        }
        notifyListeners()
        return shouldRefresh
    }

    /** 结束刷新并把交互状态复位。 */
    public fun completeRefresh(state: PixelRefreshIndicatorState) {
        if (!state.isRefreshing && state.pullDistancePx == 0f && !state.isArmed) return
        state.isRefreshing = false
        state.isArmed = false
        state.pullDistancePx = 0f
        notifyListeners()
    }
}
