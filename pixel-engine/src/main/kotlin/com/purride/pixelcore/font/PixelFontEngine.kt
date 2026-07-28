package com.purride.pixelcore

/**
 * 字形样式描述。
 *
 * 这里先保留和现有 Launcher 字体链路兼容的字段形状，方便后面把真实字形包接到
 * pixel-engine UI layer 或 demo 上时不需要重新设计一轮样式协议。
 */
public data class GlyphStyle(
    /** Logical bitmap cell height used to select compatible glyph packs. */
    val cellHeight: Int,
    /** Default horizontal advance for narrow scalar glyphs. */
    val narrowAdvanceWidth: Int,
    /** Default horizontal advance for wide scalar glyphs. */
    val wideAdvanceWidth: Int,
    /** Sampling multiplier used by host font conversion pipelines. */
    val oversampleFactor: Int,
    /** Minimum accepted narrow-glyph sample ratio. */
    val narrowMinimumSampleRatio: Float,
    /** Minimum accepted wide-glyph sample ratio. */
    val wideMinimumSampleRatio: Float,
    /** Text-size ratio used while generating narrow glyph records. */
    val narrowTextSizeRatio: Float,
    /** Text-size ratio used while generating wide glyph records. */
    val wideTextSizeRatio: Float,
    /** Font weight requested for narrow glyph generation. */
    val narrowFontWeight: PixelFontWeight,
    /** Font weight requested for wide glyph generation. */
    val wideFontWeight: PixelFontWeight,
    /** Font family requested for narrow glyph generation. */
    val narrowFontFamily: PixelFontFamily,
    /** Font family requested for wide glyph generation. */
    val wideFontFamily: PixelFontFamily,
    /** Additional logical pixels inserted between adjacent visible scalar glyphs. */
    val baseLetterSpacing: Int = 0,
)

/** 定义 `PixelFontWeight` 在 `PixelFontEngine` 中承担的数据与行为边界。
 *
 * Supported weight choices for generated bitmap glyphs.
 */
public enum class PixelFontWeight {
    /** Uses the normal-weight font face. */
    NORMAL,
    /** Uses the bold-weight font face. */
    BOLD,
}

/** 定义 `PixelFontFamily` 在 `PixelFontEngine` 中承担的数据与行为边界。
 *
 * Supported family choices for generated bitmap glyphs.
 */
public enum class PixelFontFamily {
    /** Uses the host's default sans-serif-compatible family. */
    DEFAULT,
    /** Uses a host monospace family. */
    MONOSPACE,
}

/**
 * 字形度量信息。
 *
 * `inkLeft/inkRight` 用来描述真实墨迹边界，后续在中英文或宽窄字符混排时，
 * 可以据此保证最小可视空列，而不是简单按字符分类硬编码。
 */
public data class GlyphMetrics(
    /** Horizontal cursor advance after painting this glyph. */
    val advanceWidth: Int,
    /** Baseline row measured from the bitmap top. */
    val baselineOffset: Int,
    /** Whether the glyph participates in wide-cell metrics. */
    val isWideGlyph: Boolean,
    /** Whether adjacent visible glyphs require a protected minimum gap. */
    val requiresVisualGapProtection: Boolean = false,
    /** First bitmap column containing visible ink, or [advanceWidth] for blank glyphs. */
    val inkLeft: Int = 0,
    /** Last bitmap column containing visible ink, or `-1` for blank glyphs. */
    val inkRight: Int = advanceWidth - 1,
)

/** 解包后的单个字形 bitmap 和度量。 */
public data class GlyphBitmap(
    /** Bitmap width in logical pixels. */
    val width: Int,
    /** Bitmap height in logical pixels. */
    val height: Int,
    /** Row-major binary pixel coverage with exactly [width] × [height] entries. */
    val pixels: ByteArray,
    /** Cursor, baseline and ink metrics associated with [pixels]. */
    val metrics: GlyphMetrics,
)

