#!/usr/bin/env python3
"""Verify that built APK manifests and XML resources exclude the retired plaintext credential file."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence
from xml.etree import ElementTree


# Android XML namespace used by manifest attributes in apkanalyzer output.
ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
# The retired preference file must remain excluded forever so old backups cannot restore it.
LEGACY_PREFERENCE_FILE = "pixel_launcher_ai_prefs.xml"


@dataclass(frozen=True)
class BackupContractEvidence:
    """Records which built APK and resource scopes were verified."""

    apk: str
    manifest_rules: bool
    full_backup_shared_preferences: bool
    cloud_backup_shared_preferences: bool
    device_transfer_shared_preferences: bool


def parse_arguments(arguments: Sequence[str]) -> argparse.Namespace:
    """Parse one or more built APK inputs and a stable JSON report path."""

    default_root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=default_root, help="Repository root.")
    parser.add_argument("--apk", action="append", type=Path, required=True, help="Built APK to inspect.")
    parser.add_argument(
        "--apkanalyzer",
        type=Path,
        default=None,
        help="Android SDK apkanalyzer executable; auto-detected when omitted.",
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=default_root / "build/reports/security/backup-contract.json",
        help="Machine-readable evidence output.",
    )
    return parser.parse_args(arguments)


def resolve_apkanalyzer(explicit_path: Path | None) -> Path:
    """Resolve apkanalyzer from an explicit path, PATH, or the configured Android SDK."""

    if explicit_path is not None:
        candidate = explicit_path.resolve()
        if candidate.is_file():
            return candidate
        raise FileNotFoundError(f"apkanalyzer does not exist: {candidate}")

    path_candidate = shutil.which("apkanalyzer")
    if path_candidate:
        return Path(path_candidate).resolve()
    sdk_root = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if sdk_root:
        candidate = Path(sdk_root) / "cmdline-tools/latest/bin/apkanalyzer"
        if candidate.is_file():
            return candidate.resolve()
    raise FileNotFoundError("apkanalyzer was not found on PATH or under the configured Android SDK.")


def run_apkanalyzer(executable: Path, arguments: Sequence[str]) -> str:
    """Run apkanalyzer and return XML output, preserving non-zero failures as gate failures."""

    result = subprocess.run(
        [str(executable), *arguments],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    return result.stdout


def has_exact_exclude(parent: ElementTree.Element, domain: str) -> bool:
    """Return true when one scope excludes the exact retired preference file and domain."""

    return any(
        child.tag == "exclude"
        and child.attrib.get("domain") == domain
        and child.attrib.get("path") == LEGACY_PREFERENCE_FILE
        for child in parent
    )


def validate_contract(
    manifest_xml: str,
    full_backup_xml: str,
    data_extraction_xml: str,
    apk_label: str,
) -> BackupContractEvidence:
    """Validate manifest references plus API 24–30 and API 31+ backup/transfer exclusions."""

    manifest = ElementTree.fromstring(manifest_xml)
    application = manifest.find("application")
    if application is None:
        raise ValueError(f"Built manifest has no application element: {apk_label}")
    full_backup_reference = application.attrib.get(f"{{{ANDROID_NAMESPACE}}}fullBackupContent", "")
    extraction_reference = application.attrib.get(f"{{{ANDROID_NAMESPACE}}}dataExtractionRules", "")
    # Compiled manifests expose resource IDs such as @ref/0x7f..., while plain XML tests retain @xml names.
    # The exact compiled resources are independently extracted and validated below.
    manifest_rules = full_backup_reference.startswith("@") and extraction_reference.startswith("@")
    if not manifest_rules:
        raise ValueError(f"Built manifest does not reference both backup rule resources: {apk_label}")

    full_backup = ElementTree.fromstring(full_backup_xml)
    full_backup_excluded = has_exact_exclude(full_backup, "sharedpref")
    if not full_backup_excluded:
        raise ValueError(f"API 24–30 backup rules do not exclude the retired preference: {apk_label}")

    data_extraction = ElementTree.fromstring(data_extraction_xml)
    cloud_backup = data_extraction.find("cloud-backup")
    device_transfer = data_extraction.find("device-transfer")
    cloud_backup_excluded = cloud_backup is not None and has_exact_exclude(cloud_backup, "sharedpref")
    device_transfer_excluded = device_transfer is not None and has_exact_exclude(device_transfer, "sharedpref")
    if not cloud_backup_excluded or not device_transfer_excluded:
        raise ValueError(f"API 31+ cloud/device rules do not both exclude the retired preference: {apk_label}")

    return BackupContractEvidence(
        apk=apk_label,
        manifest_rules=manifest_rules,
        full_backup_shared_preferences=full_backup_excluded,
        cloud_backup_shared_preferences=cloud_backup_excluded,
        device_transfer_shared_preferences=device_transfer_excluded,
    )


def inspect_apk(apkanalyzer: Path, apk: Path, root: Path) -> BackupContractEvidence:
    """Extract the merged manifest and compiled XML resources from one built APK."""

    resolved_apk = apk.resolve()
    if not resolved_apk.is_file():
        raise FileNotFoundError(f"Built APK does not exist: {resolved_apk}")
    try:
        apk_label = resolved_apk.relative_to(root.resolve()).as_posix()
    except ValueError:
        apk_label = resolved_apk.as_posix()
    manifest_xml = run_apkanalyzer(apkanalyzer, ["manifest", "print", str(resolved_apk)])
    # Release resource optimization may rename res/xml files, so resolve each compiled table value first.
    full_backup_path = run_apkanalyzer(
        apkanalyzer,
        ["resources", "value", "--config", "default", "--type", "xml", "--name", "backup_rules", str(resolved_apk)],
    ).strip()
    extraction_path = run_apkanalyzer(
        apkanalyzer,
        [
            "resources",
            "value",
            "--config",
            "default",
            "--type",
            "xml",
            "--name",
            "data_extraction_rules",
            str(resolved_apk),
        ],
    ).strip()
    full_backup_xml = run_apkanalyzer(
        apkanalyzer,
        ["resources", "xml", "--file", full_backup_path, str(resolved_apk)],
    )
    data_extraction_xml = run_apkanalyzer(
        apkanalyzer,
        ["resources", "xml", "--file", extraction_path, str(resolved_apk)],
    )
    return validate_contract(manifest_xml, full_backup_xml, data_extraction_xml, apk_label)


def write_report(path: Path, evidence: Sequence[BackupContractEvidence]) -> None:
    """Write deterministic JSON evidence safe for CI artifact upload."""

    report = {
        "schemaVersion": 1,
        "status": "passed",
        "legacyPreferenceFile": LEGACY_PREFERENCE_FILE,
        "apks": [
            {
                "apk": item.apk,
                "manifestRules": item.manifest_rules,
                "fullBackupSharedPreferences": item.full_backup_shared_preferences,
                "cloudBackupSharedPreferences": item.cloud_backup_shared_preferences,
                "deviceTransferSharedPreferences": item.device_transfer_shared_preferences,
            }
            for item in evidence
        ],
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main(arguments: Sequence[str] | None = None) -> int:
    """Inspect every requested APK and fail closed when any compiled contract is incomplete."""

    options = parse_arguments(arguments if arguments is not None else sys.argv[1:])
    root = options.root.resolve()
    try:
        apkanalyzer = resolve_apkanalyzer(options.apkanalyzer)
        evidence = [inspect_apk(apkanalyzer, apk, root) for apk in options.apk]
        write_report(options.report.resolve(), evidence)
    except Exception as error:
        print(f"Backup contract verification failed: {error}", file=sys.stderr)
        return 1
    print(f"Backup contract verified for {len(evidence)} APK(s): {options.report.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
