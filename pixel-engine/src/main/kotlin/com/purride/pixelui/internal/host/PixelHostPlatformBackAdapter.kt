package com.purride.pixelui.internal.host

import android.annotation.TargetApi
import android.os.Build
import android.view.View
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.purride.pixelui.PixelPredictiveBackEvent
import com.purride.pixelui.PixelPredictiveBackSwipeEdge

/** Android 平台 callback 向 Host 暴露的稳定、可伪造事件边界。 */
internal interface PixelHostPlatformBackCallbacks {
    /** Android 14+ 手势开始。 */
    fun onBackStarted(event: PixelPredictiveBackEvent)

    /** Android 14+ 手势进度更新。 */
    fun onBackProgressed(event: PixelPredictiveBackEvent)

    /** Android 14+ 手势取消。 */
    fun onBackCancelled()

    /** API 33+ 完成事件，或 Android 14+ 手势提交。 */
    fun onBackInvoked()
}

/** 一次平台注册的幂等释放句柄。 */
internal fun interface PixelHostPlatformBackRegistration {
    /** 解除平台注册。 */
    fun dispose()
}

/** 可替换的平台 callback 注册器，JVM 测试用 fake 验证生命周期而不冒充设备事件。 */
internal fun interface PixelHostPlatformBackRegistrar {
    /** 注册 [callbacks]；当前平台不支持或 View 尚不可注册时返回 `null`。 */
    fun register(callbacks: PixelHostPlatformBackCallbacks): PixelHostPlatformBackRegistration?
}

/** Android API level 对应的平台返回能力，用于显式审计 24/33/34 分叉。 */
internal enum class PixelHostAndroidBackCapability {
    /** API 24–32：没有平台 callback，由 Activity 调用离散兼容入口。 */
    Manual,

    /** API 33：只有返回完成 callback，没有手势进度。 */
    Invoked,

    /** API 34+：具有 start/progress/cancel/commit 完整动画 callback。 */
    Animation,
}

/** 把 Android SDK level 映射为 Host 支持的返回能力。 */
internal fun resolvePixelHostAndroidBackCapability(sdkInt: Int): PixelHostAndroidBackCapability {
    return when {
        sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> PixelHostAndroidBackCapability.Animation
        sdkInt >= Build.VERSION_CODES.TIRAMISU -> PixelHostAndroidBackCapability.Invoked
        else -> PixelHostAndroidBackCapability.Manual
    }
}

/**
 * 管理 View attach、handler 可用性与平台注册之间的一对一关系。
 *
 * 控制器不依赖 Android API level，平台差异由 [PixelHostPlatformBackRegistrar] 隔离，
 * 因此可以在普通 JVM 中验证不会重复注册、泄漏或遗留未取消会话。
 */
internal class PixelHostPlatformBackController(
    /** 实际执行 API 33/34 注册的适配器。 */
    private val registrar: PixelHostPlatformBackRegistrar,
    /** Host 当前是否确实需要拦截系统返回。 */
    private val shouldRegister: () -> Boolean,
    /** 转发给 Host 会话状态机的 callback。 */
    private val callbacks: PixelHostPlatformBackCallbacks,
) {
    /** View 当前是否处于 attach 生命周期内。 */
    private var attached: Boolean = false

    /** 当前唯一的平台 callback 注册。 */
    private var registration: PixelHostPlatformBackRegistration? = null

    /** View attach 后按当前 handler 可用性尝试注册。 */
    fun attach() {
        if (attached) return
        attached = true
        refresh()
    }

    /**
     * 在 handler/focus/配置变化后同步注册状态。
     *
     * API 24–32 的 registrar 返回 `null`，保留 [PixelHostView.handleBackPressed] 兼容路径。
     */
    fun refresh() {
        val needsRegistration = attached && shouldRegister()
        if (!needsRegistration) {
            unregister()
            return
        }
        if (registration == null) {
            registration = registrar.register(callbacks)
        }
    }

    /** View detach 时取消手势并解除平台注册，重复调用安全。 */
    fun detach() {
        if (!attached && registration == null) return
        attached = false
        unregister()
    }

    /** 先回滚可能正在进行的手势，再释放平台 callback。 */
    private fun unregister() {
        val activeRegistration = registration ?: return
        registration = null
        callbacks.onBackCancelled()
        activeRegistration.dispose()
    }
}

