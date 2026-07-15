#!/usr/bin/env python3
"""Generate the fixed Unicode 17 Bidi property tables and reviewed conformance resources."""

from __future__ import annotations

import argparse
import hashlib
import pathlib
import re
import sys
import urllib.request
from dataclasses import dataclass


# Unicode release whose UAX #9 behavior is frozen by the SDK 1.0 contract.
UNICODE_VERSION = "17.0.0"
# UAX #9 revision reissued for Unicode 17.0.0.
UAX_REVISION = "51"
# Official Unicode Character Database root used only when no local mirror is supplied.
UNICODE_BASE_URL = f"https://www.unicode.org/Public/{UNICODE_VERSION}/ucd"
# Maximum scalar/code-point slot represented by the Unicode Character Database.
UNICODE_LIMIT = 0x110000
# Reviewed UnicodeData-17.0.0.txt digest from which the two BD16-relevant singletons were derived.
UNICODE_DATA_SHA256 = "2e1efc1dcb59c575eedf5ccae60f95229f706ee6d031835247d843c11d96470c"
# Canonical singleton decompositions that affect the Unicode 17 paired-bracket set.
CANONICAL_BRACKET_SINGLETONS = {
    0x2329: 0x3008,
    0x232A: 0x3009,
}


@dataclass(frozen=True)
class OfficialInput:
    """One pinned upstream Unicode input and its immutable SHA-256 digest."""

    # Relative filename used in a local mirror and in diagnostics.
    filename: str
    # Authoritative Unicode Consortium download URL.
    url: str
    # Exact digest that prevents silent upstream or proxy drift.
    sha256: str


# Complete normative/informative inputs needed for data generation and conformance evidence.
OFFICIAL_INPUTS = (
    OfficialInput(
        filename="extracted/DerivedBidiClass.txt",
        url=f"{UNICODE_BASE_URL}/extracted/DerivedBidiClass.txt",
        sha256="4867b4b7f0731ed1bfcd34cc6251211ff1542541fce0734b6fbda139ee80b3a4",
    ),
    OfficialInput(
        filename="BidiBrackets.txt",
        url=f"{UNICODE_BASE_URL}/BidiBrackets.txt",
        sha256="dadbaf38a0d0246e5b805bf8725cb81b7c621f93d030595635f5ba2c2f179428",
    ),
    OfficialInput(
        filename="BidiMirroring.txt",
        url=f"{UNICODE_BASE_URL}/BidiMirroring.txt",
        sha256="a2f16fb873ab4fcdf3221cb1a8a85a134ddd6ed03603181823ff5206af3741ce",
    ),
    OfficialInput(
        filename="BidiTest.txt",
        url=f"{UNICODE_BASE_URL}/BidiTest.txt",
        sha256="888bdfc8090652272d1f859cdb00ae659e2dc6c26740be61ef1d03998a687620",
    ),
    OfficialInput(
        filename="BidiCharacterTest.txt",
        url=f"{UNICODE_BASE_URL}/BidiCharacterTest.txt",
        sha256="a3e6e905ab5afbe318a96df5401d0372a04cd73ef139ab5e3cf0ae241c255488",
    ),
)

