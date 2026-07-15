package com.purride.pixelui.internal.text

import com.purride.pixelui.PixelTextEditingValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies Android-free editing normalization shared by the production InputConnection. */
class PixelTextEditingNormalizationTest {
    /** A collapsed interior offset uses downstream affinity and composition expands outwards. */
    @Test
    fun normalizeGraphemeOffsets_snapsSelectionAndExpandsComposition(): Unit {
        val decomposed = "e\u0301"
        val value = PixelTextEditingValue(
            text = decomposed,
            selectionStart = 1,
            selectionEnd = 1,
            compositionStart = 1,
            compositionEnd = 2,
        )

        val normalized = value.normalizeGraphemeOffsets()

        assertSame(decomposed, normalized.text)
        assertEquals(2, normalized.selectionStart)
        assertEquals(2, normalized.selectionEnd)
        assertEquals(0, normalized.compositionStart)
        assertEquals(2, normalized.compositionEnd)
    }

    /** Invalid ranges clamp deterministically without rewriting decomposed source code units. */
    @Test
    fun normalizeGraphemeOffsets_clampsAndClearsInvalidComposition(): Unit {
        val decomposed = "A\u030A"

        val normalized = PixelTextEditingValue(
            text = decomposed,
            selectionStart = 99,
            selectionEnd = -4,
            compositionStart = 2,
            compositionEnd = 1,
        ).normalizeGraphemeOffsets()

        assertEquals("A\u030A", normalized.text)
        assertEquals(2, normalized.selectionStart)
        assertEquals(2, normalized.selectionEnd)
        assertEquals(-1, normalized.compositionStart)
        assertEquals(-1, normalized.compositionEnd)
    }

    /** New IME text accepts scalar strings and rejects every orphaned surrogate shape. */
    @Test
    fun isWellFormedUtf16_rejectsOrphanedSurrogates(): Unit {
        assertTrue(isWellFormedUtf16("Latin e\u0301 😀"))
        assertFalse(isWellFormedUtf16("\uD83D"))
        assertFalse(isWellFormedUtf16("\uDE00"))
        assertFalse(isWellFormedUtf16("\uD83DA"))
    }

    /** Code-point movement preserves valid pairs and rejects traversal across lone surrogates. */
    @Test
    fun offsetByCodePointsStrictly_preservesPairsAndRejectsLoneSurrogate(): Unit {
        /** Buffer containing one valid supplementary scalar followed by a malformed high surrogate. */
        val text = "A😀\uD83DB"

        assertEquals(1, offsetByCodePointsStrictly(text, offset = 3, codePointDelta = -1))
        assertEquals(3, offsetByCodePointsStrictly(text, offset = 1, codePointDelta = 1))
        assertEquals(null, offsetByCodePointsStrictly(text, offset = 3, codePointDelta = 1))
        assertEquals(null, offsetByCodePointsStrictly(text, offset = 4, codePointDelta = -1))
    }
}