/** 可选字形来源；找不到 Unicode code point 时返回 null 让下一个来源兜底。 */
public interface GlyphSource {
    /**
     * 查找一个 Unicode scalar value 对应的字形 bitmap。
     *
     * [codePoint] 必须是合法 scalar（0..0x10FFFF 且非 surrogate），实现需要按完整标量查表，
     * 不能把 supplementary code point 截断成 UTF-16 码元。找不到时返回 `null`，交给下一个
     * source 或最终 provider 兜底。
     */
    public fun findGlyph(codePoint: Int, style: GlyphStyle): GlyphBitmap?

    /** 清理来源内部缓存。 */
    public fun clearCache(): Unit = Unit
}

/** 必须能为任意 Unicode code point 返回字形的最终提供器。 */
public interface GlyphProvider {
    /**
     * 栅格化一个 Unicode scalar value；找不到真实字形时必须返回兜底字形。
     *
     * [codePoint] 必须是合法 scalar（0..0x10FFFF 且非 surrogate），否则抛
     * `IllegalArgumentException`。supplementary code point 与 BMP 一视同仁，实现不得把它
     * 降级成替换字符。
     */
    public fun rasterizeGlyph(codePoint: Int, style: GlyphStyle): GlyphBitmap

    /** 清理提供器内部缓存。 */
    public fun clearCache(): Unit = Unit
}

/**
 * 组合字形提供器。
 *
 * 上层可以把多个字形来源按优先级串起来，例如拉丁包、中文包、兜底空字形。
 */
public class CompositeGlyphProvider(
    /** Ordered code-point sources queried before the deterministic fallback. */
    private val sources: List<GlyphSource>,
) : GlyphProvider {

    /** Resolves a scalar without narrowing supplementary values to UTF-16 code units. */
    override fun rasterizeGlyph(codePoint: Int, style: GlyphStyle): GlyphBitmap {
        requireUnicodeScalar(codePoint)
        return sources.firstNotNullOfOrNull { source -> source.findGlyph(codePoint, style) }
            ?: emptyGlyph(codePoint, style)
    }

    /** Clears every source cache owned by this composite. */
    override fun clearCache() {
        sources.forEach { source -> source.clearCache() }
    }

    /** Builds exactly one visible or blank fallback cell for one unsupported scalar. */
    private fun emptyGlyph(codePoint: Int, style: GlyphStyle): GlyphBitmap {
        /** Non-ASCII scalars use the configured wide fallback cell. */
        val isWideGlyph = codePoint !in ASCII_PRINTABLE_RANGE
        /** Advance width selected without inspecting UTF-16 code-unit count. */
        val width = if (isWideGlyph) style.wideAdvanceWidth else style.narrowAdvanceWidth
        /** Positive fallback cell height required by bitmap allocation. */
        val height = style.cellHeight.coerceAtLeast(1)
        /** Mutable fallback pixels filled with one deterministic box/X pattern when visible. */
        val pixels = ByteArray(width * height)
        /** Whitespace and control scalars advance without drawing an accidental tofu glyph. */
        val isVisibleFallback = !Character.isWhitespace(codePoint) && !Character.isISOControl(codePoint)
        if (isVisibleFallback && width > 0) {
            /** Fallback bitmap row. */
            for (y in 0 until height) {
                /** Fallback bitmap column. */
                for (x in 0 until width) {
                    if (x == 0 || x == width - 1 || y == 0 || y == height - 1 || x == y || x == width - 1 - y) {
                        pixels[(y * width) + x] = 1
                    }
                }
            }
        }
        return GlyphBitmap(
            width = width,
            height = height,
            pixels = pixels,
            metrics = GlyphMetrics(
                advanceWidth = width,
                baselineOffset = height - 2,
                isWideGlyph = isWideGlyph,
                requiresVisualGapProtection = requiresVisualGapProtection(codePoint, isWideGlyph),
                inkLeft = if (isVisibleFallback) 0 else width,
                inkRight = if (isVisibleFallback) width - 1 else -1,
            ),
        )
    }
}

/**
 * 位图字形源。
 *
 * 它只负责把压缩字形记录解包成像素矩阵，并缓存解包结果；不负责资源加载。
 */
