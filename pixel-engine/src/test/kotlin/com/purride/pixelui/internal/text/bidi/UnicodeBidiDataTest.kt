package com.purride.pixelui.internal.text.bidi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Verifies generated Unicode 17 Bidi property tables independently of paragraph rendering. */
class UnicodeBidiDataTest {
    /** Every scalar has a property entry and representative @missing defaults remain fixed. */
    @Test
    fun bidiClassTableCoversUnicode17AndReviewedDefaults() {
        /** Number of valid Unicode code-point slots successfully resolved. */
        var resolvedCount = 0
        for (codePoint in 0..0x10FFFF) {
            UnicodeBidiData.bidiClass(codePoint)
            resolvedCount += 1
        }

        assertEquals(0x110000, resolvedCount)
        assertEquals("17.0.0", UnicodeBidiData.VERSION)
        assertEquals("51", UnicodeBidiData.UAX_REVISION)
        assertEquals(UnicodeBidiClass.R, UnicodeBidiData.bidiClass(0x0590))
        assertEquals(UnicodeBidiClass.AL, UnicodeBidiData.bidiClass(0x0608))
        assertEquals(UnicodeBidiClass.ET, UnicodeBidiData.bidiClass(0x20CF))
        assertEquals(UnicodeBidiClass.R, UnicodeBidiData.bidiClass(0x10940))
        assertEquals(UnicodeBidiClass.L, UnicodeBidiData.bidiClass(0x0378))
        assertEquals(UnicodeBidiClass.BN, UnicodeBidiData.bidiClass(0x10FFFF))
    }

    /** Every official character-based mirroring mapping is present byte-for-byte. */
    @Test
    fun completeBidiMirroringTableMatchesUnicode17() {
        /** Number of non-comment official mappings checked. */
        var mappingCount = 0
        resourceLines(BIDI_MIRRORING_RESOURCE).forEach { rawLine ->
            /** Comment-free property row. */
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            /** Source and mirrored scalar fields. */
            val fields = line.split(';').map(String::trim)
            /** Source scalar. */
            val source = fields[0].toInt(radix = 16)
            /** Expected character-based mirror scalar. */
            val expected = fields[1].toInt(radix = 16)
            assertEquals("U+${source.toString(16)}", expected, UnicodeBidiData.mirroredCodePoint(source))
            mappingCount += 1
        }

        assertEquals(428, mappingCount)
        assertEquals('A'.code, UnicodeBidiData.mirroredCodePoint('A'.code))
    }

    /** Every official paired bracket has the correct partner, type and shared canonical id. */
    @Test
    fun completeBidiBracketTableMatchesUnicode17() {
        /** Number of non-comment official bracket rows checked. */
        var bracketCount = 0
        resourceLines(BIDI_BRACKETS_RESOURCE).forEach { rawLine ->
            /** Comment-free property row. */
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            /** Bracket scalar, partner scalar and type fields. */
            val fields = line.split(';').map(String::trim)
            /** Bracket scalar described by this row. */
            val codePoint = fields[0].toInt(radix = 16)
            /** Normative partner scalar. */
            val partner = fields[1].toInt(radix = 16)
            /** Reference open/close byte. */
            val expectedType: Byte = if (fields[2] == "o") 1 else 2
            assertEquals(partner, UnicodeBidiData.pairedBracketCodePoint(codePoint))
            assertEquals(expectedType, UnicodeBidiData.pairedBracketType(codePoint))
            assertEquals(
                UnicodeBidiData.pairedBracketIdentity(codePoint),
                UnicodeBidiData.pairedBracketIdentity(partner),
            )
            bracketCount += 1
        }

        assertEquals(128, bracketCount)
        assertEquals(
            UnicodeBidiData.pairedBracketIdentity(0x2329),
            UnicodeBidiData.pairedBracketIdentity(0x3008),
        )
        assertNotEquals(0.toByte(), UnicodeBidiData.pairedBracketType(0x2329))
    }

    /** Reads one required UTF-8 test resource as exact logical lines. */
    private fun resourceLines(path: String): List<String> {
        /** Required classpath stream for the pinned official data. */
        val stream = checkNotNull(javaClass.getResourceAsStream(path)) { "Missing resource $path" }
        return stream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readLines() }
    }

    private companion object {
        /** Complete Unicode 17 Bidi_Mirroring_Glyph source. */
        const val BIDI_MIRRORING_RESOURCE: String = "/unicode/17.0.0/BidiMirroring.txt"
        /** Complete Unicode 17 paired-bracket property source. */
        const val BIDI_BRACKETS_RESOURCE: String = "/unicode/17.0.0/BidiBrackets.txt"
    }
}
