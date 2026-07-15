package com.purride.pixelui

import android.icu.lang.UCharacter
import android.icu.text.BreakIterator
import android.icu.util.VersionInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Differential acceptance test between the engine-owned Unicode 17 map and Android platform ICU.
 *
 * [PixelGraphemeBoundaryMap] remains authoritative. Android ICU is only a versioned oracle: an ICU
 * mismatch passes solely when the exact SDK/ICU/Unicode profile, case id, and both boundary lists are
 * present in the manually reviewed expected-difference asset. The test never derives or persists an
 * allowlist from its current runtime result.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
class PixelGraphemeIcuDifferentialInstrumentedTest {
    /**
     * Compares a controlled acceptance corpus and a reviewed official GraphemeBreakTest subset.
     *
     * The emitted one-line summary records the platform fingerprint plus ICU and Unicode versions,
     * making API 24/37 evidence reviewable without treating either platform version as the engine's
     * segmentation authority.
     */
    @Test
    fun unicode17AuthorityMatchesIcuOrAnExactReviewedDifference(): Unit {
        /** Android test APK context owns the versioned assets under src/androidTest/assets. */
        val testContext = InstrumentationRegistry.getInstrumentation().context
        /** Exact running SDK/ICU/Unicode tuple used to select reviewed differences. */
        val platformProfile = AndroidIcuProfile.current()
        /** Hand-curated sequences cover all user-facing M5-3C acceptance forms. */
        val controlledCases = controlledCases()
        /** Verbatim expressions copied from the licensed Unicode 17 conformance file. */
        val officialCases = testContext.assets.open(OfficialSubsetAsset).bufferedReader().use { reader ->
            parseOfficialSubset(reader.readLines())
        }
        /** Combined ids are stable keys for the expected-difference data set. */
        val allCases = controlledCases + officialCases
        /** Versioned reviewed records loaded read-only; runtime output can never update this list. */
        val allExpectedDifferences = testContext.assets.open(ExpectedDifferencesAsset)
            .bufferedReader()
            .use { reader -> parseExpectedDifferences(reader.readLines()) }
        /** Records that apply only to the exact runtime profile. */
        val profileDifferences = allExpectedDifferences
            .filter { difference -> difference.profileKey == platformProfile.key }
            .associateBy { difference -> difference.caseId }
        /** Corpus ids used to reject stale or misspelled allowlist entries on every runtime. */
        val allCaseIds = allCases.mapTo(mutableSetOf()) { graphemeCase -> graphemeCase.id }
        /** Accumulated integrity, conformance, or unreviewed-difference failures. */
        val problems = mutableListOf<String>()
        /** Case ids whose exact reviewed mismatch was observed on this runtime. */
        val consumedDifferenceIds = mutableSetOf<String>()
        /** Number of platform cases that agree directly with the Unicode 17 authority. */
        var directMatchCount = 0
        /** Number of platform mismatches accepted through an exact reviewed record. */
        var reviewedDifferenceCount = 0

        if (allCases.size != allCaseIds.size) {
            problems += "duplicate corpus case id detected"
        }
        for (difference in allExpectedDifferences) {
            if (difference.caseId !in allCaseIds) {
                problems += "expected-difference record references unknown case ${difference.caseId}"
            }
        }
        /** Duplicate profile/case records would be hidden by associateBy and are therefore rejected. */
        val uniqueDifferenceKeys = allExpectedDifferences
            .map { difference -> "${difference.profileKey}|${difference.caseId}" }
            .toSet()
        if (uniqueDifferenceKeys.size != allExpectedDifferences.size) {
            problems += "duplicate expected-difference profile/case record detected"
        }

        for (graphemeCase in officialCases) {
            /** Engine output must first agree with the official Unicode 17 subset itself. */
            val engineBoundaries = engineBoundaries(graphemeCase.text)
            if (engineBoundaries != graphemeCase.officialBoundaries) {
                problems += "engine/official mismatch ${graphemeCase.id}: " +
                    "official=${graphemeCase.officialBoundaries} engine=$engineBoundaries"
            }
        }

        for (graphemeCase in allCases) {
            /** Unicode 17 boundary list supplied by the engine-owned production implementation. */
            val engineBoundaries = engineBoundaries(graphemeCase.text)
            /** Runtime Android ICU character boundaries used only for differential evidence. */
            val platformBoundaries = androidIcuBoundaries(graphemeCase.text)
            /** Exact manually reviewed record, if any, for this runtime and case. */
            val expectedDifference = profileDifferences[graphemeCase.id]
            if (engineBoundaries == platformBoundaries) {
                directMatchCount += 1
                if (expectedDifference != null) {
                    problems += "stale expected difference ${graphemeCase.id}: runtime now matches $engineBoundaries"
                }
                continue
            }
            if (expectedDifference == null) {
                problems += "unreviewed ICU difference ${graphemeCase.id}: " +
                    "engine=$engineBoundaries platform=$platformBoundaries text=${graphemeCase.text.toCodePointDebug()}"
                continue
            }
            if (
                expectedDifference.engineBoundaries != engineBoundaries ||
                expectedDifference.platformBoundaries != platformBoundaries
            ) {
                problems += "changed ICU difference ${graphemeCase.id}: " +
                    "recordedEngine=${expectedDifference.engineBoundaries} actualEngine=$engineBoundaries " +
                    "recordedPlatform=${expectedDifference.platformBoundaries} actualPlatform=$platformBoundaries"
                continue
            }
            consumedDifferenceIds += graphemeCase.id
            reviewedDifferenceCount += 1
            println(
                "PIXEL_GRAPHEME_ICU_EXPECTED_DIFFERENCE profile=${platformProfile.key} " +
                    "case=${graphemeCase.id} engine=$engineBoundaries platform=$platformBoundaries " +
                    "rationale=${expectedDifference.rationale}",
            )
        }

        for (difference in profileDifferences.values) {
            if (difference.caseId !in consumedDifferenceIds) {
                problems += "expected difference was not consumed: ${difference.caseId}"
            }
        }

        /** Reviewable result line retained in Gradle connected-test and instrumentation output. */
        val summary = "PIXEL_GRAPHEME_ICU_SUMMARY profile=${platformProfile.key} " +
            "engineUnicode=${PixelGraphemeBoundaryMap.UnicodeVersion} " +
            "fingerprint=${Build.FINGERPRINT} controlled=${controlledCases.size} " +
            "officialSubset=${officialCases.size} total=${allCases.size} " +
            "matches=$directMatchCount reviewedDifferences=$reviewedDifferenceCount " +
            "problems=${problems.size}"
        println(summary)
        assertTrue(
            buildString {
                append(summary)
                if (problems.isNotEmpty()) {
                    append("\n")
                    append(problems.joinToString(separator = "\n"))
                }
            },
            problems.isEmpty(),
        )
    }

