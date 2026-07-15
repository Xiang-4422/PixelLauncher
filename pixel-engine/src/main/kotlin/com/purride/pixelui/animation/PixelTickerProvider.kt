package com.purride.pixelui.animation

import com.purride.pixelui.host.PixelFrameCallbackRegistration
import com.purride.pixelui.host.PixelFrameScheduler
import com.purride.pixelui.host.scheduleCancellableFrame

/**
 * 定义 `PixelTickerProviderDiagnostics` 在 `PixelTickerProvider` 中承担的数据与行为边界。
 *
 * Immutable counters describing one [PixelTickerProvider] without exposing ticker references.
 *
 * @property activeTickerCount Tickers currently requesting frames.
 * @property liveTickerCount Created tickers that have not been disposed.
 * @property pendingFrameCallbackCount Number of provider-owned source callbacks, always zero or one.
 * @property createdTickerCount Cumulative ticker creations for leak and soak assertions.
 * @property disposedTickerCount Cumulative ticker disposals for leak and soak assertions.
 * @property dispatchedFrameCount Active frames delivered by this provider.
 * @property activeTimeNanos Provider time excluding every paused interval.
 * @property isPaused Whether frame delivery is currently frozen.
 * @property isDisposed Whether terminal provider disposal has completed.
 */
public data class PixelTickerProviderDiagnostics(
    public val activeTickerCount: Int,
    public val liveTickerCount: Int,
    public val pendingFrameCallbackCount: Int,
    public val createdTickerCount: Long,
    public val disposedTickerCount: Long,
    public val dispatchedFrameCount: Long,
    public val activeTimeNanos: Long,
    public val isPaused: Boolean,
    public val isDisposed: Boolean,
)

/**
 * 定义 `PixelTickerProvider` 在 `PixelTickerProvider` 中承担的数据与行为边界。
 *
 * Creates and coalesces [PixelTicker] instances on one frame scheduler.
 *
 * The original constructor and ticker factory remain source and binary compatible. Additive
 * lifecycle methods let a Host-owned scope freeze active time, cancel its pending source frame,
 * resume continuously, and dispose every ticker deterministically.
 */