# Enumeration order intentionally matches UnicodeBidiReference's byte constants.
BIDI_CLASSES = (
    "L",
    "LRE",
    "LRO",
    "R",
    "AL",
    "RLE",
    "RLO",
    "PDF",
    "EN",
    "ES",
    "ET",
    "AN",
    "CS",
    "NSM",
    "BN",
    "B",
    "S",
    "WS",
    "ON",
    "LRI",
    "RLI",
    "FSI",
    "PDI",
)
# Long property names used by DerivedBidiClass @missing declarations.
BIDI_CLASS_ALIASES = {
    "Left_To_Right": "L",
    "Left_To_Right_Embedding": "LRE",
    "Left_To_Right_Override": "LRO",
    "Right_To_Left": "R",
    "Arabic_Letter": "AL",
    "Right_To_Left_Embedding": "RLE",
    "Right_To_Left_Override": "RLO",
    "Pop_Directional_Format": "PDF",
    "European_Number": "EN",
    "European_Separator": "ES",
    "European_Terminator": "ET",
    "Arabic_Number": "AN",
    "Common_Separator": "CS",
    "Nonspacing_Mark": "NSM",
    "Boundary_Neutral": "BN",
    "Paragraph_Separator": "B",
    "Segment_Separator": "S",
    "White_Space": "WS",
    "Other_Neutral": "ON",
    "Left_To_Right_Isolate": "LRI",
    "Right_To_Left_Isolate": "RLI",
    "First_Strong_Isolate": "FSI",
    "Pop_Directional_Isolate": "PDI",
}
# Pattern shared by property ranges and @missing declarations.
CODE_POINT_RANGE_PATTERN = re.compile(r"^([0-9A-F]+)(?:\.\.([0-9A-F]+))?$")


def parse_args() -> argparse.Namespace:
    """Parse reproducible generator locations without assuming the current directory."""

    # Repository root derived from this checked-in script by default.
    repository_root = pathlib.Path(__file__).resolve().parents[1]
    # Command-line parser supporting offline, checksum-verified regeneration.
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input-dir",
        type=pathlib.Path,
        help="Read pinned official inputs from this directory instead of unicode.org.",
    )
    parser.add_argument(
        "--output-kotlin",
        type=pathlib.Path,
        default=(
            repository_root
            / "pixel-engine/src/main/kotlin/com/purride/pixelui/internal/text/bidi/UnicodeBidiData.kt"
        ),
    )
    parser.add_argument(
        "--output-resource-dir",
        type=pathlib.Path,
        default=repository_root / "pixel-engine/src/test/resources/unicode/17.0.0",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Verify tracked outputs byte-for-byte without modifying the worktree.",
    )
    return parser.parse_args()


def load_official_input(spec: OfficialInput, input_dir: pathlib.Path | None) -> bytes:
    """Load one official file and reject bytes that differ from the reviewed digest."""

    # Local mirror path preserves upstream subdirectories such as extracted/ when present.
    local_path = input_dir / spec.filename if input_dir is not None else None
    if local_path is not None:
        if not local_path.is_file():
            # Tracked test resources intentionally flatten the one extracted/ upstream path.
            local_path = input_dir / pathlib.Path(spec.filename).name
        # Exact offline bytes supplied by the caller.
        payload = local_path.read_bytes()
    else:
        # Exact online bytes fetched from the pinned Unicode release URL.
        with urllib.request.urlopen(spec.url, timeout=60) as response:
            payload = response.read()
    # Digest used both for fail-closed verification and generation diagnostics.
    actual_digest = hashlib.sha256(payload).hexdigest()
    if actual_digest != spec.sha256:
        raise ValueError(
            f"SHA-256 mismatch for {spec.filename}: expected {spec.sha256}, got {actual_digest}"
        )
    return payload


def parse_code_point_range(value: str) -> tuple[int, int]:
    """Parse one UCD scalar or inclusive range into integer endpoints."""

    # Validated hexadecimal range match.
    match = CODE_POINT_RANGE_PATTERN.fullmatch(value.strip())
    if match is None:
        raise ValueError(f"Invalid code-point range: {value!r}")
    # Inclusive start scalar.
    start = int(match.group(1), 16)
    # Inclusive end scalar or the same scalar for a singleton.
    end = int(match.group(2), 16) if match.group(2) else start
    return start, end


def normalize_bidi_class(value: str) -> str:
    """Convert short or long UCD Bidi_Class spelling to the generated enum name."""

    # Short alias used by ordinary DerivedBidiClass data rows.
    if value in BIDI_CLASSES:
        return value
    # Long alias used by @missing declarations.
    normalized = BIDI_CLASS_ALIASES.get(value)
    if normalized is None:
        raise ValueError(f"Unknown Bidi_Class value: {value}")
    return normalized


