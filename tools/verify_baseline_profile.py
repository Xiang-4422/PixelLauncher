#!/usr/bin/env python3
"""Verify source Baseline Profiles and their final AAR/APK packaging evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import zipfile
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Sequence


# ART text profiles start with optional hot/startup/post-startup flags followed by an owning descriptor.
PROFILE_RULE_PATTERN = re.compile(r"^[HSP]*(L[^;]+;)(?:->.+)?$")
# The library AAR must expose its text profile at the standard Android Gradle Plugin entry.
AAR_PROFILE_ENTRY = "baseline-prof.txt"
# Release APKs must contain both binary ART profile assets consumed by ProfileInstaller/platform install.
APK_PROFILE_ENTRIES = ("assets/dexopt/baseline.prof", "assets/dexopt/baseline.profm")
# This marker proves the consumer includes the stable ProfileInstaller runtime needed on older Android versions.
PROFILE_INSTALLER_MARKER = "META-INF/androidx.profileinstaller_profileinstaller.version"


@dataclass(frozen=True)
class TextProfileEvidence:
    """Describes one validated, source-controlled ART text profile."""

    path: str
    byte_size: int
    rule_count: int
    sha256: str


@dataclass(frozen=True)
class ArchiveEvidence:
    """Describes one final archive and the profile entries proven to be non-empty."""

    path: str
    byte_size: int
    sha256: str
    profile_entries: dict[str, int]


def parse_arguments(arguments: Sequence[str]) -> argparse.Namespace:
    """Parse source profiles, final artifacts, and the stable report destination."""

    # Repository-relative defaults make the command reproducible from CI and developer shells.
    default_root = Path(__file__).resolve().parents[1]
    # The parser exposes every artifact explicitly so tests can substitute isolated fixtures.
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=default_root, help="Repository root.")
    parser.add_argument("--engine-profile", type=Path, required=True, help="SDK text Baseline Profile.")
    parser.add_argument("--consumer-profile", type=Path, required=True, help="Consumer text Baseline Profile.")
    parser.add_argument("--startup-profile", type=Path, required=True, help="Consumer startup DEX-layout profile.")
    parser.add_argument("--engine-aar", type=Path, required=True, help="Release SDK AAR to inspect.")
    parser.add_argument(
        "--consumer-apk",
        action="append",
        type=Path,
        required=True,
        help="Release-like consumer APK to inspect; may be repeated.",
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=default_root / "build/reports/performance/baseline-profile-packaging.json",
        help="Machine-readable evidence output.",
    )
    return parser.parse_args(arguments)


def sha256_bytes(content: bytes) -> str:
    """Return the lowercase SHA-256 digest for deterministic evidence."""

    return hashlib.sha256(content).hexdigest()


def path_label(path: Path, root: Path) -> str:
    """Return a repository-relative label when possible, otherwise an absolute label."""

    # Both operands are normalized before relative comparison to avoid cwd-dependent reports.
    resolved_path = path.resolve()
    # The repository root is normalized once for the same deterministic reason.
    resolved_root = root.resolve()
    try:
        return resolved_path.relative_to(resolved_root).as_posix()
    except ValueError:
        return resolved_path.as_posix()


def inspect_text_profile(
    path: Path,
    root: Path,
    allowed_owner_prefixes: Sequence[str],
    required_owners: Sequence[str],
    minimum_rule_count: int,
) -> tuple[TextProfileEvidence, bytes]:
    """Validate syntax, ownership, required classes, uniqueness, and minimum useful coverage."""

    # A missing or empty tracked profile must never fall through to archive-only verification.
    resolved_path = path.resolve()
    if not resolved_path.is_file():
        raise FileNotFoundError(f"Text profile does not exist: {resolved_path}")
    # Raw bytes are retained so the AAR can be checked byte-for-byte against reviewed SDK rules.
    content = resolved_path.read_bytes()
    if not content:
        raise ValueError(f"Text profile is empty: {resolved_path}")
    try:
        # UTF-8 is required by the Android Gradle Plugin text-profile contract.
        text = content.decode("utf-8")
    except UnicodeDecodeError as error:
        raise ValueError(f"Text profile is not UTF-8: {resolved_path}") from error
    # Empty lines are excluded from rule counts but rejected below to keep generated files canonical.
    raw_lines = text.splitlines()
    if any(not line for line in raw_lines):
        raise ValueError(f"Text profile contains blank rules: {resolved_path}")
    # Duplicate rules inflate reports and hide generator instability, so they fail closed.
    if len(raw_lines) != len(set(raw_lines)):
        raise ValueError(f"Text profile contains duplicate rules: {resolved_path}")
    if len(raw_lines) < minimum_rule_count:
        raise ValueError(
            f"Text profile has {len(raw_lines)} rules; expected at least {minimum_rule_count}: {resolved_path}",
        )

    # Owners are extracted separately from referenced parameter/return descriptors.
    owners: set[str] = set()
    for line_number, line in enumerate(raw_lines, start=1):
        # A complete match prevents malformed trailing fields from being silently accepted.
        match = PROFILE_RULE_PATTERN.fullmatch(line)
        if match is None:
            raise ValueError(f"Malformed profile rule at {resolved_path}:{line_number}: {line}")
        # The first capture is always the class that owns the class or method rule.
        owner = match.group(1)
        if not any(owner.startswith(prefix) for prefix in allowed_owner_prefixes):
            raise ValueError(f"Out-of-scope profile owner at {resolved_path}:{line_number}: {owner}")
        owners.add(owner)

    # Required class rules lock the profile to the engine/consumer entry points used by the CUJs.
    missing_owners = [owner for owner in required_owners if owner not in owners]
    if missing_owners:
        raise ValueError(f"Text profile is missing required owners {missing_owners}: {resolved_path}")

    # Stable evidence is returned together with the exact source bytes needed by AAR comparison.
    evidence = TextProfileEvidence(
        path=path_label(resolved_path, root),
        byte_size=len(content),
        rule_count=len(raw_lines),
        sha256=sha256_bytes(content),
    )
    return evidence, content


def inspect_engine_aar(path: Path, root: Path, expected_profile: bytes) -> ArchiveEvidence:
    """Prove the release AAR carries exactly the reviewed SDK text Baseline Profile."""

    # The resolved archive is checked before opening so missing-build errors stay actionable.
    resolved_path = path.resolve()
    if not resolved_path.is_file():
        raise FileNotFoundError(f"Engine AAR does not exist: {resolved_path}")
    # Archive bytes are hashed for an immutable link between evidence and the inspected artifact.
    archive_content = resolved_path.read_bytes()
    with zipfile.ZipFile(resolved_path) as archive:
        try:
            # Exact equality prevents a stale generated AAR from passing against newer source rules.
            packaged_profile = archive.read(AAR_PROFILE_ENTRY)
        except KeyError as error:
            raise ValueError(f"Engine AAR is missing {AAR_PROFILE_ENTRY}: {resolved_path}") from error
    if packaged_profile != expected_profile:
        raise ValueError(f"Engine AAR contains a stale or altered {AAR_PROFILE_ENTRY}: {resolved_path}")
    return ArchiveEvidence(
        path=path_label(resolved_path, root),
        byte_size=len(archive_content),
        sha256=sha256_bytes(archive_content),
        profile_entries={AAR_PROFILE_ENTRY: len(packaged_profile)},
    )


def inspect_consumer_apk(path: Path, root: Path) -> ArchiveEvidence:
    """Prove a release-like consumer packages binary ART profiles and ProfileInstaller."""

    # The resolved archive is checked before opening so an unbuilt variant cannot reuse stale evidence.
    resolved_path = path.resolve()
    if not resolved_path.is_file():
        raise FileNotFoundError(f"Consumer APK does not exist: {resolved_path}")
    # Archive bytes are hashed for traceability to the exact Release/Benchmark candidate.
    archive_content = resolved_path.read_bytes()
    # Entry sizes are retained in the report as direct non-empty binary packaging evidence.
    profile_entries: dict[str, int] = {}
    with zipfile.ZipFile(resolved_path) as archive:
        # A set avoids repeated linear scans of the APK central directory.
        archive_names = set(archive.namelist())
        for entry in APK_PROFILE_ENTRIES:
            if entry not in archive_names:
                raise ValueError(f"Consumer APK is missing {entry}: {resolved_path}")
            # Reading the data catches corrupt ZIP entries in addition to central-directory metadata.
            entry_size = len(archive.read(entry))
            if entry_size <= 0:
                raise ValueError(f"Consumer APK contains an empty {entry}: {resolved_path}")
            profile_entries[entry] = entry_size
        if PROFILE_INSTALLER_MARKER not in archive_names:
            raise ValueError(f"Consumer APK is missing ProfileInstaller marker: {resolved_path}")
        # The marker is also read to reject a corrupt or accidentally empty dependency artifact.
        installer_marker_size = len(archive.read(PROFILE_INSTALLER_MARKER))
        if installer_marker_size <= 0:
            raise ValueError(f"Consumer APK contains an empty ProfileInstaller marker: {resolved_path}")
        profile_entries[PROFILE_INSTALLER_MARKER] = installer_marker_size
    return ArchiveEvidence(
        path=path_label(resolved_path, root),
        byte_size=len(archive_content),
        sha256=sha256_bytes(archive_content),
        profile_entries=profile_entries,
    )


def write_report(
    path: Path,
    engine_profile: TextProfileEvidence,
    consumer_profile: TextProfileEvidence,
    startup_profile: TextProfileEvidence,
    engine_aar: ArchiveEvidence,
    consumer_apks: Sequence[ArchiveEvidence],
) -> None:
    """Write deterministic packaging evidence suitable for a release-gate artifact."""

    # The versioned schema can evolve without silently changing downstream CI interpretation.
    report = {
        "schemaVersion": 1,
        "status": "passed",
        "profiles": {
            "engineBaseline": asdict(engine_profile),
            "consumerBaseline": asdict(consumer_profile),
            "consumerStartup": asdict(startup_profile),
        },
        "engineAar": asdict(engine_aar),
        "consumerApks": [asdict(evidence) for evidence in consumer_apks],
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main(arguments: Sequence[str] | None = None) -> int:
    """Validate all tracked/generated profile layers and fail closed on incomplete packaging."""

    # Explicit arguments keep the function directly testable without mutating process state.
    options = parse_arguments(arguments if arguments is not None else sys.argv[1:])
    # The normalized root is shared by every deterministic report label.
    root = options.root.resolve()
    try:
        # SDK rules may only be owned by the two current engine package roots.
        engine_profile, engine_profile_bytes = inspect_text_profile(
            options.engine_profile,
            root,
            ("Lcom/purride/pixelcore/", "Lcom/purride/pixelui/"),
            ("Lcom/purride/pixelcore/PixelBuffer;", "Lcom/purride/pixelui/PixelHostView;"),
            minimum_rule_count=100,
        )
        # Consumer baseline rules intentionally contain only the benchmark target's own code.
        consumer_profile, _ = inspect_text_profile(
            options.consumer_profile,
            root,
            ("Lcom/purride/pixelbenchmark/target/",),
            ("Lcom/purride/pixelbenchmark/target/PixelBenchmarkActivity;",),
            minimum_rule_count=10,
        )
        # Startup DEX layout needs both the real target Activity and engine Host entry point.
        startup_profile, _ = inspect_text_profile(
            options.startup_profile,
            root,
            (
                "Lcom/purride/pixelcore/",
                "Lcom/purride/pixelui/",
                "Lcom/purride/pixelbenchmark/target/",
            ),
            (
                "Lcom/purride/pixelbenchmark/target/PixelBenchmarkActivity;",
                "Lcom/purride/pixelui/PixelHostView;",
            ),
            minimum_rule_count=100,
        )
        # AAR validation is byte-for-byte against the tracked engine profile.
        engine_aar = inspect_engine_aar(options.engine_aar, root, engine_profile_bytes)
        # Every requested release-like variant must independently carry binary profile assets.
        consumer_apks = [inspect_consumer_apk(apk, root) for apk in options.consumer_apk]
        write_report(
            options.report.resolve(),
            engine_profile,
            consumer_profile,
            startup_profile,
            engine_aar,
            consumer_apks,
        )
    except Exception as error:
        print(f"Baseline Profile verification failed: {error}", file=sys.stderr)
        return 1
    print(f"Baseline Profile packaging verified for {len(consumer_apks)} APK(s): {options.report.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
