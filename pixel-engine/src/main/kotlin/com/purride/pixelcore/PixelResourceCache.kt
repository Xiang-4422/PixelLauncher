package com.purride.pixelcore

/** 资源缓存计数和命中率快照，用于 diagnostics 与测试断言。 */
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

/** 小型内存资源缓存，按调用方提供的 key 复用 bitmap 与 sprite sheet。 */
public class PixelResourceCache {
    private val bitmaps: MutableMap<String, PixelBitmap> = linkedMapOf()
    private val spriteSheets: MutableMap<String, PixelSpriteSheet> = linkedMapOf()
    private var bitmapHits = 0
    private var bitmapMisses = 0
    private var spriteSheetHits = 0
    private var spriteSheetMisses = 0
    private var removeCount = 0
    private var clearCount = 0

    /** 读取 bitmap 缓存；未命中时执行 [loader] 并保存结果。 */
    public fun getBitmap(key: String, loader: () -> PixelBitmap): PixelBitmap {
        val safeKey = requireKey(key)
        bitmaps[safeKey]?.let { cached ->
            bitmapHits += 1
            return cached
        }
        bitmapMisses += 1
        return loader().also { bitmaps[safeKey] = it }
    }

    /** 读取 sprite sheet 缓存；未命中时执行 [loader] 并保存结果。 */
    public fun getSpriteSheet(key: String, loader: () -> PixelSpriteSheet): PixelSpriteSheet {
        val safeKey = requireKey(key)
        spriteSheets[safeKey]?.let { cached ->
            spriteSheetHits += 1
            return cached
        }
        spriteSheetMisses += 1
        return loader().also { spriteSheets[safeKey] = it }
    }

    /** 移除同名 bitmap 和 sprite sheet 条目。 */
    public fun remove(key: String) {
        val safeKey = requireKey(key)
        val removedBitmap = bitmaps.remove(safeKey) != null
        val removedSpriteSheet = spriteSheets.remove(safeKey) != null
        if (removedBitmap || removedSpriteSheet) removeCount += 1
    }

    /** 清空所有缓存条目并更新清理计数。 */
    public fun clear() {
        if (bitmaps.isNotEmpty() || spriteSheets.isNotEmpty()) {
            clearCount += 1
            bitmaps.clear()
            spriteSheets.clear()
        }
    }

    /** 返回当前缓存大小和命中统计。 */
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

    /** 当前缓存的 bitmap 数量。 */
    public val bitmapCount: Int
        get() = bitmaps.size

    /** 当前缓存的 sprite sheet 数量。 */
    public val spriteSheetCount: Int
        get() = spriteSheets.size

    private fun requireKey(key: String): String {
        require(key.isNotBlank()) { "resource cache key must not be blank" }
        return key
    }
}
