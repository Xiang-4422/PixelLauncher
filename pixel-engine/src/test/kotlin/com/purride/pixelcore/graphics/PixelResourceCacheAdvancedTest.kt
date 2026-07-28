package com.purride.pixelcore

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证资源缓存的容量 LRU、并发单飞和清理竞态契约。 */
class PixelResourceCacheAdvancedTest {
    /** 类型字节预算必须淘汰该类型内真正最久未访问的条目。 */
    @Test
    fun typeByteBudgetEvictsLeastRecentlyUsedEntry() {
        /** 收集锁外发出的淘汰事件。 */
        val events = Collections.synchronizedList(mutableListOf<PixelResourceEviction>())
        /** 只允许两个 1x1 bitmap 的小缓存。 */
        val cache = PixelResourceCache(
            limits = limits(maxTotalBytes = 128L, maxBitmapBytes = 8L),
            evictionListener = PixelResourceEvictionListener(events::add),
        )

        cache.getBitmap("a") { bitmap(PixelColor.White) }
        cache.getBitmap("b") { bitmap(PixelColor.Black) }
        cache.getBitmap("a") { error("a should be cached") }
        cache.getBitmap("c") { bitmap(PixelColor.fromRgb(255, 0, 0)) }

        /** 淘汰后按访问序排列的剩余 key。 */
        val keys = cache.detailedSnapshot().entries.map(PixelResourceCacheEntrySnapshot::key)
        assertEquals(listOf("a", "c"), keys)
        assertEquals(1, events.size)
        assertEquals("b", events.single().key)
        assertEquals(PixelResourceEvictionReason.TYPE_BYTE_BUDGET, events.single().reason)
    }

    /** 总预算必须跨资源类型选择全局最久未访问条目。 */
    @Test
    fun totalByteBudgetEvictsOldestEntryAcrossKinds() {
        /** 记录跨类型淘汰事件。 */
        val events = mutableListOf<PixelResourceEviction>()
        /** 允许单张 sheet，但 bitmap 与 sheet 合计会超过 100 字节。 */
        val cache = PixelResourceCache(
            limits = limits(
                maxTotalBytes = 100L,
                maxBitmapBytes = 100L,
                maxSpriteSheetBytes = 100L,
            ),
            evictionListener = PixelResourceEvictionListener(events::add),
        )

        cache.getBitmap("old-bitmap") { bitmap() }
        cache.getSpriteSheet("new-sheet") { sheet() }

        /** 淘汰完成后的无引用快照。 */
        val snapshot: PixelResourceCacheDetailedSnapshot = cache.detailedSnapshot()
        assertEquals(0, snapshot.bitmapCount)
        assertEquals(1, snapshot.spriteSheetCount)
        assertEquals(100L, snapshot.totalBytes)
        assertEquals("old-bitmap", events.single().key)
        assertEquals(PixelResourceEvictionReason.TOTAL_BYTE_BUDGET, events.single().reason)
    }

    /** 单条资源过大时仍返回加载结果，但不得把它驻留在缓存。 */
    @Test
    fun oversizedEntryIsReturnedButRejectedFromCache() {
        /** 收集拒绝缓存事件。 */
        val events = mutableListOf<PixelResourceEviction>()
        /** bitmap 类型只允许四字节。 */
        val cache = PixelResourceCache(
            limits = limits(maxTotalBytes = 64L, maxBitmapBytes = 4L),
            evictionListener = PixelResourceEvictionListener(events::add),
        )

        /** 占用八字节、超过逐类预算的结果。 */
        val loaded = cache.getBitmap("wide") {
            PixelBitmap(width = 2, height = 1, pixels = intArrayOf(1, 2))
        }

        assertEquals(2, loaded.width)
        assertEquals(0, cache.bitmapCount)
        assertEquals(1L, cache.detailedSnapshot().rejectedEntryCount)
        assertEquals(PixelResourceEvictionReason.ENTRY_TOO_LARGE, events.single().reason)
    }

    /** 消费方淘汰监听器失败时缓存结果仍成功，并保持已经提交的淘汰状态。 */
    @Test
    fun evictionListenerFailureDoesNotCorruptCommittedCacheState() {
        /** 只允许两个单像素 bitmap 的缓存，用第三次写入稳定触发淘汰。 */
        val cache = PixelResourceCache(
            limits = limits(maxTotalBytes = 128L, maxBitmapBytes = 8L),
            evictionListener = PixelResourceEvictionListener { throw IllegalStateException("listener failed") },
        )

        cache.getBitmap("first") { bitmap(PixelColor.White) }
        cache.getBitmap("second") { bitmap(PixelColor.Black) }
        /** 监听器异常不得把已经完成的第三次资源加载改写成失败。 */
        val loaded = cache.getBitmap("third") { bitmap(PixelColor.fromRgb(255, 0, 0)) }

        assertEquals(PixelColor.fromRgb(255, 0, 0).argb, loaded.pixels.single())
        assertEquals(listOf("second", "third"), cache.detailedSnapshot().entries.map { entry -> entry.key })
        assertEquals(1L, cache.detailedSnapshot().evictionCount)
    }

