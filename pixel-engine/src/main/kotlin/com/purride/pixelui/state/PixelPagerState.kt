package com.purride.pixelui.state

import android.os.Bundle
import com.purride.pixelcore.AxisMotionState
import com.purride.pixelcore.PixelAxis
import com.purride.pixelui.internal.PixelArtifactInternalApi

/**
 * 通用分页状态。
 *
 * 分页语义明确归 pixel-engine UI layer：
 * 当前页、页数、轴向，以及吸附过程中的目标页都由这里持有。
 */
public class PixelPagerState(
    axis: PixelAxis = PixelAxis.HORIZONTAL,
    currentPage: Int = 0,
    pageCount: Int = 1,
) {
    /** 保存 `PixelPagerState` 当前的 `axis` 状态维度；写入后由所属对象在下一次状态同步时生效。 */
    public var axis: PixelAxis = axis
        internal set

    /** 提供 `PixelPagerState` 当前管理的 `currentPage` 内容；写入后由所属对象在下一次状态同步时生效。 */
    public var currentPage: Int = currentPage.coerceAtLeast(0)
        internal set

    /** 保存 `PixelPagerState` 的 `pageCount` 计数或索引边界；写入后由所属对象在下一次状态同步时生效。 */
    public var pageCount: Int = pageCount.coerceAtLeast(1)
        internal set

    /** 提供 `PixelPagerState` 当前管理的 `settleTargetPage` 内容；写入后由所属对象在下一次状态同步时生效。 */
    public var settleTargetPage: Int = this.currentPage
        internal set

    /** 供 Host 去重分页回调的最近已派发页码。 */
    @PixelArtifactInternalApi
    public var lastDispatchedPage: Int = this.currentPage

    internal var motionState: AxisMotionState = AxisMotionState()
    internal var dragStartOffsetPx: Float = 0f

    /** 表示 `PixelPagerState` 当前是否满足 `isDragging` 对应条件。 */
    public val isDragging: Boolean
        get() = motionState.isDragging

    /** 表示 `PixelPagerState` 当前是否满足 `isSettling` 对应条件。 */
    public val isSettling: Boolean
        get() = motionState.isSettling
}

/**
 * PageView 的可持久化页位置。
 */
public data class PixelPagerSavedState(
    /** 提供 `PixelPagerState` 当前管理的 `currentPage` 内容。 */
    public val currentPage: Int,
    /** 保存 `PixelPagerState` 当前的 `axis` 状态维度。 */
    public val axis: PixelAxis,
)

/** [PixelPagerSavedState.saveToBundle] 与 [getPixelPagerSavedState] 使用的默认 key。 */
public const val PixelPagerSavedStateBundleKey: String = "com.purride.pixelui.pager.savedState"

/** 把分页位置写入 Android [Bundle]，供 Activity / Fragment 保存实例状态。 */
public fun PixelPagerSavedState.saveToBundle(
    outState: Bundle,
    key: String = PixelPagerSavedStateBundleKey,
) {
    require(key.isNotBlank()) { "PixelPagerSavedState Bundle key must not be blank" }
    val bundle = Bundle()
    bundle.putInt(PixelPagerSavedStateKeys.CurrentPage, currentPage)
    bundle.putString(PixelPagerSavedStateKeys.Axis, axis.name)
    outState.putBundle(key, bundle)
}

/** 从 Android [Bundle] 读取之前保存的分页位置；缺少或损坏时返回 null。 */
public fun Bundle.getPixelPagerSavedState(
    key: String = PixelPagerSavedStateBundleKey,
): PixelPagerSavedState? {
    require(key.isNotBlank()) { "PixelPagerSavedState Bundle key must not be blank" }
    val bundle = getBundle(key) ?: return null
    val axisName = bundle.getString(PixelPagerSavedStateKeys.Axis) ?: return null
    val axis = runCatching { PixelAxis.valueOf(axisName) }.getOrNull() ?: return null
    return PixelPagerSavedState(
        currentPage = bundle.getInt(PixelPagerSavedStateKeys.CurrentPage),
        axis = axis,
    )
}

private object PixelPagerSavedStateKeys {
    const val CurrentPage = "currentPage"
    const val Axis = "axis"
}
