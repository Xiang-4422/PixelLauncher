package com.purride.pixelui.host

import android.os.Debug
import android.os.Trace
import com.purride.pixelui.PixelFrameDropReason
import com.purride.pixelui.PixelFrameTimings
import com.purride.pixelui.PixelFrameWorkload
import com.purride.pixelui.PixelHostFrameDiagnostics
import com.purride.pixelui.internal.PixelFramePhaseSink
import kotlin.math.roundToLong

/** Process-wide ART allocation and garbage-collection counters at one frame boundary. */
internal data class RuntimeFrameMetricsSample(
    /** Cumulative bytes allocated by the process, or null when the ART key is unavailable. */
    val allocatedBytes: Long?,
    /** Cumulative process garbage-collection count, or null when unavailable. */
    val garbageCollectionCount: Long?,
) {
    /** Shared sample representing a runtime that exposes neither requested counter. */
    companion object {
        /** Allocation and GC counters are both unavailable. */
        val Unavailable: RuntimeFrameMetricsSample = RuntimeFrameMetricsSample(
            allocatedBytes = null,
            garbageCollectionCount = null,
        )
    }
}

/** Supplies process runtime counters only while full-frame diagnostics are active. */
internal fun interface RuntimeFrameMetricsSampler {
    /** Captures cumulative counters at the current frame boundary. */
    fun sample(): RuntimeFrameMetricsSample
}

/** Android ART-backed runtime sampler used by production Hosts on API 24 and newer. */
internal object AndroidRuntimeFrameMetricsSampler : RuntimeFrameMetricsSampler {
    /** Captures supported ART counters without failing a frame when a key is unavailable. */
    override fun sample(): RuntimeFrameMetricsSample {
        return RuntimeFrameMetricsSample(
            allocatedBytes = readRuntimeStat(ALLOCATED_BYTES_KEY),
            garbageCollectionCount = readRuntimeStat(GARBAGE_COLLECTION_COUNT_KEY),
        )
    }

    /** Reads one cumulative ART statistic while treating vendor omissions as unavailable data. */
    private fun readRuntimeStat(key: String): Long? {
        return try {
            Debug.getRuntimeStat(key)?.toLongOrNull()
        } catch (_: RuntimeException) {
            null
        } catch (_: LinkageError) {
            null
        }
    }

    /** ART key for cumulative process allocation bytes. */
    private const val ALLOCATED_BYTES_KEY: String = "art.gc.bytes-allocated"

    /** ART key for cumulative process garbage collections. */
    private const val GARBAGE_COLLECTION_COUNT_KEY: String = "art.gc.gc-count"
}

/** 把 opt-in Host 帧阶段投递到平台 trace；实现不得自行分配阶段名称。 */
internal interface PixelFrameTraceSink {
    /** 开始一个与调用线程绑定的同步 trace 区间。 */
    fun beginSection(name: String)

    /** 结束调用线程最近开始且尚未关闭的同步 trace 区间。 */
    fun endSection()
}

/** JVM 测试与未接入平台 trace 的 recorder 使用的零开销空实现。 */
internal object NoOpPixelFrameTraceSink : PixelFrameTraceSink {
    /** 空实现不会创建 trace 区间。 */
    override fun beginSection(name: String) = Unit

    /** 空实现无需维护 trace 栈。 */
    override fun endSection() = Unit
}

/** 使用 Android 同步 trace API 输出 Host 帧与阶段 slice 的生产实现。 */
internal object AndroidPixelFrameTraceSink : PixelFrameTraceSink {
    /** 把固定短名称写入当前线程的 atrace/Perfetto 轨道。 */
    override fun beginSection(name: String) {
        Trace.beginSection(name)
    }

    /** 关闭当前线程最近开始的 Host trace 区间。 */
    override fun endSection() {
        Trace.endSection()
    }
}

/**
 * Host-owned accumulator for one complete Android frame.
 *
 * The class retains primitive counters between calls and creates public value objects only from
 * [finishFrame]. A Host must not call [beginFrame] unless diagnostics are explicitly enabled.
 */
