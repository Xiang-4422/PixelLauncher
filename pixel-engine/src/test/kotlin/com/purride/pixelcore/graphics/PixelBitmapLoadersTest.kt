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
}
