package com.purride.pixelui.host

import com.purride.pixelui.PixelFrameDropReason
import com.purride.pixelui.PixelHostFrameDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies deterministic full-frame timing, workload, and deadline attribution. */
class PixelFrameDiagnosticsRecorderTest {
    /** 验证启用诊断时 trace 区间与计时阶段严格嵌套且重复 begin 不会破坏栈。 */
    @Test
    fun emitsBalancedTraceSectionsForOneCompleteFrame() {
        /** 记录同步区间开始和结束顺序的测试 trace 出口。 */
        val traceSink = RecordingFrameTraceSink()
        /** 为每个阶段提供单调时间的确定性测试时钟。 */
        val clock = FakeDiagnosticsClock()
        /** 使用空运行时样本，避免 trace 契约测试依赖 ART 计数。 */
        val recorder = PixelFrameDiagnosticsRecorder(
            clock = clock,
            runtimeMetricsSampler = FakeRuntimeMetricsSampler.unavailable(),
            frameTraceSink = traceSink,
        )

        recorder.beginFrame(frameBudgetNanos = 100L)
        recorder.beginBuild()
        recorder.beginBuild()
        clock.advance(1L)
        recorder.endBuild()
        recorder.beginLayout()
        clock.advance(1L)
        recorder.endLayout()
        recorder.beginPaint()
        clock.advance(1L)
        recorder.endPaint()
        recorder.beginBufferSubmit()
        clock.advance(1L)
        recorder.endBufferSubmit()
        recorder.beginAndroidDraw()
        clock.advance(1L)
        recorder.endAndroidDraw()
        requireNotNull(recorder.finishFrame())

        assertEquals(
            listOf(
                "+pixel.frame",
                "+pixel.build",
                "-pixel.build",
                "+pixel.layout",
                "-pixel.layout",
                "+pixel.paint",
                "-pixel.paint",
                "+pixel.buffer_submit",
                "-pixel.buffer_submit",
                "+pixel.android_draw",
                "-pixel.android_draw",
                "-pixel.frame",
            ),
            traceSink.events,
        )
        assertTrue(traceSink.openSections.isEmpty())
    }

    /** Records a complete frame without sampling or allocating before diagnostics are enabled. */
    @Test
    fun recordsEveryFrameStageAndWorkloadOnlyAfterBegin() {
        /** Controllable monotonic time used to assign an exact duration to every phase. */
        val clock = FakeDiagnosticsClock()
        /** Process-wide allocation and GC samples captured at frame boundaries. */
        val sampler = FakeRuntimeMetricsSampler(
            samples = ArrayDeque(
                listOf(
                    RuntimeFrameMetricsSample(allocatedBytes = 1_000L, garbageCollectionCount = 4L),
                    RuntimeFrameMetricsSample(allocatedBytes = 1_300L, garbageCollectionCount = 5L),
                ),
            ),
        )
        /** Recorder under test; construction alone must not activate the expensive sampler. */
        val recorder = PixelFrameDiagnosticsRecorder(clock = clock, runtimeMetricsSampler = sampler)

        assertNull(recorder.finishFrame())
        assertEquals(0, sampler.sampleCount)

        recorder.beginFrame(frameBudgetNanos = 10L)
        recorder.beginAndroidDraw()
        clock.advance(1L)
        recorder.endAndroidDraw()
        recorder.beginBuild()
        clock.advance(2L)
        recorder.endBuild()
        recorder.beginLayout()
        clock.advance(3L)
        recorder.endLayout()
        recorder.beginPaint()
        clock.advance(4L)
        recorder.endPaint()
        recorder.beginBufferSubmit()
        clock.advance(5L)
        recorder.endBufferSubmit()
        recorder.beginAndroidDraw()
        clock.advance(1L)
        recorder.endAndroidDraw()
        recorder.recordBuildWork(dirtyElementCount = 7)
        recorder.recordPipelineWork(
            dirtyRenderNodeCount = 11,
            paintedPixelCount = 320L,
            renderCacheHit = false,
        )
        recorder.recordBufferPoolActivity(hitCount = 3L, missCount = 1L)
        recorder.recordBufferSubmit(submittedPixelCount = 320L)

        /** Immutable snapshot emitted only after every frame phase has closed. */
        val diagnostics = requireNotNull(recorder.finishFrame())
        assertEquals(2, sampler.sampleCount)
        assertEquals(2L, diagnostics.timings.buildNanos)
        assertEquals(3L, diagnostics.timings.layoutNanos)
        assertEquals(4L, diagnostics.timings.paintNanos)
        assertEquals(5L, diagnostics.timings.bufferSubmitNanos)
        assertEquals(2L, diagnostics.timings.androidDrawNanos)
        assertEquals(16L, diagnostics.timings.totalFrameNanos)
        assertEquals(0L, diagnostics.timings.unattributedNanos)
        assertEquals(7, diagnostics.workload.dirtyElementCount)
        assertEquals(11, diagnostics.workload.dirtyRenderNodeCount)
        assertEquals(320L, diagnostics.workload.paintedPixelCount)
        assertEquals(320L, diagnostics.workload.submittedPixelCount)
        assertEquals(300L, diagnostics.workload.allocatedBytes)
        assertEquals(1L, diagnostics.workload.garbageCollectionCount)
        assertEquals(3L, diagnostics.workload.bufferCacheHitCount)
        assertEquals(1L, diagnostics.workload.bufferCacheMissCount)
        assertFalse(diagnostics.workload.renderCacheHit)
        assertEquals(PixelFrameDropReason.BUFFER_SUBMIT, diagnostics.dropReason)
        assertEquals(1, diagnostics.missedVsyncCount)
    }

