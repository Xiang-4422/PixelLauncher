package com.purride.pixelui.host

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 定义 `PixelFrameCallbackRegistration` 在 `PixelCancellableFrameScheduler` 中的可替换调用契约。
 *
 * Handle for one not-yet-delivered frame callback.
 *
 * Cancellation is idempotent. A callback that has already started or completed is no longer
 * pending and cannot be cancelled retroactively.
 */
public interface PixelFrameCallbackRegistration {
    /** 表示 `PixelCancellableFrameScheduler` 当前是否满足 `isPending` 对应条件。
 *
 * Whether the callback is still eligible for one future delivery.
 */
    public val isPending: Boolean

    /**
 * 判断 `PixelCancellableFrameScheduler` 是否满足 `cancel` 条件，不修改现有状态。
 *
     * Cancels the pending callback.
     *
     * @return `true` only when this call changed a pending callback to cancelled.
     */
    public fun cancel(): Boolean
}

/**
 * 定义 `PixelCancellableFrameScheduler` 在 `PixelCancellableFrameScheduler` 中的可替换调用契约。
 *
 * Optional scheduler capability that can physically remove a pending frame callback.
 *
 * [PixelFrameScheduler.scheduleFrame] intentionally keeps its historical `Unit` return type.
 * Host-owned lifecycle scopes use this additive capability when available and fall back to a
 * guarded logical cancellation for third-party schedulers implementing only the original API.
 */
public interface PixelCancellableFrameScheduler : PixelFrameScheduler {
    /** 执行 `PixelCancellableFrameScheduler` 的 `scheduleCancellableFrame` 公开行为；具体参数、返回和副作用见下文。
 *
 * Schedules one frame callback and returns its cancellation registration.
 */
    public fun scheduleCancellableFrame(
        callback: (frameTimeNanos: Long) -> Unit,
    ): PixelFrameCallbackRegistration
}

/**
 * 执行 `PixelCancellableFrameScheduler` 的 `scheduleCancellableFrame` 公开行为；具体参数、返回和副作用见下文。
 *
 * Schedules a cancellable callback without changing [PixelFrameScheduler.scheduleFrame].
 *
 * Native cancellation is used for [PixelCancellableFrameScheduler]. Other scheduler
 * implementations receive a guarded callback whose body becomes a no-op after cancellation.
 */
public fun PixelFrameScheduler.scheduleCancellableFrame(
    callback: (frameTimeNanos: Long) -> Unit,
): PixelFrameCallbackRegistration {
    if (this is PixelCancellableFrameScheduler) {
        return scheduleCancellableFrame(callback)
    }
    val registration = GuardedFrameCallbackRegistration(callback)
    scheduleFrame(registration::dispatch)
    return registration
}

/** Logical cancellation fallback for schedulers without a removal capability. */
private class GuardedFrameCallbackRegistration(
    /** Consumer callback guarded by [pending]. */
    private val callback: (Long) -> Unit,
) : PixelFrameCallbackRegistration {
    /** Atomic pending bit permits cancellation from a lifecycle callback or another thread. */
    private val pending: AtomicBoolean = AtomicBoolean(true)

    /** Whether the guarded callback has not yet been cancelled or claimed for dispatch. */
    override val isPending: Boolean
        get() = pending.get()

    /** Atomically prevents a future callback body from running. */
    override fun cancel(): Boolean = pending.compareAndSet(true, false)

    /** Claims and invokes the callback at most once. */
    fun dispatch(frameTimeNanos: Long) {
        if (!pending.compareAndSet(true, false)) return
        callback(frameTimeNanos)
    }
}
