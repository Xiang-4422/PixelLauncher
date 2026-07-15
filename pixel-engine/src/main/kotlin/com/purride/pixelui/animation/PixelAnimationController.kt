package com.purride.pixelui.animation

import com.purride.pixelui.ChangeNotifier
import com.purride.pixelui.Listenable
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Duration

/** 定义 `Animation` 在 `PixelAnimationController` 中的可替换调用契约。
 *
 * Read-only listenable animation value and status contract.
 */
public interface Animation<T> : Listenable {
    /** 公开 `PixelAnimationController` 的 `value` 配置或运行值。
 *
 * Current animation value.
 */
    public val value: T

    /** 公开 `PixelAnimationController` 当前的 `status` 状态维度。
 *
 * Current direction or terminal boundary status.
 */
    public val status: PixelAnimationStatus
}

/**
 * 定义 `PixelAnimationController` 在 `PixelAnimationController` 中承担的数据与行为边界。
 *
 * Drives a normalized `0f..1f` animation on one [PixelTickerProvider].
 *
 * [duration] describes a full-range trip. Starting or reversing from an interior value scales the
 * remaining duration by the distance to the requested endpoint. Host/provider pause intervals are
 * excluded by the ticker's active-time clock.
 */
public class PixelAnimationController(
    /** 公开 `PixelAnimationController` 的 `duration` 配置或运行值。
 *
 * Duration of one complete `0f..1f` trip.
 */
    public val duration: Duration,
    /** Provider that owns the controller's ticker and active-time clock. */
    vsync: PixelTickerProvider,
    /** Initial value, clamped into the normalized `0f..1f` range. */
    initialValue: Float = 0f,
) : ChangeNotifier(), Animation<Float> {
    /** Current normalized value exposed through [value]. */
    private var currentValue: Float = initialValue.coerceIn(0f, 1f)

    /** Current direction or terminal boundary status exposed through [status]. */
    private var currentStatus: PixelAnimationStatus = terminalStatusForStableValue(currentValue)

    /** Value captured when the current finite segment starts. */
    private var segmentStartValue: Float = currentValue

    /** Endpoint requested by the current finite segment. */
    private var segmentTargetValue: Float = currentValue

    /** Active-time duration of the current finite segment. */
    private var segmentDurationNanos: Long = 0L

    /** `null` for a finite segment, `false` for forward loop, and `true` for ping-pong. */
    private var repeatMode: Boolean? = null

    /** Whether terminal controller disposal has already completed. */
    private var disposed: Boolean = false

    init {
        require(!duration.isNegative()) { "PixelAnimationController duration must not be negative" }
        require(!initialValue.isNaN()) { "PixelAnimationController initialValue must not be NaN" }
    }

    /** Ticker owned for the complete controller lifetime. */
    private val ticker: PixelTicker = vsync.createTicker { elapsedNanos ->
        onTick(elapsedNanos)
    }

    /** Current normalized animation value. */
    override val value: Float
        get() = currentValue

    /** Current direction or terminal boundary status. */
    override val status: PixelAnimationStatus
        get() = currentStatus

    /** 表示 `PixelAnimationController` 当前是否满足 `isAnimating` 对应条件。
 *
 * Whether the ticker is actively requesting frames.
 */
    public val isAnimating: Boolean
        get() = ticker.isActive

    /**
 * 执行 `PixelAnimationController` 的 `forward` 公开行为；具体参数、返回和副作用见下文。
 *
     * Runs toward `1f` from [from] or the current visual value.
     *
     * An explicit [from] is clamped and applied immediately. Omitting it during a direction change
     * preserves the current value, so forward/reverse retargeting has no visual jump.
     */
    public fun forward(from: Float? = null) {
        startFiniteMotion(
            from = validatedOptionalValue(from, "forward(from)"),
            target = 1f,
            direction = PixelAnimationStatus.Forward,
            terminalStatus = PixelAnimationStatus.Completed,
        )
    }

    /**
 * 执行 `PixelAnimationController` 的 `reverse` 公开行为；具体参数、返回和副作用见下文。
 *
     * Runs toward `0f` from [from] or the current visual value.
     *
     * An explicit [from] is clamped and applied immediately. Omitting it reverses continuously from
     * the value presented by the latest frame.
     */
    public fun reverse(from: Float? = null) {
        startFiniteMotion(
            from = validatedOptionalValue(from, "reverse(from)"),
            target = 0f,
            direction = PixelAnimationStatus.Reverse,
            terminalStatus = PixelAnimationStatus.Dismissed,
        )
    }

    /**
 * 执行 `PixelAnimationController` 的 `repeat` 公开行为；具体参数、返回和副作用见下文。
 *
     * Repeats from `0f`, optionally alternating direction at each endpoint.
     *
     * Repeating a zero-duration controller settles synchronously: forward-only repeat ends at
     * `1f`, while ping-pong represents one complete cycle and ends at `0f`. Neither schedules a
     * frame, avoiding an infinite same-frame loop.
     */
    public fun repeat(reverse: Boolean = false) {
        ensureUsable()
        ticker.stop()
        val oldValue = currentValue
        val oldStatus = currentStatus
        currentValue = 0f
        segmentStartValue = 0f
        segmentTargetValue = 1f
        segmentDurationNanos = fullDurationNanos()
        repeatMode = reverse
        if (segmentDurationNanos == 0L) {
            currentValue = if (reverse) 0f else 1f
            currentStatus = if (reverse) {
                PixelAnimationStatus.Dismissed
            } else {
                PixelAnimationStatus.Completed
            }
        } else {
            currentStatus = PixelAnimationStatus.Forward
            ticker.start()
        }
        notifyIfChanged(oldValue, oldStatus)
    }

    /**
 * 执行 `PixelAnimationController` 的 `stop` 公开行为；具体参数、返回和副作用见下文。
 *
     * Cancels active motion while preserving the current value and direction status.
     *
     * Since [isAnimating] reflects ticker activity rather than direction status, a stopped
     * controller reports `false` even when [status] remains `Forward` or `Reverse`. Repeated stop is
     * an idempotent no-op.
     */
    public fun stop() {
        ticker.stop()
        repeatMode = null
    }

    /** 执行 `PixelAnimationController` 的 `reset` 公开行为；具体参数、返回和副作用见下文。
 *
 * Stops motion and restores the dismissed `0f` boundary.
 */
    public fun reset() {
        ensureUsable()
        ticker.stop()
        repeatMode = null
        segmentStartValue = 0f
        segmentTargetValue = 0f
        segmentDurationNanos = 0L
        updateState(value = 0f, status = PixelAnimationStatus.Dismissed)
    }

    /** 更新 `PixelAnimationController` 的 `setValue` 状态并保持派生数据一致。
 *
 * Stops motion and applies a clamped manual [v].
 */
    public fun setValue(v: Float) {
        ensureUsable()
        require(!v.isNaN()) { "PixelAnimationController value must not be NaN" }
        ticker.stop()
        repeatMode = null
        val clampedValue = v.coerceIn(0f, 1f)
        segmentStartValue = clampedValue
        segmentTargetValue = clampedValue
        segmentDurationNanos = 0L
        updateState(
            value = clampedValue,
            status = terminalStatusForStableValue(clampedValue),
        )
    }

    /** 从 `PixelAnimationController` 释放 `dispose` 内容并收敛相关所有权。
 *
 * Stops and disposes the owned ticker exactly once.
 */
    public fun dispose() {
        if (disposed) return
        ticker.stop()
        ticker.dispose()
        disposed = true
        repeatMode = null
    }

    /** Starts one forward or reverse segment from a validated current/explicit value. */
    private fun startFiniteMotion(
        from: Float?,
        target: Float,
        direction: PixelAnimationStatus,
        terminalStatus: PixelAnimationStatus,
    ) {
        ensureUsable()
        ticker.stop()
        repeatMode = null
        val oldValue = currentValue
        val oldStatus = currentStatus
        val startValue = (from ?: currentValue).coerceIn(0f, 1f)
        currentValue = startValue
        segmentStartValue = startValue
        segmentTargetValue = target
        segmentDurationNanos = scaledDurationNanos(
            distance = abs(target.toDouble() - startValue.toDouble()),
        )
        if (segmentDurationNanos == 0L || startValue == target) {
            currentValue = target
            currentStatus = terminalStatus
        } else {
            currentStatus = direction
            ticker.start()
        }
        notifyIfChanged(oldValue, oldStatus)
    }

    /** Routes one active-time tick to finite or repeating motion. */
    private fun onTick(elapsedNanos: Long) {
        val repeating = repeatMode
        if (repeating == null) {
            tickFiniteMotion(elapsedNanos)
        } else {
            tickRepeatingMotion(elapsedNanos, reverse = repeating)
        }
    }

    /** Advances one finite segment and settles exactly at its endpoint after any overshoot. */
    private fun tickFiniteMotion(elapsedNanos: Long) {
        val safeElapsed = elapsedNanos.coerceAtLeast(0L)
        val durationNanos = segmentDurationNanos
        if (durationNanos <= 0L) return
        val fullDurationNanos = fullDurationNanos()
        val elapsedDistance =
            (safeElapsed.toDouble() / fullDurationNanos.toDouble()).toFloat()
        // Float-space crossing keeps value/status aligned at decimal starts such as 0.8f.
        val projectedValue = if (segmentTargetValue >= segmentStartValue) {
            segmentStartValue + elapsedDistance
        } else {
            segmentStartValue - elapsedDistance
        }
        val reachedTargetInFloatSpace = if (segmentTargetValue >= segmentStartValue) {
            projectedValue >= segmentTargetValue
        } else {
            projectedValue <= segmentTargetValue
        }
        val completed = safeElapsed >= durationNanos || reachedTargetInFloatSpace
        val progress = if (completed) {
            1.0
        } else {
            safeElapsed.toDouble() / durationNanos.toDouble()
        }
        val nextValue = if (completed) {
            segmentTargetValue
        } else {
            (segmentStartValue + (segmentTargetValue - segmentStartValue) * progress).toFloat()
                .coerceIn(0f, 1f)
        }
        if (completed) ticker.stop()
        val nextStatus = if (completed) {
            if (segmentTargetValue >= 1f) {
                PixelAnimationStatus.Completed
            } else {
                PixelAnimationStatus.Dismissed
            }
        } else {
            currentStatus
        }
        updateState(nextValue, nextStatus, notifyEvenIfUnchanged = true)
    }

    /** Advances a forward loop or overflow-safe ping-pong loop without restarting its ticker. */
    private fun tickRepeatingMotion(elapsedNanos: Long, reverse: Boolean) {
        val durationNanos = fullDurationNanos()
        if (durationNanos <= 0L) return
        val safeElapsed = elapsedNanos.coerceAtLeast(0L)
        val completedLegs = safeElapsed / durationNanos
        val legElapsed = safeElapsed % durationNanos
        if (!reverse) {
            val nextValue = if (safeElapsed > 0L && legElapsed == 0L) {
                0f
            } else {
                (legElapsed.toDouble() / durationNanos.toDouble()).toFloat().coerceIn(0f, 1f)
            }
            updateState(
                value = nextValue,
                status = PixelAnimationStatus.Forward,
                notifyEvenIfUnchanged = true,
            )
            return
        }
        val isReverseLeg = completedLegs % 2L == 1L
        val legProgress = legElapsed.toDouble() / durationNanos.toDouble()
        val nextValue = when {
            legElapsed == 0L && safeElapsed > 0L && isReverseLeg -> 1f
            legElapsed == 0L && safeElapsed > 0L -> 0f
            isReverseLeg -> (1.0 - legProgress).toFloat()
            else -> legProgress.toFloat()
        }.coerceIn(0f, 1f)
        updateState(
            value = nextValue,
            status = if (isReverseLeg) {
                PixelAnimationStatus.Reverse
            } else {
                PixelAnimationStatus.Forward
            },
            notifyEvenIfUnchanged = true,
        )
    }

    /** Updates value/status and notifies listeners once when requested or observably changed. */
    private fun updateState(
        value: Float,
        status: PixelAnimationStatus,
        notifyEvenIfUnchanged: Boolean = false,
    ) {
        val oldValue = currentValue
        val oldStatus = currentStatus
        currentValue = value
        currentStatus = status
        if (notifyEvenIfUnchanged || oldValue != value || oldStatus != status) {
            notifyListeners()
        }
    }

    /** Notifies once after a command changed its immediate value or status. */
    private fun notifyIfChanged(oldValue: Float, oldStatus: PixelAnimationStatus) {
        if (oldValue != currentValue || oldStatus != currentStatus) {
            notifyListeners()
        }
    }

    /** Returns full-range active duration, treating sub-nanosecond/zero values as synchronous. */
    private fun fullDurationNanos(): Long = duration.inWholeNanoseconds.coerceAtLeast(0L)

    /** Scales full duration by normalized [distance] without overflowing large durations. */
    private fun scaledDurationNanos(distance: Double): Long {
        val fullDuration = fullDurationNanos()
        if (fullDuration == 0L || distance <= 0.0) return 0L
        if (distance >= 1.0) return fullDuration
        return (fullDuration.toDouble() * distance)
            .roundToLong()
            .coerceAtLeast(1L)
    }

    /** Validates one optional explicit start before clamping it into the normalized range. */
    private fun validatedOptionalValue(value: Float?, operation: String): Float? {
        if (value == null) return null
        require(!value.isNaN()) { "PixelAnimationController $operation must not be NaN" }
        return value.coerceIn(0f, 1f)
    }

    /** Rejects mutations after controller or externally owned provider disposal. */
    private fun ensureUsable() {
        check(!disposed && !ticker.isDisposed) { "PixelAnimationController is already disposed" }
    }

    /** Maps an inactive normalized value to its stable boundary status. */
    private companion object {
        /** Returns Completed only at the upper bound; every other stable value is Dismissed. */
        fun terminalStatusForStableValue(value: Float): PixelAnimationStatus {
            return if (value >= 1f) {
                PixelAnimationStatus.Completed
            } else {
                PixelAnimationStatus.Dismissed
            }
        }
    }
}
