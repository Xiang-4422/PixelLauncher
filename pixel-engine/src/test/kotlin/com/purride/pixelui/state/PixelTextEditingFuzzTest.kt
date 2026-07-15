package com.purride.pixelui.state

import com.purride.pixelui.PixelGraphemeBoundaryMap
import com.purride.pixelui.PixelUtf16Range
import java.util.ArrayDeque
import java.util.EnumMap
import java.util.Random
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic property tests for the controller's UTF-16 and extended-grapheme invariants.
 *
 * The random stream contains only well-formed insertion fragments. Consequently, any unpaired
 * surrogate produced by the 12,000-step run is an editing defect rather than invalid generated
 * input. Every text-changing operation is checked against an exact UTF-16 oracle, which also proves
 * that the controller does not silently NFC-normalize decomposed input.
 */
class PixelTextEditingFuzzTest {
    /**
     * Runs every editing family with one fixed, reproducible seed and a bounded failure trace.
     *
     * Selection and composition requests deliberately include out-of-range and grapheme-interior
     * offsets. Text mutations only cut at engine boundaries, then exercise the controller's public
     * normalization path with arbitrary requested offsets.
     */
    @Test
    fun fixedSeedControllerFuzzPreservesEveryEditingInvariant(): Unit {
        /** Stable JDK random stream; changing this seed requires an explicit corpus review. */
        val random = Random(FuzzSeed)
        /** Public controller under test; no private state or test-only mutation path is used. */
        val controller = PixelTextFieldController()
        /** Initial corpus contains every well-formed acceptance sequence required by M5-3C. */
        val state = controller.create(
            initialText = InitialWellFormedCorpus,
            selectionStart = InitialWellFormedCorpus.length,
        )
        /** Last successful operations retained for an immediately replayable failure message. */
        val trace = ArrayDeque<String>()
        /** Per-family counts prove that random selection did not accidentally omit an operation. */
        val operationCounts = EnumMap<FuzzOperation, Int>(FuzzOperation::class.java).apply {
            for (operation in FuzzOperation.values()) {
                put(operation, 0)
            }
        }
        /** Stable operation ordering guarantees at least one early sample of every family. */
        val operationFamilies = FuzzOperation.values()

        assertEquals("initial text must preserve the reviewed UTF-16 corpus", InitialWellFormedCorpus, state.text)
        assertTrue("the well-formed fuzz corpus must not contain a lone surrogate", state.text.isWellFormedUtf16())
        assertEditingInvariants(state)

        repeat(FuzzStepCount) { step ->
            /** Oversized states are forced through a direct edit that deterministically shrinks them. */
            val operation = when {
                step < operationFamilies.size -> operationFamilies[step]
                state.text.length > MaximumFuzzTextLength -> FuzzOperation.UPDATE_TEXT_EDIT
                else -> operationFamilies[random.nextInt(operationFamilies.size)]
            }
            /** Compact before-state record included even when the operation itself throws. */
            val beforeSummary = state.toTraceSummary()
            try {
                /** Exact expected text plus human-readable random choices for this operation. */
                val result = executeOperation(
                    operation = operation,
                    controller = controller,
                    state = state,
                    random = random,
                )
                assertEquals(
                    "operation $operation must preserve the exact modeled UTF-16 text",
                    result.expectedText,
                    state.text,
                )
                assertEditingInvariants(state)
                operationCounts[operation] = checkNotNull(operationCounts[operation]) + 1
                appendTrace(
                    trace = trace,
                    entry = "step=$step operation=$operation ${result.detail} after=${state.toTraceSummary()}",
                )
            } catch (failure: Throwable) {
                appendTrace(
                    trace = trace,
                    entry = "step=$step operation=$operation before=$beforeSummary FAILED",
                )
                failWithTrace(
                    step = step,
                    operation = operation,
                    trace = trace,
                    failure = failure,
                )
            }
        }

        for (operation in operationFamilies) {
            /** Count for the current family, initialized above and incremented after every pass. */
            val count = checkNotNull(operationCounts[operation])
            assertTrue("fuzz operation $operation was not exercised", count > 0)
        }
        assertEquals("the acceptance run must execute at least 10,000 edits", 12_000, FuzzStepCount)
    }

