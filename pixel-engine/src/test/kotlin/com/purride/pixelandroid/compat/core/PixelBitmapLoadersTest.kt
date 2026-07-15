package com.purride.pixelcore

import org.junit.Assert.assertTrue
import org.junit.Test

class PixelBitmapLoadersTest {
    @Test
    fun loadExceptionIncludesSourceInMessage() {
        val error = PixelBitmapLoadException("Failed to decode PNG asset 'missing.png': file not found")

        assertTrue(error.message.orEmpty().contains("missing.png"))
        assertTrue(error.message.orEmpty().contains("PNG asset"))
    }

    /** 解码上限对象必须保留调用方提供的安全预算。 */
    @Test
    fun decodeLimitsExposeReviewedBudgets() {
        /** 用于小资源测试的显式解码预算。 */
        val limits = PixelBitmapDecodeLimits(
            maxEncodedBytes = 1_024,
            maxDimension = 64,
            maxPixels = 4_096L,
        )

        org.junit.Assert.assertEquals(1_024, limits.maxEncodedBytes)
        org.junit.Assert.assertEquals(64, limits.maxDimension)
        org.junit.Assert.assertEquals(4_096L, limits.maxPixels)
    }
}