def parse_bidi_classes(payload: bytes) -> list[str]:
    """Materialize all Unicode code-point Bidi_Class values with @missing precedence."""

    # Default from the first global @missing declaration in the official file.
    values = ["L"] * UNICODE_LIMIT
    # Decoded official property rows and comments.
    lines = payload.decode("utf-8").splitlines()
    # @missing ranges are applied in source order so specialized RTL blocks override global L.
    for line in lines:
        stripped = line.strip()
        if not stripped.startswith("# @missing:"):
            continue
        # Range and long property name following the @missing marker.
        fields = stripped.removeprefix("# @missing:").split(";", maxsplit=1)
        if len(fields) != 2:
            raise ValueError(f"Malformed @missing declaration: {line}")
        # Inclusive code-point range receiving this default.
        start, end = parse_code_point_range(fields[0])
        # Enum-compatible property name.
        bidi_class = normalize_bidi_class(fields[1].strip())
        values[start : end + 1] = [bidi_class] * (end - start + 1)
    # Explicit property rows override every default range.
    for line in lines:
        content = line.split("#", maxsplit=1)[0].strip()
        if not content:
            continue
        # Range and short property fields before the descriptive comment.
        fields = [field.strip() for field in content.split(";")]
        if len(fields) < 2:
            raise ValueError(f"Malformed DerivedBidiClass row: {line}")
        # Inclusive explicit range.
        start, end = parse_code_point_range(fields[0])
        # Explicit short property name.
        bidi_class = normalize_bidi_class(fields[1])
        values[start : end + 1] = [bidi_class] * (end - start + 1)
    return values


def compress_property_ranges(values: list[str]) -> list[tuple[int, int, str]]:
    """Compress a complete property vector into inclusive same-value ranges."""

    if not values:
        return []
    # Compressed output in monotonically increasing scalar order.
    ranges: list[tuple[int, int, str]] = []
    # First scalar in the current property run.
    start = 0
    # Property held by the current run.
    current = values[0]
    for code_point in range(1, len(values)):
        # Property at the next scalar slot.
        value = values[code_point]
        if value == current:
            continue
        ranges.append((start, code_point - 1, current))
        start = code_point
        current = value
    ranges.append((start, len(values) - 1, current))
    return ranges


def canonical_singleton(code_point: int, mappings: dict[int, int]) -> int:
    """Follow the short canonical singleton chain to its stable representative."""

    # Current representative advanced until no singleton decomposition remains.
    current = code_point
    # Cycle guard for malformed input, though reviewed UnicodeData contains no cycle.
    visited: set[int] = set()
    while current in mappings and current not in visited:
        visited.add(current)
        current = mappings[current]
    return current


def parse_brackets(brackets_payload: bytes) -> list[tuple[int, int, int, int]]:
    """Parse bracket type, partner and canonical pair id for UAX #9 N0/BD16."""

    # Reviewed Unicode 17 singleton mapping needed only for U+2329/U+232A equivalence.
    canonical_mappings = CANONICAL_BRACKET_SINGLETONS
    # Sorted generated bracket entries: code point, partner, type ordinal, canonical pair id.
    entries: list[tuple[int, int, int, int]] = []
    for line in brackets_payload.decode("utf-8").splitlines():
        content = line.split("#", maxsplit=1)[0].strip()
        if not content:
            continue
        # Official code point, partner and open/close marker.
        fields = [field.strip() for field in content.split(";")]
        if len(fields) != 3:
            raise ValueError(f"Malformed BidiBrackets row: {line}")
        # Bracket scalar described by this row.
        code_point = int(fields[0], 16)
        # Normative paired bracket scalar.
        partner = int(fields[1], 16)
        # Reference-algorithm bracket type: none=0, open=1, close=2.
        pair_type = {"o": 1, "c": 2}[fields[2]]
        # Stable identity shared by both ends and canonical-equivalent bracket pairs.
        pair_id = min(
            canonical_singleton(code_point, canonical_mappings),
            canonical_singleton(partner, canonical_mappings),
        )
        entries.append((code_point, partner, pair_type, pair_id))
    return sorted(entries)