    /**
     * Covers legacy ill-formed state without allowing the random generator to create new surrogates.
     *
     * The read/update/save paths preserve the original lone code unit exactly. Once the caller
     * explicitly selects that atomic cluster, a normal controller delete removes it and returns the
     * state to well-formed UTF-16.
     */
    @Test
    fun initialLoneSurrogateIsPreservedOnlyUntilExplicitWholeClusterDeletion(): Unit {
        /** Deliberately ill-formed legacy payload; it is never part of the generated insertion pool. */
        val legacyText = "A\uD83DB"
        /** Controller used to prove preservation through public state and snapshot operations. */
        val controller = PixelTextFieldController()
        /** State starts before the isolated high-surrogate cluster. */
        val state = controller.create(initialText = legacyText, selectionStart = 1)
        /** The engine map treats the unpaired code unit as one deterministic atomic cluster. */
        val boundaryMap = PixelGraphemeBoundaryMap(state.text)

        assertFalse("the fixture must remain intentionally ill-formed", legacyText.isWellFormedUtf16())
        assertEquals(legacyText, state.text)
        assertTrue(boundaryMap.isBoundary(1))
        assertTrue(boundaryMap.isBoundary(2))

        controller.updateComposition(state, compositionStart = 1, compositionEnd = 2)
        assertEquals(1, state.compositionStart)
        assertEquals(2, state.compositionEnd)
        assertEquals(legacyText, state.text)

        /** Stable snapshot must not normalize, replace, or drop the invalid legacy code unit. */
        val savedState = controller.saveState(state)
        /** Independent restored target proves the round trip rather than reading the source state. */
        val restoredState = controller.create(initialText = "replacement")
        controller.restoreState(restoredState, savedState)
        assertEquals(legacyText, restoredState.text)

        controller.setSelection(restoredState, selectionStart = 1, selectionEnd = 2)
        assertTrue(controller.deleteForward(restoredState))
        assertEquals("AB", restoredState.text)
        assertTrue(restoredState.text.isWellFormedUtf16())
        assertEditingInvariants(restoredState)
    }

