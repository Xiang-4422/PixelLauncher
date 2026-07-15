package com.purride.pixelui.internal

import com.purride.pixelcore.PixelClusterTextRasterizer
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.internal.text.UnicodeGraphemeBreakProperty
import com.purride.pixelui.internal.text.UnicodeGraphemeData
import com.purride.pixelui.internal.text.bidi.UnicodeBidiData

/** Shared Unicode cluster classification and deterministic paragraph fallback policy. */
internal object PixelParagraphClusterSupport {
    /** One replacement scalar painted for an unsupported non-ignorable cluster. */
    internal const val MissingClusterText: String = "\uFFFD"

    /** Returns whether [cluster] is one Unicode hard line-break sequence. */
    internal fun isHardLineBreak(cluster: String): Boolean {
        if (cluster == "\r\n") return true
        if (cluster.isEmpty()) return false
        /** First scalar value of a cluster that can only be a single hard-break code point here. */
        val codePoint = Character.codePointAt(cluster, 0)
        return Character.charCount(codePoint) == cluster.length && codePoint in HARD_BREAK_CODE_POINTS
    }

    /**
     * Resolves the exact String passed to measurement and painting for one logical cluster.
     *
     * Single scalars remain compatible with every existing rasterizer. Multi-scalar clusters are
     * passed through only when a consumer explicitly implements [PixelClusterTextRasterizer]; an
     * unsupported cluster produces exactly one replacement glyph. Default-ignorable-only clusters
     * remain in the backing text and Bidi input but consume no width and paint no tofu.
     */
    internal fun resolveRenderableText(
        cluster: String,
        rasterizer: PixelTextRasterizer,
    ): String {
        if (cluster.isEmpty() || isHardLineBreak(cluster) || isDefaultIgnorableOnly(cluster)) {
            return ""
        }
        /** Number of scalar values contained by this complete grapheme. */
        val codePointCount = Character.codePointCount(cluster, 0, cluster.length)
        if (codePointCount == 1) return cluster
        return if ((rasterizer as? PixelClusterTextRasterizer)?.canRasterizeCluster(cluster) == true) {
            cluster
        } else {
            MissingClusterText
        }
    }

    /** Mirrors deterministic paired punctuation when UAX #9 resolves a cluster to an odd level. */
    internal fun mirrorForOddLevel(text: String, bidiLevel: Int): String {
        if (text.isEmpty() || bidiLevel % 2 == 0) return text
        /** Mirrored result retaining every non-paired scalar exactly. */
        val mirrored = StringBuilder(text.length)
        /** UTF-16 offset advanced by complete scalar values. */
        var offset = 0
        while (offset < text.length) {
            /** Current scalar whose paired glyph may need substitution. */
            val codePoint = Character.codePointAt(text, offset)
            mirrored.appendCodePoint(UnicodeBidiData.mirroredCodePoint(codePoint))
            offset += Character.charCount(codePoint)
        }
        return mirrored.toString()
    }

    /** Returns whether every scalar is an engine-recognized zero-width formatting control. */
    private fun isDefaultIgnorableOnly(cluster: String): Boolean {
        /** UTF-16 offset advanced while every scalar remains ignorable. */
        var offset = 0
        while (offset < cluster.length) {
            /** Scalar classified only by fixed Unicode 17 generated properties. */
            val codePoint = Character.codePointAt(cluster, offset)
            /** Grapheme property used to identify format/control boundaries without platform ICU. */
            val property = UnicodeGraphemeData.graphemeBreakProperty(codePoint)
            if (
                property != UnicodeGraphemeBreakProperty.CONTROL &&
                property != UnicodeGraphemeBreakProperty.ZWJ &&
                !UnicodeGraphemeData.isDefaultIgnorable(codePoint)
            ) {
                return false
            }
            offset += Character.charCount(codePoint)
        }
        return true
    }

    /** Unicode hard-break scalars handled as source boundaries rather than paintable clusters. */
    private val HARD_BREAK_CODE_POINTS: Set<Int> = setOf(
        0x000A, // LF
        0x000D, // CR
        0x0085, // NEL
        0x2028, // LINE SEPARATOR
        0x2029, // PARAGRAPH SEPARATOR
    )

}