def parse_mirroring(payload: bytes) -> list[tuple[int, int]]:
    """Parse all character-based Bidi_Mirroring_Glyph mappings."""

    # Sorted source-to-mirror scalar mapping.
    mappings: list[tuple[int, int]] = []
    for line in payload.decode("utf-8").splitlines():
        content = line.split("#", maxsplit=1)[0].strip()
        if not content:
            continue
        # Source and mirror scalar fields.
        fields = [field.strip() for field in content.split(";")]
        if len(fields) != 2:
            raise ValueError(f"Malformed BidiMirroring row: {line}")
        mappings.append((int(fields[0], 16), int(fields[1], 16)))
    return sorted(mappings)


def format_int_array(values: list[int], indent: str = "        ") -> str:
    """Format a compact Kotlin IntArray body with deterministic line wrapping."""

    # Hexadecimal values make Unicode table review substantially easier.
    tokens = [f"0x{value:X}" for value in values]
    # Eight scalars per source line keeps generated diffs bounded and readable.
    lines = [", ".join(tokens[index : index + 8]) for index in range(0, len(tokens), 8)]
    return (",\n" + indent).join(lines)


def format_byte_array(values: list[int], indent: str = "        ") -> str:
    """Format a compact Kotlin byteArrayOf body with deterministic wrapping."""

    # Explicit toByte calls avoid relying on Kotlin's literal inference in generated code.
    tokens = [f"{value}.toByte()" for value in values]
    # Twelve byte values per source line balances file size and reviewability.
    lines = [", ".join(tokens[index : index + 12]) for index in range(0, len(tokens), 12)]
    return (",\n" + indent).join(lines)


