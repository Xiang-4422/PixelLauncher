#!/usr/bin/env python3
"""Generate the engine-owned Unicode 17.0.0 grapheme property tables and test corpus."""

from __future__ import annotations

import argparse
import hashlib
import sys
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable


# The generator deliberately pins every upstream artifact instead of following mutable "latest" URLs.
UNICODE_VERSION = "17.0.0"
UNICODE_BASE_URL = f"https://www.unicode.org/Public/{UNICODE_VERSION}/ucd"
UNICODE_LICENSE_URL = "https://www.unicode.org/license.txt"


@dataclass(frozen=True)
class UpstreamFile:
    """Describes one official Unicode input and the checksum accepted by this generator."""

    filename: str
    url: str
    sha256: str


# Official inputs required by UAX #29 revision 47 for extended grapheme clusters.
UPSTREAM_FILES = (
    UpstreamFile(
        filename="GraphemeBreakProperty.txt",
        url=f"{UNICODE_BASE_URL}/auxiliary/GraphemeBreakProperty.txt",
        sha256="d6b51d1d2ae5c33b451b7ed994b48f1f4dc62b2272a5831e7fd418514a6bae89",
    ),
    UpstreamFile(
        filename="GraphemeBreakTest.txt",
        url=f"{UNICODE_BASE_URL}/auxiliary/GraphemeBreakTest.txt",
        sha256="e2d134d2c52919bace503ebb6a551c1855fe1a1faec18478c78fff254a1793ec",
    ),
    UpstreamFile(
        filename="emoji-data.txt",
        url=f"{UNICODE_BASE_URL}/emoji/emoji-data.txt",
        sha256="2cb2bb9455cda83e8481541ecf5b6dfda66a3bb89efa3fa7c5297eccf607b72b",
    ),
    UpstreamFile(
        filename="DerivedCoreProperties.txt",
        url=f"{UNICODE_BASE_URL}/DerivedCoreProperties.txt",
        sha256="24c7fed1195c482faaefd5c1e7eb821c5ee1fb6de07ecdbaa64b56a99da22c08",
    ),
    UpstreamFile(
        filename="LICENSE-UNICODE.txt",
        url=UNICODE_LICENSE_URL,
        sha256="e7a93b009565cfce55919a381437ac4db883e9da2126fa28b91d12732bc53d96",
    ),
)

# Stable integer encodings keep the generated Kotlin lookup tables compact and reviewable.
GRAPHEME_PROPERTY_CODES = {
    "Other": 0,
    "CR": 1,
    "LF": 2,
    "Control": 3,
    "Extend": 4,
    "ZWJ": 5,
    "Regional_Indicator": 6,
    "Prepend": 7,
    "SpacingMark": 8,
    "L": 9,
    "V": 10,
    "T": 11,
    "LV": 12,
    "LVT": 13,
}

# Unicode 17 GB9c consumes all three non-default Indic_Conjunct_Break values.
INDIC_PROPERTY_CODES = {
    "None": 0,
    "Consonant": 1,
    "Extend": 2,
    "Linker": 3,
}


@dataclass(frozen=True, order=True)
class CodePointRange:
    """Stores one inclusive code-point range and its compact generated property value."""

    start: int
    end: int
    value: int


def parse_args() -> argparse.Namespace:
    """Parses deterministic generation and verification options."""

    # Repository-relative defaults make the documented one-line refresh command reproducible.
    repository_root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source-dir",
        type=Path,
        help="Read already-downloaded official inputs from this directory instead of unicode.org.",
    )
    parser.add_argument(
        "--output-source",
        type=Path,
        default=repository_root
        / "pixel-engine/src/main/kotlin/com/purride/pixelui/internal/text/UnicodeGraphemeData.kt",
        help="Generated Kotlin property-table destination.",
    )
    parser.add_argument(
        "--output-resource-dir",
        type=Path,
        default=repository_root / "pixel-engine/src/test/resources/unicode/17.0.0",
        help="Destination for the official conformance corpus and Unicode license.",
    )
    parser.add_argument(
        "--output-license",
        type=Path,
        default=repository_root
        / "pixel-engine/src/main/resources/META-INF/LICENSE-UNICODE.txt",
        help="Packaged Unicode-3.0 license destination for release artifacts.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Verify tracked outputs byte-for-byte without modifying the worktree.",
    )
    return parser.parse_args()


def sha256(payload: bytes) -> str:
    """Returns the lowercase SHA-256 digest used to pin official Unicode inputs."""

    return hashlib.sha256(payload).hexdigest()