public class PixelTickerProvider(
    /** Scheduler that supplies source frame timestamps. */
    private val frameScheduler: PixelFrameScheduler,
) {
    /** Active tickers retained in deterministic activation order. */
    private val activeTickers: LinkedHashSet<PixelTicker> = linkedSetOf()

    /** All non-disposed tickers owned by this provider. */
    private val liveTickers: LinkedHashSet<PixelTicker> = linkedSetOf()

    /** Cancellable source callback currently owned by this provider. */
    private var scheduledFrame: PixelFrameCallbackRegistration? = null

    /** Most recent source timestamp used to advance active time. */
    private var lastSourceFrameNanos: Long? = null

    /** Monotonic provider time that excludes paused source intervals. */
    private var activeTimeNanos: Long = 0L

    /** Whether the first source frame after resume must establish a new raw-time anchor. */
    private var reanchorOnNextFrame: Boolean = false

    /** 表示 `PixelTickerProvider` 当前是否满足 `isPaused` 对应条件。
 *
 * Whether frame scheduling and active-time advancement are paused.
 */
    public var isPaused: Boolean = false
        private set

    /** 表示 `PixelTickerProvider` 当前是否满足 `isDisposed` 对应条件。
 *
 * Whether this provider has permanently disposed its tickers and pending callback.
 */
    public var isDisposed: Boolean = false
        private set

    /** Cumulative number of tickers created by this provider. */
    private var createdTickerCount: Long = 0L

    /** Cumulative number of tickers disposed by this provider. */
    private var disposedTickerCount: Long = 0L

    /** Cumulative number of active frames dispatched by this provider. */
    private var dispatchedFrameCount: Long = 0L

    /** 公开 `PixelTickerProvider` 的 `activeTickerCount` 配置或运行值。
 *
 * Number of tickers currently requesting frames.
 */
    public val activeTickerCount: Int
        get() = activeTickers.size

    /** 公开 `PixelTickerProvider` 的 `liveTickerCount` 配置或运行值。
 *
 * Number of created tickers that still require disposal.
 */
    public val liveTickerCount: Int
        get() = liveTickers.size

    /**
     * 创建一个 ticker。
     *
     * 当 [maxFps] 为 null（默认）时：每个 vsync 都派发 [onTick]。
     *
     * 当 [maxFps] 大于 0 时：provider 内部包装 [onTick]，只有距上次派发
     * 间隔 ≥ `1_000_000_000 / maxFps` 纳秒时才向调用方派发，中间帧被丢弃。
     * `elapsedNanos` 使用排除 pause 区间的 active time，因此 Host 从后台恢复时
     * 动画不会突然跳过暂停时长。
     *
     * 这是像素引擎专属能力：限制单个动画到 15 / 30 FPS 让风格保留离散感。
     */
    public fun createTicker(
        maxFps: Int? = null,
        onTick: (Long) -> Unit,
    ): PixelTicker {
        check(!isDisposed) { "PixelTickerProvider is already disposed" }
        val callback: (Long) -> Unit = if (maxFps == null) onTick else {
            require(maxFps > 0) { "maxFps must be > 0, got $maxFps" }
            val minIntervalNanos = 1_000_000_000L / maxFps
            var lastDispatchNanos = -1L
            { elapsedNanos ->
                if (lastDispatchNanos < 0L || elapsedNanos - lastDispatchNanos >= minIntervalNanos) {
                    lastDispatchNanos = elapsedNanos
                    onTick(elapsedNanos)
                }
            }
        }
        val ticker = PixelTicker(onTick = callback, provider = this)
        liveTickers += ticker
        createdTickerCount += 1L
        return ticker
    }

    /**
 * 执行 `PixelTickerProvider` 的 `pause` 公开行为；具体参数、返回和副作用见下文。
 *
     * Freezes active time and cancels the provider-owned pending source callback.
     *
     * Tickers remain active so [resume] continues them from the same elapsed value.
     */
    public fun pause() {
        if (isDisposed || isPaused) return
        isPaused = true
        reanchorOnNextFrame = true
        cancelScheduledFrame()
    }

    /** 执行 `PixelTickerProvider` 的 `resume` 公开行为；具体参数、返回和副作用见下文。
 *
 * Resumes active tickers without including the paused raw-time interval.
 */
    public fun resume() {
        if (isDisposed || !isPaused) return
        isPaused = false
        reanchorOnNextFrame = true
        scheduleIfNeeded()
    }

    /** 从 `PixelTickerProvider` 释放 `dispose` 内容并收敛相关所有权。
 *
 * Cancels the pending callback and disposes every active or inactive ticker exactly once.
 */
    public fun dispose() {
        if (isDisposed) return
        isDisposed = true
        isPaused = false
        cancelScheduledFrame()
        liveTickers.toList().forEach(PixelTicker::dispose)
        activeTickers.clear()
        liveTickers.clear()
    }

    /** 执行 `PixelTickerProvider` 的 `diagnostics` 公开行为；具体参数、返回和副作用见下文。
 *
 * Captures stable diagnostics for lifecycle and 10,000-cycle leak assertions.
 */
    public fun diagnostics(): PixelTickerProviderDiagnostics {
        return PixelTickerProviderDiagnostics(
            activeTickerCount = activeTickers.size,
            liveTickerCount = liveTickers.size,
            pendingFrameCallbackCount = if (scheduledFrame?.isPending == true) 1 else 0,
            createdTickerCount = createdTickerCount,
            disposedTickerCount = disposedTickerCount,
            dispatchedFrameCount = dispatchedFrameCount,
            activeTimeNanos = activeTimeNanos,
            isPaused = isPaused,
            isDisposed = isDisposed,
        )
    }

    /** Activates [ticker] and coalesces it onto the provider's single source callback. */
    internal fun activate(ticker: PixelTicker) {
        if (isDisposed || ticker !in liveTickers) return
        activeTickers += ticker
        scheduleIfNeeded()
    }

    /** Deactivates [ticker] and removes an unnecessary pending source callback. */
    internal fun deactivate(ticker: PixelTicker) {
        activeTickers -= ticker
        if (activeTickers.isEmpty()) cancelScheduledFrame()
    }

    /** Releases provider ownership after one ticker reaches terminal disposal. */
    internal fun unregister(ticker: PixelTicker) {
        if (liveTickers.remove(ticker)) {
            disposedTickerCount += 1L
        }
        activeTickers -= ticker
        if (activeTickers.isEmpty()) cancelScheduledFrame()
    }

    /** Schedules one cancellable source callback when active work requires it. */
    private fun scheduleIfNeeded() {
        if (
            isDisposed ||
            isPaused ||
            scheduledFrame?.isPending == true ||
            activeTickers.isEmpty()
        ) {
            return
        }
        scheduledFrame = frameScheduler.scheduleCancellableFrame(::dispatchFrame)
    }

    /** Dispatches one source frame using pause-excluding active time. */
    private fun dispatchFrame(frameNanos: Long) {
        scheduledFrame = null
        if (isDisposed || isPaused || activeTickers.isEmpty()) return
        val frameActiveTimeNanos = advanceActiveTime(frameNanos)
        dispatchedFrameCount += 1L
        try {
            val snapshot: List<PixelTicker> = activeTickers.toList()
            for (ticker in snapshot) {
                if (isDisposed || isPaused) break
                ticker.dispatchTick(frameActiveTimeNanos)
            }
        } finally {
            scheduleIfNeeded()
        }
    }

    /** Advances active time or reanchors the first frame after a paused interval. */
    private fun advanceActiveTime(frameNanos: Long): Long {
        val previousSourceFrame = lastSourceFrameNanos
        if (previousSourceFrame == null || reanchorOnNextFrame) {
            lastSourceFrameNanos = frameNanos
            reanchorOnNextFrame = false
            return activeTimeNanos
        }
        if (frameNanos > previousSourceFrame) {
            val delta = frameNanos - previousSourceFrame
            activeTimeNanos = if (Long.MAX_VALUE - activeTimeNanos < delta) {
                Long.MAX_VALUE
            } else {
                activeTimeNanos + delta
            }
        }
        lastSourceFrameNanos = frameNanos
        return activeTimeNanos
    }

    /** Cancels and releases the pending source callback, when present. */
    private fun cancelScheduledFrame() {
        val registration = scheduledFrame ?: return
        scheduledFrame = null
        registration.cancel()
    }
}