    /** 多线程并发未命中同一 key 时 loader 只执行一次且共享同一实例。 */
    @Test
    fun concurrentMissesShareExactlyOneLoader() {
        /** 并发调用数量。 */
        val callerCount = 8
        /** 被测试缓存。 */
        val cache = PixelResourceCache()
        /** 固定大小调用线程池。 */
        val executor = Executors.newFixedThreadPool(callerCount)
        /** 让所有调用尽量同时进入缓存。 */
        val startGate = CountDownLatch(1)
        /** 唯一 loader 已经开始的信号。 */
        val loaderStarted = CountDownLatch(1)
        /** 控制 loader 完成时机。 */
        val releaseLoader = CountDownLatch(1)
        /** 实际 loader 执行次数。 */
        val loads = AtomicInteger()
        /** 预期由所有调用共享的 bitmap。 */
        val expected = bitmap(PixelColor.fromRgb(255, 0, 0))
        /** 并发提交的返回任务。 */
        val futures = List(callerCount) {
            executor.submit<PixelBitmap> {
                startGate.await()
                cache.getBitmap("shared") {
                    loads.incrementAndGet()
                    loaderStarted.countDown()
                    check(releaseLoader.await(5, TimeUnit.SECONDS)) { "loader release timed out" }
                    expected
                }
            }
        }

        try {
            startGate.countDown()
            assertTrue(loaderStarted.await(5, TimeUnit.SECONDS))
            releaseLoader.countDown()
            futures.forEach { future -> assertSame(expected, future.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, loads.get())
            assertEquals(1, cache.bitmapCount)
        } finally {
            releaseLoader.countDown()
            executor.shutdownNow()
        }
    }

    /** clear 与加载并发时，旧任务结果可以返回调用方但不得重新写回缓存。 */
    @Test
    fun clearPreventsInFlightResultFromRepopulatingCache() {
        /** 被测试缓存。 */
        val cache = PixelResourceCache()
        /** 执行阻塞 loader 的单线程池。 */
        val executor = Executors.newSingleThreadExecutor()
        /** loader 已经开始的信号。 */
        val loaderStarted = CountDownLatch(1)
        /** 允许 loader 返回的信号。 */
        val releaseLoader = CountDownLatch(1)
        /** loader 最终返回的对象。 */
        val expected = bitmap(PixelColor.fromRgb(255, 0, 0))
        /** 在后台执行的缓存读取。 */
        val future = executor.submit<PixelBitmap> {
            cache.getBitmap("stale") {
                loaderStarted.countDown()
                check(releaseLoader.await(5, TimeUnit.SECONDS)) { "loader release timed out" }
                expected
            }
        }

        try {
            assertTrue(loaderStarted.await(5, TimeUnit.SECONDS))
            cache.clear()
            releaseLoader.countDown()
            assertSame(expected, future.get(5, TimeUnit.SECONDS))
            assertEquals(0, cache.bitmapCount)
            assertEquals(0, cache.detailedSnapshot().inFlightLoadCount)
        } finally {
            releaseLoader.countDown()
            executor.shutdownNow()
        }
    }

    /** 构造满足其余资源类型预算的测试限额。 */
    private fun limits(
        maxTotalBytes: Long,
        maxBitmapBytes: Long,
        maxSpriteSheetBytes: Long = 1_024L,
    ): PixelResourceCacheLimits = PixelResourceCacheLimits(
        maxTotalBytes = maxTotalBytes,
        maxBitmapBytes = maxBitmapBytes,
        maxSpriteSheetBytes = maxSpriteSheetBytes,
        maxGlyphPackBytes = 1_024L,
        maxBitmapEntries = 32,
        maxSpriteSheetEntries = 32,
        maxGlyphPackEntries = 32,
    )

    /** 构造单像素测试 bitmap。 */
    private fun bitmap(color: PixelColor = PixelColor.White): PixelBitmap {
        return PixelBitmap(width = 1, height = 1, pixels = intArrayOf(color.argb))
    }

    /** 构造估算大小恰好为 100 字节的单帧 sheet。 */
    private fun sheet(): PixelSpriteSheet = PixelSpriteSheet(
        bitmap = bitmap(),
        frames = listOf(PixelBitmapRegion(0, 0, 1, 1)),
    )
}
