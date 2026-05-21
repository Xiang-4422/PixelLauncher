package com.purride.pixelui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelFrameView
import com.purride.pixelcore.PixelGridGeometryResolver
import com.purride.pixelcore.PixelShape
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelcore.ScreenProfileFactory
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.internal.PixelClickTarget
import com.purride.pixelui.gesture.NestedScrollGesturePolicy
import com.purride.pixelui.gesture.PagerGesturePolicy
import com.purride.pixelui.host.PixelHostFrameLoop
import com.purride.pixelui.host.PixelFrameScheduler
import com.purride.pixelui.PixelScrollPhysics
import com.purride.pixelui.internal.PixelPagerTarget
import com.purride.pixelui.internal.PixelRenderResult
import com.purride.pixelui.internal.PixelListTarget
import com.purride.pixelui.internal.PixelSliderTarget
import com.purride.pixelui.internal.PixelTextInputTarget
import com.purride.pixelui.internal.HostRootWidget
import com.purride.pixelui.internal.NestedScrollSession
import com.purride.pixelui.internal.PixelUiRuntime
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * pixel-engine UI layer 的最小宿主 View。
 *
 * 引擎是纯像素渲染器——widget 树 → ARGB 像素网格。
 * 背景色通过 [backgroundColor] 属性控制；不再有 colorMode / palette / themeData。
 */
public class PixelHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs), PixelFrameView {

    override var interactionListener: PixelFrameView.InteractionListener? = null

    public var screenProfile: ScreenProfile = ScreenProfile(
        logicalWidth = 96,
        logicalHeight = 96,
        dotSizePx = 8,
    )
        set(value) {
            field = value
            invalidate()
        }

    public var profilePreference: PixelHostProfilePreference? = null
        set(value) {
            field = value
            updateScreenProfileFromPreference()
        }

    /**
     * 画布背景色。像素网格绘制在它之上（也是屏幕外 bezel 的颜色）。
     */
    public var backgroundColor: PixelColor = PixelColor.Black
        set(value) {
            field = value
            invalidate()
        }

    /**
     * 像素格栅色：
     * - 间隙开启时作为所有"熄灭"像素点的填色（B 方案），让格点矩阵可见
     * - 间隙关闭时作为内容区底色，区分屏幕 panel 与外部 bezel（A 方案）
     * 默认 #111111（极深灰），比 bezel 稍亮，不影响显眼颜色的对比度。
     */
    public var pixelGridColor: PixelColor = PixelColor.fromRgb(17, 17, 17)
        set(value) {
            field = value
            invalidate()
        }

    /** 是否启用暗角效果（Vignette）。 */
    public var vignetteEnabled: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /**
     * 暗角强度，0.0 = 无，1.0 = 最强（默认 0.6）。
     * 修改此值会使缓存 shader 失效。
     */
    public var vignetteStrength: Float = 0.6f
        set(value) {
            field = value.coerceIn(0f, 1f)
            cachedVignetteShader = null
            invalidate()
        }

    private var runtime = PixelUiRuntime(onVisualUpdate = { postInvalidateOnAnimation() })
    private var contentProvider: RootWidgetProvider? = null
    private var lastRenderResult: PixelRenderResult? = null
    private var pixelGapEnabled: Boolean = true
    private var pixelGapRatio: Float = 1.0f
    private var activeSliderTarget: PixelSliderTarget? = null
    private val frameLoop = PixelHostFrameLoop()
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
    private val nestedScrollSession = NestedScrollSession()
    private var candidatePagerTarget: PixelPagerTarget?
        get() = nestedScrollSession.candidatePagerTarget
        set(value) { nestedScrollSession.candidatePagerTarget = value }
    private var activePagerTarget: PixelPagerTarget?
        get() = nestedScrollSession.activePagerTarget
        set(value) { nestedScrollSession.activePagerTarget = value }
    private var candidateListTarget: PixelListTarget?
        get() = nestedScrollSession.candidateListTarget
        set(value) { nestedScrollSession.candidateListTarget = value }
    private var activeListTarget: PixelListTarget?
        get() = nestedScrollSession.activeListTarget
        set(value) { nestedScrollSession.activeListTarget = value }
    private var focusedTextInputTarget: PixelTextInputTarget?
        get() = nestedScrollSession.focusedTextInputTarget
        set(value) { nestedScrollSession.focusedTextInputTarget = value }

