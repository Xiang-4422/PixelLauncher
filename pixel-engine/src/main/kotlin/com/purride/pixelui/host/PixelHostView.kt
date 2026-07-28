package com.purride.pixelui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.InputDevice
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelFrameView
import com.purride.pixelcore.PixelGridGeometry
import com.purride.pixelcore.PixelGridGeometryResolver
import com.purride.pixelcore.PixelShape
import com.purride.pixelcore.PixelViewportPolicy
import com.purride.pixelcore.PixelViewportQuantization
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelengine.PixelEngine
import com.purride.pixelui.animation.PixelTickerProviderFactory
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.internal.PixelClickTarget
import com.purride.pixelui.gesture.NestedScrollGesturePolicy
import com.purride.pixelui.gesture.PagerGesturePolicy
import com.purride.pixelui.host.PixelFrameScheduler
import com.purride.pixelui.host.PixelHostFrameScope
import com.purride.pixelui.host.PixelHostFrameScopeDiagnostics
import com.purride.pixelui.host.AndroidPixelMotionSettingsSource
import com.purride.pixelui.host.AndroidPixelHostCapabilitiesSource
import com.purride.pixelui.host.PixelHostCapabilitiesSource
import com.purride.pixelui.host.PixelMotionSettingsSource
import com.purride.pixelui.PixelScrollPhysics
import com.purride.pixelui.internal.PixelPagerTarget
import com.purride.pixelui.internal.PixelRenderResult
import com.purride.pixelui.internal.PixelListTarget
import com.purride.pixelui.internal.PixelRefreshTarget
import com.purride.pixelui.internal.PixelScrollbarTarget
import com.purride.pixelui.internal.PixelSliderTarget
import com.purride.pixelui.internal.PixelTextInputTarget
import com.purride.pixelui.internal.host.PixelJoystickFocusRouter
import com.purride.pixelui.internal.host.PixelHostAccessibilityNodeProvider
import com.purride.pixelui.internal.host.AndroidPixelHostBackRegistrar
import com.purride.pixelui.internal.host.PixelHostPlatformBackCallbacks
import com.purride.pixelui.internal.host.PixelHostPlatformBackController
import com.purride.pixelui.internal.host.PixelHostPredictiveBackSession
import com.purride.pixelui.internal.host.handlePixelHostBack
import com.purride.pixelui.internal.host.mapAndroidKeyCodeToPixelKeyEvent
import com.purride.pixelui.internal.host.mapAndroidKeyCodeToPixelTextInputEvent
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

    /**
     * 当前 Host 绑定的 Engine 实例。
     *
     * 默认每个 Host 创建独立 Engine；赋值时会原子切换后续帧使用的服务、主题和 capability。
     */
    public var engine: PixelEngine = PixelEngine.Builder().build()
        set(value) {
            if (field === value) return
            field = value
            capabilitiesOverride = value.services.hostCapabilities
            frameScheduler = value.services.frameScheduler
            invalidate()
        }

    /** 绑定一个 Engine，并返回当前 Host 以便装配链继续配置。 */
    public fun bindEngine(engine: PixelEngine): PixelHostView = apply {
        this.engine = engine
    }

    override var interactionListener: PixelFrameView.InteractionListener? = null

    /**
     * 当前生效的逻辑屏幕配置。
     *
     * 该值始终由 [profilePolicy] 与当前视口、密度、[viewportPolicy] 解析得到，不能直接赋值；
     * 需要固定网格时请赋 [PixelHostProfilePolicy.Fixed]。
     */
    public var screenProfile: ScreenProfile = ScreenProfile(
        logicalWidth = 96,
        logicalHeight = 96,
        dotSizePx = 8,
    )
        private set(value) {
            if (field == value) return
            field = value
            markEffectiveCapabilitiesDirty()
            reprojectPlatformInsets()
            requestApplyInsets()
            invalidate()
        }

    /**
     * 当前 Host 的 canonical 视口策略。
     *
     * 控制 contain/cover、整数/分数缩放与对齐；默认值 Contain + Integer + Center 就是引擎的
     * canonical 默认策略。绘制、命中映射和 inset 投影共用同一份策略解析出的几何。
     */
    public var viewportPolicy: PixelViewportPolicy = PixelViewportPolicy()
        set(value) {
            if (field == value) return
            field = value
            updateScreenProfileFromPolicy()
            markEffectiveCapabilitiesDirty()
            reprojectPlatformInsets()
            requestApplyInsets()
            invalidate()
        }

    /**
     * 逻辑屏幕解析策略；这是 Host profile 配置的唯一入口。
     *
     * Fixed or adaptive logical-screen policy evaluated against current Host environment.
     *
     * Assigning [PixelHostProfilePolicy.Fixed] replaces [screenProfile] immediately. Adaptive
     * policies wait for a non-empty viewport and then re-evaluate after size, density or viewport
     * policy changes.
     */
    public var profilePolicy: PixelHostProfilePolicy = PixelHostProfilePolicy.Fixed(screenProfile)
        set(value) {
            if (field == value) return
            field = value
            updateScreenProfileFromPolicy()
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
     * 当前系统栏 / 窗口 inset，单位是 pixel-engine 逻辑像素。
     *
     * Android 宿主会在 [onApplyWindowInsets] 中自动更新；测试或自定义宿主可直接
     * 赋值，或调用 [setWindowInsets]。
     */
    public var windowInsets: PixelWindowInsets = PixelWindowInsets.Zero
        set(value) {
            if (!applyingProjectedPlatformInsets) {
                platformWindowInsetsOwned = false
            }
            if (field == value) return
            field = value
            invalidate()
        }

    /**
     * 当前临时遮挡 inset，单位是 pixel-engine 逻辑像素。
     *
     * 它和 [windowInsets] 分开：后者表示系统栏等稳定安全区，前者表示 IME 键盘等
     * 临时遮挡。
     */
    public var viewInsets: PixelWindowInsets = PixelWindowInsets.Zero
        set(value) {
            if (!applyingProjectedPlatformInsets) {
                platformViewInsetsOwned = false
            }
            if (field == value) return
            field = value
            invalidate()
        }

    /** Latest physical system-bar plus cutout-safe inset snapshot supplied by Android. */
    private var rawPlatformWindowInsets: PixelPhysicalInsets = PixelPhysicalInsets.Zero

    /** Latest physical IME obscuration supplied or inferred from Android WindowInsets. */
    private var rawPlatformViewInsets: PixelPhysicalInsets = PixelPhysicalInsets.Zero

    /** Physical display-cutout rectangles retained for later logical capability projection. */
    private var rawPlatformCutoutBounds: List<Rect> = emptyList()

    /** Whether [windowInsets] should be recomputed from [rawPlatformWindowInsets]. */
    private var platformWindowInsetsOwned: Boolean = false

    /** Whether [viewInsets] should be recomputed from [rawPlatformViewInsets]. */
    private var platformViewInsetsOwned: Boolean = false

    /** Prevents internal projection assignments from being mistaken for manual public overrides. */
    private var applyingProjectedPlatformInsets: Boolean = false

    internal var lastRenderResult: PixelRenderResult?
        get() = renderCoordinator.lastRenderResult
        set(value) { renderCoordinator.lastRenderResult = value }
    private var pixelGapEnabled: Boolean = true
    private var pixelGapRatio: Float = 1.0f
    /** Click target captured by the current pointer down; taps never retarget to a later snapshot. */
    internal var capturedClickTarget: PixelClickTarget? = null
    /** Slider target captured by the current pointer down and reconciled after every rendered snapshot. */
    internal var activeSliderTarget: PixelSliderTarget? = null
    /** Click target currently receiving pressed=true until release, cancellation, or takeover. */
    internal var activePressedClickTarget: PixelClickTarget? = null
    /** Click target currently receiving mouse or stylus hover feedback. */
    internal var hoveredClickTarget: PixelClickTarget? = null
    /** Slider target currently receiving mouse or stylus hover feedback. */
    internal var hoveredSliderTarget: PixelSliderTarget? = null
    /** Scrollbar target captured by the current pointer sequence. */
    internal var activeScrollbarTarget: PixelScrollbarTarget? = null
    /** Scrollbar target currently receiving mouse or stylus hover feedback. */
    internal var hoveredScrollbarTarget: PixelScrollbarTarget? = null
    internal var activeSwipeTarget: PixelClickTarget? = null
    internal var candidateSwipeTarget: PixelClickTarget? = null
    /** Refresh target that has crossed gesture arbitration and owns an active pull. */
    internal var activeRefreshTarget: PixelRefreshTarget? = null
    /** Refresh target captured at down but not yet promoted to an active pull. */
    internal var candidateRefreshTarget: PixelRefreshTarget? = null
    /** Refresh target currently receiving mouse or stylus hover feedback. */
    internal var hoveredRefreshTarget: PixelRefreshTarget? = null
    /** Pointer-to-thumb offset retained so scrollbar drags never jump under the pointer. */
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
    internal var lastClickTapTimeMs: Long = -1L
    internal var lastClickTapSource: Any? = null
    internal var pendingClickTapSource: Any? = null
    /** Delayed single-tap owner, refreshed from each target snapshot before its runnable fires. */
    internal var pendingClickTarget: PixelClickTarget? = null
    internal var pendingClickRunnable: Runnable? = null
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
        set(value) {
            nestedScrollSession.focusedTextInputTarget = value
            platformBackController.refresh()
        }
    private val gestureRouter = PixelHostGestureRouter(this)
    private val joystickFocusRouter = PixelJoystickFocusRouter()
    private val accessibilityNodeProvider = PixelHostAccessibilityNodeProvider(this)
    private val textInputCoordinator = PixelHostTextInputCoordinator(this)
    private val renderCoordinator = PixelHostRenderCoordinator(this, textInputCoordinator)
    private val lifecycleCoordinator = PixelHostLifecycleCoordinator(::handleLifecycleDiagnosticsChanged)

    /** Platform motion observer; internal replacement keeps Android settings deterministic in tests. */
    private var motionSettingsSource: PixelMotionSettingsSource = AndroidPixelMotionSettingsSource(context)

    /** Latest platform snapshot retained across ordinary detach/reattach cycles. */
    private var systemMotionSettings: PixelMotionSettings = motionSettingsSource.currentSettings

    /** Android configuration/contrast/display observer; replaceable only in module tests. */
    private var hostCapabilitiesSource: PixelHostCapabilitiesSource =
        AndroidPixelHostCapabilitiesSource(context)

    /** Latest automatic Android capability snapshot retained across ordinary detach cycles. */
    private var systemHostCapabilities: HostCapabilitiesData =
        hostCapabilitiesSource.currentCapabilities

    /** Host 级预测返回会话，保证取消手势不会提前修改输入焦点或 widget 返回栈。 */
    private val predictiveBackSession = PixelHostPredictiveBackSession(
        hasFocusedTextInput = { focusedTextInputTarget != null },
        clearFocusedTextInput = ::clearFocusedTextInput,
        backDispatcher = { backDispatcher },
        onUnhandledBack = { onUnhandledBack },
        onSessionChanged = ::invalidate,
    )

    /** Android API 33/34 平台 callback 的 attach/注册生命周期控制器。 */
    private val platformBackController = PixelHostPlatformBackController(
        registrar = AndroidPixelHostBackRegistrar(this),
        shouldRegister = {
            lifecycleCoordinator.isInteractive &&
                androidPredictiveBackEnabled &&
                (
                    focusedTextInputTarget != null ||
                        backDispatcher?.hasRegisteredHandlers == true ||
                        onUnhandledBack != null
                )
        },
        callbacks = object : PixelHostPlatformBackCallbacks {
            override fun onBackStarted(event: PixelPredictiveBackEvent) {
                handlePredictiveBackStarted(event)
            }

            override fun onBackProgressed(event: PixelPredictiveBackEvent) {
                handlePredictiveBackProgressed(event)
            }

            override fun onBackCancelled() {
                handlePredictiveBackCancelled()
            }

            override fun onBackInvoked() {
                handlePredictiveBackCommitted()
            }
        },
    )

    /** 当前 Dispatcher 的 handler 可用性监听句柄。 */
    private var backAvailabilityRegistration: PixelBackRegistration? = null

    /** terminal destroy 资源是否已经完成一次性释放。 */
    private var terminalResourcesDisposed: Boolean = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable = true
        isFocusableInTouchMode = true
    }

    /** 当前 Host 唯一的平台能力来源，即所绑定 Engine 装配好的 typed capability 集合。 */
    internal val effectiveHostServices: PixelHostCapabilitySet
        get() = engine.services.hostServices

    /**
 * 公开 `PixelHostView` 的 `capabilitiesOverride` 配置或运行值。
 *
     * 完整 Host 环境快照的唯一覆盖入口。
     *
     * `null` 表示跟随 Android 的 locale、方向、文字缩放、对比度、密度、刷新率与动效设置，并原子地
     * 合并逻辑 cutout 特性。非 null 值是权威的完整快照，此后不再跟随平台变化；只想改动个别字段时
     * 请从 [hostCapabilities] 派生。该属性必须在拥有当前 Host 的线程上赋值。
     *
     * Only application-owned override for the complete Host environment snapshot.
     *
     * `null` follows Android locale, direction, text scale, contrast, density, refresh rate and
     * animator settings, merged atomically with logical cutout features. A non-null value is the
     * authoritative complete snapshot and no longer tracks later platform changes; build it from
     * [hostCapabilities] to adjust only selected fields. Callers must assign this property on the
     * thread that owns this Host.
     */
    public var capabilitiesOverride: HostCapabilitiesData? = null
        set(value) {
            if (field == value) return
            /** Density visible to adaptive-dp profiles before replacing the complete snapshot. */
            val previousDensity = effectiveProfileDensity()
            field = value
            if (previousDensity != effectiveProfileDensity()) updateScreenProfileFromPolicy()
            markEffectiveCapabilitiesDirty()
            invalidate()
        }

    /** Whether the next automatic capability read must rebuild its immutable merged snapshot. */
    private var effectiveCapabilitiesDirty: Boolean = true

    /** Reusable merged snapshot avoiding defensive-list copies on every rendered frame. */
    private var cachedAutomaticCapabilities: HostCapabilitiesData = HostCapabilitiesData.Default

    /**
 * 公开 `PixelHostView` 的 `hostCapabilities` 配置或运行值。
 *
     * 下一帧 widget 树将继承的完整不可变环境快照。
     *
     * 安装了 [capabilitiesOverride] 时直接返回该覆盖值，否则返回实时 Android 快照与当前 Host 的
     * 动效设置、逻辑显示特性的合并结果。读取它是基于当前平台值构造部分覆盖的受支持做法。
     *
     * Complete immutable environment snapshot inherited by the next rendered widget tree.
     *
     * It returns [capabilitiesOverride] when one is installed, otherwise the live Android snapshot
     * merged with this Host's animator settings and logical display features. Reading it is the
     * supported way to build a partial [capabilitiesOverride] from current platform values.
     */
    public val hostCapabilities: HostCapabilitiesData
        get() {
            capabilitiesOverride?.let { explicitSnapshot -> return explicitSnapshot }
            if (effectiveCapabilitiesDirty) {
                /** Android snapshot plus Host-owned motion and logical display-feature projection. */
                cachedAutomaticCapabilities = systemHostCapabilities.copy(
                    motionSettings = systemMotionSettings,
                    displayFeatures = resolveLogicalDisplayFeatures(),
                )
                effectiveCapabilitiesDirty = false
            }
            return cachedAutomaticCapabilities
        }

    /** Invalidates the lazily merged automatic capability snapshot. */
    private fun markEffectiveCapabilitiesDirty() {
        effectiveCapabilitiesDirty = true
    }

    /**
     * Replaces the Android observer with a fake source for Host integration tests.
     *
     * The previous source is terminally released and the replacement follows current View
     * attachment immediately. This internal seam is deliberately absent from the public API.
     */
    internal fun replaceMotionSettingsSourceForTesting(source: PixelMotionSettingsSource) {
        if (motionSettingsSource === source || terminalResourcesDisposed) return
        motionSettingsSource.destroy()
        motionSettingsSource = source
        systemMotionSettings = source.currentSettings
        markEffectiveCapabilitiesDirty()
        if (isAttachedToWindow) {
            source.attach(::handleSystemMotionSettingsChanged)
        }
        if (capabilitiesOverride == null) invalidate()
    }

    /** Replaces Android configuration observation with a deterministic source for integration tests. */
    internal fun replaceHostCapabilitiesSourceForTesting(source: PixelHostCapabilitiesSource) {
        if (hostCapabilitiesSource === source || terminalResourcesDisposed) return
        /** Density visible before replacing the automatic Android capability source. */
        val previousDensity = effectiveProfileDensity()
        hostCapabilitiesSource.destroy()
        hostCapabilitiesSource = source
        systemHostCapabilities = source.currentCapabilities
        if (capabilitiesOverride == null && previousDensity != effectiveProfileDensity()) {
            updateScreenProfileFromPolicy()
        }
        markEffectiveCapabilitiesDirty()
        if (isAttachedToWindow) {
            source.attach(::handleSystemHostCapabilitiesChanged, display?.displayId)
        }
        if (capabilitiesOverride == null) invalidate()
    }

    /** 返回 attachment 与 owner lifecycle 两个状态轴的当前诊断快照。 */
    public val lifecycleDiagnostics: PixelHostLifecycleDiagnostics
        get() = lifecycleCoordinator.diagnostics()

    /** 当前 Host 是否同时满足 attach 与 resumed/unmanaged，可供内部输入适配器 gating。 */
    internal val isLifecycleInteractive: Boolean
        get() = lifecycleCoordinator.isInteractive

    /**
     * 显式绑定一个 [LifecycleOwner]。
     *
     * 显式 owner 优先于 [androidx.lifecycle.ViewTreeLifecycleOwner]；绑定后会立即同步 owner
     * 的当前状态，并在 owner destroy 时终结 Host。
     */
    public fun bindLifecycleOwner(owner: LifecycleOwner) {
        lifecycleCoordinator.bindExplicitLifecycleOwner(owner)
    }

    /**
     * 解除显式或自动 owner；若 View 已 attach，会优先恢复当前 ViewTree owner。
     *
     * 没有 ViewTree owner 时回到 attach 即活跃的 unmanaged 兼容模式。
     */
    public fun unbindLifecycleOwner() {
        lifecycleCoordinator.unbindLifecycleOwner()
        if (isAttachedToWindow && !lifecycleCoordinator.isDestroyed) {
            lifecycleCoordinator.updateViewTreeLifecycleOwner(findViewTreeLifecycleOwner())
        }
    }

    /** 非 Android-owner 宿主显式进入 started；重复或迟到调用会被安全忽略。 */
    public fun start() {
        lifecycleCoordinator.start()
    }

    /** 非 Android-owner 宿主显式进入 resumed，允许动态渲染、输入和返回处理。 */
    public fun resume() {
        lifecycleCoordinator.resume()
    }

    /** [PixelFrameView] 兼容入口，委托给新的 owner lifecycle 状态机。 */
    override fun onHostResume() {
        resume()
    }

    /** 非 Android-owner 宿主显式暂停动态渲染、输入和返回处理，但保留 retained tree。 */
    public fun pause() {
        lifecycleCoordinator.pause()
    }

    /** [PixelFrameView] 兼容入口，暂停输入、动态渲染与 Host 私有 frame scope。 */
    override fun onHostPause() {
        pause()
    }

    /** 非 Android-owner 宿主显式进入 stopped，但保留 retained tree 供后续恢复。 */
    public fun stop() {
        lifecycleCoordinator.stop()
    }

    /** 进入不可逆终态并释放 observer、retained tree、输入会话和像素缓存。 */
    public fun destroy() {
        lifecycleCoordinator.destroy()
    }

    /**
     * widget 返回栈调度器。
     *
     * 设置后 Host 会只在存在已启用 handler 时注册 Android 33+ 系统 callback，根页面不会
     * 因空 dispatcher 吞掉系统返回。
     */
    public var backDispatcher: PixelBackDispatcher? = null
        set(value) {
            if (field === value) return
            predictiveBackSession.cancel()
            backAvailabilityRegistration?.dispose()
            field = value
            bindBackAvailabilityListener()
            platformBackController.refresh()
        }

    /**
     * 输入与 widget 栈均未处理时的 app fallback。
     *
     * 非 `null` 表示 app 主动接管系统返回，因此 API 33+ Host 会保持平台注册；callback
     * 应在返回 `true` 时完成 app 级动作。
     */
    public var onUnhandledBack: (() -> Boolean)? = null
        set(value) {
            if (field === value) return
            predictiveBackSession.cancel()
            field = value
            platformBackController.refresh()
        }

    /**
     * 是否允许 View 自动接入 Android 系统预测返回。
     *
     * API 34+ 提供 start/progress/cancel/commit；API 33 只有 commit；API 24–32 保留
     * [handleBackPressed] 手动兼容入口。关闭时不会向平台注册 callback。
     */
    public var androidPredictiveBackEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            platformBackController.refresh()
        }
    /** 提供 `PixelHostView` 当前管理的 `pagerGesturePolicy` 内容；写入后由所属对象在下一次状态同步时生效。 */
    public var pagerGesturePolicy: PagerGesturePolicy = PagerGesturePolicy.Default
    /** 记录 `PixelHostView` 的 `nestedScrollPolicy` 配置或运行值，读取与更新均遵守所属类型约束；写入后由所属对象在下一次状态同步时生效。 */
    public var nestedScrollPolicy: NestedScrollGesturePolicy = NestedScrollGesturePolicy.Default
    /** 记录 `PixelHostView` 的 `scrollPhysics` 配置或运行值，读取与更新均遵守所属类型约束；写入后由所属对象在下一次状态同步时生效。 */
    public var scrollPhysics: PixelScrollPhysics = PixelScrollPhysics.Default
    /**
     * 当前 Host 私有 frame scope 的上游帧源。
     *
     * 替换帧源会终态释放旧 scope 及其 ticker，并创建一个遵循当前 Host lifecycle 的新 scope。
     */
    public var frameScheduler: PixelFrameScheduler = PixelFrameScheduler.Default
        set(value) {
            /** Engine ticker 工厂变化时，即使上游 scheduler 相同也必须重建 Host scope。 */
            val tickerProviderFactory = engine.services.tickerProviderFactory
            if (field === value && frameScopeTickerProviderFactory === tickerProviderFactory) return
            val previousScope = frameScope
            val replacementScope = PixelHostFrameScope(value, tickerProviderFactory)
            when {
                lifecycleCoordinator.isDestroyed -> replacementScope.dispose()
                !lifecycleCoordinator.isInteractive -> replacementScope.pause()
            }
            field = value
            frameScopeTickerProviderFactory = tickerProviderFactory
            frameScope = replacementScope
            previousScope.dispose()
            invalidate()
        }

    /** Host 独占的 frame/ticker 生命周期边界，构造时因尚未 attach 而保持暂停。 */
    /** 当前 frame scope 创建时使用的 ticker 工厂，用于识别 Engine 服务切换。 */
    private var frameScopeTickerProviderFactory: PixelTickerProviderFactory =
        engine.services.tickerProviderFactory

    /** 当前 Host 私有 frame/ticker scope。 */
    private var frameScope: PixelHostFrameScope = PixelHostFrameScope(
        frameScheduler,
        frameScopeTickerProviderFactory,
    ).apply {
        pause()
    }

    /**
     * 当前 Host 私有的 ticker provider。
     *
     * 替换 [frameScheduler] 后该属性会返回新 provider，旧 provider 会随旧 scope 一并 dispose。
     */
    public val tickerProvider: PixelTickerProvider
        get() = frameScope.tickerProvider

    /**
     * 返回当前 Host 私有 frame scope 的只读资源诊断快照。
     *
     * 快照只包含基础类型计数，不暴露 callback、listener 或 ticker 引用；适合在调试、
     * 生命周期验收和长时 soak 的非热路径中按需读取。
     */
    public val frameScopeDiagnostics: PixelHostFrameScopeDiagnostics
        get() = frameScope.diagnostics()

    /** 保存 `PixelHostView` 对外传递的 `textRasterizer` 数据；写入后由所属对象在下一次状态同步时生效。 */
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

    /**
 * 公开 `PixelHostView` 的 `frameDiagnosticsEnabled` 配置或运行值。
 *
     * Enables collection of the latest full-pipeline frame diagnostics without requiring an
     * observer callback.
     *
     * The default is false. When this property is false and [frameDiagnosticsObserver] is null,
     * the Host skips ART sampling, phase clock reads, and diagnostics snapshot allocation. Enable
     * it only for Inspector, benchmark, or troubleshooting sessions.
     */
    public var frameDiagnosticsEnabled: Boolean = false

    /**
 * 公开 `PixelHostView` 的 `frameDiagnosticsObserver` 配置或运行值。
 *
     * Optional UI-thread observer for complete build/layout/paint/submit/Android-draw diagnostics.
     *
     * A non-null observer implicitly enables sampling even when [frameDiagnosticsEnabled] is
     * false. The callback runs synchronously at the end of `onDraw`; it must not block or mutate
     * the Host recursively. Assign null to restore the allocation-bounded release path.
     */
    public var frameDiagnosticsObserver: ((PixelHostFrameDiagnostics) -> Unit)? = null

    /** 公开 `PixelHostView` 的 `latestFrameDiagnostics` 配置或运行值。
 *
 * Most recently completed diagnostics snapshot, or null before opt-in sampling produces one.
 */
    public val latestFrameDiagnostics: PixelHostFrameDiagnostics?
        get() = renderCoordinator.snapshotFrameDiagnostics()

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
    /** 方形整数提交时复用的非透明逻辑像素边界，避免每帧创建临时 Rect。 */
    private val reusableActivePixelBounds = Rect()
    /** 方形 gap 位图提交时复用的浮点目标区域，保留 fractional viewport 的几何精度。 */
    private val reusableDestRectF = RectF()

    /** 更新 `PixelHostView` 的 `setContent` 状态，并保持相关边界与派生状态一致。 */
    public fun setContent(provider: RootWidgetProvider) {
        renderCoordinator.setContent(provider)
    }

    internal fun requestRender() { invalidate() }

    /** 更新 `PixelHostView` 的 `updateFocusedTextInput` 状态，并保持相关边界与派生状态一致。 */
    public fun updateFocusedTextInput(
        text: String,
        selectionStart: Int = text.length,
        selectionEnd: Int = selectionStart,
        compositionStart: Int = -1,
        compositionEnd: Int = -1,
    ) {
        if (!lifecycleCoordinator.isInteractive) return
        textInputCoordinator.updateFocusedTextInput(
            text = text,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            compositionStart = compositionStart,
            compositionEnd = compositionEnd,
        )
    }

    /** 从 `PixelHostView` 释放 `clearFocusedTextInput` 对应内容；重复调用按既有幂等约束处理。 */
    public fun clearFocusedTextInput() {
        textInputCoordinator.clearFocusedTextInput()
    }

    /** 向 `PixelHostView` 提交 `submitFocusedTextInput` 数据或事件，并按所属类型的顺序与所有权规则保存。 */
    public fun submitFocusedTextInput() {
        if (!lifecycleCoordinator.isInteractive) return
        textInputCoordinator.submitFocusedTextInput()
    }

    /**
     * 处理宿主 back 事件。
     *
     * 顺序固定为：先关闭文本输入，再交给 widget back 栈，最后交给 app fallback。
     */
    public fun handleBackPressed(): Boolean {
        if (!lifecycleCoordinator.isInteractive) return false
        predictiveBackSession.cancel()
        return handlePixelHostBack(
            hasFocusedTextInput = focusedTextInputTarget != null,
            clearFocusedTextInput = ::clearFocusedTextInput,
            backDispatcher = backDispatcher,
            onUnhandledBack = onUnhandledBack,
            onHandled = ::invalidate,
        )
    }

    /**
     * 开始一条可取消的预测返回会话。
     *
     * 自定义非 Android Host 也可调用该入口；返回 `false` 表示当前 Pixel 树没有消费者。
     */
    public fun handlePredictiveBackStarted(event: PixelPredictiveBackEvent): Boolean {
        if (!lifecycleCoordinator.isInteractive) return false
        return predictiveBackSession.start(event)
    }

    /** 把同一手势的最新进度发送给 start 时锁定的消费者。 */
    public fun handlePredictiveBackProgressed(event: PixelPredictiveBackEvent) {
        if (!lifecycleCoordinator.isInteractive) return
        predictiveBackSession.progress(event)
    }

    /** 取消当前预测返回并回滚临时视觉状态；重复调用安全。 */
    public fun handlePredictiveBackCancelled() {
        predictiveBackSession.cancel()
    }

    /** 提交当前预测返回；API 33 或硬件返回在没有 start 时自动走离散兼容路径。 */
    public fun handlePredictiveBackCommitted(): Boolean {
        if (!lifecycleCoordinator.isInteractive) {
            predictiveBackSession.cancel()
            return false
        }
        return predictiveBackSession.commit()
    }

    /**
     * 对当前聚焦的 TextField 执行 [action]。
     *
     * 没有聚焦字段、选区或剪贴板内容不足以执行该动作时返回 `false`。
     */
    public fun performFocusedTextEditAction(action: PixelTextEditAction): Boolean {
        if (!lifecycleCoordinator.isInteractive) return false
        return textInputCoordinator.performEditAction(action)
    }

    /**
     * 返回最近一次已提交逻辑像素帧的深拷贝。
     *
     * 这是宿主级快照入口，业务层可用于冻结当前画面，而不需要访问 internal render result。
     */
    public fun snapshotCurrentFrameBuffer(): PixelBuffer? {
        return lastRenderResult?.buffer?.copy()
    }

    /**
 * 执行 `PixelHostView` 的 `dispatchPixelKeyEvent` 公开行为；具体参数、返回和副作用见下文。
 *
     * Dispatches a normalized non-text key to this Host's runtime-local focus tree.
     *
     * 每个 Host 只驱动自己的焦点树；可打印文本请改用 [dispatchPixelTextInput]。
     */
    public fun dispatchPixelKeyEvent(event: PixelKeyEvent): Boolean {
        if (!lifecycleCoordinator.isInteractive) return false
        /** 本 Host 的聚焦节点链是否消费了该归一化按键。 */
        val handled = renderCoordinator.focusOwner.dispatchKeyEvent(event)
        if (handled) invalidate()
        return handled
    }

    /**
 * 执行 `PixelHostView` 的 `dispatchPixelTextInput` 公开行为；具体参数、返回和副作用见下文。
 *
     * Dispatches exact text to this Host's runtime-local focused node chain.
     *
     * Supplementary-plane、组合簇和多 code point 的 IME 提交都保持为一次事件，不会被拆分，也
     * 不会退化成 [PixelKeyEvent]。
     *
     * @param event Exact text payload produced by an IME, hardware key, or custom Host bridge.
     * @return `true` when a focused text handler consumed the complete payload.
     */
    public fun dispatchPixelTextInput(event: PixelTextInputEvent): Boolean {
        if (!lifecycleCoordinator.isInteractive) return false
        /** Whether this Host's focused node chain consumed the complete text payload. */
        val handled = renderCoordinator.focusOwner.dispatchTextInputEvent(event)
        if (handled) invalidate()
        return handled
    }

    /** Routes Android key-down events through exact text first, then non-text key dispatch. */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (!lifecycleCoordinator.isInteractive) return super.dispatchKeyEvent(event)
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            /** Exact scalar payload, or `null` when this Android key has dedicated non-text meaning. */
            val textInputEvent = event.toPixelTextInputEvent()
            if (textInputEvent != null) {
                if (dispatchPixelTextInput(textInputEvent)) return true
            } else if (dispatchPixelKeyEvent(event.toPixelKeyEvent())) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Routes joystick and gamepad axes through the same runtime-local focus tree. */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!lifecycleCoordinator.isInteractive) return super.dispatchGenericMotionEvent(event)
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
            if (keyEvent != null && dispatchPixelKeyEvent(keyEvent)) return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun submitFrame(pixelBuffer: PixelBuffer, screenProfile: ScreenProfile, backgroundColor: PixelColor) {
        renderCoordinator.submitFrame(pixelBuffer, screenProfile, backgroundColor)
    }

    override fun setPixelGapEnabled(enabled: Boolean) {
        if (pixelGapEnabled == enabled) return
        pixelGapEnabled = enabled
        if (!enabled) {
            recycleGapBackgroundBitmap()
        }
        reprojectPlatformInsets()
        invalidate()
    }

    /**
     * 设置像素间隙大小比例（0.0 = 无间隙，1.0 = 最大间隙）。
     * 当 [setPixelGapEnabled] 为 false 时本值无效。
     */
    public fun setPixelGapRatio(ratio: Float) {
        pixelGapRatio = ratio.coerceIn(0f, 1f)
        reprojectPlatformInsets()
        invalidate()
    }

    /**
     * 手动设置逻辑窗口 inset。
     *
     * Android 宿主通常依赖 [onApplyWindowInsets] 自动注入；测试或自定义宿主可用它
     * 注入已转换好的逻辑 inset。
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
     * 手动设置逻辑视图遮挡 inset。
     *
     * Android 宿主通常依赖 [onApplyWindowInsets] 自动注入；测试或自定义宿主可用它
     * 注入已转换好的 IME 或临时遮挡 inset。
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

    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider {
        return accessibilityNodeProvider
    }

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

    /** 执行 `PixelHostView` 的 `dumpSemanticsTree` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns an indented stable-id semantic tree with state, actions, and logical bounds.
 */
    public fun dumpSemanticsTree(): String {
        val nodes = lastRenderResult?.semanticsNodes.orEmpty()
        if (nodes.isEmpty()) return "<empty semantics>"
        val nodesById = nodes.associateBy(PixelSemanticsNode::id)
        return nodes.joinToString(separator = "\n") { node ->
            buildString {
                repeat(semanticsDepth(node, nodesById)) { append("  ") }
                append(node.role)
                append(" label=\"").append(node.label).append('"')
                append(" enabled=").append(node.enabled)
                append(" focused=").append(node.focused)
                append(" id=").append(node.id)
                append(" parent=").append(node.parentId ?: "HOST")
                node.value?.let { value -> append(" value=\"").append(value).append('"') }
                append(" selected=").append(node.selected)
                node.checked?.let { checked -> append(" checked=").append(checked) }
                node.expanded?.let { expanded -> append(" expanded=").append(expanded) }
                append(" actions=").append(node.actions)
                append(" bounds=")
                    .append(node.left).append(',')
                    .append(node.top).append(',')
                    .append(node.width).append(',')
                    .append(node.height)
            }
        }
    }

    /** Computes one node's diagnostic depth while tolerating malformed or missing parent links. */
    private fun semanticsDepth(
        node: PixelSemanticsNode,
        nodesById: Map<Long, PixelSemanticsNode>,
    ): Int {
        var parentId = node.parentId
        var depth = 0
        val visitedIds = mutableSetOf<Long>()
        while (parentId != null && visitedIds.add(parentId)) {
            val parent = nodesById[parentId] ?: break
            depth += 1
            parentId = parent.parentId
        }
        return depth
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
        /** 附加整帧诊断前的基础统计快照。 */
        val snapshot = PixelInspectorSnapshot(
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
        snapshot.attachFrameDiagnostics(renderCoordinator.snapshotFrameDiagnostics())
        return snapshot
    }

    /**
     * [destroy] 的终态兼容入口。
     *
     * 普通 detach 不再销毁 retained tree；Fragment 或自定义宿主可在明确终态调用本方法。
     */
    public fun dispose() {
        destroy()
    }

    /** 绑定当前 Dispatcher 的可用性变化；View 重新 attach 后可安全重建该监听。 */
    private fun bindBackAvailabilityListener() {
        backAvailabilityRegistration?.dispose()
        if (lifecycleCoordinator.isDestroyed) {
            backAvailabilityRegistration = null
            return
        }
        backAvailabilityRegistration = backDispatcher?.addAvailabilityListener {
            platformBackController.refresh()
        }
    }

    /** 根据正交生命周期快照同步渲染、输入、手势与系统返回 gating。 */
    private fun handleLifecycleDiagnosticsChanged(diagnostics: PixelHostLifecycleDiagnostics) {
        if (diagnostics.isInteractive) {
            frameScope.resume()
        } else {
            frameScope.pause()
        }
        renderCoordinator.setLifecycleActive(diagnostics.isInteractive)
        accessibilityNodeProvider.onHostInteractiveChanged(diagnostics.isInteractive)
        if (!diagnostics.isInteractive) {
            predictiveBackSession.cancel()
            cancelPendingClick()
            gestureRouter.cancelActiveGesture()
            effectiveHostServices.hideTextInput()
        }
        platformBackController.refresh()
        if (diagnostics.lifecycleState == PixelHostLifecycleState.Destroyed) {
            disposeTerminalResources()
        }
    }

    /** 只执行一次终态资源释放；该方法不会反向推进 lifecycle 状态机。 */
    private fun disposeTerminalResources() {
        if (terminalResourcesDisposed) return
        terminalResourcesDisposed = true
        platformBackController.detach()
        predictiveBackSession.dispose()
        backAvailabilityRegistration?.dispose()
        backAvailabilityRegistration = null
        cancelPendingClick()
        gestureRouter.cancelActiveGesture()
        textInputCoordinator.clearFocusedTextInput()
        effectiveHostServices.hideTextInput()
        frameScope.dispose()
        motionSettingsSource.destroy()
        hostCapabilitiesSource.destroy()
        renderCoordinator.dispose()
        accessibilityNodeProvider.dispose()
        recycleGapBackgroundBitmap()
        reusableBitmap?.recycle()
        reusableBitmap = null
        lastTextInputTapState = null
        lastClickTapSource = null
        backDispatcher = null
        onUnhandledBack = null
        frameStatsObserver = null
        frameDiagnosticsObserver = null
        frameDiagnosticsEnabled = false
        interactionListener = null
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
        renderCoordinator.beginAndroidFrame()
        try {
            renderCoordinator.beginAndroidDraw()
            try {
                super.onDraw(canvas)
            } finally {
                renderCoordinator.endAndroidDraw()
            }
            /** Retained build/layout/paint result measured independently from Android drawing. */
            val renderResult = renderCoordinator.renderFrame()
            renderCoordinator.beginAndroidDraw()
            try {
                canvas.drawColor(bezelColor.argb)
            } finally {
                renderCoordinator.endAndroidDraw()
            }
            if (renderResult != null) {
                renderCoordinator.beginBufferSubmit()
                try {
                    drawBuffer(canvas, renderResult.buffer)
                    renderCoordinator.recordBufferSubmit(renderResult.buffer)
                } finally {
                    renderCoordinator.endBufferSubmit()
                }
                renderCoordinator.beginAndroidDraw()
                try {
                    accessibilityNodeProvider.notifySemanticsChanged()
                } finally {
                    renderCoordinator.endAndroidDraw()
                }
            }
        } finally {
            renderCoordinator.endAndroidFrame()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == oldw && h == oldh) return
        updateScreenProfileFromPolicy()
        reprojectPlatformInsets()
    }

    @Suppress("DEPRECATION")
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            /** Current physical system-bar edges. */
            val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
            /** Current physical IME obscuration. */
            val ime = insets.getInsets(WindowInsets.Type.ime())
            /** Physical display-cutout safe edges, independent from bar visibility. */
            val cutoutSafe = insets.getInsets(WindowInsets.Type.displayCutout())
            rawPlatformWindowInsets = PixelPhysicalInsets(
                left = maxOf(systemBars.left, cutoutSafe.left),
                top = maxOf(systemBars.top, cutoutSafe.top),
                right = maxOf(systemBars.right, cutoutSafe.right),
                bottom = maxOf(systemBars.bottom, cutoutSafe.bottom),
            )
            rawPlatformViewInsets = PixelPhysicalInsets(
                left = ime.left,
                top = ime.top,
                right = ime.right,
                bottom = ime.bottom,
            )
            rawPlatformCutoutBounds = insets.displayCutout?.boundingRects
                ?.map(::Rect)
                .orEmpty()
        } else {
            /** Legacy combined system/IME inset snapshot. */
            val systemWindow = PixelPhysicalInsets(
                left = insets.systemWindowInsetLeft,
                top = insets.systemWindowInsetTop,
                right = insets.systemWindowInsetRight,
                bottom = insets.systemWindowInsetBottom,
            )
            /** Legacy stable system-bar baseline used to identify transient IME obscuration. */
            val stableWindow = PixelPhysicalInsets(
                left = insets.stableInsetLeft,
                top = insets.stableInsetTop,
                right = insets.stableInsetRight,
                bottom = insets.stableInsetBottom,
            )
            /** Version-independent split shared with pure JVM regression tests. */
            val split = splitLegacyPlatformInsets(
                systemWindow = systemWindow,
                stableWindow = stableWindow,
            )
            /** API 28–29 cutout object; older releases have no display-cutout API. */
            val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                insets.displayCutout
            } else {
                null
            }
            /** Physical cutout-safe edges merged into stable view padding. */
            val cutoutSafe = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && cutout != null) {
                PixelPhysicalInsets(
                    left = cutout.safeInsetLeft,
                    top = cutout.safeInsetTop,
                    right = cutout.safeInsetRight,
                    bottom = cutout.safeInsetBottom,
                )
            } else {
                PixelPhysicalInsets.Zero
            }
            rawPlatformWindowInsets = split.systemBars.maxWith(cutoutSafe)
            rawPlatformViewInsets = split.ime
            rawPlatformCutoutBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                cutout?.boundingRects?.map(::Rect).orEmpty()
            } else {
                emptyList()
            }
        }
        platformWindowInsetsOwned = true
        platformViewInsetsOwned = true
        markEffectiveCapabilitiesDirty()
        reprojectPlatformInsets()
        hostCapabilitiesSource.refresh()
        return super.onApplyWindowInsets(insets)
    }

    /** View 进入 Window 后再查询 dispatcher，避免在构造期注册到错误或空 Window。 */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (lifecycleCoordinator.isDestroyed) return
        motionSettingsSource.attach(::handleSystemMotionSettingsChanged)
        hostCapabilitiesSource.attach(
            onChanged = ::handleSystemHostCapabilitiesChanged,
            displayId = display?.displayId,
        )
        lifecycleCoordinator.updateViewTreeLifecycleOwner(findViewTreeLifecycleOwner())
        lifecycleCoordinator.attach()
        if (backAvailabilityRegistration == null) {
            bindBackAvailabilityListener()
        }
        platformBackController.attach()
    }

    override fun onDetachedFromWindow() {
        platformBackController.detach()
        motionSettingsSource.detach()
        hostCapabilitiesSource.detach()
        lifecycleCoordinator.detach()
        super.onDetachedFromWindow()
    }

    /** Rebinds automatic capabilities after a View-level configuration or display change. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (terminalResourcesDisposed) return
        hostCapabilitiesSource.updateDisplay(display?.displayId)
        hostCapabilitiesSource.refresh()
        reprojectPlatformInsets()
        requestApplyInsets()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!lifecycleCoordinator.isInteractive) return super.onTouchEvent(event)
        return gestureRouter.onTouchEvent(event) ?: super.onTouchEvent(event)
    }

    /** Routes only real mouse/stylus hover; touchscreen hover remains available to TalkBack. */
    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) {
            return accessibilityNodeProvider.dispatchTouchExplorationHover(event) || super.onHoverEvent(event)
        }
        if (!lifecycleCoordinator.isInteractive) return super.onHoverEvent(event)
        return gestureRouter.onHoverEvent(event) ?: super.onHoverEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** Re-evaluates the configured profile policy from one current Host environment snapshot. */
    private fun updateScreenProfileFromPolicy() {
        /** Policy captured before resolution so re-entrant View callbacks cannot mix policies. */
        val policy = profilePolicy
        if (policy !is PixelHostProfilePolicy.Fixed && (width <= 0 || height <= 0)) return
        /** Resolved profile derived by the same viewport strategy used for painting and input. */
        screenProfile = PixelHostProfileResolver.resolve(
            policy = policy,
            widthPx = width,
            heightPx = height,
            density = effectiveProfileDensity(),
            viewportPolicy = viewportPolicy,
        )
    }

    /** Returns the density currently visible to an adaptive-dp profile. */
    private fun effectiveProfileDensity(): Float {
        return capabilitiesOverride?.density ?: systemHostCapabilities.density
    }

    /**
     * Recomputes logical insets from retained physical Android data after any geometry change.
     *
     * Manually assigned logical [windowInsets] or [viewInsets] remain caller-owned and are not
     * overwritten. Both platform channels are projected in one guarded block so a frame cannot
     * observe a new system-bar transform with a stale IME transform.
     */
    private fun reprojectPlatformInsets() {
        if (width <= 0 || height <= 0) return
        markEffectiveCapabilitiesDirty()
        if (!platformWindowInsetsOwned && !platformViewInsetsOwned) return
        applyingProjectedPlatformInsets = true
        try {
            if (platformWindowInsetsOwned) {
                windowInsets = rawPlatformWindowInsets.toLogicalInsets(includeViewportCrop = true)
            }
            if (platformViewInsetsOwned) {
                viewInsets = rawPlatformViewInsets.toLogicalInsets(includeViewportCrop = false)
            }
        } finally {
            applyingProjectedPlatformInsets = false
        }
    }

    /** Projects one retained physical edge snapshot through the current viewport geometry. */
    private fun PixelPhysicalInsets.toLogicalInsets(
        /** Whether permanent cover cropping contributes to this logical inset channel. */
        includeViewportCrop: Boolean,
    ): PixelWindowInsets {
        return lifecycleCoordinator.platformInsetsToLogical(
            leftPx = left,
            topPx = top,
            rightPx = right,
            bottomPx = bottom,
            viewWidth = width,
            viewHeight = height,
            screenProfile = screenProfile,
            viewportPolicy = viewportPolicy,
            includeViewportCrop = includeViewportCrop,
            pixelGapEnabled = pixelGapEnabled,
            pixelGapRatio = pixelGapRatio,
        )
    }

    /** Returns defensive physical cutout rectangles for the Android capability adapter. */
    internal fun rawDisplayCutoutBoundsForCapabilities(): List<Rect> {
        return rawPlatformCutoutBounds.map(::Rect)
    }

    /** Returns the retained physical bar/IME channels for version-branch instrumentation tests. */
    internal fun rawPlatformInsetChannelsForTesting(): PixelLegacyPlatformInsetSplit {
        return PixelLegacyPlatformInsetSplit(
            systemBars = rawPlatformWindowInsets,
            ime = rawPlatformViewInsets,
        )
    }

    /** Injects physical platform insets without constructing version-specific WindowInsets tests. */
    internal fun applyRawPlatformInsetsForTesting(
        /** Stable system-bar and cutout-safe physical edges. */
        windowInsets: PixelPhysicalInsets,
        /** Transient IME physical edges. */
        viewInsets: PixelPhysicalInsets,
        /** Physical cutout rectangles retained for capability conversion. */
        cutoutBounds: List<Rect> = emptyList(),
    ) {
        rawPlatformWindowInsets = windowInsets
        rawPlatformViewInsets = viewInsets
        rawPlatformCutoutBounds = cutoutBounds.map(::Rect)
        platformWindowInsetsOwned = true
        platformViewInsetsOwned = true
        markEffectiveCapabilitiesDirty()
        reprojectPlatformInsets()
    }

    /** Converts retained physical cutout rectangles through the current shared grid geometry. */
    private fun resolveLogicalDisplayFeatures(): List<PixelDisplayFeature> {
        /** Platform-neutral features supplied directly by the capability source or a test fake. */
        val sourceFeatures = systemHostCapabilities.displayFeatures
        if (rawPlatformCutoutBounds.isEmpty()) return sourceFeatures
        /** Current physical-to-logical transform shared with paint, pointer and Accessibility. */
        val geometry = resolveGridGeometry() ?: return sourceFeatures
        /** Positive uniform cell scale used for exact floating-point feature coordinates. */
        val cellSize = geometry.cellSize.coerceAtLeast(1f)
        /** Cutout features derived without retaining mutable Android Rect objects. */
        val cutoutFeatures = rawPlatformCutoutBounds.map { physicalBounds ->
            PixelDisplayFeature(
                bounds = PixelLogicalRect(
                    left = (physicalBounds.left - geometry.originX) / cellSize,
                    top = (physicalBounds.top - geometry.originY) / cellSize,
                    right = (physicalBounds.right - geometry.originX) / cellSize,
                    bottom = (physicalBounds.bottom - geometry.originY) / cellSize,
                ),
                type = PixelDisplayFeatureType.CUTOUT,
                state = PixelDisplayFeatureState.UNKNOWN,
            )
        }
        return (sourceFeatures + cutoutFeatures).distinct()
    }

    internal fun resolveClickTarget(logicalX: Int, logicalY: Int): PixelClickTarget? {
        return lastRenderResult?.clickTargets?.lastOrNull { it.bounds.contains(logicalX, logicalY) }
    }

    internal fun resolveSwipeTarget(logicalX: Int, logicalY: Int): PixelClickTarget? {
        return lastRenderResult?.clickTargets?.lastOrNull {
            it.bounds.contains(logicalX, logicalY) && it.hasSwipe
        }
    }

    /** Reconciles pointer and hover ownership whenever a new target snapshot becomes current. */
    internal fun reconcileInteractionTargets(renderResult: PixelRenderResult) {
        gestureRouter.reconcileTargets(renderResult)
        pendingClickTarget?.let { previous ->
            val replacement = renderResult.clickTargets.lastOrNull { candidate ->
                if (previous.source != null && candidate.source != null) {
                    previous.source === candidate.source
                } else {
                    previous === candidate
                }
            }
            if (replacement != null) {
                pendingClickTarget = replacement
            } else {
                cancelPendingClick()
            }
        }
    }

    internal fun cancelPendingClick() {
        pendingClickRunnable?.let(::removeCallbacks)
        pendingClickRunnable = null
        pendingClickTapSource = null
        pendingClickTarget = null
    }

    internal fun schedulePendingClick(target: PixelClickTarget, delayMillis: Long) {
        cancelPendingClick()
        val source = target.source
        val runnable = Runnable {
            val currentTarget = pendingClickTarget
            if (
                lifecycleCoordinator.isInteractive &&
                pendingClickTapSource === source &&
                currentTarget != null
            ) {
                pendingClickRunnable = null
                pendingClickTapSource = null
                pendingClickTarget = null
                currentTarget.onClick.invoke()
                invalidate()
            } else if (pendingClickTapSource === source) {
                pendingClickRunnable = null
                pendingClickTapSource = null
                pendingClickTarget = null
            }
        }
        pendingClickTapSource = source
        pendingClickTarget = target
        pendingClickRunnable = runnable
        postDelayed(runnable, delayMillis)
    }

    internal fun resolveTextInputTarget(logicalX: Int, logicalY: Int): PixelTextInputTarget? {
        return lastRenderResult?.textInputTargets?.lastOrNull { it.bounds.contains(logicalX, logicalY) }
    }

    internal fun focusTextInput(target: PixelTextInputTarget) {
        if (!lifecycleCoordinator.isInteractive) return
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
        val geometry = resolveGridGeometry() ?: return 0f
        val rawVelocity = when (axis) {
            PixelAxis.HORIZONTAL -> velocityTracker?.xVelocity ?: 0f
            PixelAxis.VERTICAL -> velocityTracker?.yVelocity ?: 0f
        }
        return rawVelocity / geometry.cellSize.coerceAtLeast(1f)
    }

    internal fun resolveGridGeometry(): PixelGridGeometry? {
        return PixelGridGeometryResolver.resolve(
            viewWidth = width,
            viewHeight = height,
            profile = screenProfile,
            viewportPolicy = viewportPolicy,
            pixelGapEnabled = pixelGapEnabled,
            pixelGapRatio = pixelGapRatio,
        )
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
            viewportPolicy = viewportPolicy,
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
            val bitmap = updateReusableBufferBitmap(buffer)

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
        /** 一条内部网格线的完整物理宽度。 */
        val gapWidth = geometry.dotInset * 2f
        reusablePaint.color = bezelColor.argb
        val cell = geometry.cellSize
        for (x in 1 until buffer.width) {
            val left = geometry.originX + x * cell - geometry.dotInset
            canvas.drawRect(
                left,
                geometry.originY,
                left + gapWidth,
                geometry.originY + geometry.contentHeight,
                reusablePaint,
            )
        }
        for (y in 1 until buffer.height) {
            val top = geometry.originY + y * cell - geometry.dotInset
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
        if (
            shape == PixelShape.SQUARE &&
            viewportPolicy.quantization == PixelViewportQuantization.INTEGER
        ) {
            drawSquarePixelBitmap(canvas = canvas, buffer = buffer, geometry = geometry)
            return
        }
        for (y in 0 until buffer.height) {
            for (x in 0 until buffer.width) {
                val pixel = buffer.getPixel(x, y)
                if (pixel.alpha <= 0) continue

                val left = geometry.originX + dotLeft(geometry.cellSize, geometry.dotInset, x)
                val top = geometry.originY + dotTop(geometry.cellSize, geometry.dotInset, y)
                val right = geometry.originX + dotRight(geometry.cellSize, geometry.dotInset, buffer.width, x)
                val bottom = geometry.originY + dotBottom(geometry.cellSize, geometry.dotInset, buffer.height, y)
                reusablePaint.color = pixel.argb
                drawPixelShape(canvas, left, top, right, bottom, shape)
            }
        }
    }

    /**
     * 把所有方形逻辑像素合并为一次 Bitmap 提交。
     *
     * 透明像素会露出预先缓存的熄灭点阵背景，后续 bezel overlay 再覆盖内部网格线，
     * 因而最终结果与逐格 `drawRect` 一致，同时避免每个点亮像素一次 Canvas 调用。
     * 该快速路径只用于整数 viewport；fractional viewport 保留逐格路径以维持子像素栅格化语义。
     */
    private fun drawSquarePixelBitmap(
        canvas: Canvas,
        buffer: PixelBuffer,
        geometry: PixelGridGeometry,
    ) {
        if (!resolveNonTransparentPixelBounds(buffer, reusableActivePixelBounds)) return
        /** 只包含当前非透明逻辑边界的复用位图，避免把大面积透明区域上传到 GPU。 */
        val bitmap = updateReusableBufferBitmapRegion(
            buffer = buffer,
            left = reusableActivePixelBounds.left,
            top = reusableActivePixelBounds.top,
            regionWidth = reusableActivePixelBounds.width(),
            regionHeight = reusableActivePixelBounds.height(),
        )
        reusableDestRectF.set(
            geometry.originX + reusableActivePixelBounds.left * geometry.cellSize,
            geometry.originY + reusableActivePixelBounds.top * geometry.cellSize,
            geometry.originX + reusableActivePixelBounds.right * geometry.cellSize,
            geometry.originY + reusableActivePixelBounds.bottom * geometry.cellSize,
        )
        canvas.drawBitmap(bitmap, null, reusableDestRectF, null)
    }

    /** 把当前逻辑 buffer 写入尺寸匹配的复用 Bitmap，并返回可直接提交的实例。 */
    private fun updateReusableBufferBitmap(buffer: PixelBuffer): Bitmap {
        return updateReusableBufferBitmapRegion(
            buffer = buffer,
            left = 0,
            top = 0,
            regionWidth = buffer.width,
            regionHeight = buffer.height,
        )
    }

    /**
     * 把当前逻辑 buffer 的指定区域写入尺寸匹配的复用 Bitmap。
     *
     * [left]、[top] 和区域尺寸只来自本类已经验证的非透明边界或完整 buffer 边界。
     */
    private fun updateReusableBufferBitmapRegion(
        buffer: PixelBuffer,
        left: Int,
        top: Int,
        regionWidth: Int,
        regionHeight: Int,
    ): Bitmap {
        /** 仅在逻辑分辨率变化时替换的上一张复用位图。 */
        val existing = reusableBitmap
        /** 与当前待提交区域尺寸完全一致的提交位图。 */
        val bitmap = if (existing != null && existing.width == regionWidth && existing.height == regionHeight) {
            existing
        } else {
            existing?.recycle()
            Bitmap.createBitmap(regionWidth, regionHeight, Bitmap.Config.ARGB_8888).also { reusableBitmap = it }
        }
        /** 区域首像素在原始整帧数组中的偏移。 */
        val sourceOffset = top * buffer.width + left
        bitmap.setPixels(buffer.pixels, sourceOffset, buffer.width, 0, 0, regionWidth, regionHeight)
        return bitmap
    }

    /**
     * 扫描当前 buffer 的非透明像素包围盒并写入 [outBounds]。
     *
     * `PixelBuffer.pixels` 是兼容公开 API，调用方可以直接更新数组，因此这里不能依赖仅由
     * Engine 写入口维护的脏区元数据。单次无分配线性扫描换取更小的 GPU 纹理上传区域。
     */
    private fun resolveNonTransparentPixelBounds(buffer: PixelBuffer, outBounds: Rect): Boolean {
        /** 尚未发现非透明像素时使用的左、上哨兵。 */
        var minimumX = buffer.width
        var minimumY = buffer.height
        /** 尚未发现非透明像素时使用的右、下哨兵。 */
        var maximumX = -1
        var maximumY = -1
        /** 当前线性像素下标，避免热循环重复乘法。 */
        var pixelIndex = 0
        for (y in 0 until buffer.height) {
            for (x in 0 until buffer.width) {
                /** 当前像素只需检查 alpha；RGB 在完全透明时不会影响最终合成。 */
                val alpha = buffer.pixels[pixelIndex] ushr 24
                pixelIndex += 1
                if (alpha == 0) continue
                if (x < minimumX) minimumX = x
                if (x > maximumX) maximumX = x
                if (y < minimumY) minimumY = y
                maximumY = y
            }
        }
        if (maximumX < 0) {
            outBounds.setEmpty()
            return false
        }
        outBounds.set(minimumX, minimumY, maximumX + 1, maximumY + 1)
        return true
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
                val left = dotLeft(geometry.cellSize, geometry.dotInset, x)
                val top = dotTop(geometry.cellSize, geometry.dotInset, y)
                val right = dotRight(geometry.cellSize, geometry.dotInset, logicalWidth, x)
                val bottom = dotBottom(geometry.cellSize, geometry.dotInset, logicalHeight, y)
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

    private fun dotLeft(cell: Float, inset: Float, x: Int): Float =
        x * cell + if (x == 0) 0f else inset

    private fun dotTop(cell: Float, inset: Float, y: Int): Float =
        y * cell + if (y == 0) 0f else inset

    private fun dotRight(cell: Float, inset: Float, width: Int, x: Int): Float =
        (x + 1) * cell - if (x == width - 1) 0f else inset

    private fun dotBottom(cell: Float, inset: Float, height: Int, y: Int): Float =
        (y + 1) * cell - if (y == height - 1) 0f else inset

    internal fun mapTouchToLogical(touchX: Float, touchY: Float): Pair<Int, Int>? {
        return PixelGridGeometryResolver.mapSurfaceToLogical(
            touchX = touchX,
            touchY = touchY,
            viewWidth = width,
            viewHeight = height,
            profile = screenProfile,
            viewportPolicy = viewportPolicy,
            pixelGapEnabled = pixelGapEnabled,
            pixelGapRatio = pixelGapRatio,
        )
    }

    /** Applies one distinct platform settings snapshot and rebuilds inherited motion consumers. */
    private fun handleSystemMotionSettingsChanged(settings: PixelMotionSettings) {
        if (terminalResourcesDisposed || systemMotionSettings == settings) return
        systemMotionSettings = settings
        markEffectiveCapabilitiesDirty()
        if (capabilitiesOverride == null) invalidate()
    }

    /** Applies one distinct Android configuration/contrast/display snapshot atomically. */
    private fun handleSystemHostCapabilitiesChanged(capabilities: HostCapabilitiesData) {
        if (terminalResourcesDisposed || systemHostCapabilities == capabilities) return
        /** Automatic density visible before applying the distinct platform snapshot. */
        val previousDensity = effectiveProfileDensity()
        systemHostCapabilities = capabilities
        if (capabilitiesOverride == null && previousDensity != effectiveProfileDensity()) {
            updateScreenProfileFromPolicy()
        }
        markEffectiveCapabilitiesDirty()
        if (capabilitiesOverride == null) invalidate()
    }
}

/** Immutable Android edge insets retained in physical pixel coordinates. */
internal data class PixelPhysicalInsets(
    /** Physical pixels obscured from the left Host edge. */
    val left: Int = 0,
    /** Physical pixels obscured from the top Host edge. */
    val top: Int = 0,
    /** Physical pixels obscured from the right Host edge. */
    val right: Int = 0,
    /** Physical pixels obscured from the bottom Host edge. */
    val bottom: Int = 0,
) {
    init {
        require(left >= 0 && top >= 0 && right >= 0 && bottom >= 0) {
            "PixelPhysicalInsets edges must be >= 0, got ($left, $top, $right, $bottom)"
        }
    }

    /** Returns the edge-wise maximum used to combine system bars and display-cutout safety. */
    fun maxWith(other: PixelPhysicalInsets): PixelPhysicalInsets {
        return PixelPhysicalInsets(
            left = maxOf(left, other.left),
            top = maxOf(top, other.top),
            right = maxOf(right, other.right),
            bottom = maxOf(bottom, other.bottom),
        )
    }

    /** Canonical empty physical inset snapshot. */
    companion object {
        /** Snapshot containing no physical obscuration. */
        val Zero: PixelPhysicalInsets = PixelPhysicalInsets()
    }
}

/** Physical system-bar and IME channels recovered from a pre-API 30 combined inset snapshot. */
internal data class PixelLegacyPlatformInsetSplit(
    /** Current system-bar edges with transient IME extent removed. */
    val systemBars: PixelPhysicalInsets,
    /** Transient IME edges, or zero when the legacy snapshot reports no larger obscuration. */
    val ime: PixelPhysicalInsets,
)

/**
 * Splits API 24–29 combined system-window insets using stable bar dimensions as the baseline.
 *
 * A transient edge larger than its positive stable counterpart is treated as IME and retains the
 * complete combined extent, matching API 30 `Type.ime()` semantics. When a device supplies no
 * stable value, the current system edge conservatively remains a system bar.
 */
internal fun splitLegacyPlatformInsets(
    /** Current combined system-window inset snapshot. */
    systemWindow: PixelPhysicalInsets,
    /** Stable system-bar dimensions reported by the same WindowInsets object. */
    stableWindow: PixelPhysicalInsets,
): PixelLegacyPlatformInsetSplit {
    /** Resolves one current system-bar edge without including a larger transient IME edge. */
    fun systemBarEdge(current: Int, stable: Int): Int {
        return when {
            current <= 0 -> 0
            stable <= 0 -> current
            else -> minOf(current, stable)
        }
    }

    /** Returns the complete transient edge only when it exceeds a known stable baseline. */
    fun imeEdge(current: Int, stable: Int): Int {
        return if (stable > 0 && current > stable) current else 0
    }

    return PixelLegacyPlatformInsetSplit(
        systemBars = PixelPhysicalInsets(
            left = systemBarEdge(systemWindow.left, stableWindow.left),
            top = systemBarEdge(systemWindow.top, stableWindow.top),
            right = systemBarEdge(systemWindow.right, stableWindow.right),
            bottom = systemBarEdge(systemWindow.bottom, stableWindow.bottom),
        ),
        ime = PixelPhysicalInsets(
            left = imeEdge(systemWindow.left, stableWindow.left),
            top = imeEdge(systemWindow.top, stableWindow.top),
            right = imeEdge(systemWindow.right, stableWindow.right),
            bottom = imeEdge(systemWindow.bottom, stableWindow.bottom),
        ),
    )
}

/** Cached shaped-dot bitmap identity for one complete physical viewport transform. */
private data class GapBackgroundKey(
    /** Physical cached bitmap width. */
    val bitmapWidth: Int,
    /** Physical cached bitmap height. */
    val bitmapHeight: Int,
    /** Logical grid width represented by the bitmap. */
    val logicalWidth: Int,
    /** Logical grid height represented by the bitmap. */
    val logicalHeight: Int,
    /** Physical scale of one logical cell. */
    val cellSize: Float,
    /** Physical interior inset of one shaped dot. */
    val dotInset: Float,
    /** Physical painted dot extent. */
    val dotSize: Float,
    /** 每个逻辑像素点渲染使用的形状。 */
    val pixelShape: PixelShape,
    /** 像素点之间的 ARGB 背景色。 */
    val pixelGridArgb: Int,
)

/** 把一个 Android 按键转换为 canonical 的导航/激活/取消事件模型。 */
private fun android.view.KeyEvent.toPixelKeyEvent(): PixelKeyEvent {
    return mapAndroidKeyCodeToPixelKeyEvent(
        keyCode = keyCode,
        isShiftPressed = isShiftPressed,
    )
}

/** Converts one printable Android key to exact text without narrowing supplementary scalars. */
private fun android.view.KeyEvent.toPixelTextInputEvent(): PixelTextInputEvent? {
    return mapAndroidKeyCodeToPixelTextInputEvent(
        keyCode = keyCode,
        unicodeChar = unicodeChar,
    )
}
