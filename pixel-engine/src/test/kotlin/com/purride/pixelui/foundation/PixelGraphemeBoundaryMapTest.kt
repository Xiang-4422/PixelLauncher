package com.purride.pixelui

import com.purride.pixelui.internal.text.UnicodeGraphemeData
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the Unicode 17 extended-grapheme kernel and its UTF-16 navigation contract. */
class PixelGraphemeBoundaryMapTest {
    /** Pinned bytes prove the checked-in corpus and Unicode-3.0 license were imported intact. */
    @Test
    fun officialCorpusAndLicenseMatchPinnedChecksums() {
        /** Exact official Unicode 17 GraphemeBreakTest bytes. */
        val corpusBytes = readResourceBytes(OFFICIAL_CORPUS_RESOURCE)
        /** Exact official Unicode License v3 bytes shipped beside the corpus. */
        val licenseBytes = readResourceBytes(OFFICIAL_LICENSE_RESOURCE)

        assertEquals(OFFICIAL_CORPUS_SHA256, corpusBytes.sha256())
        assertEquals(OFFICIAL_LICENSE_SHA256, licenseBytes.sha256())
        assertTrue(corpusBytes.toString(StandardCharsets.UTF_8).startsWith(CORPUS_VERSION_HEADER))
        assertTrue(licenseBytes.toString(StandardCharsets.UTF_8).startsWith(LICENSE_VERSION_HEADER))
    }

    /** Every official Unicode 17.0.0 GraphemeBreakTest boundary matches, including GB9c and GB11. */
    @Test
    fun completeUnicode17ConformanceCorpusMatchesEveryUtf16Offset() {
        /** Complete checked-in Unicode corpus parsed without consulting a runtime boundary API. */
        val conformanceCases = loadConformanceCases()

        assertEquals(OFFICIAL_CASE_COUNT, conformanceCases.size)
        conformanceCases.forEach { conformanceCase ->
            /** Engine-owned map under test for this official scalar-value sequence. */
            val boundaryMap = PixelGraphemeBoundaryMap(conformanceCase.text)
            /** Expected grapheme count is one less than the number of edge boundaries. */
            val expectedGraphemeCount = conformanceCase.expectedBoundaries.size - 1

            assertEquals(
                conformanceCase.failureMessage("grapheme count"),
                expectedGraphemeCount,
                boundaryMap.graphemeCount,
            )
            for (offset in 0..conformanceCase.text.length) {
                assertEquals(
                    conformanceCase.failureMessage("UTF-16 offset $offset"),
                    offset in conformanceCase.expectedBoundaries,
                    boundaryMap.isBoundary(offset),
                )
            }
        }
    }

    /** Named acceptance sequences cover every non-default rule and emoji form required by M5-3C. */
    @Test
    fun namedRulesKeepRequiredSequencesAtomic() {
        assertBoundaries("A\r\nB", 0, 1, 3, 4) // GB3 plus GB4/GB5.
        assertBoundaries("A\u0000\u0308B", 0, 1, 2, 3, 4) // Control precedes GB9.
        assertBoundaries("e\u0301", 0, 2) // GB9 Extend.
        assertBoundaries("\u0915\u093E", 0, 2) // GB9a SpacingMark.
        assertBoundaries("\u0600A", 0, 2) // GB9b Prepend.
        assertBoundaries("\u0915\u0308\u094D\u0308\u0937", 0, 5) // GB9c InCB.
        assertBoundaries("\u1100\u1161\u11A8", 0, 3) // GB6 through GB8 Hangul.
        assertBoundaries("\u2764\uFE0F", 0, 2) // Variation selector is Extend.
        assertBoundaries(codePointString(0x1F44D, 0x1F3FD), 0, 4) // Emoji modifier.
        assertBoundaries(
            codePointString(0x1F469, 0x1F3FD, 0x200D, 0x1F4BB),
            0,
            7,
        ) // GB11 Extended_Pictographic Extend* ZWJ sequence.
        assertBoundaries(
            codePointString(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467, 0x200D, 0x1F466),
            0,
            11,
        ) // Multi-link family ZWJ sequence.
        assertBoundaries(codePointString(0x1F1E6, 0x1F1E7, 0x1F1E8), 0, 4, 6)
        assertBoundaries(codePointString(0x1F1E6, 0x1F1E7, 0x1F1E8, 0x1F1E9), 0, 4, 8)
        assertBoundaries("1\uFE0F\u20E3", 0, 3) // Keycap sequence through GB9.
        assertBoundaries("A\u200D" + codePointString(0x1F4BB), 0, 2, 4) // GB11 needs EP prefix.
    }

