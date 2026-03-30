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
        @Volatile
        private var configuredFontSize: PixelFontSize = PixelFontCatalog.defaultFontSize

        @Volatile
        private var configuredFontStyle: PixelFontStyle = PixelFontCatalog.defaultFontStyle

        val APP_LABEL_16: GlyphStyle
            get() = styleForAppLabel(configuredFontSize, configuredFontStyle)

        val UI_SMALL_10: GlyphStyle
            get() = styleForUiSmall(configuredFontSize, configuredFontStyle)

        fun configure(fontSize: PixelFontSize, fontStyle: PixelFontStyle) {
            configuredFontSize = fontSize
            configuredFontStyle = fontStyle
        }

        private fun styleForAppLabel(size: PixelFontSize, style: PixelFontStyle): GlyphStyle {
            val cellHeight = size.px
            return GlyphStyle(
                cellHeight = cellHeight,
                narrowAdvanceWidth = narrowAdvanceWidth(size, style),
                wideAdvanceWidth = wideAdvanceWidth(size, style),
                oversampleFactor = 1,
                narrowMinimumSampleRatio = 1f,
                wideMinimumSampleRatio = 1f,
                narrowTextSizeRatio = 1f,
                wideTextSizeRatio = 1f,
                narrowFontWeight = PixelFontWeight.NORMAL,
                wideFontWeight = PixelFontWeight.NORMAL,
                narrowFontFamily = PixelFontFamily.MONOSPACE,
                wideFontFamily = PixelFontFamily.DEFAULT,
            )
        }

        private fun styleForUiSmall(size: PixelFontSize, style: PixelFontStyle): GlyphStyle {
            val cellHeight = size.px
            return GlyphStyle(
                cellHeight = cellHeight,
                narrowAdvanceWidth = narrowAdvanceWidth(size, style),
                wideAdvanceWidth = wideAdvanceWidth(size, style),
                oversampleFactor = 1,
                narrowMinimumSampleRatio = 1f,
                wideMinimumSampleRatio = 1f,
                narrowTextSizeRatio = 1f,
                wideTextSizeRatio = 1f,
                narrowFontWeight = PixelFontWeight.NORMAL,
                wideFontWeight = PixelFontWeight.NORMAL,
                narrowFontFamily = PixelFontFamily.MONOSPACE,
                wideFontFamily = PixelFontFamily.DEFAULT,
            )
        }

        private fun narrowAdvanceWidth(size: PixelFontSize, style: PixelFontStyle): Int {
            return when (style) {
                PixelFontStyle.MONO -> size.px
                PixelFontStyle.PROP -> when (size) {
                    PixelFontSize.PX_8 -> 4
                    PixelFontSize.PX_10 -> 6
                    PixelFontSize.PX_12 -> 8
                }
            }
        }

        private fun wideAdvanceWidth(size: PixelFontSize, style: PixelFontStyle): Int {
            return when (style) {
                PixelFontStyle.MONO -> size.px
                PixelFontStyle.PROP -> size.px
            }
        }
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
    val requiresVisualGapProtection: Boolean = false,
    val inkLeft: Int = 0,
    val inkRight: Int = advanceWidth - 1,
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
                requiresVisualGapProtection = requiresVisualGapProtection(character, isWideGlyph),
                inkLeft = width,
                inkRight = -1,
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
                            character = character,
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

    private fun computeInkBounds(width: Int, height: Int, pixels: ByteArray): Pair<Int, Int> {
        var left = width
        var right = -1
        for (y in 0 until height) {
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
        val packId: String,
        val codePoint: Int,
    )
}

class PixelFontEngine(
    private val glyphProvider: GlyphProvider,
) {
    companion object {
        private const val MIN_WIDE_PAIR_VISUAL_GAP = 1
    }

    private val glyphCache = linkedMapOf<GlyphKey, GlyphBitmap>()

    fun measureText(text: String, style: GlyphStyle): Int {
        var totalWidth = 0
        var previousGlyph: GlyphBitmap? = null
        text.forEach { character ->
            val glyph = glyphFor(character, style)
            totalWidth += glyph.metrics.advanceWidth + interGlyphSpacing(previousGlyph, glyph)
            previousGlyph = glyph
        }
        return totalWidth
    }

    fun trimToWidth(text: String, style: GlyphStyle, maxWidth: Int): String {
        if (text.isEmpty() || maxWidth <= 0) {
            return ""
        }

        val builder = StringBuilder(text.length)
        var consumedWidth = 0
        var previousGlyph: GlyphBitmap? = null
        text.forEach { character ->
            val glyph = glyphFor(character, style)
            val nextWidth = consumedWidth + glyph.metrics.advanceWidth + interGlyphSpacing(previousGlyph, glyph)
            if (nextWidth > maxWidth) {
                return builder.toString()
            }
            builder.append(character)
            consumedWidth = nextWidth
            previousGlyph = glyph
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
        renderableText.indices.forEach { index ->
            val character = renderableText[index]
            val glyph = glyphFor(character, style)
            drawGlyph(
                buffer = buffer,
                glyph = glyph,
                startX = cursorX,
                startY = startY,
            )
            val nextGlyph = renderableText.getOrNull(index + 1)?.let { nextCharacter ->
                glyphFor(nextCharacter, style)
            }
            cursorX += glyph.metrics.advanceWidth + interGlyphSpacing(glyph, nextGlyph)
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

    private fun interGlyphSpacing(left: GlyphBitmap?, right: GlyphBitmap?): Int {
        if (left == null || right == null) {
            return 0
        }
        return if (!requiresMinimumGap(left, right)) {
            0
        } else {
            (MIN_WIDE_PAIR_VISUAL_GAP - currentVisualGap(left, right)).coerceAtLeast(0)
        }
    }

    private fun requiresMinimumGap(left: GlyphBitmap, right: GlyphBitmap): Boolean {
        if (!left.hasVisibleInk() || !right.hasVisibleInk()) {
            return false
        }
        return left.metrics.requiresVisualGapProtection || right.metrics.requiresVisualGapProtection
    }

    private fun currentVisualGap(left: GlyphBitmap, right: GlyphBitmap): Int {
        return left.metrics.advanceWidth + right.metrics.inkLeft - left.metrics.inkRight - 1
    }

    private fun GlyphBitmap.hasVisibleInk(): Boolean {
        return metrics.inkRight >= metrics.inkLeft
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

private fun requiresVisualGapProtection(character: Char, isWideGlyph: Boolean): Boolean {
    if (isWideGlyph) {
        return true
    }
    return when (Character.UnicodeBlock.of(character)) {
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
