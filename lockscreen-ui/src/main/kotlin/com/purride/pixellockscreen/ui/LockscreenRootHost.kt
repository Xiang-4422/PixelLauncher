package com.purride.pixellockscreen.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.purride.pixelcore.PixelColor
import com.purride.pixeldesign.ProductThemeCatalog
import com.purride.pixeldesign.font.fitProductTextWithin
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

    /** 最近一次外部提交的完整产品外观。 */
    private var currentAppearance: LockscreenAppearance? = null

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
    }

    /**
     * 原子提交一帧格式化状态和具体主题变体。
     *
     * 完全相同的请求不会重设 Widget 树；AUTO 必须由外部先解析进 [LockscreenAppearance]。
     */
    public fun update(
        state: LockscreenUiState,
        appearance: LockscreenAppearance,
    ) {
        check(!disposed) { "LockscreenRootHost 已释放" }
        applyAppearance(appearance)
        /** 当前物理尺寸对应的逻辑宽度，测量前沿用安全基准值。 */
        val logicalWidth = if (width > 0) {
            lockscreenLogicalSize(width, height, appearance.dotSizePx).first
        } else {
            LOCKSCREEN_LOGICAL_WIDTH
        }
        /** 本次待提交的完整不可变渲染请求。 */
        val request = LockscreenSceneRequest(
            state = state,
            family = appearance.themeFamily,
            brightness = appearance.brightness,
            contentListener = contentListener,
            logicalWidth = logicalWidth,
        )
        if (!shouldSubmitLockscreenRequest(lastRequest, request)) return
        submitRequest(request)
    }

    /**
     * 普通锁屏不拦截任何触摸，保证窗口级宿主下方的 SystemUI 上滑与认证手势始终可用。
     *
     * 当前阶段暂停普通页卡片直点；等原生手势仲裁桥具备精确命中与取消转发后再恢复，不能为了
     * 通知或快捷按钮牺牲设备的基础解锁能力。
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        if (ordinaryLockscreenTouchPassesThrough()) false else super.dispatchTouchEvent(event)

    /** 宿主尺寸变化后按真实逻辑宽度重建场景，确保大像素尺寸不会裁切时钟。 */
    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        /** 尚未接收状态时无需创建空场景。 */
        val previous = lastRequest ?: return
        /** 当前外观在新物理尺寸下解析出的逻辑宽度。 */
        val appearance = currentAppearance ?: return
        /** 与 AdaptivePixels 策略相同的逻辑宽度。 */
        val logicalWidth = lockscreenLogicalSize(width, height, appearance.dotSizePx).first
        updateTextRasterizer(appearance, logicalWidth)
        if (previous.logicalWidth != logicalWidth) {
            submitRequest(previous.copy(logicalWidth = logicalWidth))
        }
    }

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

    /** 把共享点大小、形状、间隙和主题背景原子应用到 PixelHostView。 */
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
        /** 当前宿主尺寸可用时立即应用共享字体。 */
        val logicalWidth = lockscreenLogicalSize(width, height, appearance.dotSizePx).first
        updateTextRasterizer(appearance, logicalWidth)
    }

    /** 按时钟的最大整数倍率缩小用户字体，保证方屏宽度内不裁切。 */
    private fun updateTextRasterizer(appearance: LockscreenAppearance, logicalWidth: Int) {
        /** 与场景大时钟选择一致的最大整数倍率。 */
        val timeScale = when {
            logicalWidth >= 132 -> 4
            logicalWidth >= 100 -> 3
            else -> 2
        }
        /** 单倍时钟可使用的安全逻辑宽度。 */
        val baseTimeWidth = ((logicalWidth - 12) / timeScale).coerceAtLeast(1)
        pixelHostView.textRasterizer = appearance.defaultTextRasterizer.fitProductTextWithin(
            sampleText = "88:88",
            maxWidth = baseTimeWidth,
            maxHeight = LOCKSCREEN_BASE_FONT_HEIGHT,
        )
    }
}

/** 返回普通锁屏是否把完整触摸序列交给下层 SystemUI；基础解锁链必须始终优先。 */
internal fun ordinaryLockscreenTouchPassesThrough(): Boolean = true

/** 锁屏布局的标准单倍字高，大时钟在此基础上整数放大。 */
private const val LOCKSCREEN_BASE_FONT_HEIGHT: Int = 7
