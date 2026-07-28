package com.purride.pixelcore

import java.util.LinkedHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/** 资源缓存计数和命中率兼容快照，用于既有 diagnostics 与测试断言。 */
public data class PixelResourceCacheSnapshot(
    /** 当前 bitmap 条目数。 */
    val bitmapCount: Int,
    /** 当前 sprite sheet 条目数。 */
    val spriteSheetCount: Int,
    /** bitmap 命中次数。 */
    val bitmapHits: Long,
    /** bitmap 未命中次数。 */
    val bitmapMisses: Long,
    /** sprite sheet 命中次数。 */
    val spriteSheetHits: Long,
    /** sprite sheet 未命中次数。 */
    val spriteSheetMisses: Long,
    /** 至少移除一个同名条目的显式 remove 次数。 */
    val removeCount: Long,
    /** 至少清理一个条目的 clear 次数。 */
    val clearCount: Long,
)

/** 缓存能够独立预算和观测的资源类型。 */
public enum class PixelResourceKind {
    /** ARGB bitmap。 */
    BITMAP,
    /** bitmap 与帧元数据组成的 sprite sheet。 */
    SPRITE_SHEET,
    /** manifest 与压缩字形组成的 glyph pack。 */
    GLYPH_PACK,
}

/** 自动淘汰或拒绝缓存的确定性原因。 */
public enum class PixelResourceEvictionReason {
    /** 单个资源大于其类型预算或总预算，因此未进入缓存。 */
    ENTRY_TOO_LARGE,
    /** 该类型字节数超过预算。 */
    TYPE_BYTE_BUDGET,
    /** 该类型条目数超过预算。 */
    TYPE_ENTRY_BUDGET,
    /** 所有类型合计字节数超过总预算。 */
    TOTAL_BYTE_BUDGET,
}

/** 单次自动淘汰事件；事件不持有资源对象，监听器不会阻止 GC。 */
public data class PixelResourceEviction(
    /** 被淘汰条目的调用方 key。 */
    val key: String,
    /** 被淘汰资源类型。 */
    val kind: PixelResourceKind,
    /** 该条目的保守估算字节数。 */
    val byteSize: Long,
    /** 淘汰或拒绝缓存的原因。 */
    val reason: PixelResourceEvictionReason,
)

/** 接收资源缓存自动淘汰事件的轻量监听器。 */
public fun interface PixelResourceEvictionListener {
    /** 在缓存锁外接收事件；监听器异常不会破坏缓存或资源加载。 */
    public fun onEvicted(eviction: PixelResourceEviction)
}

/** 资源缓存的总量、逐类字节和逐类条目预算。 */
public data class PixelResourceCacheLimits(
    /** 所有资源保守估算字节数的总上限。 */
    val maxTotalBytes: Long = 128L * 1024L * 1024L,
    /** bitmap 类型字节上限。 */
    val maxBitmapBytes: Long = 96L * 1024L * 1024L,
    /** sprite sheet 类型字节上限。 */
    val maxSpriteSheetBytes: Long = 96L * 1024L * 1024L,
    /** glyph pack 类型字节上限。 */
    val maxGlyphPackBytes: Long = 48L * 1024L * 1024L,
    /** bitmap 条目数上限。 */
    val maxBitmapEntries: Int = 1_024,
    /** sprite sheet 条目数上限。 */
    val maxSpriteSheetEntries: Int = 1_024,
    /** glyph pack 条目数上限。 */
    val maxGlyphPackEntries: Int = 128,
) {
    init {
        require(maxTotalBytes > 0L) { "maxTotalBytes must be > 0" }
        require(maxBitmapBytes > 0L) { "maxBitmapBytes must be > 0" }
        require(maxSpriteSheetBytes > 0L) { "maxSpriteSheetBytes must be > 0" }
        require(maxGlyphPackBytes > 0L) { "maxGlyphPackBytes must be > 0" }
        require(maxBitmapEntries > 0) { "maxBitmapEntries must be > 0" }
        require(maxSpriteSheetEntries > 0) { "maxSpriteSheetEntries must be > 0" }
        require(maxGlyphPackEntries > 0) { "maxGlyphPackEntries must be > 0" }
    }

    /** 返回指定资源类型的字节预算。 */
    internal fun maxBytes(kind: PixelResourceKind): Long = when (kind) {
        PixelResourceKind.BITMAP -> maxBitmapBytes
        PixelResourceKind.SPRITE_SHEET -> maxSpriteSheetBytes
        PixelResourceKind.GLYPH_PACK -> maxGlyphPackBytes
    }

    /** 返回指定资源类型的条目预算。 */
    internal fun maxEntries(kind: PixelResourceKind): Int = when (kind) {
        PixelResourceKind.BITMAP -> maxBitmapEntries
        PixelResourceKind.SPRITE_SHEET -> maxSpriteSheetEntries
        PixelResourceKind.GLYPH_PACK -> maxGlyphPackEntries
    }
}

