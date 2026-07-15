package com.purride.pixelui

/**
 * 定义 `PixelFrameTimings` 在 `PixelHostFrameDiagnostics` 中承担的数据与行为边界。
 *
 * Exclusive timing breakdown for one Android Host frame.
 *
 * [buildNanos], [layoutNanos], [paintNanos], [bufferSubmitNanos], and
 * [androidDrawNanos] do not overlap. Their sum can be smaller than [totalFrameNanos] because
 * scheduler stepping, callback dispatch, diagnostics sampling, and framework overhead are
 * reported as [unattributedNanos]. All values use the monotonic Android clock.
 *
 * @property buildNanos Time spent reconciling and rebuilding the retained Element tree.
 * @property layoutNanos Time spent laying out the retained RenderObject tree.
 * @property paintNanos Time spent painting the logical PixelBuffer and exporting frame targets.
 * @property bufferSubmitNanos Time spent submitting PixelBuffer content to the Android Canvas.
 * @property androidDrawNanos Host drawing time outside buffer submission, including View drawing,
 * background clear, and accessibility publication.
 * @property totalFrameNanos Time from [PixelHostView.onDraw] entry through its terminal cleanup.
 * @property unattributedNanos Non-overlapping frame time not owned by one named phase.
 */
public data class PixelFrameTimings(
    /** 公开 `PixelHostFrameDiagnostics` 的 `buildNanos` 配置或运行值。
 *
 * Time spent reconciling and rebuilding retained Elements.
 */
    public val buildNanos: Long,
    /** 公开 `PixelHostFrameDiagnostics` 的 `layoutNanos` 配置或运行值。
 *
 * Time spent laying out the retained RenderObject tree.
 */
    public val layoutNanos: Long,
    /** 公开 `PixelHostFrameDiagnostics` 的 `paintNanos` 配置或运行值。
 *
 * Time spent painting the logical buffer and exporting targets.
 */
    public val paintNanos: Long,
    /** 公开 `PixelHostFrameDiagnostics` 的 `bufferSubmitNanos` 配置或运行值。
 *
 * Time spent converting and submitting the PixelBuffer to Android Canvas.
 */
    public val bufferSubmitNanos: Long,
    /** 公开 `PixelHostFrameDiagnostics` 的 `androidDrawNanos` 配置或运行值。
 *
 * Exclusive Android View drawing time outside buffer submission.
 */
    public val androidDrawNanos: Long,
    /** 公开 `PixelHostFrameDiagnostics` 的 `totalFrameNanos` 配置或运行值。
 *
 * Complete observed frame duration.
 */
    public val totalFrameNanos: Long,
    /** 公开 `PixelHostFrameDiagnostics` 的 `unattributedNanos` 配置或运行值。
 *
 * Complete frame duration not enclosed by a named phase.
 */
    public val unattributedNanos: Long,
) {
    /** Rejects impossible negative or overlapping phase snapshots at the stable API boundary. */
    init {
        require(buildNanos >= 0L) { "buildNanos must be non-negative" }
        require(layoutNanos >= 0L) { "layoutNanos must be non-negative" }
        require(paintNanos >= 0L) { "paintNanos must be non-negative" }
        require(bufferSubmitNanos >= 0L) { "bufferSubmitNanos must be non-negative" }
        require(androidDrawNanos >= 0L) { "androidDrawNanos must be non-negative" }
        require(totalFrameNanos >= 0L) { "totalFrameNanos must be non-negative" }
        require(unattributedNanos >= 0L) { "unattributedNanos must be non-negative" }
        /** Saturated sum avoids validation overflow for externally constructed diagnostics. */
        var attributedNanos = buildNanos
        attributedNanos = saturatedAdd(attributedNanos, layoutNanos)
        attributedNanos = saturatedAdd(attributedNanos, paintNanos)
        attributedNanos = saturatedAdd(attributedNanos, bufferSubmitNanos)
        attributedNanos = saturatedAdd(attributedNanos, androidDrawNanos)
        require(attributedNanos <= totalFrameNanos) {
            "exclusive frame phases cannot exceed totalFrameNanos"
        }
        require(totalFrameNanos - attributedNanos == unattributedNanos) {
            "unattributedNanos must equal totalFrameNanos minus exclusive phases"
        }
    }

    /** Adds two validated non-negative durations without wrapping their validation sum. */
    private fun saturatedAdd(left: Long, right: Long): Long {
        return if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
    }
}

