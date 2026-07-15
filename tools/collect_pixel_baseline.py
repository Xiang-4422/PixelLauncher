#!/usr/bin/env python3
"""Collect reproducible pixel-engine build, test, API, artifact, and JVM performance evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import subprocess
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable, Sequence
from xml.etree import ElementTree


# The report schema is versioned so later benchmark modules can migrate consumers explicitly.
SCHEMA_VERSION = 1
def parse_arguments(arguments: Sequence[str]) -> argparse.Namespace:
    """Parse collector options with stable repository-relative defaults."""

    default_root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=default_root, help="Repository root.")
    parser.add_argument(
        "--output",
        type=Path,
        default=default_root / "build/reports/pixel-baseline/baseline.json",
        help="Machine-readable baseline output.",
    )
    return parser.parse_args(arguments)


def run_text(command: Sequence[str], root: Path, *, stderr: int | None = None) -> str:
    """Run a read-only command and return normalized UTF-8 output."""

    result = subprocess.run(
        list(command),
        cwd=root,
        check=True,
        stdout=subprocess.PIPE,
        stderr=stderr if stderr is not None else subprocess.PIPE,
        text=True,
    )
    return result.stdout.strip()


def hash_file(path: Path) -> str:
    """Return the SHA-256 checksum of one release artifact without loading it all into memory."""

    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def count_kotlin_sources(source_root: Path) -> dict[str, int]:
    """Count main Kotlin files and physical lines under the engine source root."""

    source_files = sorted(source_root.rglob("*.kt"))
    line_count = sum(len(path.read_text(encoding="utf-8").splitlines()) for path in source_files)
    return {"files": len(source_files), "physicalLines": line_count}


def parse_test_results(module: str, result_root: Path) -> dict[str, int | str]:
    """Aggregate fresh JUnit XML suites for one Gradle module."""

    result_files = sorted(result_root.rglob("TEST-*.xml")) if result_root.exists() else []
    if not result_files:
        raise FileNotFoundError(f"Missing JUnit XML results for {module}: {result_root}")

    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    for result_file in result_files:
        suite = ElementTree.parse(result_file).getroot()
        for key in totals:
            totals[key] += int(suite.attrib.get(key, "0"))
    return {"module": module, "suiteFiles": len(result_files), **totals}


def parse_key_value_report(path: Path) -> dict[str, str]:
    """Read the leading key/value section of a text report before any detail list."""

    if not path.exists():
        raise FileNotFoundError(f"Missing report: {path}")
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            break
        if "=" not in line:
            continue
        key, value = line.split("=", maxsplit=1)
        values[key] = value
    return values


def parse_kdoc_report(path: Path) -> dict[str, int | float]:
    """Convert the KDoc gate report into numeric machine-readable fields."""

    values = parse_key_value_report(path)
    required_keys = {
        "publicDeclarations",
        "documentedDeclarations",
        "coveragePercent",
        "minimumPercent",
    }
    if not required_keys.issubset(values):
        raise ValueError(f"Incomplete KDoc report: {path}")
    return {
        "publicDeclarations": int(values["publicDeclarations"]),
        "documentedDeclarations": int(values["documentedDeclarations"]),
        "coveragePercent": float(values["coveragePercent"]),
        "minimumPercent": float(values["minimumPercent"]),
    }


def parse_perf_report(path: Path) -> dict[str, Any]:
    """Parse the thresholded JVM properties report while labeling it as non-device evidence."""

    if not path.exists():
        raise FileNotFoundError(f"Missing JVM performance report: {path}")
    values = {
        key: value
        for line in path.read_text(encoding="utf-8").splitlines()
        if "=" in line
        for key, value in [line.split("=", maxsplit=1)]
    }
    required_metadata = {
        "formatVersion",
        "runId",
        "thresholdScale",
        "warmupFrames",
        "sampleFrames",
        "sampleBatches",
        "sceneCount",
        "overallPass",
    }
    if not required_metadata.issubset(values):
        raise ValueError(f"Incomplete JVM performance report metadata: {path}")
    scene_names = sorted(
        key.removeprefix("scene.").removesuffix(".frames")
        for key in values
        if key.startswith("scene.") and key.endswith(".frames")
    )
    if len(scene_names) != int(values["sceneCount"]):
        raise ValueError(f"JVM performance scene count does not match its fields: {path}")

    samples: list[dict[str, int | float | str]] = []
    for scene_name in scene_names:
        prefix = f"scene.{scene_name}."
        required_scene_fields = {
            "frames",
            "width",
            "height",
            "totalNanos",
            "averageNanos",
            "batchAverageNanos",
            "baseMaxAverageNanos",
            "maxAverageNanos",
            "pass",
        }
        if not all(f"{prefix}{field}" in values for field in required_scene_fields):
            raise ValueError(f"Incomplete JVM performance scene '{scene_name}': {path}")
        # Batch samples preserve the v2 median source distribution used by the JVM trend gate.
        batch_average_nanos = [
            int(value)
            for value in values[f"{prefix}batchAverageNanos"].split(",")
        ]
        if len(batch_average_nanos) != int(values["sampleBatches"]):
            raise ValueError(f"JVM performance scene '{scene_name}' has the wrong batch count: {path}")
        samples.append(
            {
                "name": scene_name,
                "frames": int(values[f"{prefix}frames"]),
                "width": int(values[f"{prefix}width"]),
                "height": int(values[f"{prefix}height"]),
                "totalNanos": int(values[f"{prefix}totalNanos"]),
                "averageNanos": int(values[f"{prefix}averageNanos"]),
                "batchAverageNanos": batch_average_nanos,
                "baseMaxAverageNanos": int(values[f"{prefix}baseMaxAverageNanos"]),
                "maxAverageNanos": int(values[f"{prefix}maxAverageNanos"]),
                "passed": values[f"{prefix}pass"] == "true",
            },
        )
    if not samples:
        raise ValueError(f"Incomplete JVM performance report: {path}")
    return {
        "kind": "jvm-smoke",
        "coversAndroidCanvasSubmit": False,
        "formatVersion": int(values["formatVersion"]),
        "runId": values["runId"],
        "thresholdScale": float(values["thresholdScale"]),
        "warmupFrames": int(values["warmupFrames"]),
        "sampleFrames": int(values["sampleFrames"]),
        "sampleBatches": int(values["sampleBatches"]),
        "environment": {
            "javaRuntimeVersion": values.get("javaRuntimeVersion", "unreported"),
            "javaVmName": values.get("javaVmName", "unreported"),
            "osName": values.get("osName", "unreported"),
            "osArch": values.get("osArch", "unreported"),
        },
        "overallPassed": values["overallPass"] == "true",
        "samples": samples,
    }


def inspect_release_aar(path: Path, root: Path | None = None) -> dict[str, int | str]:
    """Record size, checksum, and class count for the exact Release AAR."""

    if not path.exists():
        raise FileNotFoundError(f"Missing release AAR: {path}")
    with zipfile.ZipFile(path) as aar:
        try:
            classes_jar = aar.read("classes.jar")
        except KeyError as error:
            raise ValueError(f"Release AAR has no classes.jar: {path}") from error
    from io import BytesIO

    with zipfile.ZipFile(BytesIO(classes_jar)) as classes:
        class_count = sum(1 for entry in classes.infolist() if entry.filename.endswith(".class"))
    artifact_path = path.as_posix()
    if root is not None:
        try:
            artifact_path = path.resolve().relative_to(root.resolve()).as_posix()
        except ValueError:
            artifact_path = path.resolve().as_posix()
    return {
        "path": artifact_path,
        "bytes": path.stat().st_size,
        "sha256": hash_file(path),
        "classCount": class_count,
    }


def java_version(root: Path) -> str:
    """Return the first combined Java version line used by Gradle."""

    result = subprocess.run(
        ["java", "-version"],
        cwd=root,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    return result.stdout.splitlines()[0].strip()


def load_optional_json(path: Path, missing_reason: str) -> dict[str, Any]:
    """Load optional device evidence or emit an explicit machine-readable skipped state."""

    if not path.exists():
        return {"status": "skipped", "reason": missing_reason}
    with path.open(encoding="utf-8") as source:
        return json.load(source)


def relative_paths(paths: Iterable[Path], root: Path) -> list[str]:
    """Normalize changed paths for environment-independent JSON output."""

    normalized: list[str] = []
    for path in paths:
        try:
            normalized.append(path.resolve().relative_to(root.resolve()).as_posix())
        except ValueError:
            normalized.append(path.resolve().as_posix())
    return sorted(normalized)


def git_changed_paths(root: Path) -> list[Path]:
    """Return modified and untracked paths without locale-dependent Git quoting."""

    tracked = subprocess.run(
        ["git", "diff", "HEAD", "--name-only", "-z"],
        cwd=root,
        check=True,
        stdout=subprocess.PIPE,
    ).stdout.decode("utf-8").split("\0")
    untracked = subprocess.run(
        ["git", "ls-files", "--others", "--exclude-standard", "-z"],
        cwd=root,
        check=True,
        stdout=subprocess.PIPE,
    ).stdout.decode("utf-8").split("\0")
    return [root / path for path in sorted({*tracked, *untracked} - {""})]


def collect_baseline(root: Path) -> dict[str, Any]:
    """Collect all required fresh evidence and fail when a required build report is missing."""

    aar_path = root / "pixel-engine/build/outputs/aar/pixel-engine-release.aar"
    test_roots = {
        "pixel-engine": root / "pixel-engine/build/test-results/testDebugUnitTest",
        "pixel-demo": root / "pixel-demo/build/test-results/testDebugUnitTest",
        "app": root / "app/build/test-results/testDebugUnitTest",
    }
    changed_paths = git_changed_paths(root)
    return {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
        "repository": {
            "head": run_text(["git", "rev-parse", "HEAD"], root),
            "branch": run_text(["git", "branch", "--show-current"], root),
            "dirty": bool(changed_paths),
            "changedPaths": relative_paths(changed_paths, root),
        },
        "environment": {
            "operatingSystem": platform.platform(),
            "architecture": platform.machine(),
            "python": platform.python_version(),
            "java": java_version(root),
            "androidSdkRootConfigured": bool(os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")),
        },
        "source": {
            "pixelEngineMainKotlin": count_kotlin_sources(root / "pixel-engine/src/main/kotlin"),
        },
        "tests": [
            parse_test_results(module, result_root)
            for module, result_root in test_roots.items()
        ],
        "kdoc": parse_kdoc_report(root / "pixel-engine/build/reports/kdoc/kdoc-coverage.txt"),
        "artifact": inspect_release_aar(aar_path, root),
        "performance": {
            "jvm": parse_perf_report(root / "pixel-engine/build/reports/perf/pixel-engine-render-smoke.txt"),
            "jvmTrend": load_optional_json(
                root / "build/reports/performance/jvm-smoke-trend.json",
                "JVM smoke trend gate was not produced before baseline collection.",
            ),
            "device": load_optional_json(
                root / "build/reports/device-baseline/summary.json",
                "No device frame baseline was produced for this run.",
            ),
        },
        "security": {
            "secretScan": load_optional_json(
                root / "build/reports/security/secret-scan.json",
                "Secret scan report was not produced before baseline collection.",
            ),
            "backupContract": load_optional_json(
                root / "build/reports/security/backup-contract.json",
                "Compiled APK backup contract was not verified before baseline collection.",
            ),
        },
    }


def write_baseline(path: Path, baseline: dict[str, Any]) -> None:
    """Write UTF-8 deterministic-key JSON for CI, reviews, and later comparison tooling."""

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(baseline, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def main(arguments: Sequence[str] | None = None) -> int:
    """Collect and write the baseline, returning non-zero when required evidence is incomplete."""

    options = parse_arguments(arguments if arguments is not None else sys.argv[1:])
    root = options.root.resolve()
    try:
        baseline = collect_baseline(root)
        write_baseline(options.output.resolve(), baseline)
    except Exception as error:
        print(f"Pixel baseline collection failed: {error}", file=sys.stderr)
        return 1
    print(f"Pixel baseline written to {options.output.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
