package com.purride.pixelui.internal

/**
 * 定义 `PixelFramePhaseSink` 在 `PixelFramePhaseSink` 中的可替换调用契约。
 *
 * Optional runtime-to-Host sink for non-overlapping frame phase measurements.
 *
 * The retained runtime depends only on this platform-neutral contract. Android clock and ART
 * sampling remain owned by the Host implementation so later artifact splitting does not make the
 * runtime depend on Android diagnostics APIs.
 */
public interface PixelFramePhaseSink {
    /** 执行 `PixelFramePhaseSink` 的 `beginBuild` 公开行为；具体参数、返回和副作用见下文。
 *
 * Starts retained Element reconciliation and build timing.
 */
    public fun beginBuild()

    /** 执行 `PixelFramePhaseSink` 的 `endBuild` 公开行为；具体参数、返回和副作用见下文。
 *
 * Ends retained Element reconciliation and build timing.
 */
    public fun endBuild()

    /** 执行 `PixelFramePhaseSink` 的 `recordBuildWork` 公开行为；具体参数、返回和副作用见下文。
 *
 * Adds the number of Elements rebuilt by the completed build phase.
 */
    public fun recordBuildWork(dirtyElementCount: Int)

    /** 执行 `PixelFramePhaseSink` 的 `beginLayout` 公开行为；具体参数、返回和副作用见下文。
 *
 * Starts RenderObject layout timing.
 */
    public fun beginLayout()

    /** 执行 `PixelFramePhaseSink` 的 `endLayout` 公开行为；具体参数、返回和副作用见下文。
 *
 * Ends RenderObject layout timing.
 */
    public fun endLayout()

    /** 执行 `PixelFramePhaseSink` 的 `beginPaint` 公开行为；具体参数、返回和副作用见下文。
 *
 * Starts logical PixelBuffer paint and target-export timing.
 */
    public fun beginPaint()

    /** 执行 `PixelFramePhaseSink` 的 `endPaint` 公开行为；具体参数、返回和副作用见下文。
 *
 * Ends logical PixelBuffer paint and target-export timing.
 */
    public fun endPaint()

    /** 执行 `PixelFramePhaseSink` 的 `recordPipelineWork` 公开行为；具体参数、返回和副作用见下文。
 *
 * Records dirty RenderObject, logical-pixel, and whole-result cache work.
 */
    public fun recordPipelineWork(
        dirtyRenderNodeCount: Int,
        paintedPixelCount: Long,
        renderCacheHit: Boolean,
    )

    /** 执行 `PixelFramePhaseSink` 的 `recordBufferPoolActivity` 公开行为；具体参数、返回和副作用见下文。
 *
 * Adds PixelBuffer pool hit and miss deltas observed by the runtime.
 */
    public fun recordBufferPoolActivity(hitCount: Long, missCount: Long)
}