/** 详细快照中的单个缓存条目，不包含资源引用。 */
public data class PixelResourceCacheEntrySnapshot(
    /** 调用方 key。 */
    val key: String,
    /** 资源类型。 */
    val kind: PixelResourceKind,
    /** 保守估算字节数。 */
    val byteSize: Long,
    /** 最近访问序号；数值越大表示越新。 */
    val lastAccessSequence: Long,
)

/** 带字节、glyph、淘汰和在途加载信息的完整缓存快照。 */
public data class PixelResourceCacheDetailedSnapshot(
    /** 当前所有资源估算总字节数。 */
    val totalBytes: Long,
    /** 当前 bitmap 估算字节数。 */
    val bitmapBytes: Long,
    /** 当前 sprite sheet 估算字节数。 */
    val spriteSheetBytes: Long,
    /** 当前 glyph pack 估算字节数。 */
    val glyphPackBytes: Long,
    /** 当前 bitmap 条目数。 */
    val bitmapCount: Int,
    /** 当前 sprite sheet 条目数。 */
    val spriteSheetCount: Int,
    /** 当前 glyph pack 条目数。 */
    val glyphPackCount: Int,
    /** 全类型累计命中次数。 */
    val hitCount: Long,
    /** 全类型累计未命中次数。 */
    val missCount: Long,
    /** 自动 LRU 淘汰次数。 */
    val evictionCount: Long,
    /** 因单条过大而未缓存的次数。 */
    val rejectedEntryCount: Long,
    /** 当前共享中的加载数量。 */
    val inFlightLoadCount: Int,
    /** 按访问新旧顺序排序的无引用条目列表。 */
    val entries: List<PixelResourceCacheEntrySnapshot>,
)

/**
 * 线程安全、按字节受限的资源 LRU。
 *
 * 相同类型和 key 的并发未命中只执行一次 loader；loader 永远在缓存锁外运行。显式 [remove] 或
 * [clear] 会令先前开始的在途结果失去写回资格，保证清理后不会被旧任务重新持有。
 */
