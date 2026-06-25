package com.purride.pixelui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.InputDevice
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelFrameView
import com.purride.pixelcore.PixelGridGeometry
import com.purride.pixelcore.PixelGridGeometryResolver
import com.purride.pixelcore.PixelShape
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelcore.ScreenProfileFactory
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.internal.PixelClickTarget
import com.purride.pixelui.gesture.NestedScrollGesturePolicy
import com.purride.pixelui.gesture.PagerGesturePolicy
import com.purride.pixelui.host.PixelFrameScheduler
import com.purride.pixelui.PixelScrollPhysics
import com.purride.pixelui.internal.PixelPagerTarget
import com.purride.pixelui.internal.PixelRenderResult
import com.purride.pixelui.internal.PixelListTarget
import com.purride.pixelui.internal.PixelRefreshTarget
import com.purride.pixelui.internal.PixelScrollbarTarget
import com.purride.pixelui.internal.PixelSliderTarget
import com.purride.pixelui.internal.PixelTextInputTarget
import com.purride.pixelui.internal.host.PixelJoystickFocusRouter
import com.purride.pixelui.internal.host.mapAndroidKeyCodeToPixelKeyEvent
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixelui.internal.NestedScrollSession
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/**
 * pixel-engine UI layer 的最小宿主 View。
 *
 * 引擎是纯像素渲染器——widget 树 → ARGB 像素网格。
 * 屏幕外框颜色通过 [bezelColor] 属性控制；不再有 colorMode / palette / themeData。
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
    public var bezelColor: PixelColor = PixelColor.Black
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
    public var offPixelColor: PixelColor = PixelColor.fromRgb(17, 17, 17)
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Current system/window insets in pixel-engine logical coordinates.
     *
     * Android host callbacks update this automatically when [onApplyWindowInsets] runs.
     * Tests or custom hosts may set it directly or call [setWindowInsets].
     */
    public var windowInsets: PixelWindowInsets = PixelWindowInsets.Zero
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /**
     * Current obscuring insets in pixel-engine logical coordinates.
     *
     * This is separate from [windowInsets]: [windowInsets] models persistent system safe areas,
     * while [viewInsets] models transient occlusion such as the IME keyboard.
     */
    public var viewInsets: PixelWindowInsets = PixelWindowInsets.Zero
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    internal var lastRenderResult: PixelRenderResult?
        get() = renderCoordinator.lastRenderResult
        set(value) { renderCoordinator.lastRenderResult = value }
    private var pixelGapEnabled: Boolean = true
    private var pixelGapRatio: Float = 1.0f
    internal var activeSliderTarget: PixelSliderTarget? = null
    internal var activeScrollbarTarget: PixelScrollbarTarget? = null
    internal var activeSwipeTarget: PixelClickTarget? = null
    internal var candidateSwipeTarget: PixelClickTarget? = null
    internal var activeRefreshTarget: PixelRefreshTarget? = null
    internal var candidateRefreshTarget: PixelRefreshTarget? = null
    internal var scrollbarDragThumbOffsetY: Int = 0
    internal var velocityTracker: VelocityTracker? = null
    internal val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    internal var touchDownX = 0f
    internal var touchDownY = 0f
    internal var touchDownLogicalX = 0
    internal var touchDownLogicalY = 0
    internal var lastPagerLogicalX = 0
    internal var lastPagerLogicalY = 0
    internal var lastListLogicalY = 0
    internal var lastTextInputTapTimeMs: Long = -1L
    internal var lastTextInputTapState: PixelTextFieldState? = null
    internal var activeTextInputSelectionTarget: PixelTextInputTarget? = null
    internal var activeTextInputSelectionHandle: TextInputSelectionHandle? = null
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
    private val joystickFocusRouter = PixelJoystickFocusRouter()
    private val textInputCoordinator = PixelHostTextInputCoordinator(this)
    private val renderCoordinator = PixelHostRenderCoordinator(this, textInputCoordinator)
    private val lifecycleCoordinator = PixelHostLifecycleCoordinator(disposeRender = renderCoordinator::dispose)

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
    private var gapBackgroundBitmap: Bitmap? = null
    private var gapBackgroundKey: GapBackgroundKey? = null
    private val reusableGapBackgroundCanvas = Canvas()
    private val reusableDestRect = Rect()

    public fun setContent(provider: RootWidgetProvider) {
        renderCoordinator.setContent(provider)
    }

    internal fun requestRender() { invalidate() }

    public fun updateFocusedTextInput(
        text: String,
        selectionStart: Int = text.length,
        selectionEnd: Int = selectionStart,
        compositionStart: Int = -1,
        compositionEnd: Int = -1,
    ) {
        textInputCoordinator.updateFocusedTextInput(
            text = text,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            compositionStart = compositionStart,
            compositionEnd = compositionEnd,
        )
    }

    public fun clearFocusedTextInput() {
        textInputCoordinator.clearFocusedTextInput()
    }

    public fun submitFocusedTextInput() {
        textInputCoordinator.submitFocusedTextInput()
    }

    /**
     * 对当前聚焦的 TextField 执行 [action]。
     *
     * 没有聚焦字段、选区或剪贴板内容不足以执行该动作时返回 `false`。
     */
    public fun performFocusedTextEditAction(action: PixelTextEditAction): Boolean {
        return textInputCoordinator.performEditAction(action)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            val handled = PixelFocusManager.dispatchKeyEvent(event.toPixelKeyEvent())
            if (handled) {
                invalidate()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (
            event.action == MotionEvent.ACTION_MOVE &&
            (event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.isFromSource(InputDevice.SOURCE_GAMEPAD))
        ) {
            val keyEvent = joystickFocusRouter.onAxes(
                xAxis = event.getAxisValue(MotionEvent.AXIS_X),
                yAxis = event.getAxisValue(MotionEvent.AXIS_Y),
                hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X),
                hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y),
                eventTimeMs = event.eventTime,
            )
            if (keyEvent != null && PixelFocusManager.dispatchKeyEvent(keyEvent)) {
                invalidate()
                return true
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun submitFrame(pixelBuffer: PixelBuffer, screenProfile: ScreenProfile, backgroundColor: PixelColor) {
        renderCoordinator.submitFrame(pixelBuffer, screenProfile, backgroundColor)
    }

    override fun setPixelGapEnabled(enabled: Boolean) {
        pixelGapEnabled = enabled
        if (!enabled) {
            recycleGapBackgroundBitmap()
        }
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

    /**
     * Sets logical window insets manually.
     *
     * Android hosts usually rely on [onApplyWindowInsets]; tests and custom hosts can use this
     * method to inject already-converted logical insets.
     */
    public fun setWindowInsets(
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0,
    ) {
        windowInsets = lifecycleCoordinator.manualInsets(left = left, top = top, right = right, bottom = bottom)
    }

    /**
     * Sets logical view insets manually.
     *
     * Android hosts usually rely on [onApplyWindowInsets]; tests and custom hosts can use this
     * method to inject already-converted IME or transient occlusion insets.
     */
    public fun setViewInsets(
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0,
    ) {
        viewInsets = lifecycleCoordinator.manualInsets(left = left, top = top, right = right, bottom = bottom)
    }

    override fun asView(): View = this

    /**
     * 把当前 retained element tree 序列化成 ASCII 缩进的字符串，
     * 用于运行时调试 / log dump。仅当至少渲染过一帧后返回非空内容。
     *
     * 该 API 在主线程调用，不应在生产热路径上频繁调用。
     */
    public fun dumpElementTree(): String {
        return renderCoordinator.dumpElementTree()
    }

    /**
     * 把当前 render tree 序列化成 ASCII 缩进字符串。
     *
     * 用于运行时调试；不应在生产热路径高频调用。还没渲染过一帧时返回
     * `<no render root>`。
     */
    public fun dumpRenderTree(): String {
        return renderCoordinator.dumpRenderTree()
    }

    public fun dumpSemanticsTree(): String {
        val nodes = lastRenderResult?.semanticsNodes.orEmpty()
        if (nodes.isEmpty()) return "<empty semantics>"
        return nodes.joinToString(separator = "\n") { node ->
            "${node.role} label=\"${node.label}\" enabled=${node.enabled} focused=${node.focused} bounds=${node.left},${node.top},${node.width},${node.height}"
        }
    }

    /**
     * 采样当前 host inspector 快照，供调试面板、log dump 或崩溃诊断使用。
     *
     * 该方法会构造多段诊断字符串，请只在调试路径按需调用。
     */
    public fun inspect(
        includeFrameStats: Boolean = true,
        includeAllocationSample: Boolean = false,
    ): PixelInspectorSnapshot {
        val renderResult = lastRenderResult
        val targetCounts = renderResult?.let { result ->
            PixelInspectorTargetCounts(
                click = result.clickTargets.size,
                pager = result.pagerTargets.size,
                list = result.listTargets.size,
                scrollbar = result.scrollbarTargets.size,
                refresh = result.refreshTargets.size,
                textInput = result.textInputTargets.size,
                slider = result.sliderTargets.size,
                semantics = result.semanticsNodes.size,
            )
        } ?: PixelInspectorTargetCounts.Empty
        val nodeAssociations = renderCoordinator.collectInspectorNodeAssociations()
        return PixelInspectorSnapshot(
            frameStats = if (includeFrameStats) renderCoordinator.snapshotFrameStats() else null,
            allocationSample = if (includeAllocationSample) snapshotAllocationSample() else null,
            targetCounts = targetCounts,
            targetSnapshots = renderResult?.toInspectorTargetSnapshots(nodeAssociations).orEmpty(),
            elementTree = dumpElementTree(),
            renderTree = dumpRenderTree(),
            semanticsTree = dumpSemanticsTree(),
            hasPendingBuild = renderCoordinator.hasPendingBuild(),
            focusedTextInput = focusedTextInputTarget != null,
            activePagerCount = renderResult?.pagerTargets.orEmpty().count { it.controller.isActive(it.state) },
            activeListCount = renderResult?.listTargets.orEmpty().count { it.controller.isActive(it.state) },
            activeSlider = activeSliderTarget != null,
            activeScrollbar = activeScrollbarTarget != null,
            activeRefresh = activeRefreshTarget != null,
        )
    }

    private fun snapshotAllocationSample(): PixelInspectorAllocationSample {
        val runtime = Runtime.getRuntime()
        val total = runtime.totalMemory()
        val free = runtime.freeMemory()
        return PixelInspectorAllocationSample(
            usedHeapBytes = (total - free).coerceAtLeast(0L),
            totalHeapBytes = total,
            maxHeapBytes = runtime.maxMemory(),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val renderResult = renderCoordinator.renderFrame()
        canvas.drawColor(bezelColor.argb)
        if (renderResult != null) {
            drawBuffer(canvas, renderResult.buffer)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == oldw && h == oldh) return
        updateScreenProfileFromPreference()
    }

    @Suppress("DEPRECATION")
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            windowInsets = lifecycleCoordinator.platformInsetsToLogical(
                leftPx = systemBars.left,
                topPx = systemBars.top,
                rightPx = systemBars.right,
                bottomPx = systemBars.bottom,
                viewWidth = width,
                viewHeight = height,
                screenProfile = screenProfile,
                pixelGapEnabled = pixelGapEnabled,
                pixelGapRatio = pixelGapRatio,
            )
            viewInsets = lifecycleCoordinator.platformInsetsToLogical(
                leftPx = ime.left,
                topPx = ime.top,
                rightPx = ime.right,
                bottomPx = ime.bottom,
                viewWidth = width,
                viewHeight = height,
                screenProfile = screenProfile,
                pixelGapEnabled = pixelGapEnabled,
                pixelGapRatio = pixelGapRatio,
            )
        } else {
            windowInsets = lifecycleCoordinator.platformInsetsToLogical(
                leftPx = insets.systemWindowInsetLeft,
                topPx = insets.systemWindowInsetTop,
                rightPx = insets.systemWindowInsetRight,
                bottomPx = insets.systemWindowInsetBottom,
                viewWidth = width,
                viewHeight = height,
                screenProfile = screenProfile,
                pixelGapEnabled = pixelGapEnabled,
                pixelGapRatio = pixelGapRatio,
            )
            viewInsets = PixelWindowInsets.Zero
        }
        return super.onApplyWindowInsets(insets)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        recycleGapBackgroundBitmap()
        lifecycleCoordinator.onDetachedFromWindow()
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

    internal fun resolveClickTarget(logicalX: Int, logicalY: Int): PixelClickTarget? {
        return lastRenderResult?.clickTargets?.lastOrNull { it.bounds.contains(logicalX, logicalY) }
    }

    internal fun resolveTextInputTarget(logicalX: Int, logicalY: Int): PixelTextInputTarget? {
        return lastRenderResult?.textInputTargets?.lastOrNull { it.bounds.contains(logicalX, logicalY) }
    }

    internal fun focusTextInput(target: PixelTextInputTarget) {
        textInputCoordinator.focus(target)
    }

    internal fun showTextSelectionMenu(target: PixelTextInputTarget) {
        showPixelTextSelectionActionMode(target)
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
     * - 间隙开启：逐格绘制，熄灭格用 [offPixelColor]（格点矩阵可见），点亮格用自身颜色
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
            // Canvas was already cleared to bezelColor by onDraw().
            drawPixelShapes(canvas, buffer, geometry)
            drawBezelOverlay(canvas, buffer, geometry)
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
            // Fill content area with offPixelColor so transparent pixels aren't bare-black
            fillContentArea(canvas, geometry)
            canvas.drawBitmap(bitmap, null, reusableDestRect, null)
        }

    }

    /**
     * 最后绘制像素间隙，确保屏幕模拟层覆盖所有 UI 内容。
     */
    private fun drawBezelOverlay(
        canvas: Canvas,
        buffer: PixelBuffer,
        geometry: PixelGridGeometry,
    ) {
        val gap = geometry.dotInset
        if (gap <= 0f) return
        reusablePaint.color = bezelColor.argb
        val cell = geometry.cellSize
        val gapWidth = gap * 2f
        for (x in 0..buffer.width) {
            val left = geometry.originX + x * cell - gap
            canvas.drawRect(
                left,
                geometry.originY,
                left + gapWidth,
                geometry.originY + geometry.contentHeight,
                reusablePaint,
            )
        }
        for (y in 0..buffer.height) {
            val top = geometry.originY + y * cell - gap
            canvas.drawRect(
                geometry.originX,
                top,
                geometry.originX + geometry.contentWidth,
                top + gapWidth,
                reusablePaint,
            )
        }
    }

    /** 用 [offPixelColor] 填充逻辑像素内容区（screen panel 底色）。 */
    private fun fillContentArea(canvas: Canvas, geometry: PixelGridGeometry) {
        if (offPixelColor.argb == bezelColor.argb) return
        reusablePaint.color = offPixelColor.argb
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
     * - 熄灭格（alpha == 0）：以 [offPixelColor] 绘制（格点矩阵可见）
     *   当 [offPixelColor] == [bezelColor] 时跳过熄灭格（等同旧行为）
     *
     * Canvas 在调用前已由 [onDraw] 清为 [bezelColor]；此处不再重绘背景。
     */
    private fun drawPixelShapes(canvas: Canvas, buffer: PixelBuffer, geometry: PixelGridGeometry) {
        val showDeadPixels = offPixelColor.argb != bezelColor.argb
        val shape = screenProfile.pixelShape
        if (showDeadPixels) {
            drawGapBackground(canvas, buffer, geometry, shape)
        }
        for (y in 0 until buffer.height) {
            for (x in 0 until buffer.width) {
                val pixel = buffer.getPixel(x, y)
                if (pixel.alpha <= 0) continue

                val left = geometry.originX + x * geometry.cellSize + geometry.dotInset
                val top = geometry.originY + y * geometry.cellSize + geometry.dotInset
                val right = left + geometry.dotSize
                val bottom = top + geometry.dotSize
                reusablePaint.color = pixel.argb
                drawPixelShape(canvas, left, top, right, bottom, shape)
            }
        }
    }

    private fun drawGapBackground(
        canvas: Canvas,
        buffer: PixelBuffer,
        geometry: PixelGridGeometry,
        shape: PixelShape,
    ) {
        val bitmap = gapBackgroundBitmap(buffer.width, buffer.height, geometry, shape) ?: return
        canvas.drawBitmap(bitmap, geometry.originX, geometry.originY, null)
    }

    private fun gapBackgroundBitmap(
        logicalWidth: Int,
        logicalHeight: Int,
        geometry: PixelGridGeometry,
        shape: PixelShape,
    ): Bitmap? {
        val bitmapWidth = ceil(geometry.contentWidth).toInt().coerceAtLeast(1)
        val bitmapHeight = ceil(geometry.contentHeight).toInt().coerceAtLeast(1)
        val key = GapBackgroundKey(
            bitmapWidth = bitmapWidth,
            bitmapHeight = bitmapHeight,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
            cellSize = geometry.cellSize,
            dotInset = geometry.dotInset,
            dotSize = geometry.dotSize,
            pixelShape = shape,
            pixelGridArgb = offPixelColor.argb,
        )
        val existing = gapBackgroundBitmap
        if (existing != null && gapBackgroundKey == key && !existing.isRecycled) {
            return existing
        }

        recycleGapBackgroundBitmap()
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        reusableGapBackgroundCanvas.setBitmap(bitmap)
        reusablePaint.color = offPixelColor.argb
        for (y in 0 until logicalHeight) {
            for (x in 0 until logicalWidth) {
                val left = x * geometry.cellSize + geometry.dotInset
                val top = y * geometry.cellSize + geometry.dotInset
                val right = left + geometry.dotSize
                val bottom = top + geometry.dotSize
                drawPixelShape(reusableGapBackgroundCanvas, left, top, right, bottom, shape)
            }
        }
        reusableGapBackgroundCanvas.setBitmap(null)
        gapBackgroundBitmap = bitmap
        gapBackgroundKey = key
        return bitmap
    }

    private fun drawPixelShape(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        shape: PixelShape,
    ) {
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

    private fun recycleGapBackgroundBitmap() {
        reusableGapBackgroundCanvas.setBitmap(null)
        gapBackgroundBitmap?.recycle()
        gapBackgroundBitmap = null
        gapBackgroundKey = null
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

private data class GapBackgroundKey(
    val bitmapWidth: Int,
    val bitmapHeight: Int,
    val logicalWidth: Int,
    val logicalHeight: Int,
    val cellSize: Float,
    val dotInset: Float,
    val dotSize: Float,
    val pixelShape: PixelShape,
    val pixelGridArgb: Int,
)

private fun android.view.KeyEvent.toPixelKeyEvent(): PixelKeyEvent {
    return mapAndroidKeyCodeToPixelKeyEvent(
        keyCode = keyCode,
        isShiftPressed = isShiftPressed,
        unicodeChar = unicodeChar,
    )
}
