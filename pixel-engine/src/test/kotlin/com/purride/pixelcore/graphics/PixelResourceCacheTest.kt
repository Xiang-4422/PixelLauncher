package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PixelResourceCacheTest {
    @Test
    fun getBitmapCachesByKey() {
        val cache = PixelResourceCache()
        var loads = 0

        val first = cache.getBitmap("icon") { loads += 1; bitmap() }
        val second = cache.getBitmap("icon") { loads += 1; bitmap() }

        assertSame(first, second)
        assertEquals(1, loads)
        assertEquals(1, cache.bitmapCount)
    }

    @Test
    fun removeClearsBitmapAndSpriteSheetForKey() {
        val cache = PixelResourceCache()
        cache.getBitmap("runner") { bitmap() }
        cache.getSpriteSheet("runner") { sheet() }

        cache.remove("runner")

        assertEquals(0, cache.bitmapCount)
        assertEquals(0, cache.spriteSheetCount)
    }

    @Test
    fun clearRemovesAllResources() {
        val cache = PixelResourceCache()
        cache.getBitmap("a") { bitmap() }
        cache.getSpriteSheet("b") { sheet() }

        cache.clear()

        assertEquals(0, cache.bitmapCount)
        assertEquals(0, cache.spriteSheetCount)
    }

    private fun bitmap(): PixelBitmap = PixelBitmap(width = 1, height = 1, pixels = intArrayOf(PixelColor.White.argb))

    private fun sheet(): PixelSpriteSheet = PixelSpriteSheet(
        bitmap = bitmap(),
        frames = listOf(PixelBitmapRegion(0, 0, 1, 1)),
    )
}