    /** Returns controlled user-facing and intentional cross-Unicode-version differential cases. */
    private fun controlledCases(): List<GraphemeDifferentialCase> {
        return listOf(
            GraphemeDifferentialCase("controlled-decomposed", "e\u0301"),
            GraphemeDifferentialCase("controlled-supplementary", "\uD83D\uDE00"),
            GraphemeDifferentialCase("controlled-skin-tone", "\uD83D\uDC4B\uD83C\uDFFD"),
            GraphemeDifferentialCase(
                "controlled-family-zwj",
                "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66",
            ),
            GraphemeDifferentialCase("controlled-variation-selector", "\u2708\uFE0F"),
            GraphemeDifferentialCase("controlled-keycap", "1\uFE0F\u20E3"),
            GraphemeDifferentialCase("controlled-ri-flag", "\uD83C\uDDE8\uD83C\uDDF3"),
            GraphemeDifferentialCase("controlled-crlf", "\r\n"),
            GraphemeDifferentialCase("controlled-gb9c-devanagari", "\u0915\u094D\u0924"),
            GraphemeDifferentialCase(
                "controlled-unicode15-pictographic-zwj",
                "\uD83E\uDEE8\u200D\uD83D\uDED1",
            ),
        )
    }

    /** Parses stable ids and verbatim break expressions from the reviewed official subset asset. */
    private fun parseOfficialSubset(lines: List<String>): List<GraphemeDifferentialCase> {
        /** Parsed conformance cases in reviewed file order. */
        val cases = mutableListOf<GraphemeDifferentialCase>()
        for ((lineIndex, rawLine) in lines.withIndex()) {
            /** Comments and surrounding whitespace have no conformance meaning. */
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            /** Stable id and exact marker expression separated by the asset schema delimiter. */
            val columns = line.split('|', limit = 2)
            require(columns.size == 2) { "invalid official subset line ${lineIndex + 1}: $rawLine" }
            cases += parseOfficialExpression(
                id = columns[0].trim(),
                expression = columns[1].trim(),
            )
        }
        require(cases.isNotEmpty()) { "official grapheme subset must not be empty" }
        return cases
    }

