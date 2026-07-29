package com.purride.pixelcore

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.LinkedHashMap

/**
 * PGLY v1/v2 的只读索引表示。
 *
 * 它只保留原始二进制和 primitive 索引，不为每个字形创建 Map 节点或记录对象。
 */
public class PixelIndexedGlyphPack internal constructor(
    /** 与二进制头和 catalog 一致的 manifest。 */
    public val manifest: PixelGlyphPackManifest,
    /** 只读 PGLY v1/v2 二进制。 */
    internal val binary: ByteBuffer,
    /** 按 Unicode scalar 严格递增的码点。 */
    internal val codePoints: IntArray,
    /** 每条记录的真实前进宽度。 */
    internal val advances: IntArray,
    /** 每条记录的位图宽度。 */
    internal val widths: IntArray,
    /** 每条记录相对排版光标的水平偏移。 */
    internal val bitmapOffsetsX: IntArray,
    /** 每条记录相对行顶的垂直偏移。 */
    internal val bitmapOffsetsY: IntArray,
    /** 每条记录自身的位图高度。 */
    internal val heights: IntArray,
    /** 每条记录压缩像素在 [binary] 中的起点。 */
    internal val dataOffsets: IntArray,
    /** 每条记录压缩像素字节数。 */
    internal val dataLengths: IntArray,
) {
    /** 当前 pack 中的字形数量。 */
    public val glyphCount: Int
        get() = codePoints.size

    /** 原始二进制与八组 Int 索引的保守字节数。 */
    public val byteSize: Long
        get() = binary.limit().toLong() + glyphCount.toLong() * INDEX_BYTES_PER_GLYPH

    /** 判断 pack 是否包含完整 Unicode scalar。 */
    public fun contains(codePoint: Int): Boolean = findIndex(codePoint) >= 0

    /** 二分查找指定 Unicode scalar 的记录索引。 */
    internal fun findIndex(codePoint: Int): Int = codePoints.binarySearch(codePoint)

    /** 读取一条记录的压缩像素副本。 */
    internal fun packedPixels(index: Int): ByteArray {
        require(index in 0 until glyphCount) { "glyph index out of bounds: $index" }
        val duplicate = binary.asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN)
        duplicate.position(dataOffsets[index])
        return ByteArray(dataLengths[index]).also(duplicate::get)
    }

    private companion object {
        /** code point、advance、width、X/Y placement、高度、数据 offset/length 八个 Int。 */
        const val INDEX_BYTES_PER_GLYPH: Long = 32L
    }
}

/** 安全解析 PGLY v1/v2 为 [PixelIndexedGlyphPack]。 */
public object PixelIndexedGlyphPackParser {
    /** 从有界输入流解析并可选校验 SHA-256。 */
    @JvmOverloads
    public fun parseBinary(
        manifest: PixelGlyphPackManifest,
        inputStream: InputStream,
        expectedSha256: String? = null,
    ): PixelIndexedGlyphPack {
        val bytes = inputStream.use { input ->
            input.readBoundedBytes(PixelResourceSafetyLimits.MaxGlyphBinaryBytes, "indexed glyph binary")
        }
        bytes.requireSha256(expectedSha256, "indexed glyph binary")
        return parseBinary(manifest, ByteBuffer.wrap(bytes))
    }