internal class PixelFrameDiagnosticsRecorder(
    /** Monotonic clock shared with legacy Host frame timing and replaceable by deterministic tests. */
    private val clock: MonotonicClock = AndroidUptimeClock,
    /** Process runtime sampler invoked exactly twice per active frame. */
    private val runtimeMetricsSampler: RuntimeFrameMetricsSampler = AndroidRuntimeFrameMetricsSampler,
    /** 仅在显式诊断帧中输出同步 trace slice 的可替换平台出口。 */
    private val frameTraceSink: PixelFrameTraceSink = NoOpPixelFrameTraceSink,
) : PixelFramePhaseSink {
    /** Whether a frame currently owns the mutable phase accumulators. */
    private var active: Boolean = false

    /** 当前诊断帧是否已经打开最外层同步 trace 区间。 */
    private var frameTraceActive: Boolean = false

    /** One-based number assigned to the next completed diagnostics snapshot. */
    private var frameNumber: Long = 0L

    /** Start timestamp of the current frame. */
    private var frameStartNanos: Long = UNSET_NANOS

    /** Start timestamp of the previously observed frame, retained across frames. */
    private var previousFrameStartNanos: Long = UNSET_NANOS

    /** Display deadline budget used by the current frame. */
    private var frameBudgetNanos: Long = DEFAULT_FRAME_BUDGET_NANOS

    /** Interval from the previous observed frame start to the current start. */
    private var frameIntervalNanos: Long = 0L

    /** Start timestamp for the currently open build segment. */
    private var buildStartNanos: Long = UNSET_NANOS

    /** Accumulated retained build duration for the current frame. */
    private var buildNanos: Long = 0L

    /** Start timestamp for the currently open layout segment. */
    private var layoutStartNanos: Long = UNSET_NANOS

    /** Accumulated RenderObject layout duration for the current frame. */
    private var layoutNanos: Long = 0L

    /** Start timestamp for the currently open logical paint segment. */
    private var paintStartNanos: Long = UNSET_NANOS

    /** Accumulated logical PixelBuffer paint duration for the current frame. */
    private var paintNanos: Long = 0L

    /** Start timestamp for the currently open PixelBuffer submit segment. */
    private var bufferSubmitStartNanos: Long = UNSET_NANOS

    /** Accumulated PixelBuffer-to-Canvas submit duration for the current frame. */
    private var bufferSubmitNanos: Long = 0L

    /** Start timestamp for the currently open exclusive Android drawing segment. */
    private var androidDrawStartNanos: Long = UNSET_NANOS

    /** Accumulated Android drawing duration outside buffer submission. */
    private var androidDrawNanos: Long = 0L

    /** Elements rebuilt during the current retained build pass. */
    private var dirtyElementCount: Int = 0

    /** RenderObjects traversed by a dirty whole-tree layout or paint pass. */
    private var dirtyRenderNodeCount: Int = 0

    /** Logical pixels repainted by the retained engine during this frame. */
    private var paintedPixelCount: Long = 0L

    /** Logical pixels submitted from the completed buffer to Android Canvas. */
    private var submittedPixelCount: Long = 0L

    /** PixelBuffer pool hits accumulated during this frame. */
    private var bufferCacheHitCount: Long = 0L

    /** PixelBuffer pool misses accumulated during this frame. */
    private var bufferCacheMissCount: Long = 0L

    /** Whether any render request reused the complete retained pipeline result. */
    private var renderCacheHit: Boolean = false

    /** Process runtime counters captured immediately after frame timing begins. */
    private var runtimeMetricsAtStart: RuntimeFrameMetricsSample = RuntimeFrameMetricsSample.Unavailable

    /** Most recently completed immutable snapshot for on-demand Inspector access. */
    var latestDiagnostics: PixelHostFrameDiagnostics? = null
        private set

    /** Resets per-frame counters and starts timing an explicitly enabled diagnostics frame. */
    fun beginFrame(frameBudgetNanos: Long) {
        if (active) finishFrame()
        frameTraceSink.beginSection(FRAME_TRACE_SECTION)
        frameTraceActive = true
        /** Current start timestamp used for both total time and inter-frame delivery interval. */
        val now = clock.nanoTime()
        active = true
        frameNumber = saturatedIncrement(frameNumber)
        frameStartNanos = now
        frameIntervalNanos = if (previousFrameStartNanos == UNSET_NANOS) {
            0L
        } else {
            nonNegativeDuration(previousFrameStartNanos, now)
        }
        previousFrameStartNanos = now
        this.frameBudgetNanos = frameBudgetNanos.coerceAtLeast(1L)
        resetFrameAccumulators()
        runtimeMetricsAtStart = runtimeMetricsSampler.sample()
    }

    /** Starts the retained Element build phase when a diagnostics frame is active. */
    override fun beginBuild() {
        buildStartNanos = beginPhase(buildStartNanos, BUILD_TRACE_SECTION)
    }

    /** Ends and accumulates the retained Element build phase. */
    override fun endBuild() {
        if (!active || buildStartNanos == UNSET_NANOS) return
        /** Completed build duration sampled before the phase marker is reset. */
        val duration = nonNegativeDuration(buildStartNanos, clock.nanoTime())
        buildStartNanos = UNSET_NANOS
        buildNanos = saturatedAdd(buildNanos, duration)
        frameTraceSink.endSection()
    }

    /** Adds rebuilt Elements while preventing integer overflow in long-running recovery paths. */
    override fun recordBuildWork(dirtyElementCount: Int) {
        if (!active) return
        this.dirtyElementCount = saturatedAdd(this.dirtyElementCount, dirtyElementCount.coerceAtLeast(0))
    }

    /** Starts the RenderObject layout phase when diagnostics are active. */
    override fun beginLayout() {
        layoutStartNanos = beginPhase(layoutStartNanos, LAYOUT_TRACE_SECTION)
    }

    /** Ends and accumulates the RenderObject layout phase. */
    override fun endLayout() {
        if (!active || layoutStartNanos == UNSET_NANOS) return
        /** Completed layout duration sampled before the phase marker is reset. */
        val duration = nonNegativeDuration(layoutStartNanos, clock.nanoTime())
        layoutStartNanos = UNSET_NANOS
        layoutNanos = saturatedAdd(layoutNanos, duration)
        frameTraceSink.endSection()
    }

    /** Starts logical PixelBuffer painting and target export. */
    override fun beginPaint() {
        paintStartNanos = beginPhase(paintStartNanos, PAINT_TRACE_SECTION)
    }

    /** Ends and accumulates logical PixelBuffer painting and target export. */
    override fun endPaint() {
        if (!active || paintStartNanos == UNSET_NANOS) return
        /** Completed paint duration sampled before the phase marker is reset. */
        val duration = nonNegativeDuration(paintStartNanos, clock.nanoTime())
        paintStartNanos = UNSET_NANOS
        paintNanos = saturatedAdd(paintNanos, duration)
        frameTraceSink.endSection()
    }

    /** Adds retained render work emitted by the completed pipeline request. */
    override fun recordPipelineWork(
        dirtyRenderNodeCount: Int,
        paintedPixelCount: Long,
        renderCacheHit: Boolean,
    ) {
        if (!active) return
        this.dirtyRenderNodeCount = saturatedAdd(
            this.dirtyRenderNodeCount,
            dirtyRenderNodeCount.coerceAtLeast(0),
        )
        this.paintedPixelCount = saturatedAdd(this.paintedPixelCount, paintedPixelCount.coerceAtLeast(0L))
        this.renderCacheHit = this.renderCacheHit || renderCacheHit
    }

    /** Adds PixelBuffer cache activity measured around the retained render request. */
    override fun recordBufferPoolActivity(hitCount: Long, missCount: Long) {
        if (!active) return
        bufferCacheHitCount = saturatedAdd(bufferCacheHitCount, hitCount.coerceAtLeast(0L))
        bufferCacheMissCount = saturatedAdd(bufferCacheMissCount, missCount.coerceAtLeast(0L))
    }

    /** Starts one exclusive Android View drawing segment outside buffer submission. */
    fun beginAndroidDraw() {
        androidDrawStartNanos = beginPhase(androidDrawStartNanos, ANDROID_DRAW_TRACE_SECTION)
    }

    /** Ends and accumulates one exclusive Android View drawing segment. */
    fun endAndroidDraw() {
        if (!active || androidDrawStartNanos == UNSET_NANOS) return
        /** Completed Android draw duration sampled before the phase marker is reset. */
        val duration = nonNegativeDuration(androidDrawStartNanos, clock.nanoTime())
        androidDrawStartNanos = UNSET_NANOS
        androidDrawNanos = saturatedAdd(androidDrawNanos, duration)
        frameTraceSink.endSection()
    }

    /** Starts PixelBuffer conversion and submission to the Android Canvas. */
    fun beginBufferSubmit() {
        bufferSubmitStartNanos = beginPhase(bufferSubmitStartNanos, BUFFER_SUBMIT_TRACE_SECTION)
    }

    /** Ends and accumulates PixelBuffer conversion and Android Canvas submission. */
    fun endBufferSubmit() {
        if (!active || bufferSubmitStartNanos == UNSET_NANOS) return
        /** Completed submit duration sampled before the phase marker is reset. */
        val duration = nonNegativeDuration(bufferSubmitStartNanos, clock.nanoTime())
        bufferSubmitStartNanos = UNSET_NANOS
        bufferSubmitNanos = saturatedAdd(bufferSubmitNanos, duration)
        frameTraceSink.endSection()
    }

    /** Adds logical source pixels submitted by one completed [PixelHostView] draw. */
    fun recordBufferSubmit(submittedPixelCount: Long) {
        if (!active) return
        this.submittedPixelCount = saturatedAdd(
            this.submittedPixelCount,
            submittedPixelCount.coerceAtLeast(0L),
        )
    }

    /**
     * Closes any active phase and returns the immutable snapshot for the current frame.
     *
     * Calling this method without a matching [beginFrame] returns null and does not sample ART.
     */
    fun finishFrame(): PixelHostFrameDiagnostics? {
        if (!active) return null
        closeOpenPhases()
        /** Process counters after all named frame work and before immutable snapshot allocation. */
        val runtimeMetricsAtEnd = runtimeMetricsSampler.sample()
        /** Terminal timestamp includes runtime sampling cost in total and unattributed time. */
        val frameEndNanos = clock.nanoTime()
        closeFrameTrace()
        /** Complete non-negative time owned by this onDraw observation. */
        val totalFrameNanos = nonNegativeDuration(frameStartNanos, frameEndNanos)
        /** Sum of every exclusive named stage, saturated against counter overflow. */
        var attributedNanos = buildNanos
        attributedNanos = saturatedAdd(attributedNanos, layoutNanos)
        attributedNanos = saturatedAdd(attributedNanos, paintNanos)
        attributedNanos = saturatedAdd(attributedNanos, bufferSubmitNanos)
        attributedNanos = saturatedAdd(attributedNanos, androidDrawNanos)
        /** Remaining frame work not enclosed by one explicit stage boundary. */
        val unattributedNanos = (totalFrameNanos - attributedNanos).coerceAtLeast(0L)
        /** Process allocation delta, absent if either boundary counter was unavailable. */
        val allocatedBytes = counterDelta(
            start = runtimeMetricsAtStart.allocatedBytes,
            end = runtimeMetricsAtEnd.allocatedBytes,
        )
        /** Process GC delta, absent if either boundary counter was unavailable. */
        val garbageCollectionCount = counterDelta(
            start = runtimeMetricsAtStart.garbageCollectionCount,
            end = runtimeMetricsAtEnd.garbageCollectionCount,
        )
        /** Greatest number of display deadlines crossed by work or frame delivery. */
        val missedVsyncCount = maxOf(
            crossedDeadlineCount(totalFrameNanos, frameBudgetNanos),
            crossedDeadlineCount(frameIntervalNanos, frameBudgetNanos),
        )
        /** Primary reason selected only after a real display deadline was crossed. */
        val dropReason = classifyDropReason(
            totalFrameNanos = totalFrameNanos,
            unattributedNanos = unattributedNanos,
            garbageCollectionCount = garbageCollectionCount,
            missedVsyncCount = missedVsyncCount,
        )
        /** Stable public snapshot allocated once for the enabled frame. */
        val diagnostics = PixelHostFrameDiagnostics(
            frameNumber = frameNumber,
            frameIntervalNanos = frameIntervalNanos,
            frameBudgetNanos = frameBudgetNanos,
            timings = PixelFrameTimings(
                buildNanos = buildNanos,
                layoutNanos = layoutNanos,
                paintNanos = paintNanos,
                bufferSubmitNanos = bufferSubmitNanos,
                androidDrawNanos = androidDrawNanos,
                totalFrameNanos = totalFrameNanos,
                unattributedNanos = unattributedNanos,
            ),
            workload = PixelFrameWorkload(
                dirtyElementCount = dirtyElementCount,
                dirtyRenderNodeCount = dirtyRenderNodeCount,
                paintedPixelCount = paintedPixelCount,
                submittedPixelCount = submittedPixelCount,
                allocatedBytes = allocatedBytes,
                garbageCollectionCount = garbageCollectionCount,
                bufferCacheHitCount = bufferCacheHitCount,
                bufferCacheMissCount = bufferCacheMissCount,
                renderCacheHit = renderCacheHit,
            ),
            dropReason = dropReason,
            missedVsyncCount = missedVsyncCount,
        )
        active = false
        frameStartNanos = UNSET_NANOS
        latestDiagnostics = diagnostics
        return diagnostics
    }

    /** Resets every counter that must not leak from the previous active frame. */
    private fun resetFrameAccumulators() {
        buildStartNanos = UNSET_NANOS
        buildNanos = 0L
        layoutStartNanos = UNSET_NANOS
        layoutNanos = 0L
        paintStartNanos = UNSET_NANOS
        paintNanos = 0L
        bufferSubmitStartNanos = UNSET_NANOS
        bufferSubmitNanos = 0L
        androidDrawStartNanos = UNSET_NANOS
        androidDrawNanos = 0L
        dirtyElementCount = 0
        dirtyRenderNodeCount = 0
        paintedPixelCount = 0L
        submittedPixelCount = 0L
        bufferCacheHitCount = 0L
        bufferCacheMissCount = 0L
        renderCacheHit = false
    }

    /** Closes whichever phase was left open by an exceptional frame path. */
    private fun closeOpenPhases() {
        if (buildStartNanos != UNSET_NANOS) endBuild()
        if (layoutStartNanos != UNSET_NANOS) endLayout()
        if (paintStartNanos != UNSET_NANOS) endPaint()
        if (bufferSubmitStartNanos != UNSET_NANOS) endBufferSubmit()
        if (androidDrawStartNanos != UNSET_NANOS) endAndroidDraw()
    }

    /** 开始一个计时与 trace 共用边界的阶段，重复 begin 或未激活调用保持幂等。 */
    private fun beginPhase(currentStartNanos: Long, traceSectionName: String): Long {
        if (!active || currentStartNanos != UNSET_NANOS) return currentStartNanos
        frameTraceSink.beginSection(traceSectionName)
        return clock.nanoTime()
    }

    /** 幂等关闭最外层帧 trace，避免异常帧把区间泄漏到下一次 onDraw。 */
    private fun closeFrameTrace() {
        if (!frameTraceActive) return
        frameTraceActive = false
        frameTraceSink.endSection()
    }

    /** Selects the dominant exclusive phase or a delivery/runtime reason for a missed deadline. */
    private fun classifyDropReason(
        totalFrameNanos: Long,
        unattributedNanos: Long,
        garbageCollectionCount: Long?,
        missedVsyncCount: Int,
    ): PixelFrameDropReason? {
        if (missedVsyncCount <= 0) return null
        if (totalFrameNanos <= frameBudgetNanos) return PixelFrameDropReason.FRAME_SCHEDULER
        /** Dominant named phase duration used for deterministic primary attribution. */
        var dominantDuration = buildNanos
        /** Dominant named phase reason paired with [dominantDuration]. */
        var dominantReason = PixelFrameDropReason.BUILD
        if (layoutNanos > dominantDuration) {
            dominantDuration = layoutNanos
            dominantReason = PixelFrameDropReason.LAYOUT
        }
        if (paintNanos > dominantDuration) {
            dominantDuration = paintNanos
            dominantReason = PixelFrameDropReason.PAINT
        }
        if (bufferSubmitNanos > dominantDuration) {
            dominantDuration = bufferSubmitNanos
            dominantReason = PixelFrameDropReason.BUFFER_SUBMIT
        }
        if (androidDrawNanos > dominantDuration) {
            dominantDuration = androidDrawNanos
            dominantReason = PixelFrameDropReason.ANDROID_DRAW
        }
        if (unattributedNanos > dominantDuration) {
            return if ((garbageCollectionCount ?: 0L) > 0L) {
                PixelFrameDropReason.GARBAGE_COLLECTION
            } else {
                PixelFrameDropReason.UNATTRIBUTED
            }
        }
        return dominantReason
    }

    /** Returns a counter delta only when both monotonic cumulative values are available. */
    private fun counterDelta(start: Long?, end: Long?): Long? {
        if (start == null || end == null) return null
        return (end - start).coerceAtLeast(0L)
    }

    /** Counts display budgets crossed by one non-negative work or interval duration. */
    private fun crossedDeadlineCount(durationNanos: Long, budgetNanos: Long): Int {
        if (durationNanos <= budgetNanos) return 0
        /** Long count before conversion prevents an extreme pause from wrapping an Int. */
        val crossed = (durationNanos - 1L) / budgetNanos
        return crossed.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** Returns a non-negative monotonic duration while tolerating a defensive clock reset. */
    private fun nonNegativeDuration(startNanos: Long, endNanos: Long): Long {
        if (startNanos == UNSET_NANOS || endNanos <= startNanos) return 0L
        return endNanos - startNanos
    }

    /** Saturating non-negative Long addition used by cumulative frame counters. */
    private fun saturatedAdd(left: Long, right: Long): Long {
        if (right <= 0L) return left
        return if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
    }

    /** Saturating non-negative Int addition used by per-frame dirty-node counters. */
    private fun saturatedAdd(left: Int, right: Int): Int {
        if (right <= 0) return left
        return if (left > Int.MAX_VALUE - right) Int.MAX_VALUE else left + right
    }

    /** Increments a cumulative frame number without wrapping it negative. */
    private fun saturatedIncrement(value: Long): Long {
        return if (value == Long.MAX_VALUE) Long.MAX_VALUE else value + 1L
    }

    /** Recorder constants shared by production refresh-rate conversion and test sentinels. */
    companion object {
        /** 创建接入 Android trace 的生产 Host recorder；JVM 测试默认继续使用空出口。 */
        fun forAndroidHost(): PixelFrameDiagnosticsRecorder {
            return PixelFrameDiagnosticsRecorder(frameTraceSink = AndroidPixelFrameTraceSink)
        }

        /** Nanosecond marker distinguishable from a valid clock value of zero. */
        private const val UNSET_NANOS: Long = Long.MIN_VALUE

        /** Number of nanoseconds represented by one second. */
        private const val NANOS_PER_SECOND: Double = 1_000_000_000.0

        /** Conservative budget used only when no valid display refresh rate is available. */
        private const val DEFAULT_FRAME_BUDGET_NANOS: Long = 16_666_667L

        /** 包围一次完整 opt-in Host onDraw 的 Perfetto slice 名称。 */
        private const val FRAME_TRACE_SECTION: String = "pixel.frame"

        /** retained Element 构建阶段的 Perfetto slice 名称。 */
        private const val BUILD_TRACE_SECTION: String = "pixel.build"

        /** retained RenderObject 布局阶段的 Perfetto slice 名称。 */
        private const val LAYOUT_TRACE_SECTION: String = "pixel.layout"

        /** 逻辑 PixelBuffer 绘制阶段的 Perfetto slice 名称。 */
        private const val PAINT_TRACE_SECTION: String = "pixel.paint"

        /** PixelBuffer 到 Android Canvas 提交阶段的 Perfetto slice 名称。 */
        private const val BUFFER_SUBMIT_TRACE_SECTION: String = "pixel.buffer_submit"

        /** buffer 提交之外 Android View 绘制阶段的 Perfetto slice 名称。 */
        private const val ANDROID_DRAW_TRACE_SECTION: String = "pixel.android_draw"

        /** Converts a validated refresh rate into a positive per-frame nanosecond budget. */
        fun frameBudgetNanos(refreshRateHz: Float?): Long {
            /** Finite positive Host rate, or the documented 60 Hz fallback. */
            val effectiveRefreshRate = refreshRateHz
                ?.takeIf { rate -> rate.isFinite() && rate > 0f }
                ?.toDouble()
                ?: 60.0
            return (NANOS_PER_SECOND / effectiveRefreshRate).roundToLong().coerceAtLeast(1L)
        }
    }
}
