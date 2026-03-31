package com.purride.pixelcore

import java.util.Locale

/**
 * 聚合渲染链路中的性能日志，避免逐帧刷屏。
 *
 * 默认按固定时间窗口汇总每个阶段的调用次数、平均耗时和最大耗时，
 * 便于快速判断瓶颈位于主线程整帧渲染、Idle 物理更新，还是 GL 上传/绘制阶段。
 */
object RenderPerfLogger {
    private const val TAG = "RenderPerf"
    private const val REPORT_WINDOW_MS = 2_000L
    private const val enabled = true
    private val lock = Any()
    private val statsByStage = linkedMapOf<String, StageStats>()
    private var lastFlushUptimeMs = nowMs()

    fun mark(event: String, detail: String) {
        if (!enabled) {
            return
        }
        emit("$event | $detail")
    }

    fun <T> measure(stage: String, block: () -> T): T {
        if (!enabled) {
            return block()
        }
        val startNs = nowNs()
        return try {
            block()
        } finally {
            record(stage, nowNs() - startNs)
        }
    }

    fun record(stage: String, durationNs: Long) {
        if (!enabled) {
            return
        }
        val safeDurationNs = durationNs.coerceAtLeast(0L)
        val now = nowMs()
        synchronized(lock) {
            val stats = statsByStage.getOrPut(stage) { StageStats() }
            stats.count += 1
            stats.totalDurationNs += safeDurationNs
            if (safeDurationNs > stats.maxDurationNs) {
                stats.maxDurationNs = safeDurationNs
            }
            maybeFlushLocked(now)
        }
    }

    private fun maybeFlushLocked(nowUptimeMs: Long) {
        if (nowUptimeMs - lastFlushUptimeMs < REPORT_WINDOW_MS || statsByStage.isEmpty()) {
            return
        }
        val windowMs = (nowUptimeMs - lastFlushUptimeMs).coerceAtLeast(1L)
        val summary = statsByStage.entries.joinToString(separator = " | ") { (stage, stats) ->
            val avgMs = stats.totalDurationNs.toDouble() / stats.count.toDouble() / 1_000_000.0
            val maxMs = stats.maxDurationNs.toDouble() / 1_000_000.0
            val hz = stats.count.toDouble() * 1000.0 / windowMs.toDouble()
            String.format(
                Locale.US,
                "%s count=%d avg=%.2fms max=%.2fms rate=%.1f/s",
                stage,
                stats.count,
                avgMs,
                maxMs,
                hz,
            )
        }
        emit("window=${windowMs}ms | $summary")
        statsByStage.clear()
        lastFlushUptimeMs = nowUptimeMs
    }

    private fun nowNs(): Long = System.nanoTime()

    private fun nowMs(): Long = nowNs() / 1_000_000L

    private fun emit(message: String) {
        runCatching {
            val logClass = Class.forName("android.util.Log")
            val infoMethod = logClass.getMethod("i", String::class.java, String::class.java)
            infoMethod.invoke(null, TAG, message)
        }.getOrElse {
            println("$TAG: $message")
        }
    }

    private data class StageStats(
        var count: Int = 0,
        var totalDurationNs: Long = 0L,
        var maxDurationNs: Long = 0L,
    )
}
