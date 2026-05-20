package com.purride.pixelcore

/**
 * PixelBuffer 池。
 *
 * pixel-engine 的渲染主线程为单线程（PixelHostView.onDraw），所以池不需要
 * 跨线程同步。租用的 buffer 在 paint() 或 host 帧生命周期内归还，避免
 * 每帧重新分配内存给 GC。
 *
 * Mono 和 Color 使用两个独立的 HashMap，确保不同模式的 buffer 不会混用。
 * 池按 (width, height) 分桶；每个桶最多保留 [maxBuffersPerKey] 个 buffer，
 * 防止内存无限增长。
 */
public class PixelBufferPool(
    private val maxBuffersPerKey: Int = DEFAULT_MAX_BUFFERS_PER_KEY,
) {
    private val monoPools = HashMap<Long, ArrayDeque<MonoPixelBuffer>>()
    private val colorPools = HashMap<Long, ArrayDeque<ColorPixelBuffer>>()
    private var hits: Long = 0L
    private var misses: Long = 0L

    /**
     * 取一个指定尺寸的 Mono buffer；命中时复用并清零，未命中时新建。
     */
    public fun acquireMono(width: Int, height: Int): MonoPixelBuffer {
        val safeWidth = width.coerceAtLeast(0)
        val safeHeight = height.coerceAtLeast(0)
        val key = packKey(safeWidth, safeHeight)
        val bucket = monoPools[key]
        val cached = bucket?.removeLastOrNull()
        if (cached != null) {
            cached.clear()
            hits += 1L
            return cached
        }
        misses += 1L
        return MonoPixelBuffer(width = safeWidth, height = safeHeight)
    }

    /**
     * 取一个指定尺寸的 Color buffer；命中时复用并清零，未命中时新建。
     */
    public fun acquireColor(width: Int, height: Int): ColorPixelBuffer {
        val safeWidth = width.coerceAtLeast(0)
        val safeHeight = height.coerceAtLeast(0)
        val key = packKey(safeWidth, safeHeight)
        val bucket = colorPools[key]
        val cached = bucket?.removeLastOrNull()
        if (cached != null) {
            cached.clear()
            hits += 1L
            return cached
        }
        misses += 1L
        return ColorPixelBuffer(width = safeWidth, height = safeHeight)
    }

    /**
     * 兼容过渡：等价于 [acquireMono]。
     *
     * 仅供迁移期间的调用方使用，新代码请直接调用 [acquireMono] 或 [acquireColor]。
     */
    @Deprecated("Use acquireMono() explicitly", ReplaceWith("acquireMono(width, height)"))
    public fun acquire(width: Int, height: Int): MonoPixelBuffer = acquireMono(width, height)

    /**
     * 把 buffer 归还到对应池。超过桶上限时直接丢弃。
     */
    public fun release(buffer: PixelBuffer) {
        val key = packKey(buffer.width, buffer.height)
        when (buffer) {
            is MonoPixelBuffer -> {
                val bucket = monoPools.getOrPut(key) { ArrayDeque() }
                if (bucket.size < maxBuffersPerKey) bucket.addLast(buffer)
            }
            is ColorPixelBuffer -> {
                val bucket = colorPools.getOrPut(key) { ArrayDeque() }
                if (bucket.size < maxBuffersPerKey) bucket.addLast(buffer)
            }
        }
    }

    /**
     * 清空所有桶，主要用于 runtime dispose 或测试隔离。
     */
    public fun clear() {
        monoPools.clear()
        colorPools.clear()
        hits = 0L
        misses = 0L
    }

    /**
     * 返回缓存命中统计快照。
     */
    public fun stats(): PixelBufferPoolStats {
        return PixelBufferPoolStats(
            buckets = monoPools.size + colorPools.size,
            cached = monoPools.values.sumOf { it.size } + colorPools.values.sumOf { it.size },
            hits = hits,
            misses = misses,
        )
    }

    private fun packKey(width: Int, height: Int): Long {
        return (width.toLong() shl 32) or (height.toLong() and 0xFFFFFFFFL)
    }

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
