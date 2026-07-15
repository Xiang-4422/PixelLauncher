package com.purride.pixelui.internal.text.bidi

import java.io.BufferedReader
import java.io.InputStreamReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the engine-owned resolver against both complete Unicode 17 UAX #9 conformance corpora. */
class UnicodeBidiConformanceTest {
    /** Every property-only sequence and requested paragraph direction matches official results. */
    @Test
    fun completeBidiTestCorpusMatchesUnicode17() {
        /** Most recent expected level vector declared by an @Levels line. */
        var expectedLevels: List<Int?> = emptyList()
        /** Most recent expected visual-to-logical map declared by an @Reorder line. */
        var expectedReorder: IntArray = IntArray(0)
        /** Number of paragraph-direction executions validated across all data rows. */
        var executionCount = 0
        /** First bounded failures retained while the complete corpus continues running. */
        val failures = mutableListOf<String>()

        resourceReader(BIDI_TEST_RESOURCE).useLines { lines ->
            lines.forEachIndexed { lineIndex, rawLine ->
                /** Trimmed corpus row used for directive/data dispatch. */
                val line = rawLine.trim()
                when {
                    line.startsWith("@Levels:") -> {
                        expectedLevels = parseLevels(line.substringAfter(':'))
                    }
                    line.startsWith("@Reorder:") -> {
                        expectedReorder = parseIndexes(line.substringAfter(':'))
                    }
                    line.isEmpty() || line.startsWith('#') || line.startsWith('@') -> Unit
                    else -> {
                        /** Bidi_Class input and paragraph-level bitset. */
                        val fields = line.split(';', limit = 2)
                        /** Logical class sequence for one official test row. */
                        val types = fields[0].trim().split(WHITESPACE).filter(String::isNotEmpty).map {
                            token -> UnicodeBidiClass.valueOf(token)
                        }
                        /** Directions requested by the compact official bitset. */
                        val bitset = fields[1].trim().toInt(radix = 16)
                        paragraphLevels(bitset).forEach { paragraphLevel ->
                            /** Complete engine result through UAX #9 L2. */
                            val actual = UnicodeBidiResolver.resolveTypes(types, paragraphLevel)
                            executionCount += 1
                            verifyResult(
                                source = "BidiTest:${lineIndex + 1}:p=$paragraphLevel",
                                actual = actual,
                                expectedParagraphLevel = null,
                                expectedLevels = expectedLevels,
                                expectedReorder = expectedReorder,
                                failures = failures,
                            )
                        }
                    }
                }
            }
        }

        assertTrue("Expected a complete BidiTest execution count, got $executionCount", executionCount > 500_000)
        assertEquals(failures.joinToString(separator = "\n"), emptyList<String>(), failures)
    }

    /** Every explicit-code-point bracket, isolate and control case matches official results. */
    @Test
    fun completeBidiCharacterTestCorpusMatchesUnicode17() {
        /** Number of explicit character rows validated. */
        var executionCount = 0
        /** First bounded failures retained while the complete corpus continues running. */
        val failures = mutableListOf<String>()

        resourceReader(BIDI_CHARACTER_TEST_RESOURCE).useLines { lines ->
            lines.forEachIndexed { lineIndex, rawLine ->
                /** Comment-free trimmed corpus row. */
                val line = rawLine.substringBefore('#').trim()
                if (line.isEmpty()) return@forEachIndexed
                /** Five official fields: input, direction, base, levels and reorder. */
                val fields = line.split(';')
                /** Logical Unicode code-point sequence. */
                val codePoints = fields[0].trim().split(WHITESPACE).filter(String::isNotEmpty)
                    .map { token -> token.toInt(radix = 16) }
                    .toIntArray()
                /** Requested paragraph level using the same 0/1/2 convention as the resolver. */
                val paragraphLevel = fields[1].trim().toByte()
                /** Official resolved base level. */
                val expectedParagraphLevel = fields[2].trim().toInt()
                /** Official per-code-point levels, with X9 entries represented by null. */
                val expectedLevels = parseLevels(fields[3])
                /** Official visual-to-logical map excluding X9 entries. */
                val expectedReorder = parseIndexes(fields[4])
                /** Complete fixed-data engine result through UAX #9 L2. */
                val actual = UnicodeBidiResolver.resolveCodePoints(codePoints, paragraphLevel)
                executionCount += 1
                verifyResult(
                    source = "BidiCharacterTest:${lineIndex + 1}",
                    actual = actual,
                    expectedParagraphLevel = expectedParagraphLevel,
                    expectedLevels = expectedLevels,
                    expectedReorder = expectedReorder,
                    failures = failures,
                )
            }
        }

        assertTrue(
            "Expected the complete BidiCharacterTest corpus, got $executionCount",
            executionCount > 90_000,
        )
        assertEquals(failures.joinToString(separator = "\n"), emptyList<String>(), failures)
    }

