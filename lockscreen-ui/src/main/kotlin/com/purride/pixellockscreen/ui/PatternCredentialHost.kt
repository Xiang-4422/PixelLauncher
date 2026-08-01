package com.purride.pixellockscreen.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.purride.pixeldesign.ProductThemeCatalog
import com.purride.pixeldesign.font.fitProductTextWithin
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

    /** 当前点大小和宿主尺寸解析出的逻辑布局。 */
    private var currentLayout: PatternCredentialLayout = patternCredentialLayout()

    /** 最近一次外部提交的完整产品外观。 */
    private var currentAppearance: LockscreenAppearance? = null

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
        pixelHostView.setOnTouchListener { _, event -> dispatchPatternTouchEvent(event) }
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
    }

    /** 提交非敏感反馈与主题；完整相同请求不会重建 Widget 树。 */
    public fun update(
        state: PatternCredentialUiState,
        appearance: LockscreenAppearance,
    ) {
        check(!disposed) { "PatternCredentialHost 已释放" }
        applyAppearance(appearance)
        /** 本次完整非敏感请求。 */
        val request = PatternCredentialSceneRequest(
            state = state,
            family = appearance.themeFamily,
            brightness = appearance.brightness,
            layout = currentLayout,
        )
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
        emergencyAccessibilityView.isEnabled = state.isEmergencyAvailable
        emergencyAccessibilityView.visibility = if (state.isEmergencyAvailable) {
            View.VISIBLE
        } else {
            View.GONE
        }
        emergencyAccessibilityView.contentDescription = state.emergencyAccessibilityLabel
        submitCurrentScene()
        requestLayout()
    }

    /** 按固定方屏布局为透明紧急无障碍节点提供真实物理尺寸。 */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        /** 与像素按钮一致的物理边界。 */
        val emergencyBounds = resolveEmergencyBounds(
            viewWidth = measuredWidth,
            viewHeight = measuredHeight,
            layout = currentLayout,
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

    /** 物理尺寸变化时同步渲染、触摸和无障碍共用的动态方屏布局。 */
    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateLogicalLayout(width, height, submitScene = true)
    }

    /** 消费完整图案指针序列，禁止事件穿透到仍作为回退保留的原生 Bouncer。 */
    override fun onTouchEvent(event: MotionEvent): Boolean = dispatchPatternTouchEvent(event)

    /**
     * 统一处理父宿主和内部全屏绘制层收到的事件。
     *
     * [PixelHostView] 自身的通用手势路由会消费无目标的 ACTION_DOWN，因此必须在它进入引擎
     * 路由前把完整序列交给图案跟踪器；SOS 独立子节点仍位于绘制层上方并保留原生点击语义。
     */
    private fun dispatchPatternTouchEvent(event: MotionEvent): Boolean {
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
        if (
            lastRequest?.state?.isEmergencyAvailable == true &&
            currentLayout.containsEmergency(point.first, point.second)
        ) {
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
            if (
                lastRequest?.state?.isEmergencyAvailable == true &&
                point != null &&
                currentLayout.containsEmergency(point.first, point.second)
            ) {
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
            pixelGapEnabled = currentAppearance?.pixelGapEnabled == true,
        )

    /** 把共享点大小、形状、间隙和主题背景应用到图案宿主。 */
    private fun applyAppearance(appearance: LockscreenAppearance) {
        if (currentAppearance == appearance) return
        currentAppearance = appearance
        /** 当前主题用作开启 GAP 后的熄灭像素底色。 */
        val palette = ProductThemeCatalog.resolve(appearance.themeFamily, appearance.brightness)
        pixelHostView.profilePolicy = PixelHostProfilePolicy.AdaptivePixels(
            dotSizePx = appearance.dotSizePx,
            pixelShape = appearance.pixelShape,
        )
        pixelHostView.setPixelGapEnabled(appearance.pixelGapEnabled)
        pixelHostView.offPixelColor = if (appearance.pixelGapEnabled) {
            palette.background
        } else {
            PixelColor.Transparent
        }
        pixelHostView.textRasterizer = appearance.defaultTextRasterizer.fitProductTextWithin(
            maxHeight = CREDENTIAL_FONT_HEIGHT,
        )
        updateLogicalLayout(width, height, submitScene = false)
    }

    /** 根据当前物理尺寸重算布局，并让手势跟踪器与场景原子切换到相同几何。 */
    private fun updateLogicalLayout(widthPx: Int, heightPx: Int, submitScene: Boolean) {
        if (widthPx <= 0 || heightPx <= 0) return
        /** 当前有效外观。 */
        val appearance = currentAppearance ?: return
        /** AdaptivePixels 将生成的真实逻辑尺寸。 */
        val logicalSize = lockscreenLogicalSize(widthPx, heightPx, appearance.dotSizePx)
        /** 新物理尺寸对应的图案布局。 */
        val nextLayout = patternCredentialLayout(logicalSize.first, logicalSize.second)
        if (nextLayout == currentLayout) return
        cancelGesture()
        currentLayout = nextLayout
        gestureTracker.updateLayout(nextLayout)
        if (submitScene) {
            lastRequest = lastRequest?.copy(layout = nextLayout)
            submitCurrentScene()
            requestLayout()
        }
    }

    /** 使用固定逻辑尺寸解析紧急按钮的真实物理边界。 */
    private fun resolveEmergencyBounds(
        /** Android 宿主物理宽度。 */
        viewWidth: Int,
        /** Android 宿主物理高度。 */
        viewHeight: Int,
        /** 当前方屏逻辑布局。 */
        layout: PatternCredentialLayout,
    ): Rect? {
        /** 与当前 AdaptivePixels 场景一致的临时屏幕配置。 */
        val appearance = currentAppearance ?: return null
        val profile = ScreenProfile(
            logicalWidth = layout.logicalWidth,
            logicalHeight = layout.logicalHeight,
            dotSizePx = appearance.dotSizePx,
            pixelShape = appearance.pixelShape,
        )
        /** 与 Pixel Engine 绘制共用的物理网格几何。 */
        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            profile = profile,
            viewportPolicy = pixelHostView.viewportPolicy,
            pixelGapEnabled = appearance.pixelGapEnabled,
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

/** 图案提示与紧急入口允许的最大单行逻辑字高。 */
private const val CREDENTIAL_FONT_HEIGHT: Int = 7
