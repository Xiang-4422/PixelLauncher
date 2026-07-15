package com.purride.pixelui.internal.text

import com.purride.pixelui.PixelGraphemeBoundaryMap
import com.purride.pixelui.PixelTextEditingValue

/**
 * 执行 `PixelTextEditingNormalization` 的 `normalizeGraphemeOffsets` 公开行为；具体参数、返回和副作用见下文。
 *
 * Normalizes all public editing offsets against the exact, non-normalized [PixelTextEditingValue.text].
 *
 * Selection ranges expand outwards, collapsed carets use the boundary map's deterministic
 * downstream-tie affinity, and invalid or empty composition ranges are cleared. The returned text
 * is the original String instance and is never NFC-normalized or otherwise rewritten.
 */
public fun PixelTextEditingValue.normalizeGraphemeOffsets(): PixelTextEditingValue {
    /** Boundary authority built from the exact text without platform normalization. */
    val boundaries = PixelGraphemeBoundaryMap(text)
    /** Stable selection produced with outward range and downstream caret rules. */
    val selection = boundaries.expand(selectionStart, selectionEnd)
    /** Non-empty stable composing range, or null when Android reports no valid composition. */
    val composition = if (compositionStart < 0 || compositionEnd <= compositionStart) {
        null
    } else {
        boundaries.expand(compositionStart, compositionEnd).takeUnless { it.isCollapsed }
    }
    return PixelTextEditingValue(
        text = text,
        selectionStart = selection.start,
        selectionEnd = selection.end,
        compositionStart = composition?.start ?: NO_COMPOSITION,
        compositionEnd = composition?.end ?: NO_COMPOSITION,
    )
}

/**
 * 判断 `PixelTextEditingNormalization` 是否满足 `isWellFormedUtf16` 条件，不修改现有状态。
 *
 * Returns whether [text] contains only Unicode scalar values plus non-surrogate BMP code points.
 *
 * Existing engine state may intentionally preserve an already present unpaired surrogate for
 * deterministic recovery, but newly committed platform text must pass this check so an IME cannot
 * introduce a fresh orphaned UTF-16 code unit.
 */
public fun isWellFormedUtf16(text: CharSequence): Boolean {
    /** UTF-16 position advanced only across complete scalar values. */
    var offset = 0
    while (offset < text.length) {
        /** Code unit that determines whether one or two units must be consumed. */
        val current = text[offset]
        when {
            current.isHighSurrogate() -> {
                if (offset + 1 >= text.length || !text[offset + 1].isLowSurrogate()) return false
                offset += 2
            }
            current.isLowSurrogate() -> return false
            else -> offset += 1
        }
    }
    return true
}

/**
 * 执行 `PixelTextEditingNormalization` 的 `offsetByCodePointsStrictly` 公开行为；具体参数、返回和副作用见下文。
 *
 * Moves [offset] by at most [codePointDelta] Unicode code points without splitting valid pairs.
 *
 * Android's `deleteSurroundingTextInCodePoints` contract treats an unpaired surrogate encountered
 * in the requested traversal as an invalid range. This helper therefore returns `null` instead of
 * deleting that code unit. Reaching the beginning or end before consuming the requested count is
 * valid and returns the corresponding edge.
 *
 * @param text exact UTF-16 buffer inspected by the platform command.
 * @param offset attached selection/composition edge from which traversal begins.
 * @param codePointDelta signed number of code points to traverse.
 * @return a legal UTF-16 offset, or `null` when the start/range contains malformed surrogate data.
 */
public fun offsetByCodePointsStrictly(
    text: CharSequence,
    offset: Int,
    codePointDelta: Int,
): Int? {
    if (offset !in 0..text.length) return null
    /** UTF-16 cursor advanced only after the current scalar has been validated. */
    var cursor = offset
    if (codePointDelta < 0) {
        /** Long arithmetic keeps `Int.MIN_VALUE` from overflowing while counting backwards. */
        var remaining = -codePointDelta.toLong()
        while (remaining > 0L && cursor > 0) {
            /** Code unit immediately before the current cursor. */
            val trailing = text[--cursor]
            when {
                trailing.isHighSurrogate() -> return null
                trailing.isLowSurrogate() -> {
                    if (cursor <= 0 || !text[cursor - 1].isHighSurrogate()) return null
                    cursor -= 1
                }
            }
            remaining -= 1L
        }
    } else {
        /** Remaining forward scalar count; Long avoids overflow in shared arithmetic. */
        var remaining = codePointDelta.toLong()
        while (remaining > 0L && cursor < text.length) {
            /** Code unit at the current forward cursor. */
            val current = text[cursor]
            when {
                current.isLowSurrogate() -> return null
                current.isHighSurrogate() -> {
                    if (cursor + 1 >= text.length || !text[cursor + 1].isLowSurrogate()) return null
                    cursor += 2
                }
                else -> cursor += 1
            }
            remaining -= 1L
        }
    }
    return cursor
}

/** Sentinel used by Android and the retained controller for an absent composition. */
private const val NO_COMPOSITION: Int = -1