public class BitmapGlyphSource(
    /** Immutable glyph packs searched in caller-defined priority order. */
    private val packs: List<PixelGlyphPack>,
) : GlyphSource {

    /** Unpacked bitmap cache keyed by full scalar value and style-sensitive metrics. */
    private val unpackedGlyphCache = mutableMapOf<GlyphCacheKey, GlyphBitmap>()

    /** Looks up a complete scalar key, including supplementary glyph-pack records. */
    override fun findGlyph(codePoint: Int, style: GlyphStyle): GlyphBitmap? {
        requireUnicodeScalar(codePoint)
        /** Candidate pack examined in caller-defined fallback order. */
        for (pack in packs) {
            if (pack.manifest.cellHeight != style.cellHeight) {
                continue
            }

            /** Packed record addressed by the full Unicode scalar rather than one UTF-16 unit. */
            val record = pack.glyphs[codePoint] ?: continue
            /** Cache identity preserving pack, scalar and style-sensitive width classification. */
            val cacheKey = GlyphCacheKey(
                packId = pack.manifest.packId,
                codePoint = codePoint,
                narrowAdvanceWidth = style.narrowAdvanceWidth,
            )
            return unpackedGlyphCache.getOrPut(cacheKey) {
                /** Exact binary bitmap expanded to one byte per logical pixel. */
                val unpackedPixels = unpackBits(
                    packed = record.packedPixelsUnsafe,
                    pixelCount = record.width * pack.manifest.cellHeight,
                )
                /** Horizontal visible-ink bounds computed from expanded pixels. */
                val inkBounds = computeInkBounds(
                    width = record.width,
                    height = pack.manifest.cellHeight,
                    pixels = unpackedPixels,
                )
                GlyphBitmap(
                    width = record.width,
                    height = pack.manifest.cellHeight,
                    pixels = unpackedPixels,
                    metrics = GlyphMetrics(
                        advanceWidth = record.advanceWidth,
                        baselineOffset = pack.manifest.baseline,
                        isWideGlyph = record.advanceWidth > style.narrowAdvanceWidth,
                        requiresVisualGapProtection = requiresVisualGapProtection(
                            codePoint = codePoint,
                            isWideGlyph = record.advanceWidth > style.narrowAdvanceWidth,
                        ),
                        inkLeft = inkBounds.first,
                        inkRight = inkBounds.second,
                    ),
                )
            }
        }
        return null
    }

    /** Drops every unpacked bitmap while retaining immutable pack bytes. */
    override fun clearCache() {
        unpackedGlyphCache.clear()
    }

    /** Expands MSB-first packed bits into one binary byte per requested pixel. */
    private fun unpackBits(packed: ByteArray, pixelCount: Int): ByteArray {
        /** Mutable row-major binary pixels returned to the renderer. */
        val pixels = ByteArray(pixelCount)
        /** Linear unpacked pixel position. */
        for (index in 0 until pixelCount) {
            /** Unsigned packed source byte containing [index]. */
            val packedByte = packed[index / 8].toInt() and 0xFF
            /** MSB-first bit position inside [packedByte]. */
            val bitShift = 7 - (index % 8)
            pixels[index] = if (((packedByte shr bitShift) and 0x01) == 1) 1 else 0
        }
        return pixels
    }

    /** Finds the first and last columns containing ink, or `(width, -1)` for blank glyphs. */
    private fun computeInkBounds(width: Int, height: Int, pixels: ByteArray): Pair<Int, Int> {
        /** Smallest visible column encountered so far. */
        var left = width
        /** Largest visible column encountered so far. */
        var right = -1
        /** Bitmap row inspected for ink. */
        for (y in 0 until height) {
            /** Bitmap column inspected for ink. */
            for (x in 0 until width) {
                if (pixels[(y * width) + x].toInt() != 0) {
                    if (x < left) {
                        left = x
                    }
                    if (x > right) {
                        right = x
                    }
                }
            }
        }
        return left to right
    }

    private data class GlyphCacheKey(
        /** Stable glyph-pack identity. */
        val packId: String,
        /** Complete Unicode scalar key. */
        val codePoint: Int,
        /** Style input affecting wide/narrow metric classification. */
        val narrowAdvanceWidth: Int,
    )
}

