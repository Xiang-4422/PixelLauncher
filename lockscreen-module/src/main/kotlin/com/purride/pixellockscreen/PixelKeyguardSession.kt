package com.purride.pixellockscreen

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.purride.pixeldesign.ProductThemeFamily
import com.purride.pixellockscreen.ui.LockscreenRootHost
import com.purride.pixellockscreen.ui.LockscreenContentListener

/**
 * 一次 SystemUI 进程生命周期内的普通像素 Keyguard 挂载与回退会话。
 *
 * 宿主作为窗口级稳定子视图避开原生 Keyguard 的位移与透明度转场；前景遮罩和原生 Bouncer
 * 仍保留在更高层级，宿主显隐由真实 Keyguard 状态显式驱动。
 */
internal class PixelKeyguardSession(
    /** 已通过 Titan 2 签名探测的 SystemUI 视图绑定。 */
    private val binding: Titan2SystemUiBinding,
    /** 会话完全释放后的上层清理回调。 */
    private val onDisposed: (PixelKeyguardSession) -> Unit,
    /** 只包含固定状态码的运行诊断回调，不得传递用户数据。 */
    private val onDiagnostic: (String) -> Unit = {},
) : ViewTreeObserver.OnPreDrawListener,
    ViewTreeObserver.OnDrawListener,
    View.OnAttachStateChangeListener {
    /** 只读解析生物识别、StrongAuth、信任代理和 Extend Unlock 的 Titan 2 适配器。 */
    private val biometricAdapter = Titan2BiometricStateAdapter.bind(binding.indicationController)

    /** 跨熄屏、亮屏和遮挡转场读取真实 Keyguard 显示状态的适配器。 */
    private val visibilityAdapter = Titan2KeyguardVisibilityAdapter.bind(binding.keyguardViewMediator)

    /** 只读解析原生通知隐私结果和当前媒体播放器的 Titan 2 适配器。 */
    private val contentAdapter = Titan2LockscreenContentAdapter.bind(binding.shadeWindow)

    /** 把像素内容卡最小事件转发到 SystemUI 已安装的原生点击链。 */
    private val contentListener: LockscreenContentListener = object : LockscreenContentListener {
        /** 转发当前脱敏键对应的通知点击。 */
        override fun onNotificationRequested(notificationKey: String) {
            contentAdapter.performNotificationClick(notificationKey)
        }

        /** 转发当前媒体会话的播放暂停点击。 */
        override fun onMediaPlayPauseRequested() {
            contentAdapter.performMediaPlayPause()
        }

        /** 转发当前 START 或 END 槽位的原生快捷操作。 */
        override fun onQuickActionRequested(actionKey: String) {
            contentAdapter.performQuickAction(actionKey)
        }

        /** 任何过期或失效的原生操作目标都要求整页回退。 */
        override fun onInteractionFailure(throwable: Throwable) {
            dispose()
        }
    }

    /** 真正绘制 Pixel Engine 锁屏并接收内容卡命中的 Android 宿主。 */
    private val host: LockscreenRootHost = LockscreenRootHost(
        context = binding.keyguardRoot.context,
        contentListener = contentListener,
    ).apply {
        /** SystemUI 动态节点统一使用非零 ID，便于运行时层级诊断。 */
        id = View.generateViewId()
        /** SystemUI 启动时通常仍处于桌面，等待真实 Keyguard 状态后再允许首帧绘制。 */
        visibility = View.INVISIBLE
    }

    /** 普通原生锁屏节点的可恢复显隐事务。 */
    private val nativeVisibility = NativeKeyguardVisibilityTransaction(
        keyguardRoot = binding.keyguardRoot,
        shadeWindow = binding.shadeWindow,
        pixelHost = host,
    )

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

    /** 普通原生锁屏的可恢复隐藏事务当前是否已经生效。 */
    private var visualTakeoverActive: Boolean = false

    /** 判断现有会话是否仍绑定同一 Keyguard 根视图。 */
    fun isBoundTo(keyguardRoot: ViewGroup): Boolean = !disposed && binding.keyguardRoot === keyguardRoot

    /** 在完整像素凭据页展示期间暂停普通时钟页，但继续隐藏普通原生锁屏内容。 */
    fun setCredentialTakeoverActive(active: Boolean) {
        if (disposed || credentialTakeoverActive == active) {
            return
        }
        credentialTakeoverActive = active
        if (active) {
            /** 凭据接管开始必须立即隐藏普通页，避免与安全页首帧重叠。 */
            host.visibility = View.INVISIBLE
        }
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
            /** 前景遮罩继续位于宿主上方，从而保留熄屏、亮屏和解锁的系统明暗动画。 */
            binding.shadeWindow.addView(
                host,
                pixelHostInsertionIndex(
                    anchorIndex = binding.shadeWindow.indexOfChild(binding.foregroundScrim),
                    childCount = binding.shadeWindow.childCount,
                ),
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
            ensurePixelHostLayer()
            stateAdapter.updateSecurity(biometricAdapter.snapshot())
            stateAdapter.updateContent(contentAdapter.snapshot())
            if (disposed) return true
            /** 瞬时 View alpha 不参与判断，避免 DOZING 到 LOCKSCREEN 转场误恢复原生页面。 */
            val ordinaryKeyguardVisible = visibilityAdapter.isOrdinaryKeyguardVisible()
            /** 窗口级宿主不再继承 KeyguardRootView 状态，必须显式限制在普通锁屏阶段。 */
            host.visibility = if (ordinaryKeyguardVisible && !credentialTakeoverActive) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
            /** 凭据页接管时普通宿主隐藏，但仍需继续隐藏其后的原生普通锁屏分支。 */
            val shouldTakeOver = credentialTakeoverActive ||
                (
                    firstFrameDrawn &&
                        ordinaryKeyguardVisible &&
                        isHostStructurallyReady()
                    )
            if (shouldTakeOver) {
                nativeVisibility.hide()
                if (!visualTakeoverActive) {
                    visualTakeoverActive = true
                    onDiagnostic("visual_takeover_active")
                }
            } else {
                nativeVisibility.restore()
                if (visualTakeoverActive) {
                    visualTakeoverActive = false
                    onDiagnostic("visual_takeover_restored")
                }
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
        onDiagnostic("visual_first_frame_drawn")
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

    /**
     * 验证像素宿主仍紧邻在前景遮罩下方的稳定窗口层。
     *
     * 不在运行期自动重排，避免一次临时层级异常把宿主移动到 Bouncer 上方；合同变化时直接走
     * fail-closed 回退，由下一次已验证的 SystemUI 会话重新挂载。
     */
    private fun ensurePixelHostLayer() {
        check(host.parent === binding.shadeWindow) { "pixel_host_parent_changed" }
        check(binding.foregroundScrim.parent === binding.shadeWindow) { "foreground_scrim_parent_changed" }
        /** 宿主必须恰好位于前景遮罩前一层，禁止覆盖系统认证容器。 */
        val hostIndex = binding.shadeWindow.indexOfChild(host)
        /** 前景遮罩的当前窗口层级。 */
        val foregroundScrimIndex = binding.shadeWindow.indexOfChild(binding.foregroundScrim)
        check(hostIndex >= 0 && foregroundScrimIndex == hostIndex + 1) { "pixel_host_z_order_changed" }
    }

    /** 检查像素宿主仍附着、保持完整尺寸且没有被 SystemUI 替换父容器。 */
    private fun isHostStructurallyReady(): Boolean =
        host.isAttachedToWindow &&
            host.width > 0 &&
            host.height > 0 &&
            host.parent === binding.shadeWindow

    /** 幂等恢复原生节点、注销广播、移除监听并释放 Pixel Engine 宿主。 */
    fun dispose() {
        if (disposed || disposing) return
        disposing = true
        runCatching { nativeVisibility.restore() }
        visualTakeoverActive = false
        if (binding.shadeWindow.viewTreeObserver.isAlive) {
            binding.shadeWindow.viewTreeObserver.removeOnPreDrawListener(this)
        }
        if (host.viewTreeObserver.isAlive) {
            host.viewTreeObserver.removeOnDrawListener(this)
        }
        host.removeOnAttachStateChangeListener(this)
        runCatching { stateAdapter.stop() }
        if (host.parent === binding.shadeWindow) {
            binding.shadeWindow.removeView(host)
        }
        runCatching { host.dispose() }
        started = false
        disposed = true
        disposing = false
        onDisposed(this)
    }

}

/**
 * 返回像素宿主在窗口中的插入位置。
 *
 * 宿主插在 `scrim_in_front` 的现有位置，使其位于普通锁屏和通知之上，同时严格保留前景遮罩、
 * 提示区与 Bouncer 的更高绘制层级。
 */
internal fun pixelHostInsertionIndex(anchorIndex: Int, childCount: Int): Int {
    require(childCount > 0) { "childCount must be positive" }
    require(anchorIndex in 0 until childCount) { "anchorIndex must reference an existing child" }
    return anchorIndex
}