def load_upstream(upstream: UpstreamFile, source_dir: Path | None) -> bytes:
    """Loads and checksum-verifies one local or unicode.org-hosted official input."""

    if source_dir is None:
        # A fixed user agent avoids transparent HTML challenge pages being mistaken for UCD data.
        request = urllib.request.Request(
            upstream.url,
            headers={"User-Agent": "PixelEngine-Unicode-Generator/1.0"},
        )
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = response.read()
    else:
        payload = (source_dir / upstream.filename).read_bytes()

    actual_digest = sha256(payload)
    if actual_digest != upstream.sha256:
        raise ValueError(
            f"Checksum mismatch for {upstream.filename}: "
            f"expected {upstream.sha256}, got {actual_digest}"
        )
    return payload


def parse_code_point_span(field: str) -> tuple[int, int]:
    """Parses one UCD singleton or inclusive hexadecimal code-point range."""

    endpoints = field.strip().split("..")
    start = int(endpoints[0], 16)
    end = int(endpoints[-1], 16)
    return start, end


def merge_ranges(ranges: Iterable[CodePointRange]) -> list[CodePointRange]:
    """Merges adjacent ranges only when their generated property value is identical."""

    merged: list[CodePointRange] = []
    for current in sorted(ranges):
        if current.start > current.end:
            raise ValueError(f"Reversed code-point range: {current}")
        if merged and current.start <= merged[-1].end:
            raise ValueError(f"Overlapping generated ranges: {merged[-1]} and {current}")
        if merged and current.start == merged[-1].end + 1 and current.value == merged[-1].value:
            previous = merged[-1]
            merged[-1] = CodePointRange(previous.start, current.end, current.value)
        else:
            merged.append(current)
    return merged


def parse_property_file(
    payload: bytes,
    property_selector: Callable[[list[str]], str | None],
    value_codes: dict[str, int],
) -> list[CodePointRange]:
    """Parses selected semicolon-delimited UCD properties into disjoint merged ranges."""

    ranges: list[CodePointRange] = []
    for raw_line in payload.decode("utf-8").splitlines():
        # UCD comments are intentionally ignored because generated metadata preserves source hashes.
        data = raw_line.split("#", 1)[0].strip()
        if not data:
            continue
        fields = [field.strip() for field in data.split(";")]
        property_name = property_selector(fields)
        if property_name is None:
            continue
        if property_name not in value_codes:
            raise ValueError(f"Unsupported Unicode property value: {property_name}")
        start, end = parse_code_point_span(fields[0])
        ranges.append(CodePointRange(start, end, value_codes[property_name]))
    return merge_ranges(ranges)


def format_int_array(values: Iterable[int], *, hexadecimal: bool) -> str:
    """Formats a bounded-width Kotlin IntArray initializer for readable table diffs."""

    rendered = [f"0x{value:04X}" if hexadecimal else str(value) for value in values]
    lines = []
    for index in range(0, len(rendered), 8):
        lines.append("        " + ", ".join(rendered[index : index + 8]) + ",")
    return "\n".join(lines)


def render_table_object(
    *,
    object_name: str,
    description: str,
    ranges: list[CodePointRange],
    value_lookup: bool,
) -> str:
    """Renders one independently initialized Kotlin range table to avoid JVM method-size limits."""

    starts = format_int_array((item.start for item in ranges), hexadecimal=True)
    ends = format_int_array((item.end for item in ranges), hexadecimal=True)
    values = format_int_array((item.value for item in ranges), hexadecimal=False)
    value_declaration = (
        f"""

    /** Stable compact property code aligned by index with [rangeStarts] and [rangeEnds]. */
    internal val rangeValues: IntArray = intArrayOf(
{values}
    )"""
        if value_lookup
        else ""
    )
    return f"""
/** {description} */
private object {object_name} {{
    /** Inclusive first code point for every sorted, disjoint generated range. */
    internal val rangeStarts: IntArray = intArrayOf(
{starts}
    )

    /** Inclusive last code point aligned by index with [rangeStarts]. */
    internal val rangeEnds: IntArray = intArrayOf(
{ends}
    ){value_declaration}
}}
"""