    /** 从只读或 mmap [ByteBuffer] 解析，不复制完整二进制。 */
    @JvmOverloads
    public fun parseBinary(
        manifest: PixelGlyphPackManifest,
        byteBuffer: ByteBuffer,
        expectedSha256: String? = null,
    ): PixelIndexedGlyphPack {
        val source = byteBuffer.asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN)
        source.position(0)
        require(source.remaining() <= PixelResourceSafetyLimits.MaxGlyphBinaryBytes) {
            "indexed glyph binary exceeds byte limit ${PixelResourceSafetyLimits.MaxGlyphBinaryBytes}"
        }
        if (expectedSha256 != null) {
            require(sha256(source) == expectedSha256.lowercase()) { "indexed glyph binary SHA-256 mismatch" }
            source.position(0)
        }
        require(source.remaining() >= HEADER_BYTES) { "indexed glyph binary is truncated before header" }
        val magic = source.int
        require(magic == MAGIC) { "Unexpected glyph pack magic: $magic" }
        val version = source.int
        require(version == VERSION_V1 || version == VERSION_V2) { "Unsupported glyph pack version: $version" }
        val cellHeight = source.int
        require(cellHeight == manifest.cellHeight) {
            "Manifest cellHeight ${manifest.cellHeight} does not match binary $cellHeight"
        }
        val glyphCount = source.int
        require(glyphCount in 0..PixelResourceSafetyLimits.MaxGlyphCount) {
            "glyph count $glyphCount exceeds ${PixelResourceSafetyLimits.MaxGlyphCount}"
        }
        val codePoints = IntArray(glyphCount)
        val advances = IntArray(glyphCount)
        val widths = IntArray(glyphCount)
        val bitmapOffsetsX = IntArray(glyphCount)
        val bitmapOffsetsY = IntArray(glyphCount)
        val heights = IntArray(glyphCount)
        val dataOffsets = IntArray(glyphCount)
        val dataLengths = IntArray(glyphCount)
        var previousCodePoint = -1
        repeat(glyphCount) { index ->
            val recordHeaderBytes = if (version == VERSION_V2) RECORD_HEADER_BYTES_V2 else RECORD_HEADER_BYTES_V1
            require(source.remaining() >= recordHeaderBytes) { "glyph[$index] is truncated before record header" }
            val codePoint = source.int
            require(Character.isValidCodePoint(codePoint) && codePoint !in 0xD800..0xDFFF) {
                "glyph[$index] code point is not a Unicode scalar"
            }
            require(codePoint > previousCodePoint) { "glyph code points must be strictly increasing" }
            previousCodePoint = codePoint
            val advance = source.int
            val bitmapOffsetX = if (version == VERSION_V2) source.int else 0
            val bitmapOffsetY = if (version == VERSION_V2) source.int else 0
            val width = source.int
            val height = if (version == VERSION_V2) source.int else cellHeight
            val length = source.int
            require(advance in 1..PixelResourceSafetyLimits.MaxDimension) { "glyph[$index] advance is invalid" }
            require(width in 1..PixelResourceSafetyLimits.MaxDimension) { "glyph[$index] width is invalid" }
            require(height in 1..PixelResourceSafetyLimits.MaxDimension) { "glyph[$index] height is invalid" }
            require(bitmapOffsetX in -PixelResourceSafetyLimits.MaxDimension..PixelResourceSafetyLimits.MaxDimension) {
                "glyph[$index] bitmapOffsetX is invalid"
            }
            require(bitmapOffsetY in -PixelResourceSafetyLimits.MaxDimension..PixelResourceSafetyLimits.MaxDimension) {
                "glyph[$index] bitmapOffsetY is invalid"
            }
            val expectedLength = ((width.toLong() * height.toLong() + 7L) / 8L).toInt()
            require(length == expectedLength) { "glyph[$index] data length does not match dimensions" }
            require(length <= source.remaining()) { "glyph[$index] packed pixels are truncated" }
            codePoints[index] = codePoint
            advances[index] = advance
            widths[index] = width
            bitmapOffsetsX[index] = bitmapOffsetX
            bitmapOffsetsY[index] = bitmapOffsetY
            heights[index] = height
            dataOffsets[index] = source.position()
            dataLengths[index] = length
            source.position(source.position() + length)
        }
        require(!source.hasRemaining()) { "indexed glyph binary contains trailing bytes" }
        return PixelIndexedGlyphPack(
            manifest = manifest,
            binary = byteBuffer.asReadOnlyBuffer().apply { position(0) }.slice().asReadOnlyBuffer(),
            codePoints = codePoints,
            advances = advances,
            widths = widths,
            bitmapOffsetsX = bitmapOffsetsX,
            bitmapOffsetsY = bitmapOffsetsY,
            heights = heights,
            dataOffsets = dataOffsets,
            dataLengths = dataLengths,
        )
    }

    /** 计算 ByteBuffer 全部内容的 SHA-256，不改变调用方位置。 */
    private fun sha256(buffer: ByteBuffer): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val duplicate = buffer.asReadOnlyBuffer().apply { position(0) }
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        while (duplicate.hasRemaining()) {
            val count = minOf(chunk.size, duplicate.remaining())
            duplicate.get(chunk, 0, count)
            digest.update(chunk, 0, count)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private const val MAGIC: Int = 0x50474C59
    private const val VERSION_V1: Int = 1
    private const val VERSION_V2: Int = 2
    private const val HEADER_BYTES: Int = 16
    private const val RECORD_HEADER_BYTES_V1: Int = 16
    private const val RECORD_HEADER_BYTES_V2: Int = 28
}