    /** Applies one random public controller operation and returns its exact-text oracle. */
    private fun executeOperation(
        operation: FuzzOperation,
        controller: PixelTextFieldController,
        state: PixelTextFieldState,
        random: Random,
    ): FuzzStepResult {
        /** Exact pre-operation text used by every branch's non-normalizing oracle. */
        val beforeText = state.text
        /** Boundary map tied to the exact pre-operation text. */
        val beforeMap = PixelGraphemeBoundaryMap(beforeText)
        return when (operation) {
            FuzzOperation.UPDATE_TEXT_EDIT -> {
                /** Direct edits over the cap reset to the reviewed corpus to bound runtime and memory. */
                val editedText: String
                /** Source range removed by the modeled direct edit. */
                val replacedRange: PixelUtf16Range
                /** Well-formed fragment inserted without normalization. */
                val replacement: String
                if (beforeText.length > MaximumFuzzTextLength) {
                    editedText = InitialWellFormedCorpus
                    replacedRange = PixelUtf16Range(0, beforeText.length)
                    replacement = InitialWellFormedCorpus
                } else {
                    replacedRange = randomBoundaryRange(beforeMap, random)
                    replacement = randomFragment(random, allowEmpty = true)
                    editedText = beforeText.replaceRange(
                        startIndex = replacedRange.start,
                        endIndex = replacedRange.end,
                        replacement = replacement,
                    )
                }
                /** Arbitrary selection request tests clamp, range expansion, and downstream affinity. */
                val requestedSelectionStart = randomRequestedOffset(random, editedText.length)
                /** Independent end request includes inverted and grapheme-interior ranges. */
                val requestedSelectionEnd = randomRequestedOffset(random, editedText.length)
                /** Random composition request is normalized against the newly edited text. */
                val requestedCompositionStart = randomRequestedOffset(random, editedText.length)
                /** Independent composition end deliberately includes empty and inverted ranges. */
                val requestedCompositionEnd = randomRequestedOffset(random, editedText.length)
                controller.updateText(
                    state = state,
                    text = editedText,
                    selectionStart = requestedSelectionStart,
                    selectionEnd = requestedSelectionEnd,
                    compositionStart = requestedCompositionStart,
                    compositionEnd = requestedCompositionEnd,
                )
                FuzzStepResult(
                    expectedText = editedText,
                    detail = "range=$replacedRange insert=${replacement.toCodeUnitDebug()} " +
                        "selection=$requestedSelectionStart..$requestedSelectionEnd " +
                        "composition=$requestedCompositionStart..$requestedCompositionEnd",
                )
            }

            FuzzOperation.SET_SELECTION -> {
                /** Raw start intentionally need not be in the text or on a boundary. */
                val requestedStart = randomRequestedOffset(random, beforeText.length)
                /** Raw end independently exercises outward expansion and inverted collapse. */
                val requestedEnd = randomRequestedOffset(random, beforeText.length)
                controller.setSelection(state, requestedStart, requestedEnd)
                FuzzStepResult(beforeText, "selection=$requestedStart..$requestedEnd")
            }

            FuzzOperation.SET_COMPOSITION -> {
                /** Raw composition start includes negative clear requests and cluster interiors. */
                val requestedStart = randomRequestedOffset(random, beforeText.length)
                /** Raw composition end includes invalid, empty, and expandable ranges. */
                val requestedEnd = randomRequestedOffset(random, beforeText.length)
                controller.updateComposition(state, requestedStart, requestedEnd)
                FuzzStepResult(beforeText, "composition=$requestedStart..$requestedEnd")
            }

            FuzzOperation.REPLACE_SELECTION -> {
                /** Random raw selection is normalized before deriving the independent text oracle. */
                val requestedRange = setRandomSelection(controller, state, random)
                /** Replacement may be empty so selection deletion is part of this family. */
                val replacement = randomFragment(random, allowEmpty = true)
                /** Controller has already normalized this selection against [beforeText]. */
                val normalizedRange = PixelUtf16Range(state.selectionStart, state.selectionEnd)
                /** Exact expected result; no Unicode normalization is applied by the oracle. */
                val expectedText = beforeText.replaceRange(
                    normalizedRange.start,
                    normalizedRange.end,
                    replacement,
                )
                controller.replaceSelection(state, replacement)
                FuzzStepResult(
                    expectedText,
                    "requested=$requestedRange normalized=$normalizedRange replace=${replacement.toCodeUnitDebug()}",
                )
            }

            FuzzOperation.PASTE -> {
                /** Paste replaces a random normalized selection rather than only appending at EOF. */
                val requestedRange = setRandomSelection(controller, state, random)
                /** Empty paste is retained in the stream to verify its compatibility no-op. */
                val replacement = randomFragment(random, allowEmpty = true)
                /** Actual normalized selection after the public setSelection request. */
                val normalizedRange = PixelUtf16Range(state.selectionStart, state.selectionEnd)
                /** Empty paste historically preserves selected text rather than deleting it. */
                val expectedText = if (replacement.isEmpty()) {
                    beforeText
                } else {
                    beforeText.replaceRange(normalizedRange.start, normalizedRange.end, replacement)
                }
                controller.paste(state, replacement)
                FuzzStepResult(
                    expectedText,
                    "requested=$requestedRange normalized=$normalizedRange paste=${replacement.toCodeUnitDebug()}",
                )
            }

            FuzzOperation.DELETE_BACKWARD -> {
                /** A random selection mixes selected-range deletion with collapsed-caret deletion. */
                val requestedRange = setRandomSelection(controller, state, random)
                /** Stable selection from which the expected adjacent grapheme is derived. */
                val current = beforeMap.expand(state.selectionStart, state.selectionEnd)
                /** Whole range removed by backward deletion under the controller contract. */
                val deletion = if (current.isCollapsed) {
                    PixelUtf16Range(beforeMap.previous(current.start), current.start)
                } else {
                    current
                }
                /** Exact non-normalized result, including decomposed code-unit spelling. */
                val expectedText = beforeText.removeRange(deletion.start, deletion.end)
                controller.deleteBackward(state)
                FuzzStepResult(expectedText, "requested=$requestedRange delete=$deletion")
            }

            FuzzOperation.DELETE_FORWARD -> {
                /** A random selection mixes selected-range deletion with collapsed-caret deletion. */
                val requestedRange = setRandomSelection(controller, state, random)
                /** Stable selection from which the expected adjacent grapheme is derived. */
                val current = beforeMap.expand(state.selectionStart, state.selectionEnd)
                /** Whole range removed by forward deletion under the controller contract. */
                val deletion = if (current.isCollapsed) {
                    PixelUtf16Range(current.end, beforeMap.next(current.end))
                } else {
                    current
                }
                /** Exact non-normalized result, including variation selectors and combining marks. */
                val expectedText = beforeText.removeRange(deletion.start, deletion.end)
                controller.deleteForward(state)
                FuzzStepResult(expectedText, "requested=$requestedRange delete=$deletion")
            }

            FuzzOperation.MOVE_BACKWARD -> {
                /** Extended and collapsed movement must both land on stable boundaries. */
                val extendSelection = random.nextBoolean()
                controller.moveCaretBackward(state, extendSelection)
                FuzzStepResult(beforeText, "extend=$extendSelection")
            }

            FuzzOperation.MOVE_FORWARD -> {
                /** Extended and collapsed movement must both land on stable boundaries. */
                val extendSelection = random.nextBoolean()
                controller.moveCaretForward(state, extendSelection)
                FuzzStepResult(beforeText, "extend=$extendSelection")
            }

            FuzzOperation.CUT -> {
                /** Cut is fuzzed separately from delete because it also returns exact clipboard text. */
                val requestedRange = setRandomSelection(controller, state, random)
                /** Stable selection used to model both clipboard payload and remaining text. */
                val normalizedRange = PixelUtf16Range(state.selectionStart, state.selectionEnd)
                /** Expected clipboard payload; collapsed selections return null. */
                val expectedClipboard = if (normalizedRange.isCollapsed) {
                    null
                } else {
                    beforeText.substring(normalizedRange.start, normalizedRange.end)
                }
                /** Exact expected remaining text after a non-empty cut. */
                val expectedText = if (normalizedRange.isCollapsed) {
                    beforeText
                } else {
                    beforeText.removeRange(normalizedRange.start, normalizedRange.end)
                }
                /** Actual controller clipboard payload validated independently from final text. */
                val actualClipboard = controller.cutSelection(state)
                assertEquals("cut clipboard must contain whole graphemes", expectedClipboard, actualClipboard)
                FuzzStepResult(expectedText, "requested=$requestedRange cut=$normalizedRange")
            }

            FuzzOperation.SELECT_WORD -> {
                /** Word hit includes negative, trailing, and grapheme-interior UTF-16 positions. */
                val requestedIndex = randomRequestedOffset(random, beforeText.length)
                controller.selectWordAt(state, requestedIndex)
                FuzzStepResult(beforeText, "index=$requestedIndex")
            }
        }
    }

