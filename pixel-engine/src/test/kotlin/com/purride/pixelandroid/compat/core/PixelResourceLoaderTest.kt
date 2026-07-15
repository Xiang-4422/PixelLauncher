package com.purride.pixelcore

import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证正式资源加载 API 的线程、去重、取消、预取和失败缓存契约。 */
class PixelResourceLoaderTest {
    /** 同步 API 默认拒绝主线程，且拒绝发生在实际 loader 之前。 */
    @Test
    fun synchronousLoadRejectsMainThreadBeforeIo() {
        /** 实际 loader 执行次数。 */
        val loads = AtomicInteger()
        /** 使用直接 executor 的加载器。 */
        val loader = PixelResourceLoader(PixelResourceCache(), directExecutor())
        loader.mainThreadProbe = { true }

        /** 捕获同步主线程错误。 */
        val error = expectThrows<IllegalStateException> {
            loader.loadBitmap("icon") {
                loads.incrementAndGet()
                bitmap()
            }
        }

        assertTrue(error.message.orEmpty().contains("main thread"))
        assertEquals(0, loads.get())
    }

    /** 相同类型/key 的异步订阅必须共享一次 executor 工作。 */
    @Test
    fun asyncLoadsShareOneBackgroundTask() {
        /** 最终保存结果的缓存。 */
        val cache = PixelResourceCache()
        /** 单线程后台 executor。 */
        val executor = Executors.newSingleThreadExecutor()
        /** loader 开始信号。 */
        val started = CountDownLatch(1)
        /** loader 释放信号。 */
        val release = CountDownLatch(1)
        /** 实际执行次数。 */
        val loads = AtomicInteger()
        /** 共享对象。 */
        val expected = bitmap(PixelColor.fromRgb(255, 0, 0))
        /** 被测试加载器。 */
        val resourceLoader = PixelResourceLoader(cache, executor)

        try {
            /** 第一个订阅。 */
            val first = resourceLoader.loadBitmapAsync("shared") {
                loads.incrementAndGet()
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS)) { "loader release timed out" }
                expected
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            /** 第二个订阅不得再次执行其 loader。 */
            val second = resourceLoader.loadBitmapAsync("shared") {
                error("deduplicated loader must not run")
            }

            assertEquals(1, resourceLoader.snapshot().inFlightCount)
            release.countDown()
            assertSame(expected, first.await())
            assertSame(expected, second.await())
            assertEquals(1, loads.get())
            assertEquals(1, cache.bitmapCount)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    /** 取消一个订阅不得取消共享 IO、其他订阅或最终缓存写入。 */
    @Test
    fun cancellingSubscriberDoesNotCancelSharedLoad() {
        /** 最终保存结果的缓存。 */
        val cache = PixelResourceCache()
        /** 单线程后台 executor。 */
        val executor = Executors.newSingleThreadExecutor()
        /** loader 开始信号。 */
        val started = CountDownLatch(1)
        /** loader 释放信号。 */
        val release = CountDownLatch(1)
        /** 共享对象。 */
        val expected = bitmap(PixelColor.fromRgb(255, 0, 0))
        /** 被测试加载器。 */
        val resourceLoader = PixelResourceLoader(cache, executor)

        try {
            /** 会被当前调用方取消的订阅。 */
            val cancelled = resourceLoader.loadBitmapAsync("shared") {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS)) { "loader release timed out" }
                expected
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            /** 仍需正常接收共享结果的订阅。 */
            val survivor = resourceLoader.loadBitmapAsync("shared") {
                error("deduplicated loader must not run")
            }

            assertTrue(cancelled.cancel())
            assertTrue(cancelled.isCancelled)
            release.countDown()
            assertSame(expected, survivor.await())
            expectThrows<CancellationException> { cancelled.await() }
            assertEquals(1, cache.bitmapCount)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    /** 预取必须执行真实后台加载并把结果填入共享缓存。 */
    @Test
    fun prefetchPopulatesSharedCache() {
        /** 共享缓存。 */
        val cache = PixelResourceCache()
        /** 后台 executor。 */
        val executor = Executors.newSingleThreadExecutor()
        /** 实际执行次数。 */
        val loads = AtomicInteger()
        /** 被测试加载器。 */
        val resourceLoader = PixelResourceLoader(cache, executor)

        try {
            resourceLoader.prefetchBitmap("prefetched") {
                loads.incrementAndGet()
                bitmap()
            }.await()

            /** 同步缓存命中不得执行备用 loader。 */
            val cached = cache.getBitmap("prefetched") { error("prefetch must populate cache") }
            assertEquals(PixelColor.White.argb, cached.pixelAt(0, 0))
            assertEquals(1, loads.get())
        } finally {
            executor.shutdownNow()
        }
    }

    /** 短期失败缓存必须阻止重试风暴，并在单调时钟过期后允许重试。 */
    @Test
    fun failureCacheSuppressesRetriesUntilExpiry() {
        /** 可控的单调时钟纳秒值。 */
        var nowNanos = 0L
        /** 实际 loader 尝试次数。 */
        val attempts = AtomicInteger()
        /** 一秒失败缓存策略。 */
        val resourceLoader = PixelResourceLoader(
            cache = PixelResourceCache(),
            executor = directExecutor(),
            policy = PixelResourceLoadingPolicy(failureCacheDurationMillis = 1_000L),
        )
        resourceLoader.nanoTimeProvider = { nowNanos }

        /** 首次真实失败。 */
        val first = expectThrows<IllegalArgumentException> {
            resourceLoader.loadBitmapAsync("flaky") {
                attempts.incrementAndGet()
                throw IllegalArgumentException("broken resource")
            }.await()
        }
        /** 过期前应直接复用失败。 */
        val second = expectThrows<IllegalArgumentException> {
            resourceLoader.loadBitmapAsync("flaky") {
                attempts.incrementAndGet()
                bitmap()
            }.await()
        }

        assertEquals("broken resource", first.message)
        assertEquals("broken resource", second.message)
        assertEquals(1, attempts.get())
        assertEquals(1, resourceLoader.snapshot().cachedFailureCount)

        nowNanos = TimeUnit.SECONDS.toNanos(2L)
        /** 过期后的成功结果。 */
        val recovered = resourceLoader.loadBitmapAsync("flaky") {
            attempts.incrementAndGet()
            bitmap(PixelColor.fromRgb(255, 0, 0))
        }.await()

        assertEquals(PixelColor.fromRgb(255, 0, 0).argb, recovered.pixelAt(0, 0))
        assertEquals(2, attempts.get())
        assertEquals(0, resourceLoader.snapshot().cachedFailureCount)
    }

    /** 显式清理失败记录后必须立即重试，无需等待 TTL。 */
    @Test
    fun clearFailureAllowsImmediateRetry() {
        /** 实际 loader 尝试次数。 */
        val attempts = AtomicInteger()
        /** 使用长失败 TTL 的加载器。 */
        val resourceLoader = PixelResourceLoader(
            cache = PixelResourceCache(),
            executor = directExecutor(),
            policy = PixelResourceLoadingPolicy(failureCacheDurationMillis = 60_000L),
        )

        expectThrows<IllegalStateException> {
            resourceLoader.loadBitmapAsync("retry") {
                attempts.incrementAndGet()
                error("first failure")
            }.await()
        }
        resourceLoader.clearFailure(PixelResourceKind.BITMAP, "retry")
        /** 清理失败后返回的恢复结果。 */
        val recovered = resourceLoader.loadBitmapAsync("retry") {
            attempts.incrementAndGet()
            bitmap()
        }.await()

        assertEquals(PixelColor.White.argb, recovered.pixelAt(0, 0))
        assertEquals(2, attempts.get())
    }

    /** 未完成句柄禁止在主线程 await，完成后允许无阻塞读取。 */
    @Test
    fun awaitRejectsOnlyIncompleteMainThreadWait() {
        /** 暂存 executor 收到但尚未执行的任务。 */
        var pending: Runnable? = null
        /** 只排队、不主动执行的 executor。 */
        val queuedExecutor = Executor { command -> pending = command }
        /** 被测试加载器。 */
        val resourceLoader = PixelResourceLoader(PixelResourceCache(), queuedExecutor)
        resourceLoader.mainThreadProbe = { true }
        /** 尚未完成的异步句柄。 */
        val handle = resourceLoader.loadBitmapAsync("queued") { bitmap() }

        assertFalse(handle.isDone)
        assertTrue(
            expectThrows<IllegalStateException> { handle.await() }
                .message.orEmpty().contains("main thread"),
        )
        pending?.run() ?: error("executor did not receive task")
        assertTrue(handle.isDone)
        assertEquals(PixelColor.White.argb, handle.await().pixelAt(0, 0))
    }

    /** executor 拒绝任务时，句柄必须确定性失败且清理在途记录。 */
    @Test
    fun rejectedExecutorCompletesHandleExceptionally() {
        /** 始终拒绝任务的 executor。 */
        val rejectingExecutor = Executor { throw RejectedExecutionException("closed") }
        /** 被测试加载器。 */
        val resourceLoader = PixelResourceLoader(PixelResourceCache(), rejectingExecutor)
        /** 传播给调用方的拒绝异常。 */
        val error = expectThrows<RejectedExecutionException> {
            resourceLoader.loadBitmapAsync("icon") { bitmap() }.await()
        }

        assertEquals("closed", error.message)
        assertEquals(0, resourceLoader.snapshot().inFlightCount)
    }

    /** 创建在当前线程立即执行任务的测试 executor。 */
    private fun directExecutor(): Executor = Executor(Runnable::run)

    /** 构造单像素测试 bitmap。 */
    private fun bitmap(color: PixelColor = PixelColor.White): PixelBitmap {
        return PixelBitmap(width = 1, height = 1, pixels = intArrayOf(color.argb))
    }

    /** 执行代码并返回指定类型异常。 */
    private inline fun <reified T : Throwable> expectThrows(block: () -> Unit): T {
        return try {
            block()
            error("Expected ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            if (error is T) error else throw error
        }
    }
}