    /** Proves disabled phase calls remain primitive no-ops and never touch the ART sampler. */
    @Test
    fun inactiveRecorderNeverSamplesOrCreatesFrameSnapshots() {
        /** Sampler whose invocation count makes any disabled-path ART access observable. */
        val sampler = FakeRuntimeMetricsSampler.unavailable()
        /** Recorder deliberately used without [PixelFrameDiagnosticsRecorder.beginFrame]. */
        val recorder = PixelFrameDiagnosticsRecorder(
            clock = FakeDiagnosticsClock(),
            runtimeMetricsSampler = sampler,
        )

        repeat(10_000) {
            recorder.beginBuild()
            recorder.endBuild()
            recorder.beginLayout()
            recorder.endLayout()
            recorder.beginPaint()
            recorder.endPaint()
            recorder.beginBufferSubmit()
            recorder.endBufferSubmit()
            recorder.beginAndroidDraw()
            recorder.endAndroidDraw()
            recorder.recordBuildWork(1)
            recorder.recordPipelineWork(1, 1L, renderCacheHit = false)
            recorder.recordBufferPoolActivity(1L, 1L)
            recorder.recordBufferSubmit(1L)
        }

        assertNull(recorder.finishFrame())
        assertNull(recorder.latestDiagnostics)
        assertEquals(0, sampler.sampleCount)
    }

    /** Ensures an artificial bottleneck is attributed to its actual measured phase. */
    @Test
    fun attributesArtificialBottlenecksToTheirMeasuredStage() {
        /** Every independently delayed phase and its expected primary deadline-miss reason. */
        val cases = listOf(
            PixelFrameDropReason.BUILD,
            PixelFrameDropReason.LAYOUT,
            PixelFrameDropReason.PAINT,
            PixelFrameDropReason.BUFFER_SUBMIT,
            PixelFrameDropReason.ANDROID_DRAW,
        )

        cases.forEach { expectedReason ->
            /** Diagnostics produced by delaying only the phase represented by [expectedReason]. */
            val diagnostics = captureSinglePhaseBottleneck(expectedReason)
            assertEquals(expectedReason, diagnostics.dropReason)
            assertTrue(diagnostics.isOverBudget)
        }
    }