    /** Sets an arbitrary public selection and returns the raw request for the operation trace. */
    private fun setRandomSelection(
        controller: PixelTextFieldController,
        state: PixelTextFieldState,
        random: Random,
    ): PixelUtf16Range {
        /** Raw endpoints are stored in sorted order so this helper usually exercises a true range. */
        val first = randomRequestedOffset(random, state.text.length)
        /** Independent endpoint can still make the request empty after clamp. */
        val second = randomRequestedOffset(random, state.text.length)
        /** Trace range cannot represent negative endpoints, so clamp only this diagnostic value. */
        val diagnosticStart = min(first, second).coerceAtLeast(0)
        /** Diagnostic end also clamps to maintain [PixelUtf16Range]'s constructor invariant. */
        val diagnosticEnd = max(first, second).coerceAtLeast(diagnosticStart)
        controller.setSelection(state, min(first, second), max(first, second))
        return PixelUtf16Range(diagnosticStart, diagnosticEnd)
    }

    /** Returns one range whose endpoints are actual boundaries in [map]. */
    private fun randomBoundaryRange(
        map: PixelGraphemeBoundaryMap,
        random: Random,
    ): PixelUtf16Range {
        /** Complete stable boundary list derived only from the public next operation. */
        val boundaries = map.boundariesForTesting()
        /** First randomly selected boundary index. */
        val firstIndex = random.nextInt(boundaries.size)
        /** Second randomly selected boundary index. */
        val secondIndex = random.nextInt(boundaries.size)
        return PixelUtf16Range(
            start = boundaries[min(firstIndex, secondIndex)],
            end = boundaries[max(firstIndex, secondIndex)],
        )
    }

