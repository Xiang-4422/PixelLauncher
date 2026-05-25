package com.purride.pixelui.perf

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.RichText
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.PixelUiRuntime
import com.purride.pixelui.state.PixelListController
import java.lang.management.ManagementFactory

internal data class PerfSample(
    val renderAllocBytesAvg: Long,
    val renderAllocBytesMax: Long,
    val renderTimeNanosAvg: Long,
    val blitNanosAvg: Long,
    val colorBlitNanosAvg: Long,
    val richTextLayoutNanosByChars: Map<Int, Long>,
    val variableLazyListNanosByItemCount: Map<Int, Long>,
)

internal object PerfMeasurements {
    const val BASELINE_PATH: String = "src/test/resources/perf-baseline.json"
    const val BASELINE_RESOURCE: String = "perf-baseline.json"

    fun captureSample(): PerfSample {
        warmup()
        return PerfSample(
            renderAllocBytesAvg = measureRenderAllocBytes(iterations = RENDER_ITERATIONS, mode = AggregateMode.AVG),
            renderAllocBytesMax = measureRenderAllocBytes(iterations = RENDER_ITERATIONS, mode = AggregateMode.MAX),
            renderTimeNanosAvg = medianLongSample(RENDER_TIME_SAMPLES) {
                measureRenderTimeNanos(iterations = RENDER_ITERATIONS)
            },
            blitNanosAvg = medianLongSample(BLIT_SAMPLES) {
                measureBlitNanos(iterations = BLIT_ITERATIONS)
            },
            colorBlitNanosAvg = medianLongSample(BLIT_SAMPLES) {
                measureColorBlitNanos(iterations = BLIT_ITERATIONS)
            },
            richTextLayoutNanosByChars = mapOf(
                100 to medianLongSample(RICH_TEXT_SAMPLES) {
                    measureRichTextLayoutNanos(charCount = 100, iterations = RICH_TEXT_ITERATIONS)
                },
                500 to medianLongSample(RICH_TEXT_SAMPLES) {
                    measureRichTextLayoutNanos(charCount = 500, iterations = RICH_TEXT_ITERATIONS)
                },
                1000 to medianLongSample(RICH_TEXT_SAMPLES) {
                    measureRichTextLayoutNanos(charCount = 1000, iterations = RICH_TEXT_ITERATIONS)
                },
            ),
            variableLazyListNanosByItemCount = mapOf(
                1_000 to medianLongSample(VARIABLE_LIST_SAMPLES) {
                    measureVariableLazyListRenderNanos(
                        itemCount = 1_000,
                        iterations = VARIABLE_LIST_ITERATIONS,
                    )
                },
                5_000 to medianLongSample(VARIABLE_LIST_SAMPLES) {
                    measureVariableLazyListRenderNanos(
                        itemCount = 5_000,
                        iterations = VARIABLE_LIST_ITERATIONS,
                    )
                },
            ),
        )
    }

    fun isAllocationTrackingAvailable(): Boolean {
        return allocationProbe != null
    }

    fun buildJson(sample: PerfSample): String {
        val richTextEntries = sample.richTextLayoutNanosByChars.entries
            .sortedBy { it.key }
            .joinToString(separator = ",") { (chars, nanos) ->
                "\"$chars\":$nanos"
            }
        val variableListEntries = sample.variableLazyListNanosByItemCount.entries
            .sortedBy { it.key }
            .joinToString(separator = ",") { (itemCount, nanos) ->
                "\"$itemCount\":$nanos"
            }
        return buildString {
            append("{\n")
            append("  \"capturedAtEpochMs\": ").append(System.currentTimeMillis()).append(",\n")
            append("  \"jvmVersion\": \"").append(System.getProperty("java.version")).append("\",\n")
            append("  \"renderAllocBytesAvg\": ").append(sample.renderAllocBytesAvg).append(",\n")
            append("  \"renderAllocBytesMax\": ").append(sample.renderAllocBytesMax).append(",\n")
            append("  \"renderTimeNanosAvg\": ").append(sample.renderTimeNanosAvg).append(",\n")
            append("  \"blitNanosAvg\": ").append(sample.blitNanosAvg).append(",\n")
            append("  \"colorBlitNanosAvg\": ").append(sample.colorBlitNanosAvg).append(",\n")
            append("  \"richTextLayoutNanosByChars\": {").append(richTextEntries).append("},\n")
            append("  \"variableLazyListNanosByItemCount\": {").append(variableListEntries).append("}\n")
            append("}\n")
        }
    }

    private fun warmup() {
        val widget = Row(
            children = listOf(
                Text("WARMUP", style = TextStyle.Default),
                Text("CACHE", style = TextStyle.Default),
            ),
        )
        val runtime = PixelUiRuntime()
        try {
            repeat(WARMUP_ITERATIONS) {
                runtime.render(root = widget, logicalWidth = 64, logicalHeight = 16)
            }
        } finally {
            runtime.dispose()
        }
    }

    private fun measureRenderAllocBytes(iterations: Int, mode: AggregateMode): Long {
        val widget = standardRenderWidget()
        val runtime = PixelUiRuntime()
        try {
            @Suppress("DEPRECATION")
            val threadId = Thread.currentThread().id
            val samples = LongArray(iterations)
            for (i in 0 until iterations) {
                val before = currentThreadAllocatedBytes(threadId)
                runtime.render(root = widget, logicalWidth = 64, logicalHeight = 16)
                val after = currentThreadAllocatedBytes(threadId)
                samples[i] = (after - before).coerceAtLeast(0L)
            }
            return when (mode) {
                AggregateMode.AVG -> samples.sum() / iterations.toLong().coerceAtLeast(1L)
                AggregateMode.MAX -> samples.max()
            }
        } finally {
            runtime.dispose()
        }
    }

