package com.purride.pixellockscreen

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.purride.pixeldesign.ProductThemeFamily
import com.purride.pixellockscreen.ui.LockscreenRootHost

/**
 * 一次 SystemUI 进程生命周期内的普通像素 Keyguard 挂载与回退会话。
 *
 * 宿主作为 `KeyguardRootView` 子视图自动跟随锁屏可见性；原生 Bouncer 保留在窗口更高层级。
 */
internal class PixelKeyguardSession(
    /** 已通过 Titan 2 签名探测的 SystemUI 视图绑定。 */
    private val binding: Titan2SystemUiBinding,
    /** 会话完全释放后的上层清理回调。 */
    private val onDisposed: (PixelKeyguardSession) -> Unit,
) : ViewTreeObserver.OnPreDrawListener,
    ViewTreeObserver.OnDrawListener,
    View.OnAttachStateChangeListener {
    /** 真正绘制 Pixel Engine 锁屏的 Android 宿主。 */
    private val host: LockscreenRootHost = LockscreenRootHost(binding.keyguardRoot.context)

    /** 普通原生锁屏节点的可恢复显隐事务。 */
    private val nativeVisibility = NativeKeyguardVisibilityTransaction(binding.shadeWindow)

    /** 只读解析原生生物识别、StrongAuth 和系统提示的 Titan 2 适配器。 */
    private val biometricAdapter = Titan2BiometricStateAdapter.bind(binding.indicationController)

    /** 系统广播驱动的时间、电量与明暗状态适配器。 */
    private val stateAdapter = AndroidKeyguardStateAdapter(binding.keyguardRoot.context) { state, brightness ->
        runCatching {
            host.update(state, ProductThemeFamily.MIDNIGHT, brightness)
        }.onFailure {
            dispose()
        }
    }

    /** 宿主是否已加入 SystemUI 视图树。 */
    private var started: Boolean = false

    /** 会话是否正在执行恢复与释放，防止脱离回调重入。 */
    private var disposing: Boolean = false

    /** 会话是否已完全释放。 */
    private var disposed: Boolean = false

    /** 像素宿主是否至少完成过一次 Android `draw()`。 */
    private var firstFrameDrawn: Boolean = false

    /** 完整像素凭据页是否正在上层接管可见 UI。 */
    private var credentialTakeoverActive: Boolean = false

    /** 判断现有会话是否仍绑定同一 Keyguard 根视图。 */
    fun isBoundTo(keyguardRoot: ViewGroup): Boolean = !disposed && binding.keyguardRoot === keyguardRoot

    /** 在完整像素凭据页展示期间暂停普通时钟页，但继续隐藏普通原生锁屏内容。 */
    fun setCredentialTakeoverActive(active: Boolean) {
        if (disposed || credentialTakeoverActive == active) {
            return
        }
        credentialTakeoverActive = active
        host.visibility = if (active) View.INVISIBLE else View.VISIBLE
        binding.shadeWindow.invalidate()
    }

    /**
     * 在不改变原生视图的前提下完成所有前置验证，然后挂载像素宿主。
     *
     * 任何一步失败都会立即恢复原生状态并重新抛出异常。
     */
    fun start() {
        check(!started && !disposed) { "Pixel Keyguard session already used" }
        try {
            nativeVisibility.prepare()
            stateAdapter.start()
            host.addOnAttachStateChangeListener(this)
            binding.keyguardRoot.addView(
                host,
                0,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            host.viewTreeObserver.addOnDrawListener(this)
            binding.shadeWindow.viewTreeObserver.addOnPreDrawListener(this)
            started = true
        } catch (throwable: Throwable) {
            dispose()
            throw throwable
        }
    }

    /**
     * 每帧在绘制前根据像素宿主的实际可见性隐藏或恢复原生普通锁屏。
     *
     * @return 始终允许 SystemUI 继续本帧绘制。
     */
    override fun onPreDraw(): Boolean {
        if (disposed) return true
        runCatching {
            stateAdapter.updateBiometric(biometricAdapter.snapshot())
            if (disposed) return true
            /** 宿主只有在首帧、继承可见性和物理尺寸都就绪时才允许接管。 */
            val shouldTakeOver = credentialTakeoverActive ||
                (firstFrameDrawn && isHostEffectivelyVisible())
            if (shouldTakeOver) {
                nativeVisibility.hide()
            } else {
                nativeVisibility.restore()
            }
        }.onFailure {
            dispose()
        }
        return true
    }

    /** 首次完成像素宿主绘制后触发下一帧，下一帧才允许隐藏原生 UI。 */
    override fun onDraw() {
        if (firstFrameDrawn || disposed) return
        firstFrameDrawn = true
        host.post {
            if (host.viewTreeObserver.isAlive) {
                host.viewTreeObserver.removeOnDrawListener(this)
            }
            if (!disposed) {
                binding.shadeWindow.invalidate()
            }
        }
    }

    /** 宿主挂载到 SystemUI 窗口时无需额外操作。 */
    override fun onViewAttachedToWindow(view: View) = Unit

    /** 宿主因 SystemUI 重建或锁屏根节点替换而脱离时立即回退。 */
    override fun onViewDetachedFromWindow(view: View) {
        if (!disposing) dispose()
    }

    /** 检查宿主到 SystemUI 窗口的整条父链可见性和 alpha，避免解锁后覆盖通知遮罩。 */
    private fun isHostEffectivelyVisible(): Boolean {
        if (!host.isAttachedToWindow || host.width <= 0 || host.height <= 0) return false
        /** 当前待检查的宿主或父视图。 */
        var currentView: View? = host
        while (currentView != null) {
            if (currentView.visibility != View.VISIBLE || currentView.alpha <= MIN_VISIBLE_ALPHA) {
                return false
            }
            if (currentView === binding.shadeWindow) {
                return currentView.windowVisibility == View.VISIBLE
            }
            currentView = currentView.parent as? View
        }
        return false
    }

    /** 幂等恢复原生节点、注销广播、移除监听并释放 Pixel Engine 宿主。 */
    fun dispose() {
        if (disposed || disposing) return
        disposing = true
        runCatching { nativeVisibility.restore() }
        if (binding.shadeWindow.viewTreeObserver.isAlive) {
            binding.shadeWindow.viewTreeObserver.removeOnPreDrawListener(this)
        }
        if (host.viewTreeObserver.isAlive) {
            host.viewTreeObserver.removeOnDrawListener(this)
        }
        host.removeOnAttachStateChangeListener(this)
        runCatching { stateAdapter.stop() }
        if (host.parent === binding.keyguardRoot) {
            binding.keyguardRoot.removeView(host)
        }
        runCatching { host.dispose() }
        started = false
        disposed = true
        disposing = false
        onDisposed(this)
    }

    private companion object {
        /** 父链 alpha 低于该阈值时视为 SystemUI 已隐藏 Keyguard。 */
        const val MIN_VISIBLE_ALPHA: Float = 0.01f
    }
}
