package com.purride.pixelui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
import com.purride.pixelui.gesture.NestedScrollGesturePolicy
import com.purride.pixelui.gesture.PagerGesturePolicy
import com.purride.pixelui.host.PixelHostFrameLoop
import com.purride.pixelui.host.PixelFrameScheduler
import com.purride.pixelui.PixelScrollPhysics
import com.purride.pixelui.internal.PixelPagerTarget
import com.purride.pixelui.internal.PixelRenderResult
import com.purride.pixelui.internal.PixelListTarget
import com.purride.pixelui.internal.PixelTextInputTarget
import com.purride.pixelui.internal.HostRootWidget
import com.purride.pixelui.internal.NestedScrollSession
import com.purride.pixelui.internal.PixelUiRuntime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * pixel-engine UI layer 的最小宿主 View。
 *
 * 当前宿主已经开始走 retained build runtime：
 * - 公开层交给 `Widget / StatefulWidget / InheritedWidget`
 * - 宿主负责维持 retained build tree
 * - 渲染阶段默认直接进入新 pipeline
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

    /**
     * 宿主显示偏好。
     *
     * 当业务层只关心“点大小和像素形状”时，可以设置这个偏好，把真正的
     * 全屏 `ScreenProfile` 推导交给 `PixelHostView` 自己完成。
     */
    public var profilePreference: PixelHostProfilePreference? = null
        set(value) {
            field = value
            updateScreenProfileFromPreference()
        }

    private var runtime = PixelUiRuntime(
        onVisualUpdate = { postInvalidateOnAnimation() },
    )
    private var contentProvider: RootWidgetProvider? = null
    private var lastRenderResult: PixelRenderResult? = null
    private var palette: PixelPalette = PixelPalette.terminalGreen()
    private var pixelGapEnabled: Boolean = true
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
        set(value) {
            nestedScrollSession.candidatePagerTarget = value
        }
    private var activePagerTarget: PixelPagerTarget?
        get() = nestedScrollSession.activePagerTarget
        set(value) {
            nestedScrollSession.activePagerTarget = value
        }
    private var candidateListTarget: PixelListTarget?
        get() = nestedScrollSession.candidateListTarget
        set(value) {
            nestedScrollSession.candidateListTarget = value
        }
    private var activeListTarget: PixelListTarget?
        get() = nestedScrollSession.activeListTarget
        set(value) {
            nestedScrollSession.activeListTarget = value
        }
    private var focusedTextInputTarget: PixelTextInputTarget?
        get() = nestedScrollSession.focusedTextInputTarget
        set(value) {
            nestedScrollSession.focusedTextInputTarget = value
        }

    public var hostBridge: PixelHostBridge? = null

    /**
     * 分页拖动启动策略。由 PixelHostSetupConfig 注入；业务可换上自定义子类。
     */
    public var pagerGesturePolicy: PagerGesturePolicy = PagerGesturePolicy.Default

    /**
     * 嵌套滚动手势仲裁策略。
     */
    public var nestedScrollPolicy: NestedScrollGesturePolicy = NestedScrollGesturePolicy.Default

    /**
     * 列表/单子节点 ScrollView 的滚动物理参数。默认值适配常见 launcher 滑动手感。
     */
    public var scrollPhysics: PixelScrollPhysics = PixelScrollPhysics.Default

    /**
     * 帧调度器。默认走 Android Choreographer；测试可注入 ManualFrameScheduler
     * 显式驱动帧时机。当前 PixelHostView 主路径仍依赖 View.postInvalidateOnAnimation
     * 触发重绘；scheduler 主要给业务侧动画引擎调用 `scheduleFrame { tNs -> ... }`
     * 拿到精确帧时间戳用。
     */
    public var frameScheduler: PixelFrameScheduler = PixelFrameScheduler.Default

    /**
     * 宿主级默认主题。
     *
     * 当整页大部分组件共享同一套默认样式时，可以直接把主题挂在宿主上，
     * 避免每个场景最外层都再包一层 `Theme(data, child)`。
     */
    public var themeData: ThemeData? = null
        set(value) {
            field = value
            invalidate()
        }

    /**
     * 宿主级默认文本方向。
     *
     * 这层会进入根环境，供 `Directionality.of(context)`、方向性对齐、
     * 方向性边距和方向性定位统一消费。
     */
    public var textDirection: TextDirection = TextDirection.LTR
        set(value) {
            field = value
            invalidate()
        }

    /**
     * 当前宿主使用的文本栅格器。
     *
     * 默认继续使用内置位图字体，但 demo 或后续业务层可以在不改 runtime 的情况下
     * 注入另一套文本实现。
     */
    public var textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default
        set(value) {
            if (field === value) return
            field = value
            // rasterizer 通过 DefaultTextRasterizer InheritedWidget 在
            // HostRootWidget 里注入；只要触发一次 invalidate，下一帧 onDraw 重新
            // 包装根 widget 时新 rasterizer 就会经过 retained build 流到所有
            // TextWidget / RichTextWidget。runtime 与 buffer pool 复用，避免
            // 切换字体时大块重建。
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
     * 设置宿主当前要渲染的根组件。
     */
    public fun setContent(provider: RootWidgetProvider) {
        contentProvider = provider
        postInvalidateOnAnimation()
    }

    /**
     * 手动重绘入口。
     *
     * 新公开主路径已经不再推荐页面层直接调用它；页面刷新应尽量走
     * `State.setState`、`Listenable`、控制器通知这三类 retained 机制。
     */
    internal fun requestRender() {
        invalidate()
    }

    public fun updateFocusedTextInput(
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

    override fun submitFrame(pixelBuffer: PixelBuffer, screenProfile: ScreenProfile, palette: PixelPalette) {
        this.screenProfile = screenProfile
        this.palette = palette
        // PipelineOwner 自己持有上一帧并在下一帧脏时归还，外部直接 submitFrame
        // 只需丢弃我们对它的引用即可。
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
        val frameDeltaMs = frameLoop.consumeFrameDeltaMs()
        stepActivePagers(frameDeltaMs)
        stepActiveLists(frameDeltaMs)
        val provider = contentProvider
        val renderResult = if (provider != null) {
            val rootWidget = provider()
            val wrappedRoot = HostRootWidget(
                screenProfile = screenProfile,
                textDirection = textDirection,
                themeData = themeData,
                textRasterizer = textRasterizer,
                child = rootWidget,
                key = "host-root",
            )
            // PipelineOwner 自己缓存上一帧 buffer 并在下一帧脏时归还池，
            // 这里只需要清掉我们对它的引用。
            lastRenderResult = null
            runtime.render(
                root = wrappedRoot,
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
                        nestedScrollPolicy.shouldHandOffListToPager(
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
                    val pagerWantsDrag = pagerGesturePolicy.shouldStartDrag(
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
                    val shouldDeferToList = nestedScrollPolicy.shouldDeferPagerToList(
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
            requestedTarget.state.autofocusConsumed = true
            return
        }

        val autofocusTarget = targets.lastOrNull { target ->
            target.autofocus &&
                !target.state.autofocusConsumed &&
                focusedTextInputTarget == null
        }
        if (autofocusTarget != null) {
            autofocusTarget.state.autofocusConsumed = true
            focusTextInput(autofocusTarget)
        }
    }

    private fun focusTextInput(target: PixelTextInputTarget) {
        if (focusedTextInputTarget?.state !== target.state) {
            focusedTextInputTarget?.let { previous ->
                previous.controller.blur(previous.state)
            }
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
}