/**
 * 像素文本引擎。
 *
 * 这一版专注三件事：
 * 1. 基于字形提供器测量文本宽度
 * 2. 按像素宽度裁剪文本
 * 3. 按真实字形位图绘制文本
 */
public class PixelFontEngine(
    /** Final provider queried with complete Unicode scalar values. */
    private val glyphProvider: GlyphProvider,
) {
    /** 集中提供 `PixelFontEngine` 的 `<companion>` 共享入口。
 *
 * Fixed cache and pair-spacing limits shared by every engine instance.
 */
    public companion object {
        /** Minimum blank pixel columns protected between selected wide glyph pairs. */
        private const val MIN_WIDE_PAIR_VISUAL_GAP = 1
        /** Maximum scalar/style bitmap entries retained by one engine instance. */
        private const val MAX_CACHE_ENTRIES = 2048
        /** Small initial allocation that grows only when consumers use more glyphs. */
        private const val CACHE_INITIAL_CAPACITY = 64
        /** Load factor balancing glyph-cache memory and lookup cost. */
        private const val CACHE_LOAD_FACTOR = 0.75f
    }

    /**
     * 访问序 LinkedHashMap：getOrPut 后命中条目会被移到链尾，
     * 真正实现 LRU 而非 FIFO 驱逐，让 CJK 等大字符集场景下高频字
     * 不会被冷僻字挤出去。
     */
    private val glyphCache = LinkedHashMap<GlyphKey, GlyphBitmap>(
        CACHE_INITIAL_CAPACITY,
        CACHE_LOAD_FACTOR,
        true,
    )
    /** Number of scalar/style lookups served from [glyphCache]. */
    private var glyphCacheHits: Long = 0L
    /** Number of scalar/style lookups delegated to [glyphProvider]. */
    private var glyphCacheMisses: Long = 0L

    /** 测量整段文本在指定样式下需要的像素宽度。 */
    public fun measureText(text: String, style: GlyphStyle): Int {
        return measureTextSegments(first = text, second = null, style = style)
    }

    /** 连续测量两个相邻文本片段，保留跨片段 pair spacing 且不创建拼接字符串。 */
    internal fun measureAdjacentText(
        first: String,
        second: String,
        style: GlyphStyle,
    ): Int {
        return measureTextSegments(first = first, second = second, style = style)
    }

    /** 扫描一个或两个文本片段，共享前一字形状态以计算精确相邻间距。 */
    private fun measureTextSegments(
        first: String,
        second: String?,
        style: GlyphStyle,
    ): Int {
        /** 全部完整码点累计得到的视觉前进宽度。 */
        var totalWidth = 0
        /** 用于计算相邻字形间距的前一个标量字形。 */
        var previousGlyph: GlyphBitmap? = null
        /** 当前扫描的片段；第二轮存在时不会重置 [previousGlyph]。 */
        var segment = first
        /** 已进入的片段序号，最多扫描 first/second 两轮。 */
        var segmentIndex = 0
        while (true) {
            /** 按完整标量或单个畸形 UTF-16 码元推进的源偏移。 */
            var sourceOffset = 0
            while (sourceOffset < segment.length) {
                /** 原始解码值；孤立代理项只在字形查询时映射为兜底值。 */
                val decodedValue = Character.codePointAt(segment, sourceOffset)
                /** 有效标量键；畸形 UTF-16 输入使用确定性的替换字符键。 */
                val codePoint = decodedValue.toGlyphCodePoint()
                /** 按完整标量值寻址的缓存位图。 */
                val glyph = glyphFor(codePoint, style)
                totalWidth += glyph.metrics.advanceWidth +
                    interGlyphSpacing(previousGlyph, glyph, style)
                previousGlyph = glyph
                sourceOffset += Character.charCount(decodedValue)
            }
            if (segmentIndex > 0 || second == null) break
            segment = second
            segmentIndex += 1
        }
        return totalWidth
    }

    /** 返回能完整放进 [maxWidth] 的前缀文本。 */
    public fun trimToWidth(text: String, style: GlyphStyle, maxWidth: Int): String {
        if (text.isEmpty() || maxWidth <= 0) {
            return ""
        }

        /** Width accepted through [acceptedEndOffset]. */
        var consumedWidth = 0
        /** Previous accepted glyph used for pair spacing. */
        var previousGlyph: GlyphBitmap? = null
        /** UTF-16 source offset of the next scalar candidate. */
        var sourceOffset = 0
        /** 最后一个被接受的完整标量之后的 UTF-16 边界。 */
        var acceptedEndOffset = 0
        while (sourceOffset < text.length) {
            /** 原始解码值，保留该源位置占一个还是两个 UTF-16 码元的信息。 */
            val decodedValue = Character.codePointAt(text, sourceOffset)
            /** 合法查表标量；遇到畸形 UTF-16 码元时为确定性替换标量。 */
            val codePoint = decodedValue.toGlyphCodePoint()
            /** Candidate glyph measured as one scalar rather than one surrogate half. */
            val glyph = glyphFor(codePoint, style)
            /** Width after accepting the complete candidate scalar. */
            val nextWidth = consumedWidth + glyph.metrics.advanceWidth + interGlyphSpacing(previousGlyph, glyph, style)
            if (nextWidth > maxWidth) {
                return text.substring(0, acceptedEndOffset)
            }
            consumedWidth = nextWidth
            previousGlyph = glyph
            sourceOffset += Character.charCount(decodedValue)
            acceptedEndOffset = sourceOffset
        }
        return text.substring(0, acceptedEndOffset)
    }

    /** 计算文本渲染所需的字体度量。 */
    public fun fontMetrics(text: String = " ", style: GlyphStyle): PixelFontMetrics {
        /** Non-empty sample needed to provide stable fallback metrics. */
        val sample = text.ifEmpty { " " }
        /** Scalar glyphs contributing to baseline and ink bounds. */
        val glyphs = mutableListOf<GlyphBitmap>()
        /** UTF-16 sample offset advanced across complete scalars. */
        var sourceOffset = 0
        while (sourceOffset < sample.length) {
            /** Raw decoded value used to preserve correct UTF-16 advancement. */
            val decodedValue = Character.codePointAt(sample, sourceOffset)
            glyphs += glyphFor(decodedValue.toGlyphCodePoint(), style)
            sourceOffset += Character.charCount(decodedValue)
        }
        /** Shared baseline selected from every scalar bitmap. */
        val baseline = glyphs.maxOfOrNull { glyph -> glyph.metrics.baselineOffset } ?: (style.cellHeight - 2)
        /** Smallest row containing visible ink. */
        var inkTop = style.cellHeight
        /** Largest row containing visible ink. */
        var inkBottom = -1
        glyphs.forEach { glyph ->
            /** Bitmap row inspected for ink. */
            for (y in 0 until glyph.height) {
                /** Bitmap column inspected for ink. */
                for (x in 0 until glyph.width) {
                    if (glyph.pixels[(y * glyph.width) + x].toInt() != 0) {
                        if (y < inkTop) inkTop = y
                        if (y > inkBottom) inkBottom = y
                    }
                }
            }
        }
        if (inkBottom < inkTop) {
            inkTop = 0
            inkBottom = 0
        }
        return PixelFontMetrics(
            cellHeight = style.cellHeight,
            baseline = baseline,
            ascent = baseline.coerceAtLeast(0),
            descent = (style.cellHeight - baseline).coerceAtLeast(0),
            inkTop = inkTop,
            inkBottom = inkBottom,
        )
    }

    /** 将文本按指定颜色绘制到 [buffer]，超过 [maxWidth] 的部分会被裁剪。 */
    public fun drawText(
        buffer: PixelBuffer,
        text: String,
        startX: Int,
        startY: Int,
        maxWidth: Int,
        color: PixelColor = PixelColor.fromRgb(255, 255, 255),
        style: GlyphStyle,
    ) {
        if (text.isEmpty() || maxWidth <= 0) {
            return
        }

        /** Prefix containing only complete code points that fit [maxWidth]. */
        val renderableText = trimToWidth(text, style, maxWidth)
        /** Horizontal draw cursor advanced by scalar glyph metrics. */
        var cursorX = startX
        /** 当前正在绘制的标量在源串中的 UTF-16 偏移。 */
        var sourceOffset = 0
        while (sourceOffset < renderableText.length) {
            /** 原始解码值，其 UTF-16 宽度决定下一个源边界位置。 */
            val decodedValue = Character.codePointAt(renderableText, sourceOffset)
            /** 完整标量字形；畸形 UTF-16 状态下为一个替换字形。 */
            val glyph = glyphFor(decodedValue.toGlyphCodePoint(), style)
            drawGlyph(
                buffer = buffer,
                glyph = glyph,
                startX = cursorX,
                startY = startY,
                color = color,
            )
            /** UTF-16 offset immediately after the complete current scalar. */
            val nextOffset = sourceOffset + Character.charCount(decodedValue)
            /** Following scalar glyph used only to resolve pair spacing. */
            val nextGlyph = if (nextOffset < renderableText.length) {
                glyphFor(Character.codePointAt(renderableText, nextOffset).toGlyphCodePoint(), style)
            } else {
                null
            }
            cursorX += glyph.metrics.advanceWidth + interGlyphSpacing(glyph, nextGlyph, style)
            sourceOffset = nextOffset
        }
    }

    /** 清空文本引擎和底层 glyph provider 的缓存。 */
    public fun clearCache() {
        glyphCache.clear()
        glyphCacheHits = 0L
        glyphCacheMisses = 0L
        glyphProvider.clearCache()
    }

    /**
     * 返回 glyph cache 的命中统计快照，主要给基线/perf 测试使用。
     */
    public fun glyphCacheStats(): GlyphCacheStats {
        return GlyphCacheStats(
            size = glyphCache.size,
            capacity = MAX_CACHE_ENTRIES,
            hits = glyphCacheHits,
            misses = glyphCacheMisses,
        )
    }

    /** Resolves and caches one complete Unicode scalar. */
    private fun glyphFor(codePoint: Int, style: GlyphStyle): GlyphBitmap {
        /** Cache identity retaining the full supplementary scalar value. */
        val key = GlyphKey(codePoint, style)
        /** Previously rasterized scalar bitmap, when present. */
        val cached = glyphCache[key]
        if (cached != null) {
            glyphCacheHits += 1L
            return cached
        }
        glyphCacheMisses += 1L
        /** Provider result produced without narrowing the scalar to `Char`. */
        val rasterized = glyphProvider.rasterizeGlyph(codePoint, style)
        glyphCache[key] = rasterized
        trimCacheIfNeeded()
        return rasterized
    }

    /** Resolves caller letter spacing plus any required visual-gap compensation. */
    private fun interGlyphSpacing(left: GlyphBitmap?, right: GlyphBitmap?, style: GlyphStyle): Int {
        if (left == null || right == null) {
            return 0
        }
        /** Extra spacing needed to satisfy the protected minimum visible gap. */
        val protectedGapCompensation = if (!requiresMinimumGap(left, right)) {
            0
        } else {
            (MIN_WIDE_PAIR_VISUAL_GAP - currentVisualGap(left, right)).coerceAtLeast(0)
        }
        return style.baseLetterSpacing + protectedGapCompensation
    }

    /** Returns whether two visible glyphs participate in protected pair spacing. */
    private fun requiresMinimumGap(left: GlyphBitmap, right: GlyphBitmap): Boolean {
        if (!left.hasVisibleInk() || !right.hasVisibleInk()) {
            return false
        }
        return left.metrics.requiresVisualGapProtection || right.metrics.requiresVisualGapProtection
    }

    /** Computes blank columns between the left ink edge and right ink edge pair. */
    private fun currentVisualGap(left: GlyphBitmap, right: GlyphBitmap): Int {
        return left.metrics.advanceWidth + right.metrics.inkLeft - left.metrics.inkRight - 1
    }

    /** Returns whether this bitmap has at least one recorded visible ink column. */
    private fun GlyphBitmap.hasVisibleInk(): Boolean {
        return metrics.inkRight >= metrics.inkLeft
    }

    /** Copies one binary glyph bitmap into the destination pixel buffer. */
    private fun drawGlyph(
        buffer: PixelBuffer,
        glyph: GlyphBitmap,
        startX: Int,
        startY: Int,
        color: PixelColor,
    ) {
        /** Glyph row copied into the destination. */
        for (y in 0 until glyph.height) {
            /** Glyph column copied into the destination. */
            for (x in 0 until glyph.width) {
                if (glyph.pixels[(y * glyph.width) + x].toInt() == 1) {
                    buffer.setPixel(startX + x, startY + y, color)
                }
            }
        }
    }

    /** Evicts least-recently-used entries until the hard cache limit is satisfied. */
    private fun trimCacheIfNeeded() {
        while (glyphCache.size > MAX_CACHE_ENTRIES) {
            /** Access-ordered iterator whose first element is the LRU cache entry. */
            val iterator = glyphCache.entries.iterator()
            iterator.next()
            iterator.remove()
        }
    }

    private data class GlyphKey(
        /** Complete Unicode scalar used for provider lookup. */
        val codePoint: Int,
        /** Immutable style dimensions affecting the rasterized bitmap. */
        val style: GlyphStyle,
    )
}

