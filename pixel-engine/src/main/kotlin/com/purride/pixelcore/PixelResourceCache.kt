package com.purride.pixelcore

public data class PixelResourceCacheSnapshot(
    val bitmapCount: Int,
    val spriteSheetCount: Int,
    val bitmapHits: Int,
    val bitmapMisses: Int,
    val spriteSheetHits: Int,
    val spriteSheetMisses: Int,
    val removeCount: Int,
    val clearCount: Int,
)

public class PixelResourceCache {
    private val bitmaps: MutableMap<String, PixelBitmap> = linkedMapOf()
    private val spriteSheets: MutableMap<String, PixelSpriteSheet> = linkedMapOf()
    private var bitmapHits = 0
    private var bitmapMisses = 0
    private var spriteSheetHits = 0
    private var spriteSheetMisses = 0
    private var removeCount = 0
    private var clearCount = 0

    public fun getBitmap(key: String, loader: () -> PixelBitmap): PixelBitmap {
        val safeKey = requireKey(key)
        bitmaps[safeKey]?.let { cached ->
            bitmapHits += 1
            return cached
        }
        bitmapMisses += 1
        return loader().also { bitmaps[safeKey] = it }
    }

    public fun getSpriteSheet(key: String, loader: () -> PixelSpriteSheet): PixelSpriteSheet {
        val safeKey = requireKey(key)
        spriteSheets[safeKey]?.let { cached ->
            spriteSheetHits += 1
            return cached
        }
        spriteSheetMisses += 1
        return loader().also { spriteSheets[safeKey] = it }
    }

    public fun remove(key: String) {
        val safeKey = requireKey(key)
        val removedBitmap = bitmaps.remove(safeKey) != null
        val removedSpriteSheet = spriteSheets.remove(safeKey) != null
        if (removedBitmap || removedSpriteSheet) removeCount += 1
    }

    public fun clear() {
        if (bitmaps.isNotEmpty() || spriteSheets.isNotEmpty()) {
            clearCount += 1
            bitmaps.clear()
            spriteSheets.clear()
        }
    }

    public fun snapshot(): PixelResourceCacheSnapshot {
        return PixelResourceCacheSnapshot(
            bitmapCount = bitmapCount,
            spriteSheetCount = spriteSheetCount,
            bitmapHits = bitmapHits,
            bitmapMisses = bitmapMisses,
            spriteSheetHits = spriteSheetHits,
            spriteSheetMisses = spriteSheetMisses,
            removeCount = removeCount,
            clearCount = clearCount,
        )
    }

    public val bitmapCount: Int
        get() = bitmaps.size

    public val spriteSheetCount: Int
        get() = spriteSheets.size

    private fun requireKey(key: String): String {
        require(key.isNotBlank()) { "resource cache key must not be blank" }
        return key
    }
}