    /** Enumerates public-map boundaries without inspecting private engine storage. */
    private fun PixelGraphemeBoundaryMap.boundariesForTesting(): IntArray {
        /** Mutable list begins with mandatory GB1 at UTF-16 offset zero. */
        val boundaries = mutableListOf(0)
        /** Current accepted boundary advanced strictly through [PixelGraphemeBoundaryMap.next]. */
        var current = 0
        while (current < utf16Length) {
            current = next(current)
            boundaries += current
        }
        return boundaries.toIntArray()
    }

    /** Asserts all stable public offsets and the well-formed-text invariant after one fuzz step. */
    private fun assertEditingInvariants(state: PixelTextFieldState) {
        /** Fresh map proves offsets are valid for the resulting text, not a stale pre-edit value. */
        val map = PixelGraphemeBoundaryMap(state.text)
        assertTrue("selectionStart=${state.selectionStart} must be a boundary", map.isBoundary(state.selectionStart))
        assertTrue("selectionEnd=${state.selectionEnd} must be a boundary", map.isBoundary(state.selectionEnd))
        assertTrue("selection must be ordered", state.selectionStart <= state.selectionEnd)
        if (state.compositionStart == -1 || state.compositionEnd == -1) {
            assertEquals("composition absence must use -1/-1", -1, state.compositionStart)
            assertEquals("composition absence must use -1/-1", -1, state.compositionEnd)
        } else {
            assertTrue("composition must be non-empty", state.compositionStart < state.compositionEnd)
            assertTrue("compositionStart must be a boundary", map.isBoundary(state.compositionStart))
            assertTrue("compositionEnd must be a boundary", map.isBoundary(state.compositionEnd))
        }
        assertTrue("well-formed generated input must never gain a lone surrogate", state.text.isWellFormedUtf16())
    }

    /** Returns a random request from four code units before the text through four after it. */
    private fun randomRequestedOffset(random: Random, textLength: Int): Int {
        /** Inclusive diagnostic margin exercises both clamp directions on every text length. */
        val margin = 4
        return random.nextInt(textLength + margin * 2 + 1) - margin
    }

    /** Selects a reviewed well-formed insertion fragment, optionally including the empty string. */
    private fun randomFragment(random: Random, allowEmpty: Boolean): String {
        /** First valid index excludes the empty sentinel when the current operation requires content. */
        val firstIndex = if (allowEmpty) 0 else 1
        return WellFormedFragments[firstIndex + random.nextInt(WellFormedFragments.size - firstIndex)]
    }

    /** Checks UTF-16 pairing directly so a platform replacement character cannot hide corruption. */
    private fun String.isWellFormedUtf16(): Boolean {
        /** Current UTF-16 code-unit index. */
        var index = 0
        while (index < length) {
            /** Current code unit examined without code-point replacement semantics. */
            val codeUnit = this[index]
            when {
                codeUnit.isHighSurrogate() -> {
                    if (index + 1 >= length || !this[index + 1].isLowSurrogate()) return false
                    index += 2
                }
                codeUnit.isLowSurrogate() -> return false
                else -> index += 1
            }
        }
        return true
    }

    /** Appends one trace record while retaining only the most recent bounded replay window. */
    private fun appendTrace(trace: ArrayDeque<String>, entry: String) {
        if (trace.size == MaximumTraceEntries) {
            trace.removeFirst()
        }
        trace.addLast(entry)
    }

    /** Throws one assertion containing seed, step, operation, and the bounded operation trace. */
    private fun failWithTrace(
        step: Int,
        operation: FuzzOperation,
        trace: ArrayDeque<String>,
        failure: Throwable,
    ): Nothing {
        /** Self-contained replay diagnostics required for deterministic CI triage. */
        val message = buildString {
            append("Pixel text fuzz failed: seed=")
            append(FuzzSeed)
            append(" step=")
            append(step)
            append(" operation=")
            append(operation)
            append("\noperation trace (oldest to newest):\n")
            append(trace.joinToString(separator = "\n"))
        }
        /** AssertionError preserves the original stack as its cause while surfacing replay data. */
        val assertion = AssertionError(message)
        assertion.initCause(failure)
        throw assertion
    }