    public var hostBridge: PixelHostBridge? = null
    public var pagerGesturePolicy: PagerGesturePolicy = PagerGesturePolicy.Default
    public var nestedScrollPolicy: NestedScrollGesturePolicy = NestedScrollGesturePolicy.Default
    public var scrollPhysics: PixelScrollPhysics = PixelScrollPhysics.Default
    public var frameScheduler: PixelFrameScheduler = PixelFrameScheduler.Default

    public var textDirection: TextDirection = TextDirection.LTR
        set(value) {
            field = value
            invalidate()
        }

    public var textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default
        set(value) {
            if (field === value) return
            field = value
            invalidate()
        }

    private val reusablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val reusableDiamondPath = Path()
    private var reusableBitmap: Bitmap? = null
    private val reusableDestRect = Rect()
    // Vignette — lazily built RadialGradient, invalidated when geometry or strength changes
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var cachedVignetteShader: RadialGradient? = null
    private var cachedVignetteCx = Float.NaN
    private var cachedVignetteCy = Float.NaN
    private var cachedVignetteRadius = Float.NaN
    private var cachedVignetteAlpha = -1

    public fun setContent(provider: RootWidgetProvider) {
        contentProvider = provider
        postInvalidateOnAnimation()
    }

    internal fun requestRender() { invalidate() }

    public fun updateFocusedTextInput(
        text: String,
        selectionStart: Int = text.length,
        selectionEnd: Int = selectionStart,
    ) {
        val target = focusedTextInputTarget ?: return
        if (target.readOnly) return
        target.controller.updateText(
            state = target.state,
            text = text,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
        )
        target.onChanged?.invoke(text)
        invalidate()
    }

    public fun clearFocusedTextInput() {
        val target = focusedTextInputTarget ?: return
        target.controller.blur(target.state)
        nestedScrollSession.clearTextInputOwner()
        hostBridge?.hideTextInput()
        invalidate()
    }

    public fun submitFocusedTextInput() {
        val target = focusedTextInputTarget ?: return
        target.onSubmitted?.invoke(target.state.text)
        invalidate()
    }

    override fun submitFrame(pixelBuffer: PixelBuffer, screenProfile: ScreenProfile, backgroundColor: PixelColor) {
        this.screenProfile = screenProfile
        this.backgroundColor = backgroundColor
        lastRenderResult = PixelRenderResult(
            buffer = pixelBuffer,
            clickTargets = emptyList(),
            pagerTargets = emptyList(),
            listTargets = emptyList(),
            textInputTargets = emptyList(),
            sliderTargets = emptyList(),
        )
        invalidate()
    }

    override fun setPixelGapEnabled(enabled: Boolean) {
        pixelGapEnabled = enabled
        invalidate()
    }

    /**
     * 设置像素间隙大小比例（0.0 = 无间隙，1.0 = 最大间隙）。
     * 当 [setPixelGapEnabled] 为 false 时本值无效。
     */
    public fun setPixelGapRatio(ratio: Float) {
        pixelGapRatio = ratio.coerceIn(0f, 1f)
        invalidate()
    }

    override fun asView(): View = this

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val frameDeltaMs = frameLoop.consumeFrameDeltaMs()
        stepActivePagers(frameDeltaMs)
        stepActiveLists(frameDeltaMs)
        val provider = contentProvider
        val renderResult = if (provider != null) {
            val rootWidget = provider()
            val wrappedRoot = HostRootWidget(
                screenProfile = screenProfile,
                textDirection = textDirection,
                textRasterizer = textRasterizer,
                child = rootWidget,
                key = "host-root",
            )
            lastRenderResult = null
            runtime.render(
                root = wrappedRoot,
                logicalWidth = screenProfile.logicalWidth,
                logicalHeight = screenProfile.logicalHeight,
            )
        } else {
            lastRenderResult
        }

