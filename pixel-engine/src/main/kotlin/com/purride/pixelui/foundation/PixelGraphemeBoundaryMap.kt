package com.purride.pixelui

import com.purride.pixelui.internal.text.UnicodeGraphemeBreakProperty
import com.purride.pixelui.internal.text.UnicodeGraphemeData
import com.purride.pixelui.internal.text.UnicodeIndicConjunctBreakProperty

/**
 * 定义 `PixelUtf16Range` 在 `PixelGraphemeBoundaryMap` 中承担的数据与行为边界。
 *
 * Half-open range expressed in UTF-16 offsets, matching Android selection and accessibility APIs.
 *
 * This value does not claim that either endpoint is a grapheme boundary by itself. Construct it
 * through [PixelGraphemeBoundaryMap.expand] when a caller needs an outward-normalized grapheme
 * selection.
 *
 * @property start Inclusive UTF-16 offset, greater than or equal to zero.
 * @property end Exclusive UTF-16 offset, greater than or equal to [start].
 * @throws IllegalArgumentException when an endpoint is negative or [end] precedes [start].
 */
public data class PixelUtf16Range(
    public val start: Int,
    public val end: Int,
) {
    init {
        require(start >= 0) { "PixelUtf16Range.start must be >= 0, got $start" }
        require(end >= start) {
            "PixelUtf16Range.end must be >= start, got start=$start, end=$end"
        }
    }

    /** 公开 `PixelGraphemeBoundaryMap` 的 `length` 配置或运行值。
 *
 * Number of UTF-16 code units in this half-open range.
 */
    public val length: Int
        get() = end - start

    /** 表示 `PixelGraphemeBoundaryMap` 当前是否满足 `isCollapsed` 对应条件。
 *
 * Whether this range represents one collapsed caret rather than selected text.
 */
    public val isCollapsed: Boolean
        get() = start == end
}

/**
 * 定义 `PixelGraphemeBoundaryMap` 在 `PixelGraphemeBoundaryMap` 中承担的数据与行为边界。
 *
 * Immutable extended-grapheme boundary map for one exact [text] value.
 *
 * Offsets are UTF-16 code-unit offsets so the map can be used directly with Android selection,
 * composition, and Accessibility APIs. Boundary decisions are made from engine-owned Unicode
 * [UnicodeVersion] data and the default extended grapheme rules GB1 through GB999 from UAX #29
 * revision 47. The production algorithm does not call a platform `BreakIterator` or ICU API and
 * never normalizes or otherwise rewrites [text].
 *
 * A well-formed surrogate pair is decoded as one Unicode code point, so its internal UTF-16 offset
 * is never a boundary. Ill-formed UTF-16 is preserved byte-for-code-unit: each unpaired surrogate
 * is treated as a boundary-forcing control and therefore remains one isolated atomic cluster. That
 * deterministic profile only applies outside Unicode scalar-value strings and allows a later
 * editing layer to reject newly orphaned surrogates without this read-only map mutating content.
 *
 * Construction performs one text pass and fixed-table binary property lookups. It stores only the
 * sorted boundary offsets; every query is `O(log graphemeCount)` and the retained memory is
 * `O(graphemeCount)`.
 *
 * @property text Exact, non-normalized text represented by this immutable map.
 */