    /** Strict movement, inclusive snaps, downstream ties, and range expansion share one contract. */
    @Test
    fun boundaryNavigationUsesDocumentedUtf16SnapRules() {
        /** Family cluster surrounded by ASCII exposes both interior and exact-boundary queries. */
        val family = codePointString(
            0x1F468,
            0x200D,
            0x1F469,
            0x200D,
            0x1F467,
            0x200D,
            0x1F466,
        )
        /** Boundary map has offsets 0, 1, 12, and 13. */
        val map = PixelGraphemeBoundaryMap("a${family}b")

        assertEquals(13, map.utf16Length)
        assertEquals(3, map.graphemeCount)
        assertEquals(1, map.previous(5))
        assertEquals(12, map.next(5))
        assertEquals(1, map.previous(12))
        assertEquals(12, map.next(1))
        assertEquals(0, map.previous(0))
        assertEquals(13, map.next(13))
        assertEquals(1, map.floor(5))
        assertEquals(12, map.ceil(5))
        assertEquals(1, map.nearest(6))
        assertEquals(12, map.nearest(7))
        assertEquals(PixelUtf16Range(1, 12), map.expand(start = 3, end = 9))
        assertEquals(PixelUtf16Range(1, 12), map.expand(start = 1, end = 12))

        /** CRLF gives an even-width cluster whose midpoint proves downstream tie affinity. */
        val crlfMap = PixelGraphemeBoundaryMap("\r\n")
        assertEquals(2, crlfMap.nearest(1))
        assertEquals(PixelUtf16Range(2, 2), crlfMap.expand(start = 1, end = 1))
    }

    /** Out-of-range and inverted legacy selections normalize without introducing invalid offsets. */
    @Test
    fun navigationClampsInputsAndInvertedRangeCollapsesAtStart() {
        /** Decomposed accent cluster gives a non-boundary offset at one. */
        val map = PixelGraphemeBoundaryMap("e\u0301x")
        /** Empty map proves every saturating query remains defined at its single boundary. */
        val emptyMap = PixelGraphemeBoundaryMap("")

        assertFalse(map.isBoundary(-1))
        assertFalse(map.isBoundary(4))
        assertEquals(0, map.previous(Int.MIN_VALUE))
        assertEquals(0, map.floor(-100))
        assertEquals(0, map.ceil(-100))
        assertEquals(3, map.next(Int.MAX_VALUE))
        assertEquals(3, map.floor(100))
        assertEquals(3, map.ceil(100))
        assertEquals(PixelUtf16Range(0, 2), map.expand(start = -10, end = 1))
        assertEquals(PixelUtf16Range(2, 2), map.expand(start = 1, end = 0))
        assertEquals(0, emptyMap.graphemeCount)
        assertTrue(emptyMap.isBoundary(0))
        assertEquals(0, emptyMap.previous(1))
        assertEquals(0, emptyMap.next(-1))
        assertEquals(0, emptyMap.floor(1))
        assertEquals(0, emptyMap.ceil(-1))
        assertEquals(0, emptyMap.nearest(1))
        assertEquals(PixelUtf16Range(0, 0), emptyMap.expand(start = 1, end = -1))
    }

    /** Valid pairs stay indivisible while every isolated surrogate is one deterministic cluster. */
    @Test
    fun surrogateProfilePreservesPairsAndIsolatesIllFormedCodeUnits() {
        /** One valid supplementary-plane emoji encoded as a UTF-16 pair. */
        val paired = "\uD83D\uDE00"
        /** Low-high reversal plus one high surrogate proves both isolated-surrogate directions. */
        val illFormed = "\uDE00\uD83DA\uD83D"

        assertBoundaries(paired, 0, 2)
        assertFalse(PixelGraphemeBoundaryMap(paired).isBoundary(1))
        assertBoundaries(illFormed, 0, 1, 2, 3, 4)
    }

