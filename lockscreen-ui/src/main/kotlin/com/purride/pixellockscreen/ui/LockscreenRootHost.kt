package com.purride.pixellockscreen.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.purride.pixelcore.PixelColor
import com.purride.pixeldesign.ProductThemeBrightness
import com.purride.pixeldesign.ProductThemeFamily
import com.purride.pixelui.PixelHostProfilePolicy
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.SizedBox

/**
 * 可由 Showcase 和未来 SystemUI 适配器共同持有的透明像素锁屏宿主。
 *
 * 宿主不访问系统服务、不创建定时器、不注册输入桥；所有状态必须由调用方通过 [update] 提交。
 */
public class LockscreenRootHost @JvmOverloads constructor(
    /** 创建内部 PixelHostView 使用的 Android 上下文。 */
    context: Context,
    /** 可选 XML 属性；当前宿主不声明自定义属性。 */
    attrs: AttributeSet? = null,
    /** 可选的 SystemUI 内容操作转发器；Showcase 和静态宿主保持为空。 */
    private val contentListener: LockscreenContentListener? = null,
) : FrameLayout(context, attrs) {
    /** 实际执行透明像素渲染的唯一子 View。 */
    private val pixelHostView: PixelHostView = PixelHostView(context)

    /** 最近一次已经提交渲染的不可变请求，用于跳过完全相同的更新。 */
    private var lastRequest: LockscreenSceneRequest? = null

    /** 标记宿主资源是否已经释放，释放后拒绝继续更新。 */
    private var disposed: Boolean = false

    /** 初始化透明、无焦点、无输入消费的静态宿主边界。 */
    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        isLongClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

        pixelHostView.setBackgroundColor(Color.TRANSPARENT)
        pixelHostView.bezelColor = PixelColor.Transparent
        pixelHostView.offPixelColor = PixelColor.Transparent
        pixelHostView.setPixelGapEnabled(false)
        pixelHostView.isClickable = false
        pixelHostView.isFocusable = false
        pixelHostView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        pixelHostView.setContent { SizedBox(width = 0, height = 0) }
        addView(
            pixelHostView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        configureProfile()
    }

    /**
     * 原子提交一帧格式化状态和具体主题变体。
     *
     * 完全相同的请求不会重设 Widget 树；AUTO 必须由外部先解析为 [ProductThemeBrightness]。
     */
    public fun update(
        state: LockscreenUiState,
        family: ProductThemeFamily,
        brightness: ProductThemeBrightness,
    ) {
        check(!disposed) { "LockscreenRootHost 已释放" }
        /** 本次待提交的完整不可变渲染请求。 */
        val request = LockscreenSceneRequest(
            state = state,
            family = family,
            brightness = brightness,
            contentListener = contentListener,
        )
        if (!shouldSubmitLockscreenRequest(lastRequest, request)) return
        submitRequest(request)
    }

    /** 仅在运行时提供内容监听器时让像素卡片参与命中，空白区域继续交给原生手势。 */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        if (contentListener == null) false else super.dispatchTouchEvent(event)

    /** 幂等释放 Pixel Engine 运行时和子 View，释放后宿主不可再次使用。 */
    public fun dispose() {
        if (disposed) return
        disposed = true
        lastRequest = null
        pixelHostView.dispose()
        removeAllViews()
    }

    /** 把不可变请求交给 PixelHostView，并记录去重基线。 */
    private fun submitRequest(request: LockscreenSceneRequest) {
        lastRequest = request
        pixelHostView.setContent { buildLockscreenScene(request) }
    }

    /** 固定使用 Titan 2 方屏逻辑网格，物理尺寸变化仅影响统一视口缩放。 */
    private fun configureProfile() {
        pixelHostView.profilePolicy = PixelHostProfilePolicy.AdaptiveLogicalSize(
            logicalWidth = LOCKSCREEN_LOGICAL_WIDTH,
            logicalHeight = LOCKSCREEN_LOGICAL_HEIGHT,
        )
    }
}
