package com.purride.pixelcore

/**
 * PixelBuffer 对象池。
 *
 * 渲染主线程为单线程（PixelHostView.onDraw），池不需要跨线程同步。
 * 按 (width, height) 分桶；每个桶最多保留 [maxBuffersPerKey] 个 buffer。
 */
public class PixelBufferPool(
    private val maxBuffersPerKey: Int = DEFAULT_MAX_BUFFERS_PER_KEY,
) {
    private val pool = HashMap<Long, ArrayDeque<PixelBuffer>>()
    private var hits: Long = 0L
    private var misses: Long = 0L

    /**
     * 取一个指定尺寸的 buffer；命中时复用并清零，未命中时新建。
     */
    public fun acquire(width: Int, height: Int): PixelBuffer {
        val safeWidth = width.coerceAtLeast(0)
        val safeHeight = height.coerceAtLeast(0)
        val key = packKey(safeWidth, safeHeight)
        val cached = pool[key]?.removeLastOrNull()
        if (cached != null) {
            cached.clear()
            hits += 1L
            return cached
        }
        misses += 1L
        return PixelBuffer(width = safeWidth, height = safeHeight)
    }

    /**
     * 把 buffer 归还到池。超过桶上限时直接丢弃。
     */
    public fun release(buffer: PixelBuffer) {
        if (maxBuffersPerKey <= 0) return
        val key = packKey(buffer.width, buffer.height)
        val bucket = pool.getOrPut(key) { ArrayDeque() }
        if (bucket.size < maxBuffersPerKey) bucket.addLast(buffer)
    }

    /**
     * 清空所有桶，主要用于 runtime dispose 或测试隔离。
     */
    public fun clear() {
        pool.clear()
        hits = 0L
        misses = 0L
    }

    /**
     * 返回缓存命中统计快照。
     */
    public fun stats(): PixelBufferPoolStats = PixelBufferPoolStats(
        buckets = pool.size,
        cached = pool.values.sumOf { it.size },
        hits = hits,
        misses = misses,
    )

    private fun packKey(width: Int, height: Int): Long =
        (width.toLong() shl 32) or (height.toLong() and 0xFFFFFFFFL)

    /** 集中提供 `PixelBufferPool` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        private const val DEFAULT_MAX_BUFFERS_PER_KEY = 4
    }
}

/**
 * PixelBufferPool 的统计快照，供测试和 diagnostics 使用。
 */
public data class PixelBufferPoolStats(
    val buckets: Int,
    val cached: Int,
    val hits: Long,
    val misses: Long,
) {
    val total: Long get() = hits + misses
    val hitRate: Double get() = if (total == 0L) 0.0 else hits.toDouble() / total.toDouble()
}