public class PixelGraphemeBoundaryMap(
    public val text: String,
) {
    /** Sorted UTF-16 offsets containing GB1, GB2, and every accepted internal boundary. */
    private val boundaryOffsets: IntArray = buildBoundaryOffsets(text)

    /** 公开 `PixelGraphemeBoundaryMap` 的 `utf16Length` 配置或运行值。
 *
 * UTF-16 code-unit length shared with Android text APIs.
 */
    public val utf16Length: Int
        get() = text.length

    /** 公开 `PixelGraphemeBoundaryMap` 的 `graphemeCount` 配置或运行值。
 *
 * Number of extended grapheme clusters represented by this map.
 */
    public val graphemeCount: Int
        get() = boundaryOffsets.size - 1

    /**
 * 判断 `PixelGraphemeBoundaryMap` 是否满足 `isBoundary` 条件，不修改现有状态。
 *
     * Returns whether [offset] is an extended-grapheme boundary.
     *
     * Values outside `0..utf16Length` are not valid text positions and return `false`.
     */
    public fun isBoundary(offset: Int): Boolean {
        if (offset !in 0..utf16Length) return false
        /** Insertion point locating [offset] in the sorted boundary table. */
        val boundaryIndex = lowerBound(offset)
        return boundaryIndex < boundaryOffsets.size && boundaryOffsets[boundaryIndex] == offset
    }

    /**
 * 执行 `PixelGraphemeBoundaryMap` 的 `previous` 公开行为；具体参数、返回和副作用见下文。
 *
     * Returns the boundary strictly before [offset], saturating at zero.
     *
     * An offset inside a grapheme resolves to that grapheme's leading boundary. An offset already
     * on a boundary moves to the preceding grapheme, which makes this operation suitable for
     * backward caret movement and backward deletion.
     *
     * Inputs outside the text are clamped before applying strict movement semantics.
     */
    public fun previous(offset: Int): Int {
        /** Valid text position used for strict backward movement. */
        val clampedOffset = offset.coerceIn(0, utf16Length)
        if (clampedOffset == 0) return 0
        /** Table entry immediately preceding the clamped position. */
        val previousIndex = lowerBound(clampedOffset) - 1
        return if (previousIndex >= 0) boundaryOffsets[previousIndex] else 0
    }

    /**
 * 执行 `PixelGraphemeBoundaryMap` 的 `next` 公开行为；具体参数、返回和副作用见下文。
 *
     * Returns the boundary strictly after [offset], saturating at [utf16Length].
     *
     * An offset inside a grapheme resolves to that grapheme's trailing boundary. An offset already
     * on a boundary moves to the following grapheme, which makes this operation suitable for
     * forward caret movement and forward deletion.
     *
     * Inputs outside the text are clamped before applying strict movement semantics.
     */
    public fun next(offset: Int): Int {
        /** Valid text position used for strict forward movement. */
        val clampedOffset = offset.coerceIn(0, utf16Length)
        if (clampedOffset == utf16Length) return utf16Length
        /** Table entry immediately following the clamped position. */
        val nextIndex = upperBound(clampedOffset)
        return if (nextIndex < boundaryOffsets.size) {
            boundaryOffsets[nextIndex]
        } else {
            utf16Length
        }
    }

    /**
 * 执行 `PixelGraphemeBoundaryMap` 的 `floor` 公开行为；具体参数、返回和副作用见下文。
 *
     * Snaps [offset] to the greatest boundary less than or equal to it.
     *
     * Inputs outside the text clamp to the nearest endpoint before snapping.
     */
    public fun floor(offset: Int): Int {
        /** Valid position whose preceding-or-equal boundary is requested. */
        val clampedOffset = offset.coerceIn(0, utf16Length)
        return boundaryOffsets[upperBound(clampedOffset) - 1]
    }

    /**
 * 执行 `PixelGraphemeBoundaryMap` 的 `ceil` 公开行为；具体参数、返回和副作用见下文。
 *
     * Snaps [offset] to the smallest boundary greater than or equal to it.
     *
     * Inputs outside the text clamp to the nearest endpoint before snapping.
     */
    public fun ceil(offset: Int): Int {
        /** Valid position whose following-or-equal boundary is requested. */
        val clampedOffset = offset.coerceIn(0, utf16Length)
        return boundaryOffsets[lowerBound(clampedOffset)]
    }

    /**
 * 执行 `PixelGraphemeBoundaryMap` 的 `nearest` 公开行为；具体参数、返回和副作用见下文。
 *
     * Snaps [offset] to its nearest boundary using logical downstream affinity for exact ties.
     *
     * "Downstream" is intentionally defined as the greater UTF-16 offset, independent of visual
     * Bidi order. This fixed rule makes a collapsed caret deterministic until the paragraph layer
     * supplies visual affinity in M5-3D.
     *
     * Inputs outside the text clamp to the nearest endpoint before snapping.
     */
    public fun nearest(offset: Int): Int {
        /** Valid position used for deterministic affinity comparison. */
        val clampedOffset = offset.coerceIn(0, utf16Length)
        /** Candidate before or exactly at the requested position. */
        val floorBoundary = floor(clampedOffset)
        /** Candidate after or exactly at the requested position. */
        val ceilBoundary = ceil(clampedOffset)
        return if (clampedOffset - floorBoundary < ceilBoundary - clampedOffset) {
            floorBoundary
        } else {
            ceilBoundary
        }
    }

    /**
 * 执行 `PixelGraphemeBoundaryMap` 的 `expand` 公开行为；具体参数、返回和副作用见下文。
 *
     * Normalizes a half-open UTF-16 selection to stable grapheme endpoints.
     *
     * Non-collapsed ranges expand outward through [floor] and [ceil], so selected text is never
     * silently discarded. A collapsed range remains collapsed and snaps through [nearest], whose
     * exact-tie behavior is documented as logical downstream affinity.
     *
     * Endpoints clamp to the text. An inverted legacy selection collapses at the normalized
     * [start] rather than reversing caller intent, preserving the controller's historical ABI.
     */
    public fun expand(start: Int, end: Int): PixelUtf16Range {
        /** Selection start constrained to the represented UTF-16 text. */
        val clampedStart = start.coerceIn(0, utf16Length)
        /** Selection end constrained independently before range normalization. */
        val clampedEnd = end.coerceIn(0, utf16Length)
        if (clampedStart >= clampedEnd) {
            /** Collapsed caret chosen with the documented downstream tie affinity. */
            val caret = nearest(clampedStart)
            return PixelUtf16Range(start = caret, end = caret)
        }
        return PixelUtf16Range(start = floor(clampedStart), end = ceil(clampedEnd))
    }

    /** Returns the first boundary-array index whose value is greater than or equal to [offset]. */
    private fun lowerBound(offset: Int): Int {
        /** Inclusive lower edge of the remaining binary-search interval. */
        var low = 0
        /** Exclusive upper edge of the remaining binary-search interval. */
        var high = boundaryOffsets.size
        while (low < high) {
            /** Overflow-safe midpoint of the remaining interval. */
            val middle = (low + high).ushr(1)
            if (boundaryOffsets[middle] < offset) {
                low = middle + 1
            } else {
                high = middle
            }
        }
        return low
    }

    /** Returns the first boundary-array index whose value is strictly greater than [offset]. */
    private fun upperBound(offset: Int): Int {
        /** Inclusive lower edge of the remaining binary-search interval. */
        var low = 0
        /** Exclusive upper edge of the remaining binary-search interval. */
        var high = boundaryOffsets.size
        while (low < high) {
            /** Overflow-safe midpoint of the remaining interval. */
            val middle = (low + high).ushr(1)
            if (boundaryOffsets[middle] <= offset) {
                low = middle + 1
            } else {
                high = middle
            }
        }
        return low
    }

    /** 集中提供 `PixelGraphemeBoundaryMap` 的 `<companion>` 共享入口。
 *
 * Exposes the fixed Unicode contract without consulting the host runtime.
 */
    public companion object {
        /** 公开 `PixelGraphemeBoundaryMap` 的 `UnicodeVersion` 配置或运行值。
 *
 * Unicode data version and UAX #29 behavior implemented by this boundary map.
 */
        public const val UnicodeVersion: String = "17.0.0"

        /**
         * Computes sorted UTF-16 boundaries with one streaming pass over [text].
         *
         * The finite context tracks the only rules that need more than the adjacent pair: GB9c
         * Indic linkers, GB11 pictographic ZWJ prefixes, and GB12/GB13 RI parity.
         */
        private fun buildBoundaryOffsets(text: String): IntArray {
            /** Maximum-sized staging array; the returned copy contains only accepted boundaries. */
            val pendingBoundaries = IntArray(text.length + 1)
            /** Number of initialized entries, beginning with the mandatory GB1 boundary. */
            var boundaryCount = 1
            pendingBoundaries[0] = 0
            if (text.isEmpty()) {
                return pendingBoundaries.copyOf(boundaryCount)
            }

            /** First decoded scalar or isolated unpaired code unit in the stream. */
            val firstCodePoint = decodeCodePoint(text = text, offset = 0)
            /** Break property retained as the left side of the next candidate boundary. */
            var previousProperty = graphemeBreakProperty(firstCodePoint)
            /** Indic-conjunct property seeding the GB9c suffix automaton. */
            val firstIndicProperty =
                UnicodeGraphemeData.indicConjunctBreakProperty(firstCodePoint)
            /** Extended-pictographic membership seeding the GB11 prefix state. */
            val firstIsExtendedPictographic =
                UnicodeGraphemeData.isExtendedPictographic(firstCodePoint)

            /** Length of the current consecutive RI suffix used for GB12/GB13 parity. */
            var regionalIndicatorRunLength =
                if (previousProperty == UnicodeGraphemeBreakProperty.REGIONAL_INDICATOR) 1 else 0
            /** Current GB9c consonant/extend/linker suffix state. */
            var indicSequenceState = advanceIndicSequence(
                state = IndicSequenceState.NONE,
                property = firstIndicProperty,
            )
            /** Whether the latest non-Extend scalar can begin a GB11 pictographic prefix. */
            var lastNonExtendWasExtendedPictographic = false
            /** Whether the immediately preceding ZWJ closes a valid GB11 prefix. */
            var previousZwjHasExtendedPictographicPrefix =
                previousProperty == UnicodeGraphemeBreakProperty.ZWJ &&
                    lastNonExtendWasExtendedPictographic
            if (previousProperty != UnicodeGraphemeBreakProperty.EXTEND) {
                lastNonExtendWasExtendedPictographic = firstIsExtendedPictographic
            }

            /** UTF-16 offset of the candidate scalar to the right of the next boundary. */
            var currentOffset = utf16Width(firstCodePoint)
            while (currentOffset < text.length) {
                /** Scalar or isolated unpaired code unit at [currentOffset]. */
                val currentCodePoint = decodeCodePoint(text = text, offset = currentOffset)
                /** Grapheme-break property on the right side of the candidate boundary. */
                val currentProperty = graphemeBreakProperty(currentCodePoint)
                /** Indic-conjunct property used by GB9c at this candidate boundary. */
                val currentIndicProperty =
                    UnicodeGraphemeData.indicConjunctBreakProperty(currentCodePoint)
                /** Extended-pictographic membership used by GB11 at this candidate boundary. */
                val currentIsExtendedPictographic =
                    UnicodeGraphemeData.isExtendedPictographic(currentCodePoint)

                if (
                    shouldBreak(
                        previousProperty = previousProperty,
                        currentProperty = currentProperty,
                        currentIndicProperty = currentIndicProperty,
                        currentIsExtendedPictographic = currentIsExtendedPictographic,
                        regionalIndicatorRunLength = regionalIndicatorRunLength,
                        indicSequenceState = indicSequenceState,
                        previousZwjHasExtendedPictographicPrefix =
                            previousZwjHasExtendedPictographicPrefix,
                    )
                ) {
                    pendingBoundaries[boundaryCount] = currentOffset
                    boundaryCount += 1
                }

                /** GB11 prefix state to retain if the current scalar itself is ZWJ. */
                val currentZwjHasExtendedPictographicPrefix =
                    currentProperty == UnicodeGraphemeBreakProperty.ZWJ &&
                        lastNonExtendWasExtendedPictographic
                regionalIndicatorRunLength = if (
                    currentProperty == UnicodeGraphemeBreakProperty.REGIONAL_INDICATOR
                ) {
                    if (previousProperty == UnicodeGraphemeBreakProperty.REGIONAL_INDICATOR) {
                        regionalIndicatorRunLength + 1
                    } else {
                        1
                    }
                } else {
                    0
                }
                indicSequenceState = advanceIndicSequence(
                    state = indicSequenceState,
                    property = currentIndicProperty,
                )
                if (currentProperty != UnicodeGraphemeBreakProperty.EXTEND) {
                    lastNonExtendWasExtendedPictographic = currentIsExtendedPictographic
                }

                previousProperty = currentProperty
                previousZwjHasExtendedPictographicPrefix =
                    currentZwjHasExtendedPictographicPrefix
                currentOffset += utf16Width(currentCodePoint)
            }

            pendingBoundaries[boundaryCount] = text.length
            boundaryCount += 1
            return pendingBoundaries.copyOf(boundaryCount)
        }

        /** Applies ordered UAX #29 rules GB3 through GB999 at one candidate boundary. */
        private fun shouldBreak(
            previousProperty: UnicodeGraphemeBreakProperty,
            currentProperty: UnicodeGraphemeBreakProperty,
            currentIndicProperty: UnicodeIndicConjunctBreakProperty,
            currentIsExtendedPictographic: Boolean,
            regionalIndicatorRunLength: Int,
            indicSequenceState: IndicSequenceState,
            previousZwjHasExtendedPictographicPrefix: Boolean,
        ): Boolean {
            if (
                previousProperty == UnicodeGraphemeBreakProperty.CR &&
                currentProperty == UnicodeGraphemeBreakProperty.LF
            ) {
                return false // GB3
            }
            if (previousProperty.isBoundaryControl()) {
                return true // GB4
            }
            if (currentProperty.isBoundaryControl()) {
                return true // GB5
            }
            if (
                previousProperty == UnicodeGraphemeBreakProperty.L &&
                (
                    currentProperty == UnicodeGraphemeBreakProperty.L ||
                        currentProperty == UnicodeGraphemeBreakProperty.V ||
                        currentProperty == UnicodeGraphemeBreakProperty.LV ||
                        currentProperty == UnicodeGraphemeBreakProperty.LVT
                    )
            ) {
                return false // GB6
            }
            if (
                (
                    previousProperty == UnicodeGraphemeBreakProperty.LV ||
                        previousProperty == UnicodeGraphemeBreakProperty.V
                    ) &&
                (
                    currentProperty == UnicodeGraphemeBreakProperty.V ||
                        currentProperty == UnicodeGraphemeBreakProperty.T
                    )
            ) {
                return false // GB7
            }
            if (
                (
                    previousProperty == UnicodeGraphemeBreakProperty.LVT ||
                        previousProperty == UnicodeGraphemeBreakProperty.T
                    ) &&
                currentProperty == UnicodeGraphemeBreakProperty.T
            ) {
                return false // GB8
            }
            if (
                currentProperty == UnicodeGraphemeBreakProperty.EXTEND ||
                currentProperty == UnicodeGraphemeBreakProperty.ZWJ
            ) {
                return false // GB9
            }
            if (currentProperty == UnicodeGraphemeBreakProperty.SPACING_MARK) {
                return false // GB9a
            }
            if (previousProperty == UnicodeGraphemeBreakProperty.PREPEND) {
                return false // GB9b
            }
            if (
                currentIndicProperty == UnicodeIndicConjunctBreakProperty.CONSONANT &&
                indicSequenceState == IndicSequenceState.AFTER_LINKER
            ) {
                return false // GB9c
            }
            if (
                currentIsExtendedPictographic &&
                previousProperty == UnicodeGraphemeBreakProperty.ZWJ &&
                previousZwjHasExtendedPictographicPrefix
            ) {
                return false // GB11
            }
            if (
                previousProperty == UnicodeGraphemeBreakProperty.REGIONAL_INDICATOR &&
                currentProperty == UnicodeGraphemeBreakProperty.REGIONAL_INDICATOR &&
                regionalIndicatorRunLength % 2 == 1
            ) {
                return false // GB12 and GB13
            }
            return true // GB999
        }

        /** Updates the suffix automaton required by the Unicode 17 GB9c linker expression. */
        private fun advanceIndicSequence(
            state: IndicSequenceState,
            property: UnicodeIndicConjunctBreakProperty,
        ): IndicSequenceState {
            return when (property) {
                UnicodeIndicConjunctBreakProperty.NONE -> IndicSequenceState.NONE
                UnicodeIndicConjunctBreakProperty.CONSONANT ->
                    IndicSequenceState.AFTER_CONSONANT
                UnicodeIndicConjunctBreakProperty.EXTEND -> state
                UnicodeIndicConjunctBreakProperty.LINKER -> {
                    if (state == IndicSequenceState.NONE) {
                        IndicSequenceState.NONE
                    } else {
                        IndicSequenceState.AFTER_LINKER
                    }
                }
            }
        }

        /** Decodes one valid surrogate pair or preserves one unpaired UTF-16 code unit verbatim. */
        private fun decodeCodePoint(text: String, offset: Int): Int {
            /** First UTF-16 code unit, returned directly unless it starts a valid pair. */
            val first = text[offset].code
            if (first !in HIGH_SURROGATE_START..HIGH_SURROGATE_END || offset + 1 >= text.length) {
                return first
            }
            /** Candidate low-surrogate code unit following a high surrogate. */
            val second = text[offset + 1].code
            if (second !in LOW_SURROGATE_START..LOW_SURROGATE_END) {
                return first
            }
            return SUPPLEMENTARY_CODE_POINT_START +
                ((first - HIGH_SURROGATE_START) shl SURROGATE_SHIFT) +
                (second - LOW_SURROGATE_START)
        }

        /** Assigns unpaired surrogate code units an isolated, boundary-forcing editing profile. */
        private fun graphemeBreakProperty(codePoint: Int): UnicodeGraphemeBreakProperty {
            return if (codePoint in HIGH_SURROGATE_START..LOW_SURROGATE_END) {
                UnicodeGraphemeBreakProperty.CONTROL
            } else {
                UnicodeGraphemeData.graphemeBreakProperty(codePoint)
            }
        }

        /** Returns the UTF-16 width of a decoded scalar or preserved unpaired surrogate. */
        private fun utf16Width(codePoint: Int): Int {
            return if (codePoint >= SUPPLEMENTARY_CODE_POINT_START) 2 else 1
        }

        /** Whether this property forces GB4/GB5 boundaries around its code point. */
        private fun UnicodeGraphemeBreakProperty.isBoundaryControl(): Boolean {
            return this == UnicodeGraphemeBreakProperty.CONTROL ||
                this == UnicodeGraphemeBreakProperty.CR ||
                this == UnicodeGraphemeBreakProperty.LF
        }

        /** First UTF-16 high-surrogate code unit. */
        private const val HIGH_SURROGATE_START: Int = 0xD800

        /** Last UTF-16 high-surrogate code unit. */
        private const val HIGH_SURROGATE_END: Int = 0xDBFF

        /** First UTF-16 low-surrogate code unit. */
        private const val LOW_SURROGATE_START: Int = 0xDC00

        /** Last UTF-16 low-surrogate code unit. */
        private const val LOW_SURROGATE_END: Int = 0xDFFF

        /** First Unicode code point requiring a UTF-16 surrogate pair. */
        private const val SUPPLEMENTARY_CODE_POINT_START: Int = 0x10000

        /** Bit width of the payload stored in each UTF-16 surrogate. */
        private const val SURROGATE_SHIFT: Int = 10
    }
}

/** Finite GB9c suffix state carried across candidate boundaries. */
private enum class IndicSequenceState {
    /** No suffix currently starts with an InCB consonant. */
    NONE,

    /** A consonant and optional extenders have been seen, but no linker has been seen. */
    AFTER_CONSONANT,

    /** A consonant suffix contains at least one linker and may join the next consonant. */
    AFTER_LINKER,
}