public class PixelResourceCache @JvmOverloads public constructor(
    /** 总量和逐类预算。 */
    private val limits: PixelResourceCacheLimits = PixelResourceCacheLimits(),
    /** 可选的锁外淘汰监听器。 */
    private val evictionListener: PixelResourceEvictionListener? = null,
) {
    /** 保护所有条目、计数和在途表的唯一锁。 */
    private val lock = Any()
    /** bitmap 的 access-order LRU。 */
    private val bitmaps = LinkedHashMap<String, CacheEntry<PixelBitmap>>(16, 0.75f, true)
    /** sprite sheet 的 access-order LRU。 */
    private val spriteSheets = LinkedHashMap<String, CacheEntry<PixelSpriteSheet>>(16, 0.75f, true)
    /** glyph pack 的 access-order LRU。 */
    private val glyphPacks = LinkedHashMap<String, CacheEntry<PixelGlyphPack>>(16, 0.75f, true)
    /** 相同类型/key 共享的加载任务。 */
    private val inFlight = mutableMapOf<ResourceKey, LoadingSlot>()
    /** 每次命中或插入递增的全局 LRU 序号。 */
    private var accessSequence: Long = 0L
    /** remove/clear 后递增，用于阻止旧在途结果写回。 */
    private var mutationGeneration: Long = 0L
    /** bitmap 当前估算字节数。 */
    private var bitmapBytes: Long = 0L
    /** sprite sheet 当前估算字节数。 */
    private var spriteSheetBytes: Long = 0L
    /** glyph pack 当前估算字节数。 */
    private var glyphPackBytes: Long = 0L
    /** bitmap 命中次数。 */
    private var bitmapHits: Long = 0L
    /** bitmap 未命中次数。 */
    private var bitmapMisses: Long = 0L
    /** sprite sheet 命中次数。 */
    private var spriteSheetHits: Long = 0L
    /** sprite sheet 未命中次数。 */
    private var spriteSheetMisses: Long = 0L
    /** glyph pack 命中次数。 */
    private var glyphPackHits: Long = 0L
    /** glyph pack 未命中次数。 */
    private var glyphPackMisses: Long = 0L
    /** 自动 LRU 淘汰次数。 */
    private var evictionCount: Long = 0L
    /** 过大条目拒绝缓存次数。 */
    private var rejectedEntryCount: Long = 0L
    /** 至少移除一个条目的显式 remove 次数。 */
    private var removeCount: Long = 0L
    /** 至少移除一个条目的 clear 次数。 */
    private var clearCount: Long = 0L

    /** 读取 bitmap；未命中时在锁外执行一次共享 [loader]。 */
    public fun getBitmap(key: String, loader: () -> PixelBitmap): PixelBitmap {
        return getOrLoad(
            key = key,
            kind = PixelResourceKind.BITMAP,
            loader = loader,
            estimator = PixelBitmap::byteSize,
        )
    }

    /** 读取 sprite sheet；未命中时在锁外执行一次共享 [loader]。 */
    public fun getSpriteSheet(key: String, loader: () -> PixelSpriteSheet): PixelSpriteSheet {
        return getOrLoad(
            key = key,
            kind = PixelResourceKind.SPRITE_SHEET,
            loader = loader,
            estimator = ::estimateSpriteSheetBytes,
        )
    }

    /** 读取 glyph pack；未命中时在锁外执行一次共享 [loader]。 */
    public fun getGlyphPack(key: String, loader: () -> PixelGlyphPack): PixelGlyphPack {
        return getOrLoad(
            key = key,
            kind = PixelResourceKind.GLYPH_PACK,
            loader = loader,
            estimator = ::estimateGlyphPackBytes,
        )
    }

    /** 移除同名 bitmap、sprite sheet 和 glyph pack 条目，并使同名在途结果失去写回资格。 */
    public fun remove(key: String) {
        /** 经过空白校验的稳定 key。 */
        val safeKey = requireKey(key)
        synchronized(lock) {
            /** 本次实际释放的资源条目数。 */
            var removed = 0
            bitmaps.remove(safeKey)?.let { entry -> bitmapBytes -= entry.byteSize; removed += 1 }
            spriteSheets.remove(safeKey)?.let { entry -> spriteSheetBytes -= entry.byteSize; removed += 1 }
            glyphPacks.remove(safeKey)?.let { entry -> glyphPackBytes -= entry.byteSize; removed += 1 }
            /** 是否存在尚未完成的同名加载。 */
            val hasInFlight = inFlight.keys.any { resourceKey -> resourceKey.key == safeKey }
            if (removed > 0 || hasInFlight) mutationGeneration += 1L
            if (removed > 0) removeCount += 1L
        }
    }

    /** 清空所有缓存条目，并使已经开始的在途结果失去写回资格。 */
    public fun clear() {
        synchronized(lock) {
            /** 清理前是否至少持有一个资源引用。 */
            val hadEntries = bitmaps.isNotEmpty() || spriteSheets.isNotEmpty() || glyphPacks.isNotEmpty()
            /** 清理前是否存在在途结果可能写回。 */
            val hadInFlight = inFlight.isNotEmpty()
            if (hadEntries || hadInFlight) mutationGeneration += 1L
            if (hadEntries) clearCount += 1L
            bitmaps.clear()
            spriteSheets.clear()
            glyphPacks.clear()
            bitmapBytes = 0L
            spriteSheetBytes = 0L
            glyphPackBytes = 0L
        }
    }

    /** 返回 bitmap/sprite sheet 条目数与累计命中计数快照。 */
    public fun snapshot(): PixelResourceCacheSnapshot = synchronized(lock) {
        PixelResourceCacheSnapshot(
            bitmapCount = bitmaps.size,
            spriteSheetCount = spriteSheets.size,
            bitmapHits = bitmapHits,
            bitmapMisses = bitmapMisses,
            spriteSheetHits = spriteSheetHits,
            spriteSheetMisses = spriteSheetMisses,
            removeCount = removeCount,
            clearCount = clearCount,
        )
    }

    /** 返回不持有资源对象的完整字节/LRU/淘汰快照。 */
    public fun detailedSnapshot(): PixelResourceCacheDetailedSnapshot = synchronized(lock) {
        /** 三类条目合并后的无引用快照。 */
        val entries = buildList {
            addAll(bitmaps.map { (key, entry) -> entry.snapshot(key, PixelResourceKind.BITMAP) })
            addAll(
                spriteSheets.map { (key, entry) ->
                    entry.snapshot(key, PixelResourceKind.SPRITE_SHEET)
                },
            )
            addAll(
                glyphPacks.map { (key, entry) ->
                    entry.snapshot(key, PixelResourceKind.GLYPH_PACK)
                },
            )
        }.sortedBy(PixelResourceCacheEntrySnapshot::lastAccessSequence)
        PixelResourceCacheDetailedSnapshot(
            totalBytes = totalBytesLocked(),
            bitmapBytes = bitmapBytes,
            spriteSheetBytes = spriteSheetBytes,
            glyphPackBytes = glyphPackBytes,
            bitmapCount = bitmaps.size,
            spriteSheetCount = spriteSheets.size,
            glyphPackCount = glyphPacks.size,
            hitCount = bitmapHits + spriteSheetHits + glyphPackHits,
            missCount = bitmapMisses + spriteSheetMisses + glyphPackMisses,
            evictionCount = evictionCount,
            rejectedEntryCount = rejectedEntryCount,
            inFlightLoadCount = inFlight.size,
            entries = entries,
        )
    }

    /** 当前缓存的 bitmap 数量。 */
    public val bitmapCount: Int
        get() = synchronized(lock) { bitmaps.size }

    /** 当前缓存的 sprite sheet 数量。 */
    public val spriteSheetCount: Int
        get() = synchronized(lock) { spriteSheets.size }

    /** 当前缓存的 glyph pack 数量。 */
    public val glyphPackCount: Int
        get() = synchronized(lock) { glyphPacks.size }

    /** 通用的单飞加载、插入和异常传播实现。 */
    private fun <T : Any> getOrLoad(
        key: String,
        kind: PixelResourceKind,
        loader: () -> T,
        estimator: (T) -> Long,
    ): T {
        /** 经过空白校验的稳定 key。 */
        val safeKey = requireKey(key)
        /** 类型和文本共同组成的在途 key。 */
        val resourceKey = ResourceKey(kind = kind, key = safeKey)
        /** 当前调用需要等待或负责完成的共享槽位。 */
        lateinit var slot: LoadingSlot
        /** 当前线程是否是唯一 loader 所有者。 */
        var ownsLoad = false
        synchronized(lock) {
            getCachedLocked<T>(kind, safeKey)?.let { cached ->
                recordHitLocked(kind)
                cached.lastAccessSequence = nextAccessSequenceLocked()
                return cached.value
            }
            recordMissLocked(kind)
            /** 已存在的同 key 加载，若为空则由当前线程创建。 */
            val existing = inFlight[resourceKey]
            if (existing != null) {
                require(existing.ownerThread !== Thread.currentThread()) {
                    "recursive resource load for $kind '$safeKey'"
                }
                slot = existing
            } else {
                slot = LoadingSlot(
                    future = CompletableFuture(),
                    ownerThread = Thread.currentThread(),
                    generation = mutationGeneration,
                )
                inFlight[resourceKey] = slot
                ownsLoad = true
            }
        }
        if (ownsLoad) completeOwnedLoad(
            resourceKey = resourceKey,
            slot = slot,
            loader = loader,
            estimator = estimator,
        )
        return awaitSlot(slot)
    }

    /** 在缓存锁外执行 loader，并在仍有效时写回和执行淘汰。 */
    private fun <T : Any> completeOwnedLoad(
        resourceKey: ResourceKey,
        slot: LoadingSlot,
        loader: () -> T,
        estimator: (T) -> Long,
    ) {
        try {
            /** loader 成功产生的资源。 */
            val value = loader()
            /** 资源的非负保守估算字节数。 */
            val byteSize = estimator(value)
            require(byteSize >= 0L) { "resource byteSize must be >= 0" }
            /** 需要在锁外通知的淘汰事件。 */
            val evictions = synchronized(lock) {
                inFlight.remove(resourceKey)
                if (slot.generation != mutationGeneration) {
                    emptyList()
                } else {
                    insertAndEvictLocked(
                        key = resourceKey.key,
                        kind = resourceKey.kind,
                        value = value,
                        byteSize = byteSize,
                    )
                }
            }
            notifyEvictions(evictions)
            slot.future.complete(value)
        } catch (error: Throwable) {
            synchronized(lock) { inFlight.remove(resourceKey) }
            slot.future.completeExceptionally(error)
        }
    }

    /** 等待共享结果并解包原始 loader 异常。 */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> awaitSlot(slot: LoadingSlot): T {
        return try {
            slot.future.join() as T
        } catch (error: CompletionException) {
            throw error.cause ?: error
        }
    }

    /** 插入资源并按单条、逐类和总量预算依次淘汰。调用方必须持有 [lock]。 */
    private fun <T : Any> insertAndEvictLocked(
        key: String,
        kind: PixelResourceKind,
        value: T,
        byteSize: Long,
    ): List<PixelResourceEviction> {
        /** 本次插入产生的确定性事件。 */
        val events = mutableListOf<PixelResourceEviction>()
        if (byteSize > limits.maxBytes(kind) || byteSize > limits.maxTotalBytes) {
            rejectedEntryCount += 1L
            events += PixelResourceEviction(
                key = key,
                kind = kind,
                byteSize = byteSize,
                reason = PixelResourceEvictionReason.ENTRY_TOO_LARGE,
            )
            return events
        }
        /** 带最新访问序号的新条目。 */
        val entry = CacheEntry(
            value = value,
            byteSize = byteSize,
            lastAccessSequence = nextAccessSequenceLocked(),
        )
        putEntryLocked(kind = kind, key = key, entry = entry)
        while (kindBytesLocked(kind) > limits.maxBytes(kind)) {
            events += evictOldestOfKindLocked(kind, PixelResourceEvictionReason.TYPE_BYTE_BUDGET)
        }
        while (kindCountLocked(kind) > limits.maxEntries(kind)) {
            events += evictOldestOfKindLocked(kind, PixelResourceEvictionReason.TYPE_ENTRY_BUDGET)
        }
        while (totalBytesLocked() > limits.maxTotalBytes) {
            events += evictOldestGlobalLocked(PixelResourceEvictionReason.TOTAL_BYTE_BUDGET)
        }
        return events
    }

    /** 插入或替换指定类型条目并维护字节计数。调用方必须持有 [lock]。 */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> putEntryLocked(kind: PixelResourceKind, key: String, entry: CacheEntry<T>) {
        when (kind) {
            PixelResourceKind.BITMAP -> {
                /** 被相同 key 替换的旧条目。 */
                val previous = bitmaps.put(key, entry as CacheEntry<PixelBitmap>)
                bitmapBytes += entry.byteSize - (previous?.byteSize ?: 0L)
            }
            PixelResourceKind.SPRITE_SHEET -> {
                /** 被相同 key 替换的旧条目。 */
                val previous = spriteSheets.put(key, entry as CacheEntry<PixelSpriteSheet>)
                spriteSheetBytes += entry.byteSize - (previous?.byteSize ?: 0L)
            }
            PixelResourceKind.GLYPH_PACK -> {
                /** 被相同 key 替换的旧条目。 */
                val previous = glyphPacks.put(key, entry as CacheEntry<PixelGlyphPack>)
                glyphPackBytes += entry.byteSize - (previous?.byteSize ?: 0L)
            }
        }
    }

    /** 返回并触碰指定类型缓存条目。调用方必须持有 [lock]。 */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> getCachedLocked(kind: PixelResourceKind, key: String): CacheEntry<T>? {
        return when (kind) {
            PixelResourceKind.BITMAP -> bitmaps[key]
            PixelResourceKind.SPRITE_SHEET -> spriteSheets[key]
            PixelResourceKind.GLYPH_PACK -> glyphPacks[key]
        } as CacheEntry<T>?
    }

    /** 淘汰某一类型最旧条目。调用方必须持有 [lock]。 */
    private fun evictOldestOfKindLocked(
        kind: PixelResourceKind,
        reason: PixelResourceEvictionReason,
    ): PixelResourceEviction {
        /** access-order map 中最旧条目的 key。 */
        val key = when (kind) {
            PixelResourceKind.BITMAP -> bitmaps.entries.first().key
            PixelResourceKind.SPRITE_SHEET -> spriteSheets.entries.first().key
            PixelResourceKind.GLYPH_PACK -> glyphPacks.entries.first().key
        }
        return removeEvictedLocked(key = key, kind = kind, reason = reason)
    }

    /** 淘汰三类资源中全局访问序号最小的条目。调用方必须持有 [lock]。 */
    private fun evictOldestGlobalLocked(reason: PixelResourceEvictionReason): PixelResourceEviction {
        /** 各类型最旧候选。 */
        val candidates = buildList {
            bitmaps.entries.firstOrNull()?.let { (key, entry) ->
                add(GlobalCandidate(key, PixelResourceKind.BITMAP, entry.lastAccessSequence))
            }
            spriteSheets.entries.firstOrNull()?.let { (key, entry) ->
                add(GlobalCandidate(key, PixelResourceKind.SPRITE_SHEET, entry.lastAccessSequence))
            }
            glyphPacks.entries.firstOrNull()?.let { (key, entry) ->
                add(GlobalCandidate(key, PixelResourceKind.GLYPH_PACK, entry.lastAccessSequence))
            }
        }
        /** 全局最旧候选；总字节超限时至少存在一个条目。 */
        val oldest = candidates.minByOrNull(GlobalCandidate::sequence)
            ?: error("total byte budget exceeded without cache entries")
        return removeEvictedLocked(key = oldest.key, kind = oldest.kind, reason = reason)
    }

    /** 移除自动淘汰条目并更新计数。调用方必须持有 [lock]。 */
    private fun removeEvictedLocked(
        key: String,
        kind: PixelResourceKind,
        reason: PixelResourceEvictionReason,
    ): PixelResourceEviction {
        /** 被移除条目的字节数。 */
        val byteSize = when (kind) {
            PixelResourceKind.BITMAP -> bitmaps.remove(key)!!.byteSize.also { bitmapBytes -= it }
            PixelResourceKind.SPRITE_SHEET ->
                spriteSheets.remove(key)!!.byteSize.also { spriteSheetBytes -= it }
            PixelResourceKind.GLYPH_PACK ->
                glyphPacks.remove(key)!!.byteSize.also { glyphPackBytes -= it }
        }
        evictionCount += 1L
        return PixelResourceEviction(key = key, kind = kind, byteSize = byteSize, reason = reason)
    }

    /** 在缓存锁外逐个通知监听器。 */
    private fun notifyEvictions(evictions: List<PixelResourceEviction>) {
        /** 当前缓存的可选监听器。 */
        val listener = evictionListener ?: return
        evictions.forEach { eviction -> runCatching { listener.onEvicted(eviction) } }
    }

    /** 记录指定类型命中。调用方必须持有 [lock]。 */
    private fun recordHitLocked(kind: PixelResourceKind) {
        when (kind) {
            PixelResourceKind.BITMAP -> bitmapHits += 1L
            PixelResourceKind.SPRITE_SHEET -> spriteSheetHits += 1L
            PixelResourceKind.GLYPH_PACK -> glyphPackHits += 1L
        }
    }

    /** 记录指定类型未命中。调用方必须持有 [lock]。 */
    private fun recordMissLocked(kind: PixelResourceKind) {
        when (kind) {
            PixelResourceKind.BITMAP -> bitmapMisses += 1L
            PixelResourceKind.SPRITE_SHEET -> spriteSheetMisses += 1L
            PixelResourceKind.GLYPH_PACK -> glyphPackMisses += 1L
        }
    }

    /** 返回指定类型当前字节数。调用方必须持有 [lock]。 */
    private fun kindBytesLocked(kind: PixelResourceKind): Long = when (kind) {
        PixelResourceKind.BITMAP -> bitmapBytes
        PixelResourceKind.SPRITE_SHEET -> spriteSheetBytes
        PixelResourceKind.GLYPH_PACK -> glyphPackBytes
    }

    /** 返回指定类型当前条目数。调用方必须持有 [lock]。 */
    private fun kindCountLocked(kind: PixelResourceKind): Int = when (kind) {
        PixelResourceKind.BITMAP -> bitmaps.size
        PixelResourceKind.SPRITE_SHEET -> spriteSheets.size
        PixelResourceKind.GLYPH_PACK -> glyphPacks.size
    }

    /** 返回所有类型当前总字节数。调用方必须持有 [lock]。 */
    private fun totalBytesLocked(): Long = bitmapBytes + spriteSheetBytes + glyphPackBytes

    /** 生成严格递增的 LRU 序号。调用方必须持有 [lock]。 */
    private fun nextAccessSequenceLocked(): Long {
        if (accessSequence == Long.MAX_VALUE) renumberAccessSequencesLocked()
        accessSequence += 1L
        return accessSequence
    }

    /** 极端长跑发生序号溢出前，保持相对顺序并从零重新编号。 */
    private fun renumberAccessSequencesLocked() {
        /** 三类条目按旧访问顺序合并。 */
        val ordered = buildList<Pair<Long, CacheEntry<*>>> {
            addAll(bitmaps.values.map { entry -> entry.lastAccessSequence to entry })
            addAll(spriteSheets.values.map { entry -> entry.lastAccessSequence to entry })
            addAll(glyphPacks.values.map { entry -> entry.lastAccessSequence to entry })
        }.sortedBy { pair -> pair.first }
        ordered.forEachIndexed { index, pair -> pair.second.lastAccessSequence = index.toLong() + 1L }
        accessSequence = ordered.size.toLong()
    }

    /** 拒绝空白和过长 key，避免无界诊断字符串。 */
    private fun requireKey(key: String): String {
        require(key.isNotBlank()) { "resource cache key must not be blank" }
        require(key.length <= 1_024) { "resource cache key exceeds 1024 chars" }
        return key
    }

    /** 缓存值、字节权重和最近访问序号。 */
    private data class CacheEntry<T : Any>(
        /** 实际资源对象。 */
        val value: T,
        /** 保守估算字节数。 */
        val byteSize: Long,
        /** 全类型最近访问序号。 */
        var lastAccessSequence: Long,
    ) {
        /** 构建不持有 [value] 的公开条目快照。 */
        fun snapshot(key: String, kind: PixelResourceKind): PixelResourceCacheEntrySnapshot {
            return PixelResourceCacheEntrySnapshot(
                key = key,
                kind = kind,
                byteSize = byteSize,
                lastAccessSequence = lastAccessSequence,
            )
        }
    }

    /** 在途表使用的复合 key。 */
    private data class ResourceKey(
        /** 资源类型。 */
        val kind: PixelResourceKind,
        /** 调用方文本 key。 */
        val key: String,
    )

    /** 单次共享加载的 future、所有者和开始代次。 */
    private data class LoadingSlot(
        /** 等待者共享但不由等待者取消的结果。 */
        val future: CompletableFuture<Any>,
        /** 用于拒绝同线程递归等待的 loader 线程。 */
        val ownerThread: Thread,
        /** loader 开始时的缓存 mutation 代次。 */
        val generation: Long,
    )

    /** 全局 LRU 选择使用的最旧候选。 */
    private data class GlobalCandidate(
        /** 候选条目 key。 */
        val key: String,
        /** 候选资源类型。 */
        val kind: PixelResourceKind,
        /** 候选最近访问序号。 */
        val sequence: Long,
    )
}

/** 保守估算 sprite sheet 的 bitmap 与帧元数据字节。 */
private fun estimateSpriteSheetBytes(sheet: PixelSpriteSheet): Long {
    /** 每帧四个 Int 与对象/列表引用的保守固定开销。 */
    val frameBytes = sheet.frames.size.toLong() * 32L
    return sheet.bitmap.byteSize + frameBytes + 64L
}

/** 保守估算 glyph pack 的压缩字节、记录和 manifest 文本。 */
private fun estimateGlyphPackBytes(pack: PixelGlyphPack): Long {
    /** manifest 固定字段和 UTF-16 文本的近似字节。 */
    val manifestBytes = 64L +
        (pack.manifest.packId.length + pack.manifest.displayName.length).toLong() * 2L +
        pack.manifest.supportedRanges.sumOf { range -> range.length.toLong() * 2L }
    /** 每条 glyph 的压缩字节与 map/record 保守开销。 */
    val glyphBytes = pack.glyphs.values.sumOf { record ->
        record.packedPixelsUnsafe.size.toLong() + 64L
    }
    return manifestBytes + glyphBytes
}
