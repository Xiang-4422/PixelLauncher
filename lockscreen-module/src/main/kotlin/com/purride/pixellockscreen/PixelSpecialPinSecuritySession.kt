package com.purride.pixellockscreen

import android.content.res.Configuration
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.ViewGroup
import com.purride.pixeldesign.ProductThemeBrightness
import com.purride.pixeldesign.ProductThemeFamily
import com.purride.pixellockscreen.credential.SpecialPinCredentialCoordinator
import com.purride.pixellockscreen.credential.Titan2EmergencyActionBridge
import com.purride.pixellockscreen.credential.Titan2SecurityContainerViewBinding
import com.purride.pixellockscreen.credential.Titan2SpecialPinControllerBinding
import com.purride.pixellockscreen.ui.PinCredentialHost
import com.purride.pixellockscreen.ui.PinCredentialUiState

/**
 * Titan 2 一次 SIM PIN/PUK/ME 或 AntiTheft 展示周期的像素接管会话。
 *
 * 原生页面保持挂载并独占全部输入与校验状态；像素宿主只转发离散按钮事件、镜像掩码长度，
 * 并在任何结构或反射异常时立即恢复原生内容。
 */
internal class PixelSpecialPinSecuritySession(
    /** 当前 SystemUI 主安全容器控制器。 */
    private val securityController: Any,
    /** 当前已经完成原生恢复的 SIM 或 AntiTheft 控制器。 */
    private val specialController: Any,
    /** SystemUI 最终应用类加载器。 */
    private val classLoader: ClassLoader,
    /** 特殊页接管状态变化时同步普通像素锁屏的动作。 */
    private val onTakeoverChanged: (Boolean) -> Unit,
    /** 会话失败时记录脱敏原因的动作。 */
    private val onFailure: (PixelSpecialPinSecuritySession, Throwable) -> Unit,
    /** 会话完全释放后的上层清理动作。 */
    private val onDisposed: (PixelSpecialPinSecuritySession) -> Unit,
) : ViewTreeObserver.OnPreDrawListener,
    ViewTreeObserver.OnDrawListener,
    View.OnAttachStateChangeListener {
    /** 当前特殊页的精确原生控件和模式绑定。 */
    private lateinit var specialBinding: Titan2SpecialPinControllerBinding

    /** 当前主安全容器的可覆盖 ViewGroup 绑定。 */
    private lateinit var containerBinding: Titan2SecurityContainerViewBinding

    /** 当前特殊页继承的 ROM 原生紧急操作桥。 */
    private lateinit var emergencyBridge: Titan2EmergencyActionBridge

    /** 不建立输入缓冲的特殊数字页协调器。 */
    private lateinit var coordinator: SpecialPinCredentialCoordinator

    /** 绘制全部可见特殊数字安全页的像素宿主。 */
    private lateinit var host: PinCredentialHost

    /** 隐藏并恢复原生特殊安全页的可逆事务。 */
    private lateinit var nativeVisibility: NativeCredentialVisibilityTransaction

    /** 会话是否已经成功加入主安全容器。 */
    private var started: Boolean = false

    /** 会话是否正在执行恢复和清理。 */
    private var disposing: Boolean = false

    /** 会话是否已经完成释放。 */
    private var disposed: Boolean = false

    /** 像素宿主是否至少完成过一次 Android draw。 */
    private var firstFrameDrawn: Boolean = false

    /** 当前是否已经隐藏原生特殊安全页。 */
    private var takeoverActive: Boolean = false

    /** 判断现有会话是否仍绑定同一原生特殊控制器。 */
    fun isBoundTo(controller: Any): Boolean = !disposed && specialController === controller

    /** 返回像素首帧是否已经正式替代原生特殊安全页。 */
    fun isTakeoverActive(): Boolean = !disposed && takeoverActive

    /** 完成全部 fail-closed 绑定、挂载像素宿主并等待首帧。 */
    fun start() {
        checkMainThread()
        check(!started && !disposed) { "special_pin_session_already_used" }
        try {
            specialBinding = Titan2SpecialPinControllerBinding.bind(specialController, classLoader)
            check(specialBinding.credentialView.isAttachedToWindow) {
                "special_pin_view_detached"
            }
            containerBinding = Titan2SecurityContainerViewBinding.bind(
                securityController,
                classLoader,
            )
            check(containerBinding.securityContainer.isAttachedToWindow) {
                "special_pin_container_detached"
            }
            emergencyBridge = Titan2EmergencyActionBridge.bindSpecial(
                credentialController = specialController,
                mode = specialBinding.mode,
                classLoader = classLoader,
            )
            coordinator = SpecialPinCredentialCoordinator(
                mode = specialBinding.mode,
                actions = specialBinding,
                onEmergencyAction = emergencyBridge::requestEmergencyAction,
                isEmergencyAvailable = emergencyBridge::isAvailable,
                onStateChanged = ::renderState,
                onInteractionFailed = ::fail,
            )
            host = PinCredentialHost(containerBinding.securityContainer.context, coordinator).apply {
                /** 兼容父容器当前或后续切换为 ConstraintLayout 后的约束克隆。 */
                id = View.generateViewId()
                translationZ = PIXEL_OVERLAY_TRANSLATION_Z
            }
            nativeVisibility = NativeCredentialVisibilityTransaction(
                securityContainer = containerBinding.securityContainer,
                credentialView = specialBinding.credentialView,
                pixelHost = host,
            )
            nativeVisibility.prepare()
            coordinator.refresh()
            host.addOnAttachStateChangeListener(this)
            containerBinding.securityContainer.addView(
                host,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            check(nativeVisibility.isStructureValid()) { "special_pin_host_attach_structure" }
            host.viewTreeObserver.addOnDrawListener(this)
            containerBinding.securityContainer.viewTreeObserver.addOnPreDrawListener(this)
            started = true
        } catch (throwable: Throwable) {
            dispose()
            throw throwable
        }
    }

    /** 每帧镜像原生脱敏状态、验证结构并决定是否隐藏原生页面。 */
    override fun onPreDraw(): Boolean {
        if (disposed) {
            return true
        }
        runCatching {
            coordinator.refresh()
            check(nativeVisibility.isStructureValid()) { "special_pin_native_structure_changed" }
            /** 首帧和整条可见父链均有效时才允许真正接管。 */
            val shouldTakeOver = firstFrameDrawn && isHostEffectivelyVisible()
            if (shouldTakeOver) {
                nativeVisibility.hide()
            } else {
                nativeVisibility.restore()
            }
            updateTakeoverState(shouldTakeOver)
        }.onFailure(::fail)
        return true
    }

    /** 首次像素绘制后请求下一帧，避免原生页面在像素首帧前消失。 */
    override fun onDraw() {
        if (firstFrameDrawn || disposed) {
            return
        }
        firstFrameDrawn = true
        host.post {
            if (::host.isInitialized && host.viewTreeObserver.isAlive) {
                host.viewTreeObserver.removeOnDrawListener(this)
            }
            if (!disposed && ::containerBinding.isInitialized) {
                containerBinding.securityContainer.invalidate()
            }
        }
    }

    /** 像素宿主完成挂载时无需额外动作。 */
    override fun onViewAttachedToWindow(view: View) = Unit

    /** 特殊页切换或安全容器重建时立即回退。 */
    override fun onViewDetachedFromWindow(view: View) {
        if (!disposing) {
            dispose()
        }
    }

    /** 使用 SystemUI 当前日夜配置渲染非敏感数字页状态。 */
    private fun renderState(state: PinCredentialUiState) {
        check(!disposed) { "special_pin_session_disposed" }
        /** 当前 SystemUI 日间或夜间配置。 */
        val brightness = when (
            specialBinding.credentialView.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
        ) {
            Configuration.UI_MODE_NIGHT_YES -> ProductThemeBrightness.DARK
            else -> ProductThemeBrightness.LIGHT
        }
        host.update(state, ProductThemeFamily.MIDNIGHT, brightness)
    }

    /** 检查像素宿主到主安全容器之间的整条可见父链。 */
    private fun isHostEffectivelyVisible(): Boolean {
        if (!host.isAttachedToWindow || host.width <= 0 || host.height <= 0) {
            return false
        }
        /** 当前待检查的宿主或父视图。 */
        var currentView: View? = host
        while (currentView != null) {
            if (currentView.visibility != View.VISIBLE || currentView.alpha <= MIN_VISIBLE_ALPHA) {
                return false
            }
            if (currentView === containerBinding.securityContainer) {
                return currentView.windowVisibility == View.VISIBLE
            }
            currentView = currentView.parent as? View
        }
        return false
    }

    /** 只在真实接管状态变化时通知普通锁屏会话。 */
    private fun updateTakeoverState(active: Boolean) {
        if (takeoverActive == active) {
            return
        }
        takeoverActive = active
        onTakeoverChanged(active)
    }

    /** 记录脱敏错误并立即执行幂等原生回退。 */
    private fun fail(throwable: Throwable) {
        if (disposed) {
            return
        }
        runCatching { onFailure(this, throwable) }
        dispose()
    }

    /** 幂等恢复原生页面、移除监听并释放像素资源。 */
    fun dispose() {
        if (disposed || disposing) {
            return
        }
        disposing = true
        disposed = true
        if (::nativeVisibility.isInitialized) {
            runCatching { nativeVisibility.restore() }
        }
        if (takeoverActive) {
            takeoverActive = false
            runCatching { onTakeoverChanged(false) }
        }
        if (
            ::containerBinding.isInitialized &&
            containerBinding.securityContainer.viewTreeObserver.isAlive
        ) {
            containerBinding.securityContainer.viewTreeObserver.removeOnPreDrawListener(this)
        }
        if (::host.isInitialized) {
            if (host.viewTreeObserver.isAlive) {
                host.viewTreeObserver.removeOnDrawListener(this)
            }
            host.removeOnAttachStateChangeListener(this)
        }
        if (::coordinator.isInitialized) {
            coordinator.close()
        }
        if (::emergencyBridge.isInitialized) {
            emergencyBridge.dispose()
        }
        if (
            ::host.isInitialized &&
            ::containerBinding.isInitialized &&
            host.parent === containerBinding.securityContainer
        ) {
            containerBinding.securityContainer.removeView(host)
        }
        if (::host.isInitialized) {
            runCatching { host.dispose() }
        }
        started = false
        disposing = false
        onDisposed(this)
    }

    /** 拒绝从非 SystemUI 主线程操作安全页面 View。 */
    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "special_pin_session_main_thread"
        }
    }

    private companion object {
        /** 像素宿主高于原生特殊数字页的绘制层级。 */
        const val PIXEL_OVERLAY_TRANSLATION_Z: Float = 100f

        /** 父链 alpha 低于该值时视为 SystemUI 已隐藏页面。 */
        const val MIN_VISIBLE_ALPHA: Float = 0.01f
    }
}
