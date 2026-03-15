package com.purride.pixellauncherv2.render

data class GlyphStyle(
    val cellHeight: Int,
    val narrowAdvanceWidth: Int,
    val wideAdvanceWidth: Int,
    val oversampleFactor: Int,
    val narrowMinimumSampleRatio: Float,
    val wideMinimumSampleRatio: Float,
    val narrowTextSizeRatio: Float,
    val wideTextSizeRatio: Float,
    val narrowFontWeight: PixelFontWeight,
    val wideFontWeight: PixelFontWeight,
    val narrowFontFamily: PixelFontFamily,
    val wideFontFamily: PixelFontFamily,
) {
    companion object {
        val APP_LABEL_16 = GlyphStyle(
            cellHeight = 16,
            narrowAdvanceWidth = 8,
            wideAdvanceWidth = 16,
            oversampleFactor = 8,
            narrowMinimumSampleRatio = 0.24f,
            wideMinimumSampleRatio = 0.18f,
            narrowTextSizeRatio = 0.82f,
            wideTextSizeRatio = 0.88f,
            narrowFontWeight = PixelFontWeight.BOLD,
            wideFontWeight = PixelFontWeight.NORMAL,
            narrowFontFamily = PixelFontFamily.MONOSPACE,
            wideFontFamily = PixelFontFamily.DEFAULT,
        )

        val UI_SMALL_10 = GlyphStyle(
            cellHeight = 10,
            narrowAdvanceWidth = 6,
            wideAdvanceWidth = 10,
            oversampleFactor = 6,
            narrowMinimumSampleRatio = 0.30f,
            wideMinimumSampleRatio = 0.24f,
            narrowTextSizeRatio = 0.90f,
            wideTextSizeRatio = 0.84f,
            narrowFontWeight = PixelFontWeight.NORMAL,
            wideFontWeight = PixelFontWeight.BOLD,
            narrowFontFamily = PixelFontFamily.MONOSPACE,
            wideFontFamily = PixelFontFamily.DEFAULT,
        )
    }
}

enum class PixelFontWeight {
    NORMAL,
    BOLD,
}

enum class PixelFontFamily {
    DEFAULT,
    MONOSPACE,
}

data class GlyphMetrics(
    val advanceWidth: Int,
    val baselineOffset: Int,
    val isWideGlyph: Boolean,
)

data class GlyphBitmap(
    val width: Int,
    val height: Int,
    val pixels: ByteArray,
    val metrics: GlyphMetrics,
)

interface GlyphSource {
    fun findGlyph(character: Char, style: GlyphStyle): GlyphBitmap?

    fun clearCache() = Unit
}

interface GlyphProvider {
    fun rasterizeGlyph(character: Char, style: GlyphStyle): GlyphBitmap

    fun clearCache() = Unit
}

class CompositeGlyphProvider(
    private val sources: List<GlyphSource>,
) : GlyphProvider {

    override fun rasterizeGlyph(character: Char, style: GlyphStyle): GlyphBitmap {
        return sources.firstNotNullOfOrNull { source -> source.findGlyph(character, style) }
            ?: emptyGlyph(character, style)
    }

    override fun clearCache() {
        sources.forEach { source -> source.clearCache() }
    }

    private fun emptyGlyph(character: Char, style: GlyphStyle): GlyphBitmap {
        val isWideGlyph = character.code !in 32..126
        val width = if (isWideGlyph) style.wideAdvanceWidth else style.narrowAdvanceWidth
        return GlyphBitmap(
            width = width,
            height = style.cellHeight,
            pixels = ByteArray(width * style.cellHeight),
            metrics = GlyphMetrics(
                advanceWidth = width,
                baselineOffset = style.cellHeight - 2,
                isWideGlyph = isWideGlyph,
            ),
        )
    }
}

