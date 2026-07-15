package com.purride.pixelcore

import android.os.Looper
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/** 同步主线程限制和短期失败缓存策略。 */
public data class PixelResourceLoadingPolicy(
    /** 是否允许同步加载 API 在 Android 主线程执行；默认禁止。 */
    val allowSynchronousMainThread: Boolean = false,
    /** 同一类型/key 失败后避免立即重复 IO 的缓存时长。 */
    val failureCacheDurationMillis: Long = 5_000L,
) {
    init {
        require(failureCacheDurationMillis >= 0L) { "failureCacheDurationMillis must be >= 0" }
    }
}

/** 资源加载器当前在途和失败缓存数量快照。 */
public data class PixelResourceLoaderSnapshot(
    /** 当前共享后台任务数量。 */
    val inFlightCount: Int,
    /** 尚未过期的失败记录数量。 */
    val cachedFailureCount: Int,
)

/**
 * 单个异步资源订阅句柄。
 *
 * [cancel] 只取消当前调用方等待，不取消同 key 的共享 IO；后台成功结果仍会进入 [PixelResourceCache]，
 * 其他订阅者也不会被一个调用方的取消影响。
 */
public class PixelResourceLoadHandle<T : Any> internal constructor(
    /** 当前调用方独立的完成 future。 */
    private val subscriber: CompletableFuture<T>,
    /** 用于阻止主线程发生真实阻塞的探针。 */
    private val isMainThread: () -> Boolean,
) {
    /** 当前订阅是否已经完成。 */
    public val isDone: Boolean
        get() = subscriber.isDone

    /** 当前订阅是否已经取消。 */
    public val isCancelled: Boolean
        get() = subscriber.isCancelled

    /** 取消当前订阅；返回 false 表示此前已经结束。 */
    public fun cancel(): Boolean = subscriber.cancel(false)

    /**
     * 等待并返回结果。
     *
     * 未完成时禁止在 Android 主线程调用；已经完成的结果允许立即读取。
     */
    public fun await(): T {
        check(subscriber.isDone || !isMainThread()) {
            "Blocking resource await is not allowed on the Android main thread"
        }
        return try {
            subscriber.join()
        } catch (error: CompletionException) {
            throw error.cause ?: error
        }
    }
}

/**
 * 正式资源加载 API，统一主线程限制、异步执行、同 key 去重、取消、预取和短期失败缓存。
 *
 * [executor] 生命周期由调用方持有；本类不会关闭共享线程池。同步 API 适合已经位于后台线程的
 * 调用方，UI 线程应使用 `load*Async` 或 `prefetch*`。
 */