    /** Compares one result while retaining only a bounded diagnostic set. */
    private fun verifyResult(
        /** Stable corpus location written into diagnostics. */
        source: String,
        /** Engine-owned resolution being checked. */
        actual: UnicodeBidiResult,
        /** Optional expected base level for BidiCharacterTest. */
        expectedParagraphLevel: Int?,
        /** Expected levels where null represents a rule-X9 entry. */
        expectedLevels: List<Int?>,
        /** Expected visual-to-logical indexes excluding X9 entries. */
        expectedReorder: IntArray,
        /** Shared bounded failure list. */
        failures: MutableList<String>,
    ) {
        if (failures.size >= MAX_REPORTED_FAILURES) return
        if (actual.levels.size != expectedLevels.size) {
            failures += "$source length expected=${expectedLevels.size} actual=${actual.levels.size}"
            return
        }
        /** Actual levels with unspecified X9 positions masked identically to the corpus. */
        val comparableLevels = actual.levels.mapIndexed { index, level ->
            if (expectedLevels[index] == null) null else level.toInt()
        }
        /** Actual order with rule-X9 logical indexes removed. */
        val comparableReorder = actual.visualToLogical.filter { logicalIndex ->
            expectedLevels[logicalIndex] != null
        }.toIntArray()
        if (
            expectedParagraphLevel != null &&
            actual.paragraphLevel.toInt() != expectedParagraphLevel
        ) {
            failures += "$source base expected=$expectedParagraphLevel actual=${actual.paragraphLevel}"
        } else if (comparableLevels != expectedLevels) {
            failures += "$source levels expected=$expectedLevels actual=$comparableLevels"
        } else if (!comparableReorder.contentEquals(expectedReorder)) {
            failures += "$source reorder expected=${expectedReorder.contentToString()} " +
                "actual=${comparableReorder.contentToString()}"
        }
    }

    /** Parses level tokens while retaining UAX #9 rule-X9 `x` markers. */
    private fun parseLevels(value: String): List<Int?> {
        return value.trim().split(WHITESPACE).filter(String::isNotEmpty).map { token ->
            if (token.equals("x", ignoreCase = true)) null else token.toInt()
        }
    }

    /** Parses an optional whitespace-separated visual index vector. */
    private fun parseIndexes(value: String): IntArray {
        return value.trim().split(WHITESPACE).filter(String::isNotEmpty)
            .map(String::toInt)
            .toIntArray()
    }

    /** Expands the BidiTest direction bitset into reference paragraph-level values. */
    private fun paragraphLevels(bitset: Int): List<Byte> {
        /** Requested levels in official bit order: auto, explicit LTR, explicit RTL. */
        val levels = mutableListOf<Byte>()
        if (bitset and 0x1 != 0) levels += UnicodeBidiResolver.AUTO_PARAGRAPH_LEVEL
        if (bitset and 0x2 != 0) levels += 0
        if (bitset and 0x4 != 0) levels += 1
        return levels
    }

    /** Opens one required classpath corpus with a large buffered reader. */
    private fun resourceReader(path: String): BufferedReader {
        /** Required official resource stream. */
        val stream = checkNotNull(javaClass.getResourceAsStream(path)) { "Missing resource $path" }
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8), RESOURCE_BUFFER_SIZE)
    }

    private companion object {
        /** Complete property-only Unicode 17 conformance corpus. */
        const val BIDI_TEST_RESOURCE: String = "/unicode/17.0.0/BidiTest.txt"
        /** Complete explicit-code-point Unicode 17 conformance corpus. */
        const val BIDI_CHARACTER_TEST_RESOURCE: String = "/unicode/17.0.0/BidiCharacterTest.txt"
        /** Diagnostic bound preventing millions of duplicate strings after a systematic failure. */
        const val MAX_REPORTED_FAILURES: Int = 20
        /** Reader buffer reducing overhead for the two multi-megabyte official resources. */
        const val RESOURCE_BUFFER_SIZE: Int = 64 * 1024
        /** Reusable Unicode whitespace splitter. */
        val WHITESPACE: Regex = Regex("\\s+")
    }
}
