package com.purride.pixelui.state

import com.purride.pixelui.ChangeNotifier
import com.purride.pixelui.PixelScrollPhysics
import com.purride.pixelui.internal.PixelArtifactInternalApi
import kotlin.math.abs

/**
 * 通用列表控制器。
 *
 * 第一版先聚焦最小滚动能力：
 * 1. 根据内容高度和视口高度夹紧滚动范围
 * 2. 响应手指拖动更新滚动偏移
 *
 * 惯性、回弹、锚点恢复和 item 定位会在同一个 offset 模型上收敛。
 *
 * 监听变化：本类继承 [ChangeNotifier]，可直接
 * `controller.addListener { /* on changed */ }` 注册回调，或用
 * `controller.observe { ... }` 扩展拿到句柄方便后续 removeListener。
 */
public class PixelListController(
    private val physics: PixelScrollPhysics = PixelScrollPhysics.Default,
) : ChangeNotifier() {

    /** 创建列表滚动状态，可传入初始滚动偏移。 */
    public fun create(initialScrollOffsetPx: Float = 0f): PixelListState {
        return PixelListState(initialScrollOffsetPx = initialScrollOffsetPx)
    }

    /** 同步 viewport/content 几何，并夹紧当前滚动偏移。 */
    public fun sync(
        state: PixelListState,
        viewportHeightPx: Int,
        contentHeightPx: Int,
    ) {
        // 仅在几何真的变化时通知。每个滚动/列表 viewport 的 layout() 每帧都调用
        // sync()，而 layout 发生在宿主 render pass 之外（不受 BuildOwner 的
        // inRenderPass 抑制）；若无条件通知，监听该控制器的 widget 就会每帧
        // markNeedsBuild → 宿主 postInvalidateOnAnimation 永不收敛，任何含
        // 列表的页面静止时也满帧空转。事件驱动调用方（dragBy/scrollTo/restoreState
        // 等）在 sync 之后各自再 notify，不依赖此处的无条件通知。
        var changed = reconcileGeometry(
                state = state,
                viewportHeightPx = viewportHeightPx,
                contentHeightPx = contentHeightPx,
            )
        val pendingRestoration = state.pendingRestorationState
        if (pendingRestoration != null) {
            val restoredOffset = restoredOffset(
                state = state,
                savedState = pendingRestoration,
                policy = state.pendingRestorationPolicy,
                waitForAnchorGeometry = true,
            )
            if (restoredOffset != null) {
                state.pendingRestorationState = null
                changed = changed || restoredOffset != state.scrollOffsetPx
                state.scrollOffsetPx = restoredOffset
                state.isDragging = false
                state.isSettling = false
                state.scrollVelocityPxPerSecond = 0f
                state.snapTargetOffsetPx = null
            }
        }
        if (state.pendingJumpToEnd) {
            state.pendingJumpToEnd = false
            changed = changed || state.scrollOffsetPx != state.maxScrollOffsetPx
            state.scrollOffsetPx = state.maxScrollOffsetPx
            state.isDragging = false
            state.isSettling = false
            state.scrollVelocityPxPerSecond = 0f
            state.snapTargetOffsetPx = null
        }
        if (changed) {
            notifyListeners()
        }
    }

    /**
     * 把 viewport / content 几何同步进 [state] 并夹紧滚动偏移，返回本次是否真的
     * 改变了任何字段。
     *
     * 与 [sync] 的区别：本方法**不**调用 [notifyListeners]，由调用方决定是否通知。
     * 每帧的 [step] 借此在列表静止时保持沉默——否则 `step → notifyListeners`
     * 会让监听该控制器的 element 每帧 markNeedsBuild，宿主 postInvalidateOnAnimation
     * 永不收敛，形成空转重绘循环。[sync] 仍保持"总是通知"的对外语义。
     */
    private fun reconcileGeometry(
        state: PixelListState,
        viewportHeightPx: Int,
        contentHeightPx: Int,
    ): Boolean {
        val newViewportHeightPx = viewportHeightPx.coerceAtLeast(0)
        val newContentHeightPx = contentHeightPx.coerceAtLeast(0)
        val newMaxScrollOffsetPx = maxScrollOffsetPx(
            viewportHeightPx = viewportHeightPx,
            contentHeightPx = contentHeightPx,
        )
        val newScrollOffsetPx = if (physics.bounceEnabled && (state.isDragging || state.isSettling)) {
            coerceExistingOverscroll(state.scrollOffsetPx, newMaxScrollOffsetPx)
        } else {
            coerceOffset(state.scrollOffsetPx, newMaxScrollOffsetPx)
        }
        var changed = newViewportHeightPx != state.viewportHeightPx ||
            newContentHeightPx != state.contentHeightPx ||
            newMaxScrollOffsetPx != state.maxScrollOffsetPx ||
            newScrollOffsetPx != state.scrollOffsetPx
        state.viewportHeightPx = newViewportHeightPx
        state.contentHeightPx = newContentHeightPx
        state.maxScrollOffsetPx = newMaxScrollOffsetPx
        state.scrollOffsetPx = newScrollOffsetPx
        if (newScrollOffsetPx <= 0f || newScrollOffsetPx >= newMaxScrollOffsetPx) {
            if (state.isSettling) {
                stopSettling(state)
                changed = true
            }
        }
        return changed
    }

    /** 按触摸拖动增量更新滚动位置。 */
    public fun dragBy(
        state: PixelListState,
        deltaPx: Float,
        viewportHeightPx: Int,
        contentHeightPx: Int,
    ) {
        state.pendingJumpToEnd = false
        sync(
            state = state,
            viewportHeightPx = viewportHeightPx,
            contentHeightPx = contentHeightPx,
        )
        state.isDragging = true
        state.isSettling = false
        state.scrollVelocityPxPerSecond = 0f
        state.snapTargetOffsetPx = null
        state.scrollOffsetPx = coerceDraggedOffset(
            targetOffsetPx = state.scrollOffsetPx - deltaPx,
            maxScrollOffsetPx = state.maxScrollOffsetPx,
        )
        notifyListeners()
    }

    /** 进入拖动状态并取消已有 fling/snap。 */
    public fun startDrag(state: PixelListState) {
        state.pendingJumpToEnd = false
        state.isDragging = true
        state.isSettling = false
        state.scrollVelocityPxPerSecond = 0f
        state.snapTargetOffsetPx = null
        notifyListeners()
    }

    /**
     * 判断当前这次拖动是否还能被列表消费。
     *
     * `deltaPx` 使用触摸点的位移方向：
     * - 手指向下拖动时，`deltaPx > 0`，列表只有在“顶部上方还有内容”时才能继续跟手下移
     * - 手指向上拖动时，`deltaPx < 0`，列表只有在“底部下方还有内容”时才能继续向上滚
     */
    public fun canConsumeDrag(
        state: PixelListState,
        deltaPx: Float,
        viewportHeightPx: Int,
        contentHeightPx: Int,
    ): Boolean {
        state.pendingJumpToEnd = false
        sync(
            state = state,
            viewportHeightPx = viewportHeightPx,
            contentHeightPx = contentHeightPx,
        )
        return when {
            deltaPx > 0f -> state.scrollOffsetPx > 0f
            deltaPx < 0f -> state.scrollOffsetPx < state.maxScrollOffsetPx
            else -> false
        }
    }

    /** 立即滚动到指定绝对偏移，并按内容范围夹紧。 */
    public fun scrollTo(
        state: PixelListState,
        targetOffsetPx: Float,
        viewportHeightPx: Int,
        contentHeightPx: Int,
    ) {
        state.pendingJumpToEnd = false
        sync(
            state = state,
            viewportHeightPx = viewportHeightPx,
            contentHeightPx = contentHeightPx,
        )
        state.scrollOffsetPx = coerceOffset(targetOffsetPx, state.maxScrollOffsetPx)
        state.snapTargetOffsetPx = null
        notifyListeners()
    }

    internal fun scheduleJumpToEnd(state: PixelListState) {
        if (state.pendingJumpToEnd) return
        state.pendingJumpToEnd = true
        notifyListeners()
    }

    /** 判断当前滚动位置是否接近内容底部。 */
    public fun isAtEnd(state: PixelListState, tolerancePx: Float = 0.5f): Boolean {
        val safeTolerancePx = tolerancePx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        return state.maxScrollOffsetPx - state.scrollOffsetPx <= safeTolerancePx
    }

    /** 保存当前滚动位置和可选锚点，用于路由恢复。 */
    public fun saveState(state: PixelListState): PixelListSavedState {
        return PixelListSavedState(
            scrollOffsetPx = state.scrollOffsetPx.finiteOrZero().coerceAtLeast(0f),
            maxScrollOffsetPx = state.maxScrollOffsetPx.finiteOrZero().coerceAtLeast(0f),
            anchor = resolveSavedAnchor(state),
        )
    }

    /** 按指定策略恢复之前保存的滚动位置。 */
    public fun restoreState(
        state: PixelListState,
        savedState: PixelListSavedState,
        viewportHeightPx: Int = state.viewportHeightPx,
        contentHeightPx: Int = state.contentHeightPx,
        policy: PixelListRestorationPolicy = PixelListRestorationPolicy.AbsoluteOffset,
    ) {
        state.pendingRestorationState = null
        // Activity onCreate 恢复时通常还没有 viewport/content 几何，先等下一次 layout。
        if (
            savedState.scrollOffsetPx.finiteOrZero() > 0f &&
            viewportHeightPx <= 0 &&
            contentHeightPx <= 0 &&
            state.viewportHeightPx <= 0 &&
            state.contentHeightPx <= 0
        ) {
            state.pendingRestorationState = savedState
            state.pendingRestorationPolicy = policy
            state.isDragging = false
            state.isSettling = false
            state.scrollVelocityPxPerSecond = 0f
            state.snapTargetOffsetPx = null
            notifyListeners()
            return
        }
        sync(
            state = state,
            viewportHeightPx = viewportHeightPx,
            contentHeightPx = contentHeightPx,
        )
        val restoredOffset = restoredOffset(
            state = state,
            savedState = savedState,
            policy = policy,
            waitForAnchorGeometry = policy == PixelListRestorationPolicy.AnchorItem,
        )
        state.isDragging = false
        state.isSettling = false
        state.scrollVelocityPxPerSecond = 0f
        state.snapTargetOffsetPx = null
        if (restoredOffset == null) {
            state.pendingRestorationState = savedState
            state.pendingRestorationPolicy = policy
            notifyListeners()
            return
        }
        state.scrollOffsetPx = restoredOffset
        notifyListeners()
    }

    /** 供 navigation artifact 在布局完成前登记延迟恢复状态的内部 SPI。 */
    @PixelArtifactInternalApi
    public fun scheduleRestoreState(
        state: PixelListState,
        savedState: PixelListSavedState,
        policy: PixelListRestorationPolicy,
    ) {
        state.pendingRestorationState = savedState
        state.pendingRestorationPolicy = policy
    }

    private fun restoredOffset(
        state: PixelListState,
        savedState: PixelListSavedState,
        policy: PixelListRestorationPolicy,
        waitForAnchorGeometry: Boolean,
    ): Float? {
        val savedOffset = savedState.scrollOffsetPx.finiteOrZero().coerceAtLeast(0f)
        val savedMaxOffset = savedState.maxScrollOffsetPx.finiteOrZero().coerceAtLeast(0f)
        val targetOffset = when (policy) {
            PixelListRestorationPolicy.AbsoluteOffset -> savedOffset
            PixelListRestorationPolicy.RelativeProgress -> {
                if (savedMaxOffset > 0f && state.maxScrollOffsetPx > 0f) {
                    (savedOffset / savedMaxOffset).coerceIn(0f, 1f) * state.maxScrollOffsetPx
                } else {
                    savedOffset
                }
            }
            PixelListRestorationPolicy.AnchorItem -> {
                val anchor = savedState.anchor ?: return restoredOffset(
                    state = state,
                    savedState = savedState,
                    policy = PixelListRestorationPolicy.RelativeProgress,
                    waitForAnchorGeometry = waitForAnchorGeometry,
                )
                when (val anchorOffset = restoredAnchorOffset(state, anchor)) {
                    is AnchorRestoreOffset.Resolved -> anchorOffset.offsetPx
                    AnchorRestoreOffset.Unavailable -> {
                        if (waitForAnchorGeometry) return null
                        return restoredOffset(
                            state = state,
                            savedState = savedState,
                            policy = PixelListRestorationPolicy.RelativeProgress,
                            waitForAnchorGeometry = false,
                        )
                    }
                    AnchorRestoreOffset.Invalid -> {
                        return restoredOffset(
                            state = state,
                            savedState = savedState,
                            policy = PixelListRestorationPolicy.RelativeProgress,
                            waitForAnchorGeometry = false,
                        )
                    }
                }
            }
        }
        return coerceOffset(targetOffset, state.maxScrollOffsetPx)
    }

    private fun resolveSavedAnchor(state: PixelListState): PixelListAnchor? {
        resolveSliverAnchor(state)?.let { anchor -> return anchor }
        return resolveItemAnchor(
            scrollOffsetPx = state.scrollOffsetPx,
            itemTopOffsetsPx = state.itemTopOffsetsPx,
            itemHeightsPx = state.itemHeightsPx,
        )
    }

    private fun resolveSliverAnchor(state: PixelListState): PixelListAnchor? {
        if (state.sliverListGeometries.isEmpty()) return null
        val scrollOffsetPx = state.scrollOffsetPx.finiteOrZero().coerceAtLeast(0f)
        val entry = state.sliverListGeometries.entries
            .sortedBy { (_, geometry) -> geometry.contentStartPx }
            .firstOrNull { (_, geometry) ->
                val contentEndPx = geometry.contentStartPx + geometry.contentHeightPx()
                scrollOffsetPx >= geometry.contentStartPx && scrollOffsetPx < contentEndPx
            } ?: return null
        val geometry = entry.value
        if (geometry.itemCount <= 0) return null
        val itemIndex = geometry.indexAtOffsetPx(scrollOffsetPx.toInt())
        val itemTopPx = geometry.itemTopPx(itemIndex).toFloat()
        val itemOffsetPx = (scrollOffsetPx - itemTopPx)
            .coerceIn(0f, geometry.itemHeightPx(itemIndex).toFloat())
        return PixelListAnchor(
            itemIndex = itemIndex,
            itemOffsetPx = itemOffsetPx,
            sliverIndex = entry.key,
        )
    }

    private fun resolveItemAnchor(
        scrollOffsetPx: Float,
        itemTopOffsetsPx: IntArray,
        itemHeightsPx: IntArray,
    ): PixelListAnchor? {
        if (itemTopOffsetsPx.isEmpty() || itemHeightsPx.isEmpty()) return null
        val viewportTopPx = scrollOffsetPx.finiteOrZero().coerceAtLeast(0f)
        val itemIndex = itemTopOffsetsPx.indices.firstOrNull { index ->
            val itemHeight = itemHeightsPx.getOrNull(index)?.coerceAtLeast(0) ?: 0
            itemTopOffsetsPx[index] + itemHeight > viewportTopPx
        } ?: itemTopOffsetsPx.lastIndex
        val itemTopPx = itemTopOffsetsPx[itemIndex].toFloat()
        val itemHeightPx = itemHeightsPx.getOrNull(itemIndex)?.coerceAtLeast(0)?.toFloat() ?: 0f
        return PixelListAnchor(
            itemIndex = itemIndex,
            itemOffsetPx = (viewportTopPx - itemTopPx).coerceIn(0f, itemHeightPx),
        )
    }

    private fun restoredAnchorOffset(
        state: PixelListState,
        anchor: PixelListAnchor,
    ): AnchorRestoreOffset {
        val sliverIndex = anchor.sliverIndex
        if (sliverIndex != null) {
            val geometry = state.sliverListGeometries[sliverIndex]
            if (geometry == null) {
                return if (state.sliverListGeometries.isEmpty()) {
                    AnchorRestoreOffset.Unavailable
                } else {
                    AnchorRestoreOffset.Invalid
                }
            }
            if (anchor.itemIndex !in 0 until geometry.itemCount) {
                return AnchorRestoreOffset.Invalid
            }
            val offset = geometry.itemTopPx(anchor.itemIndex) +
                anchor.itemOffsetPx.finiteOrZero().coerceAtLeast(0f)
            return AnchorRestoreOffset.Resolved(offset)
        }

        if (state.itemTopOffsetsPx.isEmpty() || state.itemHeightsPx.isEmpty()) {
            return AnchorRestoreOffset.Unavailable
        }
        if (anchor.itemIndex !in state.itemTopOffsetsPx.indices) {
            return AnchorRestoreOffset.Invalid
        }
        val offset = state.itemTopOffsetsPx[anchor.itemIndex] +
            anchor.itemOffsetPx.finiteOrZero().coerceAtLeast(0f)
        return AnchorRestoreOffset.Resolved(offset)
    }

    /** 结束拖动；必要时进入 fling 或 snap settling。 */
    public fun endDrag(
        state: PixelListState,
        velocityPxPerSecond: Float,
        viewportHeightPx: Int,
        contentHeightPx: Int,
    ) {
        sync(
            state = state,
            viewportHeightPx = viewportHeightPx,
            contentHeightPx = contentHeightPx,
        )
        state.isDragging = false

        val snapTarget = resolveSnapTarget(
            state = state,
            velocityPxPerSecond = velocityPxPerSecond,
        )
        if (snapTarget != null && abs(snapTarget - state.scrollOffsetPx) > physics.snapEpsilonPx) {
            state.snapTargetOffsetPx = snapTarget
            state.isSettling = true
            state.scrollVelocityPxPerSecond = if (snapTarget > state.scrollOffsetPx) {
                -SNAP_VELOCITY_PX_PER_SECOND
            } else {
                SNAP_VELOCITY_PX_PER_SECOND
            }
            notifyListeners()
            return
        }

        val canScroll = state.maxScrollOffsetPx > 0f
        if (!canScroll || !velocityPxPerSecond.isFinite() || abs(velocityPxPerSecond) < physics.minFlingVelocityPxPerSecond) {
            stopSettling(state)
            return
        }

        state.isSettling = true
        state.scrollVelocityPxPerSecond = velocityPxPerSecond
        notifyListeners()
    }

    /** 推进 fling/snap 动画，静止时不通知监听者。 */
    public fun step(
        state: PixelListState,
        deltaMs: Long,
        viewportHeightPx: Int,
        contentHeightPx: Int,
    ) {
        val geometryChanged = reconcileGeometry(
            state = state,
            viewportHeightPx = viewportHeightPx,
            contentHeightPx = contentHeightPx,
        )
        if (!state.isSettling || deltaMs <= 0L) {
            // 列表静止（或无时间推进）时，仅在几何真正变化时通知一次，否则保持
            // 沉默，让宿主进入 idle，不再每帧空转重绘。
            if (geometryChanged) {
                notifyListeners()
            }
            return
        }

        state.snapTargetOffsetPx?.let { target ->
            val distance = target - state.scrollOffsetPx
            val stepPx = SNAP_VELOCITY_PX_PER_SECOND * (deltaMs / 1000f)
            if (abs(distance) <= stepPx.coerceAtLeast(physics.snapEpsilonPx)) {
                state.scrollOffsetPx = target
                stopSettling(state)
            } else {
                state.scrollOffsetPx += if (distance > 0f) stepPx else -stepPx
            }
            notifyListeners()
            return
        }

        val deltaSeconds = deltaMs / 1000f
        val velocity = state.scrollVelocityPxPerSecond
        if (abs(velocity) < physics.minFlingVelocityPxPerSecond) {
            stopSettling(state)
            return
        }

        state.scrollOffsetPx = (state.scrollOffsetPx - (velocity * deltaSeconds))
            .let { targetOffset ->
                if (physics.bounceEnabled) {
                    coerceDraggedOffset(targetOffset, state.maxScrollOffsetPx)
                } else {
                    coerceOffset(targetOffset, state.maxScrollOffsetPx)
                }
            }

        val deceleration = if (velocity > 0f) {
            -physics.decelerationPxPerSecondSquared
        } else {
            physics.decelerationPxPerSecondSquared
        }
        val nextVelocity = velocity + (deceleration * deltaSeconds)
        state.scrollVelocityPxPerSecond = when {
            velocity > 0f && nextVelocity < 0f -> 0f
            velocity < 0f && nextVelocity > 0f -> 0f
            else -> nextVelocity
        }

        if (state.scrollOffsetPx <= 0f || state.scrollOffsetPx >= state.maxScrollOffsetPx) {
            stopSettling(state)
            return
        }
        if (abs(state.scrollVelocityPxPerSecond) < physics.minFlingVelocityPxPerSecond ||
            abs(state.scrollOffsetPx) <= physics.snapEpsilonPx ||
            abs(state.scrollOffsetPx - state.maxScrollOffsetPx) <= physics.snapEpsilonPx
        ) {
            stopSettling(state)
        }
        notifyListeners()
    }

    /** 当前列表是否仍在拖动或惯性滚动。 */
    public fun isActive(state: PixelListState): Boolean {
        return state.isDragging || state.isSettling
    }

    /**
     * 将指定项滚动到当前视口内。
     *
     * 第一版采用“尽量少移动”的规则：
     * - 如果目标项已经完整可见，则保持当前位置
     * - 如果目标项在视口上方，则把该项顶部对齐到视口顶部
     * - 如果目标项在视口下方，则把该项底部拉回到视口底部
     */
    public fun scrollItemIntoView(
        state: PixelListState,
        itemIndex: Int,
    ) {
        if (itemIndex !in state.itemTopOffsetsPx.indices || itemIndex !in state.itemHeightsPx.indices) {
            return
        }
        if (state.viewportHeightPx <= 0) {
            return
        }

        val itemTopPx = state.itemTopOffsetsPx[itemIndex].toFloat()
        val itemBottomPx = (state.itemTopOffsetsPx[itemIndex] + state.itemHeightsPx[itemIndex]).toFloat()
        val viewportTopPx = state.scrollOffsetPx
        val viewportBottomPx = viewportTopPx + state.viewportHeightPx
        val targetOffsetPx = when {
            itemTopPx < viewportTopPx -> itemTopPx
            itemBottomPx > viewportBottomPx -> itemBottomPx - state.viewportHeightPx
            else -> state.scrollOffsetPx
        }

        // 变高 lazy list 远端定位：如果目标项尚未被真实测量，本次只能按 estimated
        // 高度算 offset，下一帧测量到位后还需要再校正一次。把意图存进 state，由
        // RenderVariableLazyListViewport.layout() 在测量完成后重入。
        state.pendingScrollIntoViewItemIndex = if (state.separatedItemGeometryActive) {
            val virtualIndex = itemIndex * 2
            if (
                state.separatedItemExtentVariable &&
                virtualIndex in state.measuredSeparatedVirtualHeightsPx.indices &&
                state.measuredSeparatedVirtualHeightsPx[virtualIndex] == 0
            ) {
                itemIndex
            } else {
                null
            }
        } else if (
            itemIndex in state.measuredItemHeightsPx.indices &&
            state.measuredItemHeightsPx[itemIndex] > 0
        ) {
            null
        } else if (itemIndex in state.measuredItemHeightsPx.indices) {
            itemIndex
        } else {
            // 非变高路径（measuredItemHeightsPx 为空）不需要二次微调
            null
        }

        scrollTo(
            state = state,
            targetOffsetPx = targetOffsetPx,
            viewportHeightPx = state.viewportHeightPx,
            contentHeightPx = state.contentHeightPx,
        )
    }

    /**
     * 将 CustomScrollView 中指定 lazy sliver 的 item 滚动到视口内。
     */
    public fun scrollSliverItemIntoView(
        state: PixelListState,
        sliverIndex: Int,
        itemIndex: Int,
    ) {
        val geometry = state.sliverListGeometries[sliverIndex] ?: return
        if (itemIndex !in 0 until geometry.itemCount || state.viewportHeightPx <= 0) return

        val itemTopPx = geometry.itemTopPx(itemIndex).toFloat()
        val itemBottomPx = geometry.itemBottomPx(itemIndex).toFloat()
        val viewportTopPx = state.scrollOffsetPx
        val viewportBottomPx = viewportTopPx + state.viewportHeightPx
        val targetOffsetPx = when {
            itemTopPx < viewportTopPx -> itemTopPx
            itemBottomPx > viewportBottomPx -> itemBottomPx - state.viewportHeightPx
            else -> state.scrollOffsetPx
        }

        state.pendingSliverScrollIntoView = if (
            geometry.variableHeight &&
            geometry.measuredItemHeightsPx.getOrNull(itemIndex) == 0
        ) {
            PixelPendingSliverScrollIntoView(sliverIndex = sliverIndex, itemIndex = itemIndex)
        } else {
            null
        }
        scrollTo(
            state = state,
            targetOffsetPx = targetOffsetPx,
            viewportHeightPx = state.viewportHeightPx,
            contentHeightPx = state.contentHeightPx,
        )
    }

    private fun maxScrollOffsetPx(
        viewportHeightPx: Int,
        contentHeightPx: Int,
    ): Float {
        return (contentHeightPx - viewportHeightPx).coerceAtLeast(0).toFloat()
    }

    private fun stopSettling(state: PixelListState) {
        state.scrollOffsetPx = coerceOffset(state.scrollOffsetPx, state.maxScrollOffsetPx)
        state.isSettling = false
        state.scrollVelocityPxPerSecond = 0f
        state.snapTargetOffsetPx = null
    }

    private fun resolveSnapTarget(
        state: PixelListState,
        velocityPxPerSecond: Float,
    ): Float? {
        val range = state.scrollSnapRanges.firstOrNull { snapRange ->
            state.scrollOffsetPx > snapRange.startOffsetPx &&
                state.scrollOffsetPx < snapRange.endOffsetPx
        } ?: return null
        return when {
            velocityPxPerSecond < -physics.minFlingVelocityPxPerSecond -> range.endOffsetPx
            velocityPxPerSecond > physics.minFlingVelocityPxPerSecond -> range.startOffsetPx
            state.scrollOffsetPx - range.startOffsetPx <
                range.endOffsetPx - state.scrollOffsetPx -> range.startOffsetPx
            else -> range.endOffsetPx
        }.coerceIn(0f, state.maxScrollOffsetPx)
    }

    private fun coerceOffset(
        targetOffsetPx: Float,
        maxScrollOffsetPx: Float,
    ): Float {
        return targetOffsetPx.coerceIn(0f, maxScrollOffsetPx)
    }

    private fun coerceDraggedOffset(
        targetOffsetPx: Float,
        maxScrollOffsetPx: Float,
    ): Float {
        if (!physics.bounceEnabled) {
            return coerceOffset(targetOffsetPx, maxScrollOffsetPx)
        }
        val limit = physics.bounceOverscrollLimitPx.coerceAtLeast(0f)
        if (limit == 0f) {
            return coerceOffset(targetOffsetPx, maxScrollOffsetPx)
        }
        val bounded = targetOffsetPx.coerceIn(-limit, maxScrollOffsetPx + limit)
        return when {
            bounded < 0f -> bounded * physics.bounceResistance.coerceIn(0f, 1f)
            bounded > maxScrollOffsetPx -> maxScrollOffsetPx +
                ((bounded - maxScrollOffsetPx) * physics.bounceResistance.coerceIn(0f, 1f))
            else -> bounded
        }
    }

    private fun coerceExistingOverscroll(
        targetOffsetPx: Float,
        maxScrollOffsetPx: Float,
    ): Float {
        val limit = physics.bounceOverscrollLimitPx.coerceAtLeast(0f)
        return targetOffsetPx.coerceIn(-limit, maxScrollOffsetPx + limit)
    }

    private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f

    private companion object {
        const val SNAP_VELOCITY_PX_PER_SECOND: Float = 240f
    }
}

private sealed class AnchorRestoreOffset {
    data class Resolved(val offsetPx: Float) : AnchorRestoreOffset()

    object Unavailable : AnchorRestoreOffset()

    object Invalid : AnchorRestoreOffset()
}
