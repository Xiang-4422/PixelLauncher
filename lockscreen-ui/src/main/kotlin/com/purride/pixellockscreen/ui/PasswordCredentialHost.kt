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
 * 密码认证使用的透明像素展示与动作宿主。
 *
 * 宿主不创建文本输入框，也不接收密码字符。系统 IME 始终连接原生密码 `EditText`；本宿主只
 * 根据外部提供的长度绘制掩码，并把输入聚焦、IME 切换和紧急入口动作转发给运行时桥。
 */
public class PasswordCredentialHost(
    /** 创建像素宿主使用的 Android 上下文。 */
    context: Context,
    /** 接收输入聚焦、IME 切换、紧急和回退事件的监听器。 */
    private val listener: PasswordCredentialListener,
) : FrameLayout(context) {
    /** 实际执行全部可见绘制的 Pixel Engine 宿主。 */
    private val pixelHostView: PixelHostView = PixelHostView(context)

    /** 请求原生密码输入连接获取焦点的透明语义节点。 */
    private val inputAccessibilityView: View = View(context)

    /** 请求打开系统输入法选择器的透明语义节点。 */
    private val imeSwitcherAccessibilityView: View = View(context)

    /** 请求原生紧急操作的透明语义节点。 */
    private val emergencyAccessibilityView: View = View(context)

    /** 当前方向使用的固定逻辑布局。 */
    private var currentLayout: PasswordCredentialLayout = passwordCredentialLayout(isLandscape = false)

    /** 最近一次非敏感渲染请求。 */
    private var lastRequest: PasswordCredentialSceneRequest? = null

    /** 手动回退触摸序列使用的主指针 ID。 */
    private var activePointerId: Int = MotionEvent.INVALID_POINTER_ID

    /** 手动触摸序列最初命中的公开动作。 */
    private var activeAction: PasswordCredentialAction? = null

    /** 当前绘制按下高亮的公开动作。 */
    private var pressedAction: PasswordCredentialAction? = null

    /** 监听器异常后永久停止继续分发动作。 */
    private var interactionFailed: Boolean = false

    /** 宿主是否已经释放。 */
    private var disposed: Boolean = false

    /** 配置像素绘制层和三个不接收文本的透明语义节点。 */
    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = false
        isFocusableInTouchMode = false
        isSaveEnabled = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

        pixelHostView.setBackgroundColor(Color.TRANSPARENT)
        pixelHostView.bezelColor = PixelColor.Transparent
        pixelHostView.offPixelColor = PixelColor.Transparent
        pixelHostView.setPixelGapEnabled(false)
        pixelHostView.isClickable = false
        pixelHostView.isFocusable = false
        pixelHostView.isSaveEnabled = false
        pixelHostView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        pixelHostView.setContent { SizedBox(width = 0, height = 0) }
        addView(pixelHostView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        configureActionView(
            view = inputAccessibilityView,
            action = PasswordCredentialAction.INPUT,
            accessibilityLabel = "PASSWORD INPUT",
            callback = listener::onInputRequested,
        )
        configureActionView(
            view = imeSwitcherAccessibilityView,
            action = PasswordCredentialAction.IME_SWITCHER,
            accessibilityLabel = "KEYBOARD",
            callback = listener::onImeSwitcherRequested,
        )
        configureActionView(
            view = emergencyAccessibilityView,
            action = PasswordCredentialAction.EMERGENCY,
            accessibilityLabel = "EMERGENCY",
            callback = listener::onEmergencyRequested,
        )
        addView(inputAccessibilityView, LayoutParams(0, 0))
        addView(imeSwitcherAccessibilityView, LayoutParams(0, 0))
        addView(emergencyAccessibilityView, LayoutParams(0, 0))
        configureProfile(currentLayout)
    }

    /** 提交不含密码字符的长度、焦点、入口、反馈和主题。 */
    public fun update(
        state: PasswordCredentialUiState,
        family: ProductThemeFamily,
        brightness: ProductThemeBrightness,
    ) {
        check(!disposed) { "PasswordCredentialHost 已释放" }
        /** 当前物理尺寸解析出的方向。 */
        val isLandscape = width > 0 && height > 0 && width > height
        if (!state.isInputEnabled) {
            clearPointerState()
        }
        /** 本次完整非敏感请求。 */
        val request = PasswordCredentialSceneRequest(
            state = state,
            family = family,
            brightness = brightness,
            isLandscape = isLandscape,
            pressedAction = pressedAction,
        )
        if (!shouldSubmitPasswordCredentialRequest(lastRequest, request)) {
            return
        }
        lastRequest = request
        contentDescription = listOf(state.promptText, state.feedbackText)
            .filter(String::isNotBlank)
            .joinToString(separator = ". ")
        inputAccessibilityView.isEnabled = state.isInputEnabled
        inputAccessibilityView.contentDescription =
            "${state.inputAccessibilityLabel}, ${state.inputLength} CHARACTERS"
        imeSwitcherAccessibilityView.isEnabled = state.isImeSwitcherVisible
        imeSwitcherAccessibilityView.visibility = if (state.isImeSwitcherVisible) {
            View.VISIBLE
        } else {
            View.GONE
        }
        imeSwitcherAccessibilityView.contentDescription = state.imeSwitcherAccessibilityLabel
        emergencyAccessibilityView.contentDescription = state.emergencyAccessibilityLabel
        submitCurrentScene()
        requestLayout()
    }

    /** 尺寸变化时同步 Pixel Engine profile、动作几何和场景方向。 */
    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (disposed || width <= 0 || height <= 0) {
            return
        }
        /** 新方向使用的固定逻辑布局。 */
        val nextLayout = passwordCredentialLayout(isLandscape = width > height)
        currentLayout = nextLayout
        configureProfile(nextLayout)
        /** 保留当前状态和主题，仅切换方向。 */
        val previous = lastRequest ?: return
        if (previous.isLandscape != (width > height)) {
            lastRequest = previous.copy(isLandscape = width > height)
            submitCurrentScene()
        }
    }

    /** 按当前测量方向为三个透明语义节点提供真实物理尺寸。 */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        /** 当前测量尺寸对应的逻辑布局。 */
        val layout = passwordCredentialLayout(isLandscape = measuredWidth > measuredHeight)
        measureActionView(inputAccessibilityView, layout, layout.inputAction)
        measureActionView(emergencyAccessibilityView, layout, layout.emergencyAction)
        if (imeSwitcherAccessibilityView.visibility == View.VISIBLE) {
            measureActionView(imeSwitcherAccessibilityView, layout, layout.imeSwitcherAction)
        } else {
            imeSwitcherAccessibilityView.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY),
            )
        }
    }

    /** 按 Pixel Engine 的相同物理网格定位全部透明语义节点。 */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        layoutActionView(inputAccessibilityView, currentLayout, currentLayout.inputAction)
        layoutActionView(emergencyAccessibilityView, currentLayout, currentLayout.emergencyAction)
        if (imeSwitcherAccessibilityView.visibility == View.VISIBLE) {
            layoutActionView(
                imeSwitcherAccessibilityView,
                currentLayout,
                currentLayout.imeSwitcherAction,
            )
        } else {
            imeSwitcherAccessibilityView.layout(0, 0, 0, 0)
        }
    }

    /** 消费透明子节点未命中的回退触摸序列，禁止事件落到原生可见控件。 */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (disposed || interactionFailed) {
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handlePointerDown(event)
            MotionEvent.ACTION_MOVE -> handlePointerMove(event)
            MotionEvent.ACTION_UP -> handlePointerUp(event)
            MotionEvent.ACTION_CANCEL -> clearPointerState()
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    clearPointerState()
                }
            }
        }
        return true
    }

    /** 点击语义由具体透明动作节点承担，宿主只消费回退事件。 */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** 分离窗口时立即清除按下状态且不尝试保留 IME 焦点。 */
    override fun onDetachedFromWindow() {
        clearPointerState()
        super.onDetachedFromWindow()
    }

    /** 幂等清除触摸状态并释放 Pixel Engine 资源。 */
    public fun dispose() {
        if (disposed) {
            return
        }
        clearPointerState()
        disposed = true
        lastRequest = null
        pixelHostView.dispose()
        removeAllViews()
    }

    /** 配置一个只发送公开动作且不接收文本的透明 Android 语义节点。 */
    private fun configureActionView(
        view: View,
        action: PasswordCredentialAction,
        accessibilityLabel: String,
        callback: () -> Unit,
    ) {
        view.setBackgroundColor(Color.TRANSPARENT)
        view.contentDescription = accessibilityLabel
        view.isClickable = true
        view.isFocusable = true
        view.isSaveEnabled = false
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> setPressedAction(action)
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> setPressedAction(null)
            }
            false
        }
        view.setOnClickListener { safelyNotify(callback) }
    }

    /** 开始一个透明子节点未接管的主指针序列。 */
    private fun handlePointerDown(event: MotionEvent) {
        activePointerId = event.getPointerId(0)
        /** 当前物理落点对应的逻辑坐标。 */
        val point = mapToLogical(event.x, event.y) ?: return
        /** 当前状态是否允许输入和显示 IME 切换入口。 */
        val state = lastRequest?.state ?: return
        /** 当前逻辑落点对应且当前状态允许的动作。 */
        val action = currentLayout.actionAt(
            point.first,
            point.second,
            includeImeSwitcher = state.isImeSwitcherVisible,
        )
        activeAction = action?.takeIf { candidate ->
            candidate != PasswordCredentialAction.INPUT || state.isInputEnabled
        }
        setPressedAction(activeAction)
        parent?.requestDisallowInterceptTouchEvent(activeAction != null)
    }

    /** 指针移动时只在仍位于最初动作内时保留高亮。 */
    private fun handlePointerMove(event: MotionEvent) {
        /** 主指针当前索引。 */
        val pointerIndex = event.findPointerIndex(activePointerId)
        if (pointerIndex < 0) {
            clearPointerState()
            return
        }
        /** 当前物理落点对应的逻辑坐标。 */
        val point = mapToLogical(event.getX(pointerIndex), event.getY(pointerIndex))
        /** 当前最初命中动作是否仍包含移动后的落点。 */
        val remainsInside = point != null && containsAction(
            activeAction,
            point.first,
            point.second,
        )
        setPressedAction(if (remainsInside) activeAction else null)
    }

    /** 抬起主指针并在仍命中原动作时发送唯一事件。 */
    private fun handlePointerUp(event: MotionEvent) {
        if (event.getPointerId(event.actionIndex) != activePointerId) {
            return
        }
        /** 当前抬起点对应的逻辑坐标。 */
        val point = mapToLogical(event.getX(event.actionIndex), event.getY(event.actionIndex))
        /** 抬起时仍命中的原动作。 */
        val action = activeAction?.takeIf { candidate ->
            point != null && containsAction(candidate, point.first, point.second)
        }
        clearPointerState()
        action?.let(::dispatchAction)
        performClick()
    }

    /** 判断指定公开动作是否包含当前逻辑坐标。 */
    private fun containsAction(
        action: PasswordCredentialAction?,
        logicalX: Int,
        logicalY: Int,
    ): Boolean = when (action) {
        PasswordCredentialAction.INPUT -> currentLayout.inputAction.contains(logicalX, logicalY)
        PasswordCredentialAction.IME_SWITCHER -> currentLayout.imeSwitcherAction.contains(
            logicalX,
            logicalY,
        )
        PasswordCredentialAction.EMERGENCY -> currentLayout.emergencyAction.contains(
            logicalX,
            logicalY,
        )
        null -> false
    }

    /** 把公开动作映射为不含任何密码字符的监听事件。 */
    private fun dispatchAction(action: PasswordCredentialAction) {
        safelyNotify(
            when (action) {
                PasswordCredentialAction.INPUT -> listener::onInputRequested
                PasswordCredentialAction.IME_SWITCHER -> listener::onImeSwitcherRequested
                PasswordCredentialAction.EMERGENCY -> listener::onEmergencyRequested
            },
        )
    }

    /** 更新按下高亮并只重建当前非敏感场景。 */
    private fun setPressedAction(action: PasswordCredentialAction?) {
        if (pressedAction == action) {
            return
        }
        pressedAction = action
        val previous = lastRequest ?: return
        lastRequest = previous.copy(pressedAction = action)
        submitCurrentScene()
    }

    /** 清除主指针、高亮和父级拦截限制。 */
    private fun clearPointerState() {
        activePointerId = MotionEvent.INVALID_POINTER_ID
        activeAction = null
        setPressedAction(null)
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    /** 使用与 PixelHostView 绘制相同的几何映射物理触摸。 */
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
    private fun configureProfile(layout: PasswordCredentialLayout) {
        pixelHostView.profilePolicy = PixelHostProfilePolicy.AdaptiveLogicalSize(
            logicalWidth = layout.logicalWidth,
            logicalHeight = layout.logicalHeight,
        )
    }

    /** 按动作逻辑矩形测量一个透明语义节点。 */
    private fun measureActionView(
        view: View,
        layout: PasswordCredentialLayout,
        action: PasswordActionSpec,
    ) {
        /** 当前动作对应的真实物理边界。 */
        val bounds = resolveBounds(measuredWidth, measuredHeight, layout, action) ?: return
        view.measure(
            MeasureSpec.makeMeasureSpec(bounds.width(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(bounds.height(), MeasureSpec.EXACTLY),
        )
    }

    /** 按动作逻辑矩形摆放一个透明语义节点。 */
    private fun layoutActionView(
        view: View,
        layout: PasswordCredentialLayout,
        action: PasswordActionSpec,
    ) {
        /** 当前动作对应的真实物理边界。 */
        val bounds = resolveBounds(width, height, layout, action) ?: return
        view.layout(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    /** 使用固定逻辑尺寸把动作矩形映射到真实物理边界。 */
    private fun resolveBounds(
        viewWidth: Int,
        viewHeight: Int,
        layout: PasswordCredentialLayout,
        action: PasswordActionSpec,
    ): Rect? {
        /** 与当前场景逻辑尺寸一致的临时屏幕配置。 */
        val profile = ScreenProfile(layout.logicalWidth, layout.logicalHeight, dotSizePx = 1)
        /** 与 Pixel Engine 绘制共用的物理网格几何。 */
        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth,
            viewHeight,
            profile,
            pixelHostView.viewportPolicy,
            pixelGapEnabled = false,
        ) ?: return null
        return Rect(
            (geometry.originX + action.left * geometry.cellSize).toInt(),
            (geometry.originY + action.top * geometry.cellSize).toInt(),
            (geometry.originX + (action.left + action.width) * geometry.cellSize).toInt(),
            (geometry.originY + (action.top + action.height) * geometry.cellSize).toInt(),
        )
    }

    /** 重新提交当前非敏感请求。 */
    private fun submitCurrentScene() {
        if (disposed) {
            return
        }
        /** 当前待绘制请求。 */
        val request = lastRequest ?: return
        pixelHostView.setContent { buildPasswordCredentialScene(request) }
    }

    /** 捕获监听器异常、停止分发并通知模块恢复原生页面。 */
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
