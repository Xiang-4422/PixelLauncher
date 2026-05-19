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
        baseline.richTextLayoutNanosByChars.forEach { (chars, baselineNanos) ->
            val currentNanos = current.richTextLayoutNanosByChars[chars] ?: return@forEach
            assertWithinThreshold(
                name = "richTextLayoutNanosByChars[$chars]",
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
            richTextLayoutNanosByChars = parseRichTextMap(text = text),
        )
    }

    private fun requiredLong(text: String, key: String): Long {
        val pattern = Regex("\"$key\"\\s*:\\s*(\\d+)")
        val match = pattern.find(text)
        requireNotNull(match) { "Missing perf baseline key: $key" }
        return match.groupValues[1].toLong()
    }

    private fun parseRichTextMap(text: String): Map<Int, Long> {
        val block = Regex("\"richTextLayoutNanosByChars\"\\s*:\\s*\\{([^}]*)}")
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