    private fun measureRenderTimeNanos(iterations: Int): Long {
        val widget = standardRenderWidget()
        val runtime = PixelUiRuntime()
        try {
            val start = System.nanoTime()
            repeat(iterations) {
                runtime.render(root = widget, logicalWidth = 64, logicalHeight = 16)
            }
            val end = System.nanoTime()
            return (end - start) / iterations.toLong().coerceAtLeast(1L)
        } finally {
            runtime.dispose()
        }
    }

    private fun measureBlitNanos(iterations: Int): Long {
        val source = PixelBuffer(width = 32, height = 32)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                source.setPixel(x, y, PixelColor.White)
            }
        }
        val dest = PixelBuffer(width = 64, height = 64)
        val start = System.nanoTime()
        repeat(iterations) {
            dest.blit(source = source, destX = 0, destY = 0)
        }
        val end = System.nanoTime()
        return (end - start) / iterations.toLong().coerceAtLeast(1L)
    }

    private fun measureColorBlitNanos(iterations: Int): Long {
        val source = PixelBuffer(width = 32, height = 32)
        val fillColor = PixelColor.fromRgb(255, 128, 0)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                source.setPixel(x, y, fillColor)
            }
        }
        val dest = PixelBuffer(width = 64, height = 64)
        val start = System.nanoTime()
        repeat(iterations) {
            dest.blit(source = source, destX = 0, destY = 0)
        }
        val end = System.nanoTime()
        return (end - start) / iterations.toLong().coerceAtLeast(1L)
    }

    private fun measureRichTextLayoutNanos(charCount: Int, iterations: Int): Long {
        val text = buildString { repeat(charCount) { append(if (it % 5 == 0) ' ' else 'X') } }
        val widget = RichText(
            spans = listOf(
                PixelTextSpan(text = text, style = TextStyle.Default),
            ),
            softWrap = true,
        )
        val runtime = PixelUiRuntime()
        try {
            val start = System.nanoTime()
            repeat(iterations) {
                runtime.render(root = widget, logicalWidth = 80, logicalHeight = 240)
            }
            val end = System.nanoTime()
            return (end - start) / iterations.toLong().coerceAtLeast(1L)
        } finally {
            runtime.dispose()
        }
    }

    private fun standardRenderWidget(): Widget {
        return Row(
            children = listOf(
                Text("LEFT", style = TextStyle.Default),
                Text("RIGHT", style = TextStyle.Default),
            ),
        )
    }

    /**
     * 变高 lazy list 端到端渲染耗时。
     *
     * 每次迭代都重建 controller + state，避免命中第二帧 measuredItemHeightsPx
     * 缓存命中的"快路径"，更接近"首屏渲染 N item 列表"的成本曲线。
     * 视口固定 32x96；item 真实高度按 index 在 6/8/10/12 间循环，触发变高
     * 测量逻辑而不是固定高度快路径。
     */
    private fun measureVariableLazyListRenderNanos(itemCount: Int, iterations: Int): Long {
        val runtime = PixelUiRuntime()
        try {
            val start = System.nanoTime()
            repeat(iterations) {
                val controller = PixelListController()
                val state = controller.create()
                val widget = SizedBox(
                    width = 32,
                    height = 96,
                    child = ListViewBuilder(
                        itemCount = itemCount,
                        state = state,
                        controller = controller,
                        estimatedItemExtent = 8,
                        cacheExtent = 1,
                        spacing = 0,
                        itemBuilder = { index ->
                            SizedBox(
                                height = 6 + (index % 4) * 2,
                                child = OutlinedButton(text = "X", onPressed = { }),
                            )
                        },
                    ),
                )
                runtime.render(root = widget, logicalWidth = 32, logicalHeight = 96)
            }
            val end = System.nanoTime()
            return (end - start) / iterations.toLong().coerceAtLeast(1L)
        } finally {
            runtime.dispose()
        }
    }

    private fun medianLongSample(count: Int, sample: () -> Long): Long {
        val values = LongArray(count.coerceAtLeast(1)) {
            sample()
        }.sortedArray()
        return values[values.size / 2]
    }

    private fun currentThreadAllocatedBytes(threadId: Long): Long {
        val probe = allocationProbe ?: return 0L
        return try {
            (probe.method.invoke(probe.bean, threadId) as Long).coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }
    }

    private data class AllocationProbe(
        val bean: Any,
        val method: java.lang.reflect.Method,
    )

    private val allocationProbe: AllocationProbe? by lazy { resolveAllocationProbe() }

    private fun resolveAllocationProbe(): AllocationProbe? {
        val bean = ManagementFactory.getThreadMXBean()
        val sunClass = try {
            Class.forName("com.sun.management.ThreadMXBean")
        } catch (_: ClassNotFoundException) {
            return null
        }
        if (!sunClass.isInstance(bean)) {
            return null
        }
        return try {
            val enable = sunClass.getMethod("setThreadAllocatedMemoryEnabled", Boolean::class.javaPrimitiveType)
            enable.invoke(bean, true)
            val getter = sunClass.getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
            AllocationProbe(bean = bean, method = getter)
        } catch (_: Exception) {
            null
        }
    }

    private enum class AggregateMode { AVG, MAX }

    private const val WARMUP_ITERATIONS = 20
    private const val RENDER_ITERATIONS = 50
    private const val BLIT_ITERATIONS = 1_000
    private const val RICH_TEXT_ITERATIONS = 20
    private const val RENDER_TIME_SAMPLES = 5
    private const val BLIT_SAMPLES = 5
    private const val RICH_TEXT_SAMPLES = 5
    private const val VARIABLE_LIST_ITERATIONS = 3
    private const val VARIABLE_LIST_SAMPLES = 3
}