    /** Construction keeps the caller's exact normalization form and publishes its fixed version. */
    @Test
    fun textIsNeverNormalizedAndUnicodeVersionIsFixed() {
        /** Canonically decomposed text must remain byte-for-code-unit identical. */
        val decomposed = "Cafe\u0301"
        /** Map retains the exact String value while grouping its final combining sequence. */
        val map = PixelGraphemeBoundaryMap(decomposed)

        assertEquals("17.0.0", PixelGraphemeBoundaryMap.UnicodeVersion)
        assertEquals(PixelGraphemeBoundaryMap.UnicodeVersion, UnicodeGraphemeData.VERSION)
        assertEquals(decomposed, map.text)
        assertEquals(5, map.utf16Length)
        assertFalse(map.isBoundary(4))
        assertEquals(4, map.graphemeCount)
    }

    /** PixelUtf16Range exposes half-open length/collapse semantics and rejects invalid values. */
    @Test
    fun utf16RangeValidatesHalfOpenEndpoints() {
        /** Non-empty range used to verify its derived UTF-16 length. */
        val selected = PixelUtf16Range(start = 2, end = 5)
        /** Empty range used to verify collapsed-caret semantics. */
        val collapsed = PixelUtf16Range(start = 3, end = 3)

        assertEquals(3, selected.length)
        assertFalse(selected.isCollapsed)
        assertEquals(0, collapsed.length)
        assertTrue(collapsed.isCollapsed)
        assertThrows(IllegalArgumentException::class.java) { PixelUtf16Range(-1, 0) }
        assertThrows(IllegalArgumentException::class.java) { PixelUtf16Range(2, 1) }
    }

    /** Compares every UTF-16 offset against an explicit expected boundary set. */
    private fun assertBoundaries(text: String, vararg expectedBoundaries: Int) {
        /** Boundary map constructed without any platform Unicode dependency. */
        val map = PixelGraphemeBoundaryMap(text)
        /** Immutable expected offsets used for readable mismatch output. */
        val expected = expectedBoundaries.toSet()

        assertEquals(expected.size - 1, map.graphemeCount)
        for (offset in 0..text.length) {
            assertEquals("offset=$offset text=${text.toCodePointHex()}", offset in expected, map.isBoundary(offset))
        }
    }

    /** Builds a UTF-16 String from scalar values for supplementary-plane acceptance cases. */
    private fun codePointString(vararg codePoints: Int): String {
        /** Builder delegates encoding only; it is not used to decide boundaries. */
        val builder = StringBuilder()
        codePoints.forEach { codePoint -> builder.appendCodePoint(codePoint) }
        return builder.toString()
    }