    /** Distinguishes delayed frame delivery from work performed inside the current frame. */
    @Test
    fun attributesLongFrameIntervalToSchedulerWhenCurrentWorkIsWithinBudget() {
        /** Clock shared across two frames so the second start observes a long delivery interval. */
        val clock = FakeDiagnosticsClock()
        /** Recorder with unavailable process metrics, matching a runtime that cannot expose ART stats. */
        val recorder = PixelFrameDiagnosticsRecorder(
            clock = clock,
            runtimeMetricsSampler = FakeRuntimeMetricsSampler.unavailable(),
        )

        recorder.beginFrame(frameBudgetNanos = 10L)
        clock.advance(1L)
        requireNotNull(recorder.finishFrame())
        clock.advance(20L)
        recorder.beginFrame(frameBudgetNanos = 10L)
        clock.advance(1L)

        /** Second frame is cheap, but its start interval crossed two frame budgets. */
        val diagnostics = requireNotNull(recorder.finishFrame())
        assertEquals(21L, diagnostics.frameIntervalNanos)
        assertEquals(PixelFrameDropReason.FRAME_SCHEDULER, diagnostics.dropReason)
        assertEquals(2, diagnostics.missedVsyncCount)
    }

    /** Attributes otherwise-unmeasured over-budget time to an observed garbage collection. */
    @Test
    fun attributesUnmeasuredDelayToGarbageCollectionWhenGcCounterAdvances() {
        /** Clock whose unmeasured advancement represents runtime work outside named phases. */
        val clock = FakeDiagnosticsClock()
        /** Boundary samples proving that one process GC occurred during the frame. */
        val sampler = FakeRuntimeMetricsSampler(
            samples = ArrayDeque(
                listOf(
                    RuntimeFrameMetricsSample(allocatedBytes = 10L, garbageCollectionCount = 2L),
                    RuntimeFrameMetricsSample(allocatedBytes = 20L, garbageCollectionCount = 3L),
                ),
            ),
        )
        /** Recorder whose 10 ns budget is intentionally exceeded by unattributed runtime time. */
        val recorder = PixelFrameDiagnosticsRecorder(clock = clock, runtimeMetricsSampler = sampler)

        recorder.beginFrame(frameBudgetNanos = 10L)
        clock.advance(11L)

        /** GC is the strongest available explanation for the entirely unattributed delay. */
        val diagnostics = requireNotNull(recorder.finishFrame())
        assertEquals(11L, diagnostics.timings.unattributedNanos)
        assertEquals(PixelFrameDropReason.GARBAGE_COLLECTION, diagnostics.dropReason)
    }

    /** Converts 60/120 Hz capabilities and invalid values into deterministic frame budgets. */
    @Test
    fun frameBudgetUsesRefreshRateAndDocumentedFallback() {
        assertEquals(16_666_667L, PixelFrameDiagnosticsRecorder.frameBudgetNanos(60f))
        assertEquals(8_333_333L, PixelFrameDiagnosticsRecorder.frameBudgetNanos(120f))
        assertEquals(16_666_667L, PixelFrameDiagnosticsRecorder.frameBudgetNanos(null))
        assertEquals(16_666_667L, PixelFrameDiagnosticsRecorder.frameBudgetNanos(Float.NaN))
        assertEquals(16_666_667L, PixelFrameDiagnosticsRecorder.frameBudgetNanos(0f))
    }

    /** Produces one over-budget frame whose only timed work belongs to [reason]. */
    private fun captureSinglePhaseBottleneck(reason: PixelFrameDropReason): PixelHostFrameDiagnostics {
        /** Fresh clock prevents one case's frame interval from affecting another case. */
        val clock = FakeDiagnosticsClock()
        /** Fresh recorder isolates phase accumulators and frame counters per case. */
        val recorder = PixelFrameDiagnosticsRecorder(
            clock = clock,
            runtimeMetricsSampler = FakeRuntimeMetricsSampler.unavailable(),
        )
        recorder.beginFrame(frameBudgetNanos = 10L)
        when (reason) {
            PixelFrameDropReason.BUILD -> recorder.measureBuild { clock.advance(11L) }
            PixelFrameDropReason.LAYOUT -> recorder.measureLayout { clock.advance(11L) }
            PixelFrameDropReason.PAINT -> recorder.measurePaint { clock.advance(11L) }
            PixelFrameDropReason.BUFFER_SUBMIT -> recorder.measureBufferSubmit { clock.advance(11L) }
            PixelFrameDropReason.ANDROID_DRAW -> recorder.measureAndroidDraw { clock.advance(11L) }
            else -> error("Unsupported synthetic phase: $reason")
        }
        return requireNotNull(recorder.finishFrame())
    }

