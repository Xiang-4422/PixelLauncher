package com.purride.pixelui.perf

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 只读性能回归门禁。
 *
 * 读取 tracked baseline，对关键指标做宽松阈值断言；本测试永不写盘。
 */
class PerfRegressionTest {

    @Test
    fun currentPerfStaysWithinBaselineThresholds() {
        // 墙钟微基准在共享 CI runner 上不可移植：baseline 在固定机器上采集，
        // 而 ubuntu-latest 抢占 CPU、无 JIT 预热控制，时间指标轻易超过 2× 阈值。
        // 因此本门禁改为 opt-in——只在受控 runner 上设 RUN_PERF_REGRESSION 才执行，
        // 其余环境（含 CI）标记为 skipped 而非 failed。perf 追踪仍可手动触发。
        assumeTrue(
            "Perf regression gate is opt-in; set RUN_PERF_REGRESSION=1 on a controlled runner to enable it.",
            System.getenv("RUN_PERF_REGRESSION") != null,
        )
        val baseline = readBaselineOrSkip()
        val current = PerfMeasurements.captureSample()

        if (PerfMeasurements.isAllocationTrackingAvailable()) {
            assertWithinThreshold(
                name = "renderAllocBytesAvg",
                baseline = baseline.renderAllocBytesAvg,
                current = current.renderAllocBytesAvg,
                multiplier = ALLOCATION_MULTIPLIER,
            )
            assertWithinThreshold(
                name = "renderAllocBytesMax",
                baseline = baseline.renderAllocBytesMax,
                current = current.renderAllocBytesMax,
                multiplier = ALLOCATION_MULTIPLIER,
            )
        } else {
            println("[PerfRegression] allocation checks skipped: ThreadMXBean.getThreadAllocatedBytes 不可用")
        }

        assertWithinThreshold(
            name = "renderTimeNanosAvg",
            baseline = baseline.renderTimeNanosAvg,
            current = current.renderTimeNanosAvg,
            multiplier = TIME_MULTIPLIER,
        )
        assertWithinThreshold(
            name = "blitNanosAvg",
            baseline = baseline.blitNanosAvg,
            current = current.blitNanosAvg,
            multiplier = TIME_MULTIPLIER,
        )
        assertWithinThreshold(
            name = "resourceCacheNanosAvg",
            baseline = baseline.resourceCacheNanosAvg,
            current = current.resourceCacheNanosAvg,
            multiplier = TIME_MULTIPLIER,
        )
        assertWithinThreshold(
            name = "gridRenderNanosAvg",
            baseline = baseline.gridRenderNanosAvg,
            current = current.gridRenderNanosAvg,
            multiplier = TIME_MULTIPLIER,
        )
        assertWithinThreshold(
            name = "componentRenderNanosAvg",
            baseline = baseline.componentRenderNanosAvg,
            current = current.componentRenderNanosAvg,
            multiplier = TIME_MULTIPLIER,
        )
        baseline.richTextLayoutNanosByChars.forEach { (chars, baselineNanos) ->
            val currentNanos = current.richTextLayoutNanosByChars[chars] ?: return@forEach
            assertWithinThreshold(
                name = "richTextLayoutNanosByChars[$chars]",
                baseline = baselineNanos,
                current = currentNanos,
                multiplier = TIME_MULTIPLIER,
            )
        }
        baseline.variableLazyListNanosByItemCount.forEach { (itemCount, baselineNanos) ->
            val currentNanos = current.variableLazyListNanosByItemCount[itemCount] ?: return@forEach
            assertWithinThreshold(
                name = "variableLazyListNanosByItemCount[$itemCount]",
                baseline = baselineNanos,
                current = currentNanos,
                multiplier = TIME_MULTIPLIER,
            )
        }
    }

    private fun readBaselineOrSkip(): PerfSample {
        val text = javaClass.classLoader
            ?.getResource(PerfMeasurements.BASELINE_RESOURCE)
            ?.readText()
        assumeTrue(
            "Missing ${PerfMeasurements.BASELINE_RESOURCE}; generate it with REGEN_PERF_BASELINE=1 ./gradlew :pixel-engine:testDebugUnitTest --no-daemon",
            !text.isNullOrBlank(),
        )
        return parseBaseline(text = text.orEmpty())
    }

    private fun parseBaseline(text: String): PerfSample {
        return PerfSample(
            renderAllocBytesAvg = requiredLong(text = text, key = "renderAllocBytesAvg"),
            renderAllocBytesMax = requiredLong(text = text, key = "renderAllocBytesMax"),
            renderTimeNanosAvg = requiredLong(text = text, key = "renderTimeNanosAvg"),
            blitNanosAvg = requiredLong(text = text, key = "blitNanosAvg"),
            colorBlitNanosAvg = optionalLong(text = text, key = "colorBlitNanosAvg") ?: 0L,
            resourceCacheNanosAvg = optionalLong(text = text, key = "resourceCacheNanosAvg") ?: 0L,
            gridRenderNanosAvg = optionalLong(text = text, key = "gridRenderNanosAvg") ?: 0L,
            componentRenderNanosAvg = optionalLong(text = text, key = "componentRenderNanosAvg") ?: 0L,
            richTextLayoutNanosByChars = parseMapBlock(text = text, key = "richTextLayoutNanosByChars"),
            variableLazyListNanosByItemCount = parseMapBlock(text = text, key = "variableLazyListNanosByItemCount"),
        )
    }

    private fun requiredLong(text: String, key: String): Long {
        val pattern = Regex("\"$key\"\\s*:\\s*(\\d+)")
        val match = pattern.find(text)
        requireNotNull(match) { "Missing perf baseline key: $key" }
        return match.groupValues[1].toLong()
    }

    private fun optionalLong(text: String, key: String): Long? {
        val pattern = Regex("\"$key\"\\s*:\\s*(\\d+)")
        return pattern.find(text)?.groupValues?.get(1)?.toLong()
    }

    private fun parseMapBlock(text: String, key: String): Map<Int, Long> {
        val block = Regex("\"$key\"\\s*:\\s*\\{([^}]*)}")
            .find(text)
            ?.groupValues
            ?.get(1)
            .orEmpty()
        return Regex("\"(\\d+)\"\\s*:\\s*(\\d+)")
            .findAll(block)
            .associate { match ->
                match.groupValues[1].toInt() to match.groupValues[2].toLong()
            }
    }

    private fun assertWithinThreshold(
        name: String,
        baseline: Long,
        current: Long,
        multiplier: Double,
    ) {
        val threshold = (baseline * multiplier).toLong()
        assertTrue(
            "$name regression: baseline=$baseline current=$current threshold=$threshold multiplier=$multiplier",
            current <= threshold,
        )
    }

    private companion object {
        const val ALLOCATION_MULTIPLIER: Double = 1.5
        const val TIME_MULTIPLIER: Double = 2.0
    }
}