public class PixelResourceLoader(
    /** 最终保存成功结果的字节受限 LRU。 */
    private val cache: PixelResourceCache,
    /** 执行异步 IO 和解析的调用方 executor。 */
    private val executor: Executor,
    /** 主线程和失败缓存策略。 */
    private val policy: PixelResourceLoadingPolicy = PixelResourceLoadingPolicy(),
) {
    /** 保护异步共享任务和失败记录的唯一锁。 */
    private val lock = Any()
    /** 相同类型/key 共享的后台 future。 */
    private val inFlight = mutableMapOf<LoadKey, CompletableFuture<Any>>()
    /** 尚未过期的 loader 失败记录。 */
    private val failures = mutableMapOf<LoadKey, FailureRecord>()
    /** JVM 测试可替换的主线程探针；生产默认读取 Android Looper。 */
    internal var mainThreadProbe: () -> Boolean = ::detectAndroidMainThread
    /** JVM 测试可替换的单调时钟。 */
    internal var nanoTimeProvider: () -> Long = System::nanoTime

    /** 在当前后台线程同步读取 bitmap。 */
    public fun loadBitmap(key: String, loader: () -> PixelBitmap): PixelBitmap {
        requireSynchronousThread("loadBitmap")
        return loadBitmapInternal(key, loader)
    }

    /** 在当前后台线程同步读取 sprite sheet。 */
    public fun loadSpriteSheet(key: String, loader: () -> PixelSpriteSheet): PixelSpriteSheet {
        requireSynchronousThread("loadSpriteSheet")
        return loadSpriteSheetInternal(key, loader)
    }

    /** 在当前后台线程同步读取 glyph pack。 */
    public fun loadGlyphPack(key: String, loader: () -> PixelGlyphPack): PixelGlyphPack {
        requireSynchronousThread("loadGlyphPack")
        return loadGlyphPackInternal(key, loader)
    }

    /** 在 [executor] 上异步读取 bitmap，同 key 调用共享一次后台工作。 */
    public fun loadBitmapAsync(
        key: String,
        loader: () -> PixelBitmap,
    ): PixelResourceLoadHandle<PixelBitmap> {
        return loadAsync(
            key = requireKey(key),
            kind = PixelResourceKind.BITMAP,
            loader = { loadBitmapInternal(key, loader) },
        )
    }

    /** 在 [executor] 上异步读取 sprite sheet，同 key 调用共享一次后台工作。 */
    public fun loadSpriteSheetAsync(
        key: String,
        loader: () -> PixelSpriteSheet,
    ): PixelResourceLoadHandle<PixelSpriteSheet> {
        return loadAsync(
            key = requireKey(key),
            kind = PixelResourceKind.SPRITE_SHEET,
            loader = { loadSpriteSheetInternal(key, loader) },
        )
    }

    /** 在 [executor] 上异步读取 glyph pack，同 key 调用共享一次后台工作。 */
    public fun loadGlyphPackAsync(
        key: String,
        loader: () -> PixelGlyphPack,
    ): PixelResourceLoadHandle<PixelGlyphPack> {
        return loadAsync(
            key = requireKey(key),
            kind = PixelResourceKind.GLYPH_PACK,
            loader = { loadGlyphPackInternal(key, loader) },
        )
    }

    /** 提前异步填充 bitmap 缓存；句柄可用于观察或取消当前订阅。 */
    public fun prefetchBitmap(
        key: String,
        loader: () -> PixelBitmap,
    ): PixelResourceLoadHandle<PixelBitmap> = loadBitmapAsync(key, loader)

    /** 提前异步填充 sprite sheet 缓存。 */
    public fun prefetchSpriteSheet(
        key: String,
        loader: () -> PixelSpriteSheet,
    ): PixelResourceLoadHandle<PixelSpriteSheet> = loadSpriteSheetAsync(key, loader)

    /** 提前异步填充 glyph pack 缓存。 */
    public fun prefetchGlyphPack(
        key: String,
        loader: () -> PixelGlyphPack,
    ): PixelResourceLoadHandle<PixelGlyphPack> = loadGlyphPackAsync(key, loader)

    /** 移除全部失败记录，使下一次请求立即重试。 */
    public fun clearFailures() {
        synchronized(lock) { failures.clear() }
    }

    /** 移除指定类型/key 的失败记录。 */
    public fun clearFailure(kind: PixelResourceKind, key: String) {
        /** 经过空白校验的复合 key。 */
        val loadKey = LoadKey(kind = kind, key = requireKey(key))
        synchronized(lock) { failures.remove(loadKey) }
    }

    /** 返回在途和未过期失败数量。 */
    public fun snapshot(): PixelResourceLoaderSnapshot = synchronized(lock) {
        pruneExpiredFailuresLocked()
        PixelResourceLoaderSnapshot(
            inFlightCount = inFlight.size,
            cachedFailureCount = failures.size,
        )
    }

    /** 不重复主线程检查的 bitmap 内部加载。 */
    private fun loadBitmapInternal(key: String, loader: () -> PixelBitmap): PixelBitmap {
        /** 经过空白校验的复合 key。 */
        val loadKey = LoadKey(PixelResourceKind.BITMAP, requireKey(key))
        return withFailureCache(loadKey) { cache.getBitmap(loadKey.key, loader) }
    }

    /** 不重复主线程检查的 sprite sheet 内部加载。 */
    private fun loadSpriteSheetInternal(
        key: String,
        loader: () -> PixelSpriteSheet,
    ): PixelSpriteSheet {
        /** 经过空白校验的复合 key。 */
        val loadKey = LoadKey(PixelResourceKind.SPRITE_SHEET, requireKey(key))
        return withFailureCache(loadKey) { cache.getSpriteSheet(loadKey.key, loader) }
    }

    /** 不重复主线程检查的 glyph pack 内部加载。 */
    private fun loadGlyphPackInternal(key: String, loader: () -> PixelGlyphPack): PixelGlyphPack {
        /** 经过空白校验的复合 key。 */
        val loadKey = LoadKey(PixelResourceKind.GLYPH_PACK, requireKey(key))
        return withFailureCache(loadKey) { cache.getGlyphPack(loadKey.key, loader) }
    }

    /** 返回独立可取消订阅，同时让相同类型/key 共享一个后台 future。 */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> loadAsync(
        key: String,
        kind: PixelResourceKind,
        loader: () -> T,
    ): PixelResourceLoadHandle<T> {
        /** 类型和文本组成的共享任务 key。 */
        val loadKey = LoadKey(kind = kind, key = key)
        /** 已存在或当前调用创建的共享后台 future。 */
        val shared: CompletableFuture<T> = synchronized(lock) {
            activeFailureLocked(loadKey)?.let { failure ->
                return@synchronized failedFuture<T>(failure.error)
            }
            inFlight[loadKey]?.let { existing -> return@synchronized existing as CompletableFuture<T> }
            /** 由当前调用创建并登记的共享 future。 */
            val created = CompletableFuture<T>()
            inFlight[loadKey] = created as CompletableFuture<Any>
            try {
                executor.execute {
                    try {
                        created.complete(loader())
                    } catch (error: Throwable) {
                        created.completeExceptionally(error)
                    }
                }
            } catch (error: RejectedExecutionException) {
                created.completeExceptionally(error)
            }
            created.whenComplete { _, error ->
                synchronized(lock) {
                    inFlight.remove(loadKey, created as CompletableFuture<Any>)
                    if (error != null) cacheFailureLocked(loadKey, unwrapCompletion(error))
                }
            }
            created
        }
        /** 当前调用方独立的可取消订阅。 */
        val subscriber = CompletableFuture<T>()
        shared.whenComplete { value, error ->
            if (error == null) subscriber.complete(value)
            else subscriber.completeExceptionally(unwrapCompletion(error))
        }
        return PixelResourceLoadHandle(subscriber = subscriber, isMainThread = mainThreadProbe)
    }

    /** 同步执行并应用短期失败缓存。 */
    private inline fun <T : Any> withFailureCache(loadKey: LoadKey, block: () -> T): T {
        synchronized(lock) {
            activeFailureLocked(loadKey)?.let { failure -> throw failure.error }
        }
        return try {
            block().also { synchronized(lock) { failures.remove(loadKey) } }
        } catch (error: Throwable) {
            synchronized(lock) { cacheFailureLocked(loadKey, error) }
            throw error
        }
    }

    /** 缓存一次失败；零时长策略不保存。调用方必须持有 [lock]。 */
    private fun cacheFailureLocked(loadKey: LoadKey, error: Throwable) {
        if (error is CancellationException || policy.failureCacheDurationMillis == 0L) return
        /** 毫秒策略换算后的单调时钟时长，并对乘法饱和。 */
        val durationNanos = policy.failureCacheDurationMillis
            .coerceAtMost(Long.MAX_VALUE / 1_000_000L) * 1_000_000L
        /** 当前单调时钟。 */
        val now = nanoTimeProvider()
        /** 饱和计算的失败失效时间。 */
        val expiresAt = if (Long.MAX_VALUE - now < durationNanos) Long.MAX_VALUE else now + durationNanos
        failures[loadKey] = FailureRecord(error = error, expiresAtNanos = expiresAt)
    }

    /** 返回尚未过期的失败，过期记录会立即删除。调用方必须持有 [lock]。 */
    private fun activeFailureLocked(loadKey: LoadKey): FailureRecord? {
        /** 当前 key 的失败记录。 */
        val failure = failures[loadKey] ?: return null
        if (nanoTimeProvider() >= failure.expiresAtNanos) {
            failures.remove(loadKey)
            return null
        }
        return failure
    }

    /** 清理全部过期失败。调用方必须持有 [lock]。 */
    private fun pruneExpiredFailuresLocked() {
        /** 当前单调时钟快照。 */
        val now = nanoTimeProvider()
        failures.entries.removeAll { entry -> now >= entry.value.expiresAtNanos }
    }

    /** 同步 API 默认拒绝 Android 主线程。 */
    private fun requireSynchronousThread(operation: String) {
        check(policy.allowSynchronousMainThread || !mainThreadProbe()) {
            "$operation is not allowed on the Android main thread; use the async API"
        }
    }

    /** 拒绝空白和过长资源 key。 */
    private fun requireKey(key: String): String {
        require(key.isNotBlank()) { "resource key must not be blank" }
        require(key.length <= 1_024) { "resource key exceeds 1024 chars" }
        return key
    }

    /** 构建已经失败的 future，兼容 API 24 desugaring。 */
    private fun <T : Any> failedFuture(error: Throwable): CompletableFuture<T> {
        /** 立即完成失败的 future。 */
        val future = CompletableFuture<T>()
        future.completeExceptionally(error)
        return future
    }

    /** 类型和文本组成的加载 key。 */
    private data class LoadKey(
        /** 资源类型。 */
        val kind: PixelResourceKind,
        /** 调用方 key。 */
        val key: String,
    )

    /** 短期失败及其单调失效时间。 */
    private data class FailureRecord(
        /** 原始 loader 异常。 */
        val error: Throwable,
        /** System.nanoTime 时基上的失效点。 */
        val expiresAtNanos: Long,
    )
}

/** 在 Android 环境判断当前线程是否为主线程；纯 JVM 环境安全返回 false。 */
private fun detectAndroidMainThread(): Boolean {
    return try {
        Looper.myLooper() != null && Looper.myLooper() === Looper.getMainLooper()
    } catch (_: Throwable) {
        false
    }
}

/** 去掉 CompletableFuture 包装异常，保留 loader 原因。 */
private fun unwrapCompletion(error: Throwable): Throwable {
    return if (error is CompletionException && error.cause != null) error.cause!! else error
}
