package com.purride.pixelui.internal.text.bidi

/** Immutable UAX #9 result indexed by original Unicode code-point position. */
internal data class UnicodeBidiResult(
    /** Resolved embedding level for every input code point, including X9 controls. */
    val levels: ByteArray,
    /** Visual-to-logical code-point index map after single-line L1/L2 processing. */
    val visualToLogical: IntArray,
    /** Resolved paragraph embedding level. */
    val paragraphLevel: Byte,
)

/** Engine-owned Unicode 17.0.0/UAX #9 revision 51 bidirectional resolver. */
internal object UnicodeBidiResolver {
    /** Auto paragraph direction sentinel used by the Unicode reference algorithm. */
    internal const val AUTO_PARAGRAPH_LEVEL: Byte = 2

    /** Resolves real Unicode code points with fixed Bidi_Class and paired-bracket properties. */
    internal fun resolveCodePoints(
        /** Exact logical Unicode scalar sequence for one physical paragraph line. */
        codePoints: IntArray,
        /** Explicit LTR/RTL level or [AUTO_PARAGRAPH_LEVEL]. */
        paragraphLevel: Byte,
    ): UnicodeBidiResult {
        if (codePoints.isEmpty()) {
            return UnicodeBidiResult(
                levels = ByteArray(0),
                visualToLogical = IntArray(0),
                paragraphLevel = if (paragraphLevel == 1.toByte()) 1 else 0,
            )
        }
        /** Normative Bidi_Class byte for every input scalar. */
        val types = ByteArray(codePoints.size) { index ->
            UnicodeBidiData.bidiClass(codePoints[index]).ordinal.toByte()
        }
        /** Normative open/close bracket type aligned with [codePoints]. */
        val pairTypes = ByteArray(codePoints.size) { index ->
            UnicodeBidiData.pairedBracketType(codePoints[index])
        }
        /** Canonical-equivalence-aware bracket identity aligned with [codePoints]. */
        val pairValues = IntArray(codePoints.size) { index ->
            UnicodeBidiData.pairedBracketIdentity(codePoints[index])
        }
        return resolvePrepared(types, pairTypes, pairValues, paragraphLevel)
    }

    /** Resolves a conformance-test Bidi_Class sequence without character-specific brackets. */
    internal fun resolveTypes(
        /** Logical Bidi_Class sequence from the official BidiTest corpus. */
        types: List<UnicodeBidiClass>,
        /** Explicit LTR/RTL level or [AUTO_PARAGRAPH_LEVEL]. */
        paragraphLevel: Byte,
    ): UnicodeBidiResult {
        /** Reference byte value for every requested property. */
        val typeBytes = ByteArray(types.size) { index -> types[index].ordinal.toByte() }
        return resolvePrepared(
            types = typeBytes,
            pairTypes = ByteArray(types.size),
            pairValues = IntArray(types.size),
            paragraphLevel = paragraphLevel,
        )
    }

    /** Returns whether one class has no normative level/order entry after UAX #9 rule X9. */
    internal fun isRemovedByX9(type: UnicodeBidiClass): Boolean {
        return UnicodeBidiReference.isRemovedByX9(type.ordinal.toByte())
    }

    /** Runs the reviewed reference core and applies single-line rules L1 and L2. */
    private fun resolvePrepared(
        /** Validated reference-algorithm Bidi_Class bytes. */
        types: ByteArray,
        /** Paired-bracket open/close bytes aligned with [types]. */
        pairTypes: ByteArray,
        /** Paired-bracket canonical identities aligned with [types]. */
        pairValues: IntArray,
        /** Explicit LTR/RTL level or the auto-direction sentinel. */
        paragraphLevel: Byte,
    ): UnicodeBidiResult {
        require(paragraphLevel in 0..AUTO_PARAGRAPH_LEVEL) {
            "paragraphLevel must be 0, 1 or 2: $paragraphLevel"
        }
        /** UAX #9 X1-I2 resolver over already fixed Unicode properties. */
        val reference = UnicodeBidiReference(types, pairTypes, pairValues, paragraphLevel)
        /** Single physical-line break required by L1/L2. */
        val lineBreaks = intArrayOf(types.size)
        return UnicodeBidiResult(
            levels = reference.getLevels(lineBreaks),
            visualToLogical = reference.getReordering(lineBreaks),
            paragraphLevel = reference.baseLevel,
        )
    }
}
