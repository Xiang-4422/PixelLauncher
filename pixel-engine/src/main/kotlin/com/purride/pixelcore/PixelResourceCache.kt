package com.purride.pixelcore

public class PixelResourceCache {
    private val bitmaps: MutableMap<String, PixelBitmap> = linkedMapOf()
    private val spriteSheets: MutableMap<String, PixelSpriteSheet> = linkedMapOf()

    public fun getBitmap(key: String, loader: () -> PixelBitmap): PixelBitmap {
        return bitmaps.getOrPut(requireKey(key), loader)
    }

    public fun getSpriteSheet(key: String, loader: () -> PixelSpriteSheet): PixelSpriteSheet {
        return spriteSheets.getOrPut(requireKey(key), loader)
    }

    public fun remove(key: String) {
        val safeKey = requireKey(key)
        bitmaps.remove(safeKey)
        spriteSheets.remove(safeKey)
    }

    public fun clear() {
        bitmaps.clear()
        spriteSheets.clear()
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