/** 从 indexed pack 按需解压字形，并以字节预算限制热字形缓存。 */
public class IndexedBitmapGlyphSource @JvmOverloads public constructor(
    /** 按调用方优先级排列、且应属于同一字体家族的 pack。 */
    private val packs: List<PixelIndexedGlyphPack>,
    /** 解压后像素缓存的最大字节数。 */
    private val maxUnpackedBytes: Long = DEFAULT_UNPACKED_BYTES,
) : GlyphSource {
    init {
        require(packs.isNotEmpty()) { "indexed glyph source requires at least one pack" }
        require(maxUnpackedBytes > 0L) { "maxUnpackedBytes must be > 0" }
    }

    /** access-order 热字形缓存。 */
    private val cache = LinkedHashMap<IndexedGlyphCacheKey, GlyphBitmap>(16, 0.75f, true)
    /** 当前解压像素和度量的保守字节数。 */
    private var cacheBytes: Long = 0L

    /** 查找并按需解压完整 Unicode scalar。 */
    override fun findGlyph(codePoint: Int, style: GlyphStyle): GlyphBitmap? {
        require(Character.isValidCodePoint(codePoint) && codePoint !in 0xD800..0xDFFF) {
            "codePoint must be a Unicode scalar"
        }
        for (pack in packs) {
            if (pack.manifest.cellHeight != style.cellHeight) continue
            val index = pack.findIndex(codePoint)
            if (index < 0) continue
            val key = IndexedGlyphCacheKey(pack.manifest.packId, codePoint, style.narrowAdvanceWidth)
            cache[key]?.let { return it }
            val bitmap = unpack(pack, index, codePoint, style)
            putCached(key, bitmap)
            return bitmap
        }
        return null
    }

    /** 清理解压结果但保留 mmap/压缩索引。 */
    override fun clearCache() {
        cache.clear()
        cacheBytes = 0L
    }

    /** 解压一条记录并计算真实墨迹边界。 */
    private fun unpack(
        pack: PixelIndexedGlyphPack,
        index: Int,
        codePoint: Int,
        style: GlyphStyle,
    ): GlyphBitmap {
        val width = pack.widths[index]
        val height = pack.heights[index]
        val bitmapOffsetX = pack.bitmapOffsetsX[index]
        val bitmapOffsetY = pack.bitmapOffsetsY[index]
        val packed = pack.packedPixels(index)
        val pixels = ByteArray(width * height)
        var inkLeft = width
        var inkRight = -1
        for (pixelIndex in pixels.indices) {
            val value = (packed[pixelIndex / 8].toInt() ushr (7 - pixelIndex % 8)) and 1
            pixels[pixelIndex] = value.toByte()
            if (value != 0) {
                val x = pixelIndex % width
                if (x < inkLeft) inkLeft = x
                if (x > inkRight) inkRight = x
            }
        }
        val advance = pack.advances[index]
        val isWide = advance > style.narrowAdvanceWidth
        return GlyphBitmap(
            width = width,
            height = height,
            pixels = pixels,
            metrics = GlyphMetrics(
                advanceWidth = advance,
                baselineOffset = pack.manifest.baseline,
                isWideGlyph = isWide,
                requiresVisualGapProtection = requiresVisualGapProtection(codePoint, isWide),
                inkLeft = if (inkRight >= inkLeft) inkLeft + bitmapOffsetX else advance,
                inkRight = if (inkRight >= inkLeft) inkRight + bitmapOffsetX else -1,
                bitmapOffsetX = bitmapOffsetX,
                bitmapOffsetY = bitmapOffsetY,
            ),
        )
    }

    /** 插入热字形并淘汰最旧条目直到回到预算内。 */
    private fun putCached(key: IndexedGlyphCacheKey, bitmap: GlyphBitmap) {
        val bytes = bitmap.pixels.size.toLong() + GLYPH_OBJECT_BYTES
        if (bytes > maxUnpackedBytes) return
        cache.put(key, bitmap)?.let { previous -> cacheBytes -= previous.pixels.size + GLYPH_OBJECT_BYTES }
        cacheBytes += bytes
        while (cacheBytes > maxUnpackedBytes && cache.isNotEmpty()) {
            val eldest = cache.entries.first()
            cache.remove(eldest.key)
            cacheBytes -= eldest.value.pixels.size + GLYPH_OBJECT_BYTES
        }
    }

    /** 热字形缓存完整 key。 */
    private data class IndexedGlyphCacheKey(
        /** pack 稳定 ID。 */
        val packId: String,
        /** Unicode scalar。 */
        val codePoint: Int,
        /** 影响宽字符分类的窄格 advance。 */
        val narrowAdvanceWidth: Int,
    )

    private companion object {
        const val DEFAULT_UNPACKED_BYTES: Long = 2L * 1024L * 1024L
        const val GLYPH_OBJECT_BYTES: Long = 64L
    }
}
