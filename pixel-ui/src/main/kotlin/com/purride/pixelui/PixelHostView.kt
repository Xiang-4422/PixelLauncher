package com.purride.pixelui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelFrameView
import com.purride.pixelcore.PixelGridGeometryResolver
import com.purride.pixelcore.PixelPalette
import com.purride.pixelcore.PixelShape
import com.purride.pixelcore.PixelTone
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelcore.ScreenProfileFactory
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.internal.PixelClickTarget
import com.purride.pixelui.internal.NestedScrollGesturePolicy
import com.purride.pixelui.internal.PagerGesturePolicy
import com.purride.pixelui.internal.PixelPagerTarget
import com.purride.pixelui.internal.PixelRenderResult
import com.purride.pixelui.internal.PixelRenderRuntime
import com.purride.pixelui.internal.PixelListTarget
import com.purride.pixelui.internal.PixelTextInputTarget
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * `pixel-ui` 的最小宿主 View。
 *
 * 第一版不做 retained tree 和复杂增量渲染，而是以“每帧重建组件树 + 轻量布局绘制”的方式
 * 打通最小可运行链路，优先验证 API 与分层是否成立。
 */
class PixelHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs), PixelFrameView {

    override var interactionListener: PixelFrameView.InteractionListener? = null

    var screenProfile: ScreenProfile = ScreenProfile(
        logicalWidth = 96,
        logicalHeight = 96,
        dotSizePx = 8,
    )
        set(value) {
            field = value
            invalidate()
        }

    /**
     * 宿主显示偏好。
     *
     * 当业务层只关心“点大小和像素形状”时，可以设置这个偏好，把真正的
     * 全屏 `ScreenProfile` 推导交给 `PixelHostView` 自己完成。
     */
    var profilePreference: PixelHostProfilePreference? = null
        set(value) {
            field = value
            updateScreenProfileFromPreference()
        }

    private var runtime = PixelRenderRuntime()
    private var contentProvider: (() -> Widget)? = null
    private var lastRenderResult: PixelRenderResult? = null
    private var palette: PixelPalette = PixelPalette.terminalGreen()
    private var pixelGapEnabled: Boolean = true
    private var lastFrameUptimeMs: Long = 0L
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownLogicalX = 0
    private var touchDownLogicalY = 0
    private var lastPagerLogicalX = 0
    private var lastPagerLogicalY = 0
    private var lastListLogicalY = 0
    private var touchMoved = false
    private var candidatePagerTarget: PixelPagerTarget? = null
    private var activePagerTarget: PixelPagerTarget? = null
    private var candidateListTarget: PixelListTarget? = null
    private var activeListTarget: PixelListTarget? = null
    private var focusedTextInputTarget: PixelTextInputTarget? = null

    var hostBridge: PixelHostBridge? = null

    /**
     * 当前宿主使用的文本栅格器。
     *
     * 默认继续使用内置位图字体，但 demo 或后续业务层可以在不改 runtime 的情况下
     * 注入另一套文本实现。
     */
    var textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default
        set(value) {
            field = value
            runtime = PixelRenderRuntime(textRasterizer = value)
            invalidate()
        }

    private val onPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val offPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val reusableDiamondPath = Path()

    /**
     * 设置宿主当前要渲染的组件树。
     *
     * 公开边界已经提升到 `Widget`，这样页面层可以按 Flutter 风格组织；
     * 当前 runtime 仍然走 `PixelNode` 兼容层，所以这里会在真正渲染前做一次受控映射。
     */
    fun setContent(provider: () -> Widget) {
        contentProvider = provider
        invalidate()
    }

    fun requestRender() {
        invalidate()
    }

    fun updateFocusedTextInput(
        text: String,
        selectionStart: Int = text.length,
        selectionEnd: Int = selectionStart,
    ) {
        val target = focusedTextInputTarget ?: return
        if (target.readOnly) {
            return
        }
        target.controller.updateText(
            state = target.state,
            text = text,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
        )
        target.onChanged?.invoke(text)
        invalidate()
    }

    fun clearFocusedTextInput() {
        val target = focusedTextInputTarget ?: return
        target.controller.blur(target.state)
        focusedTextInputTarget = null
        hostBridge?.hideTextInput()
        invalidate()
    }

    fun submitFocusedTextInput() {
        val target = focusedTextInputTarget ?: return
        target.onSubmitted?.invoke(target.state.text)
        invalidate()
    }