/**
 * 定义 `PixelFrameWorkload` 在 `PixelHostFrameDiagnostics` 中承担的数据与行为边界。
 *
 * Work and runtime-pressure counters captured for one observed Host frame.
 *
 * Allocation and GC counters are process-wide ART deltas sampled at frame boundaries. They are
 * nullable because a runtime may not expose the corresponding `Debug.getRuntimeStat` key. They
 * must be used for trends and correlation, not as per-thread heap accounting.
 *
 * @property dirtyElementCount Retained Elements rebuilt during the frame.
 * @property dirtyRenderNodeCount RenderObjects participating in a dirty whole-tree layout/paint.
 * @property paintedPixelCount Logical PixelBuffer pixels repainted by the engine.
 * @property submittedPixelCount Logical PixelBuffer pixels submitted to the Android Canvas.
 * @property allocatedBytes Process-wide allocated-byte delta while this frame was observed.
 * @property garbageCollectionCount Process-wide GC-count delta while this frame was observed.
 * @property bufferCacheHitCount PixelBuffer pool acquisitions served by a cached buffer.
 * @property bufferCacheMissCount PixelBuffer pool acquisitions that allocated a new buffer.
 * @property renderCacheHit Whether the retained pipeline reused its complete previous result.
 */
public data class PixelFrameWorkload(
    /** 公开 `PixelHostFrameDiagnostics` 的 `dirtyElementCount` 配置或运行值。
 *
 * Retained Elements rebuilt by the frame.
 */
    public val dirtyElementCount: Int,
    /** 公开 `PixelHostFrameDiagnostics` 的 `dirtyRenderNodeCount` 配置或运行值。
 *
 * RenderObjects participating in an owner-wide dirty pass.
 */
    public val dirtyRenderNodeCount: Int,
    /** 公开 `PixelHostFrameDiagnostics` 的 `paintedPixelCount` 配置或运行值。
 *
 * Logical PixelBuffer pixels repainted by the engine.
 */
    public val paintedPixelCount: Long,
    /** 公开 `PixelHostFrameDiagnostics` 的 `submittedPixelCount` 配置或运行值。
 *
 * Logical PixelBuffer pixels submitted to Android Canvas.
 */
    public val submittedPixelCount: Long,
    /** 公开 `PixelHostFrameDiagnostics` 的 `allocatedBytes` 配置或运行值。
 *
 * Process-wide allocation-byte delta, or null when unavailable.
 */
    public val allocatedBytes: Long?,
    /** 公开 `PixelHostFrameDiagnostics` 的 `garbageCollectionCount` 配置或运行值。
 *
 * Process-wide garbage-collection delta, or null when unavailable.
 */
    public val garbageCollectionCount: Long?,
    /** 公开 `PixelHostFrameDiagnostics` 的 `bufferCacheHitCount` 配置或运行值。
 *
 * PixelBuffer acquisitions served by the pool.
 */
    public val bufferCacheHitCount: Long,
    /** 公开 `PixelHostFrameDiagnostics` 的 `bufferCacheMissCount` 配置或运行值。
 *
 * PixelBuffer acquisitions requiring a new allocation.
 */
    public val bufferCacheMissCount: Long,
    /** 公开 `PixelHostFrameDiagnostics` 的 `renderCacheHit` 配置或运行值。
 *
 * Whether the complete retained render result was reused.
 */
    public val renderCacheHit: Boolean,
) {
    /** Rejects impossible negative workload and cumulative counter deltas. */
    init {
        require(dirtyElementCount >= 0) { "dirtyElementCount must be non-negative" }
        require(dirtyRenderNodeCount >= 0) { "dirtyRenderNodeCount must be non-negative" }
        require(paintedPixelCount >= 0L) { "paintedPixelCount must be non-negative" }
        require(submittedPixelCount >= 0L) { "submittedPixelCount must be non-negative" }
        require(allocatedBytes == null || allocatedBytes >= 0L) { "allocatedBytes must be non-negative" }
        require(garbageCollectionCount == null || garbageCollectionCount >= 0L) {
            "garbageCollectionCount must be non-negative"
        }
        require(bufferCacheHitCount >= 0L) { "bufferCacheHitCount must be non-negative" }
        require(bufferCacheMissCount >= 0L) { "bufferCacheMissCount must be non-negative" }
    }
}

/** 定义 `PixelFrameDropReason` 在 `PixelHostFrameDiagnostics` 中承担的数据与行为边界。
 *
 * Primary reason assigned to a frame that crossed one or more display deadlines.
 */
public enum class PixelFrameDropReason {
    /** Retained Element reconciliation or rebuild dominated the missed deadline. */
    BUILD,

    /** RenderObject layout dominated the missed deadline. */
    LAYOUT,

    /** Logical PixelBuffer painting dominated the missed deadline. */
    PAINT,