def render_kotlin(
    ranges: list[tuple[int, int, str]],
    brackets: list[tuple[int, int, int, int]],
    mirrors: list[tuple[int, int]],
) -> str:
    """Render the complete generated Kotlin lookup implementation."""

    # Integer ordinals aligned with BIDI_CLASSES and the Java reference implementation.
    class_ordinals = {name: index for index, name in enumerate(BIDI_CLASSES)}
    # Inclusive range-start table.
    range_starts = [start for start, _, _ in ranges]
    # Inclusive range-end table.
    range_ends = [end for _, end, _ in ranges]
    # Property ordinal for every compressed range.
    range_types = [class_ordinals[value] for _, _, value in ranges]
    # Sorted bracket code points.
    bracket_code_points = [entry[0] for entry in brackets]
    # Normative bracket partner per code point.
    bracket_partners = [entry[1] for entry in brackets]
    # Open/close byte per bracket code point.
    bracket_types = [entry[2] for entry in brackets]
    # Canonical-equivalence-aware pair identity.
    bracket_pair_ids = [entry[3] for entry in brackets]
    # Sorted mirroring source scalars.
    mirror_sources = [entry[0] for entry in mirrors]
    # Mirroring target scalar aligned with mirror_sources.
    mirror_targets = [entry[1] for entry in mirrors]
    # Generated source contains no locale- or platform-dependent lookup.
    return f'''// Generated by tools/generate_unicode_bidi_data.py. Do not edit manually.
package com.purride.pixelui.internal.text.bidi

/** Unicode {UNICODE_VERSION} Bidi_Class values in the reference algorithm's byte order. */
internal enum class UnicodeBidiClass {{
{chr(10).join(f"    /** Unicode Bidi_Class {name}. */{chr(10)}    {name}," for name in BIDI_CLASSES)}
}}

/** Fixed Unicode {UNICODE_VERSION}/UAX #9 revision {UAX_REVISION} property lookup tables. */
internal object UnicodeBidiData {{
    /** Unicode release used by every generated property entry. */
    internal const val VERSION: String = "{UNICODE_VERSION}"

    /** UAX #9 revision implemented and tested by the engine-owned resolver. */
    internal const val UAX_REVISION: String = "{UAX_REVISION}"

    /** Returns the normative Bidi_Class for one Unicode code point. */
    internal fun bidiClass(codePoint: Int): UnicodeBidiClass {{
        require(codePoint in 0..0x10FFFF) {{ "Invalid Unicode code point: $codePoint" }}
        /** Compressed property range containing the requested scalar. */
        val index = findContainingRange(codePoint)
        return UnicodeBidiClass.entries[BIDI_CLASS_ORDINALS[index].toInt()]
    }}

    /** Returns 0 for none, 1 for opening and 2 for closing paired bracket. */
    internal fun pairedBracketType(codePoint: Int): Byte {{
        /** Exact bracket-table position or a negative insertion point. */
        val index = BRACKET_CODE_POINTS.binarySearch(codePoint)
        return if (index >= 0) BRACKET_TYPES[index] else 0
    }}

    /** Returns the normative paired bracket, or the input scalar when it is not a bracket. */
    internal fun pairedBracketCodePoint(codePoint: Int): Int {{
        /** Exact bracket-table position or a negative insertion point. */
        val index = BRACKET_CODE_POINTS.binarySearch(codePoint)
        return if (index >= 0) BRACKET_PARTNERS[index] else codePoint
    }}

    /** Returns the canonical-equivalence-aware BD16 pair id, or the input for non-brackets. */
    internal fun pairedBracketIdentity(codePoint: Int): Int {{
        /** Exact bracket-table position or a negative insertion point. */
        val index = BRACKET_CODE_POINTS.binarySearch(codePoint)
        return if (index >= 0) BRACKET_PAIR_IDENTITIES[index] else codePoint
    }}

    /** Returns the character-based mirrored scalar, or the unchanged input when none exists. */
    internal fun mirroredCodePoint(codePoint: Int): Int {{
        /** Exact mirroring-table position or a negative insertion point. */
        val index = MIRROR_CODE_POINTS.binarySearch(codePoint)
        return if (index >= 0) MIRROR_TARGETS[index] else codePoint
    }}

    /** Finds the compressed property range that must cover every valid Unicode code point. */
    private fun findContainingRange(codePoint: Int): Int {{
        /** Lower inclusive search bound. */
        var low = 0
        /** Upper inclusive search bound. */
        var high = BIDI_CLASS_RANGE_STARTS.lastIndex
        while (low <= high) {{
            /** Midpoint immune to integer addition overflow. */
            val middle = (low + high).ushr(1)
            when {{
                codePoint < BIDI_CLASS_RANGE_STARTS[middle] -> high = middle - 1
                codePoint > BIDI_CLASS_RANGE_ENDS[middle] -> low = middle + 1
                else -> return middle
            }}
        }}
        error("Generated Bidi_Class table does not cover U+${{codePoint.toString(16)}}")
    }}

    /** Inclusive start of every compressed Bidi_Class range. */
    private val BIDI_CLASS_RANGE_STARTS: IntArray = intArrayOf(
        {format_int_array(range_starts)}
    )

    /** Inclusive end of every compressed Bidi_Class range. */
    private val BIDI_CLASS_RANGE_ENDS: IntArray = intArrayOf(
        {format_int_array(range_ends)}
    )

    /** Bidi_Class ordinal aligned with every compressed range. */
    private val BIDI_CLASS_ORDINALS: ByteArray = byteArrayOf(
        {format_byte_array(range_types)}
    )

    /** Scalars with normative Bidi_Paired_Bracket_Type Open or Close. */
    private val BRACKET_CODE_POINTS: IntArray = intArrayOf(
        {format_int_array(bracket_code_points)}
    )

    /** Normative Bidi_Paired_Bracket target aligned with [BRACKET_CODE_POINTS]. */
    private val BRACKET_PARTNERS: IntArray = intArrayOf(
        {format_int_array(bracket_partners)}
    )

    /** Reference-algorithm open/close byte aligned with [BRACKET_CODE_POINTS]. */
    private val BRACKET_TYPES: ByteArray = byteArrayOf(
        {format_byte_array(bracket_types)}
    )

    /** BD16 canonical pair identity aligned with [BRACKET_CODE_POINTS]. */
    private val BRACKET_PAIR_IDENTITIES: IntArray = intArrayOf(
        {format_int_array(bracket_pair_ids)}
    )

    /** Scalars with a character-based Bidi_Mirroring_Glyph mapping. */
    private val MIRROR_CODE_POINTS: IntArray = intArrayOf(
        {format_int_array(mirror_sources)}
    )

    /** Mirrored scalar aligned with [MIRROR_CODE_POINTS]. */
    private val MIRROR_TARGETS: IntArray = intArrayOf(
        {format_int_array(mirror_targets)}
    )
}}
'''


