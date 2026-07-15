#!/usr/bin/env python3
"""Fail when a stable Metalava signature exposes an implementation-internal type."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence


# A segment-aware expression avoids false positives such as a public `InternalState` class.
INTERNAL_REFERENCE_PATTERN = re.compile(
    r"\bcom\.purride(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*\.internal(?:\b|\.)",
)


@dataclass(frozen=True)
class BoundaryFinding:
    """Records one forbidden internal reference without copying the full signature line."""

    line: int
    reference: str


@dataclass(frozen=True)
class BoundaryResult:
    """Contains the deterministic result of scanning one Metalava signature."""

    signature: str
    findings: tuple[BoundaryFinding, ...]

    @property
    def status(self) -> str:
        """Return the stable machine-readable outcome name."""

        return "failed" if self.findings else "passed"


def parse_arguments(arguments: Sequence[str]) -> argparse.Namespace:
    """Parse explicit signature and report paths for Gradle and standalone use."""

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--signature", required=True, type=Path, help="Metalava v4 signature to inspect.")
    parser.add_argument("--report", required=True, type=Path, help="Machine-readable JSON report destination.")
    return parser.parse_args(arguments)


def scan_signature(signature_path: Path, display_path: str | None = None) -> BoundaryResult:
    """Scan every signature line for a package segment named exactly ``internal``."""

    # The caller may supply a repository-relative label to keep generated evidence relocatable.
    signature_label = display_path if display_path is not None else signature_path.as_posix()
    # Findings are ordered by line and match position because both source iterators are deterministic.
    findings: list[BoundaryFinding] = []
    with signature_path.open("r", encoding="utf-8") as signature_file:
        for line_number, line_text in enumerate(signature_file, start=1):
            for match in INTERNAL_REFERENCE_PATTERN.finditer(line_text):
                findings.append(BoundaryFinding(line=line_number, reference=match.group(0).rstrip(".")))
    return BoundaryResult(signature=signature_label, findings=tuple(findings))


def write_report(report_path: Path, result: BoundaryResult) -> None:
    """Write deterministic JSON evidence before the CLI returns its gate exit code."""

    # Parent creation is idempotent and supports both repository and temporary-test paths.
    report_path.parent.mkdir(parents=True, exist_ok=True)
    # A compact schema exposes exact types and line numbers without duplicating entire API declarations.
    payload = {
        "schemaVersion": 1,
        "status": result.status,
        "signature": result.signature,
        "findingCount": len(result.findings),
        "findings": [
            {"line": finding.line, "reference": finding.reference}
            for finding in result.findings
        ],
    }
    report_path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main(arguments: Sequence[str] | None = None) -> int:
    """Run the boundary gate and return nonzero for missing input or leaked internal references."""

    # Explicit arguments make the command directly unit-testable without spawning a subprocess.
    parsed = parse_arguments(sys.argv[1:] if arguments is None else arguments)
    # Gradle invokes from the repository root, so a relative label remains stable across machines.
    try:
        signature_label = parsed.signature.resolve().relative_to(Path.cwd().resolve()).as_posix()
    except ValueError:
        signature_label = parsed.signature.name

    if not parsed.signature.is_file():
        # Missing generated evidence is a tool error, never a successful empty scan.
        error_payload = {
            "schemaVersion": 1,
            "status": "error",
            "signature": signature_label,
            "findingCount": 0,
            "findings": [],
            "reason": "SIGNATURE_NOT_FOUND",
        }
        parsed.report.parent.mkdir(parents=True, exist_ok=True)
        parsed.report.write_text(json.dumps(error_payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print(f"stable-api-boundary: missing signature {signature_label}", file=sys.stderr)
        return 2

    result = scan_signature(parsed.signature, signature_label)
    write_report(parsed.report, result)
    if result.findings:
        print(
            f"stable-api-boundary: failed with {len(result.findings)} internal API reference(s); "
            f"see {parsed.report}",
            file=sys.stderr,
        )
        return 1
    print(f"stable-api-boundary: passed ({signature_label})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
