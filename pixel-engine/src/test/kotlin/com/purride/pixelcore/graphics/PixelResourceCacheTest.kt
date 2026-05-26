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
    fun removeAllowsResourcesToReload() {
        val cache = PixelResourceCache()
        var bitmapLoads = 0
        var sheetLoads = 0
        val firstBitmap = cache.getBitmap("runner") { bitmapLoads += 1; bitmap(PixelColor.White) }
        val firstSheet = cache.getSpriteSheet("runner") { sheetLoads += 1; sheet(PixelColor.White) }

        cache.remove("runner")

        val secondBitmap = cache.getBitmap("runner") { bitmapLoads += 1; bitmap(PixelColor.Black) }
        val secondSheet = cache.getSpriteSheet("runner") { sheetLoads += 1; sheet(PixelColor.Black) }

        assertEquals(2, bitmapLoads)
        assertEquals(2, sheetLoads)
        assertEquals(PixelColor.White.argb, firstBitmap.pixels[0])
        assertEquals(PixelColor.Black.argb, secondBitmap.pixels[0])
        assertEquals(PixelColor.White.argb, firstSheet.bitmap.pixels[0])
        assertEquals(PixelColor.Black.argb, secondSheet.bitmap.pixels[0])
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

    @Test
    fun bitmapAndSpriteSheetKeysAreIndependent() {
        val cache = PixelResourceCache()
        cache.getBitmap("shared") { bitmap(PixelColor.White) }
        cache.getSpriteSheet("other") { sheet(PixelColor.Black) }

        cache.remove("shared")

        assertEquals(0, cache.bitmapCount)
        assertEquals(1, cache.spriteSheetCount)
    }

    private fun bitmap(color: PixelColor = PixelColor.White): PixelBitmap {
        return PixelBitmap(width = 1, height = 1, pixels = intArrayOf(color.argb))
    }

    private fun sheet(color: PixelColor = PixelColor.White): PixelSpriteSheet = PixelSpriteSheet(
        bitmap = bitmap(color),
        frames = listOf(PixelBitmapRegion(0, 0, 1, 1)),
    )
}