    /** Produces a compact state snapshot suitable for the bounded failure trace. */
    private fun PixelTextFieldState.toTraceSummary(): String {
        return "len=${text.length},selection=$selectionStart..$selectionEnd," +
            "composition=$compositionStart..$compositionEnd,text=${text.toCodeUnitDebug()}"
    }

    /** Renders a bounded UTF-16 code-unit sequence without losing lone-surrogate diagnostics. */
    private fun String.toCodeUnitDebug(): String {
        /** Prefix prevents long fuzz states from overwhelming a test report. */
        val displayed = take(MaximumDebugCodeUnits)
        /** Hex form is stable even for controls, combining marks, and unpaired surrogates. */
        val encoded = displayed.toList().joinToString(prefix = "[", postfix = "]") { codeUnit ->
            "U+%04X".format(codeUnit.code)
        }
        return if (length > displayed.length) "$encoded…(+${length - displayed.length})" else encoded
    }

    /** Fixed operation families whose names are persisted in replay diagnostics. */
    private enum class FuzzOperation {
        /** Direct whole-value edit through updateText. */
        UPDATE_TEXT_EDIT,

        /** Arbitrary UTF-16 selection request. */
        SET_SELECTION,

        /** Arbitrary IME composition request. */
        SET_COMPOSITION,

        /** Replacement through the shared selection primitive. */
        REPLACE_SELECTION,

        /** Clipboard-style paste, including compatibility no-op empty paste. */
        PASTE,

        /** Whole-grapheme deletion before the current selection or caret. */
        DELETE_BACKWARD,

        /** Whole-grapheme deletion after the current selection or caret. */
        DELETE_FORWARD,

        /** Logical movement to the preceding grapheme boundary. */
        MOVE_BACKWARD,

        /** Logical movement to the following grapheme boundary. */
        MOVE_FORWARD,

        /** Clipboard cut over a normalized selected range. */
        CUT,

        /** Word/cluster selection from an arbitrary hit-test offset. */
        SELECT_WORD,
    }

    /** Exact text oracle and random-choice trace returned by one operation. */
    private data class FuzzStepResult(
        /** UTF-16 text expected after the operation, without normalization. */
        val expectedText: String,
        /** Human-readable random inputs needed to understand a replayed step. */
        val detail: String,
    )

    /** Constants and immutable reviewed corpus shared by both fuzz tests. */
    private companion object {
        /** Fixed reproducibility seed; decimal form is copied directly into failure messages. */
        const val FuzzSeed: Long = 5_783_548_151_648_587L

        /** Acceptance workload exceeds the M5-3C minimum of 10,000 operations. */
        const val FuzzStepCount: Int = 12_000

        /** Bound preventing a rare insertion-heavy stream from turning into a memory benchmark. */
        const val MaximumFuzzTextLength: Int = 512

        /** Number of recent operations retained in every replayable failure. */
        const val MaximumTraceEntries: Int = 64

        /** Number of code units shown per state in the failure trace. */
        const val MaximumDebugCodeUnits: Int = 48

        /**
         * Well-formed insertion corpus: empty, ASCII, decomposed, supplementary, skin tone, family
         * ZWJ, variation selector, keycap, RI flag, CRLF, Hangul, spacing mark, and prepend forms.
         */
        val WellFormedFragments: List<String> = listOf(
            "",
            "a",
            "_42",
            "e\u0301",
            "\uD83D\uDE00",
            "\uD83D\uDC4B\uD83C\uDFFD",
            "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66",
            "\u2708\uFE0F",
            "1\uFE0F\u20E3",
            "\uD83C\uDDE8\uD83C\uDDF3",
            "\r\n",
            "\u1100\u1161\u11A8",
            "\u0915\u093E",
            "\u0600A",
        )

        /** Initial text guarantees every required well-formed sample is exercised before mutation. */
        val InitialWellFormedCorpus: String = WellFormedFragments
            .drop(1)
            .joinToString(separator = "|")
    }
}