/**
 * Android View 对 API 33 `OnBackInvokedCallback` 与 API 34 `OnBackAnimationCallback` 的薄适配。
 */
internal class AndroidPixelHostBackRegistrar(
    /** 用于查询所属 Window dispatcher 的 Pixel Host View。 */
    private val view: View,
) : PixelHostPlatformBackRegistrar {
    override fun register(
        callbacks: PixelHostPlatformBackCallbacks,
    ): PixelHostPlatformBackRegistration? {
        if (!view.isAttachedToWindow) return null
        return when (resolvePixelHostAndroidBackCapability(Build.VERSION.SDK_INT)) {
            PixelHostAndroidBackCapability.Manual -> null
            PixelHostAndroidBackCapability.Invoked -> Api33.register(view, callbacks)
            PixelHostAndroidBackCapability.Animation -> Api34.register(view, callbacks)
        }
    }

    /** API 33 仅在返回已经确定后提供一个完成回调。 */
    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private object Api33 {
        /** 在 View 所属 Window 上注册离散完成 callback。 */
        fun register(
            view: View,
            callbacks: PixelHostPlatformBackCallbacks,
        ): PixelHostPlatformBackRegistration? {
            val dispatcher = view.findOnBackInvokedDispatcher() ?: return null
            val callback = OnBackInvokedCallback { callbacks.onBackInvoked() }
            dispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback,
            )
            return idempotentRegistration {
                dispatcher.unregisterOnBackInvokedCallback(callback)
            }
        }
    }

    /** API 34+ 提供完整的 start/progress/cancel/commit 动画 callback。 */
    @TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private object Api34 {
        /** 在 View 所属 Window 上注册可交互动画 callback。 */
        fun register(
            view: View,
            callbacks: PixelHostPlatformBackCallbacks,
        ): PixelHostPlatformBackRegistration? {
            val dispatcher = view.findOnBackInvokedDispatcher() ?: return null
            val callback = createPixelHostOnBackAnimationCallback(callbacks)
            dispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback,
            )
            return idempotentRegistration {
                dispatcher.unregisterOnBackInvokedCallback(callback)
            }
        }
    }

    private companion object {
        /** 把平台 unregister 动作包装成幂等句柄。 */
        fun idempotentRegistration(
            unregister: () -> Unit,
        ): PixelHostPlatformBackRegistration {
            return object : PixelHostPlatformBackRegistration {
                /** 句柄是否已经释放。 */
                private var disposed: Boolean = false

                override fun dispose() {
                    if (disposed) return
                    disposed = true
                    unregister()
                }
            }
        }
    }
}

/**
 * 构造 API 34 平台 callback；独立函数让 instrumentation 能用真实 [BackEvent] 验证映射。
 */
@TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal fun createPixelHostOnBackAnimationCallback(
    callbacks: PixelHostPlatformBackCallbacks,
): OnBackAnimationCallback {
    return object : OnBackAnimationCallback {
        override fun onBackStarted(backEvent: BackEvent) {
            callbacks.onBackStarted(backEvent.toPixelEvent())
        }

        override fun onBackProgressed(backEvent: BackEvent) {
            callbacks.onBackProgressed(backEvent.toPixelEvent())
        }

        override fun onBackCancelled() {
            callbacks.onBackCancelled()
        }

        override fun onBackInvoked() {
            callbacks.onBackInvoked()
        }
    }
}

/** 把 Android 原始坐标/边缘映射到不泄漏平台类型的公共事件。 */
@TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun BackEvent.toPixelEvent(): PixelPredictiveBackEvent {
    val edge = when (swipeEdge) {
        BackEvent.EDGE_LEFT -> PixelPredictiveBackSwipeEdge.Left
        BackEvent.EDGE_RIGHT -> PixelPredictiveBackSwipeEdge.Right
        else -> PixelPredictiveBackSwipeEdge.None
    }
    return PixelPredictiveBackEvent(
        progress = progress.finiteOrZero().coerceIn(0f, 1f),
        touchX = touchX.finiteOrZero(),
        touchY = touchY.finiteOrZero(),
        swipeEdge = edge,
    )
}

/** 防御非标准平台实现返回 NaN/Infinity，避免事件构造破坏 UI 线程。 */
private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f
