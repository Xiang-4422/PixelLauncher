#!/usr/bin/env python3
"""Verify field-level runtime consumption of the complete Pixel theme token graph."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Mapping, Sequence


# Constructor properties inside PixelComponentTokens are the canonical standard-family inventory.
COMPONENT_TOKEN_PATTERN = re.compile(
    r"\bpublic\s+val\s+([A-Za-z_][A-Za-z0-9_]*)\s*:\s*PixelComponentColorTokens\b",
)

# Every foundation group is paired with the public data class that declares its field inventory.
FOUNDATION_GROUP_TYPES: dict[str, str] = {
    "colors": "PixelColorScheme",
    "typography": "PixelTypographyTokens",
    "spacing": "PixelSpacingTokens",
    "sizes": "PixelSizeTokens",
    "radii": "PixelRadiusTokens",
    "borders": "PixelBorderTokens",
    "elevations": "PixelElevationTokens",
    "motion": "PixelMotionThemeData",
    "components": "PixelComponentTokens",
    "labels": "PixelLabelTokens",
}

# The stable group tuple remains exported for release-task and external unit-test compatibility.
REQUIRED_FOUNDATION_GROUPS: tuple[str, ...] = tuple(FOUNDATION_GROUP_TYPES)

# Component fields are credited only through their public resolver or an explicit property read.
COMPONENT_FIELD_ACCESSORS: dict[str, tuple[str, ...]] = {
    "containerColor": ("resolveContainerColor", "containerColor"),
    "contentColor": ("resolveContentColor", "contentColor"),
    "borderColor": ("resolveBorderColor", "borderColor"),
    "focusIndicator": ("focusIndicatorFor", "focusIndicator"),
    "padding": ("resolvePadding", "padding"),
    "minimumWidth": ("resolveMinimumWidth", "minimumWidth"),
    "minimumHeight": ("resolveMinimumHeight", "minimumHeight"),
    "borderWidth": ("resolveBorderWidth", "borderWidth"),
    "cornerRadius": ("resolveCornerRadius", "cornerRadius"),
    "elevationRole": ("resolveElevation", "elevationRole"),
}

# Two independent state-color channels prevent a decorative one-off read from satisfying a family.
COMPONENT_COLOR_FIELDS: frozenset[str] = frozenset(
    {"containerColor", "contentColor", "borderColor"},
)

# Any resolved layout or paint geometry proves that component tokens affect physical output.
COMPONENT_GEOMETRY_FIELDS: frozenset[str] = frozenset(
    {
        "padding",
        "minimumWidth",
        "minimumHeight",
        "borderWidth",
        "cornerRadius",
        "elevationRole",
    },
)

# Passive surfaces do not own focus; every current or newly added family is focusable by default.
FOCUS_OPTIONAL_COMPONENTS: frozenset[str] = frozenset(
    {
        "dialog",
        "bottomSheet",
        "toast",
        "snackbar",
        "tooltip",
        "progress",
    },
)

# Token declarations and compatibility projections are definitions, not production component hosts.
TOKEN_DEFINITION_TYPES: frozenset[str] = frozenset(
    set(FOUNDATION_GROUP_TYPES.values())
    | {"PixelComponentColorTokens", "PixelComponentTokens", "PixelThemeTokens"},
)

# Kotlin control-flow words must not become accidental resolver call-graph nodes.
NON_CALL_IDENTIFIERS: frozenset[str] = frozenset(
    {"if", "for", "while", "when", "catch", "return", "require", "check"},
)


@dataclass(frozen=True, order=True)
class TokenReference:
    """Records one deterministic source location that contributes runtime evidence."""

    path: str
    line: int


@dataclass(frozen=True)
class CoverageFinding:
    """Describes one missing field, disconnected consumer, or unsafe token branch."""

    category: str
    token: str
    reason: str


@dataclass(frozen=True)
class SourceUnit:
    """Contains original and comment/string-masked forms of one production Kotlin source."""

    path: Path
    relative_path: str
    text: str
    code: str
    is_token_definition: bool


@dataclass(frozen=True)
class ComponentDeclaration:
    """Contains one component family name and its constructor default expression."""

    name: str
    initializer: str


@dataclass(frozen=True)
class ResolverDefinition:
    """Models a Kotlin function that reads foundation fields and may call another resolver."""

    name: str
    field_references: Mapping[tuple[str, str], tuple[TokenReference, ...]]
    callees: frozenset[str]


@dataclass(frozen=True)
class ComponentScan:
    """Contains component bindings, field reads, and identity-branch evidence."""

    bindings: Mapping[str, tuple[TokenReference, ...]]
    consumers: Mapping[str, tuple[TokenReference, ...]]
    fields: Mapping[str, Mapping[str, tuple[TokenReference, ...]]]
    identity_branches: Mapping[str, tuple[TokenReference, ...]]


@dataclass(frozen=True)
class FoundationFieldEvidence:
    """Explains whether one field is read directly or through reachable resolver functions."""

    direct_references: tuple[TokenReference, ...]
    resolver_references: tuple[TokenReference, ...]
    resolver_consumers: Mapping[str, tuple[TokenReference, ...]]

    @property
    def references(self) -> tuple[TokenReference, ...]:
        """Return the stable union used by compatibility report fields and gate decisions."""

        return unique_references(self.direct_references + self.resolver_references)


@dataclass(frozen=True)
class FoundationScan:
    """Contains combined field references and their direct/resolver provenance."""

    fields: Mapping[str, Mapping[str, tuple[TokenReference, ...]]]
    evidence: Mapping[str, Mapping[str, FoundationFieldEvidence]]


@dataclass(frozen=True)
class CoverageResult:
    """Contains complete machine-readable component and foundation scan evidence."""

    component_references: Mapping[str, tuple[TokenReference, ...]]
    component_bindings: Mapping[str, tuple[TokenReference, ...]]
    component_field_references: Mapping[str, Mapping[str, tuple[TokenReference, ...]]]
    foundation_references: Mapping[str, tuple[TokenReference, ...]]
    foundation_field_references: Mapping[str, Mapping[str, tuple[TokenReference, ...]]]
    foundation_field_evidence: Mapping[str, Mapping[str, FoundationFieldEvidence]]
    findings: tuple[CoverageFinding, ...]

    @property
    def status(self) -> str:
        """Return the stable gate outcome consumed by Gradle and release automation."""

        return "failed" if self.findings else "passed"

    @property
    def covered_component_count(self) -> int:
        """Return the number of families without any component-scoped finding."""

        failed_tokens = {
            finding.token.split(".", maxsplit=1)[0]
            for finding in self.findings
            if finding.category.startswith("component")
        }
        return sum(1 for token in self.component_references if token not in failed_tokens)

    @property
    def covered_foundation_count(self) -> int:
        """Return the number of groups whose every declared field has runtime evidence."""

        failed_groups = {
            finding.token.split(".", maxsplit=1)[0]
            for finding in self.findings
            if finding.category.startswith("foundation")
        }
        return sum(1 for group in self.foundation_references if group not in failed_groups)


def parse_arguments(arguments: Sequence[str]) -> argparse.Namespace:
    """Parse explicit token-definition, runtime-source, and report paths."""

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--tokens",
        required=True,
        type=Path,
        help="PixelComponentTokens Kotlin source containing the canonical component inventory.",
    )
    parser.add_argument(
        "--source-root",
        required=True,
        type=Path,
        help="Production Kotlin source root searched for runtime token consumers.",
    )
    parser.add_argument(
        "--report",
        required=True,
        type=Path,
        help="Deterministic JSON evidence destination.",
    )
    return parser.parse_args(arguments)


def mask_non_code(source_text: str) -> str:
    """Replace Kotlin comments and string/character contents while preserving offsets and lines."""

    masked = list(source_text)
    index = 0
    state = "code"
    block_depth = 0
    while index < len(source_text):
        current = source_text[index]
        following = source_text[index + 1] if index + 1 < len(source_text) else ""
        triple = source_text[index:index + 3]
        if state == "code":
            if current == "/" and following == "/":
                masked[index] = masked[index + 1] = " "
                state = "line_comment"
                index += 2
                continue
            if current == "/" and following == "*":
                masked[index] = masked[index + 1] = " "
                block_depth = 1
                state = "block_comment"
                index += 2
                continue
            if triple == '\"\"\"':
                masked[index:index + 3] = [" ", " ", " "]
                state = "triple_string"
                index += 3
                continue
            if current == '"':
                masked[index] = " "
                state = "string"
                index += 1
                continue
            if current == "'":
                masked[index] = " "
                state = "character"
                index += 1
                continue
            index += 1
            continue
        if state == "line_comment":
            if current == "\n":
                state = "code"
            else:
                masked[index] = " "
            index += 1
            continue
        if state == "block_comment":
            if current == "/" and following == "*":
                masked[index] = masked[index + 1] = " "
                block_depth += 1
                index += 2
                continue
            if current == "*" and following == "/":
                masked[index] = masked[index + 1] = " "
                block_depth -= 1
                state = "code" if block_depth == 0 else "block_comment"
                index += 2
                continue
            if current != "\n":
                masked[index] = " "
            index += 1
            continue
        if state == "triple_string":
            if triple == '\"\"\"':
                masked[index:index + 3] = [" ", " ", " "]
                state = "code"
                index += 3
                continue
            if current != "\n":
                masked[index] = " "
            index += 1
            continue
        if current == "\\" and following:
            masked[index] = " "
            if following != "\n":
                masked[index + 1] = " "
            index += 2
            continue
        terminator = '"' if state == "string" else "'"
        if current == terminator:
            masked[index] = " "
            state = "code"
        elif current != "\n":
            masked[index] = " "
        index += 1
    return "".join(masked)


def find_matching_delimiter(
    code: str,
    opening_offset: int,
    opening: str,
    closing: str,
) -> int | None:
    """Return the closing delimiter offset for one balanced Kotlin source region."""

    depth = 0
    for offset in range(opening_offset, len(code)):
        character = code[offset]
        if character == opening:
            depth += 1
        elif character == closing:
            depth -= 1
            if depth == 0:
                return offset
    return None


def constructor_region(code: str, class_name: str) -> tuple[int, int] | None:
    """Return the primary-constructor contents for one named public data class."""

    class_pattern = re.compile(
        rf"\b(?:public\s+)?data\s+class\s+{re.escape(class_name)}\s*\(",
    )
    class_match = class_pattern.search(code)
    if class_match is None:
        return None
    opening_offset = code.find("(", class_match.start(), class_match.end())
    closing_offset = find_matching_delimiter(code, opening_offset, "(", ")")
    if closing_offset is None:
        return None
    return opening_offset + 1, closing_offset


def discover_component_declarations(tokens_path: Path) -> tuple[ComponentDeclaration, ...]:
    """Discover component families and their defaults from PixelComponentTokens."""

    source_text = tokens_path.read_text(encoding="utf-8")
    code = mask_non_code(source_text)
    region = constructor_region(code, "PixelComponentTokens")
    if region is None:
        return ()
    region_start, region_end = region
    constructor_code = code[region_start:region_end]
    matches = tuple(COMPONENT_TOKEN_PATTERN.finditer(constructor_code))
    declarations: list[ComponentDeclaration] = []
    for index, match in enumerate(matches):
        initializer_end = matches[index + 1].start() if index + 1 < len(matches) else len(constructor_code)
        initializer = constructor_code[match.end():initializer_end]
        declarations.append(ComponentDeclaration(name=match.group(1), initializer=initializer))
    return tuple(sorted(declarations, key=lambda declaration: declaration.name))


def collect_source_units(source_root: Path) -> tuple[SourceUnit, ...]:
    """Collect deterministic production Kotlin units and classify token-definition sources."""

    units: list[SourceUnit] = []
    for source_path in sorted(source_root.rglob("*.kt"), key=lambda item: item.as_posix()):
        source_text = source_path.read_text(encoding="utf-8")
        code = mask_non_code(source_text)
        definition_pattern = re.compile(
            r"\bdata\s+class\s+(" + "|".join(map(re.escape, TOKEN_DEFINITION_TYPES)) + r")\b",
        )
        units.append(
            SourceUnit(
                path=source_path,
                relative_path=source_path.relative_to(source_root).as_posix(),
                text=source_text,
                code=code,
                is_token_definition=definition_pattern.search(code) is not None,
            ),
        )
    return tuple(units)


def reference_at(unit: SourceUnit, offset: int) -> TokenReference:
    """Create a one-based source reference for an offset in a source unit."""

    return TokenReference(path=unit.relative_path, line=unit.text.count("\n", 0, offset) + 1)


def unique_references(references: Iterable[TokenReference]) -> tuple[TokenReference, ...]:
    """Return sorted source references with duplicate path/line pairs removed."""

    return tuple(sorted(set(references)))


def enclosing_block_end(code: str, offset: int) -> int:
    """Return the end of the innermost brace block containing an offset."""

    stack: list[int] = []
    for character_offset, character in enumerate(code[:offset]):
        if character == "{":
            stack.append(character_offset)
        elif character == "}" and stack:
            stack.pop()
    if not stack:
        return len(code)
    closing_offset = find_matching_delimiter(code, stack[-1], "{", "}")
    return len(code) if closing_offset is None else closing_offset


def locate_focus_helper_references(
    unit: SourceUnit,
    region_start: int,
    region_end: int,
    alias: str,
) -> tuple[TokenReference, ...]:
    """Locate focus-helper calls that pass the exact component-token alias."""

    references: list[TokenReference] = []
    helper_pattern = re.compile(r"\bwithControlFocusIndicator\s*\(")
    for helper_match in helper_pattern.finditer(unit.code, region_start, region_end):
        opening_offset = unit.code.find("(", helper_match.start(), helper_match.end())
        closing_offset = find_matching_delimiter(unit.code, opening_offset, "(", ")")
        if closing_offset is None or closing_offset > region_end:
            continue
        arguments = unit.code[opening_offset + 1:closing_offset]
        alias_pattern = re.compile(
            rf"\bcomponentTokens\s*=\s*{re.escape(alias)}\b",
        )
        if alias_pattern.search(arguments):
            references.append(reference_at(unit, helper_match.start()))
    return unique_references(references)


def scan_component_consumers(
    units: Sequence[SourceUnit],
    declarations: Sequence[ComponentDeclaration],
) -> ComponentScan:
    """Scan resolver-backed component consumers inside their exact lexical scopes."""

    family_names = tuple(declaration.name for declaration in declarations)
    bindings: dict[str, list[TokenReference]] = {family: [] for family in family_names}
    consumers: dict[str, list[TokenReference]] = {family: [] for family in family_names}
    fields: dict[str, dict[str, list[TokenReference]]] = {
        family: {field: [] for field in COMPONENT_FIELD_ACCESSORS}
        for family in family_names
    }
    identity_branches: dict[str, list[TokenReference]] = {family: [] for family in family_names}

    family_alternation = "|".join(map(re.escape, family_names))
    accessor_to_field = {
        accessor: field
        for field, accessors in COMPONENT_FIELD_ACCESSORS.items()
        for accessor in accessors
    }
    accessor_alternation = "|".join(
        sorted(map(re.escape, accessor_to_field), key=len, reverse=True),
    )
    binding_pattern = re.compile(
        rf"\b(?:val|var)\s+(?P<alias>[A-Za-z_][A-Za-z0-9_]*)"
        rf"(?:\s*:\s*[^=\n]+)?\s*=\s*"
        rf"[^;\n{{}}=]+?\.\s*components\s*\.\s*(?P<family>{family_alternation})\b",
    )
    # A shared renderer may select one of several component families before resolving the same
    # fields. Follow that exact local alias instead of demanding duplicated production code.
    conditional_binding_pattern = re.compile(
        r"\b(?:val|var)\s+(?P<alias>[A-Za-z_][A-Za-z0-9_]*)"
        r"(?:\s*:\s*[^=\n]+)?\s*=\s*when\s*\([^)]*\)\s*\{",
    )
    # Family references are accepted only from the parsed conditional initializer body.
    conditional_family_pattern = re.compile(
        rf"\.\s*components\s*\.\s*(?P<family>{family_alternation})\b",
    )
    direct_pattern = re.compile(
        rf"\.\s*components\s*\.\s*(?P<family>{family_alternation})"
        rf"\s*\.\s*(?P<accessor>{accessor_alternation})\b",
    )

    for unit in units:
        if unit.is_token_definition:
            continue

        def record_alias_consumption(
            alias: str,
            family: str,
            binding_reference: TokenReference,
            usage_start: int,
            block_end: int,
        ) -> None:
            """Credit fields actually read from one local component-token alias."""

            bindings[family].append(binding_reference)
            consumed_fields: set[str] = set()
            alias_accessor_pattern = re.compile(
                rf"\b{re.escape(alias)}\s*\.\s*(?P<accessor>{accessor_alternation})\b",
            )
            for accessor_match in alias_accessor_pattern.finditer(
                unit.code,
                usage_start,
                block_end,
            ):
                field = accessor_to_field[accessor_match.group("accessor")]
                fields[family][field].append(reference_at(unit, accessor_match.start()))
                consumed_fields.add(field)
            helper_references = locate_focus_helper_references(
                unit,
                usage_start,
                block_end,
                alias,
            )
            fields[family]["focusIndicator"].extend(helper_references)
            if helper_references:
                consumed_fields.add("focusIndicator")
            if consumed_fields:
                consumers[family].append(binding_reference)
            identity_pattern = re.compile(
                rf"(?:\b{re.escape(alias)}\s*(?:===|!==|==|!=)\s*"
                rf"PixelComponentTokens\s*\.\s*Default\s*\.\s*{re.escape(family)}\b|"
                rf"\bPixelComponentTokens\s*\.\s*Default\s*\.\s*{re.escape(family)}"
                rf"\s*(?:===|!==|==|!=)\s*{re.escape(alias)}\b)",
            )
            for identity_match in identity_pattern.finditer(
                unit.code,
                usage_start,
                block_end,
            ):
                identity_branches[family].append(reference_at(unit, identity_match.start()))

        for binding_match in binding_pattern.finditer(unit.code):
            alias = binding_match.group("alias")
            family = binding_match.group("family")
            binding_reference = reference_at(unit, binding_match.start())
            block_end = enclosing_block_end(unit.code, binding_match.start())
            record_alias_consumption(
                alias=alias,
                family=family,
                binding_reference=binding_reference,
                usage_start=binding_match.end(),
                block_end=block_end,
            )

        for conditional_match in conditional_binding_pattern.finditer(unit.code):
            alias = conditional_match.group("alias")
            initializer_open = unit.code.rfind(
                "{",
                conditional_match.start(),
                conditional_match.end(),
            )
            initializer_close = find_matching_delimiter(unit.code, initializer_open, "{", "}")
            if initializer_close is None:
                continue
            block_end = enclosing_block_end(unit.code, conditional_match.start())
            # Comments and later unrelated references remain masked or outside this exact range.
            family_matches = conditional_family_pattern.finditer(
                unit.code,
                conditional_match.end(),
                initializer_close,
            )
            for family_match in family_matches:
                family = family_match.group("family")
                record_alias_consumption(
                    alias=alias,
                    family=family,
                    binding_reference=reference_at(unit, family_match.start()),
                    usage_start=initializer_close + 1,
                    block_end=block_end,
                )

        for direct_match in direct_pattern.finditer(unit.code):
            family = direct_match.group("family")
            field = accessor_to_field[direct_match.group("accessor")]
            direct_reference = reference_at(unit, direct_match.start())
            bindings[family].append(direct_reference)
            consumers[family].append(direct_reference)
            fields[family][field].append(direct_reference)

    return ComponentScan(
        bindings={family: unique_references(value) for family, value in bindings.items()},
        consumers={family: unique_references(value) for family, value in consumers.items()},
        fields={
            family: {
                field: unique_references(references)
                for field, references in family_fields.items()
            }
            for family, family_fields in fields.items()
        },
        identity_branches={
            family: unique_references(value)
            for family, value in identity_branches.items()
        },
    )


def discover_foundation_fields(
    units: Sequence[SourceUnit],
) -> dict[str, tuple[str, ...]]:
    """Discover every public constructor field in each required foundation token class."""

    discovered: dict[str, tuple[str, ...]] = {}
    for group, class_name in FOUNDATION_GROUP_TYPES.items():
        group_fields: set[str] = set()
        for unit in units:
            region = constructor_region(unit.code, class_name)
            if region is None:
                continue
            region_start, region_end = region
            constructor_code = unit.code[region_start:region_end]
            field_pattern = re.compile(r"\bpublic\s+val\s+([A-Za-z_][A-Za-z0-9_]*)\s*:")
            group_fields.update(field_pattern.findall(constructor_code))
        discovered[group] = tuple(sorted(group_fields))
    return discovered


def direct_foundation_field_references(
    units: Sequence[SourceUnit],
    inventories: Mapping[str, tuple[str, ...]],
) -> dict[str, dict[str, list[TokenReference]]]:
    """Locate direct `theme.group.field` and group-alias field reads outside definitions."""

    references: dict[str, dict[str, list[TokenReference]]] = {
        group: {field: [] for field in fields}
        for group, fields in inventories.items()
    }
    for unit in units:
        if unit.is_token_definition:
            continue
        for group, group_fields in inventories.items():
            if not group_fields:
                continue
            # Kotlin property references such as `PixelLabelTokens::loading` are real runtime
            # reads when a shared resolver evaluates them, even though they do not use dot syntax.
            group_type = FOUNDATION_GROUP_TYPES[group]
            field_alternation = "|".join(
                sorted(map(re.escape, group_fields), key=len, reverse=True),
            )
            direct_pattern = re.compile(
                rf"\??\.\s*{re.escape(group)}\s*\??\.\s*"
                rf"(?P<field>{field_alternation})\b",
            )
            for direct_match in direct_pattern.finditer(unit.code):
                field = direct_match.group("field")
                references[group][field].append(reference_at(unit, direct_match.start()))

            callable_reference_pattern = re.compile(
                rf"\b{re.escape(group_type)}\s*::\s*(?P<field>{field_alternation})\b",
            )
            for callable_match in callable_reference_pattern.finditer(unit.code):
                field = callable_match.group("field")
                references[group][field].append(reference_at(unit, callable_match.start()))

            alias_pattern = re.compile(
                rf"\b(?:val|var)\s+(?P<alias>[A-Za-z_][A-Za-z0-9_]*)\s*=\s*"
                rf"[^;\n{{}}=]+?\.\s*{re.escape(group)}\b",
            )
            for alias_match in alias_pattern.finditer(unit.code):
                alias = alias_match.group("alias")
                block_end = enclosing_block_end(unit.code, alias_match.start())
                field_pattern = re.compile(
                    rf"\b{re.escape(alias)}\s*\.\s*(?P<field>{field_alternation})\b",
                )
                for field_match in field_pattern.finditer(
                    unit.code,
                    alias_match.end(),
                    block_end,
                ):
                    field = field_match.group("field")
                    references[group][field].append(reference_at(unit, field_match.start()))

            if group == "motion":
                provider_pattern = re.compile(
                    rf"\bPixelMotionTheme\s*\.\s*(?:of|maybeOf)\s*\([^\n;]*?\)"
                    rf"\s*\??\s*\.\s*(?P<field>{field_alternation})\b",
                )
                for provider_match in provider_pattern.finditer(unit.code):
                    field = provider_match.group("field")
                    references[group][field].append(reference_at(unit, provider_match.start()))
    return references


def function_body_region(code: str, parameter_end: int) -> tuple[int, int] | None:
    """Return a block or single-line expression body following a Kotlin function signature."""

    line_end = code.find("\n", parameter_end)
    search_end = len(code) if line_end < 0 else line_end
    block_start = code.find("{", parameter_end, search_end + 1)
    assignment_start = code.find("=", parameter_end, search_end + 1)
    if block_start >= 0 and (assignment_start < 0 or block_start < assignment_start):
        block_end = find_matching_delimiter(code, block_start, "{", "}")
        if block_end is not None:
            return block_start + 1, block_end
    if assignment_start >= 0:
        return assignment_start + 1, search_end
    return None


def enclosing_data_class(
    unit: SourceUnit,
    function_offset: int,
) -> str | None:
    """Return the foundation data class whose brace body contains a function, if any."""

    for class_name in FOUNDATION_GROUP_TYPES.values():
        class_pattern = re.compile(
            rf"\b(?:public\s+)?data\s+class\s+{re.escape(class_name)}\s*\(",
        )
        class_match = class_pattern.search(unit.code)
        if class_match is None or class_match.start() > function_offset:
            continue
        constructor = constructor_region(unit.code, class_name)
        if constructor is None:
            continue
        _, constructor_end = constructor
        class_block_start = unit.code.find("{", constructor_end)
        if class_block_start < 0 or class_block_start > function_offset:
            continue
        class_block_end = find_matching_delimiter(unit.code, class_block_start, "{", "}")
        if class_block_end is not None and function_offset < class_block_end:
            return class_name
    return None


def discover_resolver_definitions(
    units: Sequence[SourceUnit],
    inventories: Mapping[str, tuple[str, ...]],
) -> tuple[ResolverDefinition, ...]:
    """Discover typed or owner-local foundation field reads inside Kotlin functions."""

    type_to_group = {class_name: group for group, class_name in FOUNDATION_GROUP_TYPES.items()}
    definitions: list[ResolverDefinition] = []
    function_pattern = re.compile(r"\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(")
    for unit in units:
        if not unit.is_token_definition:
            continue
        for function_match in function_pattern.finditer(unit.code):
            function_name = function_match.group(1)
            opening_offset = unit.code.find("(", function_match.start(), function_match.end())
            parameter_end = find_matching_delimiter(unit.code, opening_offset, "(", ")")
            if parameter_end is None:
                continue
            body_region = function_body_region(unit.code, parameter_end)
            if body_region is None:
                continue
            body_start, body_end = body_region
            body_code = unit.code[body_start:body_end]
            parameters_code = unit.code[opening_offset + 1:parameter_end]
            field_references: dict[tuple[str, str], list[TokenReference]] = {}
            typed_parameter_pattern = re.compile(
                r"\b([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([A-Za-z_][A-Za-z0-9_]*)\b",
            )
            for parameter_match in typed_parameter_pattern.finditer(parameters_code):
                parameter_name, parameter_type = parameter_match.groups()
                group = type_to_group.get(parameter_type)
                if group is None:
                    continue
                for field in inventories[group]:
                    read_pattern = re.compile(
                        rf"\b{re.escape(parameter_name)}\s*\.\s*{re.escape(field)}\b",
                    )
                    for read_match in read_pattern.finditer(body_code):
                        reference = reference_at(unit, body_start + read_match.start())
                        field_references.setdefault((group, field), []).append(reference)

            # Reachable PixelThemeTokens projections read aggregate fields as `group.field`.
            for group, group_fields in inventories.items():
                if not group_fields:
                    continue
                field_alternation = "|".join(
                    sorted(map(re.escape, group_fields), key=len, reverse=True),
                )
                aggregate_pattern = re.compile(
                    rf"\b{re.escape(group)}\s*\.\s*(?P<field>{field_alternation})\b",
                )
                for aggregate_match in aggregate_pattern.finditer(body_code):
                    field = aggregate_match.group("field")
                    reference = reference_at(unit, body_start + aggregate_match.start())
                    field_references.setdefault((group, field), []).append(reference)

            owner_class = enclosing_data_class(unit, function_match.start())
            owner_group = type_to_group.get(owner_class or "")
            if owner_group is not None:
                for field in inventories[owner_group]:
                    unqualified_pattern = re.compile(
                        rf"(?<![A-Za-z0-9_.]){re.escape(field)}\b",
                    )
                    for read_match in unqualified_pattern.finditer(body_code):
                        reference = reference_at(unit, body_start + read_match.start())
                        field_references.setdefault((owner_group, field), []).append(reference)

            call_pattern = re.compile(r"\b([A-Za-z_][A-Za-z0-9_]*)\s*\(")
            callees = frozenset(
                called_name
                for called_name in call_pattern.findall(body_code)
                if called_name not in NON_CALL_IDENTIFIERS and called_name != function_name
            )
            definitions.append(
                ResolverDefinition(
                    name=function_name,
                    field_references={
                        field_key: unique_references(references)
                        for field_key, references in field_references.items()
                    },
                    callees=callees,
                ),
            )
    return tuple(definitions)


def production_resolver_roots(
    units: Sequence[SourceUnit],
    resolver_names: frozenset[str],
) -> dict[str, tuple[TokenReference, ...]]:
    """Return resolver names and call sites from non-definition production Kotlin sources."""

    called_references: dict[str, list[TokenReference]] = {
        resolver_name: [] for resolver_name in resolver_names
    }
    call_pattern = re.compile(r"\b([A-Za-z_][A-Za-z0-9_]*)\s*\(")
    for unit in units:
        if unit.is_token_definition:
            continue
        for call_match in call_pattern.finditer(unit.code):
            called_name = call_match.group(1)
            if called_name in called_references:
                called_references[called_name].append(reference_at(unit, call_match.start()))
    return {
        # One deterministic call site keeps per-field resolver evidence concise and actionable.
        resolver_name: unique_references(references)[:1]
        for resolver_name, references in called_references.items()
        if references
    }


def reachable_resolver_names(
    definitions: Sequence[ResolverDefinition],
    roots: frozenset[str],
) -> frozenset[str]:
    """Expand production roots through resolver-to-resolver calls until stable."""

    reachable = set(roots)
    changed = True
    while changed:
        changed = False
        for definition in definitions:
            if definition.name not in reachable:
                continue
            for callee in definition.callees:
                if callee not in reachable:
                    reachable.add(callee)
                    changed = True
    return frozenset(reachable)


def scan_foundation_fields(
    units: Sequence[SourceUnit],
    inventories: Mapping[str, tuple[str, ...]],
) -> FoundationScan:
    """Combine direct consumers with field reads in production-reachable resolver chains."""

    direct_references = direct_foundation_field_references(units, inventories)
    resolver_definitions = discover_resolver_definitions(units, inventories)
    resolver_names = frozenset(definition.name for definition in resolver_definitions)
    root_references = production_resolver_roots(units, resolver_names)
    reachability_by_root = {
        root_name: reachable_resolver_names(resolver_definitions, frozenset({root_name}))
        for root_name in root_references
    }
    reachable = frozenset(
        resolver_name
        for resolver_names_from_root in reachability_by_root.values()
        for resolver_name in resolver_names_from_root
    )
    resolver_references: dict[str, dict[str, list[TokenReference]]] = {
        group: {field: [] for field in fields}
        for group, fields in inventories.items()
    }
    resolver_consumers: dict[str, dict[str, dict[str, tuple[TokenReference, ...]]]] = {
        group: {field: {} for field in fields}
        for group, fields in inventories.items()
    }
    for definition in resolver_definitions:
        if definition.name not in reachable:
            continue
        for (group, field), references in definition.field_references.items():
            resolver_references[group][field].extend(references)
            for root_name, root_reachable_names in reachability_by_root.items():
                if definition.name in root_reachable_names:
                    resolver_consumers[group][field][root_name] = root_references[root_name]
    evidence = {
        group: {
            field: FoundationFieldEvidence(
                direct_references=unique_references(direct_references[group][field]),
                resolver_references=unique_references(resolver_references[group][field]),
                resolver_consumers=resolver_consumers[group][field],
            )
            for field in group_fields
        }
        for group, group_fields in inventories.items()
    }
    return FoundationScan(
        fields={
            group: {
                field: field_evidence.references
                for field, field_evidence in group_evidence.items()
            }
            for group, group_evidence in evidence.items()
        },
        evidence=evidence,
    )


def scan_coverage(tokens_path: Path, source_root: Path) -> CoverageResult:
    """Scan production sources for resolver-backed component and field-level foundation use."""

    findings: list[CoverageFinding] = []
    if not tokens_path.is_file():
        return CoverageResult(
            component_references={},
            component_bindings={},
            component_field_references={},
            foundation_references={},
            foundation_field_references={},
            foundation_field_evidence={},
            findings=(
                CoverageFinding("input", "componentTokens", "TOKEN_DEFINITION_NOT_FOUND"),
            ),
        )
    if not source_root.is_dir():
        return CoverageResult(
            component_references={},
            component_bindings={},
            component_field_references={},
            foundation_references={},
            foundation_field_references={},
            foundation_field_evidence={},
            findings=(CoverageFinding("input", "sourceRoot", "SOURCE_ROOT_NOT_FOUND"),),
        )

    declarations = discover_component_declarations(tokens_path)
    if not declarations:
        return CoverageResult(
            component_references={},
            component_bindings={},
            component_field_references={},
            foundation_references={},
            foundation_field_references={},
            foundation_field_evidence={},
            findings=(
                CoverageFinding("input", "componentTokens", "NO_COMPONENT_TOKENS_DISCOVERED"),
            ),
        )
    units = collect_source_units(source_root)
    if not units:
        return CoverageResult(
            component_references={},
            component_bindings={},
            component_field_references={},
            foundation_references={},
            foundation_field_references={},
            foundation_field_evidence={},
            findings=(
                CoverageFinding("input", "sourceRoot", "NO_KOTLIN_SOURCES_DISCOVERED"),
            ),
        )

    component_scan = scan_component_consumers(units, declarations)
    declaration_by_name = {declaration.name: declaration for declaration in declarations}
    for family in sorted(declaration_by_name):
        family_fields = component_scan.fields[family]
        if not component_scan.bindings[family]:
            findings.append(CoverageFinding("component", family, "NO_COMPONENT_TOKEN_BINDING"))
        if not component_scan.consumers[family]:
            findings.append(CoverageFinding("component", family, "NO_RUNTIME_CONSUMER"))
        resolved_color_fields = {
            field for field in COMPONENT_COLOR_FIELDS if family_fields[field]
        }
        if len(resolved_color_fields) < 2:
            findings.append(CoverageFinding("component", family, "INSUFFICIENT_COLOR_FIELD_CONSUMERS"))
        if not any(family_fields[field] for field in COMPONENT_GEOMETRY_FIELDS):
            findings.append(CoverageFinding("component", family, "NO_GEOMETRY_FIELD_CONSUMER"))
        focus_required = family not in FOCUS_OPTIONAL_COMPONENTS
        if focus_required and not family_fields["focusIndicator"]:
            findings.append(CoverageFinding("component", family, "NO_FOCUS_FIELD_CONSUMER"))
        if (
            focus_required
            and re.search(r"\bfocusIndicator\s*=\s*null\b", declaration_by_name[family].initializer)
        ):
            findings.append(CoverageFinding("component", family, "FOCUS_TOKEN_DISABLED"))
        if component_scan.identity_branches[family]:
            findings.append(CoverageFinding("component", family, "TOKEN_IDENTITY_BRANCH"))

    foundation_inventories = discover_foundation_fields(units)
    foundation_scan = scan_foundation_fields(units, foundation_inventories)
    foundation_fields = foundation_scan.fields
    foundation_references: dict[str, tuple[TokenReference, ...]] = {}
    for group in REQUIRED_FOUNDATION_GROUPS:
        group_inventory = foundation_inventories[group]
        if not group_inventory:
            findings.append(
                CoverageFinding("foundation", group, "NO_FOUNDATION_TOKEN_FIELDS_DISCOVERED"),
            )
        for field in group_inventory:
            if not foundation_fields[group][field]:
                findings.append(
                    CoverageFinding(
                        "foundation-field",
                        f"{group}.{field}",
                        "NO_RUNTIME_FIELD_CONSUMER",
                    ),
                )
        foundation_references[group] = unique_references(
            reference
            for field_references in foundation_fields[group].values()
            for reference in field_references
        )

    return CoverageResult(
        component_references=component_scan.consumers,
        component_bindings=component_scan.bindings,
        component_field_references=component_scan.fields,
        foundation_references=foundation_references,
        foundation_field_references=foundation_fields,
        foundation_field_evidence=foundation_scan.evidence,
        findings=tuple(findings),
    )


def serialize_references(references: Iterable[TokenReference]) -> list[dict[str, object]]:
    """Convert deterministic token references into their stable JSON representation."""

    return [
        {"path": reference.path, "line": reference.line}
        for reference in references
    ]


def write_report(report_path: Path, result: CoverageResult) -> None:
    """Write additive schema-v2 evidence while preserving all schema-v1 top-level keys."""

    report_path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "schemaVersion": 2,
        "status": result.status,
        "componentTokenCount": len(result.component_references),
        "coveredComponentTokenCount": result.covered_component_count,
        "foundationGroupCount": len(result.foundation_references),
        "coveredFoundationGroupCount": result.covered_foundation_count,
        "componentReferences": {
            token: serialize_references(references)
            for token, references in result.component_references.items()
        },
        "foundationReferences": {
            token: serialize_references(references)
            for token, references in result.foundation_references.items()
        },
        "componentBindings": {
            token: serialize_references(references)
            for token, references in result.component_bindings.items()
        },
        "componentFieldReferences": {
            token: {
                field: serialize_references(references)
                for field, references in fields.items()
            }
            for token, fields in result.component_field_references.items()
        },
        "foundationFieldReferences": {
            group: {
                field: serialize_references(references)
                for field, references in fields.items()
            }
            for group, fields in result.foundation_field_references.items()
        },
        "foundationFieldEvidence": {
            group: {
                field: {
                    "directReferences": serialize_references(evidence.direct_references),
                    "resolverReferences": serialize_references(evidence.resolver_references),
                    "resolverConsumers": {
                        resolver: serialize_references(references)
                        for resolver, references in evidence.resolver_consumers.items()
                    },
                }
                for field, evidence in fields.items()
            }
            for group, fields in result.foundation_field_evidence.items()
        },
        "findingCount": len(result.findings),
        "findings": [
            {
                "category": finding.category,
                "token": finding.token,
                "reason": finding.reason,
            }
            for finding in result.findings
        ],
    }
    report_path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main(arguments: Sequence[str] | None = None) -> int:
    """Run the token gate and return nonzero for malformed inputs or uncovered fields."""

    parsed = parse_arguments(sys.argv[1:] if arguments is None else arguments)
    result = scan_coverage(parsed.tokens, parsed.source_root)
    write_report(parsed.report, result)
    if result.findings:
        print(
            f"theme-token-coverage: failed with {len(result.findings)} finding(s); "
            f"see {parsed.report}",
            file=sys.stderr,
        )
        return 1
    print(
        "theme-token-coverage: passed "
        f"({len(result.component_references)} component tokens, "
        f"{len(result.foundation_references)} foundation groups)",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