    /** Converts one official ÷/× code-point expression into text and UTF-16 boundaries. */
    private fun parseOfficialExpression(
        id: String,
        expression: String,
    ): GraphemeDifferentialCase {
        require(id.isNotBlank()) { "official grapheme case id must not be blank" }
        /** Exact UTF-16 text constructed from the official scalar-value sequence. */
        val text = StringBuilder()
        /** Boundaries indicated by ÷ tokens, converted from code points to UTF-16 offsets. */
        val boundaries = mutableListOf<Int>()
        /** UTF-16 offset after the code points parsed so far. */
        var utf16Offset = 0
        for (token in expression.split(Regex("\\s+"))) {
            when (token) {
                "÷" -> boundaries += utf16Offset
                "×" -> Unit
                else -> {
                    /** Official hexadecimal Unicode scalar value. */
                    val codePoint = token.toInt(radix = 16)
                    require(Character.isValidCodePoint(codePoint)) {
                        "invalid code point $token in official case $id"
                    }
                    text.appendCodePoint(codePoint)
                    utf16Offset += Character.charCount(codePoint)
                }
            }
        }
        require(boundaries.firstOrNull() == 0 && boundaries.lastOrNull() == utf16Offset) {
            "official case $id must contain GB1 and GB2 boundaries"
        }
        return GraphemeDifferentialCase(
            id = id,
            text = text.toString(),
            officialBoundaries = boundaries,
        )
    }

    /** Parses exact, manually reviewed platform differences without accepting wildcard profiles. */
    private fun parseExpectedDifferences(lines: List<String>): List<ReviewedIcuDifference> {
        /** Parsed records in source order for stable diagnostics. */
        val differences = mutableListOf<ReviewedIcuDifference>()
        for ((lineIndex, rawLine) in lines.withIndex()) {
            /** Comments and whitespace are ignored; every data row must satisfy the full schema. */
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            /** Exact profile, id, both outputs, and rationale make each acceptance reviewable. */
            val columns = line.split('|', limit = 5)
            require(columns.size == 5) {
                "invalid expected-difference line ${lineIndex + 1}: $rawLine"
            }
            /** Exact SDK/ICU/Unicode profile; wildcard matching is intentionally unsupported. */
            val profileKey = columns[0].trim()
            /** Stable corpus case id associated with this reviewed difference. */
            val caseId = columns[1].trim()
            /** Engine-authority UTF-16 offsets reviewed for this profile and case. */
            val engineBoundaries = parseBoundaryList(columns[2], lineIndex)
            /** Android ICU UTF-16 offsets observed and reviewed for this profile and case. */
            val platformBoundaries = parseBoundaryList(columns[3], lineIndex)
            /** Human rationale must explain the Unicode/ICU version difference. */
            val rationale = columns[4].trim()
            require(profileKey.matches(ProfileKeyPattern)) {
                "expected-difference line ${lineIndex + 1} must use an exact profile key"
            }
            require(caseId.isNotBlank() && rationale.isNotBlank()) {
                "expected-difference line ${lineIndex + 1} requires case id and rationale"
            }
            differences += ReviewedIcuDifference(
                profileKey = profileKey,
                caseId = caseId,
                engineBoundaries = engineBoundaries,
                platformBoundaries = platformBoundaries,
                rationale = rationale,
            )
        }
        return differences
    }

    /** Parses a non-empty comma-separated UTF-16 boundary list. */
    private fun parseBoundaryList(column: String, zeroBasedLineIndex: Int): List<Int> {
        /** Trimmed boundary column whose values must be ordered and non-negative. */
        val value = column.trim()
        require(value.isNotEmpty()) {
            "empty boundary list on expected-difference line ${zeroBasedLineIndex + 1}"
        }
        /** Integer offsets as reviewed in the versioned asset. */
        val boundaries = value.split(',').map { token -> token.trim().toInt() }
        require(boundaries.first() == 0 && boundaries.zipWithNext().all { pair -> pair.first < pair.second }) {
            "invalid boundary order on expected-difference line ${zeroBasedLineIndex + 1}"
        }
        return boundaries
    }