class BitmapGlyphSource(
    private val packs: List<PixelGlyphPack>,
) : GlyphSource {

    private val unpackedGlyphCache = mutableMapOf<GlyphCacheKey, GlyphBitmap>()

    override fun findGlyph(character: Char, style: GlyphStyle): GlyphBitmap? {
        val codePoint = character.code
        for (pack in packs) {
            if (pack.manifest.cellHeight != style.cellHeight) {
                continue
            }

            val record = pack.glyphs[codePoint] ?: continue
            val cacheKey = GlyphCacheKey(pack.manifest.packId, codePoint)
            return unpackedGlyphCache.getOrPut(cacheKey) {
                val unpackedPixels = unpackBits(
                    packed = record.packedPixels,
                    pixelCount = record.width * pack.manifest.cellHeight,
                )
                GlyphBitmap(
                    width = record.width,
                    height = pack.manifest.cellHeight,
                    pixels = unpackedPixels,
                    metrics = GlyphMetrics(
                        advanceWidth = record.advanceWidth,
                        baselineOffset = pack.manifest.baseline,
                        isWideGlyph = record.advanceWidth > style.narrowAdvanceWidth,
                    ),
                )
            }
        }
        return null
    }

    override fun clearCache() {
        unpackedGlyphCache.clear()
    }

    private fun unpackBits(packed: ByteArray, pixelCount: Int): ByteArray {
        val pixels = ByteArray(pixelCount)
        for (index in 0 until pixelCount) {
            val packedByte = packed[index / 8].toInt() and 0xFF
            val bitShift = 7 - (index % 8)
            pixels[index] = if (((packedByte shr bitShift) and 0x01) == 1) 1 else 0
        }
        return pixels
    }

    private data class GlyphCacheKey(
        val packId: String,
        val codePoint: Int,
    )
}

class PixelFontEngine(
    private val glyphProvider: GlyphProvider,
) {

    private val glyphCache = linkedMapOf<GlyphKey, GlyphBitmap>()

    fun measureText(text: String, style: GlyphStyle): Int {
        return text.sumOf { character -> glyphFor(character, style).metrics.advanceWidth }
    }

    fun trimToWidth(text: String, style: GlyphStyle, maxWidth: Int): String {
        if (text.isEmpty() || maxWidth <= 0) {
            return ""
        }

        val builder = StringBuilder()
        var consumedWidth = 0
        text.forEach { character ->
            val glyph = glyphFor(character, style)
            val nextWidth = consumedWidth + glyph.metrics.advanceWidth
            if (nextWidth > maxWidth) {
                return builder.toString()
            }
            builder.append(character)
            consumedWidth = nextWidth
        }
        return builder.toString()
    }

    fun drawText(
        buffer: PixelBuffer,
        text: String,
        startX: Int,
        startY: Int,
        maxWidth: Int,
        style: GlyphStyle,
    ) {
        if (text.isEmpty() || maxWidth <= 0) {
            return
        }

        val renderableText = trimToWidth(text, style, maxWidth)
        var cursorX = startX
        renderableText.forEach { character ->
            val glyph = glyphFor(character, style)
            drawGlyph(
                buffer = buffer,
                glyph = glyph,
                startX = cursorX,
                startY = startY,
            )
            cursorX += glyph.metrics.advanceWidth
        }
    }

    fun clearCache() {
        glyphCache.clear()
        glyphProvider.clearCache()
    }

    private fun glyphFor(character: Char, style: GlyphStyle): GlyphBitmap {
        val key = GlyphKey(character, style)
        return glyphCache.getOrPut(key) {
            glyphProvider.rasterizeGlyph(character, style)
        }.also {
            trimCacheIfNeeded()
        }
    }

    private fun drawGlyph(buffer: PixelBuffer, glyph: GlyphBitmap, startX: Int, startY: Int) {
        for (y in 0 until glyph.height) {
            for (x in 0 until glyph.width) {
                if (glyph.pixels[(y * glyph.width) + x].toInt() == 1) {
                    buffer.setPixel(startX + x, startY + y)
                }
            }
        }
    }

    private fun trimCacheIfNeeded() {
        val maxCacheEntries = 512
        while (glyphCache.size > maxCacheEntries) {
            val iterator = glyphCache.entries.iterator()
            iterator.next()
            iterator.remove()
        }
    }

    private data class GlyphKey(
        val character: Char,
        val style: GlyphStyle,
    )
}
