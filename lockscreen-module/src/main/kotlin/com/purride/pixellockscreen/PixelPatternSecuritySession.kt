package com.purride.pixellockscreen

import android.content.res.Configuration
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.purride.pixeldesign.ProductAppearance
import com.purride.pixellockscreen.credential.CredentialBridgeContractResult
import com.purride.pixellockscreen.credential.CredentialCheckResult
import com.purride.pixellockscreen.credential.EphemeralCredentialLease
import com.purride.pixellockscreen.credential.KeyguardSecurityDisposition
import com.purride.pixellockscreen.credential.PatternCredentialCoordinator
import com.purride.pixellockscreen.credential.PendingCredentialCheck
import com.purride.pixellockscreen.credential.SystemCredentialBridge
import com.purride.pixellockscreen.credential.Titan2CredentialMode
import com.purride.pixellockscreen.credential.Titan2EmergencyActionBridge
import com.purride.pixellockscreen.credential.Titan2KeyguardSecurityBridge
import com.purride.pixellockscreen.credential.Titan2PatternControllerBinding
import com.purride.pixellockscreen.credential.Titan2SecurityContainerViewBinding
import com.purride.pixellockscreen.ui.PatternCredentialHost
import com.purride.pixellockscreen.ui.PatternCredentialUiState
import com.purride.pixellockscreen.ui.resolveLockscreenAppearance
import kotlin.math.min

/**
 * Titan 2 当前一次图案 Bouncer 展示周期的像素认证接管与原生回退会话。
 *
 * 原生图案控制器、紧急按钮和安全回调始终存活。像素首帧绘制成功后只隐藏原生页面内容；
 * 任一合同、结构、用户、模式、异步校验或生命周期异常都会恢复原生内容并结束会话。
 */