    /** Enumerates the engine's authoritative UTF-16 boundaries through its public query API. */
    private fun engineBoundaries(text: String): List<Int> {
        /** Immutable map built from fixed Unicode 17 production tables. */
        val map = PixelGraphemeBoundaryMap(text)
        /** Result starts at mandatory GB1. */
        val boundaries = mutableListOf(0)
        /** Strictly increasing accepted boundary. */
        var current = 0
        while (current < text.length) {
            current = map.next(current)
            boundaries += current
        }
        return boundaries
    }

    /** Enumerates Android ICU character boundaries for the exact runtime under test. */
    private fun androidIcuBoundaries(text: String): List<Int> {
        /** Locale-neutral extended-character iterator supplied by platform ICU. */
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        /** UTF-16 offsets emitted in ascending order by Android ICU. */
        val boundaries = mutableListOf<Int>()
        /** Current iterator boundary, starting with GB1-equivalent first(). */
        var current = iterator.first()
        while (current != BreakIterator.DONE) {
            boundaries += current
            current = iterator.next()
        }
        return boundaries
    }

    /** Renders scalar values instead of glyphs so platform-difference output remains stable. */
    private fun String.toCodePointDebug(): String {
        /** Current UTF-16 cursor advanced by the decoded scalar width. */
        var offset = 0
        /** Stable hexadecimal sequence for test diagnostics. */
        val values = mutableListOf<String>()
        while (offset < length) {
            /** Well-formed corpus code point at the current UTF-16 offset. */
            val codePoint = codePointAt(offset)
            values += "U+%04X".format(codePoint)
            offset += Character.charCount(codePoint)
        }
        return values.joinToString(separator = " ")
    }

    /** Runtime profile recorded in differential output and used as an exact allowlist key. */
    private data class AndroidIcuProfile(
        /** Android framework API level. */
        val sdk: Int,
        /** Platform ICU library version. */
        val icuVersion: String,
        /** Unicode data version exposed by platform ICU. */
        val unicodeVersion: String,
    ) {
        /** Stable exact key persisted in the manually reviewed expected-difference asset. */
        val key: String = "sdk=$sdk;icu=$icuVersion;unicode=$unicodeVersion"

        /** Builds the exact running profile without relying on build-time SDK assumptions. */
        companion object {
            /** Captures framework, ICU, and Unicode versions from Android's public API 24 surface. */
            fun current(): AndroidIcuProfile {
                return AndroidIcuProfile(
                    sdk = Build.VERSION.SDK_INT,
                    icuVersion = VersionInfo.ICU_VERSION.toString(),
                    unicodeVersion = UCharacter.getUnicodeVersion().toString(),
                )
            }
        }
    }

    /** One controlled or official-subset case expressed in exact UTF-16 text. */
    private data class GraphemeDifferentialCase(
        /** Stable id referenced by reviewed expected differences. */
        val id: String,
        /** Exact input text segmented by both engines. */
        val text: String,
        /** Official Unicode 17 boundaries when sourced from GraphemeBreakTest, otherwise null. */
        val officialBoundaries: List<Int>? = null,
    )

    /** Exact reviewed mismatch for one runtime profile and one stable corpus case. */
    private data class ReviewedIcuDifference(
        /** Exact SDK/ICU/Unicode tuple with no wildcard matching. */
        val profileKey: String,
        /** Stable controlled or official-subset case id. */
        val caseId: String,
        /** Reviewed engine Unicode 17 UTF-16 boundaries. */
        val engineBoundaries: List<Int>,
        /** Reviewed Android platform ICU UTF-16 boundaries. */
        val platformBoundaries: List<Int>,
        /** Human explanation of the versioned behavior difference. */
        val rationale: String,
    )

    /** Versioned asset locations and exact-profile validation shared by the test. */
    private companion object {
        /** Reviewed official Unicode 17 subset packaged into the instrumentation APK. */
        const val OfficialSubsetAsset: String =
            "unicode/17.0.0/GraphemeBreakTest-api24-compatible-subset.txt"

        /** Manually reviewed Android ICU differences; never generated by the test runtime. */
        const val ExpectedDifferencesAsset: String =
            "unicode/17.0.0/android-icu-expected-differences.txt"

        /** Exact profile schema deliberately rejects stars, ranges, and omitted versions. */
        val ProfileKeyPattern: Regex =
            Regex("sdk=\\d+;icu=\\d+(?:\\.\\d+)*;unicode=\\d+(?:\\.\\d+)*")
    }
}
