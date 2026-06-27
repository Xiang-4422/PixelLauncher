package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class PixelBufferPoolTest {
    @Test
    fun acquireReusesReleasedBufferAndClearsPixels() {
        val pool = PixelBufferPool()
        val first = pool.acquire(width = 4, height = 3)
        first.setPixel(1, 1, PixelColor.White)

        pool.release(first)
        val second = pool.acquire(width = 4, height = 3)

        assertSame(first, second)
        assertEquals(PixelColor.Transparent, second.getPixel(1, 1))
        assertEquals(1L, pool.stats().hits)
        assertEquals(1L, pool.stats().misses)
    }

    @Test
    fun differentSizesUseDifferentBuckets() {
        val pool = PixelBufferPool()
        val first = pool.acquire(width = 4, height = 3)
        pool.release(first)

        val second = pool.acquire(width = 5, height = 3)

        assertNotSame(first, second)
        assertEquals(2L, pool.stats().misses)
        assertEquals(1, pool.stats().cached)
    }

    @Test
    fun releaseHonorsBucketLimit() {
        val pool = PixelBufferPool(maxBuffersPerKey = 1)
        val first = PixelBuffer(width = 4, height = 3)
        val second = PixelBuffer(width = 4, height = 3)

        pool.release(first)
        pool.release(second)

        assertEquals(1, pool.stats().cached)
    }

    @Test
    fun zeroCapacityDoesNotCreateEmptyBucket() {
        val pool = PixelBufferPool(maxBuffersPerKey = 0)

        pool.release(PixelBuffer(width = 4, height = 3))

        assertEquals(0, pool.stats().buckets)
        assertEquals(0, pool.stats().cached)
    }

    @Test
    fun clearDropsCachedBuffersAndStats() {
        val pool = PixelBufferPool()
        val first = pool.acquire(width = 4, height = 3)
        pool.release(first)
        pool.acquire(width = 4, height = 3)

        pool.clear()

        assertEquals(PixelBufferPoolStats(buckets = 0, cached = 0, hits = 0L, misses = 0L), pool.stats())
    }
}
