package com.purride.pixellockscreen

import android.content.res.Configuration
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.purride.pixeldesign.ProductAppearance
import com.purride.pixellockscreen.credential.PasswordCredentialCoordinator
import com.purride.pixellockscreen.credential.Titan2CredentialMode
import com.purride.pixellockscreen.credential.Titan2EmergencyActionBridge
import com.purride.pixellockscreen.credential.Titan2KeyguardSecurityBridge
import com.purride.pixellockscreen.credential.Titan2PasswordControllerBinding
import com.purride.pixellockscreen.credential.Titan2SecurityContainerViewBinding
import com.purride.pixellockscreen.ui.PasswordCredentialHost
import com.purride.pixellockscreen.ui.PasswordCredentialUiState
import com.purride.pixellockscreen.ui.resolveLockscreenAppearance

/**
 * Titan 2 当前一次密码 Bouncer 展示周期的像素 UI 接管会话。
 *
 * 原生密码 `EditText`、IME、控制器异步校验、失败计数、限流和 Keyguard 回调始终保持原样。
 * 会话只监听 `Editable.length` 与系统阶段，像素首帧后透明化原生绘制；任一结构或生命周期
 * 异常都会恢复所有原生属性并移除像素宿主。
 */
internal class PixelPasswordSecuritySession(
    /** 当前 SystemUI 主安全容器控制器。 */
    private val securityController: Any,
    /** 当前已执行 `onResume()` 的原生密码控制器。 */
    private val passwordController: Any,
    /** SystemUI 最终应用类加载器。 */
    private val classLoader: ClassLoader,
    /** 每次渲染时提供 Launcher 最新共享外观。 */
    private val appearanceProvider: () -> ProductAppearance,
    /** 密码页接管或恢复时暂停、恢复普通像素锁屏的动作。 */
    private val onTakeoverChanged: (Boolean) -> Unit,
    /** 会话异步失败时记录脱敏原因的动作。 */
    private val onFailure: (PixelPasswordSecuritySession, Throwable) -> Unit,
    /** 会话完全释放后的上层清理动作。 */
    private val onDisposed: (PixelPasswordSecuritySession) -> Unit,
) : ViewTreeObserver.OnPreDrawListener,
    ViewTreeObserver.OnDrawListener,
    ViewTreeObserver.OnGlobalFocusChangeListener,
    View.OnAttachStateChangeListener,
    TextWatcher {
    /** 当前密码控制器的精确视图、输入连接、回调与模式绑定。 */
    private lateinit var passwordBinding: Titan2PasswordControllerBinding

    /** 当前主安全容器控制器对应的可覆盖 ViewGroup。 */
    private lateinit var containerBinding: Titan2SecurityContainerViewBinding

    /** 当前主安全容器的原生回调与锁定截止时间桥。 */
    private lateinit var securityBridge: Titan2KeyguardSecurityBridge

    /** 当前原生紧急按钮点击链桥。 */
    private lateinit var emergencyBridge: Titan2EmergencyActionBridge

    /** 只协调非敏感长度和原生认证阶段的密码状态协调器。 */
    private lateinit var coordinator: PasswordCredentialCoordinator

    /** 绘制全部可见密码认证内容并承载公开动作语义的像素宿主。 */
    private lateinit var host: PasswordCredentialHost

    /** 保留原生输入连接但替代其全部可见绘制的可恢复事务。 */
    private lateinit var nativePresentation: NativePasswordPresentationTransaction

    /** 当前由系统提供的锁定截止时间。 */
    private var lockoutDeadlineElapsedRealtime: Long = 0L

    /** 会话是否已经成功加入原生安全容器。 */
    private var started: Boolean = false

    /** 会话是否正在执行恢复和清理。 */
    private var disposing: Boolean = false

    /** 会话是否已经完成释放。 */
    private var disposed: Boolean = false

    /** 像素宿主是否至少完成过一次 Android draw。 */
    private var firstFrameDrawn: Boolean = false

    /** 当前是否已经透明化原生密码内容并暂停普通像素锁屏。 */
    private var takeoverActive: Boolean = false

    /** 最近一次非敏感界面状态，用于外观变化后立即重绘。 */
    private var lastRenderedState: PasswordCredentialUiState? = null

    /** 按系统截止时间刷新剩余秒数的单一主线程任务。 */
    private val lockoutTick: Runnable = Runnable(::updateLockoutCountdown)

    /** 判断现有会话是否仍绑定同一原生密码控制器。 */
    fun isBoundTo(controller: Any): Boolean = !disposed && passwordController === controller

    /** 返回像素首帧是否已经正式替代原生密码绘制。 */
    fun isTakeoverActive(): Boolean = !disposed && takeoverActive

    /** Launcher 外观变化时使用最近状态立即刷新密码页。 */
    fun refreshAppearance() {
        if (disposed || !::host.isInitialized) return
        lastRenderedState?.let(::renderState)
    }

    /**
     * 在原生密码页和 IME 合同完全可用时挂载像素宿主并等待首帧。
     *
     * 任一步失败都会在重新抛出前恢复原生密码绘制。
     */
    fun start() {
        checkMainThread()
        check(!started && !disposed) { "password_session_already_used" }
        try {
            passwordBinding = Titan2PasswordControllerBinding.bind(passwordController, classLoader)
            check(passwordBinding.passwordView.isAttachedToWindow) { "password_view_detached" }
            check(passwordBinding.passwordEntry.isAttachedToWindow) { "password_entry_detached" }
            containerBinding = Titan2SecurityContainerViewBinding.bind(
                securityController,
                classLoader,
            )
            check(containerBinding.securityContainer.isAttachedToWindow) {
                "security_container_detached"
            }
            securityBridge = Titan2KeyguardSecurityBridge.bind(securityController, classLoader)
            check(securityBridge.credentialMode == Titan2CredentialMode.PASSWORD) {
                "password_security_mode"
            }
            check(
                securityBridge.matchesControllerBinding(
                    passwordBinding.securityCallback,
                    passwordBinding.securityMode,
                ),
            ) { "password_security_callback_mismatch" }
            emergencyBridge = Titan2EmergencyActionBridge.bind(
                credentialController = passwordController,
                credentialMode = Titan2CredentialMode.PASSWORD,
                classLoader = classLoader,
            )
            coordinator = createCoordinator()
            host = PasswordCredentialHost(containerBinding.securityContainer.context, coordinator).apply {
                /** 兼容父容器当前或后续切换为 ConstraintLayout 后的约束克隆。 */
                id = View.generateViewId()
                translationZ = PIXEL_OVERLAY_TRANSLATION_Z
            }
            nativePresentation = NativePasswordPresentationTransaction(
                securityContainer = containerBinding.securityContainer,
                passwordView = passwordBinding.passwordView,
                passwordEntry = passwordBinding.passwordEntry,
                pixelHost = host,
            )
            nativePresentation.prepare()
            passwordBinding.passwordEntry.addTextChangedListener(this)
            passwordBinding.passwordEntry.viewTreeObserver.addOnGlobalFocusChangeListener(this)
            host.addOnAttachStateChangeListener(this)
            containerBinding.securityContainer.addView(
                host,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            check(nativePresentation.isStructureValid()) { "password_host_attach_structure" }
            host.viewTreeObserver.addOnDrawListener(this)
            containerBinding.securityContainer.viewTreeObserver.addOnPreDrawListener(this)
            coordinator.showInitial(
                length = passwordBinding.currentInputLength(),
                focused = passwordBinding.hasInputFocus(),
                imeVisible = passwordBinding.isImeSwitcherVisible(),
            )
            started = true
            /** 页面恢复时可能已经存在的系统锁定截止时间。 */
            val initialDeadline = securityBridge.currentLockoutDeadline()
            if (initialDeadline > SystemClock.elapsedRealtime()) {
                startLockout(initialDeadline)
            }
        } catch (throwable: Throwable) {
            dispose()
            throw throwable
        }
    }

    /** 每帧验证安全上下文、输入连接和视图结构，再决定透明化或恢复原生绘制。 */
    override fun onPreDraw(): Boolean {
        if (disposed) {
            return true
        }
        runCatching {
            check(securityBridge.isCurrentContext()) { "password_security_context_changed" }
            check(passwordBinding.isCurrent()) { "password_input_binding_changed" }
            check(nativePresentation.isStructureValid()) { "password_native_structure_changed" }
            coordinator.onNativeFocusChanged(passwordBinding.hasInputFocus())
            coordinator.onImeSwitcherVisibilityChanged(passwordBinding.isImeSwitcherVisible())
            coordinator.refreshEmergencyAvailability()
            /** 首帧和整条可见父链均有效时才允许真正接管。 */
            val shouldTakeOver = firstFrameDrawn && isHostEffectivelyVisible()
            if (shouldTakeOver) {
                nativePresentation.hide()
            } else {
                nativePresentation.restore()
            }
            updateTakeoverState(shouldTakeOver)
        }.onFailure(::fail)
        return true
    }

    /** 首次完成像素绘制后请求下一帧，下一帧才透明化原生密码绘制。 */
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

    /** 全局焦点变化时只重新读取原生输入框的布尔焦点状态。 */
    override fun onGlobalFocusChanged(oldFocus: View?, newFocus: View?) {
        if (disposed || (oldFocus !== passwordBinding.passwordEntry && newFocus !== passwordBinding.passwordEntry)) {
            return
        }
        runCatching {
            coordinator.onNativeFocusChanged(passwordBinding.hasInputFocus())
        }.onFailure(::fail)
    }

    /** 文本变化前不读取或缓存任何密码内容。 */
    override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

    /** 文本变化过程中不读取或缓存任何密码内容。 */
    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit

    /** 文本变化完成后只读取瞬时 `Editable.length`，不调用 `toString()`。 */
    override fun afterTextChanged(editable: Editable?) {
        if (disposed) {
            return
        }
        runCatching {
            /** 本次回调唯一允许读取的非敏感字符数量。 */
            val length = editable?.length ?: passwordBinding.currentInputLength()
            coordinator.onNativeInputLengthChanged(length)
        }.onFailure(::fail)
    }

    /** 像素宿主挂载完成时无需额外动作。 */
    override fun onViewAttachedToWindow(view: View) = Unit

    /** 密码页面重建或退出时立即恢复原生绘制并释放监听器。 */
    override fun onViewDetachedFromWindow(view: View) {
        if (!disposing) {
            dispose()
        }
    }

    /** 原生 `verifyPasswordAndUnlock()` 即将执行时进入不可编辑的校验反馈。 */
    fun onVerificationStarted() {
        if (disposed) {
            return
        }
        runCatching { coordinator.showChecking() }.onFailure(::fail)
    }

    /** 原生校验完成后只根据结果和系统超时显示反馈，不参与回调或凭据处置。 */
    fun onPasswordChecked(timeoutMillis: Int, matched: Boolean) {
        if (disposed || matched) {
            return
        }
        runCatching {
            if (timeoutMillis <= 0) {
                coordinator.showRejected()
            } else {
                /** 锁定 Hook 应已送达截止时间；缺失时从系统桥重新读取。 */
                val deadline = securityBridge.currentLockoutDeadline()
                check(deadline > SystemClock.elapsedRealtime()) {
                    "password_lockout_deadline_missing"
                }
                startLockout(deadline)
            }
        }.onFailure(::fail)
    }

    /** 原生 `handleAttemptLockout()` 完成后使用同一系统截止时间启动像素倒计时。 */
    fun onLockoutStarted(deadlineElapsedRealtime: Long) {
        if (disposed) {
            return
        }
        runCatching { startLockout(deadlineElapsedRealtime) }.onFailure(::fail)
    }

    /** 原生 `resetState()` 完成后同步输入状态和当前系统限流。 */
    fun onNativeStateReset() {
        if (disposed) {
            return
        }
        runCatching {
            coordinator.onNativeInputLengthChanged(passwordBinding.currentInputLength())
            coordinator.onNativeFocusChanged(passwordBinding.hasInputFocus())
            coordinator.onImeSwitcherVisibilityChanged(passwordBinding.isImeSwitcherVisible())
            /** SystemUI 当前用户的真实限流截止时间。 */
            val deadline = securityBridge.currentLockoutDeadline()
            if (deadline > SystemClock.elapsedRealtime()) {
                startLockout(deadline)
            } else {
                lockoutDeadlineElapsedRealtime = 0L
                host.removeCallbacks(lockoutTick)
                coordinator.showReady()
            }
        }.onFailure(::fail)
    }

    /** 创建只转发原生输入连接公开动作的密码协调器。 */
    private fun createCoordinator(): PasswordCredentialCoordinator = PasswordCredentialCoordinator(
        onInputRequestedAction = passwordBinding::requestInput,
        onImeSwitcherRequestedAction = passwordBinding::requestImeSwitcher,
        onEmergencyAction = emergencyBridge::requestEmergencyAction,
        isEmergencyAvailable = emergencyBridge::isAvailable,
        onStateChanged = ::renderState,
        onInteractionFailed = ::fail,
    )

    /** 使用当前 SystemUI 明暗配置提交非敏感认证状态。 */
    private fun renderState(state: PasswordCredentialUiState) {
        check(!disposed) { "password_session_disposed" }
        lastRenderedState = state
        /** 当前 SystemUI 是否处于夜间配置。 */
        val systemInDarkMode = when (
            passwordBinding.passwordView.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
        ) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
        host.update(state, appearanceProvider().resolveLockscreenAppearance(systemInDarkMode))
    }

    /** 使用系统给出的单调时钟截止时间进入限流反馈。 */
    private fun startLockout(deadlineElapsedRealtime: Long) {
        check(deadlineElapsedRealtime > SystemClock.elapsedRealtime()) {
            "password_lockout_deadline"
        }
        lockoutDeadlineElapsedRealtime = deadlineElapsedRealtime
        updateLockoutCountdown()
    }

    /** 按系统截止时间刷新剩余秒数，不维护本地失败次数。 */
    private fun updateLockoutCountdown() {
        if (disposed || lockoutDeadlineElapsedRealtime <= 0L) {
            return
        }
        /** 当前截止时间对应的向上取整剩余秒数。 */
        val remainingSeconds = remainingLockoutSeconds(
            deadlineElapsedRealtime = lockoutDeadlineElapsedRealtime,
            nowElapsedRealtime = SystemClock.elapsedRealtime(),
        )
        host.removeCallbacks(lockoutTick)
        if (remainingSeconds <= 0) {
            lockoutDeadlineElapsedRealtime = 0L
            coordinator.showReady()
            return
        }
        coordinator.showLockedOut(remainingSeconds)
        host.postDelayed(lockoutTick, LOCKOUT_TICK_INTERVAL_MILLIS)
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

    /** 只在接管状态真正变化时通知普通像素锁屏。 */
    private fun updateTakeoverState(active: Boolean) {
        if (takeoverActive == active) {
            return
        }
        takeoverActive = active
        onTakeoverChanged(active)
    }

    /** 记录一次脱敏失败并幂等恢复原生密码页面。 */
    private fun fail(throwable: Throwable) {
        if (disposed) {
            return
        }
        runCatching { onFailure(this, throwable) }
        dispose()
    }

    /** 幂等移除监听器、恢复原生绘制并释放像素资源。 */
    fun dispose() {
        if (disposed || disposing) {
            return
        }
        disposing = true
        disposed = true
        lockoutDeadlineElapsedRealtime = 0L
        if (::host.isInitialized) {
            host.removeCallbacks(lockoutTick)
        }
        if (::nativePresentation.isInitialized) {
            runCatching { nativePresentation.restore() }
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
        if (::passwordBinding.isInitialized) {
            passwordBinding.passwordEntry.removeTextChangedListener(this)
            if (passwordBinding.passwordEntry.viewTreeObserver.isAlive) {
                passwordBinding.passwordEntry.viewTreeObserver
                    .removeOnGlobalFocusChangeListener(this)
            }
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

    /** 拒绝从非主线程直接操作 SystemUI View 或协调状态。 */
    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "password_session_main_thread" }
    }

    private companion object {
        /** 像素认证宿主高于原生密码子视图的绘制层级。 */
        const val PIXEL_OVERLAY_TRANSLATION_Z: Float = 100f

        /** 父链 alpha 低于该阈值时视为密码页面已经不可见。 */
        const val MIN_VISIBLE_ALPHA: Float = 0.01f

        /** 锁定反馈的最大刷新频率。 */
        const val LOCKOUT_TICK_INTERVAL_MILLIS: Long = 1_000L
    }
}
