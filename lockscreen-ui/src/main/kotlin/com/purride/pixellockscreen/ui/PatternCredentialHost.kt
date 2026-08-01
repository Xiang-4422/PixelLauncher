package com.purride.pixellockscreen.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.purride.pixeldesign.ProductThemeBrightness
import com.purride.pixeldesign.ProductThemeFamily
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelGridGeometryResolver
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.PixelHostProfilePolicy
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.SizedBox

/**
 * 图案认证使用的透明交互宿主。
 *
 * 所有可见内容由内部 [PixelHostView] 绘制；本 Android 容器只负责把二维 MotionEvent 映射到
 * 相同逻辑网格，并将逐格事件交给外部可清零安全会话。
 */
public class PatternCredentialHost(
    /** 创建像素宿主使用的 Android 上下文。 */
    context: Context,
    /** 接收逐格输入和安全回退事件的唯一监听器。 */
    private val listener: PatternCredentialListener,
) : FrameLayout(context) {
    /** 实际执行全部可见绘制的 Pixel Engine 宿主。 */
    private val pixelHostView: PixelHostView = PixelHostView(context)

    /** 不绘制内容、只为 TalkBack 暴露独立紧急操作的透明 Android 节点。 */
    private val emergencyAccessibilityView: View = View(context)

    /** 当前方向的逻辑布局。 */
    private var currentLayout: PatternCredentialLayout = patternCredentialLayout(isLandscape = false)

    /** 最近一次非敏感渲染请求。 */
    private var lastRequest: PatternCredentialSceneRequest? = null

    /** 当前触摸序列使用的主指针 ID。 */
    private var activePointerId: Int = MotionEvent.INVALID_POINTER_ID

    /** 当前主指针是否由紧急按钮而非图案区域持有。 */
    private var emergencyPointerActive: Boolean = false

    /** 事件接收失败后永久禁止当前宿主继续采集。 */
    private var interactionFailed: Boolean = false

    /** 宿主是否已经释放。 */
    private var disposed: Boolean = false

    /** 仅保存当前按下序列的可清零图案跟踪器。 */
    private val gestureTracker: PatternGestureTracker = PatternGestureTracker(
        layout = currentLayout,
        onStarted = { safelyNotify(listener::onPatternStarted) },
        onCellAdded = { cellId -> safelyNotify { listener.onPatternCellAdded(cellId) } },
        onCompleted = { cellCount -> safelyNotify { listener.onPatternCompleted(cellCount) } },
        onCancelled = { safelyNotify(listener::onPatternCancelled) },
        onVisualChanged = ::submitCurrentScene,
    )

    /** 配置透明像素渲染与可聚焦的认证输入边界。 */
    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

        pixelHostView.setBackgroundColor(Color.TRANSPARENT)
        pixelHostView.bezelColor = PixelColor.Transparent
        pixelHostView.offPixelColor = PixelColor.Transparent
        pixelHostView.setPixelGapEnabled(false)
        pixelHostView.isClickable = false
        pixelHostView.isFocusable = false
        pixelHostView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        pixelHostView.setContent { SizedBox(width = 0, height = 0) }
        addView(pixelHostView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        emergencyAccessibilityView.setBackgroundColor(Color.TRANSPARENT)
        emergencyAccessibilityView.isClickable = true
        emergencyAccessibilityView.isFocusable = true
        emergencyAccessibilityView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        emergencyAccessibilityView.setOnClickListener {
            safelyNotify(listener::onEmergencyRequested)
        }
        addView(emergencyAccessibilityView, LayoutParams(0, 0))
        configureProfile(currentLayout)
    }

    /** 提交非敏感反馈与主题；完整相同请求不会重建 Widget 树。 */
    public fun update(
        state: PatternCredentialUiState,
        family: ProductThemeFamily,
        brightness: ProductThemeBrightness,
    ) {
        check(!disposed) { "PatternCredentialHost 已释放" }
        /** 当前物理尺寸解析出的方向。 */
        val isLandscape = width > 0 && height > 0 && width > height
        /** 本次完整非敏感请求。 */
        val request = PatternCredentialSceneRequest(state, family, brightness, isLandscape)
        if (!state.isInputEnabled) {
            gestureTracker.cancel()
            activePointerId = MotionEvent.INVALID_POINTER_ID
        }
        if (!shouldSubmitPatternCredentialRequest(lastRequest, request)) {
            return
        }
        lastRequest = request
        contentDescription = listOf(state.promptText, state.feedbackText)
            .filter(String::isNotBlank)
            .joinToString(separator = ". ")
        emergencyAccessibilityView.contentDescription = state.emergencyAccessibilityLabel
        submitCurrentScene()
    }

    /** 尺寸变化时同步 Pixel Engine profile、触摸几何和场景方向。 */
    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (disposed || width <= 0 || height <= 0) {
            return
        }
        /** 新方向使用的固定逻辑布局。 */
        val nextLayout = patternCredentialLayout(isLandscape = width > height)
        currentLayout = nextLayout
        gestureTracker.updateLayout(nextLayout)
        configureProfile(nextLayout)
        /** 保留反馈与主题，仅切换方向。 */
        val previous = lastRequest ?: return
        if (previous.isLandscape != (width > height)) {
            lastRequest = previous.copy(isLandscape = width > height)
            submitCurrentScene()
        }
    }

    /** 按当前测量方向为透明紧急无障碍节点提供真实物理尺寸。 */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        /** 当前测量尺寸对应的逻辑布局。 */
        val measuredLayout = patternCredentialLayout(isLandscape = measuredWidth > measuredHeight)
        /** 与像素按钮一致的物理边界。 */
        val emergencyBounds = resolveEmergencyBounds(
            viewWidth = measuredWidth,
            viewHeight = measuredHeight,
            layout = measuredLayout,
        )
        if (emergencyBounds != null) {
            emergencyAccessibilityView.measure(
                MeasureSpec.makeMeasureSpec(emergencyBounds.width(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(emergencyBounds.height(), MeasureSpec.EXACTLY),
            )
        }
    }

    /** 按 Pixel Engine 的真实物理几何定位透明紧急无障碍节点。 */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        /** 当前绘制使用的紧急按钮物理边界。 */
        val emergencyBounds = resolveEmergencyBounds(
            viewWidth = width,
            viewHeight = height,
            layout = currentLayout,
        ) ?: return
        emergencyAccessibilityView.layout(
            emergencyBounds.left,
            emergencyBounds.top,
            emergencyBounds.right,
            emergencyBounds.bottom,
        )
    }

    /** 消费完整图案指针序列，禁止事件穿透到仍作为回退保留的原生 Bouncer。 */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (disposed || interactionFailed) {
            return true
        }
        /** 当前请求决定是否允许新输入。 */
        val request = lastRequest ?: return true
        if (!request.state.isInputEnabled) {
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handlePointerDown(event)
            MotionEvent.ACTION_MOVE -> handlePointerMove(event)
            MotionEvent.ACTION_UP -> handlePointerUp(event)
            MotionEvent.ACTION_CANCEL -> cancelGesture()
            MotionEvent.ACTION_POINTER_UP -> {
                /** 抬起的指针 ID。 */
                val pointerId = event.getPointerId(event.actionIndex)
                if (pointerId == activePointerId) {
                    cancelGesture()
                }
            }
        }
        return true
    }

    /** 点击语义由完整图案手势承担，不额外触发业务动作。 */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** 分离窗口时立即清除尚未完成的路径。 */
    override fun onDetachedFromWindow() {
        cancelGesture()
        super.onDetachedFromWindow()
    }

    /** 幂等取消输入、清零路径并释放 Pixel Engine 资源。 */
    public fun dispose() {
        if (disposed) {
            return
        }
        cancelGesture()
        disposed = true
        lastRequest = null
        pixelHostView.dispose()
        removeAllViews()
    }

    /** 开始主指针序列并把物理落点映射到逻辑网格。 */
    private fun handlePointerDown(event: MotionEvent) {
        activePointerId = event.getPointerId(0)
        parent?.requestDisallowInterceptTouchEvent(true)
        /** 当前落点对应的逻辑坐标。 */
        val point = mapToLogical(event.x, event.y) ?: return
        if (currentLayout.containsEmergency(point.first, point.second)) {
            emergencyPointerActive = true
            return
        }
        gestureTracker.start(point.first, point.second)
    }

    /** 沿主指针的历史点和当前点补采路径。 */
    private fun handlePointerMove(event: MotionEvent) {
        /** 主指针当前索引。 */
        val pointerIndex = event.findPointerIndex(activePointerId)
        if (pointerIndex < 0) {
            cancelGesture()
            return
        }
        if (emergencyPointerActive) {
            return
        }
        repeat(event.historySize) { historyIndex ->
            /** 历史采样对应的逻辑坐标。 */
            val point = mapToLogical(
                event.getHistoricalX(pointerIndex, historyIndex),
                event.getHistoricalY(pointerIndex, historyIndex),
            ) ?: return@repeat
            gestureTracker.update(point.first, point.second)
        }
        /** 当前采样对应的逻辑坐标。 */
        val point = mapToLogical(event.getX(pointerIndex), event.getY(pointerIndex)) ?: return
        gestureTracker.update(point.first, point.second)
    }

    /** 完成主指针路径并释放父级拦截限制。 */
    private fun handlePointerUp(event: MotionEvent) {
        if (event.getPointerId(event.actionIndex) != activePointerId) {
            return
        }
        if (emergencyPointerActive) {
            /** 抬起点对应的逻辑坐标。 */
            val point = mapToLogical(event.getX(event.actionIndex), event.getY(event.actionIndex))
            if (point != null && currentLayout.containsEmergency(point.first, point.second)) {
                safelyNotify(listener::onEmergencyRequested)
            }
            emergencyPointerActive = false
        } else {
            gestureTracker.end()
        }
        activePointerId = MotionEvent.INVALID_POINTER_ID
        parent?.requestDisallowInterceptTouchEvent(false)
        performClick()
    }

    /** 取消当前路径并释放父级拦截限制。 */
    private fun cancelGesture() {
        gestureTracker.cancel()
        emergencyPointerActive = false
        activePointerId = MotionEvent.INVALID_POINTER_ID
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    /** 使用与 PixelHostView 绘制完全相同的几何映射物理触摸。 */
    private fun mapToLogical(physicalX: Float, physicalY: Float): Pair<Int, Int>? =
        PixelGridGeometryResolver.mapSurfaceToLogical(
            touchX = physicalX,
            touchY = physicalY,
            viewWidth = width,
            viewHeight = height,
            profile = pixelHostView.screenProfile,
            viewportPolicy = pixelHostView.viewportPolicy,
            pixelGapEnabled = false,
        )

    /** 按当前方向配置固定逻辑网格。 */
    private fun configureProfile(layout: PatternCredentialLayout) {
        pixelHostView.profilePolicy = PixelHostProfilePolicy.AdaptiveLogicalSize(
            logicalWidth = layout.logicalWidth,
            logicalHeight = layout.logicalHeight,
        )
    }

    /** 使用固定逻辑尺寸解析紧急按钮的真实物理边界。 */
    private fun resolveEmergencyBounds(
        /** Android 宿主物理宽度。 */
        viewWidth: Int,
        /** Android 宿主物理高度。 */
        viewHeight: Int,
        /** 当前方向的逻辑布局。 */
        layout: PatternCredentialLayout,
    ): Rect? {
        /** 与当前场景逻辑尺寸一致的临时屏幕配置。 */
        val profile = ScreenProfile(
            logicalWidth = layout.logicalWidth,
            logicalHeight = layout.logicalHeight,
            dotSizePx = 1,
        )
        /** 与 Pixel Engine 绘制共用的物理网格几何。 */
        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            profile = profile,
            viewportPolicy = pixelHostView.viewportPolicy,
            pixelGapEnabled = false,
        ) ?: return null
        return Rect(
            (geometry.originX + layout.emergencyLeft * geometry.cellSize).toInt(),
            (geometry.originY + layout.emergencyTop * geometry.cellSize).toInt(),
            (
                geometry.originX +
                    (layout.emergencyLeft + layout.emergencyWidth) * geometry.cellSize
                ).toInt(),
            (
                geometry.originY +
                    (layout.emergencyTop + layout.emergencyHeight) * geometry.cellSize
                ).toInt(),
        )
    }

    /** 重新提交当前请求和同一可清零路径引用。 */
    private fun submitCurrentScene() {
        if (disposed) {
            return
        }
        /** 当前待绘制请求。 */
        val request = lastRequest ?: return
        pixelHostView.setContent { buildPatternCredentialScene(request, gestureTracker) }
    }

    /** 捕获监听器异常、停止采集，并通知模块恢复原生认证页面。 */
    private fun safelyNotify(action: () -> Unit) {
        if (interactionFailed) {
            return
        }
        runCatching(action).onFailure { throwable ->
            interactionFailed = true
            runCatching { listener.onInteractionFailure(throwable) }
        }
    }
}
