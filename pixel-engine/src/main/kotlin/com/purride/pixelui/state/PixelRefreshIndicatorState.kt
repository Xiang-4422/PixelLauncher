package com.purride.pixelui.state

/**
 * Pull-to-refresh 的可持久化交互状态。
 */
public class PixelRefreshIndicatorState {
    /** 记录 `PixelRefreshIndicatorState` 的 `pullDistancePx` 配置或运行值，读取与更新均遵守所属类型约束；写入后由所属对象在下一次状态同步时生效。 */
    public var pullDistancePx: Float = 0f
        internal set

    /** 表示 `PixelRefreshIndicatorState` 当前是否满足 `isArmed` 对应条件；写入后由所属对象在下一次状态同步时生效。 */
    public var isArmed: Boolean = false
        internal set

    /** 表示 `PixelRefreshIndicatorState` 当前是否满足 `isRefreshing` 对应条件；写入后由所属对象在下一次状态同步时生效。 */
    public var isRefreshing: Boolean = false
        internal set
}

