package com.purride.pixelui.perf

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelTone
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.RichText
import com.purride.pixelui.Row
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.PixelUiRuntime
import org.junit.Test
import java.io.File
import java.lang.management.ManagementFactory

/**
 * Phase 0 性能基线。
 *
 * 这里**不是**断言式 perf 测试——它的职责是**采样并落盘**当前实现的性能数据，
 * 让 Phase 2 完成池化、增量布局、blit/wrap 优化后可以做绝对对比，
 * 而不是依靠人工记忆"重构前大概是多少"。
 *
 * 写入位置：src/test/resources/perf-baseline.json
 *
 * 重新生成基线：设置环境变量 REGEN_PERF_BASELINE=1。
 *
 * 默认模式下：如果磁盘上已有 baseline 文件，就把当前一轮采样附加到 history
 * 数组里，方便观察一段时间的趋势；如果没有 baseline 就只跑一次不落盘，
 * 避免 CI 静默修改基线。
 */
class PerfBaselineTest {

    /**
     * 全部基线指标。
     */
    private data class BaselineSample(
        val renderAllocBytesAvg: Long,
        val renderAllocBytesMax: Long,
        val renderTimeNanosAvg: Long,
        val blitNanosAvg: Long,
        val richTextLayoutNanosByChars: Map<Int, Long>,
    )

    @Test
    fun captureBaseline() {
        if (!isAllocationTrackingAvailable()) {
            // 该 JVM 不支持线程级分配统计——跳过 baseline，但记录原因。
            val report = "skipped: ThreadMXBean.getThreadAllocatedBytes 不可用，可能在该 JVM 上没有 com.sun.management 扩展"
            writeReport(report)
            return
        }
        val regen = System.getenv("REGEN_PERF_BASELINE") == "1"

        warmup()
        val sample = BaselineSample(
            renderAllocBytesAvg = measureRenderAllocBytes(iterations = RENDER_ITERATIONS, mode = AggregateMode.AVG),
            renderAllocBytesMax = measureRenderAllocBytes(iterations = RENDER_ITERATIONS, mode = AggregateMode.MAX),
            renderTimeNanosAvg = measureRenderTimeNanos(iterations = RENDER_ITERATIONS),
            blitNanosAvg = measureBlitNanos(iterations = BLIT_ITERATIONS),
            richTextLayoutNanosByChars = mapOf(
                100 to measureRichTextLayoutNanos(charCount = 100, iterations = RICH_TEXT_ITERATIONS),
                500 to measureRichTextLayoutNanos(charCount = 500, iterations = RICH_TEXT_ITERATIONS),
                1000 to measureRichTextLayoutNanos(charCount = 1000, iterations = RICH_TEXT_ITERATIONS),
            ),
        )

        val baselineFile = File(BASELINE_PATH)
        baselineFile.parentFile?.mkdirs()

        if (regen || !baselineFile.exists()) {
            writeReport(buildJson(sample = sample))
            return
        }
        // 已有 baseline：把当前样本只打印到 stdout，不修改磁盘文件，避免 CI 静默改 baseline。
        println("[PerfBaseline] 当前样本（不写盘）：${buildJson(sample = sample)}")
    }

    /**
     * 跑一轮无关 measure 的代码路径来稳定 JIT、初始化字体缓存。
     */
    private fun warmup() {
        val widget = Row(
            children = listOf(
                Text("WARMUP", style = TextStyle.Default),
                Text("CACHE", style = TextStyle.Accent),
            ),
        )
        val runtime = PixelUiRuntime(textRasterizer = PixelBitmapFont.Default)
        repeat(WARMUP_ITERATIONS) {
            runtime.render(root = widget, logicalWidth = 64, logicalHeight = 16)
        }
        runtime.dispose()
    }

    /**
     * 每帧 render 期间该线程分配的字节数。
     */
    private fun measureRenderAllocBytes(iterations: Int, mode: AggregateMode): Long {
        val widget = standardRenderWidget()
        val runtime = PixelUiRuntime(textRasterizer = PixelBitmapFont.Default)
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

    /**
     * 每帧 render 平均耗时（纳秒）。
     */
    private fun measureRenderTimeNanos(iterations: Int): Long {
        val widget = standardRenderWidget()
        val runtime = PixelUiRuntime(textRasterizer = PixelBitmapFont.Default)
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

    /**
     * PixelBuffer.blit 单次平均耗时（纳秒）。
     */
    private fun measureBlitNanos(iterations: Int): Long {
        val source = PixelBuffer(width = 32, height = 32)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                source.setPixel(x, y, PixelTone.ON.value)
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

    /**
     * RichText 在指定字符数下一次完整 render（layout + paint）的耗时（纳秒）。
     */
    private fun measureRichTextLayoutNanos(charCount: Int, iterations: Int): Long {
        val text = buildString { repeat(charCount) { append(if (it % 5 == 0) ' ' else 'X') } }
        val widget = RichText(
            spans = listOf(
                PixelTextSpan(text = text, style = TextStyle.Default),
            ),
            softWrap = true,
        )
        val runtime = PixelUiRuntime(textRasterizer = PixelBitmapFont.Default)
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
                Text("RIGHT", style = TextStyle.Accent),
            ),
        )
    }

    /**
     * 把基线数据格式化为简易 JSON 字符串。手写避免拉测试期 JSON 库依赖。
     */
    private fun buildJson(sample: BaselineSample): String {
        val richTextEntries = sample.richTextLayoutNanosByChars.entries
            .sortedBy { it.key }
            .joinToString(separator = ",") { (chars, nanos) ->
                "\"$chars\":$nanos"
            }
        return buildString {
            append("{\n")
            append("  \"capturedAtEpochMs\": ").append(System.currentTimeMillis()).append(",\n")
            append("  \"jvmVersion\": \"").append(System.getProperty("java.version")).append("\",\n")
            append("  \"renderAllocBytesAvg\": ").append(sample.renderAllocBytesAvg).append(",\n")
            append("  \"renderAllocBytesMax\": ").append(sample.renderAllocBytesMax).append(",\n")
            append("  \"renderTimeNanosAvg\": ").append(sample.renderTimeNanosAvg).append(",\n")
            append("  \"blitNanosAvg\": ").append(sample.blitNanosAvg).append(",\n")
            append("  \"richTextLayoutNanosByChars\": {").append(richTextEntries).append("}\n")
            append("}\n")
        }
    }

    private fun writeReport(content: String) {
        val target = File(BASELINE_PATH)
        target.parentFile?.mkdirs()
        target.writeText(content)
    }

    private fun isAllocationTrackingAvailable(): Boolean {
        return allocationProbe != null
    }

    private fun currentThreadAllocatedBytes(threadId: Long): Long {
        val probe = allocationProbe ?: return 0L
        return try {
            (probe.method.invoke(probe.bean, threadId) as Long).coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * 描述线程级分配字节统计的反射入口，初始化时会一次性启用统计开关，
     * 避免每次采样都走 enable 路径。
     */
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

    companion object {
        private const val WARMUP_ITERATIONS = 20
        private const val RENDER_ITERATIONS = 50
        private const val BLIT_ITERATIONS = 1_000
        private const val RICH_TEXT_ITERATIONS = 20
        private const val BASELINE_PATH = "src/test/resources/perf-baseline.json"
    }
}