    /** Loads all non-comment cases from the checked-in licensed Unicode 17 corpus. */
    private fun loadConformanceCases(): List<OfficialGraphemeBreakCase> {
        /** Exact official corpus bytes shared with checksum verification. */
        val corpusBytes = readResourceBytes(OFFICIAL_CORPUS_RESOURCE)
        /** Parsed cases retain source line numbers and comments for actionable failures. */
        val cases = mutableListOf<OfficialGraphemeBreakCase>()

        corpusBytes.inputStream().bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            lines.forEachIndexed { zeroBasedLine, rawLine ->
                val specification = rawLine.substringBefore('#').trim()
                if (specification.isEmpty()) return@forEachIndexed
                val comment = rawLine.substringAfter('#', missingDelimiterValue = "").trim()
                cases += parseConformanceCase(
                    oneBasedLine = zeroBasedLine + 1,
                    specification = specification,
                    comment = comment,
                )
            }
        }
        return cases
    }

    /** Reads one required classpath resource completely or fails with its stable path. */
    private fun readResourceBytes(resourcePath: String): ByteArray {
        /** Classpath stream whose ownership is closed before returning the copied bytes. */
        val resource = requireNotNull(javaClass.getResourceAsStream(resourcePath)) {
            "Missing $resourcePath"
        }
        return resource.use { stream -> stream.readBytes() }
    }

    /** Returns the lowercase hexadecimal SHA-256 digest used by the import generator. */
    private fun ByteArray.sha256(): String {
        /** Cryptographic digest detects truncation or unnoticed upstream fixture replacement. */
        val digest = MessageDigest.getInstance("SHA-256").digest(this)
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    /** Parses alternating Unicode break markers and hexadecimal scalar values into UTF-16 offsets. */
    private fun parseConformanceCase(
        oneBasedLine: Int,
        specification: String,
        comment: String,
    ): OfficialGraphemeBreakCase {
        /** String under test, encoded exactly from the corpus scalar sequence. */
        val text = StringBuilder()
        /** Break markers converted to Android-compatible UTF-16 offsets. */
        val expectedBoundaries = linkedSetOf<Int>()
        /** Whitespace-delimited corpus tokens alternate marker and scalar value. */
        val tokens = specification.split(Regex("\\s+"))

        tokens.forEach { token ->
            when (token) {
                BREAK_MARKER -> expectedBoundaries += text.length
                NO_BREAK_MARKER -> Unit
                else -> text.appendCodePoint(token.toInt(radix = 16))
            }
        }
        return OfficialGraphemeBreakCase(
            oneBasedLine = oneBasedLine,
            specification = specification,
            comment = comment,
            text = text.toString(),
            expectedBoundaries = expectedBoundaries,
        )
    }

    /** Renders the backing string as code points without relying on locale-sensitive text output. */
    private fun String.toCodePointHex(): String {
        /** Hex tokens make malformed or supplementary input unambiguous in failures. */
        val tokens = mutableListOf<String>()
        var offset = 0
        while (offset < length) {
            val codePoint = codePointAt(offset)
            tokens += "U+${codePoint.toString(16).uppercase().padStart(4, '0')}"
            offset += Character.charCount(codePoint)
        }
        return tokens.joinToString(separator = " ")
    }

    /** Fixed corpus metadata prevents a truncated fixture from appearing conformant. */
    private companion object {
        /** Classpath location of the complete Unicode 17.0.0 GraphemeBreakTest file. */
        private const val OFFICIAL_CORPUS_RESOURCE: String =
            "/unicode/17.0.0/GraphemeBreakTest.txt"

        /** Classpath location of the Unicode-3.0 license covering the imported data. */
        private const val OFFICIAL_LICENSE_RESOURCE: String =
            "/unicode/17.0.0/LICENSE-UNICODE.txt"

        /** Number of executable cases in the pinned official corpus. */
        private const val OFFICIAL_CASE_COUNT: Int = 766

        /** Pinned digest of the complete Unicode 17.0.0 conformance corpus. */
        private const val OFFICIAL_CORPUS_SHA256: String =
            "e2d134d2c52919bace503ebb6a551c1855fe1a1faec18478c78fff254a1793ec"

        /** Pinned digest of the tracked Unicode License v3 text. */
        private const val OFFICIAL_LICENSE_SHA256: String =
            "e7a93b009565cfce55919a381437ac4db883e9da2126fa28b91d12732bc53d96"

        /** Version header required before the corpus can be parsed as Unicode 17 data. */
        private const val CORPUS_VERSION_HEADER: String = "# GraphemeBreakTest-17.0.0.txt"

        /** License header required before imported data may be redistributed. */
        private const val LICENSE_VERSION_HEADER: String = "UNICODE LICENSE V3"

        /** Unicode marker denoting an allowed grapheme boundary. */
        private const val BREAK_MARKER: String = "÷"

        /** Unicode marker denoting a suppressed grapheme boundary. */
        private const val NO_BREAK_MARKER: String = "×"
    }
}

/** One parsed official GraphemeBreakTest line with UTF-16 expected boundaries. */
private data class OfficialGraphemeBreakCase(
    /** One-based source line used in assertion diagnostics. */
    val oneBasedLine: Int,
    /** Marker/scalar sequence retained verbatim for diagnostics. */
    val specification: String,
    /** Official descriptive rule comment retained for diagnostics. */
    val comment: String,
    /** UTF-16 test string encoded from the official scalar values. */
    val text: String,
    /** Expected extended-grapheme boundary offsets in UTF-16 code units. */
    val expectedBoundaries: Set<Int>,
) {
    /** Builds a complete failure prefix identifying the official case and checked condition. */
    fun failureMessage(condition: String): String {
        return "line=$oneBasedLine condition=$condition spec='$specification' comment='$comment'"
    }
}