        canvas.drawColor(backgroundColor.argb)
        if (renderResult == null) return
        lastRenderResult = renderResult
        dispatchPageChanged(renderResult.pagerTargets)
        syncRequestedTextInputFocus(renderResult.textInputTargets)
        drawBuffer(canvas, renderResult.buffer)
        if (renderResult.pagerTargets.any { it.controller.isActive(it.state) } ||
            renderResult.listTargets.any { it.controller.isActive(it.state) }
        ) {
            postInvalidateOnAnimation()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == oldw && h == oldh) return
        updateScreenProfileFromPreference()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        runtime.dispose()
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
                activeSliderTarget = lastRenderResult
                    ?.sliderTargets
                    ?.lastOrNull { it.bounds.contains(logicalPoint.first, logicalPoint.second) }
                candidatePagerTarget = if (activeSliderTarget == null) {
                    lastRenderResult
                        ?.pagerTargets
                        ?.lastOrNull { it.bounds.contains(logicalPoint.first, logicalPoint.second) }
                } else null
                candidateListTarget = if (activeSliderTarget == null) {
                    lastRenderResult
                        ?.listTargets
                        ?.lastOrNull { it.bounds.contains(logicalPoint.first, logicalPoint.second) }
                } else null
                val textInputTarget = lastRenderResult
                    ?.textInputTargets
                    ?.lastOrNull { it.bounds.contains(logicalPoint.first, logicalPoint.second) }
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
                if (abs(rawDeltaX) > touchSlop || abs(rawDeltaY) > touchSlop) touchMoved = true

                activeSliderTarget?.let { target ->
                    val localX = logicalPoint.first - target.bounds.left
                    val ratio = (localX.toFloat() / target.bounds.width).coerceIn(0f, 1f)
                    target.onDrag(ratio)
                    invalidate()
                    return true
                }

