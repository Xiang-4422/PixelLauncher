package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    @Test
    fun snapshotTracksHitsMissesAndLifecycleEvents() {
        val cache = PixelResourceCache()

        cache.getBitmap("icon") { bitmap() }
        cache.getBitmap("icon") { bitmap(PixelColor.Black) }
        cache.getSpriteSheet("runner") { sheet() }
        cache.getSpriteSheet("runner") { sheet(PixelColor.Black) }

        var snapshot = cache.snapshot()
        assertEquals(1, snapshot.bitmapCount)
        assertEquals(1, snapshot.spriteSheetCount)
        assertEquals(1, snapshot.bitmapHits)
        assertEquals(1, snapshot.bitmapMisses)
        assertEquals(1, snapshot.spriteSheetHits)
        assertEquals(1, snapshot.spriteSheetMisses)
        assertEquals(0, snapshot.removeCount)
        assertEquals(0, snapshot.clearCount)

        cache.remove("missing")
        cache.remove("icon")
        cache.clear()
        cache.clear()

        snapshot = cache.snapshot()
        assertEquals(0, snapshot.bitmapCount)
        assertEquals(0, snapshot.spriteSheetCount)
        assertEquals(1, snapshot.removeCount)
        assertEquals(1, snapshot.clearCount)
    }

    @Test
    fun blankKeysAreRejectedBeforeLoaderRuns() {
        val cache = PixelResourceCache()
        var loads = 0

        try {
            cache.getBitmap(" ") {
                loads += 1
                bitmap()
            }
            error("blank key should fail")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("key"))
        }

        assertEquals(0, loads)
        assertEquals(0, cache.bitmapCount)
    }

    private fun bitmap(color: PixelColor = PixelColor.White): PixelBitmap {
        return PixelBitmap(width = 1, height = 1, pixels = intArrayOf(color.argb))
    }

    private fun sheet(color: PixelColor = PixelColor.White): PixelSpriteSheet = PixelSpriteSheet(
        bitmap = bitmap(color),
        frames = listOf(PixelBitmapRegion(0, 0, 1, 1)),
    )
}
