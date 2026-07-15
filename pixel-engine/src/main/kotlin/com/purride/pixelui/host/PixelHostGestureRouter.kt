package com.purride.pixelui

import android.view.MotionEvent
import android.view.VelocityTracker
import com.purride.pixelcore.PixelAxis
import com.purride.pixelui.internal.PixelClickTarget
import com.purride.pixelui.internal.PixelRefreshTarget
import com.purride.pixelui.internal.PixelScrollbarTarget
import com.purride.pixelui.internal.PixelRenderResult
import com.purride.pixelui.internal.PixelSliderTarget
import com.purride.pixelui.internal.PixelTextInputSelectionGesture
import com.purride.pixelui.internal.PixelTextInputTarget
import kotlin.math.abs

/**
 * Routes Android touch events to pixel-engine gesture owners.
 *
 * PixelHostView keeps platform entry points and coordinate mapping; this router
 * owns the pager/list/text field/slider arbitration state machine.
 */
internal class PixelHostGestureRouter(
    private val host: PixelHostView,
) {
    /** Last logical hover point, retained only while a hover target owns feedback. */
    private var hoveredLogicalPoint: Pair<Int, Int>? = null

    /** 由 pause、detach 或 destroy 主动取消当前手势所有权，重复调用安全。 */
    fun cancelActiveGesture() {
        onCancel()
    }

    /**
     * Migrates active interaction ownership to callbacks from [renderResult] by stable source identity.
     *
     * Targets that disappeared, stopped exporting the required callback, or moved away from the
     * hover pointer are cancelled exactly once before their references are cleared.
     */
    fun reconcileTargets(renderResult: PixelRenderResult) {
        var changed = false

        host.capturedClickTarget = host.capturedClickTarget?.let { previous ->
            renderResult.clickTargets.findSameClickTarget(previous)
        }

        host.activePressedClickTarget?.let { previous ->
            val replacement = renderResult.clickTargets
                .findSameClickTarget(previous)
                ?.takeIf { it.onPressedChanged != null }
            if (replacement != null) {
                host.activePressedClickTarget = replacement
            } else {
                host.activePressedClickTarget = null
                previous.onPressedChanged?.invoke(false)
                changed = true
            }
        }

        host.activeSliderTarget?.let { previous ->
            val replacement = renderResult.sliderTargets.findSameSliderTarget(previous)
            if (replacement == null) {
                host.activeSliderTarget = null
                previous.onPressedChanged?.invoke(false)
                changed = true
            } else {
                /** 跨 artifact 属性先快照，避免依赖不稳定的智能类型转换。 */
                val previousPressedChanged = previous.onPressedChanged
                if (previousPressedChanged != null && replacement.onPressedChanged == null) {
                    previousPressedChanged.invoke(false)
                    changed = true
                }
                host.activeSliderTarget = replacement
            }
        }

        host.activeScrollbarTarget?.let { previous ->
            /** Matching enabled target from the latest render snapshot. */
            val replacement = renderResult.scrollbarTargets.findSameScrollbarTarget(previous)
            if (replacement == null) {
                host.activeScrollbarTarget = null
                previous.onPressedChanged?.invoke(false)
                endScrollbarDrag(previous)
                changed = true
            } else {
                /** 跨 artifact 属性先快照，避免依赖不稳定的智能类型转换。 */
                val previousPressedChanged = previous.onPressedChanged
                if (previousPressedChanged != null && replacement.onPressedChanged == null) {
                    previousPressedChanged.invoke(false)
                    changed = true
                }
                host.activeScrollbarTarget = replacement
            }
        }

        host.activeRefreshTarget?.let { previous ->
            /** Matching enabled refresh target from the latest render snapshot. */
            val replacement = renderResult.refreshTargets.findSameRefreshTarget(previous)
            if (replacement == null) {
                host.activeRefreshTarget = null
                host.candidateRefreshTarget = null
                previous.onPressedChanged?.invoke(false)
                cancelRefreshPull(previous)
                changed = true
            } else {
                /** 跨 artifact 属性先快照，避免依赖不稳定的智能类型转换。 */
                val previousPressedChanged = previous.onPressedChanged
                if (previousPressedChanged != null && replacement.onPressedChanged == null) {
                    previousPressedChanged.invoke(false)
                    changed = true
                }
                host.activeRefreshTarget = replacement
            }
        }
        host.candidateRefreshTarget = host.candidateRefreshTarget?.let { previous ->
            renderResult.refreshTargets.findSameRefreshTarget(previous)
        }

        val hoverPoint = hoveredLogicalPoint
        host.hoveredClickTarget?.let { previous ->
            val replacement = renderResult.clickTargets
                .findSameClickTarget(previous)
                ?.takeIf { target ->
                    target.onHoveredChanged != null &&
                        hoverPoint != null &&
                        target.bounds.contains(hoverPoint.first, hoverPoint.second)
                }
            if (replacement != null) {
                host.hoveredClickTarget = replacement
            } else {
                host.hoveredClickTarget = null
                previous.onHoveredChanged?.invoke(false)
                changed = true
            }
        }
        host.hoveredSliderTarget?.let { previous ->
            val replacement = renderResult.sliderTargets
                .findSameSliderTarget(previous)
                ?.takeIf { target ->
                    target.onHoveredChanged != null &&
                        hoverPoint != null &&
                        target.bounds.contains(hoverPoint.first, hoverPoint.second)
                }
            if (replacement != null) {
                host.hoveredSliderTarget = replacement
            } else {
                host.hoveredSliderTarget = null
                previous.onHoveredChanged?.invoke(false)
                changed = true
            }
        }
        host.hoveredScrollbarTarget?.let { previous ->
            /** Replacement retained only while the pointer remains inside its enabled track. */
            val replacement = renderResult.scrollbarTargets
                .findSameScrollbarTarget(previous)
                ?.takeIf { target ->
                    target.onHoveredChanged != null &&
                        hoverPoint != null &&
                        target.bounds.contains(hoverPoint.first, hoverPoint.second)
                }
            if (replacement != null) {
                host.hoveredScrollbarTarget = replacement
            } else {
                host.hoveredScrollbarTarget = null
                previous.onHoveredChanged?.invoke(false)
                changed = true
            }
        }
        host.hoveredRefreshTarget?.let { previous ->
            /** Replacement retained only while the pointer remains inside its enabled boundary. */
            val replacement = renderResult.refreshTargets
                .findSameRefreshTarget(previous)
                ?.takeIf { target ->
                    target.onHoveredChanged != null &&
                        hoverPoint != null &&
                        target.bounds.contains(hoverPoint.first, hoverPoint.second)
                }
            if (replacement != null) {
                host.hoveredRefreshTarget = replacement
            } else {
                host.hoveredRefreshTarget = null
                previous.onHoveredChanged?.invoke(false)
                changed = true
            }
        }
        if (
            host.hoveredClickTarget == null &&
            host.hoveredSliderTarget == null &&
            host.hoveredScrollbarTarget == null &&
            host.hoveredRefreshTarget == null
        ) {
            hoveredLogicalPoint = null
        }
        if (changed) host.invalidate()
    }

    /** 把单个 Android 触摸事件路由到当前像素手势所有者。 */
    fun onTouchEvent(event: MotionEvent): Boolean? {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(event)
            MotionEvent.ACTION_MOVE -> onMove(event)
            MotionEvent.ACTION_UP -> onUp(event)
            MotionEvent.ACTION_CANCEL -> onCancel()
            else -> null
        }
    }

    /**
     * Routes mouse and stylus hover without accepting touchscreen hover synthesized by TalkBack.
     *
     * Null means this router did not consume the platform event.
     */
    fun onHoverEvent(event: MotionEvent): Boolean? {
        if (!isMouseOrStylus(event)) return null
        return when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER,
            MotionEvent.ACTION_HOVER_MOVE,
            -> updateHover(event)

            MotionEvent.ACTION_HOVER_EXIT -> clearHoveredInteraction()
            else -> null
        }
    }

    private fun onDown(event: MotionEvent): Boolean {
        clearPressedClickTarget()
        host.capturedClickTarget = null
        host.velocityTracker?.recycle()
        host.velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
        host.touchDownX = event.x
        host.touchDownY = event.y
        host.touchMoved = false
        val logicalPoint = host.mapTouchToLogical(event.x, event.y) ?: return true
        host.touchDownLogicalX = logicalPoint.first
        host.touchDownLogicalY = logicalPoint.second
        host.lastPagerLogicalX = logicalPoint.first
        host.lastPagerLogicalY = logicalPoint.second
        host.lastListLogicalY = logicalPoint.second
        host.activeSliderTarget = host.lastRenderResult
            ?.sliderTargets
            ?.lastOrNull { it.bounds.contains(logicalPoint.first, logicalPoint.second) }
        host.activeSliderTarget?.let { target ->
            target.onPressedChanged?.invoke(true)
            host.invalidate()
        }
        host.activeScrollbarTarget = if (host.activeSliderTarget == null) {
            host.lastRenderResult
                ?.scrollbarTargets
                ?.lastOrNull { it.bounds.contains(logicalPoint.first, logicalPoint.second) }
        } else null
        host.activeScrollbarTarget?.let { target ->
            target.onPressedChanged?.invoke(true)
            host.scrollbarDragThumbOffsetY = if (target.thumbBounds.contains(logicalPoint.first, logicalPoint.second)) {
                logicalPoint.second - target.thumbBounds.top
            } else {
                target.thumbBounds.height / 2
            }.coerceIn(0, target.thumbBounds.height.coerceAtLeast(1))
            updateScrollbarDrag(target, logicalPoint.second)
            host.invalidate()
        }
        val dragExclusiveTargetActive = host.activeSliderTarget != null || host.activeScrollbarTarget != null
        if (!dragExclusiveTargetActive) {
            host.resolveClickTarget(logicalPoint.first, logicalPoint.second)?.let { target ->
                host.capturedClickTarget = target
                /** 当前按压回调快照，确保跨 artifact 调用使用同一实例。 */
                val pressedChanged = target.onPressedChanged
                if (pressedChanged != null) {
                    host.activePressedClickTarget = target
                    pressedChanged.invoke(true)
                    host.invalidate()
                }
            }
        }
        host.candidateRefreshTarget = if (!dragExclusiveTargetActive) {
            host.lastRenderResult
                ?.refreshTargets
                ?.lastOrNull { it.bounds.contains(logicalPoint.first, logicalPoint.second) }
        } else null
        host.candidatePagerTarget = if (!dragExclusiveTargetActive) {
            host.lastRenderResult
                ?.pagerTargets
                ?.lastOrNull { it.bounds.contains(logicalPoint.first, logicalPoint.second) }
        } else null
        host.candidateListTarget = if (!dragExclusiveTargetActive) {
            host.lastRenderResult
                ?.listTargets
                ?.lastOrNull { it.bounds.contains(logicalPoint.first, logicalPoint.second) }
        } else null
        val textInputTarget = host.lastRenderResult
            ?.textInputTargets
            ?.lastOrNull { it.bounds.contains(logicalPoint.first, logicalPoint.second) }
        host.candidateTextInputTarget = if (!dragExclusiveTargetActive) textInputTarget else null
        host.activeTextInputSelectionTarget = null
        host.activeTextInputSelectionHandle = null
        if (!dragExclusiveTargetActive && textInputTarget != null && !textInputTarget.readOnly) {
            PixelTextInputSelectionGesture.nearestHandle(textInputTarget, logicalPoint.first, logicalPoint.second)?.let { handle ->
                host.activeTextInputSelectionTarget = textInputTarget
                host.activeTextInputSelectionHandle = handle
            }
        }
        if (textInputTarget == null && host.focusedTextInputTarget != null) {
            host.clearFocusedTextInput()
        }
        host.activePagerTarget = null
        host.activeListTarget = null
        host.activeSwipeTarget = null
        host.candidateSwipeTarget = if (!dragExclusiveTargetActive) {
            host.resolveSwipeTarget(logicalPoint.first, logicalPoint.second)
        } else {
            null
        }
        host.activeRefreshTarget = null
        host.nestedScrollSession.consumedDeltaPx = 0f
        host.nestedScrollSession.remainingDeltaPx = 0f
        host.nestedScrollSession.edgeHandoff = false
        return true
    }

    private fun onMove(event: MotionEvent): Boolean {
        host.velocityTracker?.addMovement(event)
        val logicalPoint = host.mapTouchToLogical(event.x, event.y) ?: return true
        val rawDeltaX = event.x - host.touchDownX
        val rawDeltaY = event.y - host.touchDownY
        val logicalDeltaX = logicalPoint.first - host.touchDownLogicalX
        if (abs(rawDeltaX) > host.touchSlop || abs(rawDeltaY) > host.touchSlop) {
            host.touchMoved = true
            clearPressedClickTarget()
        }

        host.activeSliderTarget?.let { target ->
            val localX = logicalPoint.first - target.bounds.left
            val ratio = (localX.toFloat() / target.bounds.width).coerceIn(0f, 1f)
            target.onDrag(ratio)
            host.invalidate()
            return true
        }

        host.activeScrollbarTarget?.let { target ->
            updateScrollbarDrag(target, logicalPoint.second)
            host.invalidate()
            return true
        }

        host.activeRefreshTarget?.let { target ->
            val distancePx = (logicalPoint.second - host.touchDownLogicalY).toFloat().coerceAtLeast(0f)
            target.controller.updatePull(target.state, distancePx, target.thresholdPx)
            host.invalidate()
            return true
        }

        host.activeTextInputSelectionTarget?.let { target ->
            if (host.touchMoved && host.activePagerTarget == null && host.activeListTarget == null) {
                updateSelectionHandle(target, logicalPoint.first, logicalPoint.second)
                host.invalidate()
                return true
            }
        }

        host.candidateTextInputTarget?.let { target ->
            if (host.touchMoved && host.activePagerTarget == null && host.activeListTarget == null) {
                host.focusTextInput(target)
                PixelTextInputSelectionGesture.setCollapsedSelection(target, logicalPoint.first, logicalPoint.second)
                host.invalidate()
                return true
            }
        }

        host.candidateRefreshTarget?.let { target ->
            if (
                host.touchMoved &&
                host.activePagerTarget == null &&
                host.activeListTarget == null &&
                abs(rawDeltaY) >= abs(rawDeltaX) &&
                target.canStartPull(rawDeltaY)
            ) {
                host.activeRefreshTarget = target
                host.candidateRefreshTarget = null
                host.candidatePagerTarget = null
                host.candidateListTarget = null
                host.candidateTextInputTarget = null
                target.controller.startPull(target.state)
                target.onPressedChanged?.invoke(true)
                target.controller.updatePull(target.state, rawDeltaY.coerceAtLeast(0f), target.thresholdPx)
                host.invalidate()
                return true
            }
        }

        host.activeSwipeTarget?.let { target ->
            target.onSwipeUpdate?.invoke(logicalDeltaX)
            host.invalidate()
            return true
        }

        host.candidateSwipeTarget?.let { target ->
            if (
                host.activePagerTarget == null &&
                host.activeListTarget == null &&
                isHorizontalSwipe(rawDeltaX, rawDeltaY)
            ) {
                host.activeSwipeTarget = target
                host.candidateSwipeTarget = null
                host.candidatePagerTarget = null
                host.candidateListTarget = null
                host.candidateRefreshTarget = null
                host.candidateTextInputTarget = null
                clearActiveSelectionHandle()
                target.onSwipeStart?.invoke()
                target.onSwipeUpdate?.invoke(logicalDeltaX)
                host.invalidate()
                return true
            }
        }

        host.activePagerTarget?.let { target ->
            val deltaPx = when (target.axis) {
                PixelAxis.HORIZONTAL -> logicalPoint.first - host.lastPagerLogicalX
                PixelAxis.VERTICAL -> logicalPoint.second - host.lastPagerLogicalY
            }.toFloat()
            host.nestedScrollSession.consumedDeltaPx = deltaPx
            host.nestedScrollSession.remainingDeltaPx = 0f
            target.controller.dragBy(target.state, deltaPx, host.pagerViewportSize(target))
            host.lastPagerLogicalX = logicalPoint.first
            host.lastPagerLogicalY = logicalPoint.second
            host.invalidate()
            return true
        }

        host.activeListTarget?.let { target ->
            val deltaPx = (logicalPoint.second - host.lastListLogicalY).toFloat()
            val listCanConsumeDrag = target.controller.canConsumeDrag(
                target.state, deltaPx, target.viewportHeightPx, target.contentHeightPx,
            )
            host.nestedScrollSession.remainingDeltaPx = if (listCanConsumeDrag) 0f else deltaPx
            if (listCanConsumeDrag) {
                host.nestedScrollSession.consumedDeltaPx = deltaPx
                target.controller.dragBy(target.state, deltaPx, target.viewportHeightPx, target.contentHeightPx)
                host.lastListLogicalY = logicalPoint.second
                host.invalidate()
                return true
            }
            val pagerTarget = host.candidatePagerTarget
            if (pagerTarget != null &&
                host.nestedScrollPolicy.shouldHandOffListToPager(pagerTarget.axis, listCanConsumeDrag, deltaPx)
            ) {
                host.nestedScrollSession.edgeHandoff = true
                host.activeListTarget = null
                host.activePagerTarget = pagerTarget
                host.candidatePagerTarget = null
                host.candidateListTarget = null
                pagerTarget.controller.startDrag(
                    pagerTarget.state,
                    host.pagerViewportSize(pagerTarget),
                )
                pagerTarget.onPageDragStart?.invoke()
                host.lastPagerLogicalX = logicalPoint.first
                host.lastPagerLogicalY = host.lastListLogicalY
                pagerTarget.controller.dragBy(pagerTarget.state, deltaPx, host.pagerViewportSize(pagerTarget))
                host.nestedScrollSession.consumedDeltaPx = deltaPx
                host.nestedScrollSession.remainingDeltaPx = 0f
                host.lastPagerLogicalX = logicalPoint.first
                host.lastPagerLogicalY = logicalPoint.second
                host.invalidate()
                return true
            }
            host.lastListLogicalY = logicalPoint.second
            return true
        }

        host.candidatePagerTarget?.let { target ->
            val pagerWantsDrag = host.pagerGesturePolicy.shouldStartDrag(target.axis, rawDeltaX, rawDeltaY, host.touchSlop)
            val listWantsDrag = host.candidateListTarget?.let { host.shouldStartListDrag(rawDeltaX, rawDeltaY) } ?: false
            val listCanConsumeDrag = host.candidateListTarget?.let { listTarget ->
                listTarget.controller.canConsumeDrag(listTarget.state, rawDeltaY, listTarget.viewportHeightPx, listTarget.contentHeightPx)
            } ?: false
            val shouldDeferToList = host.nestedScrollPolicy.shouldDeferPagerToList(
                target.axis, pagerWantsDrag, listWantsDrag, listCanConsumeDrag,
            )
            if (pagerWantsDrag && !shouldDeferToList) {
                host.activePagerTarget = target
                host.candidatePagerTarget = null
                target.controller.startDrag(target.state, host.pagerViewportSize(target))
                target.onPageDragStart?.invoke()
                val initialDeltaPx = when (target.axis) {
                    PixelAxis.HORIZONTAL -> logicalPoint.first - host.touchDownLogicalX
                    PixelAxis.VERTICAL -> logicalPoint.second - host.touchDownLogicalY
                }.toFloat()
                if (initialDeltaPx != 0f) {
                    target.controller.dragBy(target.state, initialDeltaPx, host.pagerViewportSize(target))
                }
                host.nestedScrollSession.consumedDeltaPx = initialDeltaPx
                host.nestedScrollSession.remainingDeltaPx = 0f
                host.lastPagerLogicalX = logicalPoint.first
                host.lastPagerLogicalY = logicalPoint.second
                host.candidateListTarget = null
                host.invalidate()
            }
        }

        host.candidateListTarget?.let { target ->
            if (host.shouldStartListDrag(rawDeltaX, rawDeltaY)) {
                host.activeListTarget = target
                host.candidateListTarget = null
                target.controller.startDrag(target.state)
                val initialDeltaPx = (logicalPoint.second - host.touchDownLogicalY).toFloat()
                if (initialDeltaPx != 0f) {
                    target.controller.dragBy(target.state, initialDeltaPx, target.viewportHeightPx, target.contentHeightPx)
                }
                host.nestedScrollSession.consumedDeltaPx = initialDeltaPx
                host.nestedScrollSession.remainingDeltaPx = 0f
                host.lastListLogicalY = logicalPoint.second
                host.invalidate()
            }
        }
        return true
    }

    private fun onUp(event: MotionEvent): Boolean {
        val capturedClickTarget = host.capturedClickTarget
        host.capturedClickTarget = null
        clearPressedClickTarget()
        host.velocityTracker?.addMovement(event)
        host.velocityTracker?.computeCurrentVelocity(1000)
        val logicalPoint = host.mapTouchToLogical(event.x, event.y)

        host.activeSliderTarget?.let { target ->
            val localX = if (logicalPoint != null) {
                logicalPoint.first - target.bounds.left
            } else {
                target.bounds.width / 2
            }
            val ratio = (localX.toFloat() / target.bounds.width).coerceIn(0f, 1f)
            target.onPressedChanged?.invoke(false)
            target.onRelease(ratio)
            host.activeSliderTarget = null
            host.invalidate()
            return true
        }

        host.activeScrollbarTarget?.let { target ->
            if (logicalPoint != null) {
                updateScrollbarDrag(target, logicalPoint.second)
            }
            target.onPressedChanged?.invoke(false)
            endScrollbarDrag(target)
            host.activeScrollbarTarget = null
            recycleVelocityTracker()
            host.invalidate()
            return true
        }

        host.activeRefreshTarget?.let { target ->
            if (logicalPoint != null) {
                val distancePx = (logicalPoint.second - host.touchDownLogicalY).toFloat().coerceAtLeast(0f)
                target.controller.updatePull(target.state, distancePx, target.thresholdPx)
            }
            target.onPressedChanged?.invoke(false)
            if (target.controller.endPull(target.state, target.thresholdPx)) {
                target.onRefresh()
            }
            host.activeRefreshTarget = null
            host.candidateRefreshTarget = null
            recycleVelocityTracker()
            host.invalidate()
            return true
        }

        host.activeSwipeTarget?.let { target ->
            val rawDeltaX = event.x - host.touchDownX
            val logicalDeltaX = logicalPoint?.first?.minus(host.touchDownLogicalX) ?: 0
            target.onSwipeEnd?.invoke(logicalDeltaX)
            invokeSwipe(target, rawDeltaX)
            host.activeSwipeTarget = null
            host.candidateSwipeTarget = null
            host.candidatePagerTarget = null
            host.candidateListTarget = null
            host.candidateRefreshTarget = null
            host.candidateTextInputTarget = null
            clearActiveSelectionHandle()
            recycleVelocityTracker()
            host.invalidate()
            return true
        }

        host.activePagerTarget?.let { target ->
            val velocityPxPerSecond = host.rawVelocityToLogical(host.velocityTracker, target.axis)
            target.controller.endDrag(target.state, host.pagerViewportSize(target), velocityPxPerSecond)
            host.activePagerTarget = null
            host.candidatePagerTarget = null
            host.candidateListTarget = null
            host.candidateRefreshTarget = null
            host.candidateTextInputTarget = null
            clearActiveSelectionHandle()
            recycleVelocityTracker()
            host.invalidate()
            return true
        }
        host.activeListTarget?.let { target ->
            val velocityPxPerSecond = host.rawVelocityToLogical(host.velocityTracker, PixelAxis.VERTICAL)
            target.controller.endDrag(target.state, velocityPxPerSecond, target.viewportHeightPx, target.contentHeightPx)
            host.activeListTarget = null
            host.candidateListTarget = null
            host.candidatePagerTarget = null
            host.candidateRefreshTarget = null
            host.candidateTextInputTarget = null
            clearActiveSelectionHandle()
            recycleVelocityTracker()
            host.invalidate()
            return true
        }

        host.candidatePagerTarget = null
        host.candidateListTarget = null
        host.candidateRefreshTarget = null
        host.candidateSwipeTarget = null
        if (!host.touchMoved && logicalPoint != null) {
            (host.candidateTextInputTarget ?: host.resolveTextInputTarget(logicalPoint.first, logicalPoint.second))?.let { target ->
                host.focusTextInput(target)
                val selection = PixelTextInputSelectionGesture.resolveSelection(
                    target,
                    logicalPoint.first,
                    logicalPoint.second,
                )
                val pressedMs = event.eventTime - event.downTime
                if (!target.readOnly && pressedMs >= LONG_PRESS_TIMEOUT_MS) {
                    target.controller.selectWordAt(target.state, selection)
                    host.hostBridge?.performHapticFeedback(PixelHapticType.LONG_PRESS)
                    host.showTextSelectionMenu(target)
                } else if (!target.readOnly &&
                    host.lastTextInputTapState === target.state &&
                    event.eventTime - host.lastTextInputTapTimeMs in 0L..DOUBLE_TAP_TIMEOUT_MS
                ) {
                    target.controller.selectWordAt(target.state, selection)
                    host.showTextSelectionMenu(target)
                } else if (!target.readOnly) {
                    PixelTextInputSelectionGesture.setCollapsedSelection(target, logicalPoint.first, logicalPoint.second)
                }
                host.lastTextInputTapState = target.state
                host.lastTextInputTapTimeMs = event.eventTime
                host.invalidate()
                host.candidateTextInputTarget = null
                recycleVelocityTracker()
                return true
            }
            val clickTarget = capturedClickTarget?.takeIf { target ->
                target.bounds.contains(logicalPoint.first, logicalPoint.second)
            }
            val pressedMs = event.eventTime - event.downTime
            /** 当前长按回调快照，确保条件判断与执行使用同一实例。 */
            val longPress = clickTarget?.onLongPress
            if (pressedMs >= LONG_PRESS_TIMEOUT_MS && longPress != null) {
                host.cancelPendingClick()
                host.lastClickTapSource = null
                host.lastClickTapTimeMs = -1L
                host.hostBridge?.performHapticFeedback(PixelHapticType.LONG_PRESS)
                longPress.invoke()
            } else {
                handleClickTargetTap(clickTarget, event)
            }
            host.invalidate()
        }
        host.candidateTextInputTarget = null
        clearActiveSelectionHandle()
        recycleVelocityTracker()
        return true
    }

    private fun handleClickTargetTap(clickTarget: PixelClickTarget?, event: MotionEvent) {
        if (clickTarget == null) {
            host.cancelPendingClick()
            return
        }
        val source = clickTarget.source
        val isDoubleTap = clickTarget.onDoubleTap != null &&
            host.lastClickTapSource === source &&
            event.eventTime - host.lastClickTapTimeMs in 0L..DOUBLE_TAP_TIMEOUT_MS
        if (isDoubleTap) {
            host.cancelPendingClick()
            host.lastClickTapSource = null
            host.lastClickTapTimeMs = -1L
            clickTarget.onDoubleTap?.invoke()
            return
        }
        host.lastClickTapSource = source
        host.lastClickTapTimeMs = event.eventTime
        if (clickTarget.onDoubleTap != null) {
            host.schedulePendingClick(clickTarget, DOUBLE_TAP_TIMEOUT_MS)
        } else {
            host.cancelPendingClick()
            clickTarget.onClick.invoke()
        }
    }

    private fun onCancel(): Boolean {
        host.capturedClickTarget = null
        clearPressedClickTarget()
        host.activeSliderTarget?.onPressedChanged?.invoke(false)
        host.activeSliderTarget = null
        host.activeSwipeTarget?.onSwipeEnd?.invoke(0)
        host.activeSwipeTarget = null
        host.candidateSwipeTarget = null
        host.activeRefreshTarget?.let { target ->
            target.onPressedChanged?.invoke(false)
            cancelRefreshPull(target)
        }
        host.activeRefreshTarget = null
        host.candidateRefreshTarget = null
        host.activeScrollbarTarget?.let { target ->
            target.onPressedChanged?.invoke(false)
            endScrollbarDrag(target)
        }
        host.activeScrollbarTarget = null
        clearActiveSelectionHandle()
        host.activePagerTarget?.let { target ->
            target.controller.cancelDrag(target.state)
            host.invalidate()
        }
        host.activeListTarget?.let { target ->
            target.controller.endDrag(target.state, 0f, target.viewportHeightPx, target.contentHeightPx)
        }
        host.nestedScrollSession.resetGesture()
        clearHoveredInteraction()
        recycleVelocityTracker()
        return true
    }

    /** Clears click pressed feedback exactly once for the active pointer sequence. */
    private fun clearPressedClickTarget() {
        val target = host.activePressedClickTarget ?: return
        host.activePressedClickTarget = null
        target.onPressedChanged?.invoke(false)
        host.invalidate()
    }

    /** Updates hover ownership using slider, scrollbar, click, then refresh precedence. */
    private fun updateHover(event: MotionEvent): Boolean? {
        val logicalPoint = host.mapTouchToLogical(event.x, event.y)
        if (logicalPoint == null) return clearHoveredInteraction()
        /** Highest-priority compact value-control target under the pointer. */
        val sliderTarget = host.lastRenderResult
            ?.sliderTargets
            ?.lastOrNull { target ->
                target.bounds.contains(logicalPoint.first, logicalPoint.second) &&
                    target.onHoveredChanged != null
            }
        /** Overlay scrollbar target considered only when no slider owns the point. */
        val scrollbarTarget = if (sliderTarget == null) {
            host.lastRenderResult
                ?.scrollbarTargets
                ?.lastOrNull { target ->
                    target.bounds.contains(logicalPoint.first, logicalPoint.second) &&
                        target.onHoveredChanged != null
                }
        } else {
            null
        }
        /** Child click target takes precedence over its enclosing refresh boundary. */
        val clickTarget = if (sliderTarget == null && scrollbarTarget == null) {
            host.lastRenderResult
                ?.clickTargets
                ?.lastOrNull { target ->
                    target.bounds.contains(logicalPoint.first, logicalPoint.second) &&
                        target.onHoveredChanged != null
                }
        } else {
            null
        }
        /** Lowest-priority enclosing refresh boundary under otherwise passive content. */
        val refreshTarget = if (sliderTarget == null && scrollbarTarget == null && clickTarget == null) {
            host.lastRenderResult
                ?.refreshTargets
                ?.lastOrNull { target ->
                    target.bounds.contains(logicalPoint.first, logicalPoint.second) &&
                        target.onHoveredChanged != null
                }
        } else {
            null
        }

        var changed = false
        if (!sameSliderTarget(host.hoveredSliderTarget, sliderTarget)) {
            host.hoveredSliderTarget?.onHoveredChanged?.invoke(false)
            host.hoveredSliderTarget = sliderTarget
            sliderTarget?.onHoveredChanged?.invoke(true)
            changed = true
        } else if (sliderTarget != null) {
            host.hoveredSliderTarget = sliderTarget
        }
        if (!sameScrollbarTarget(host.hoveredScrollbarTarget, scrollbarTarget)) {
            host.hoveredScrollbarTarget?.onHoveredChanged?.invoke(false)
            host.hoveredScrollbarTarget = scrollbarTarget
            scrollbarTarget?.onHoveredChanged?.invoke(true)
            changed = true
        } else if (scrollbarTarget != null) {
            host.hoveredScrollbarTarget = scrollbarTarget
        }
        if (!sameClickTarget(host.hoveredClickTarget, clickTarget)) {
            host.hoveredClickTarget?.onHoveredChanged?.invoke(false)
            host.hoveredClickTarget = clickTarget
            clickTarget?.onHoveredChanged?.invoke(true)
            changed = true
        } else if (clickTarget != null) {
            host.hoveredClickTarget = clickTarget
        }
        if (!sameRefreshTarget(host.hoveredRefreshTarget, refreshTarget)) {
            host.hoveredRefreshTarget?.onHoveredChanged?.invoke(false)
            host.hoveredRefreshTarget = refreshTarget
            refreshTarget?.onHoveredChanged?.invoke(true)
            changed = true
        } else if (refreshTarget != null) {
            host.hoveredRefreshTarget = refreshTarget
        }
        /** Whether any component feedback channel owns the current pointer. */
        val ownsHover = sliderTarget != null || scrollbarTarget != null ||
            clickTarget != null || refreshTarget != null
        hoveredLogicalPoint = if (ownsHover) logicalPoint else null
        if (changed) host.invalidate()
        return if (ownsHover || changed) true else null
    }

    /** Clears every component hover channel and reports whether this router owned one. */
    private fun clearHoveredInteraction(): Boolean? {
        val sliderTarget = host.hoveredSliderTarget
        val scrollbarTarget = host.hoveredScrollbarTarget
        val clickTarget = host.hoveredClickTarget
        val refreshTarget = host.hoveredRefreshTarget
        if (sliderTarget == null && scrollbarTarget == null && clickTarget == null && refreshTarget == null) {
            return null
        }
        host.hoveredSliderTarget = null
        host.hoveredScrollbarTarget = null
        host.hoveredClickTarget = null
        host.hoveredRefreshTarget = null
        hoveredLogicalPoint = null
        sliderTarget?.onHoveredChanged?.invoke(false)
        scrollbarTarget?.onHoveredChanged?.invoke(false)
        clickTarget?.onHoveredChanged?.invoke(false)
        refreshTarget?.onHoveredChanged?.invoke(false)
        host.invalidate()
        return true
    }

    /** Compares retained target identity across render-result snapshot replacement. */
    private fun sameClickTarget(first: PixelClickTarget?, second: PixelClickTarget?): Boolean {
        if (first == null || second == null) return first == null && second == null
        return if (first.source != null && second.source != null) {
            first.source === second.source
        } else {
            first === second
        }
    }

    /** Compares retained slider identity across render-result snapshot replacement. */
    private fun sameSliderTarget(first: PixelSliderTarget?, second: PixelSliderTarget?): Boolean {
        if (first == null || second == null) return first == null && second == null
        return if (first.source != null && second.source != null) {
            first.source === second.source
        } else {
            first === second
        }
    }

    /** Compares retained scrollbar identity across render-result snapshot replacement. */
    private fun sameScrollbarTarget(first: PixelScrollbarTarget?, second: PixelScrollbarTarget?): Boolean {
        if (first == null || second == null) return first == null && second == null
        return if (first.source != null && second.source != null) {
            first.source === second.source
        } else {
            first.state === second.state
        }
    }

    /** Compares retained refresh identity across render-result snapshot replacement. */
    private fun sameRefreshTarget(first: PixelRefreshTarget?, second: PixelRefreshTarget?): Boolean {
        if (first == null || second == null) return first == null && second == null
        return if (first.source != null && second.source != null) {
            first.source === second.source
        } else {
            first.state === second.state
        }
    }

    /** Finds the current click snapshot for [previous] without falling through to overlapping UI. */
    private fun List<PixelClickTarget>.findSameClickTarget(previous: PixelClickTarget): PixelClickTarget? {
        return lastOrNull { candidate -> sameClickTarget(previous, candidate) }
    }

    /** Finds the current slider snapshot for [previous] by retained render source identity. */
    private fun List<PixelSliderTarget>.findSameSliderTarget(previous: PixelSliderTarget): PixelSliderTarget? {
        return lastOrNull { candidate -> sameSliderTarget(previous, candidate) }
    }

    /** Finds the current scrollbar snapshot by retained render source or controlled state. */
    private fun List<PixelScrollbarTarget>.findSameScrollbarTarget(
        previous: PixelScrollbarTarget,
    ): PixelScrollbarTarget? {
        return lastOrNull { candidate -> sameScrollbarTarget(previous, candidate) }
    }

    /** Finds the current refresh snapshot by retained render source or controlled state. */
    private fun List<PixelRefreshTarget>.findSameRefreshTarget(
        previous: PixelRefreshTarget,
    ): PixelRefreshTarget? {
        return lastOrNull { candidate -> sameRefreshTarget(previous, candidate) }
    }

    /** Accepts physical pointer hover sources while excluding touchscreen accessibility hover. */
    private fun isMouseOrStylus(event: MotionEvent): Boolean {
        return event.isFromSource(android.view.InputDevice.SOURCE_MOUSE) ||
            event.isFromSource(android.view.InputDevice.SOURCE_STYLUS)
    }

    private fun isHorizontalSwipe(rawDeltaX: Float, rawDeltaY: Float): Boolean {
        val absX = abs(rawDeltaX)
        val absY = abs(rawDeltaY)
        return absX >= host.touchSlop && absX > absY
    }

    private fun swipeCallback(target: PixelClickTarget, rawDeltaX: Float): (() -> Unit)? {
        return when {
            rawDeltaX < 0f -> target.onSwipeLeft
            rawDeltaX > 0f -> target.onSwipeRight
            else -> null
        }
    }

    private fun invokeSwipe(target: PixelClickTarget, rawDeltaX: Float) {
        swipeCallback(target, rawDeltaX)?.invoke()
    }

    private fun updateScrollbarDrag(target: PixelScrollbarTarget, logicalY: Int) {
        val thumbTravel = (target.bounds.height - target.thumbBounds.height).coerceAtLeast(0)
        val maxOffset = (target.contentHeightPx - target.viewportHeightPx).coerceAtLeast(0)
        if (thumbTravel <= 0 || maxOffset <= 0) return
        val thumbTop = (logicalY - target.bounds.top - host.scrollbarDragThumbOffsetY).coerceIn(0, thumbTravel)
        val targetOffset = (thumbTop.toFloat() / thumbTravel.toFloat()) * maxOffset.toFloat()
        target.controller.startDrag(target.state)
        target.controller.scrollTo(target.state, targetOffset, target.viewportHeightPx, target.contentHeightPx)
    }

    /** Ends one scrollbar drag without introducing fling after release or target removal. */
    private fun endScrollbarDrag(target: PixelScrollbarTarget) {
        target.controller.endDrag(
            target.state,
            0f,
            target.viewportHeightPx,
            target.contentHeightPx,
        )
    }

    /** Cancels one pull below threshold so removal can never synthesize a refresh callback. */
    private fun cancelRefreshPull(target: PixelRefreshTarget) {
        if (target.state.isRefreshing) return
        target.controller.updatePull(target.state, 0f, target.thresholdPx)
        target.controller.endPull(target.state, target.thresholdPx)
    }

    private fun updateSelectionHandle(target: PixelTextInputTarget, logicalX: Int, logicalY: Int) {
        if (target.readOnly) return
        host.focusTextInput(target)
        PixelTextInputSelectionGesture.dragHandle(
            target = target,
            handle = host.activeTextInputSelectionHandle,
            logicalX = logicalX,
            logicalY = logicalY,
        )
    }

    private fun clearActiveSelectionHandle() {
        host.activeTextInputSelectionTarget = null
        host.activeTextInputSelectionHandle = null
    }

    private fun recycleVelocityTracker() {
        host.velocityTracker?.recycle()
        host.velocityTracker = null
    }

    private companion object {
        const val DOUBLE_TAP_TIMEOUT_MS = 300L
        const val LONG_PRESS_TIMEOUT_MS = 500L
    }
}
