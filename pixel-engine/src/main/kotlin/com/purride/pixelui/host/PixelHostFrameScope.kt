package com.purride.pixelui.host

import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.animation.PixelTickerProviderFactory

/** 定义 `PixelFrameListenerRegistration` 在 `PixelHostFrameScope` 中的可替换调用契约。
 *
 * Registration for one repeating frame listener owned by [PixelHostFrameScope].
 */
public interface PixelFrameListenerRegistration {
    /** 表示 `PixelHostFrameScope` 当前是否满足 `isActive` 对应条件。
 *
 * Whether this listener remains eligible for active frame delivery.
 */
    public val isActive: Boolean

    /** 从 `PixelHostFrameScope` 释放 `dispose` 内容并收敛相关所有权。
 *
 * Removes the listener exactly once.
 */
    public fun dispose()
}

/**
 * 定义 `PixelHostFrameScopeDiagnostics` 在 `PixelHostFrameScope` 中承担的数据与行为边界。
 *
 * Immutable diagnostics for one isolated [PixelHostFrameScope].
 *
 * @property isPaused Whether active time and source scheduling are paused.
 * @property isDisposed Whether terminal scope disposal has completed.
 * @property activeTimeNanos Monotonic scope time excluding paused intervals.
 * @property pendingCallbackCount One-shot callbacks waiting for an active frame.
 * @property frameListenerCount Repeating listeners currently registered.
 * @property activeTickerCount Tickers currently requesting frames.
 * @property liveTickerCount Tickers that still require disposal.
 * @property sourceFramePending Whether one upstream source callback is pending.
 * @property scheduledCallbackCount Cumulative one-shot registrations.
 * @property deliveredCallbackCount Cumulative one-shot deliveries.
 * @property cancelledCallbackCount Cumulative one-shot cancellations.
 * @property registeredListenerCount Cumulative listener registrations.
 * @property disposedListenerCount Cumulative listener disposals.
 * @property scheduledSourceFrameCount Cumulative upstream frame requests.
 * @property dispatchedSourceFrameCount Cumulative upstream frame deliveries.
 * @property cancelledSourceFrameCount Cumulative upstream cancellations.
 */
public data class PixelHostFrameScopeDiagnostics(
    public val isPaused: Boolean,
    public val isDisposed: Boolean,
    public val activeTimeNanos: Long,
    public val pendingCallbackCount: Int,
    public val frameListenerCount: Int,
    public val activeTickerCount: Int,
    public val liveTickerCount: Int,
    public val sourceFramePending: Boolean,
    public val scheduledCallbackCount: Long,
    public val deliveredCallbackCount: Long,
    public val cancelledCallbackCount: Long,
    public val registeredListenerCount: Long,
    public val disposedListenerCount: Long,
    public val scheduledSourceFrameCount: Long,
    public val dispatchedSourceFrameCount: Long,
    public val cancelledSourceFrameCount: Long,
)

/**
 * 定义 `PixelHostFrameScope` 在 `PixelHostFrameScope` 中承担的数据与行为边界。
 *
 * Host-owned frame and ticker lifetime boundary.
 *
 * The scope multiplexes one upstream [sourceScheduler] into cancellable one-shot callbacks,
 * repeating listeners, and one [tickerProvider]. [pause] removes the upstream request and freezes
 * active time without discarding logical work. [resume] reanchors raw time and continues from the
 * previous active value. [dispose] releases every pending callback, ticker, and listener.
 *
 * A scope is intended for one Host/UI thread. Separate instances never share callbacks, tickers,
 * time accounting, or diagnostics even when they use the same source scheduler.
 */
