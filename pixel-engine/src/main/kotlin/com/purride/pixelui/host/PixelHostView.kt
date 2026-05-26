package com.purride.pixelui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
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

    private var runtime = PixelUiRuntime(onVisualUpdate = { postInvalidateOnAnimation() })
    private var contentProvider: RootWidgetProvider? = null
    internal var lastRenderResult: PixelRenderResult? = null
    private var pixelGapEnabled: Boolean = true
    private var pixelGapRatio: Float = 1.0f
    internal var activeSliderTarget: PixelSliderTarget? = null
    private val frameLoop = PixelHostFrameLoop()
    internal var velocityTracker: VelocityTracker? = null
    internal val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    internal var touchDownX = 0f
    internal var touchDownY = 0f
    internal var touchDownLogicalX = 0
    internal var touchDownLogicalY = 0
    internal var lastPagerLogicalX = 0
    internal var lastPagerLogicalY = 0
    internal var lastListLogicalY = 0
    internal var touchMoved = false
    internal val nestedScrollSession = NestedScrollSession()
    internal var candidatePagerTarget: PixelPagerTarget?
        get() = nestedScrollSession.candidatePagerTarget
        set(value) { nestedScrollSession.candidatePagerTarget = value }
    internal var activePagerTarget: PixelPagerTarget?
        get() = nestedScrollSession.activePagerTarget
        set(value) { nestedScrollSession.activePagerTarget = value }
    internal var candidateListTarget: PixelListTarget?
        get() = nestedScrollSession.candidateListTarget
        set(value) { nestedScrollSession.candidateListTarget = value }
    internal var candidateTextInputTarget: PixelTextInputTarget?
        get() = nestedScrollSession.candidateTextInputTarget
        set(value) { nestedScrollSession.candidateTextInputTarget = value }
    internal var activeListTarget: PixelListTarget?
        get() = nestedScrollSession.activeListTarget
        set(value) { nestedScrollSession.activeListTarget = value }
    internal var focusedTextInputTarget: PixelTextInputTarget?
        get() = nestedScrollSession.focusedTextInputTarget
        set(value) { nestedScrollSession.focusedTextInputTarget = value }
    private val gestureRouter = PixelHostGestureRouter(this)

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

    /**
     * 调试观察者：非 null 时 PixelHostView 在每次 `onDraw` 末尾构造一次
     * [PixelHostFrameStats] 并回调。null 时不分配（保持热路径零分配）。
     *
     * 典型用法：把 [ValueNotifier]&lt;PixelHostFrameStats?&gt;::value 推进去，
     * 然后在 widget 树用 [ValueListenableBuilder] + [PixelDebugOverlay] 显示。
     */
    public var frameStatsObserver: ((PixelHostFrameStats) -> Unit)? = null

    private val reusablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val reusableDiamondPath = Path()
    private var reusableBitmap: Bitmap? = null
    private val reusableDestRect = Rect()

    public fun setContent(provider: RootWidgetProvider) {
        contentProvider = provider
        postInvalidateOnAnimation()
    }

    internal fun requestRender() { invalidate() }

    public fun updateFocusedTextInput(
        text: String,
        selectionStart: Int = text.length,
        selectionEnd: Int = selectionStart,
        compositionStart: Int = -1,
        compositionEnd: Int = -1,
    ) {
        val target = focusedTextInputTarget ?: return
        if (target.readOnly) return
        target.controller.updateText(
            state = target.state,
            text = text,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            compositionStart = compositionStart,
            compositionEnd = compositionEnd,
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

    /**
     * 把当前 retained element tree 序列化成 ASCII 缩进的字符串，
     * 用于运行时调试 / log dump。仅当至少渲染过一帧后返回非空内容。
     *
     * 该 API 在主线程调用，不应在生产热路径上频繁调用。
     */
    public fun dumpElementTree(): String {
        return runtime.dumpElementTree()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val frameDeltaMs = frameLoop.consumeFrameDeltaMs()
        frameLoop.beginPaint()
        stepActivePagers(frameDeltaMs)
        stepActiveLists(frameDeltaMs)
        stepFocusedTextInputCursor(frameDeltaMs)
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
        if (renderResult != null) {
            lastRenderResult = renderResult
            dispatchPageChanged(renderResult.pagerTargets)
            syncRequestedTextInputFocus(renderResult.textInputTargets)
            drawBuffer(canvas, renderResult.buffer)
            if (renderResult.pagerTargets.any { it.controller.isActive(it.state) } ||
                renderResult.listTargets.any { it.controller.isActive(it.state) } ||
                isFocusedTextInputBlinking()
            ) {
                postInvalidateOnAnimation()
            }
        }
        frameLoop.endPaint()
        frameStatsObserver?.invoke(frameLoop.snapshotStats())
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
        return gestureRouter.onTouchEvent(event) ?: super.onTouchEvent(event)
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

    private fun stepFocusedTextInputCursor(deltaMs: Long) {
        val target = focusedTextInputTarget ?: return
        target.controller.stepCursorBlink(target.state, deltaMs)
    }

    private fun isFocusedTextInputBlinking(): Boolean {
        val state = focusedTextInputTarget?.state ?: return false
        return state.isFocused && state.cursorBlinkEnabled
    }

    internal fun resolveClickTarget(logicalX: Int, logicalY: Int): PixelClickTarget? {
        return lastRenderResult?.clickTargets?.lastOrNull { it.bounds.contains(logicalX, logicalY) }
    }

    internal fun resolveTextInputTarget(logicalX: Int, logicalY: Int): PixelTextInputTarget? {
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

    internal fun focusTextInput(target: PixelTextInputTarget) {
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

    internal fun pagerViewportSize(target: PixelPagerTarget): Int {
        return when (target.axis) {
            PixelAxis.HORIZONTAL -> target.bounds.width
            PixelAxis.VERTICAL -> target.bounds.height
        }.coerceAtLeast(1)
    }

    internal fun shouldStartListDrag(rawDeltaX: Float, rawDeltaY: Float): Boolean {
        return abs(rawDeltaY) > touchSlop && abs(rawDeltaY) >= abs(rawDeltaX)
    }

    internal fun rawVelocityToLogical(velocityTracker: VelocityTracker?, axis: PixelAxis): Float {
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

    internal fun mapTouchToLogical(touchX: Float, touchY: Float): Pair<Int, Int>? {
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