    override fun submitFrame(pixelBuffer: PixelBuffer, screenProfile: ScreenProfile, palette: PixelPalette) {
        this.screenProfile = screenProfile
        this.palette = palette
        lastRenderResult = PixelRenderResult(
            buffer = pixelBuffer,
            clickTargets = emptyList(),
            pagerTargets = emptyList(),
            listTargets = emptyList(),
            textInputTargets = emptyList(),
        )
        invalidate()
    }

    override fun setPalette(palette: PixelPalette) {
        this.palette = palette
        invalidate()
    }

    override fun setPixelGapEnabled(enabled: Boolean) {
        pixelGapEnabled = enabled
        invalidate()
    }

    override fun asView(): View = this

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val frameDeltaMs = consumeFrameDeltaMs()
        stepActivePagers(frameDeltaMs)
        stepActiveLists(frameDeltaMs)
        val provider = contentProvider
        val renderResult = if (provider != null) {
            runtime.render(
                root = provider().asPixelNodeForCurrentRuntime(),
                logicalWidth = screenProfile.logicalWidth,
                logicalHeight = screenProfile.logicalHeight,
            )
        } else {
            lastRenderResult
        }

        if (renderResult == null) {
            canvas.drawColor(palette.backgroundColor)
            return
        }
        lastRenderResult = renderResult
        dispatchPageChanged(renderResult.pagerTargets)
        syncRequestedTextInputFocus(renderResult.textInputTargets)
        drawBuffer(canvas, renderResult.buffer)
        if (renderResult.pagerTargets.any { target -> target.controller.isActive(target.state) } ||
            renderResult.listTargets.any { target -> target.controller.isActive(target.state) }
        ) {
            postInvalidateOnAnimation()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == oldw && h == oldh) {
            return
        }
        updateScreenProfileFromPreference()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
                touchDownX = event.x
                touchDownY = event.y
                touchMoved = false
                val logicalPoint = mapTouchToLogical(event.x, event.y) ?: return true
                touchDownLogicalX = logicalPoint.first
                touchDownLogicalY = logicalPoint.second
                lastPagerLogicalX = logicalPoint.first
                lastPagerLogicalY = logicalPoint.second
                lastListLogicalY = logicalPoint.second
                candidatePagerTarget = lastRenderResult
                    ?.pagerTargets
                    ?.lastOrNull { target -> target.bounds.contains(logicalPoint.first, logicalPoint.second) }
                candidateListTarget = lastRenderResult
                    ?.listTargets
                    ?.lastOrNull { target -> target.bounds.contains(logicalPoint.first, logicalPoint.second) }
                val textInputTarget = lastRenderResult
                    ?.textInputTargets
                    ?.lastOrNull { target -> target.bounds.contains(logicalPoint.first, logicalPoint.second) }
                if (textInputTarget == null && focusedTextInputTarget != null) {
                    clearFocusedTextInput()
                }
                activePagerTarget = null
                activeListTarget = null
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val logicalPoint = mapTouchToLogical(event.x, event.y) ?: return true
                val rawDeltaX = event.x - touchDownX
                val rawDeltaY = event.y - touchDownY
                if (abs(rawDeltaX) > touchSlop || abs(rawDeltaY) > touchSlop) {
                    touchMoved = true
                }
                activePagerTarget?.let { target ->
                    val deltaPx = when (target.axis) {
                        PixelAxis.HORIZONTAL -> logicalPoint.first - lastPagerLogicalX
                        PixelAxis.VERTICAL -> logicalPoint.second - lastPagerLogicalY
                    }.toFloat()
                    target.controller.dragBy(
                        state = target.state,
                        deltaPx = deltaPx,
                        viewportSizePx = pagerViewportSize(target),
                    )
                    lastPagerLogicalX = logicalPoint.first
                    lastPagerLogicalY = logicalPoint.second
                    invalidate()
                    return true
                }
                activeListTarget?.let { target ->
                    // 列表第一版只支持纵向拖动。
                    // 但当列表已经滑到边界时，会尝试把同一次手势接力给外层纵向分页。
                    val deltaPx = (logicalPoint.second - lastListLogicalY).toFloat()
                    val listCanConsumeDrag = target.controller.canConsumeDrag(
                        state = target.state,
                        deltaPx = deltaPx,
                        viewportHeightPx = target.viewportHeightPx,
                        contentHeightPx = target.contentHeightPx,
                    )
                    if (listCanConsumeDrag) {
                        target.controller.dragBy(
                            state = target.state,
                            deltaPx = deltaPx,
                            viewportHeightPx = target.viewportHeightPx,
                            contentHeightPx = target.contentHeightPx,
                        )
                        lastListLogicalY = logicalPoint.second
                        invalidate()
                        return true
                    }

                    val pagerTarget = candidatePagerTarget
                    if (pagerTarget != null &&
                        NestedScrollGesturePolicy.shouldHandOffListToPager(
                            pagerAxis = pagerTarget.axis,
                            listCanConsumeDrag = listCanConsumeDrag,
                            deltaPx = deltaPx,
                        )
                    ) {
                        activeListTarget = null
                        activePagerTarget = pagerTarget
                        candidatePagerTarget = null
                        candidateListTarget = null
                        pagerTarget.controller.startDrag(pagerTarget.state)
                        lastPagerLogicalX = logicalPoint.first
                        lastPagerLogicalY = lastListLogicalY
                        pagerTarget.controller.dragBy(
                            state = pagerTarget.state,
                            deltaPx = deltaPx,
                            viewportSizePx = pagerViewportSize(pagerTarget),
                        )
                        lastPagerLogicalX = logicalPoint.first
                        lastPagerLogicalY = logicalPoint.second
                        invalidate()
                        return true
                    }

                    lastListLogicalY = logicalPoint.second
                    return true
                }
                candidatePagerTarget?.let { target ->
                    val pagerWantsDrag = PagerGesturePolicy.shouldStartDrag(
                        axis = target.axis,
                        deltaX = rawDeltaX,
                        deltaY = rawDeltaY,
                        touchSlopPx = touchSlop,
                    )
                    val listWantsDrag = candidateListTarget?.let {
                        shouldStartListDrag(rawDeltaX = rawDeltaX, rawDeltaY = rawDeltaY)
                    } ?: false
                    val listCanConsumeDrag = candidateListTarget?.let { listTarget ->
                        listTarget.controller.canConsumeDrag(
                            state = listTarget.state,
                            deltaPx = rawDeltaY,
                            viewportHeightPx = listTarget.viewportHeightPx,
                            contentHeightPx = listTarget.contentHeightPx,
                        )
                    } ?: false
                    val shouldDeferToList = NestedScrollGesturePolicy.shouldDeferPagerToList(
                        pagerAxis = target.axis,
                        pagerWantsDrag = pagerWantsDrag,
                        listWantsDrag = listWantsDrag,
                        listCanConsumeDrag = listCanConsumeDrag,
                    )
                    if (pagerWantsDrag && !shouldDeferToList) {
                        activePagerTarget = target
                        candidatePagerTarget = null
                        target.controller.startDrag(target.state)
                        val initialDeltaPx = when (target.axis) {
                            PixelAxis.HORIZONTAL -> logicalPoint.first - touchDownLogicalX
                            PixelAxis.VERTICAL -> logicalPoint.second - touchDownLogicalY
                        }.toFloat()
                        if (initialDeltaPx != 0f) {
                            target.controller.dragBy(
                                state = target.state,
                                deltaPx = initialDeltaPx,
                                viewportSizePx = pagerViewportSize(target),
                            )
                        }
                        lastPagerLogicalX = logicalPoint.first
                        lastPagerLogicalY = logicalPoint.second
                        candidateListTarget = null
                        invalidate()
                    }
                }
                candidateListTarget?.let { target ->
                    if (shouldStartListDrag(rawDeltaX = rawDeltaX, rawDeltaY = rawDeltaY)) {
                        activeListTarget = target
                        candidateListTarget = null
                        target.controller.startDrag(target.state)
                        val initialDeltaPx = (logicalPoint.second - touchDownLogicalY).toFloat()
                        if (initialDeltaPx != 0f) {
                            target.controller.dragBy(
                                state = target.state,
                                deltaPx = initialDeltaPx,
                                viewportHeightPx = target.viewportHeightPx,
                                contentHeightPx = target.contentHeightPx,
                            )
                        }
                        lastListLogicalY = logicalPoint.second
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val logicalPoint = mapTouchToLogical(event.x, event.y)

                activePagerTarget?.let { target ->
                    val velocityPxPerSecond = rawVelocityToLogical(velocityTracker, target.axis)
                    target.controller.endDrag(
                        state = target.state,
                        viewportSizePx = pagerViewportSize(target),
                        velocityPxPerSecond = velocityPxPerSecond,
                    )
                    activePagerTarget = null
                    candidatePagerTarget = null
                    candidateListTarget = null
                    velocityTracker?.recycle()
                    velocityTracker = null
                    invalidate()
                    return true
                }

                activeListTarget?.let { target ->
                    val velocityPxPerSecond = rawVelocityToLogical(velocityTracker, PixelAxis.VERTICAL)
                    target.controller.endDrag(
                        state = target.state,
                        velocityPxPerSecond = velocityPxPerSecond,
                        viewportHeightPx = target.viewportHeightPx,
                        contentHeightPx = target.contentHeightPx,
                    )
                    activeListTarget = null
                    candidateListTarget = null
                    candidatePagerTarget = null
                    velocityTracker?.recycle()
                    velocityTracker = null
                    invalidate()
                    return true
                }

                candidatePagerTarget = null
                candidateListTarget = null
                if (!touchMoved && logicalPoint != null) {
                    resolveTextInputTarget(logicalPoint.first, logicalPoint.second)?.let { target ->
                        focusTextInput(target)
                        invalidate()
                        return true
                    }
                    resolveClickTarget(logicalPoint.first, logicalPoint.second)?.onClick?.invoke()
                    invalidate()
                }
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                activePagerTarget?.let { target ->
                    target.controller.cancelDrag(target.state)
                    invalidate()
                }
                activeListTarget?.let { target ->
                    target.controller.endDrag(
                        state = target.state,
                        velocityPxPerSecond = 0f,
                        viewportHeightPx = target.viewportHeightPx,
                        contentHeightPx = target.contentHeightPx,
                    )
                }
                candidatePagerTarget = null
                activePagerTarget = null
                candidateListTarget = null
                activeListTarget = null
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateScreenProfileFromPreference() {
        val preference = profilePreference ?: return
        if (width <= 0 || height <= 0) {
            return
        }
        screenProfile = ScreenProfileFactory.create(
            widthPx = width,
            heightPx = height,
            dotSizePx = preference.dotSizePx,
            pixelShape = preference.pixelShape,
        )
    }

    private fun consumeFrameDeltaMs(): Long {
        val now = SystemClock.uptimeMillis()
        val deltaMs = if (lastFrameUptimeMs == 0L) 16L else (now - lastFrameUptimeMs).coerceAtLeast(1L)
        lastFrameUptimeMs = now
        return deltaMs
    }

    private fun stepActivePagers(deltaMs: Long) {
        lastRenderResult?.pagerTargets?.forEach { target ->
            target.controller.step(
                state = target.state,
                deltaMs = deltaMs,
            )
        }
    }

    private fun dispatchPageChanged(targets: List<PixelPagerTarget>) {
        targets.forEach { target ->
            val currentPage = target.state.currentPage
            if (currentPage != target.state.lastDispatchedPage) {
                target.state.lastDispatchedPage = currentPage
                target.onPageChanged?.invoke(currentPage)
            }
        }
    }

    private fun stepActiveLists(deltaMs: Long) {
        lastRenderResult?.listTargets?.forEach { target ->
            target.controller.step(
                state = target.state,
                deltaMs = deltaMs,
                viewportHeightPx = target.viewportHeightPx,
                contentHeightPx = target.contentHeightPx,
            )
        }
    }

    private fun resolveClickTarget(logicalX: Int, logicalY: Int): PixelClickTarget? {
        return lastRenderResult
            ?.clickTargets
            ?.lastOrNull { target -> target.bounds.contains(logicalX, logicalY) }
    }

    private fun resolveTextInputTarget(logicalX: Int, logicalY: Int): PixelTextInputTarget? {
        return lastRenderResult
            ?.textInputTargets
            ?.lastOrNull { target -> target.bounds.contains(logicalX, logicalY) }
    }

    private fun syncRequestedTextInputFocus(targets: List<PixelTextInputTarget>) {
        val blurTarget = focusedTextInputTarget?.takeIf { it.state.blurRequested }
        if (blurTarget != null) {
            blurTarget.state.blurRequested = false
            clearFocusedTextInput()
            return
        }

        val requestedTarget = targets.lastOrNull { target -> target.state.focusRequested }
        if (requestedTarget != null) {
            requestedTarget.state.focusRequested = false
            focusTextInput(requestedTarget)
        }
    }

    private fun focusTextInput(target: PixelTextInputTarget) {
        if (focusedTextInputTarget?.state !== target.state) {
            focusedTextInputTarget?.let { previous ->
                previous.controller.blur(previous.state)
            }
        }
        target.controller.focus(target.state)
        focusedTextInputTarget = target
        hostBridge?.showTextInput(
            PixelTextInputRequest(
                text = target.state.text,
                selectionStart = target.state.selectionStart,
                selectionEnd = target.state.selectionEnd,
                readOnly = target.readOnly,
            ),
        )
    }

    private fun pagerViewportSize(target: PixelPagerTarget): Int {
        return when (target.axis) {
            PixelAxis.HORIZONTAL -> target.bounds.width
            PixelAxis.VERTICAL -> target.bounds.height
        }.coerceAtLeast(1)
    }

    private fun shouldStartListDrag(rawDeltaX: Float, rawDeltaY: Float): Boolean {
        return abs(rawDeltaY) > touchSlop && abs(rawDeltaY) >= abs(rawDeltaX)
    }

    private fun rawVelocityToLogical(velocityTracker: VelocityTracker?, axis: PixelAxis): Float {
        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = width,
            viewHeight = height,
            profile = screenProfile,
            pixelGapEnabled = pixelGapEnabled,
        ) ?: return 0f
        val rawVelocity = when (axis) {
            PixelAxis.HORIZONTAL -> velocityTracker?.xVelocity ?: 0f
            PixelAxis.VERTICAL -> velocityTracker?.yVelocity ?: 0f
        }
        return rawVelocity / geometry.cellSize.coerceAtLeast(1f)
    }

    private fun drawBuffer(canvas: Canvas, buffer: PixelBuffer) {
        canvas.drawColor(palette.backgroundColor)
        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = width,
            viewHeight = height,
            profile = screenProfile,
            pixelGapEnabled = pixelGapEnabled,
        ) ?: return

        onPaint.color = palette.pixelOnColor
        accentPaint.color = palette.accentColor
        offPaint.color = palette.pixelOffColor

        for (y in 0 until buffer.height) {
            for (x in 0 until buffer.width) {
                val left = geometry.originX + (x * geometry.cellSize) + geometry.dotInset
                val top = geometry.originY + (y * geometry.cellSize) + geometry.dotInset
                val right = left + geometry.dotSize
                val bottom = top + geometry.dotSize
                val paint = when (buffer.getPixel(x, y)) {
                    PixelTone.ON.value -> onPaint
                    PixelTone.ACCENT.value -> accentPaint
                    else -> offPaint
                }

                when (screenProfile.pixelShape) {
                    PixelShape.SQUARE -> canvas.drawRect(left, top, right, bottom, paint)
                    PixelShape.CIRCLE -> {
                        val centerX = (left + right) / 2f
                        val centerY = (top + bottom) / 2f
                        val radius = min(right - left, bottom - top) / 2f
                        canvas.drawCircle(centerX, centerY, radius, paint)
                    }

                    PixelShape.DIAMOND -> {
                        val centerX = (left + right) / 2f
                        val centerY = (top + bottom) / 2f
                        reusableDiamondPath.reset()
                        reusableDiamondPath.moveTo(centerX, top)
                        reusableDiamondPath.lineTo(left, centerY)
                        reusableDiamondPath.lineTo(centerX, bottom)
                        reusableDiamondPath.lineTo(right, centerY)
                        reusableDiamondPath.close()
                        canvas.drawPath(reusableDiamondPath, paint)
                    }
                }
            }
        }
    }

    private fun mapTouchToLogical(touchX: Float, touchY: Float): Pair<Int, Int>? {
        return PixelGridGeometryResolver.mapSurfaceToLogical(
            touchX = touchX,
            touchY = touchY,
            viewWidth = width,
            viewHeight = height,
            profile = screenProfile,
            pixelGapEnabled = pixelGapEnabled,
        )
    }

    /**
     * 当前 runtime 还没有完全切到 `Widget / Element / RenderObject`，
     * 所以这里先把公开 `Widget` 边界映射回兼容层 `PixelNode`。
     */
    private fun Widget.asPixelNodeForCurrentRuntime(): PixelNode {
        return this as? PixelNode
            ?: error("当前 PixelHostView 仍依赖 PixelNode 兼容层，收到未兼容的 Widget 类型: ${this::class.qualifiedName}")
    }
}