public class PixelHostFrameScope private constructor(
    /** Host 平台或测试提供的上游帧调度器。 */
    private val sourceScheduler: PixelFrameScheduler,
    /** 为当前 scope 创建唯一 ticker provider 的工厂。 */
    tickerProviderFactory: PixelTickerProviderFactory,
    /** 仅用于区分公开兼容构造器与内部主构造器。 */
    @Suppress("UNUSED_PARAMETER") constructorMarker: Unit,
) : PixelCancellableFrameScheduler {
    /** 使用默认 ticker 工厂创建 Host 私有帧边界，保留历史构造器描述符。 */
    public constructor(sourceScheduler: PixelFrameScheduler) : this(
        sourceScheduler = sourceScheduler,
        tickerProviderFactory = PixelTickerProviderFactory.Default,
        constructorMarker = Unit,
    )

    /** 使用可注入 ticker 工厂创建 Host 私有帧边界。 */
    public constructor(
        sourceScheduler: PixelFrameScheduler,
        tickerProviderFactory: PixelTickerProviderFactory,
    ) : this(
        sourceScheduler = sourceScheduler,
        tickerProviderFactory = tickerProviderFactory,
        constructorMarker = Unit,
    )
    /** One-shot callbacks waiting for an active source frame. */
    private val pendingCallbacks: LinkedHashSet<ScopeFrameCallbackRegistration> = linkedSetOf()

    /** Repeating listeners retained in registration order. */
    private val frameListeners: LinkedHashSet<ScopeFrameListenerRegistration> = linkedSetOf()

    /** Upstream source callback currently owned by this scope. */
    private var sourceFrameRegistration: PixelFrameCallbackRegistration? = null

    /** Most recent upstream timestamp used to advance active time. */
    private var lastSourceFrameNanos: Long? = null

    /** Whether the next active source frame must establish a new raw-time anchor. */
    private var reanchorOnNextFrame: Boolean = false

    /** Monotonic scope time excluding every paused interval. */
    private var activeTimeNanos: Long = 0L

    /** 表示 `PixelHostFrameScope` 当前是否满足 `isPaused` 对应条件。
 *
 * Whether this scope currently rejects upstream scheduling and time advancement.
 */
    public var isPaused: Boolean = false
        private set

    /** 表示 `PixelHostFrameScope` 当前是否满足 `isDisposed` 对应条件。
 *
 * Whether terminal cleanup has made this scope permanently inactive.
 */
    public var isDisposed: Boolean = false
        private set

    /** Cumulative one-shot callback registrations. */
    private var scheduledCallbackCount: Long = 0L

    /** Cumulative one-shot callback deliveries. */
    private var deliveredCallbackCount: Long = 0L

    /** Cumulative one-shot callback cancellations. */
    private var cancelledCallbackCount: Long = 0L

    /** Cumulative repeating listener registrations. */
    private var registeredListenerCount: Long = 0L

    /** Cumulative repeating listener disposals. */
    private var disposedListenerCount: Long = 0L

    /** Cumulative upstream frame requests. */
    private var scheduledSourceFrameCount: Long = 0L

    /** Cumulative upstream frame deliveries. */
    private var dispatchedSourceFrameCount: Long = 0L

    /** Cumulative upstream callbacks removed before delivery. */
    private var cancelledSourceFrameCount: Long = 0L

    /** 公开 `PixelHostFrameScope` 的 `tickerProvider` 配置或运行值。
 *
 * Ticker provider whose entire lifetime and active time are owned by this scope.
 */
    public val tickerProvider: PixelTickerProvider = tickerProviderFactory.create(this)

    /** Preserves [PixelFrameScheduler]'s historical fire-and-forget API. */
    override fun scheduleFrame(callback: (Long) -> Unit) {
        scheduleCancellableFrame(callback)
    }

    /** Queues one callback without requesting an upstream frame while paused. */
    override fun scheduleCancellableFrame(
        callback: (Long) -> Unit,
    ): PixelFrameCallbackRegistration {
        if (isDisposed) return InactiveFrameCallbackRegistration
        val registration = ScopeFrameCallbackRegistration(callback)
        pendingCallbacks += registration
        scheduledCallbackCount += 1L
        scheduleSourceFrameIfNeeded()
        return registration
    }

    /** 向 `PixelHostFrameScope` 注册 `addFrameListener` 内容并绑定对应生命周期。
 *
 * Adds one repeating listener and starts upstream scheduling when active.
 */
    public fun addFrameListener(
        listener: (activeTimeNanos: Long) -> Unit,
    ): PixelFrameListenerRegistration {
        if (isDisposed) return InactiveFrameListenerRegistration
        val registration = ScopeFrameListenerRegistration(listener)
        frameListeners += registration
        registeredListenerCount += 1L
        scheduleSourceFrameIfNeeded()
        return registration
    }

    /**
 * 执行 `PixelHostFrameScope` 的 `pause` 公开行为；具体参数、返回和副作用见下文。
 *
     * Freezes active time and removes the pending upstream callback.
     *
     * One-shot callbacks and listeners stay registered but cannot request new upstream frames.
     * Active tickers also remain active so they continue from the same value after [resume].
     */
    public fun pause() {
        if (isDisposed || isPaused) return
        isPaused = true
        reanchorOnNextFrame = true
        tickerProvider.pause()
        cancelSourceFrame()
    }

    /** 执行 `PixelHostFrameScope` 的 `resume` 公开行为；具体参数、返回和副作用见下文。
 *
 * Reanchors source time and resumes all retained callbacks, listeners, and tickers.
 */
    public fun resume() {
        if (isDisposed || !isPaused) return
        isPaused = false
        reanchorOnNextFrame = true
        tickerProvider.resume()
        scheduleSourceFrameIfNeeded()
    }

    /** 从 `PixelHostFrameScope` 释放 `dispose` 内容并收敛相关所有权。
 *
 * Cancels every owned source callback, logical callback, ticker, and listener exactly once.
 */
    public fun dispose() {
        if (isDisposed) return
        isDisposed = true
        isPaused = false
        cancelSourceFrame()
        tickerProvider.dispose()
        pendingCallbacks.toList().forEach(ScopeFrameCallbackRegistration::cancelFromScope)
        pendingCallbacks.clear()
        frameListeners.toList().forEach(ScopeFrameListenerRegistration::disposeFromScope)
        frameListeners.clear()
    }

    /** 执行 `PixelHostFrameScope` 的 `diagnostics` 公开行为；具体参数、返回和副作用见下文。
 *
 * Captures isolated counters without retaining callbacks, tickers, or listeners.
 */
    public fun diagnostics(): PixelHostFrameScopeDiagnostics {
        val tickerDiagnostics = tickerProvider.diagnostics()
        return PixelHostFrameScopeDiagnostics(
            isPaused = isPaused,
            isDisposed = isDisposed,
            activeTimeNanos = activeTimeNanos,
            pendingCallbackCount = pendingCallbacks.size,
            frameListenerCount = frameListeners.size,
            activeTickerCount = tickerDiagnostics.activeTickerCount,
            liveTickerCount = tickerDiagnostics.liveTickerCount,
            sourceFramePending = sourceFrameRegistration?.isPending == true,
            scheduledCallbackCount = scheduledCallbackCount,
            deliveredCallbackCount = deliveredCallbackCount,
            cancelledCallbackCount = cancelledCallbackCount,
            registeredListenerCount = registeredListenerCount,
            disposedListenerCount = disposedListenerCount,
            scheduledSourceFrameCount = scheduledSourceFrameCount,
            dispatchedSourceFrameCount = dispatchedSourceFrameCount,
            cancelledSourceFrameCount = cancelledSourceFrameCount,
        )
    }

    /** Requests one upstream frame only while active work exists. */
    private fun scheduleSourceFrameIfNeeded() {
        if (
            isDisposed ||
            isPaused ||
            sourceFrameRegistration?.isPending == true ||
            !hasFrameDemand()
        ) {
            return
        }
        sourceFrameRegistration = sourceScheduler.scheduleCancellableFrame(::dispatchSourceFrame)
        scheduledSourceFrameCount += 1L
    }

    /** Delivers one active-time frame while respecting same-frame pause or disposal. */
    private fun dispatchSourceFrame(sourceFrameNanos: Long) {
        sourceFrameRegistration = null
        if (isDisposed || isPaused) return
        val frameActiveTimeNanos = advanceActiveTime(sourceFrameNanos)
        dispatchedSourceFrameCount += 1L
        try {
            val callbackSnapshot: List<ScopeFrameCallbackRegistration> = pendingCallbacks.toList()
            for (registration in callbackSnapshot) {
                if (isDisposed || isPaused) break
                registration.dispatch(frameActiveTimeNanos)
            }
            val listenerSnapshot: List<ScopeFrameListenerRegistration> = frameListeners.toList()
            for (registration in listenerSnapshot) {
                if (isDisposed || isPaused) break
                registration.dispatch(frameActiveTimeNanos)
            }
        } finally {
            scheduleSourceFrameIfNeeded()
        }
    }

    /** Advances active time or reanchors the first frame after resume. */
    private fun advanceActiveTime(sourceFrameNanos: Long): Long {
        val previousSourceFrame = lastSourceFrameNanos
        if (previousSourceFrame == null || reanchorOnNextFrame) {
            lastSourceFrameNanos = sourceFrameNanos
            reanchorOnNextFrame = false
            return activeTimeNanos
        }
        if (sourceFrameNanos > previousSourceFrame) {
            val delta = sourceFrameNanos - previousSourceFrame
            activeTimeNanos = if (Long.MAX_VALUE - activeTimeNanos < delta) {
                Long.MAX_VALUE
            } else {
                activeTimeNanos + delta
            }
        }
        lastSourceFrameNanos = sourceFrameNanos
        return activeTimeNanos
    }

    /** Returns whether at least one callback or repeating listener needs a frame. */
    private fun hasFrameDemand(): Boolean {
        return pendingCallbacks.isNotEmpty() || frameListeners.isNotEmpty()
    }

    /** Cancels the upstream source callback after the final demand is removed. */
    private fun cancelSourceFrameIfIdle() {
        if (!hasFrameDemand()) cancelSourceFrame()
    }

    /** Cancels and releases the currently pending upstream callback. */
    private fun cancelSourceFrame() {
        val registration = sourceFrameRegistration ?: return
        sourceFrameRegistration = null
        if (registration.cancel()) {
            cancelledSourceFrameCount += 1L
        }
    }

    /** One cancellable callback retained by this scope. */
    private inner class ScopeFrameCallbackRegistration(
        /** Consumer callback delivered at most once. */
        private val callback: (Long) -> Unit,
    ) : PixelFrameCallbackRegistration {
        /** Whether this callback remains queued for delivery. */
        override var isPending: Boolean = true
            private set

        /** Cancels and removes this callback from its scope. */
        override fun cancel(): Boolean {
            if (!isPending) return false
            isPending = false
            pendingCallbacks.remove(this)
            cancelledCallbackCount += 1L
            cancelSourceFrameIfIdle()
            return true
        }

        /** Cancels this callback during terminal scope disposal. */
        fun cancelFromScope() {
            if (!isPending) return
            isPending = false
            cancelledCallbackCount += 1L
        }

        /** Claims, removes, and invokes this callback once. */
        fun dispatch(frameActiveTimeNanos: Long) {
            if (!isPending) return
            isPending = false
            pendingCallbacks.remove(this)
            deliveredCallbackCount += 1L
            callback(frameActiveTimeNanos)
        }
    }

    /** One repeating listener retained by this scope. */
    private inner class ScopeFrameListenerRegistration(
        /** Consumer listener invoked on each active source frame. */
        private val listener: (Long) -> Unit,
    ) : PixelFrameListenerRegistration {
        /** Whether this listener remains registered. */
        override var isActive: Boolean = true
            private set

        /** Removes this listener and cancels an idle upstream request. */
        override fun dispose() {
            if (!isActive) return
            isActive = false
            frameListeners.remove(this)
            disposedListenerCount += 1L
            cancelSourceFrameIfIdle()
        }

        /** Removes this listener during terminal scope disposal. */
        fun disposeFromScope() {
            if (!isActive) return
            isActive = false
            disposedListenerCount += 1L
        }

        /** Invokes this listener only while it remains registered. */
        fun dispatch(frameActiveTimeNanos: Long) {
            if (!isActive) return
            listener(frameActiveTimeNanos)
        }
    }
}

/** Inert callback handle returned after terminal scope disposal. */
private object InactiveFrameCallbackRegistration : PixelFrameCallbackRegistration {
    /** A disposed scope never has pending callbacks. */
    override val isPending: Boolean = false

    /** No callback exists to cancel. */
    override fun cancel(): Boolean = false
}

/** Inert listener handle returned after terminal scope disposal. */
private object InactiveFrameListenerRegistration : PixelFrameListenerRegistration {
    /** A disposed scope never has active listeners. */
    override val isActive: Boolean = false

    /** Terminal disposal is already complete. */
    override fun dispose(): Unit = Unit
}