def write_or_check(path: pathlib.Path, payload: bytes, check: bool) -> None:
    """Write exact bytes or fail closed when a tracked generated artifact has drifted."""

    if check:
        if not path.is_file():
            raise FileNotFoundError(f"Generated artifact is missing: {path}")
        # Checked-in bytes compared without newline or encoding normalization.
        existing = path.read_bytes()
        if existing != payload:
            raise ValueError(f"Generated artifact is stale: {path}")
        return
    # Existing bytes used to preserve incremental build timestamps when output is unchanged.
    existing = path.read_bytes() if path.exists() else None
    if existing == payload:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(payload)


def main() -> int:
    """Load, verify, generate and copy all fixed Unicode 17 Bidi artifacts."""

    # User-selected deterministic input/output locations.
    args = parse_args()
    # Verified payload keyed by its upstream relative filename.
    payloads = {
        spec.filename: load_official_input(spec, args.input_dir) for spec in OFFICIAL_INPUTS
    }
    # Complete Bidi_Class vector compressed for runtime lookup.
    ranges = compress_property_ranges(
        parse_bidi_classes(payloads["extracted/DerivedBidiClass.txt"])
    )
    # Normative paired bracket properties with canonical identities.
    brackets = parse_brackets(payloads["BidiBrackets.txt"])
    # Informative character-based mirroring mappings used by L4 painting.
    mirrors = parse_mirroring(payloads["BidiMirroring.txt"])
    # Generated runtime source.
    kotlin_source = render_kotlin(ranges, brackets, mirrors).encode("utf-8")
    write_or_check(args.output_kotlin, kotlin_source, args.check)
    # Complete official conformance files retained byte-for-byte for JVM tests.
    write_or_check(
        args.output_resource_dir / "BidiTest.txt",
        payloads["BidiTest.txt"],
        args.check,
    )
    write_or_check(
        args.output_resource_dir / "BidiCharacterTest.txt",
        payloads["BidiCharacterTest.txt"],
        args.check,
    )
    # Reviewed property sources retained for table-wide lookup and mirroring verification.
    write_or_check(
        args.output_resource_dir / "DerivedBidiClass.txt",
        payloads["extracted/DerivedBidiClass.txt"],
        args.check,
    )
    write_or_check(
        args.output_resource_dir / "BidiBrackets.txt",
        payloads["BidiBrackets.txt"],
        args.check,
    )
    write_or_check(
        args.output_resource_dir / "BidiMirroring.txt",
        payloads["BidiMirroring.txt"],
        args.check,
    )
    # Concise deterministic generation summary suitable for acceptance logs.
    print(
        f"Unicode {UNICODE_VERSION} UAX#{UAX_REVISION}: "
        f"{len(ranges)} class ranges, {len(brackets)} brackets, {len(mirrors)} mirrors"
    )
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError) as error:
        print(f"Unicode Bidi generation failed: {error}", file=sys.stderr)
        sys.exit(1)
