package com.purride.pixeldesign.font

import android.content.Context
import android.os.Handler
import com.purride.pixelcore.CompositeGlyphProvider
import com.purride.pixelcore.GlyphStyle
import com.purride.pixelcore.IndexedBitmapGlyphSource
import com.purride.pixelcore.PixelFontEngine
import com.purride.pixelcore.PixelFontFamily
import com.purride.pixelcore.PixelFontWeight
import com.purride.pixelcore.PixelIndexedGlyphPack
import com.purride.pixelcore.PixelIndexedGlyphPackAssetLoader
import com.purride.pixelcore.PixelResourceCache
import com.purride.pixelcore.PixelResourceCacheLimits
import com.purride.pixelcore.PixelResourceLoadHandle
import com.purride.pixelcore.PixelResourceLoader
import com.purride.pixelcore.PixelStyledTextRasterizer
import com.purride.pixelcore.PixelTextRasterizer
import java.util.LinkedHashMap
import java.util.concurrent.ExecutorService

/** 产品字体异步准备过程的公开状态。 */
sealed interface ProductFontLoadState {
    /** 尚未发起冷启动字体请求。 */
    data object Idle : ProductFontLoadState

    /** 旧字体保持可用、候选字体正在后台准备。 */
    data class Loading(
        /** 当前已激活字体；冷启动时为 null。 */
        val active: ProductFontSelection?,
        /** 正在准备的候选字体。 */
        val pending: ProductFontSelection,
    ) : ProductFontLoadState

    /** 完整字体已准备并可原子提交。 */
    data class Ready(
        /** 当前激活字体。 */
        val active: ProductFontSelection,
        /** 当前完整 typography。 */
        val prepared: PreparedProductFont,
    ) : ProductFontLoadState

    /** 候选失败，旧字体仍保持激活。 */
    data class Failed(
        /** 失败前的当前字体；冷启动时为 null。 */
        val active: ProductFontSelection?,
        /** 加载失败的候选字体。 */
        val requested: ProductFontSelection,
        /** 原始加载异常。 */
        val error: Throwable,
    ) : ProductFontLoadState
}

/** 一个 face 的栅格器、indexed source 与精确样式。 */
internal data class PreparedFontFace(
    /** catalog 精确 face。 */
    val descriptor: ProductFontFaceDescriptor,
    /** 负责按需解压该 face 字形的 source。 */
    val source: IndexedBitmapGlyphSource,
    /** 缺字与宽字符分类使用的样式。 */
    val style: GlyphStyle,
    /** UI 测量和绘制使用的共享栅格器。 */
    val rasterizer: PixelTextRasterizer,
)

/** 已完整准备、渲染时不再执行资源 IO 的产品字体。 */
class PreparedProductFont internal constructor(
    /** 设置页当前激活的原生 face。 */
    val selection: ProductFontSelection,
    /** 原生和组件 face 的精确索引。 */
    private val faces: Map<ProductFontSelection, PreparedFontFace>,
) {
    /** 向组件暴露的精确字号 typography。 */
    val typography: ProductTypography = ProductTypography(selection, ::rasterizer)

    /** 返回设置选择的默认栅格器。 */
    val defaultRasterizer: PixelTextRasterizer
        get() = rasterizer(selection)

    /** 返回一个已准备 face 的栅格器，禁止运行时近似或 IO。 */
    fun rasterizer(faceSelection: ProductFontSelection): PixelTextRasterizer =
        requireNotNull(faces[faceSelection]) { "Font face was not prepared: $faceSelection" }.rasterizer

    /** 返回首字形真实墨迹前距；前导空格作为内容保留。 */
    fun leadingInkInset(text: String, faceSelection: ProductFontSelection): Int {
        if (text.isEmpty()) return 0
        val codePoint = text.codePointAt(0)
        if (Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)) return 0
        val face = requireNotNull(faces[faceSelection]) { "Font face was not prepared: $faceSelection" }
        return face.source.findGlyph(codePoint, face.style)?.metrics?.inkLeft ?: 0
    }

    /** 返回末字形真实墨迹到逻辑前进宽度末端的空白；尾随空格作为内容保留。 */
    fun trailingInkInset(text: String, faceSelection: ProductFontSelection): Int {
        if (text.isEmpty()) return 0
        /** 文本末尾的完整 Unicode 标量。 */
        val codePoint = text.codePointBefore(text.length)
        if (Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)) return 0
        /** 与实际绘制 face 相同的已准备资源。 */
        val face = requireNotNull(faces[faceSelection]) { "Font face was not prepared: $faceSelection" }
        /** 字形真实墨迹与 advance；缺字不做光学补偿。 */
        val metrics = face.source.findGlyph(codePoint, face.style)?.metrics ?: return 0
        return (metrics.advanceWidth - metrics.inkRight - 1).coerceAtLeast(0)
    }

    /** 使用已准备 face 的同一栅格器测量文本逻辑宽度，不触发资源 IO。 */
    fun measureTextWidth(text: String, faceSelection: ProductFontSelection): Int =
        rasterizer(faceSelection).measureText(text)

    /** 在内存压力下释放所有解压 glyph，保留 mmap 和 primitive 索引。 */
    fun clearGlyphCaches() {
        faces.values.forEach { face -> face.source.clearCache() }
    }
}