/**
 * glyph 缓存命中统计快照。
 */
public data class GlyphCacheStats(
    /** Number of entries currently retained by the engine. */
    val size: Int,
    /** Maximum entries retained before LRU eviction. */
    val capacity: Int,
    /** Cumulative successful cache lookups since the last clear. */
    val hits: Long,
    /** Cumulative provider lookups since the last clear. */
    val misses: Long,
) {
    /** Total observed cache requests. */
    val total: Long get() = hits + misses
    /** Hit ratio in `0.0..1.0`, or zero before any request. */
    val hitRate: Double get() = if (total == 0L) 0.0 else hits.toDouble() / total.toDouble()
}

/** Returns whether one scalar participates in the engine's minimum visual-gap policy. */
private fun requiresVisualGapProtection(codePoint: Int, isWideGlyph: Boolean): Boolean {
    if (isWideGlyph) {
        return true
    }
    return when (Character.UnicodeBlock.of(codePoint)) {
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
        Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
        Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT,
        Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION,
        Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS,
        Character.UnicodeBlock.HIRAGANA,
        Character.UnicodeBlock.KATAKANA,
        Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS,
        Character.UnicodeBlock.HANGUL_JAMO,
        Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO,
        Character.UnicodeBlock.HANGUL_SYLLABLES,
        Character.UnicodeBlock.BOPOMOFO,
        Character.UnicodeBlock.BOPOMOFO_EXTENDED -> true
        else -> false
    }
}