def render_kotlin_source(
    grapheme_ranges: list[CodePointRange],
    extended_pictographic_ranges: list[CodePointRange],
    indic_ranges: list[CodePointRange],
    default_ignorable_ranges: list[CodePointRange],
) -> bytes:
    """Renders the complete internal Kotlin Unicode lookup source."""

    grapheme_table = render_table_object(
        object_name="UnicodeGraphemeBreakRanges",
        description="Generated Unicode Grapheme_Cluster_Break ranges.",
        ranges=grapheme_ranges,
        value_lookup=True,
    )
    pictographic_table = render_table_object(
        object_name="UnicodeExtendedPictographicRanges",
        description="Generated Unicode Extended_Pictographic ranges used by GB11.",
        ranges=extended_pictographic_ranges,
        value_lookup=False,
    )
    indic_table = render_table_object(
        object_name="UnicodeIndicConjunctBreakRanges",
        description="Generated Unicode Indic_Conjunct_Break ranges used by GB9c.",
        ranges=indic_ranges,
        value_lookup=True,
    )
    default_ignorable_table = render_table_object(
        object_name="UnicodeDefaultIgnorableRanges",
        description="Generated Unicode Default_Ignorable_Code_Point ranges.",
        ranges=default_ignorable_ranges,
        value_lookup=False,
    )
    source = f'''// Generated by tools/generate_unicode_grapheme_data.py. Do not edit by hand.
// Unicode {UNICODE_VERSION}; UAX #29 revision 47; inputs and SHA-256 values are pinned in that script.
// Unicode data files are licensed under Unicode-3.0; META-INF/LICENSE-UNICODE.txt is packaged.

package com.purride.pixelui.internal.text

/** Unicode Grapheme_Cluster_Break values consumed in GB3 through GB13. */
internal enum class UnicodeGraphemeBreakProperty {{
    /** Default value used when no generated range contains the code point. */
    OTHER,

    /** Carriage return, handled before other control rules. */
    CR,

    /** Line feed, handled with carriage return by GB3. */
    LF,

    /** Control or boundary-forcing format character. */
    CONTROL,

    /** Non-spacing extension or emoji modifier joined by GB9. */
    EXTEND,

    /** Zero-width joiner joined to its left by GB9 and used by GB11. */
    ZWJ,

    /** Regional-indicator symbol paired by GB12 and GB13. */
    REGIONAL_INDICATOR,

    /** Prepending character joined to its right by GB9b. */
    PREPEND,

    /** Spacing combining mark joined by GB9a. */
    SPACING_MARK,

    /** Leading Hangul conjoining jamo. */
    L,

    /** Vowel Hangul conjoining jamo. */
    V,

    /** Trailing Hangul conjoining jamo. */
    T,

    /** Precomposed Hangul syllable without a trailing jamo. */
    LV,

    /** Precomposed Hangul syllable with a trailing jamo. */
    LVT,
}}

/** Unicode Indic_Conjunct_Break values required by the Unicode 17 GB9c rule. */
internal enum class UnicodeIndicConjunctBreakProperty {{
    /** Default value outside a conjunct-forming sequence. */
    NONE,

    /** Consonant that starts or ends a conjunct sequence. */
    CONSONANT,

    /** Extending character permitted around a conjunct linker. */
    EXTEND,

    /** Virama-like linker that suppresses the following consonant boundary. */
    LINKER,
}}

/** Fixed Unicode 17.0.0 property lookup used by the engine-owned grapheme algorithm. */
internal object UnicodeGraphemeData {{
    /** Unicode data version used to generate every lookup table in this object. */
    internal const val VERSION: String = "{UNICODE_VERSION}"

    /** Resolves the normative Grapheme_Cluster_Break value for [codePoint]. */
    internal fun graphemeBreakProperty(codePoint: Int): UnicodeGraphemeBreakProperty {{
        /** Generated range containing [codePoint], or -1 for the normative default. */
        val rangeIndex = findRangeIndex(
            codePoint = codePoint,
            rangeStarts = UnicodeGraphemeBreakRanges.rangeStarts,
            rangeEnds = UnicodeGraphemeBreakRanges.rangeEnds,
        )
        return if (rangeIndex < 0) {{
            UnicodeGraphemeBreakProperty.OTHER
        }} else {{
            UnicodeGraphemeBreakProperty.entries[
                UnicodeGraphemeBreakRanges.rangeValues[rangeIndex]
            ]
        }}
    }}

    /** Returns whether [codePoint] has the Extended_Pictographic property used by GB11. */
    internal fun isExtendedPictographic(codePoint: Int): Boolean {{
        return findRangeIndex(
            codePoint = codePoint,
            rangeStarts = UnicodeExtendedPictographicRanges.rangeStarts,
            rangeEnds = UnicodeExtendedPictographicRanges.rangeEnds,
        ) >= 0
    }}

    /** Resolves the normative Indic_Conjunct_Break value for Unicode 17 GB9c. */
    internal fun indicConjunctBreakProperty(codePoint: Int): UnicodeIndicConjunctBreakProperty {{
        /** Generated range containing [codePoint], or -1 for the normative default. */
        val rangeIndex = findRangeIndex(
            codePoint = codePoint,
            rangeStarts = UnicodeIndicConjunctBreakRanges.rangeStarts,
            rangeEnds = UnicodeIndicConjunctBreakRanges.rangeEnds,
        )
        return if (rangeIndex < 0) {{
            UnicodeIndicConjunctBreakProperty.NONE
        }} else {{
            UnicodeIndicConjunctBreakProperty.entries[
                UnicodeIndicConjunctBreakRanges.rangeValues[rangeIndex]
            ]
        }}
    }}

    /** Returns whether [codePoint] has the fixed Default_Ignorable_Code_Point property. */
    internal fun isDefaultIgnorable(codePoint: Int): Boolean {{
        return findRangeIndex(
            codePoint = codePoint,
            rangeStarts = UnicodeDefaultIgnorableRanges.rangeStarts,
            rangeEnds = UnicodeDefaultIgnorableRanges.rangeEnds,
        ) >= 0
    }}

    /** Finds the containing sorted range without relying on a runtime Unicode or ICU implementation. */
    private fun findRangeIndex(
        codePoint: Int,
        rangeStarts: IntArray,
        rangeEnds: IntArray,
    ): Int {{
        /** Inclusive lower index of the remaining generated-range interval. */
        var low = 0
        /** Inclusive upper index of the remaining generated-range interval. */
        var high = rangeStarts.lastIndex
        while (low <= high) {{
            /** Overflow-safe index used to halve the remaining interval. */
            val middle = (low + high).ushr(1)
            when {{
                codePoint < rangeStarts[middle] -> high = middle - 1
                codePoint > rangeEnds[middle] -> low = middle + 1
                else -> return middle
            }}
        }}
        return -1
    }}
}}
{grapheme_table}{pictographic_table}{indic_table}{default_ignorable_table}'''
    return source.encode("utf-8")