/** 异步加载、single-flight、有限历史和原子准备产品字体。 */
class ProductFontRepository(
    /** 用于读取应用 assets 的 Context。 */
    context: Context,
    /** 字体专用双线程执行器。 */
    private val executor: ExecutorService,
    /** 把完成结果提交回 UI 线程的 Handler。 */
    private val mainHandler: Handler,
) {
    /** 产品字体专用 16MiB/8 pack 预算。 */
    private val resourceCache = PixelResourceCache(
        PixelResourceCacheLimits(
            maxTotalBytes = FONT_PACK_CACHE_BYTES,
            maxBitmapBytes = 1L,
            maxSpriteSheetBytes = 1L,
            maxGlyphPackBytes = FONT_PACK_CACHE_BYTES,
            maxBitmapEntries = 1,
            maxSpriteSheetEntries = 1,
            maxGlyphPackEntries = FONT_PACK_CACHE_ENTRIES,
        ),
    )
    /** 正式异步资源加载入口。 */
    private val resourceLoader = PixelResourceLoader(resourceCache, executor)
    /** mmap 优先的 indexed asset 加载器。 */
    private val assetLoader = PixelIndexedGlyphPackAssetLoader(context, resourceCache)
    /** 按访问顺序只保留当前与最近 prepared selection。 */
    private val preparedCache = LinkedHashMap<ProductFontSelection, PreparedProductFont>(4, 0.75f, true)
    /** 当前调用方持有的可取消等待句柄。 */
    private var pendingHandles: List<PixelResourceLoadHandle<PixelIndexedGlyphPack>> = emptyList()
    /** 递增请求代次，用于丢弃过期完成结果。 */
    @Volatile
    private var generation: Long = 0L
    /** 终态释放后拒绝回调。 */
    @Volatile
    private var disposed: Boolean = false

    /** 异步准备原生 face 和当前宽度模式的全部组件 face。 */
    fun prepare(
        selection: ProductFontSelection,
        onComplete: (Result<PreparedProductFont>) -> Unit,
    ) {
        check(!disposed) { "ProductFontRepository is disposed" }
        require(ProductFontCatalog.supports(selection)) { "Font selection is not settings-visible: $selection" }
        val requestGeneration = ++generation
        cancelPendingHandles()
        synchronized(preparedCache) { preparedCache[selection] }?.let { cached ->
            mainHandler.post {
                if (!disposed && generation == requestGeneration) onComplete(Result.success(cached))
            }
            return
        }
        val requiredSelections = requiredSelections(selection)
        val handlesBySelection = requiredSelections.associateWith { faceSelection ->
            ProductFontCatalog.requireFace(faceSelection).packs.map { pack ->
                assetLoader.loadAsync(resourceLoader, pack.assetDirectory)
            }
        }
        pendingHandles = handlesBySelection.values.flatten()
        executor.execute {
            val result = runCatching {
                val preparedFaces = handlesBySelection.mapValues { (faceSelection, handles) ->
                    val descriptor = ProductFontCatalog.requireFace(faceSelection)
                    val packs = handles.map(PixelResourceLoadHandle<PixelIndexedGlyphPack>::await)
                    validatePacks(descriptor, packs)
                    createPreparedFace(descriptor, packs)
                }
                PreparedProductFont(selection, preparedFaces)
            }
            mainHandler.post {
                if (disposed || generation != requestGeneration) {
                    result.getOrNull()?.clearGlyphCaches()
                    return@post
                }
                pendingHandles = emptyList()
                result.getOrNull()?.let(::cachePrepared)
                onComplete(result)
            }
        }
    }

    /** 只保留指定激活 selection，供 UI 隐藏时释放最近字体。 */
    fun trimToActive(active: ProductFontSelection) {
        synchronized(preparedCache) {
            val iterator = preparedCache.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key != active) {
                    entry.value.clearGlyphCaches()
                    iterator.remove()
                }
            }
        }
    }

    /** 清空所有 prepared 字体的解压 glyph。 */
    fun clearGlyphCaches() {
        synchronized(preparedCache) { preparedCache.values.forEach(PreparedProductFont::clearGlyphCaches) }
    }

    /** 返回不持有资源引用的缓存诊断快照。 */
    fun cacheSnapshot() = resourceCache.detailedSnapshot()

    /** 取消在途订阅并释放所有字体资源。 */
    fun dispose() {
        if (disposed) return
        disposed = true
        generation += 1L
        cancelPendingHandles()
        synchronized(preparedCache) {
            preparedCache.values.forEach(PreparedProductFont::clearGlyphCaches)
            preparedCache.clear()
        }
        resourceCache.clear()
    }

    /** 返回设置 face 和同宽度模式组件 face 的去重列表。 */
    private fun requiredSelections(selection: ProductFontSelection): List<ProductFontSelection> = listOf(
        selection,
        ProductFontCatalog.selectionForRole(
            family = selection.family,
            widthMode = selection.widthMode,
            role = ProductTextRole.CHROME,
        ),
    ).distinct()

    /** 校验异步加载结果没有越过 catalog 声明边界。 */
    private fun validatePacks(descriptor: ProductFontFaceDescriptor, packs: List<PixelIndexedGlyphPack>) {
        require(packs.size == descriptor.packs.size) { "Loaded pack count does not match catalog" }
        packs.zip(descriptor.packs).forEach { (pack, expected) ->
            require(pack.manifest.packId == expected.id) { "Loaded pack id does not match catalog" }
            require(pack.manifest.cellHeight == descriptor.metrics.cellHeight) { "Loaded pack cell height mismatch" }
        }
    }

    /** 根据精确 metrics 创建 indexed source 与共享栅格器。 */
    private fun createPreparedFace(
        descriptor: ProductFontFaceDescriptor,
        packs: List<PixelIndexedGlyphPack>,
    ): PreparedFontFace {
        val style = styleFor(descriptor.metrics)
        val source = IndexedBitmapGlyphSource(packs, MAX_UNPACKED_GLYPH_BYTES)
        val rasterizer = PixelStyledTextRasterizer(
            engine = PixelFontEngine(CompositeGlyphProvider(listOf(source))),
            style = style,
            lineSpacing = 1,
        )
        return PreparedFontFace(descriptor, source, style, rasterizer)
    }

    /** 把缺字度量映射为 engine 样式，真实字形仍使用 pack 自身 advance。 */
    private fun styleFor(metrics: ProductFontMetrics): GlyphStyle = GlyphStyle(
        cellHeight = metrics.cellHeight,
        narrowAdvanceWidth = metrics.narrowAdvanceWidth,
        wideAdvanceWidth = metrics.wideAdvanceWidth,
        oversampleFactor = 1,
        narrowMinimumSampleRatio = 1f,
        wideMinimumSampleRatio = 1f,
        narrowTextSizeRatio = 1f,
        wideTextSizeRatio = 1f,
        narrowFontWeight = PixelFontWeight.NORMAL,
        wideFontWeight = PixelFontWeight.NORMAL,
        narrowFontFamily = PixelFontFamily.MONOSPACE,
        wideFontFamily = PixelFontFamily.DEFAULT,
        baseLetterSpacing = 0,
    )

    /** 写入二项 prepared LRU，并释放被淘汰字体的热 glyph。 */
    private fun cachePrepared(prepared: PreparedProductFont) {
        synchronized(preparedCache) {
            preparedCache[prepared.selection] = prepared
            while (preparedCache.size > PREPARED_FONT_ENTRIES) {
                val eldest = preparedCache.entries.first()
                preparedCache.remove(eldest.key)
                eldest.value.clearGlyphCaches()
            }
        }
    }

    /** 取消当前调用方等待；共享 IO 可以继续完成并进入有界缓存。 */
    private fun cancelPendingHandles() {
        pendingHandles.forEach(PixelResourceLoadHandle<PixelIndexedGlyphPack>::cancel)
        pendingHandles = emptyList()
    }

    private companion object {
        const val FONT_PACK_CACHE_BYTES: Long = 16L * 1024L * 1024L
        const val FONT_PACK_CACHE_ENTRIES: Int = 8
        const val PREPARED_FONT_ENTRIES: Int = 2
        const val MAX_UNPACKED_GLYPH_BYTES: Long = 2L * 1024L * 1024L
    }
}