/** Converts a decoded UTF-16 value into a valid glyph key without mutating source text. */
private fun Int.toGlyphCodePoint(): Int {
    return if (this in SURROGATE_CODE_POINT_RANGE) REPLACEMENT_CODE_POINT else this
}

/** Rejects invalid public scalar keys before they can alias cache or pack entries. */
private fun requireUnicodeScalar(codePoint: Int) {
    require(codePoint in UNICODE_CODE_POINT_RANGE && codePoint !in SURROGATE_CODE_POINT_RANGE) {
        "codePoint must be a Unicode scalar value: $codePoint"
    }
}

/** Printable ASCII values use the configured narrow fallback cell. */
private val ASCII_PRINTABLE_RANGE: IntRange = 32..126

/** 排除代理项之前的完整 Unicode 码位区间。 */
private val UNICODE_CODE_POINT_RANGE: IntRange = 0x0000..0x10FFFF

/** UTF-16 代理项取值不属于 Unicode scalar value。 */
private val SURROGATE_CODE_POINT_RANGE: IntRange = 0xD800..0xDFFF

/**
 * 畸形 UTF-16 输入的确定性替换标量。
 *
 * 这是健壮性契约，不是历史 API 兼容：调用方传入孤立 surrogate 码元时，引擎用 U+FFFD 给出可
 * 预测的渲染结果，而不是崩溃或产生随机字形。
 */
private const val REPLACEMENT_CODE_POINT: Int = 0xFFFD