def write_or_check(path: Path, payload: bytes, check: bool) -> None:
    """Writes a generated artifact or verifies an existing artifact byte-for-byte."""

    if check:
        if not path.is_file():
            raise FileNotFoundError(f"Generated artifact is missing: {path}")
        if path.read_bytes() != payload:
            raise ValueError(f"Generated artifact is stale: {path}")
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(payload)


def main() -> int:
    """Loads pinned Unicode inputs, generates lookup tables, and imports licensed test data."""

    args = parse_args()
    upstream_payloads = {
        upstream.filename: load_upstream(upstream, args.source_dir)
        for upstream in UPSTREAM_FILES
    }

    grapheme_ranges = parse_property_file(
        upstream_payloads["GraphemeBreakProperty.txt"],
        property_selector=lambda fields: fields[1] if len(fields) >= 2 else None,
        value_codes=GRAPHEME_PROPERTY_CODES,
    )
    extended_pictographic_ranges = parse_property_file(
        upstream_payloads["emoji-data.txt"],
        property_selector=lambda fields: (
            fields[1] if len(fields) >= 2 and fields[1] == "Extended_Pictographic" else None
        ),
        value_codes={"Extended_Pictographic": 1},
    )
    indic_ranges = parse_property_file(
        upstream_payloads["DerivedCoreProperties.txt"],
        property_selector=lambda fields: (
            fields[2] if len(fields) >= 3 and fields[1] == "InCB" else None
        ),
        value_codes=INDIC_PROPERTY_CODES,
    )
    default_ignorable_ranges = parse_property_file(
        upstream_payloads["DerivedCoreProperties.txt"],
        property_selector=lambda fields: (
            fields[1]
            if len(fields) >= 2 and fields[1] == "Default_Ignorable_Code_Point"
            else None
        ),
        value_codes={"Default_Ignorable_Code_Point": 1},
    )

    generated_source = render_kotlin_source(
        grapheme_ranges=grapheme_ranges,
        extended_pictographic_ranges=extended_pictographic_ranges,
        indic_ranges=indic_ranges,
        default_ignorable_ranges=default_ignorable_ranges,
    )
    write_or_check(args.output_source, generated_source, args.check)
    write_or_check(
        args.output_resource_dir / "GraphemeBreakTest.txt",
        upstream_payloads["GraphemeBreakTest.txt"],
        args.check,
    )
    write_or_check(
        args.output_resource_dir / "LICENSE-UNICODE.txt",
        upstream_payloads["LICENSE-UNICODE.txt"],
        args.check,
    )
    write_or_check(
        args.output_license,
        upstream_payloads["LICENSE-UNICODE.txt"],
        args.check,
    )
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError) as error:
        print(f"Unicode grapheme generation failed: {error}", file=sys.stderr)
        sys.exit(1)
