package com.purride.pixelui.perf

import org.junit.Test
import java.io.File

/**
 * Phase 0 性能基线。
 *
 * 这里不是断言式 perf 测试。它只采样当前实现，默认打印到 stdout，不写盘。
 * 只有设置 `REGEN_PERF_BASELINE=1` 时才覆盖 `src/test/resources/perf-baseline.json`。
 */
class PerfBaselineTest {

    @Test
    fun captureBaseline() {
        val regen = System.getenv("REGEN_PERF_BASELINE") == "1"

        if (!PerfMeasurements.isAllocationTrackingAvailable()) {
            println("[PerfBaseline] skipped: ThreadMXBean.getThreadAllocatedBytes 不可用，可能在该 JVM 上没有 com.sun.management 扩展")
            return
        }

        val json = PerfMeasurements.buildJson(sample = PerfMeasurements.captureSample())
        if (regen) {
            val baselineFile = File(PerfMeasurements.BASELINE_PATH)
            baselineFile.parentFile?.mkdirs()
            baselineFile.writeText(json)
            println("[PerfBaseline] REGEN：已写入 ${PerfMeasurements.BASELINE_PATH}")
            return
        }
        println("[PerfBaseline] 当前样本（不写盘）：$json")
    }
}