    /** PixelBuffer-to-Android-Canvas submission dominated the missed deadline. */
    BUFFER_SUBMIT,

    /** Android View drawing outside buffer submission dominated the missed deadline. */
    ANDROID_DRAW,

    /** A process garbage collection best explains otherwise unattributed time. */
    GARBAGE_COLLECTION,

    /** Frame delivery was late even though current onDraw work remained within budget. */
    FRAME_SCHEDULER,

    /** Unmeasured framework or callback work dominated the missed deadline. */
    UNATTRIBUTED,
}

/**
 * 定义 `PixelHostFrameDiagnostics` 在 `PixelHostFrameDiagnostics` 中承担的数据与行为边界。
 *
 * Stable immutable diagnostics snapshot for one [PixelHostView] frame.
 *
 * Diagnostics are opt-in through [PixelHostView.frameDiagnosticsEnabled] or
 * [PixelHostView.frameDiagnosticsObserver]. When enabled, the Host reads the monotonic clock at
 * phase boundaries, allocates two small ART boundary samples, and allocates this snapshot plus its
 * two child value objects once per drawn frame. The observer runs synchronously on the Android UI
 * thread at the end of `onDraw`; consumers must hand work off instead of blocking that callback.
 * When both opt-in controls are disabled, the release frame path performs no diagnostics sampling
 * and creates no diagnostics snapshot.
 *
 * @property frameNumber One-based number of frames observed by this Host recorder.
 * @property frameIntervalNanos Monotonic interval since the previous observed frame start, or zero
 * for the first observed frame.
 * @property frameBudgetNanos Display budget derived from current refresh rate, falling back to
 * 60 Hz only when the Host reports no refresh rate.
 * @property timings Exclusive phase timings and total duration through the terminal sampling
 * boundary; snapshot construction and consumer observer callbacks are deliberately excluded.
 * @property workload Per-frame dirty work, pixel, allocation, GC, and cache counters.
 * @property dropReason Primary deadline-miss attribution, or null when no deadline was crossed.
 * @property missedVsyncCount Number of display budgets crossed by total work or delivery interval.
 */
public data class PixelHostFrameDiagnostics(
    /** 公开 `PixelHostFrameDiagnostics` 的 `frameNumber` 配置或运行值。
 *
 * One-based diagnostics frame number owned by the Host.
 */
    public val frameNumber: Long,
    /** 公开 `PixelHostFrameDiagnostics` 的 `frameIntervalNanos` 配置或运行值。
 *
 * Monotonic interval from the previous observed frame start.
 */
    public val frameIntervalNanos: Long,
    /** 公开 `PixelHostFrameDiagnostics` 的 `frameBudgetNanos` 配置或运行值。
 *
 * Display deadline budget derived from current refresh rate.
 */
    public val frameBudgetNanos: Long,
    /** 公开 `PixelHostFrameDiagnostics` 的 `timings` 配置或运行值。
 *
 * Exclusive phase timing breakdown for this frame.
 */
    public val timings: PixelFrameTimings,
    /** 公开 `PixelHostFrameDiagnostics` 的 `workload` 配置或运行值。
 *
 * Dirty work, pixel, runtime, and cache counters for this frame.
 */
    public val workload: PixelFrameWorkload,
    /** 公开 `PixelHostFrameDiagnostics` 的 `dropReason` 配置或运行值。
 *
 * Primary missed-deadline reason, or null for an on-budget frame.
 */
    public val dropReason: PixelFrameDropReason?,
    /** 公开 `PixelHostFrameDiagnostics` 的 `missedVsyncCount` 配置或运行值。
 *
 * Number of display budgets crossed by work or delivery interval.
 */
    public val missedVsyncCount: Int,
) {
    /** Rejects invalid frame identities, budgets, intervals, and drop-reason combinations. */
    init {
        require(frameNumber >= 1L) { "frameNumber must be at least one" }
        require(frameIntervalNanos >= 0L) { "frameIntervalNanos must be non-negative" }
        require(frameBudgetNanos >= 1L) { "frameBudgetNanos must be positive" }
        require(missedVsyncCount >= 0) { "missedVsyncCount must be non-negative" }
        require((dropReason == null) == (missedVsyncCount == 0)) {
            "dropReason must be present exactly when missedVsyncCount is positive"
        }
    }

    /** 表示 `PixelHostFrameDiagnostics` 当前是否满足 `isOverBudget` 对应条件。
 *
 * Whether either current work or frame delivery crossed the configured display budget.
 */
    public val isOverBudget: Boolean
        get() = missedVsyncCount > 0
}
