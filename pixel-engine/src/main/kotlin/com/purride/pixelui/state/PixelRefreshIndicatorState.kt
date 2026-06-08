package com.purride.pixelui.state

/**
 * Pull-to-refresh 的可持久化交互状态。
 */
public class PixelRefreshIndicatorState {
    public var pullDistancePx: Float = 0f
        internal set

    public var isArmed: Boolean = false
        internal set

    public var isRefreshing: Boolean = false
        internal set
}

