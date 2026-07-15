package com.purride.pixelcompat.legacy

import com.purride.pixelcore.GlyphBitmap
import com.purride.pixelcore.GlyphMetrics
import com.purride.pixelcore.GlyphProvider
import com.purride.pixelcore.GlyphStyle

/** Creates a Char-only provider compiled against the frozen pre-code-point engine interface. */
public object LegacyGlyphProviderProbe {
    /** Returns one old binary whose class file has no `rasterizeGlyph(Int, ...)` implementation. */
    @JvmStatic
    public fun create(): GlyphProvider = LegacyCharOnlyGlyphProvider

    /** Clears requests retained by the old singleton before each current-runtime invocation. */
    @JvmStatic
    public fun reset() {
        LegacyCharOnlyGlyphProvider.requests.clear()
    }

    /** Returns the exact BMP values observed through the frozen Char method. */
    @JvmStatic
    public fun requestSummary(): String {
        return LegacyCharOnlyGlyphProvider.requests.joinToString(separator = ",") { character ->
            character.code.toString(16).uppercase()
        }
    }

    /** Old provider implementation deliberately limited to the pre-M5-3D Char SPI. */
    private object LegacyCharOnlyGlyphProvider : GlyphProvider {
        /** Ordered compatibility requests received from the current engine runtime. */
        val requests: MutableList<Char> = mutableListOf()

        /** Rasterizes one frozen Char request into a deterministic solid bitmap. */
        override fun rasterizeGlyph(character: Char, style: GlyphStyle): GlyphBitmap {
            requests += character
            /** ASCII values remain narrow; replacement and other non-ASCII values remain wide. */
            val width = if (character.code in 32..126) {
                style.narrowAdvanceWidth
            } else {
                style.wideAdvanceWidth
            }
            return GlyphBitmap(
                width = width,
                height = style.cellHeight,
                pixels = ByteArray(width * style.cellHeight) { 1 },
                metrics = GlyphMetrics(
                    advanceWidth = width,
                    baselineOffset = style.cellHeight - 1,
                    isWideGlyph = width > style.narrowAdvanceWidth,
                    inkLeft = 0,
                    inkRight = width - 1,
                ),
            )
        }
    }
}
