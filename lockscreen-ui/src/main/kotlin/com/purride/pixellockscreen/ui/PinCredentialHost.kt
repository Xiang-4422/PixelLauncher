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
 * PIN 认证使用的透明像素键盘宿主。
 *
 * 所有可见内容由 [PixelHostView] 绘制；透明 Android 子节点只提供精确触摸与无障碍语义，
 * 每次按键仅向外发送一个数字或动作，不在 UI 层保存完整 PIN。
 */
public class PinCredentialHost(
    /** 创建像素宿主使用的 Android 上下文。 */
    context: Context,
    /** 接收数字、删除、确认、紧急和回退事件的监听器。 */
    private val listener: PinCredentialListener,
) : FrameLayout(context) {
    /** 实际执行全部可见绘制的 Pixel Engine 宿主。 */
    private val pixelHostView: PixelHostView = PixelHostView(context)

    /** 按稳定编号保存的十二个透明键盘语义节点。 */
    private val keyAccessibilityViews: Map<Int, View>

    /** 独立于输入禁用状态的透明紧急操作语义节点。 */
    private val emergencyAccessibilityView: View = View(context)

    /** 当前点大小和宿主尺寸解析出的逻辑布局。 */
    private var currentLayout: PinCredentialLayout = pinCredentialLayout()

    /** 最近一次外部提交的完整产品外观。 */
    private var currentAppearance: LockscreenAppearance? = null

    /** 最近一次非敏感渲染请求。 */
    private var lastRequest: PinCredentialSceneRequest? = null

    /** 手动回退触摸序列使用的主指针 ID。 */
    private var activePointerId: Int = MotionEvent.INVALID_POINTER_ID

    /** 手动触摸序列最初命中的按键编号。 */
    private var activeKeyId: Int? = null

    /** 当前绘制按下高亮的按键编号。 */
    private var pressedKeyId: Int? = null

    /** 监听器异常后永久停止继续采集。 */
    private var interactionFailed: Boolean = false

    /** 宿主是否已经释放。 */
    private var disposed: Boolean = false

    /** 配置像素绘制层和所有透明语义按键。 */
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

        keyAccessibilityViews = currentLayout.keys.associate { key ->
            key.id to createActionView(
                accessibilityLabel = when (key.id) {
                    PIN_KEY_DELETE -> "DELETE"
                    PIN_KEY_CONFIRM -> "CONFIRM"
                    else -> key.label
                },
                keyId = key.id,
                action = { dispatchKey(key.id) },
            )
        }
        keyAccessibilityViews.values.forEach { view -> addView(view, LayoutParams(0, 0)) }
        configureActionView(
            view = emergencyAccessibilityView,
            accessibilityLabel = "EMERGENCY",
            keyId = EMERGENCY_KEY_ID,
            action = { safelyNotify(listener::onEmergencyRequested) },
        )
        addView(emergencyAccessibilityView, LayoutParams(0, 0))
    }

    /** 提交非敏感 PIN 长度、反馈和主题。 */
    public fun update(
        state: PinCredentialUiState,
        appearance: LockscreenAppearance,
    ) {
        check(!disposed) { "PinCredentialHost 已释放" }
        applyAppearance(appearance)
        if (!state.isInputEnabled) {
            clearPointerState()
        }
        /** 本次完整非敏感请求。 */
        val request = PinCredentialSceneRequest(
            state = state,
            family = appearance.themeFamily,
            brightness = appearance.brightness,
            pressedKeyId = pressedKeyId,
            layout = currentLayout,
        )
        if (!shouldSubmitPinCredentialRequest(lastRequest, request)) {
            return
        }
        lastRequest = request
        contentDescription = listOf(state.promptText, state.feedbackText)
            .filter(String::isNotBlank)
            .joinToString(separator = ". ")
        keyAccessibilityViews.values.forEach { view -> view.isEnabled = state.isInputEnabled }
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

    /** 按固定方屏布局为所有透明语义节点提供真实物理尺寸。 */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        currentLayout.keys.forEach { key ->
            /** 当前按键对应的透明语义节点。 */
            val view = keyAccessibilityViews.getValue(key.id)
            /** 当前按键对应的真实物理边界。 */
            val bounds = resolveBounds(measuredWidth, measuredHeight, currentLayout, key) ?: return@forEach
            view.measure(
                MeasureSpec.makeMeasureSpec(bounds.width(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(bounds.height(), MeasureSpec.EXACTLY),
            )
        }
        /** 当前紧急入口的真实物理边界。 */
        val emergencyBounds = resolveEmergencyBounds(measuredWidth, measuredHeight, currentLayout)
        if (emergencyBounds != null) {
            emergencyAccessibilityView.measure(
                MeasureSpec.makeMeasureSpec(emergencyBounds.width(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(emergencyBounds.height(), MeasureSpec.EXACTLY),
            )
        }
    }

    /** 按 Pixel Engine 的相同物理网格定位全部透明语义节点。 */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        currentLayout.keys.forEach { key ->
            /** 当前按键的真实物理边界。 */
            val bounds = resolveBounds(width, height, currentLayout, key) ?: return@forEach
            keyAccessibilityViews.getValue(key.id).layout(
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
            )
        }
        /** 当前紧急入口的真实物理边界。 */
        val emergencyBounds = resolveEmergencyBounds(width, height, currentLayout) ?: return
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

    /** 消费透明子节点未命中的回退触摸序列，禁止事件落到原生 PIN 页面。 */
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

    /** 点击语义由具体透明按键承担，宿主只消费回退事件。 */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** 分离窗口时立即清除按下状态。 */
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

    /** 创建一个具备点击、焦点和按下动画语义的透明键盘节点。 */
    private fun createActionView(
        accessibilityLabel: String,
        keyId: Int,
        action: () -> Unit,
    ): View = View(context).also { view ->
        configureActionView(view, accessibilityLabel, keyId, action)
    }

    /** 配置一个透明 Android 动作节点。 */
    private fun configureActionView(
        view: View,
        accessibilityLabel: String,
        keyId: Int,
        action: () -> Unit,
    ) {
        view.setBackgroundColor(Color.TRANSPARENT)
        view.contentDescription = accessibilityLabel
        view.isClickable = true
        view.isFocusable = true
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> setPressedKey(if (keyId == EMERGENCY_KEY_ID) null else keyId)
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> setPressedKey(null)
            }
            false
        }
        view.setOnClickListener { action() }
    }

    /** 开始一个透明子节点未接管的主指针序列。 */
    private fun handlePointerDown(event: MotionEvent) {
        activePointerId = event.getPointerId(0)
        /** 当前物理落点对应的逻辑坐标。 */
        val point = mapToLogical(event.x, event.y) ?: return
        /** 当前逻辑落点对应的键盘按键。 */
        val key = currentLayout.keyAt(point.first, point.second)
        /** 当前状态是否允许键盘输入。 */
        val inputEnabled = lastRequest?.state?.isInputEnabled == true
        activeKeyId = when {
            lastRequest?.state?.isEmergencyAvailable == true &&
                currentLayout.containsEmergency(point.first, point.second) -> EMERGENCY_KEY_ID
            inputEnabled -> key?.id
            else -> null
        }
        setPressedKey(activeKeyId?.takeUnless { keyId -> keyId == EMERGENCY_KEY_ID })
        parent?.requestDisallowInterceptTouchEvent(activeKeyId != null)
    }

    /** 指针移动时只在仍位于最初按键内时保留高亮。 */
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
        val remainsInside = point != null && containsAction(activeKeyId, point.first, point.second)
        setPressedKey(
            if (remainsInside) activeKeyId?.takeUnless { keyId -> keyId == EMERGENCY_KEY_ID } else null,
        )
    }

    /** 抬起主指针并在仍命中原动作时发送唯一事件。 */
    private fun handlePointerUp(event: MotionEvent) {
        if (event.getPointerId(event.actionIndex) != activePointerId) {
            return
        }
        /** 当前抬起点对应的逻辑坐标。 */
        val point = mapToLogical(event.getX(event.actionIndex), event.getY(event.actionIndex))
        /** 抬起时仍命中的原动作。 */
        val actionKeyId = activeKeyId?.takeIf { keyId ->
            point != null && containsAction(keyId, point.first, point.second)
        }
        clearPointerState()
        when (actionKeyId) {
            EMERGENCY_KEY_ID -> safelyNotify(listener::onEmergencyRequested)
            null -> Unit
            else -> dispatchKey(actionKeyId)
        }
        performClick()
    }

    /** 判断指定动作编号是否包含当前逻辑坐标。 */
    private fun containsAction(keyId: Int?, logicalX: Int, logicalY: Int): Boolean = when (keyId) {
        EMERGENCY_KEY_ID -> lastRequest?.state?.isEmergencyAvailable == true &&
            currentLayout.containsEmergency(logicalX, logicalY)
        null -> false
        else -> currentLayout.keys.firstOrNull { key -> key.id == keyId }
            ?.contains(logicalX, logicalY) == true
    }

    /** 把稳定按键编号映射为不含完整 PIN 的单一监听事件。 */
    private fun dispatchKey(keyId: Int) {
        when (keyId) {
            in 0..9 -> safelyNotify { listener.onDigitEntered(('0'.code + keyId).toChar()) }
            PIN_KEY_DELETE -> safelyNotify(listener::onDeleteRequested)
            PIN_KEY_CONFIRM -> safelyNotify(listener::onConfirmRequested)
            else -> error("pin_key_id:$keyId")
        }
    }

    /** 更新按下高亮并只重建当前非敏感场景。 */
    private fun setPressedKey(keyId: Int?) {
        if (pressedKeyId == keyId) {
            return
        }
        pressedKeyId = keyId
        val previous = lastRequest ?: return
        lastRequest = previous.copy(pressedKeyId = keyId)
        submitCurrentScene()
    }

    /** 清除主指针、高亮和父级拦截限制。 */
    private fun clearPointerState() {
        activePointerId = MotionEvent.INVALID_POINTER_ID
        activeKeyId = null
        setPressedKey(null)
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
            pixelGapEnabled = currentAppearance?.pixelGapEnabled == true,
        )

    /** 把共享点大小、形状、间隙和主题背景应用到 PIN 宿主。 */
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

    /** 根据当前物理尺寸重算 PIN 几何，并原子更新场景与透明语义节点。 */
    private fun updateLogicalLayout(widthPx: Int, heightPx: Int, submitScene: Boolean) {
        if (widthPx <= 0 || heightPx <= 0) return
        /** 当前有效外观。 */
        val appearance = currentAppearance ?: return
        /** AdaptivePixels 将生成的真实逻辑尺寸。 */
        val logicalSize = lockscreenLogicalSize(widthPx, heightPx, appearance.dotSizePx)
        /** 新物理尺寸对应的 PIN 布局。 */
        val nextLayout = pinCredentialLayout(logicalSize.first, logicalSize.second)
        if (nextLayout == currentLayout) return
        clearPointerState()
        currentLayout = nextLayout
        if (submitScene) {
            lastRequest = lastRequest?.copy(layout = nextLayout)
            submitCurrentScene()
            requestLayout()
        }
    }

    /** 解析一个 PIN 按键的真实物理边界。 */
    private fun resolveBounds(
        viewWidth: Int,
        viewHeight: Int,
        layout: PinCredentialLayout,
        key: PinKeySpec,
    ): Rect? = resolveBounds(
        viewWidth,
        viewHeight,
        layout,
        key.left,
        key.top,
        key.width,
        key.height,
    )

    /** 解析紧急入口的真实物理边界。 */
    private fun resolveEmergencyBounds(
        viewWidth: Int,
        viewHeight: Int,
        layout: PinCredentialLayout,
    ): Rect? = resolveBounds(
        viewWidth,
        viewHeight,
        layout,
        layout.emergencyLeft,
        layout.emergencyTop,
        layout.emergencyWidth,
        layout.emergencyHeight,
    )

    /** 使用固定逻辑尺寸把任意逻辑矩形映射到真实物理边界。 */
    private fun resolveBounds(
        viewWidth: Int,
        viewHeight: Int,
        layout: PinCredentialLayout,
        logicalLeft: Int,
        logicalTop: Int,
        logicalWidth: Int,
        logicalHeight: Int,
    ): Rect? {
        /** 与当前 AdaptivePixels 场景一致的临时屏幕配置。 */
        val appearance = currentAppearance ?: return null
        val profile = ScreenProfile(
            layout.logicalWidth,
            layout.logicalHeight,
            dotSizePx = appearance.dotSizePx,
            pixelShape = appearance.pixelShape,
        )
        /** 与 Pixel Engine 绘制共用的物理网格几何。 */
        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth,
            viewHeight,
            profile,
            pixelHostView.viewportPolicy,
            pixelGapEnabled = appearance.pixelGapEnabled,
        ) ?: return null
        return Rect(
            (geometry.originX + logicalLeft * geometry.cellSize).toInt(),
            (geometry.originY + logicalTop * geometry.cellSize).toInt(),
            (geometry.originX + (logicalLeft + logicalWidth) * geometry.cellSize).toInt(),
            (geometry.originY + (logicalTop + logicalHeight) * geometry.cellSize).toInt(),
        )
    }

    /** 重新提交当前非敏感请求。 */
    private fun submitCurrentScene() {
        if (disposed) {
            return
        }
        /** 当前待绘制请求。 */
        val request = lastRequest ?: return
        pixelHostView.setContent { buildPinCredentialScene(request) }
    }

    /** 捕获监听器异常、停止采集并通知模块恢复原生页面。 */
    private fun safelyNotify(action: () -> Unit) {
        if (interactionFailed) {
            return
        }
        runCatching(action).onFailure { throwable ->
            interactionFailed = true
            runCatching { listener.onInteractionFailure(throwable) }
        }
    }

    private companion object {
        /** 紧急入口仅在宿主内部使用的动作编号。 */
        const val EMERGENCY_KEY_ID: Int = 12
    }
}

/** PIN 按键和提示区允许的最大单行逻辑字高。 */
private const val CREDENTIAL_FONT_HEIGHT: Int = 7