internal class PixelPatternSecuritySession(
    /** 当前 SystemUI 主安全容器控制器。 */
    private val securityController: Any,
    /** 当前已执行 `onResume()` 的原生图案控制器。 */
    private val patternController: Any,
    /** SystemUI 最终应用类加载器。 */
    private val classLoader: ClassLoader,
    /** 每次渲染时提供 Launcher 最新共享外观。 */
    private val appearanceProvider: () -> ProductAppearance,
    /** 图案页接管或恢复时暂停、恢复普通像素锁屏的动作。 */
    private val onTakeoverChanged: (Boolean) -> Unit,
    /** 会话异步失败时记录脱敏原因的动作。 */
    private val onFailure: (PixelPatternSecuritySession, Throwable) -> Unit,
    /** 会话完全释放后的上层清理动作。 */
    private val onDisposed: (PixelPatternSecuritySession) -> Unit,
) : ViewTreeObserver.OnPreDrawListener,
    ViewTreeObserver.OnDrawListener,
    View.OnAttachStateChangeListener {
    /** 当前图案控制器的精确视图、回调与模式绑定。 */
    private lateinit var patternBinding: Titan2PatternControllerBinding

    /** 当前主安全容器控制器对应的可覆盖 ViewGroup。 */
    private lateinit var containerBinding: Titan2SecurityContainerViewBinding

    /** 当前主安全容器的原生回调与 LockPatternUtils 桥。 */
    private lateinit var securityBridge: Titan2KeyguardSecurityBridge

    /** 当前原生紧急按钮点击链桥。 */
    private lateinit var emergencyBridge: Titan2EmergencyActionBridge

    /** Android 15 LockPatternChecker 隐藏 API 桥。 */
    private lateinit var credentialBridge: SystemCredentialBridge

    /** 逐格安全输入与非敏感状态协调器。 */
    private lateinit var coordinator: PatternCredentialCoordinator

    /** 绘制全部可见认证内容的像素宿主。 */
    private lateinit var host: PatternCredentialHost

    /** 隐藏并恢复原生图案页直属内容的通用凭据事务。 */
    private lateinit var nativeVisibility: NativeCredentialVisibilityTransaction

    /** 当前尚未完成的唯一系统校验任务。 */
    private var pendingCheck: PendingCredentialCheck? = null

    /** 用于丢弃旧异步结果的单调递增代次。 */
    private var credentialGeneration: Long = 0L

    /** 当前由系统提供的锁定截止时间。 */
    private var lockoutDeadlineElapsedRealtime: Long = 0L

    /** 会话是否已经成功加入原生图案页面。 */
    private var started: Boolean = false

    /** 会话是否正在执行恢复和清理。 */
    private var disposing: Boolean = false

    /** 会话是否已经完成释放。 */
    private var disposed: Boolean = false

    /** 像素宿主是否至少完成过一次 Android draw。 */
    private var firstFrameDrawn: Boolean = false

    /** 当前是否已经隐藏原生图案内容并暂停普通像素锁屏。 */
    private var takeoverActive: Boolean = false

    /** 最近一次非敏感界面状态，用于外观变化后立即重绘。 */
    private var lastRenderedState: PatternCredentialUiState? = null

    /** 按系统截止时间刷新剩余秒数的单一主线程任务。 */
    private val lockoutTick: Runnable = Runnable(::updateLockoutCountdown)

    /** 判断现有会话是否仍绑定同一原生图案控制器。 */
    fun isBoundTo(controller: Any): Boolean = !disposed && patternController === controller

    /** 返回像素首帧是否已经正式替代原生图案内容。 */
    fun isTakeoverActive(): Boolean = !disposed && takeoverActive

    /** Launcher 外观变化时使用最近状态立即刷新图案页。 */
    fun refreshAppearance() {
        if (disposed || !::host.isInitialized) return
        lastRenderedState?.let(::renderState)
    }

    /**
     * 在原生 UI 完全可用时解析全部合同、挂载像素宿主并等待首帧。
     *
     * 任一步失败都会在重新抛出前恢复原生页面。
     */
    fun start() {
        checkMainThread()
        check(!started && !disposed) { "pattern_session_already_used" }
        try {
            patternBinding = Titan2PatternControllerBinding.bind(patternController, classLoader)
            check(patternBinding.patternView.isAttachedToWindow) { "pattern_view_detached" }
            containerBinding = Titan2SecurityContainerViewBinding.bind(
                securityController,
                classLoader,
            )
            check(containerBinding.securityContainer.isAttachedToWindow) {
                "security_container_detached"
            }
            securityBridge = Titan2KeyguardSecurityBridge.bind(securityController, classLoader)
            check(securityBridge.credentialMode == Titan2CredentialMode.PATTERN) {
                "pattern_security_mode"
            }
            check(
                securityBridge.matchesControllerBinding(
                    patternBinding.securityCallback,
                    patternBinding.securityMode,
                ),
            ) { "pattern_security_callback_mismatch" }
            emergencyBridge = Titan2EmergencyActionBridge.bind(
                credentialController = patternController,
                credentialMode = Titan2CredentialMode.PATTERN,
                classLoader = classLoader,
            )
            credentialBridge = SystemCredentialBridge(classLoader)
            check(
                credentialBridge.verifyContract(securityBridge.lockPatternUtils) ==
                    CredentialBridgeContractResult.Ready,
            ) { "pattern_credential_contract" }
            coordinator = createCoordinator()
            host = PatternCredentialHost(containerBinding.securityContainer.context, coordinator).apply {
                /** 兼容父容器当前或后续切换为 ConstraintLayout 后的约束克隆。 */
                id = View.generateViewId()
                translationZ = PIXEL_OVERLAY_TRANSLATION_Z
            }
            nativeVisibility = NativeCredentialVisibilityTransaction(
                securityContainer = containerBinding.securityContainer,
                credentialView = patternBinding.patternView,
                pixelHost = host,
            )
            nativeVisibility.prepare()
            coordinator.showReady()
            host.addOnAttachStateChangeListener(this)
            containerBinding.securityContainer.addView(
                host,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            check(nativeVisibility.isStructureValid()) { "pattern_host_attach_structure" }
            host.viewTreeObserver.addOnDrawListener(this)
            containerBinding.securityContainer.viewTreeObserver.addOnPreDrawListener(this)
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

    /** 每帧验证安全上下文和视图结构，再决定隐藏或恢复原生图案内容。 */
    override fun onPreDraw(): Boolean {
        if (disposed) {
            return true
        }
        runCatching {
            check(securityBridge.isCurrentContext()) { "pattern_security_context_changed" }
            check(nativeVisibility.isStructureValid()) { "pattern_native_structure_changed" }
            coordinator.refreshEmergencyAvailability()
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

    /** 首次完成像素绘制后请求下一帧，下一帧才隐藏原生内容。 */
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

    /** 像素宿主挂载完成时无需额外动作。 */
    override fun onViewAttachedToWindow(view: View) = Unit

    /** 图案页面重建或退出时立即清零输入并恢复原生内容。 */
    override fun onViewDetachedFromWindow(view: View) {
        if (!disposing) {
            dispose()
        }
    }

    /** 创建只把安全输入交给系统桥的图案协调器。 */
    private fun createCoordinator(): PatternCredentialCoordinator = PatternCredentialCoordinator(
        onUserInput = securityBridge::signalUserInput,
        onCredentialReady = ::submitCredential,
        onEmergencyAction = emergencyBridge::requestEmergencyAction,
        isEmergencyAvailable = emergencyBridge::isAvailable,
        onStateChanged = ::renderState,
        onInteractionFailed = ::fail,
    )

    /** 使用当前 SystemUI 明暗配置提交非敏感认证状态。 */
    private fun renderState(state: PatternCredentialUiState) {
        check(!disposed) { "pattern_session_disposed" }
        lastRenderedState = state
        /** 当前 SystemUI 是否处于夜间配置。 */
        val systemInDarkMode = when (
            patternBinding.patternView.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
        ) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
        host.update(state, appearanceProvider().resolveLockscreenAppearance(systemInDarkMode))
    }

    /** 启动一次唯一系统图案校验，并处理同步回调竞态。 */
    private fun submitCredential(credential: EphemeralCredentialLease.Pattern) {
        checkMainThread()
        check(pendingCheck == null) { "pattern_check_already_pending" }
        /** 本次校验用于拒绝旧回调的代次。 */
        val generation = credentialGeneration + 1L
        credentialGeneration = generation
        /** 系统桥返回前是否已经同步送达终态。 */
        var completedSynchronously = false
        /** 系统返回的可取消校验任务。 */
        val check = credentialBridge.checkCredential(
            lockPatternUtils = securityBridge.lockPatternUtils,
            userId = securityBridge.userId,
            credential = credential,
            onCallbackFailure = { throwable -> postFailure(throwable) },
            callback = { result ->
                completedSynchronously = true
                postCredentialResult(generation, result)
            },
        )
        if (completedSynchronously || disposed || credentialGeneration != generation) {
            check.close()
        } else {
            pendingCheck = check
        }
    }

    /** 把系统回调安全切换到 SystemUI 主线程。 */
    private fun postCredentialResult(generation: Long, result: CredentialCheckResult) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            handleCredentialResult(generation, result)
        } else if (::host.isInitialized) {
            host.post { handleCredentialResult(generation, result) }
        }
    }

    /** 复用原生 Keyguard 上报链，并只按系统处置更新像素反馈。 */
    private fun handleCredentialResult(generation: Long, result: CredentialCheckResult) {
        if (disposed || generation != credentialGeneration) {
            return
        }
        pendingCheck = null
        runCatching { securityBridge.complete(result) }
            .onSuccess { disposition ->
                when (disposition) {
                    KeyguardSecurityDisposition.DismissRequested -> Unit
                    KeyguardSecurityDisposition.FailureReported -> coordinator.showRejected()
                    is KeyguardSecurityDisposition.LockoutStarted -> startLockout(
                        disposition.deadlineElapsedRealtime,
                    )
                    KeyguardSecurityDisposition.Cancelled -> coordinator.showReady()
                    KeyguardSecurityDisposition.StaleContext -> fail(
                        IllegalStateException("pattern_security_context_stale"),
                    )
                }
            }
            .onFailure(::fail)
    }

    /** 使用系统给出的单调时钟截止时间进入限流反馈。 */
    private fun startLockout(deadlineElapsedRealtime: Long) {
        check(deadlineElapsedRealtime > 0L) { "pattern_lockout_deadline" }
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

    /** 把异步错误安全切换到 SystemUI 主线程并回退。 */
    private fun postFailure(throwable: Throwable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            fail(throwable)
        } else if (::host.isInitialized) {
            host.post { fail(throwable) }
        }
    }

    /** 检查像素宿主到原生图案根之间的整条可见父链。 */
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

    /** 记录一次脱敏失败并幂等恢复原生页面。 */
    private fun fail(throwable: Throwable) {
        if (disposed) {
            return
        }
        runCatching { onFailure(this, throwable) }
        dispose()
    }

    /** 幂等取消校验、清零输入、恢复原生内容并释放像素资源。 */
    fun dispose() {
        if (disposed || disposing) {
            return
        }
        disposing = true
        disposed = true
        credentialGeneration += 1L
        lockoutDeadlineElapsedRealtime = 0L
        if (::host.isInitialized) {
            host.removeCallbacks(lockoutTick)
        }
        pendingCheck?.close()
        pendingCheck = null
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

    /** 拒绝从凭据后台线程直接操作 SystemUI View。 */
    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "pattern_session_main_thread" }
    }

    private companion object {
        /** 像素认证宿主高于原生图案子视图的绘制层级。 */
        const val PIXEL_OVERLAY_TRANSLATION_Z: Float = 100f

        /** 父链 alpha 低于该阈值时视为图案页面已经不可见。 */
        const val MIN_VISIBLE_ALPHA: Float = 0.01f

        /** 锁定反馈的最大刷新频率。 */
        const val LOCKOUT_TICK_INTERVAL_MILLIS: Long = 1_000L
    }
}

/** 按系统单调时钟截止时间计算向上取整的剩余秒数。 */
internal fun remainingLockoutSeconds(
    /** Android 返回的 elapsed realtime 截止时间。 */
    deadlineElapsedRealtime: Long,
    /** 当前 elapsed realtime。 */
    nowElapsedRealtime: Long,
): Int {
    if (deadlineElapsedRealtime <= nowElapsedRealtime) {
        return 0
    }
    /** 尚未结束的毫秒数。 */
    val remainingMillis = deadlineElapsedRealtime - nowElapsedRealtime
    /** 防止异常超长截止时间溢出 UI 整数秒。 */
    val maximumMillis = Int.MAX_VALUE.toLong() * 1_000L
    return ((min(remainingMillis, maximumMillis) + 999L) / 1_000L).toInt()
}