    /** Runs [block] between the recorder's build boundaries. */
    private inline fun PixelFrameDiagnosticsRecorder.measureBuild(block: () -> Unit) {
        beginBuild()
        try {
            block()
        } finally {
            endBuild()
        }
    }

    /** Runs [block] between the recorder's layout boundaries. */
    private inline fun PixelFrameDiagnosticsRecorder.measureLayout(block: () -> Unit) {
        beginLayout()
        try {
            block()
        } finally {
            endLayout()
        }
    }

    /** Runs [block] between the recorder's engine-paint boundaries. */
    private inline fun PixelFrameDiagnosticsRecorder.measurePaint(block: () -> Unit) {
        beginPaint()
        try {
            block()
        } finally {
            endPaint()
        }
    }

    /** Runs [block] between the recorder's PixelBuffer-to-Canvas submission boundaries. */
    private inline fun PixelFrameDiagnosticsRecorder.measureBufferSubmit(block: () -> Unit) {
        beginBufferSubmit()
        try {
            block()
        } finally {
            endBufferSubmit()
        }
    }

    /** Runs [block] between the recorder's Android-host drawing boundaries. */
    private inline fun PixelFrameDiagnosticsRecorder.measureAndroidDraw(block: () -> Unit) {
        beginAndroidDraw()
        try {
            block()
        } finally {
            endAndroidDraw()
        }
    }

    /** Mutable monotonic clock used by tests without wall-clock sleeps. */
    private class FakeDiagnosticsClock(
        /** Current nanosecond reading returned by [nanoTime]. */
        private var nanos: Long = 0L,
    ) : MonotonicClock {
        /** Millisecond projection retained for the legacy frame loop clock contract. */
        override fun uptimeMillis(): Long = nanos / NANOS_PER_MILLISECOND

        /** Exact current monotonic test timestamp. */
        override fun nanoTime(): Long = nanos

        /** Advances the monotonic timestamp by a deterministic non-negative duration. */
        fun advance(durationNanos: Long) {
            require(durationNanos >= 0L)
            nanos += durationNanos
        }

        /** Test-only conversion constant between milliseconds and nanoseconds. */
        private companion object {
            /** Number of nanoseconds represented by one millisecond. */
            const val NANOS_PER_MILLISECOND: Long = 1_000_000L
        }
    }

    /** 用字符串事件验证同步 trace 栈平衡的纯 JVM 测试实现。 */
    private class RecordingFrameTraceSink : PixelFrameTraceSink {
        /** 当前仍未结束的同步区间名称栈。 */
        val openSections: ArrayDeque<String> = ArrayDeque()

        /** 按实际调用顺序保存的 begin/end 事件。 */
        val events: MutableList<String> = mutableListOf()

        /** 记录一个区间开始并压入当前线程模拟栈。 */
        override fun beginSection(name: String) {
            openSections.addLast(name)
            events += "+$name"
        }

        /** 关闭最近区间并记录对应名称，空栈会直接使测试失败。 */
        override fun endSection() {
            /** 最近开始且必须存在的同步区间名称。 */
            val name = openSections.removeLast()
            events += "-$name"
        }
    }

    /** Queue-backed process metrics sampler with an observable invocation count. */
    private class FakeRuntimeMetricsSampler(
        /** Samples returned in frame-boundary order. */
        private val samples: ArrayDeque<RuntimeFrameMetricsSample>,
    ) : RuntimeFrameMetricsSampler {
        /** Number of boundary samples requested by the recorder. */
        var sampleCount: Int = 0
            private set

        /** Returns the next queued sample and fails if the recorder over-samples. */
        override fun sample(): RuntimeFrameMetricsSample {
            sampleCount += 1
            return samples.removeFirst()
        }

        /** Test factories for runtimes where process allocation/GC counters are unavailable. */
        companion object {
            /** Creates a sampler with enough unavailable samples for two complete frames. */
            fun unavailable(): FakeRuntimeMetricsSampler {
                return FakeRuntimeMetricsSampler(
                    samples = ArrayDeque(
                        listOf(
                            RuntimeFrameMetricsSample.Unavailable,
                            RuntimeFrameMetricsSample.Unavailable,
                            RuntimeFrameMetricsSample.Unavailable,
                            RuntimeFrameMetricsSample.Unavailable,
                        ),
                    ),
                )
            }
        }
    }
}