                activePagerTarget?.let { target ->
                    val deltaPx = when (target.axis) {
                        PixelAxis.HORIZONTAL -> logicalPoint.first - lastPagerLogicalX
                        PixelAxis.VERTICAL -> logicalPoint.second - lastPagerLogicalY
                    }.toFloat()
                    target.controller.dragBy(target.state, deltaPx, pagerViewportSize(target))
                    lastPagerLogicalX = logicalPoint.first
                    lastPagerLogicalY = logicalPoint.second
                    invalidate()
                    return true
                }
                activeListTarget?.let { target ->
                    val deltaPx = (logicalPoint.second - lastListLogicalY).toFloat()
                    val listCanConsumeDrag = target.controller.canConsumeDrag(
                        target.state, deltaPx, target.viewportHeightPx, target.contentHeightPx,
                    )
                    if (listCanConsumeDrag) {
                        target.controller.dragBy(target.state, deltaPx, target.viewportHeightPx, target.contentHeightPx)
                        lastListLogicalY = logicalPoint.second
                        invalidate()
                        return true
                    }
                    val pagerTarget = candidatePagerTarget
                    if (pagerTarget != null &&
                        nestedScrollPolicy.shouldHandOffListToPager(pagerTarget.axis, listCanConsumeDrag, deltaPx)
                    ) {
                        activeListTarget = null
                        activePagerTarget = pagerTarget
                        candidatePagerTarget = null
                        candidateListTarget = null
                        pagerTarget.controller.startDrag(pagerTarget.state)
                        lastPagerLogicalX = logicalPoint.first
                        lastPagerLogicalY = lastListLogicalY
                        pagerTarget.controller.dragBy(pagerTarget.state, deltaPx, pagerViewportSize(pagerTarget))
                        lastPagerLogicalX = logicalPoint.first
                        lastPagerLogicalY = logicalPoint.second
                        invalidate()
                        return true
                    }
                    lastListLogicalY = logicalPoint.second
                    return true
                }
                candidatePagerTarget?.let { target ->
                    val pagerWantsDrag = pagerGesturePolicy.shouldStartDrag(target.axis, rawDeltaX, rawDeltaY, touchSlop)
                    val listWantsDrag = candidateListTarget?.let { shouldStartListDrag(rawDeltaX, rawDeltaY) } ?: false
                    val listCanConsumeDrag = candidateListTarget?.let { listTarget ->
                        listTarget.controller.canConsumeDrag(listTarget.state, rawDeltaY, listTarget.viewportHeightPx, listTarget.contentHeightPx)
                    } ?: false
                    val shouldDeferToList = nestedScrollPolicy.shouldDeferPagerToList(
                        target.axis, pagerWantsDrag, listWantsDrag, listCanConsumeDrag,
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
                            target.controller.dragBy(target.state, initialDeltaPx, pagerViewportSize(target))
                        }
                        lastPagerLogicalX = logicalPoint.first
                        lastPagerLogicalY = logicalPoint.second
                        candidateListTarget = null
                        invalidate()
                    }
                }
                candidateListTarget?.let { target ->
                    if (shouldStartListDrag(rawDeltaX, rawDeltaY)) {
                        activeListTarget = target
                        candidateListTarget = null
                        target.controller.startDrag(target.state)
                        val initialDeltaPx = (logicalPoint.second - touchDownLogicalY).toFloat()
                        if (initialDeltaPx != 0f) {
                            target.controller.dragBy(target.state, initialDeltaPx, target.viewportHeightPx, target.contentHeightPx)
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

                activeSliderTarget?.let { target ->
                    val localX = if (logicalPoint != null) logicalPoint.first - target.bounds.left
                                 else target.bounds.width / 2
                    val ratio = (localX.toFloat() / target.bounds.width).coerceIn(0f, 1f)
                    target.onRelease(ratio)
                    activeSliderTarget = null
                    invalidate()
                    return true
                }

                activePagerTarget?.let { target ->
                    val velocityPxPerSecond = rawVelocityToLogical(velocityTracker, target.axis)
                    target.controller.endDrag(target.state, pagerViewportSize(target), velocityPxPerSecond)
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
                    target.controller.endDrag(target.state, velocityPxPerSecond, target.viewportHeightPx, target.contentHeightPx)
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
                activeSliderTarget = null
                activePagerTarget?.let { target ->
                    target.controller.cancelDrag(target.state)
                    invalidate()
                }
                activeListTarget?.let { target ->
                    target.controller.endDrag(target.state, 0f, target.viewportHeightPx, target.contentHeightPx)
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
        if (width <= 0 || height <= 0) return
        screenProfile = ScreenProfileFactory.create(
            widthPx = width,
            heightPx = height,
            dotSizePx = preference.dotSizePx,
            pixelShape = preference.pixelShape,
        )
    }

    private fun stepActivePagers(deltaMs: Long) {
        lastRenderResult?.pagerTargets?.forEach { it.controller.step(it.state, deltaMs) }
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
            target.controller.step(target.state, deltaMs, target.viewportHeightPx, target.contentHeightPx)
        }
    }

    private fun resolveClickTarget(logicalX: Int, logicalY: Int): PixelClickTarget? {
        return lastRenderResult?.clickTargets?.lastOrNull { it.bounds.contains(logicalX, logicalY) }
    }

    private fun resolveTextInputTarget(logicalX: Int, logicalY: Int): PixelTextInputTarget? {
        return lastRenderResult?.textInputTargets?.lastOrNull { it.bounds.contains(logicalX, logicalY) }
    }

    private fun syncRequestedTextInputFocus(targets: List<PixelTextInputTarget>) {
        val blurTarget = focusedTextInputTarget?.takeIf { it.state.blurRequested }
        if (blurTarget != null) {
            blurTarget.state.blurRequested = false
            clearFocusedTextInput()
            return
        }
        val requestedTarget = targets.lastOrNull { it.state.focusRequested }
        if (requestedTarget != null) {
            requestedTarget.state.focusRequested = false
            focusTextInput(requestedTarget)
            requestedTarget.state.autofocusConsumed = true
            return
        }
        val autofocusTarget = targets.lastOrNull { it.autofocus && !it.state.autofocusConsumed && focusedTextInputTarget == null }
        if (autofocusTarget != null) {
            autofocusTarget.state.autofocusConsumed = true
            focusTextInput(autofocusTarget)
        }
    }

    private fun focusTextInput(target: PixelTextInputTarget) {
        if (focusedTextInputTarget?.state !== target.state) {
            focusedTextInputTarget?.let { it.controller.blur(it.state) }
        }
        target.controller.focus(target.state)
        nestedScrollSession.markTextInputOwner(target)
        hostBridge?.showTextInput(
            PixelTextInputRequest(
                text = target.state.text,
                selectionStart = target.state.selectionStart,
                selectionEnd = target.state.selectionEnd,
                readOnly = target.readOnly,
                minLines = target.minLines,
                maxLines = target.maxLines,
                inputType = target.inputType,
                action = target.action,
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
            pixelGapRatio = pixelGapRatio,
        ) ?: return 0f
        val rawVelocity = when (axis) {
            PixelAxis.HORIZONTAL -> velocityTracker?.xVelocity ?: 0f
            PixelAxis.VERTICAL -> velocityTracker?.yVelocity ?: 0f
        }
        return rawVelocity / geometry.cellSize.coerceAtLeast(1f)
    }

    /**
     * 把 ARGB PixelBuffer 渲染成 Android Canvas。
     *
     * 两条路径：
     * - 间隙开启：逐格绘制，熄灭格用 [pixelGridColor]（格点矩阵可见），点亮格用自身颜色
     * - 间隙关闭：先填充内容区底色，再用 Bitmap.setPixels + drawBitmap 快速绘制
     *
     * 两条路径完成后，若 [vignetteEnabled] 则叠加暗角。
     */
    private fun drawBuffer(canvas: Canvas, buffer: PixelBuffer) {
        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = width,
            viewHeight = height,
            profile = screenProfile,
            pixelGapEnabled = pixelGapEnabled,
            pixelGapRatio = pixelGapRatio,
        ) ?: return

        val bw = buffer.width
        val bh = buffer.height

        if (pixelGapEnabled) {
            // Gap path — draw every cell as a shaped dot.
            // Canvas was already cleared to backgroundColor by onDraw().
            drawPixelShapes(canvas, buffer, geometry)
        } else {
            // No-gap path — bitmap fast path.
            val existing = reusableBitmap
            val bitmap = if (existing != null && existing.width == bw && existing.height == bh) {
                existing
            } else {
                existing?.recycle()
                Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also { reusableBitmap = it }
            }
            bitmap.setPixels(buffer.pixels, 0, bw, 0, 0, bw, bh)

            val gridWidth = (bw * geometry.cellSize).toInt()
            val gridHeight = (bh * geometry.cellSize).toInt()
            reusableDestRect.set(
                geometry.originX.toInt(),
                geometry.originY.toInt(),
                geometry.originX.toInt() + gridWidth,
                geometry.originY.toInt() + gridHeight,
            )
            // Fill content area with pixelGridColor so transparent pixels aren't bare-black
            fillContentArea(canvas, geometry)
            canvas.drawBitmap(bitmap, null, reusableDestRect, null)
        }

        if (vignetteEnabled) {
            drawVignette(canvas, geometry)
        }
    }

    /** 用 [pixelGridColor] 填充逻辑像素内容区（screen panel 底色）。 */
    private fun fillContentArea(canvas: Canvas, geometry: com.purride.pixelcore.PixelGridGeometry) {
        if (pixelGridColor.argb == backgroundColor.argb) return
        reusablePaint.color = pixelGridColor.argb
        canvas.drawRect(
            geometry.originX,
            geometry.originY,
            geometry.originX + geometry.contentWidth,
            geometry.originY + geometry.contentHeight,
            reusablePaint,
        )
    }

    /**
     * 逐格绘制像素点（gap 路径专用）。
     *
     * - 点亮格（alpha > 0）：以自身颜色绘制
     * - 熄灭格（alpha == 0）：以 [pixelGridColor] 绘制（格点矩阵可见）
     *   当 [pixelGridColor] == [backgroundColor] 时跳过熄灭格（等同旧行为）
     *
     * Canvas 在调用前已由 [onDraw] 清为 [backgroundColor]；此处不再重绘背景。
     */
    private fun drawPixelShapes(canvas: Canvas, buffer: PixelBuffer, geometry: com.purride.pixelcore.PixelGridGeometry) {
        val showDeadPixels = pixelGridColor.argb != backgroundColor.argb
        val shape = screenProfile.pixelShape
        for (y in 0 until buffer.height) {
            for (x in 0 until buffer.width) {
                val pixel = buffer.getPixel(x, y)
                val isLit = pixel.alpha > 0
                if (!isLit && !showDeadPixels) continue

                val left = geometry.originX + x * geometry.cellSize + geometry.dotInset
                val top = geometry.originY + y * geometry.cellSize + geometry.dotInset
                val right = left + geometry.dotSize
                val bottom = top + geometry.dotSize
                reusablePaint.color = if (isLit) pixel.argb else pixelGridColor.argb

                when (shape) {
                    PixelShape.CIRCLE -> {
                        val centerX = (left + right) / 2f
                        val centerY = (top + bottom) / 2f
                        val radius = min(right - left, bottom - top) / 2f
                        canvas.drawCircle(centerX, centerY, radius, reusablePaint)
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
                        canvas.drawPath(reusableDiamondPath, reusablePaint)
                    }
                    else -> canvas.drawRect(left, top, right, bottom, reusablePaint)
                }
            }
        }
    }

    /**
     * 暗角叠层：以内容区中心为圆心的径向渐变（透明→黑），覆盖在像素区上方。
     * Shader 按几何和强度缓存，避免每帧重建。
     */
    private fun drawVignette(canvas: Canvas, geometry: com.purride.pixelcore.PixelGridGeometry) {
        val cx = geometry.originX + geometry.contentWidth / 2f
        val cy = geometry.originY + geometry.contentHeight / 2f
        // Radius covers all four corners from center
        val radius = hypot(geometry.contentWidth / 2f, geometry.contentHeight / 2f)
        val alpha = (vignetteStrength * 220).toInt()

        if (cachedVignetteShader == null ||
            cachedVignetteCx != cx ||
            cachedVignetteCy != cy ||
            cachedVignetteRadius != radius ||
            cachedVignetteAlpha != alpha
        ) {
            cachedVignetteShader = RadialGradient(
                cx, cy, radius,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(alpha, 0, 0, 0)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
            cachedVignetteCx = cx
            cachedVignetteCy = cy
            cachedVignetteRadius = radius
            cachedVignetteAlpha = alpha
        }

        vignettePaint.shader = cachedVignetteShader
        canvas.drawRect(
            geometry.originX,
            geometry.originY,
            geometry.originX + geometry.contentWidth,
            geometry.originY + geometry.contentHeight,
            vignettePaint,
        )
    }

    private fun mapTouchToLogical(touchX: Float, touchY: Float): Pair<Int, Int>? {
        return PixelGridGeometryResolver.mapSurfaceToLogical(
            touchX = touchX,
            touchY = touchY,
            viewWidth = width,
            viewHeight = height,
            profile = screenProfile,
            pixelGapEnabled = pixelGapEnabled,
            pixelGapRatio = pixelGapRatio,
        )
    }
}
